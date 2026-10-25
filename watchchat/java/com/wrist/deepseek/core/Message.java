package com.wrist.deepseek.core;

public class Message {
    public long id;
    public long conversationId;
    public String role;
    public String content;
    public String reasoning;
    /** JSON array of web-search sources, empty when this message was not searched. */
    public String sources;
    /** Transient UI state; not persisted. */
    public boolean searching;
    public String searchNote;
    public long createdAt;
    /** 0 = complete, 1 = streaming, 2 = error. */
    public int status;

    public Message() {
    }

    public Message(String role, String content) {
        this.role = role;
        this.content = content == null ? "" : content;
        this.reasoning = "";
        this.sources = "";
        this.searchNote = "";
    }

    public boolean isUser() {
        return "user".equals(role);
    }

    public boolean isAssistant() {
        return "assistant".equals(role);
    }
}
