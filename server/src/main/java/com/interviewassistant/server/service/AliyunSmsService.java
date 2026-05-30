package com.interviewassistant.server.service;

import com.interviewassistant.server.config.AssistantProperties;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AliyunSmsService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AssistantProperties assistantProperties;
    private final Map<String, String> debugCodes = new ConcurrentHashMap<>();

    public AliyunSmsService(AssistantProperties assistantProperties) {
        this.assistantProperties = assistantProperties;
    }

    public String sendRegisterCode(String phone) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        debugCodes.put(phone, code);
        if (hasConfig()) {
            // Real Aliyun SMS implementation should be added here once credentials are available.
            // This placeholder keeps the flow working without breaking the application.
        }
        return code;
    }

    public boolean verifyDebugCode(String phone, String code) {
        return code != null && code.equals(debugCodes.get(phone));
    }

    private boolean hasConfig() {
        return !assistantProperties.getAliyun().getAccessKeyId().isBlank()
            && !assistantProperties.getAliyun().getAccessKeySecret().isBlank()
            && !assistantProperties.getAliyun().getSmsSignName().isBlank()
            && !assistantProperties.getAliyun().getSmsRegisterTemplateCode().isBlank();
    }
}
