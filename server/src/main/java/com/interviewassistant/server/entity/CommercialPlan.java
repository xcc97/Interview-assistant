package com.interviewassistant.server.entity;


import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class CommercialPlan {
        private String id;

        private String code;

        private String name;

        private Integer totalMinutes;

        private Integer validDays;

        private BigDecimal price;

        private String description;

        private Boolean featured;

        private String status;

        private OffsetDateTime createdAt;

        public void prePersist() {
        if (id == null || id.isBlank()) {
            id = "plan-" + UUID.randomUUID();
        }
        if (featured == null) {
            featured = false;
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getTotalMinutes() {
        return totalMinutes;
    }

    public void setTotalMinutes(Integer totalMinutes) {
        this.totalMinutes = totalMinutes;
    }

    public Integer getValidDays() {
        return validDays;
    }

    public void setValidDays(Integer validDays) {
        this.validDays = validDays;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getFeatured() {
        return featured;
    }

    public void setFeatured(Boolean featured) {
        this.featured = featured;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
