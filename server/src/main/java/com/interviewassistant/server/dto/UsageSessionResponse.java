package com.interviewassistant.server.dto;

public class UsageSessionResponse {
    private String sessionId;
    private String scenario;
    private String status;
    private String startedAt;
    private String endedAt;
    private int durationSeconds;
    private int chargedMinutes;
    private int chargedSeconds;

    public UsageSessionResponse() {
    }

    public UsageSessionResponse(String sessionId, String scenario, String status, String startedAt,
                                String endedAt, int durationSeconds, int chargedMinutes, int chargedSeconds) {
        this.sessionId = sessionId;
        this.scenario = scenario;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.chargedMinutes = chargedMinutes;
        this.chargedSeconds = chargedSeconds;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(String endedAt) {
        this.endedAt = endedAt;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getChargedMinutes() {
        return chargedMinutes;
    }

    public void setChargedMinutes(int chargedMinutes) {
        this.chargedMinutes = chargedMinutes;
    }

    public int getChargedSeconds() {
        return chargedSeconds;
    }

    public void setChargedSeconds(int chargedSeconds) {
        this.chargedSeconds = chargedSeconds;
    }
}
