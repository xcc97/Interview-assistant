package com.interviewassistant.server.dto;

public class PaymentCallbackLogResponse {
    private String id;
    private String paymentChannel;
    private String orderId;
    private String transactionId;
    private String status;
    private String errorMessage;
    private String requestBody;
    private String requestHeaders;
    private String createdAt;

    public PaymentCallbackLogResponse(String id, String paymentChannel, String orderId, String transactionId,
                                      String status, String errorMessage, String requestBody,
                                      String requestHeaders, String createdAt) {
        this.id = id;
        this.paymentChannel = paymentChannel;
        this.orderId = orderId;
        this.transactionId = transactionId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.requestBody = requestBody;
        this.requestHeaders = requestHeaders;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
