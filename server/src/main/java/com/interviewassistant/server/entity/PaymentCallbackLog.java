package com.interviewassistant.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_callback_logs", indexes = {
    @Index(name = "idx_payment_callback_order", columnList = "orderId, createdAt"),
    @Index(name = "idx_payment_callback_status_created", columnList = "status, createdAt"),
    @Index(name = "idx_payment_callback_channel_transaction", columnList = "paymentChannel, transactionId")
})
public class PaymentCallbackLog {
    @Id
    private String id;

    @Column(nullable = false, length = 32)
    private String paymentChannel;

    @Column(length = 64)
    private String orderId;

    @Column(length = 128)
    private String transactionId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String errorMessage;

    @Lob
    @Column(nullable = false)
    private String requestBody;

    @Lob
    private String requestHeaders;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = "PCL-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
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

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
