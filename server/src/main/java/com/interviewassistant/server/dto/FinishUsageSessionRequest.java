package com.interviewassistant.server.dto;

import jakarta.validation.constraints.NotBlank;

public class FinishUsageSessionRequest {
    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
