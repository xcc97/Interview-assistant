package com.interviewassistant.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "interview-assistant")
public class AssistantProperties {
    private String clientSecret = "change-me-before-production";
    private final Aliyun aliyun = new Aliyun();
    private final Bailian bailian = new Bailian();

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Aliyun getAliyun() {
        return aliyun;
    }

    public Bailian getBailian() {
        return bailian;
    }

    public static class Aliyun {
        private String nlsAppKey = "";
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private String nlsEndpoint = "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1";

        public String getNlsAppKey() {
            return nlsAppKey == null ? "" : nlsAppKey.trim();
        }

        public void setNlsAppKey(String nlsAppKey) {
            this.nlsAppKey = nlsAppKey;
        }

        public String getAccessKeyId() {
            return accessKeyId == null ? "" : accessKeyId.trim();
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret == null ? "" : accessKeySecret.trim();
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getNlsEndpoint() {
            return nlsEndpoint == null ? "" : nlsEndpoint.trim();
        }

        public void setNlsEndpoint(String nlsEndpoint) {
            this.nlsEndpoint = nlsEndpoint;
        }
    }

    public static class Bailian {
        private String apiKey = "";
        private String model = "deepseek-v3";
        private String endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

        public String getApiKey() {
            return apiKey == null ? "" : apiKey.trim();
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model == null || model.trim().isEmpty() ? "deepseek-v3" : model.trim();
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getEndpoint() {
            return endpoint == null ? "" : endpoint.trim();
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }
}
