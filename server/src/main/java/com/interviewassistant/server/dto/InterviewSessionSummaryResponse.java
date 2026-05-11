package com.interviewassistant.server.dto;

public class InterviewSessionSummaryResponse {
    private final String sessionId;
    private final String startedAt;
    private final String lastUpdatedAt;
    private final int recordCount;
    private final String previewQuestion;

    public InterviewSessionSummaryResponse(String sessionId, String startedAt, String lastUpdatedAt, int recordCount, String previewQuestion) {
        this.sessionId = sessionId;
        this.startedAt = startedAt;
        this.lastUpdatedAt = lastUpdatedAt;
        this.recordCount = recordCount;
        this.previewQuestion = previewQuestion;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public String getPreviewQuestion() {
        return previewQuestion;
    }
}
