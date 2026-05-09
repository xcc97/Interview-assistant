package com.interviewassistant.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AdminCloseOrderRequest {
    @NotBlank(message = "关闭原因不能为空")
    @Size(max = 255, message = "关闭原因最多 255 个字符")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
