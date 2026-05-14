package com.interviewassistant.server.dto;

public class InterviewRecordResponse {
    private final String id;
    private final String usageSessionId;
    private final String question;
    private final String answer;
    private final String createdAt;

    public InterviewRecordResponse(String id, String usageSessionId, String question, String answer, String createdAt) {
        this.id = id;
        this.usageSessionId = usageSessionId;
        this.question = question;
        this.answer = answer;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUsageSessionId() {
        return usageSessionId;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
