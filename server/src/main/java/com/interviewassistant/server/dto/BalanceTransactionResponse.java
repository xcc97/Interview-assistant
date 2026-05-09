package com.interviewassistant.server.dto;

public class BalanceTransactionResponse {
    private String transactionId;
    private String type;
    private int minutes;
    private int seconds;
    private String sourceType;
    private String sourceId;
    private String sourceName;
    private String createdAt;

    public BalanceTransactionResponse() {
    }

    public BalanceTransactionResponse(String transactionId, String type, int minutes, int seconds,
                                      String sourceType, String sourceId, String sourceName, String createdAt) {
        this.transactionId = transactionId;
        this.type = type;
        this.minutes = minutes;
        this.seconds = seconds;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.createdAt = createdAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }

    public int getSeconds() {
        return seconds;
    }

    public void setSeconds(int seconds) {
        this.seconds = seconds;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
