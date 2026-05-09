package com.interviewassistant.server.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateOrderRequest {
    @NotBlank(message = "planId 不能为空")
    private String planId;

    private String paymentChannel;

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }
}
