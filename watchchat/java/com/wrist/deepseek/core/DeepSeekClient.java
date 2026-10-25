package com.wrist.deepseek.core;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Small, dependency-free DeepSeek / OpenAI-compatible chat client. */
public class DeepSeekClient {
    public interface StreamCallback {
        void onDelta(String contentSoFar, String reasoningSoFar);

        void onDone(String content, String reasoning, String finishReason);

        void onError(String message);
    }

    public interface TestCallback {
        void done(boolean ok, String message);
    }

    private volatile HttpURLConnection active;
    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
        HttpURLConnection c = active;
        if (c != null) {
            try {
                c.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void stream(Settings s, String systemPrompt, List<Message> history,
                       String model, boolean thinking, StreamCallback callback) {
        cancelled = false;
        String key = s.getDeepSeekKey();
        if (TextUtils.isEmpty(key)) {
            callback.onError("还没有配置 DeepSeek API Key");
            return;
        }
        HttpURLConnection c = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", true);
            body.put("max_tokens", 8192);
            JSONObject thinkingMode = new JSONObject();
            thinkingMode.put("type", thinking ? "enabled" : "disabled");
            body.put("thinking", thinkingMode);
            if (thinking) {
                body.put("reasoning_effort", "high");
            } else {
                body.put("temperature", 1.0);
            }
            JSONArray arr = new JSONArray();
            if (!TextUtils.isEmpty(systemPrompt)) {
                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
                arr.put(sys);
            }
            for (Message m : history) {
                if (m == null || TextUtils.isEmpty(m.content)) continue;
                JSONObject o = new JSONObject();
                o.put("role", m.isUser() ? "user" : "assistant");
                o.put("content", m.content);
                arr.put(o);
            }
            body.put("messages", arr);

            c = Http.open(s.getChatEndpoint(), 20000, 180000);
            active = c;
            Http.writeJson(c, body.toString(), key);
            int code = c.getResponseCode();
            if (code != 200) {
                String err = Http.apiMessage(code, Http.errorBody(c));
                if (!cancelled) callback.onError(err);
                return;
            }

            StringBuilder content = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            String finish = "";
            boolean done = false;
            InputStream in = c.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 8192);
            String line;
            while (!cancelled && (line = br.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                if ("[DONE]".equals(data)) {
                    done = true;
                    break;
                }
                try {
                    JSONObject o = new JSONObject(data);
                    if (o.has("error")) {
                        String msg = o.optJSONObject("error") != null
                                ? o.optJSONObject("error").optString("message")
                                : o.optString("error");
                        if (!cancelled) callback.onError(msg == null || msg.isEmpty() ? "生成失败" : msg);
                        return;
                    }
                    JSONArray choices = o.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject choice = choices.optJSONObject(0);
                    if (choice == null) continue;
                    String fr = choice.optString("finish_reason", "");
                    if (!fr.isEmpty() && !"null".equals(fr)) finish = fr;
                    JSONObject delta = choice.optJSONObject("delta");
                    if (delta != null) {
                        String rc = delta.isNull("reasoning_content") ? ""
                                : delta.optString("reasoning_content", "");
                        String cc = delta.isNull("content") ? ""
                                : delta.optString("content", "");
                        if (!rc.isEmpty()) reasoning.append(rc);
                        if (!cc.isEmpty()) content.append(cc);
                        if (!rc.isEmpty() || !cc.isEmpty()) {
                            callback.onDelta(content.toString(), reasoning.toString());
                        }
                    }
                } catch (Throwable parse) {
                    // A keep-alive or non-JSON SSE comment; ignore it.
                }
            }
            if (cancelled) return;
            if (content.length() == 0 && reasoning.length() == 0) {
                callback.onError(done ? "这次没有收到内容，请重试" : "连接中断，没有收到回复");
                return;
            }
            callback.onDone(content.toString(), reasoning.toString(), finish);
        } catch (Throwable t) {
            if (cancelled) return;
            callback.onError(networkMessage(t));
        } finally {
            if (active == c) active = null;
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Non-streaming completion used for transcript repair and setup tests.
     */
    public String complete(Settings s, String system, String user, int maxTokens) throws Exception {
        String key = s.getDeepSeekKey();
        if (TextUtils.isEmpty(key)) throw new IOException("还没有配置 DeepSeek API Key");
        JSONObject body = new JSONObject();
        body.put("model", s.getModel());
        body.put("stream", false);
        body.put("temperature", 0);
        body.put("max_tokens", maxTokens);
        JSONObject thinking = new JSONObject();
        thinking.put("type", "disabled");
        body.put("thinking", thinking);
        JSONArray arr = new JSONArray();
        if (!TextUtils.isEmpty(system)) {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", system);
            arr.put(sys);
        }
        JSONObject u = new JSONObject();
        u.put("role", "user");
        u.put("content", user);
        arr.put(u);
        body.put("messages", arr);

        HttpURLConnection c = Http.open(s.getChatEndpoint(), 15000, 30000);
        try {
            Http.writeJson(c, body.toString(), key);
            int code = c.getResponseCode();
            String raw = Http.readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code != 200) throw new IOException(Http.apiMessage(code, raw));
            JSONObject o = new JSONObject(raw);
            JSONArray choices = o.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw new IOException("没有收到内容");
            JSONObject msg = choices.optJSONObject(0).optJSONObject("message");
            if (msg == null) throw new IOException("没有收到内容");
            return msg.isNull("content") ? "" : msg.optString("content", "").trim();
        } finally {
            try {
                c.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }

    public void test(Settings s, final TestCallback cb) {
        new Thread(() -> {
            try {
                complete(s, "只回复 OK 两个字母。", "ping", 8);
                cb.done(true, "连接成功");
            } catch (Throwable t) {
                cb.done(false, networkMessage(t));
            }
        }, "ds-test").start();
    }

    public static String networkMessage(Throwable t) {
        if (t == null) return "网络错误";
        String m = t.getMessage();
        if (t instanceof java.net.SocketTimeoutException) return "网络超时，请检查手表网络";
        if (t instanceof java.net.UnknownHostException) return "无法连接服务器，请检查网络";
        if (t instanceof javax.net.ssl.SSLException) return "安全连接失败，请检查网络或代理";
        if (m == null || m.trim().isEmpty()) return "网络错误，请稍后重试";
        if (m.contains("timeout") || m.contains("timed out")) return "网络超时，请检查手表网络";
        return m;
    }
}
