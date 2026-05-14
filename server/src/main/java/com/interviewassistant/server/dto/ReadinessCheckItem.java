package com.interviewassistant.server.dto;

public class ReadinessCheckItem {
    private String key;
    private String label;
    private String status;
    private String message;

    public ReadinessCheckItem() {
    }

    public ReadinessCheckItem(String key, String label, String status, String message) {
        this.key = key;
        this.label = label;
        this.status = status;
        this.message = message;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
