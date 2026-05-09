package com.interviewassistant.server.dto;

import jakarta.validation.constraints.Size;

public class AdminGrantOrderRequest {
    @Size(max = 128, message = "交易号最多 128 个字符")
    private String transactionId;

    @Size(max = 255, message = "备注最多 255 个字符")
    private String note;

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
