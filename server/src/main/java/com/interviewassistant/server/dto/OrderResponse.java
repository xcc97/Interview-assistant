package com.interviewassistant.server.dto;

import java.math.BigDecimal;

public class OrderResponse {
    private String orderId;
    private String planId;
    private String planName;
    private BigDecimal amount;
    private int grantedMinutes;
    private String status;
    private String paymentChannel;
    private String paymentTransactionId;
    private String paidAt;
    private String closedAt;
    private String closeReason;
    private String createdAt;

    public OrderResponse() {
    }

    public OrderResponse(String orderId, String planId, String planName, BigDecimal amount,
                         int grantedMinutes, String status, String paymentChannel, String paymentTransactionId,
                         String paidAt, String closedAt, String closeReason, String createdAt) {
        this.orderId = orderId;
        this.planId = planId;
        this.planName = planName;
        this.amount = amount;
        this.grantedMinutes = grantedMinutes;
        this.status = status;
        this.paymentChannel = paymentChannel;
        this.paymentTransactionId = paymentTransactionId;
        this.paidAt = paidAt;
        this.closedAt = closedAt;
        this.closeReason = closeReason;
        this.createdAt = createdAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public int getGrantedMinutes() {
        return grantedMinutes;
    }

    public void setGrantedMinutes(int grantedMinutes) {
        this.grantedMinutes = grantedMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(String paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public String getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(String paidAt) {
        this.paidAt = paidAt;
    }

    public String getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(String closedAt) {
        this.closedAt = closedAt;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
