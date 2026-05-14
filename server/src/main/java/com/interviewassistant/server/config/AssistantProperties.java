package com.interviewassistant.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "interview-assistant")
public class AssistantProperties {
    private String clientSecret = "change-me-before-production";
    private String jwtSecret = "change-this-jwt-secret-to-a-long-random-string-before-production";
    private long jwtExpireSeconds = 7200;
    private String adminPhones = "";
    private final Aliyun aliyun = new Aliyun();
    private final Bailian bailian = new Bailian();
    private final Payment payment = new Payment();

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpireSeconds() {
        return jwtExpireSeconds;
    }

    public void setJwtExpireSeconds(long jwtExpireSeconds) {
        this.jwtExpireSeconds = jwtExpireSeconds;
    }

    public String getAdminPhones() {
        return adminPhones == null ? "" : adminPhones.trim();
    }

    public void setAdminPhones(String adminPhones) {
        this.adminPhones = adminPhones;
    }

    public Aliyun getAliyun() {
        return aliyun;
    }

    public Bailian getBailian() {
        return bailian;
    }

    public Payment getPayment() {
        return payment;
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

    public static class Payment {
        private String publicBaseUrl = "http://localhost:8080";
        private boolean mockPaymentEnabled;
        private int orderTimeoutMinutes = 30;
        private final Wechat wechat = new Wechat();
        private final Alipay alipay = new Alipay();

        public String getPublicBaseUrl() {
            return publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public boolean isMockPaymentEnabled() {
            return mockPaymentEnabled;
        }

        public void setMockPaymentEnabled(boolean mockPaymentEnabled) {
            this.mockPaymentEnabled = mockPaymentEnabled;
        }

        public int getOrderTimeoutMinutes() {
            return Math.max(1, orderTimeoutMinutes);
        }

        public void setOrderTimeoutMinutes(int orderTimeoutMinutes) {
            this.orderTimeoutMinutes = orderTimeoutMinutes;
        }

        public Wechat getWechat() {
            return wechat;
        }

        public Alipay getAlipay() {
            return alipay;
        }

        public static class Wechat {
            private boolean enabled;
            private String appId = "";
            private String mchId = "";
            private String apiV3Key = "";
            private String privateKeyPath = "";
            private String privateCertPath = "";
            private String certSerialNo = "";
            private String platformCertPath = "";
            private boolean callbackSignatureRequired = true;
            private String notifyPath = "/api/payment/wechat/notify";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getAppId() {
                return appId == null ? "" : appId.trim();
            }

            public void setAppId(String appId) {
                this.appId = appId;
            }

            public String getMchId() {
                return mchId == null ? "" : mchId.trim();
            }

            public void setMchId(String mchId) {
                this.mchId = mchId;
            }

            public String getApiV3Key() {
                return apiV3Key == null ? "" : apiV3Key.trim();
            }

            public void setApiV3Key(String apiV3Key) {
                this.apiV3Key = apiV3Key;
            }

            public String getPrivateKeyPath() {
                return privateKeyPath == null ? "" : privateKeyPath.trim();
            }

            public void setPrivateKeyPath(String privateKeyPath) {
                this.privateKeyPath = privateKeyPath;
            }

            public String getPrivateCertPath() {
                return privateCertPath == null ? "" : privateCertPath.trim();
            }

            public void setPrivateCertPath(String privateCertPath) {
                this.privateCertPath = privateCertPath;
            }

            public String getCertSerialNo() {
                return certSerialNo == null ? "" : certSerialNo.trim();
            }

            public void setCertSerialNo(String certSerialNo) {
                this.certSerialNo = certSerialNo;
            }

            public String getPlatformCertPath() {
                return platformCertPath == null ? "" : platformCertPath.trim();
            }

            public void setPlatformCertPath(String platformCertPath) {
                this.platformCertPath = platformCertPath;
            }

            public boolean isCallbackSignatureRequired() {
                return callbackSignatureRequired;
            }

            public void setCallbackSignatureRequired(boolean callbackSignatureRequired) {
                this.callbackSignatureRequired = callbackSignatureRequired;
            }

            public String getNotifyPath() {
                return notifyPath == null || notifyPath.trim().isEmpty() ? "/api/payment/wechat/notify" : notifyPath.trim();
            }

            public void setNotifyPath(String notifyPath) {
                this.notifyPath = notifyPath;
            }
        }

        public static class Alipay {
            private boolean enabled;
            private String appId = "";
            private String privateKey = "";
            private String alipayPublicKey = "";
            private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
            private String notifyPath = "/api/payment/alipay/notify";
            private String returnUrl = "";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getAppId() {
                return appId == null ? "" : appId.trim();
            }

            public void setAppId(String appId) {
                this.appId = appId;
            }

            public String getPrivateKey() {
                return privateKey == null ? "" : privateKey.trim();
            }

            public void setPrivateKey(String privateKey) {
                this.privateKey = privateKey;
            }

            public String getAlipayPublicKey() {
                return alipayPublicKey == null ? "" : alipayPublicKey.trim();
            }

            public void setAlipayPublicKey(String alipayPublicKey) {
                this.alipayPublicKey = alipayPublicKey;
            }

            public String getGatewayUrl() {
                return gatewayUrl == null || gatewayUrl.trim().isEmpty() ? "https://openapi.alipay.com/gateway.do" : gatewayUrl.trim();
            }

            public void setGatewayUrl(String gatewayUrl) {
                this.gatewayUrl = gatewayUrl;
            }

            public String getNotifyPath() {
                return notifyPath == null || notifyPath.trim().isEmpty() ? "/api/payment/alipay/notify" : notifyPath.trim();
            }

            public void setNotifyPath(String notifyPath) {
                this.notifyPath = notifyPath;
            }

            public String getReturnUrl() {
                return returnUrl == null ? "" : returnUrl.trim();
            }

            public void setReturnUrl(String returnUrl) {
                this.returnUrl = returnUrl;
            }
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
