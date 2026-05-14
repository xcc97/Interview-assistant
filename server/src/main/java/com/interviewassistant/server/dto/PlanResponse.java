package com.interviewassistant.server.dto;

import java.math.BigDecimal;

public class PlanResponse {
    private String planId;
    private String name;
    private int totalMinutes;
    private int validDays;
    private BigDecimal price;
    private String description;
    private boolean recommended;

    public PlanResponse() {
    }

    public PlanResponse(String planId, String name, int totalMinutes, int validDays,
                        BigDecimal price, String description, boolean recommended) {
        this.planId = planId;
        this.name = name;
        this.totalMinutes = totalMinutes;
        this.validDays = validDays;
        this.price = price;
        this.description = description;
        this.recommended = recommended;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }

    public void setTotalMinutes(int totalMinutes) {
        this.totalMinutes = totalMinutes;
    }

    public int getValidDays() {
        return validDays;
    }

    public void setValidDays(int validDays) {
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

    public boolean isRecommended() {
        return recommended;
    }

    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }
}
