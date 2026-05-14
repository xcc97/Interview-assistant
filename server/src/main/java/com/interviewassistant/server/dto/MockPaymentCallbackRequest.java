package com.interviewassistant.server.dto;

import jakarta.validation.constraints.NotBlank;

public class MockPaymentCallbackRequest {
    @NotBlank(message = "orderId 不能为空")
    private String orderId;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
