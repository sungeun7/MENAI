package com.aichatbot.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private List<Map<String, String>> messages;
    /** 사용자가 만들 때 직접 이름을 지었으면 true — AI가 범주명을 덮어쓰지 않음 */
    private boolean skipAutoTitle;

    public ChatCategory() {
        this.id = UUID.randomUUID().toString();
        this.title = "새 범주";
        this.messages = new ArrayList<>();
    }

    public ChatCategory(String id, String title, List<Map<String, String>> messages) {
        this.id = id;
        this.title = title != null ? title : "범주";
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Map<String, String>> getMessages() {
        return messages;
    }

    public void setMessages(List<Map<String, String>> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public boolean isSkipAutoTitle() {
        return skipAutoTitle;
    }

    public void setSkipAutoTitle(boolean skipAutoTitle) {
        this.skipAutoTitle = skipAutoTitle;
    }
}
