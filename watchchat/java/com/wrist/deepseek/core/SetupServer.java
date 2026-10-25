package com.wrist.deepseek.core;

import android.content.Context;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A tiny, token-protected setup page that lives only while the watch dialog is
 * open. Lets the user type API keys on a phone instead of on a 1.4" keyboard.
 */
public class SetupServer {
    public interface Listener {
        void onSaved();
    }

    private final Context context;
    private final Settings settings;
    private final Listener listener;
    private ServerSocket server;
    private Thread thread;
    private volatile boolean running;
    private int port;
    private String token;
    private String savedMessage;

    public SetupServer(Context context, Settings settings, Listener listener) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        this.listener = listener;
    }

    public int start() throws IOException {
        token = randomToken();
        IOException last = null;
        for (int p : new int[]{8787, 8788, 8789, 8790}) {
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(p), 8);
                port = p;
                last = null;
                break;
            } catch (IOException e) {
                last = e;
                closeQuietly(server);
                server = null;
            }
        }
        if (server == null) throw last == null ? new IOException("端口被占用") : last;
        running = true;
        thread = new Thread(this::acceptLoop, "phone-setup");
        thread.setDaemon(true);
        thread.start();
        return port;
    }

    public void stop() {
        running = false;
        closeQuietly(server);
        server = null;
    }

    public String url() {
        String ip = localIp();
        return ip == null ? null : "http://" + ip + ":" + port + "/?t=" + token;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                s.setSoTimeout(7000);
                handle(s);
            } catch (Throwable t) {
                if (!running) break;
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            InputStream in = new BufferedInputStream(s.getInputStream());
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];

            Map<String, String> headers = new HashMap<>();
            String line;
            int guard = 0;
            while ((line = readLine(in)) != null && !line.isEmpty() && guard++ < 60) {
                int p = line.indexOf(':');
                if (p > 0) headers.put(line.substring(0, p).trim().toLowerCase(Locale.US),
                        line.substring(p + 1).trim());
            }
            int len = 0;
            try {
                len = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (Throwable ignored) {
            }
            if (len < 0 || len > 65536) len = 0;
            byte[] body = new byte[len];
            int read = 0;
            while (read < len) {
                int n = in.read(body, read, len - read);
                if (n <= 0) break;
                read += n;
            }
            String form = new String(body, 0, read, StandardCharsets.UTF_8);
            Map<String, String> params = parseForm(form);
            String q = path;
            int qi = q.indexOf('?');
            if (qi >= 0) {
                params.putAll(parseForm(q.substring(qi + 1)));
                q = q.substring(0, qi);
            }
            if (!token.equals(params.get("t"))) {
                respond(s, 403, page("无法打开", "<p class=\"err\">链接已失效，请在手表上重新打开配置页。</p>"));
                return;
            }
            if ("GET".equals(method) && ("/".equals(q) || "/index.html".equals(q))) {
                respond(s, 200, formPage());
                return;
            }
            if ("POST".equals(method) && "/save".equals(q)) {
                save(params);
                savedMessage = "配置已保存";
                if (listener != null) listener.onSaved();
                respond(s, 200, successPage());
                return;
            }
            respond(s, 404, page("未找到", "<p class=\"err\">页面不存在。</p>"));
        } catch (Throwable ignored) {
        }
    }

    private void save(Map<String, String> p) {
        String ds = p.get("ds_key");
        if (!TextUtils.isEmpty(ds) && !ds.trim().isEmpty()) settings.setDeepSeekKey(ds.trim());
        String asr = p.get("asr_key");
        if (!TextUtils.isEmpty(asr) && !asr.trim().isEmpty()) settings.setAsrKey(asr.trim());
        String base = p.get("ds_base");
        if (!TextUtils.isEmpty(base) && !base.trim().isEmpty()) settings.setDeepSeekBase(base.trim());
        String preset = p.get("asr_preset");
        if (!TextUtils.isEmpty(preset)
                && (preset.equals("siliconflow") || preset.equals("openai") || preset.equals("groq"))) {
            settings.applyAsrPreset(preset);
        }
        String asrModel = p.get("asr_model");
        if (!TextUtils.isEmpty(asrModel) && !asrModel.trim().isEmpty()) settings.setAsrModel(asrModel.trim());
        if ("on".equals(p.get("whisper"))) settings.setWhisperMode(true);
        else settings.setWhisperMode(false);
        if ("on".equals(p.get("autocorrect"))) settings.setAutoCorrect(true);
        else settings.setAutoCorrect(false);
    }

    private String formPage() {
        String dsSet = settings.hasDeepSeekKey() ? "已设置，留空则不修改" : "sk-...";
        String asrSet = settings.hasAsrKey() ? "已设置，留空则不修改" : "sk-...";
        String preset = settings.getAsrPreset();
        String checkedW = settings.isWhisperMode() ? "checked" : "";
        String checkedC = settings.isAutoCorrect() ? "checked" : "";
        return page("手机配置", "" +
                "<p class=\"lead\">在这里填写，手表会立即生效。密钥只保存在手表本地。</p>" +
                "<form method=\"post\" action=\"/save\">" +
                "<input type=\"hidden\" name=\"t\" value=\"" + esc(token) + "\">" +
                "<label>DeepSeek API Key</label>" +
                "<input type=\"password\" name=\"ds_key\" placeholder=\"" + esc(dsSet) + "\" autocomplete=\"off\" spellcheck=\"false\">" +
                "<label>DeepSeek 接口地址（一般不用改）</label>" +
                "<input type=\"url\" name=\"ds_base\" value=\"" + esc(settings.getDeepSeekBase()) + "\" placeholder=\"https://api.deepseek.com\">" +
                "<div class=\"hr\"></div>" +
                "<label>语音识别 API Key</label>" +
                "<input type=\"password\" name=\"asr_key\" placeholder=\"" + esc(asrSet) + "\" autocomplete=\"off\" spellcheck=\"false\">" +
                "<label>语音识别服务</label>" +
                "<select name=\"asr_preset\">" +
                "<option value=\"siliconflow\"" + ("siliconflow".equals(preset) ? " selected" : "") + ">硅基流动 · SenseVoice（推荐）</option>" +
                "<option value=\"openai\"" + ("openai".equals(preset) ? " selected" : "") + ">OpenAI Whisper</option>" +
                "<option value=\"groq\"" + ("groq".equals(preset) ? " selected" : "") + ">Groq Whisper</option>" +
                "</select>" +
                "<label>识别模型</label>" +
                "<input type=\"text\" name=\"asr_model\" value=\"" + esc(settings.getAsrModel()) + "\" placeholder=\"TeleAI/TeleSpeechASR\">" +
                "<label class=\"check\"><input type=\"checkbox\" name=\"whisper\" " + checkedW + "> 悄悄话增强</label>" +
                "<label class=\"check\"><input type=\"checkbox\" name=\"autocorrect\" " + checkedC + "> 用 DeepSeek 结合上下文纠错</label>" +
                "<button type=\"submit\">保存到手表</button>" +
                "</form>" +
                "<p class=\"foot\">页面关闭后服务会自动停止</p>");
    }

    private String successPage() {
        return page("已保存", "<div class=\"ok\">✓</div><h2>配置已保存</h2>" +
                "<p class=\"lead\">可以关闭这个页面，回到手表继续使用。</p>" +
                "<p class=\"foot\">如果修改了密钥，下次请求立即生效。</p>");
    }

    private String page(String title, String content) {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">" +
                "<meta name=\"theme-color\" content=\"#08080a\">" +
                "<title>" + esc(title) + " · DeepSeek 腕上</title><style>" +
                "*{box-sizing:border-box}body{margin:0;background:#08080a;color:#f4f4f7;" +
                "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans SC',sans-serif;" +
                "line-height:1.5;padding:18px 14px 34px}.wrap{max-width:520px;margin:0 auto}" +
                ".brand{display:flex;align-items:center;gap:10px;margin:6px 0 18px}" +
                ".logo{width:38px;height:38px;border-radius:11px;background:linear-gradient(145deg,#6e87ff,#3047ce);" +
                "display:grid;place-items:center;font-size:22px}.brand b{font-size:18px}" +
                ".card{background:#141419;border:1px solid #292932;border-radius:18px;padding:16px}" +
                "h2{font-size:21px;margin:8px 0 4px}.lead{color:#9a9aa6;font-size:13px;margin:0 0 12px}" +
                ".hr{height:1px;background:#25252e;margin:18px 0}" +
                "label{display:block;color:#b9bbc8;font-size:12px;margin:13px 0 6px}" +
                "input[type=password],input[type=url],input[type=text]{width:100%;height:45px;border-radius:12px;" +
                "border:1px solid #30303a;background:#0e0e12;color:#f4f4f7;padding:0 12px;font-size:15px;outline:none}" +
                "input:focus{border-color:#4d6bfe}select{width:100%;height:45px;border-radius:12px;" +
                "border:1px solid #30303a;background:#0e0e12;color:#f4f4f7;padding:0 10px;font-size:14px}" +
                ".check{display:flex;align-items:center;gap:9px;font-size:14px;color:#f4f4f7;margin:14px 0}" +
                ".check input{width:20px;height:20px;accent-color:#4d6bfe}" +
                "button{width:100%;height:48px;border:0;border-radius:14px;background:#4d6bfe;color:#fff;" +
                "font-size:16px;font-weight:600;margin-top:18px}button:active{background:#3a53d8}" +
                ".foot{text-align:center;color:#5f5f6b;font-size:11px;margin:15px 0 0}" +
                ".ok{width:58px;height:58px;border-radius:50%;background:#1d3a2a;color:#4ade80;" +
                "display:grid;place-items:center;font-size:30px;margin:14px auto}.err{color:#ff8080}" +
                "h2,.lead,.ok{text-align:center}" +
                "</style></head><body><div class=\"wrap\"><div class=\"brand\">" +
                "<div class=\"logo\">🐳</div><b>DeepSeek 腕上</b></div>" +
                "<div class=\"card\">" + content + "</div></div></body></html>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static Map<String, String> parseForm(String s) {
        Map<String, String> map = new HashMap<>();
        if (s == null || s.isEmpty()) return map;
        for (String pair : s.split("&")) {
            int i = pair.indexOf('=');
            String k = i >= 0 ? pair.substring(0, i) : pair;
            String v = i >= 0 ? pair.substring(i + 1) : "";
            try {
                k = URLDecoder.decode(k, "UTF-8");
                v = URLDecoder.decode(v, "UTF-8");
            } catch (Throwable ignored) {
            }
            map.put(k, v);
        }
        return map;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int last = -1;
        while (true) {
            int b = in.read();
            if (b < 0) return out.size() == 0 ? null : out.toString("UTF-8");
            if (b == '\n') break;
            if (b != '\r') out.write(b);
            if (out.size() > 8192) break;
            last = b;
        }
        return out.toString("UTF-8");
    }

    private static void respond(Socket s, int code, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : code == 403 ? " Forbidden" : " Error") + "\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n" +
                "X-Frame-Options: DENY\r\n\r\n";
        OutputStream out = s.getOutputStream();
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String randomToken() {
        String chars = "abcdefghjkmnpqrstuvwxyz23456789";
        Random r = new Random();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) b.append(chars.charAt(r.nextInt(chars.length())));
        return b.toString();
    }

    public static String localIp() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                String name = n.getName().toLowerCase(Locale.US);
                if (name.startsWith("rmnet") || name.startsWith("tun") || name.startsWith("ppp")) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void closeQuietly(ServerSocket s) {
        if (s != null) {
            try {
                s.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
