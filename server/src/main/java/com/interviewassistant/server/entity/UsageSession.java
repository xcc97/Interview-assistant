package com.interviewassistant.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usage_sessions")
public class UsageSession {
    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 64)
    private String scenario;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    private OffsetDateTime endedAt;

    private OffsetDateTime lastHeartbeatAt;

    @Column(nullable = false)
    private Integer durationSeconds;

    @Column(nullable = false)
    private Integer chargedMinutes;

    @Column(nullable = false)
    private Integer chargedSeconds;

    @PrePersist
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
