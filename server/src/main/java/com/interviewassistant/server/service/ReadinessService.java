package com.interviewassistant.server.service;

import com.interviewassistant.server.config.AssistantProperties;
import com.interviewassistant.server.dto.ReadinessCheckItem;
import com.interviewassistant.server.dto.ReadinessResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReadinessService {
    private static final String PASS = "PASS";
    private static final String WARN = "WARN";
    private static final String FAIL = "FAIL";

    private final AssistantProperties properties;

    public ReadinessService(AssistantProperties properties) {
        this.properties = properties;
    }

    public ReadinessResponse check() {
        List<ReadinessCheckItem> checks = new ArrayList<>();
        checks.add(checkClientSecret());
        checks.add(checkJwtSecret());
        checks.add(checkBailian());
        checks.add(checkAliyun());
        checks.add(checkPaymentBaseUrl());
        checks.add(checkMockPayment());
        checks.add(checkWechatPayment());
        checks.add(checkAlipayPayment());
        checks.add(checkAtLeastOneRealPayment());

        int passed = (int) checks.stream().filter(item -> PASS.equals(item.getStatus())).count();
        int warning = (int) checks.stream().filter(item -> WARN.equals(item.getStatus())).count();
        int failed = (int) checks.stream().filter(item -> FAIL.equals(item.getStatus())).count();
        String status = failed > 0 ? "BLOCKED" : warning > 0 ? "NEEDS_ATTENTION" : "READY";
        return new ReadinessResponse(status, passed, warning, failed, checks);
    }

    private ReadinessCheckItem checkClientSecret() {
        String secret = safe(properties.getClientSecret());
        if (secret.isBlank() || "change-me-before-production".equals(secret)) {
            return fail("clientSecret", "客户端密钥", "仍在使用默认客户端密钥，上线前必须替换为强随机值。");
        }
        if (secret.length() < 24) {
            return warn("clientSecret", "客户端密钥", "客户端密钥偏短，建议至少 24 位随机字符。");
        }
        return pass("clientSecret", "客户端密钥", "已配置。");
    }

    private ReadinessCheckItem checkJwtSecret() {
        String secret = safe(properties.getJwtSecret());
        if (secret.isBlank() || "change-this-jwt-secret-to-a-long-random-string-before-production".equals(secret)) {
            return fail("jwtSecret", "JWT 密钥", "仍在使用默认 JWT 密钥，上线前必须替换。");
        }
        if (secret.length() < 32) {
            return warn("jwtSecret", "JWT 密钥", "JWT 密钥偏短，建议至少 32 位随机字符。");
        }
        return pass("jwtSecret", "JWT 密钥", "已配置。");
    }

    private ReadinessCheckItem checkBailian() {
        AssistantProperties.Bailian bailian = properties.getBailian();
        if (bailian.getApiKey().isBlank()) {
            return fail("bailian", "AI 模型服务", "BAILIAN_API_KEY 未配置，AI 回答建议将不可用。");
        }
        if (bailian.getEndpoint().isBlank()) {
            return fail("bailian", "AI 模型服务", "BAILIAN_ENDPOINT 未配置。");
        }
        return pass("bailian", "AI 模型服务", "已配置模型和接口地址。");
    }

    private ReadinessCheckItem checkAliyun() {
        AssistantProperties.Aliyun aliyun = properties.getAliyun();
        if (aliyun.getNlsAppKey().isBlank() || aliyun.getAccessKeyId().isBlank() || aliyun.getAccessKeySecret().isBlank()) {
            return warn("aliyunAsr", "实时语音识别", "阿里云 ASR 配置不完整，实时语音能力可能不可用。");
        }
        return pass("aliyunAsr", "实时语音识别", "已配置。");
    }

    private ReadinessCheckItem checkPaymentBaseUrl() {
        String baseUrl = properties.getPayment().getPublicBaseUrl();
        if (baseUrl.isBlank()) {
            return fail("paymentBaseUrl", "支付回调域名", "PAYMENT_PUBLIC_BASE_URL 未配置。");
        }
        if (baseUrl.startsWith("http://localhost") || baseUrl.startsWith("http://127.0.0.1")) {
            return warn("paymentBaseUrl", "支付回调域名", "当前为本地地址，正式支付回调需要公网 HTTPS 域名。");
        }
        if (!baseUrl.startsWith("https://")) {
            return warn("paymentBaseUrl", "支付回调域名", "建议使用 HTTPS 域名作为支付回调地址。");
        }
        return pass("paymentBaseUrl", "支付回调域名", "已配置公网 HTTPS 域名。");
    }

    private ReadinessCheckItem checkMockPayment() {
        if (properties.getPayment().isMockPaymentEnabled()) {
            return fail("mockPayment", "模拟支付开关", "PAYMENT_MOCK_ENABLED 已开启，生产环境必须关闭。");
        }
        return pass("mockPayment", "模拟支付开关", "已关闭。");
    }

    private ReadinessCheckItem checkWechatPayment() {
        AssistantProperties.Payment.Wechat wechat = properties.getPayment().getWechat();
        if (!wechat.isEnabled()) {
            return warn("wechatPay", "微信支付", "微信支付未启用；如果只支持支付宝或人工支付，可以忽略。");
        }
        List<String> missing = new ArrayList<>();
        addMissing(missing, "WECHAT_PAY_APP_ID", wechat.getAppId());
        addMissing(missing, "WECHAT_PAY_MCH_ID", wechat.getMchId());
        addMissing(missing, "WECHAT_PAY_API_V3_KEY", wechat.getApiV3Key());
        addMissing(missing, "WECHAT_PAY_PRIVATE_KEY_PATH", wechat.getPrivateKeyPath());
        addMissing(missing, "WECHAT_PAY_PRIVATE_CERT_PATH", wechat.getPrivateCertPath());
        addMissing(missing, "WECHAT_PAY_CERT_SERIAL_NO", wechat.getCertSerialNo());
        if (wechat.isCallbackSignatureRequired()) {
            addMissing(missing, "WECHAT_PAY_PLATFORM_CERT_PATH", wechat.getPlatformCertPath());
        }
        if (!missing.isEmpty()) {
            return fail("wechatPay", "微信支付", "微信支付已启用，但缺少配置：" + String.join("、", missing));
        }
        return pass("wechatPay", "微信支付", "已启用并完成基础配置。");
    }

    private ReadinessCheckItem checkAlipayPayment() {
        AssistantProperties.Payment.Alipay alipay = properties.getPayment().getAlipay();
        if (!alipay.isEnabled()) {
            return warn("alipay", "支付宝", "支付宝未启用；如果只支持微信或人工支付，可以忽略。");
        }
        List<String> missing = new ArrayList<>();
        addMissing(missing, "ALIPAY_APP_ID", alipay.getAppId());
        addMissing(missing, "ALIPAY_PRIVATE_KEY", alipay.getPrivateKey());
        addMissing(missing, "ALIPAY_PUBLIC_KEY", alipay.getAlipayPublicKey());
        if (!missing.isEmpty()) {
            return fail("alipay", "支付宝", "支付宝已启用，但缺少配置：" + String.join("、", missing));
        }
        return pass("alipay", "支付宝", "已启用并完成基础配置。");
    }

    private ReadinessCheckItem checkAtLeastOneRealPayment() {
        boolean wechatEnabled = properties.getPayment().getWechat().isEnabled();
        boolean alipayEnabled = properties.getPayment().getAlipay().isEnabled();
        if (!wechatEnabled && !alipayEnabled) {
            return fail("realPayment", "正式收款通道", "微信支付和支付宝至少需要启用一个，否则商品上线后用户无法自助付款。");
        }
        if (!wechatEnabled || !alipayEnabled) {
            return warn("realPayment", "正式收款通道", "建议微信支付和支付宝同时启用，以覆盖更多用户付款场景。");
        }
        return pass("realPayment", "正式收款通道", "微信支付和支付宝均已启用。");
    }

    private void addMissing(List<String> missing, String key, String value) {
        if (value == null || value.isBlank()) {
            missing.add(key);
        }
    }

    private ReadinessCheckItem pass(String key, String label, String message) {
        return new ReadinessCheckItem(key, label, PASS, message);
    }

    private ReadinessCheckItem warn(String key, String label, String message) {
        return new ReadinessCheckItem(key, label, WARN, message);
    }

    private ReadinessCheckItem fail(String key, String label, String message) {
        return new ReadinessCheckItem(key, label, FAIL, message);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
