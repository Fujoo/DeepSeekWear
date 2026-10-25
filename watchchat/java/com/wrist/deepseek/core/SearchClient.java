package com.wrist.deepseek.core;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

/** DeepSeek's in-house web search: POST /v1/tools/search. */
public class SearchClient {
    public interface Callback {
        void onResults(List<SearchResult> results);

        void onError(String message);
    }

    public void search(Settings settings, String question, int count, Callback callback) {
        String key = settings.getDeepSeekKey();
        if (TextUtils.isEmpty(key)) {
            callback.onError("还没有配置 DeepSeek API Key");
            return;
        }
        String query = question == null ? "" : question.trim();
        if (query.isEmpty()) {
            callback.onError("搜索内容为空");
            return;
        }
        if (query.length() > 128) query = query.substring(0, 128);
        HttpURLConnection c = null;
        try {
            JSONObject body = new JSONObject();
            body.put("query", query);
            body.put("count", Math.max(1, Math.min(10, count)));
            body.put("need_content", false);
            body.put("ori_question", query);

            c = Http.open(settings.getSearchEndpoint(), 15000, 30000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            byte[] bytes = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + key);
            try (java.io.OutputStream os = c.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String raw = Http.readAll(in);
            if (code != 200) {
                callback.onError(searchError(code, raw));
                return;
            }
            List<SearchResult> results = new ArrayList<>();
            JSONArray arr = new JSONObject(raw).optJSONArray("results");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) results.add(SearchResult.fromJson(o));
                }
            }
            callback.onResults(results);
        } catch (Throwable t) {
            callback.onError(DeepSeekClient.networkMessage(t));
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String searchError(int code, String raw) {
        String detail = "";
        try {
            JSONObject o = new JSONObject(raw);
            if (o.optJSONObject("error") != null) {
                detail = o.optJSONObject("error").optString("message", "");
            }
        } catch (Throwable ignored) {
        }
        switch (code) {
            case 401:
                return "DeepSeek Key 无效，无法搜索";
            case 402:
                return "DeepSeek 余额不足，无法搜索";
            case 429:
                return "搜索太频繁，请稍后再试";
            default:
                return detail.isEmpty() ? ("搜索失败（HTTP " + code + "）") : detail;
        }
    }
}
