package com.interviewassistant.server.entity;


import java.time.OffsetDateTime;
import java.util.UUID;

public class UsageSession {
        private String id;

        private String userId;

        private String scenario;

        private String status;

        private OffsetDateTime startedAt;

    private OffsetDateTime endedAt;

    private OffsetDateTime lastHeartbeatAt;

        private Integer durationSeconds;

        private Integer chargedMinutes;

        private Integer chargedSeconds;

        public void prePersist() {
        if (id == null || id.isBlank()) {
            id = "SES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
        if (lastHeartbeatAt == null) {
            lastHeartbeatAt = startedAt;
        }
        if (durationSeconds == null) {
            durationSeconds = 0;
        }
        if (chargedMinutes == null) {
            chargedMinutes = 0;
        }
        if (chargedSeconds == null) {
            chargedSeconds = chargedMinutes * 60;
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

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public OffsetDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getChargedMinutes() {
        return chargedMinutes;
    }

    public void setChargedMinutes(Integer chargedMinutes) {
        this.chargedMinutes = chargedMinutes;
        this.chargedSeconds = chargedMinutes == null ? null : chargedMinutes * 60;
    }

    public Integer getChargedSeconds() {
        return chargedSeconds;
    }

    public void setChargedSeconds(Integer chargedSeconds) {
        this.chargedSeconds = chargedSeconds;
        this.chargedMinutes = chargedSeconds == null ? null : (int) Math.ceil(chargedSeconds / 60.0);
    }
}
