package com.aichatbot.model;

public class ChatResponse {
    private String message;
    private boolean success;
    /** 서버 세션에 저장된 대화 메시지 수(맥락 반영 범위 안내용) */
    private int historyMessageCount;
    private String categoryId;
    private String categoryTitle;

    public ChatResponse(String message, boolean success) {
        this(message, success, 0);
    }

    public ChatResponse(String message, boolean success, int historyMessageCount) {
        this.message = message;
        this.success = success;
        this.historyMessageCount = historyMessageCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getHistoryMessageCount() {
        return historyMessageCount;
    }

    public void setHistoryMessageCount(int historyMessageCount) {
        this.historyMessageCount = historyMessageCount;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryTitle() {
        return categoryTitle;
    }

    public void setCategoryTitle(String categoryTitle) {
        this.categoryTitle = categoryTitle;
    }
}

