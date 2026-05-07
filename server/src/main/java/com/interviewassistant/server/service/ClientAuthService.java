package com.interviewassistant.server.service;

import com.interviewassistant.server.config.AssistantProperties;
import org.springframework.stereotype.Service;

@Service
public class ClientAuthService {
    private final AssistantProperties properties;

    public ClientAuthService(AssistantProperties properties) {
        this.properties = properties;
    }

    public void verify(String providedSecret) {
        String expected = properties.getClientSecret();
        if (expected == null || expected.trim().isEmpty() || "change-me-before-production".equals(expected.trim())) {
            throw new IllegalStateException("服务端尚未配置 INTERVIEW_ASSISTANT_CLIENT_SECRET");
        }
        if (providedSecret == null || !expected.trim().equals(providedSecret.trim())) {
            throw new SecurityException("客户端授权失败");
        }
    }
}
