package com.interviewassistant.server.dto;

import java.math.BigDecimal;

public class PaymentNotifyResult {
    private final String orderId;
    private final String paymentChannel;
    private final BigDecimal paidAmount;
    private final String transactionId;

    public PaymentNotifyResult(String orderId, String paymentChannel, BigDecimal paidAmount, String transactionId) {
        this.orderId = orderId;
        this.paymentChannel = paymentChannel;
        this.paidAmount = paidAmount;
        this.transactionId = transactionId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
