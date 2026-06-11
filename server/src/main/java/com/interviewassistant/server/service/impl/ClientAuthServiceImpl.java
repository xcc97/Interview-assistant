package com.interviewassistant.server.service.impl;

import com.interviewassistant.server.config.AssistantProperties;
import com.interviewassistant.server.service.ClientAuthService;
import org.springframework.stereotype.Service;

@Service
public class ClientAuthServiceImpl implements ClientAuthService {
    private final AssistantProperties assistantProperties;

    public ClientAuthServiceImpl(AssistantProperties assistantProperties) {
        this.assistantProperties = assistantProperties;
    }

    @Override
    public void verify(String providedSecret) {
        String expectedSecret = assistantProperties.getClientSecret();
        if (expectedSecret == null || expectedSecret.isBlank()) {
            throw new IllegalStateException("服务端未配置客户端密钥");
        }
        if (providedSecret == null || !expectedSecret.equals(providedSecret)) {
            throw new SecurityException("客户端密钥校验失败");
        }
    }
}
