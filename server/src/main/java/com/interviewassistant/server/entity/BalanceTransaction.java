package com.interviewassistant.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "balance_transactions",
    indexes = {
        @Index(name = "idx_balance_user_created", columnList = "userId, createdAt"),
        @Index(name = "idx_balance_source", columnList = "sourceType, sourceId, type")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_balance_source_type", columnNames = {"sourceType", "sourceId", "type"})
    })
public class BalanceTransaction {
    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private Integer minutes;

    @Column(nullable = false)
    private Integer seconds;

    @Column(nullable = false, length = 32)
    private String sourceType;

    @Column(nullable = false)
    private String sourceId;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = "BAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (seconds == null) {
            seconds = minutes == null ? 0 : minutes * 60;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getMinutes() {
        return minutes;
    }

    public void setMinutes(Integer minutes) {
        this.minutes = minutes;
        this.seconds = minutes == null ? null : minutes * 60;
    }

    public Integer getSeconds() {
        return seconds;
    }

    public void setSeconds(Integer seconds) {
        this.seconds = seconds;
        this.minutes = seconds == null ? null : (int) Math.ceil(seconds / 60.0);
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
