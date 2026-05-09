package com.interviewassistant.server.dto;

public class PaymentCreateResponse {
    private String orderId;
    private String paymentChannel;
    private String paymentType;
    private String codeUrl;
    private String paymentForm;
    private String message;

    public PaymentCreateResponse() {
    }

    public PaymentCreateResponse(String orderId, String paymentChannel, String paymentType,
                                 String codeUrl, String paymentForm, String message) {
        this.orderId = orderId;
        this.paymentChannel = paymentChannel;
        this.paymentType = paymentType;
        this.codeUrl = codeUrl;
        this.paymentForm = paymentForm;
        this.message = message;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public String getCodeUrl() {
        return codeUrl;
    }

    public void setCodeUrl(String codeUrl) {
        this.codeUrl = codeUrl;
    }

    public String getPaymentForm() {
        return paymentForm;
    }

    public void setPaymentForm(String paymentForm) {
        this.paymentForm = paymentForm;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
