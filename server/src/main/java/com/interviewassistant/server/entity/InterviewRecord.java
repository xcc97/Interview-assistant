package com.interviewassistant.server.entity;


import java.time.OffsetDateTime;
import java.util.UUID;

public class InterviewRecord {
        private String id;

        private String userId;

    private String usageSessionId;

            private String question;

            private String answer;

        private OffsetDateTime createdAt;

        public void prePersist() {
        if (id == null || id.isBlank()) {
            id = "IR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsageSessionId() {
        return usageSessionId;
    }

    public void setUsageSessionId(String usageSessionId) {
        this.usageSessionId = usageSessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
