package com.wrist.deepseek.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SearchResult {
    public String title = "";
    public String url = "";
    public String snippet = "";
    public String summary = "";
    public String siteName = "";
    public String date = "";
    public int authority = -1;
    public double score = 0;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("title", title);
            o.put("url", url);
            o.put("snippet", snippet);
            o.put("summary", summary);
            o.put("site_name", siteName);
            o.put("date", date);
            o.put("authority", authority);
            o.put("score", score);
        } catch (Throwable ignored) {
        }
        return o;
    }

    public static SearchResult fromJson(JSONObject o) {
        SearchResult r = new SearchResult();
        r.title = o.optString("title", "");
        r.url = o.optString("url", "");
        r.snippet = o.optString("snippet", "");
        r.summary = o.optString("summary", "");
        r.siteName = o.optString("site_name", "");
        r.date = o.optString("date", "");
        r.authority = o.optInt("authority", -1);
        r.score = o.optDouble("score", 0);
        return r;
    }

    public static String toJsonArray(List<SearchResult> results) {
        JSONArray arr = new JSONArray();
        if (results != null) {
            for (SearchResult r : results) arr.put(r.toJson());
        }
        return arr.toString();
    }

    public static List<SearchResult> listFromJson(String raw) {
        List<SearchResult> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(fromJson(o));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
