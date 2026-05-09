package com.interviewassistant.server.dto;

import jakarta.validation.constraints.NotBlank;

public class CreatePaymentRequest {
    @NotBlank(message = "orderId 不能为空")
    private String orderId;

    @NotBlank(message = "paymentChannel 不能为空")
    private String paymentChannel;

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
}
