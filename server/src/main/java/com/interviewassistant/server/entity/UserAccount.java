package com.interviewassistant.server.entity;


import java.time.OffsetDateTime;
import java.util.UUID;

public class UserAccount {
        private String id;

        private String phone;

        private String passwordHash;

        private String nickname;

        private String status;

        private String role;

        private OffsetDateTime createdAt;

        public void prePersist() {
        if (id == null || id.isBlank()) {
            id = "user-" + UUID.randomUUID();
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (role == null || role.isBlank()) {
            role = "USER";
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
