package com.interviewassistant.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewassistant.server.config.AssistantProperties;
import com.interviewassistant.server.dto.PaymentCreateResponse;
import com.interviewassistant.server.dto.PaymentNotifyResult;
import com.interviewassistant.server.entity.CommercialOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {
    private static final String WECHAT_NATIVE_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/native";
    private static final String ALIPAY_SIGN_TYPE = "RSA2";

    private final AssistantProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public PaymentService(AssistantProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PaymentCreateResponse createPayment(CommercialOrder order) throws Exception {
        if (properties.getPayment().isMockPaymentEnabled()) {
            return createMockPayment(order);
        }
        if ("WECHAT".equals(order.getPaymentChannel())) {
            return createWechatNativePayment(order);
        }
        if ("ALIPAY".equals(order.getPaymentChannel())) {
            return createAlipayPagePayment(order);
        }
        throw new IllegalArgumentException("暂不支持该支付方式");
    }

    public PaymentNotifyResult parseWechatPaidNotify(String notifyBody, Map<String, String> headers) throws Exception {
        if (properties.getPayment().isMockPaymentEnabled()) {
            return new PaymentNotifyResult(parseMockOrderId(notifyBody), "WECHAT", null, null);
        }
        AssistantProperties.Payment.Wechat wechat = properties.getPayment().getWechat();
        ensureWechatEnabled(wechat);
        verifyWechatCallbackSignature(notifyBody, headers, wechat);
        Map<String, Object> notify = objectMapper.readValue(notifyBody, new TypeReference<>() { });
        Object resource = notify.get("resource");
        if (!(resource instanceof Map<?, ?> resourceMap)) {
            throw new IllegalArgumentException("微信支付回调缺少 resource");
        }
        String plaintext = decryptWechatResource(resourceMap);
        Map<String, Object> transaction = objectMapper.readValue(plaintext, new TypeReference<>() { });
        String tradeState = String.valueOf(transaction.getOrDefault("trade_state", ""));
        if (!"SUCCESS".equals(tradeState)) {
            throw new IllegalStateException("微信支付交易未成功：" + tradeState);
        }
        return new PaymentNotifyResult(
            String.valueOf(transaction.get("out_trade_no")),
            "WECHAT",
            parseWechatPaidAmount(transaction),
            String.valueOf(transaction.getOrDefault("transaction_id", ""))
        );
    }

    public PaymentNotifyResult parseAlipayPaidNotify(Map<String, String> params) throws Exception {
        if (properties.getPayment().isMockPaymentEnabled()) {
            return parseMockAlipayPaidNotify(params);
        }
        AssistantProperties.Payment.Alipay alipay = properties.getPayment().getAlipay();
        ensureAlipayEnabled(alipay);
        if (!verifyAlipaySign(params, alipay.getAlipayPublicKey())) {
            throw new SecurityException("支付宝回调验签失败");
        }
        return parseMockAlipayPaidNotify(params);
    }

    private PaymentCreateResponse createMockPayment(CommercialOrder order) {
        String payUrl = properties.getPayment().getPublicBaseUrl()
            + "/mock-pay?orderId=" + urlEncode(order.getId())
            + "&channel=" + urlEncode(order.getPaymentChannel())
            + "&amount=" + urlEncode(order.getAmount().toPlainString());
        return new PaymentCreateResponse(order.getId(), order.getPaymentChannel(), "MOCK", payUrl, null, "开发模式：点击模拟支付完成订单");
    }

    private PaymentCreateResponse createWechatNativePayment(CommercialOrder order) throws Exception {
        AssistantProperties.Payment.Wechat wechat = properties.getPayment().getWechat();
        ensureWechatEnabled(wechat);

        Map<String, Object> amount = new HashMap<>();
        amount.put("total", toCents(order.getAmount()));
        amount.put("currency", "CNY");

        Map<String, Object> body = new HashMap<>();
        body.put("appid", wechat.getAppId());
        body.put("mchid", wechat.getMchId());
        body.put("description", order.getPlanName());
        body.put("out_trade_no", order.getId());
        body.put("notify_url", properties.getPayment().getPublicBaseUrl() + wechat.getNotifyPath());
        body.put("amount", amount);

        String requestBody = objectMapper.writeValueAsString(body);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.set("Authorization", buildWechatAuthorization("POST", "/v3/pay/transactions/native", requestBody, wechat));

        Map<String, Object> response = restTemplate.postForObject(WECHAT_NATIVE_URL, new HttpEntity<>(requestBody, headers), Map.class);
        String codeUrl = response == null ? null : String.valueOf(response.get("code_url"));
        if (codeUrl == null || codeUrl.isBlank() || "null".equals(codeUrl)) {
            throw new IllegalStateException("微信支付未返回二维码链接");
        }
        return new PaymentCreateResponse(order.getId(), "WECHAT", "NATIVE_QR", codeUrl, null, "请使用微信扫码完成支付");
    }

    private PaymentCreateResponse createAlipayPagePayment(CommercialOrder order) throws Exception {
        AssistantProperties.Payment.Alipay alipay = properties.getPayment().getAlipay();
        ensureAlipayEnabled(alipay);
        Map<String, String> params = new HashMap<>();
        params.put("app_id", alipay.getAppId());
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", ALIPAY_SIGN_TYPE);
        params.put("timestamp", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.of("Asia/Shanghai")).format(Instant.now()));
        params.put("version", "1.0");
        params.put("notify_url", properties.getPayment().getPublicBaseUrl() + alipay.getNotifyPath());
        if (!alipay.getReturnUrl().isBlank()) {
            params.put("return_url", alipay.getReturnUrl());
        }
        Map<String, String> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", order.getId());
        bizContent.put("total_amount", order.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        bizContent.put("subject", order.getPlanName());
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        params.put("biz_content", objectMapper.writeValueAsString(bizContent));
        params.put("sign", signAlipay(params, alipay.getPrivateKey()));
        return new PaymentCreateResponse(order.getId(), "ALIPAY", "PAGE_FORM", null, buildAutoSubmitForm(alipay.getGatewayUrl(), params), "请在支付宝页面完成支付");
    }

    private String buildWechatAuthorization(String method, String canonicalUrl, String body, AssistantProperties.Payment.Wechat wechat) throws Exception {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String message = method + "\n" + canonicalUrl + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = signRsaSha256(message, loadPrivateKey(wechat.getPrivateKeyPath()));
        return "WECHATPAY2-SHA256-RSA2048 mchid=\"" + wechat.getMchId()
            + "\",nonce_str=\"" + nonce
            + "\",timestamp=\"" + timestamp
            + "\",serial_no=\"" + wechat.getCertSerialNo()
            + "\",signature=\"" + signature + "\"";
    }

    private void verifyWechatCallbackSignature(String body, Map<String, String> headers, AssistantProperties.Payment.Wechat wechat) throws Exception {
        if (!wechat.isCallbackSignatureRequired()) {
            return;
        }
        addMissingWechatCallbackHeader(headers, "wechatpay-timestamp");
        addMissingWechatCallbackHeader(headers, "wechatpay-nonce");
        addMissingWechatCallbackHeader(headers, "wechatpay-signature");
        addMissingWechatCallbackHeader(headers, "wechatpay-serial");
        if (wechat.getPlatformCertPath().isBlank()) {
            throw new SecurityException("微信支付回调验签失败：缺少 WECHAT_PAY_PLATFORM_CERT_PATH");
        }
        X509Certificate certificate = loadCertificate(wechat.getPlatformCertPath());
        String callbackSerial = headers.get("wechatpay-serial");
        String certSerial = certificate.getSerialNumber().toString(16).toUpperCase();
        if (!certSerial.equalsIgnoreCase(callbackSerial)) {
            throw new SecurityException("微信支付回调验签失败：平台证书序列号不匹配");
        }
        String message = headers.get("wechatpay-timestamp") + "\n" + headers.get("wechatpay-nonce") + "\n" + body + "\n";
        if (!verifyRsaSha256(message, headers.get("wechatpay-signature"), certificate.getPublicKey())) {
            throw new SecurityException("微信支付回调验签失败：签名无效");
        }
    }

    private void addMissingWechatCallbackHeader(Map<String, String> headers, String name) {
        if (headers == null || headers.get(name) == null || headers.get(name).isBlank()) {
            throw new SecurityException("微信支付回调验签失败：缺少请求头 " + name);
        }
    }

    private X509Certificate loadCertificate(String certificatePath) throws Exception {
        try (var input = Files.newInputStream(Path.of(certificatePath))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    private String decryptWechatResource(Map<?, ?> resource) throws Exception {
        String ciphertext = String.valueOf(resource.get("ciphertext"));
        String nonce = String.valueOf(resource.get("nonce"));
        Object associatedDataValue = resource.get("associated_data");
        String associatedData = associatedDataValue == null ? "" : String.valueOf(associatedDataValue);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
            new SecretKeySpec(properties.getPayment().getWechat().getApiV3Key().getBytes(StandardCharsets.UTF_8), "AES"),
            new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
    }

    private String signAlipay(Map<String, String> params, String privateKey) throws Exception {
        return signRsaSha256(canonicalAlipayParams(params), loadPrivateKeyFromText(privateKey));
    }

    private boolean verifyAlipaySign(Map<String, String> params, String publicKey) throws Exception {
        String sign = params.get("sign");
        if (sign == null || sign.isBlank()) {
            return false;
        }
        Map<String, String> unsignedParams = new HashMap<>(params);
        unsignedParams.remove("sign");
        unsignedParams.remove("sign_type");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.X509EncodedKeySpec(decodePem(publicKey))));
        signature.update(canonicalAlipayParams(unsignedParams).getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.getDecoder().decode(sign));
    }

    private String canonicalAlipayParams(Map<String, String> params) {
        return params.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
    }

    private PrivateKey loadPrivateKey(String privateKeyPath) throws Exception {
        return loadPrivateKeyFromText(Files.readString(Path.of(privateKeyPath), StandardCharsets.UTF_8));
    }

    private PrivateKey loadPrivateKeyFromText(String privateKeyText) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decodePem(privateKeyText)));
    }

    private byte[] decodePem(String pem) {
        return Base64.getDecoder().decode(pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", ""));
    }

    private String signRsaSha256(String content, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private boolean verifyRsaSha256(String content, String base64Signature, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(content.getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.getDecoder().decode(base64Signature));
    }

    private String buildAutoSubmitForm(String gatewayUrl, Map<String, String> params) {
        MultiValueMap<String, String> sortedParams = new LinkedMultiValueMap<>();
        params.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(entry -> sortedParams.add(entry.getKey(), entry.getValue()));
        StringBuilder form = new StringBuilder("<form id=\"alipay_submit\" name=\"alipay_submit\" method=\"post\" action=\"")
            .append(escapeHtml(gatewayUrl)).append("\">");
        sortedParams.forEach((key, values) -> values.forEach(value -> form.append("<input type=\"hidden\" name=\"")
            .append(escapeHtml(key)).append("\" value=\"").append(escapeHtml(value)).append("\"/>")));
        form.append("<input type=\"submit\" value=\"立即支付\" style=\"display:none\"/>")
            .append("</form><script>document.forms['alipay_submit'].submit();</script>");
        return form.toString();
    }

    private PaymentNotifyResult parseMockAlipayPaidNotify(Map<String, String> params) {
        String status = params.getOrDefault("trade_status", "TRADE_SUCCESS");
        if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) {
            throw new IllegalStateException("支付宝交易未成功");
        }
        BigDecimal paidAmount = params.containsKey("total_amount") ? new BigDecimal(params.get("total_amount")) : null;
        return new PaymentNotifyResult(params.get("out_trade_no"), "ALIPAY", paidAmount, params.getOrDefault("trade_no", ""));
    }

    private BigDecimal parseWechatPaidAmount(Map<String, Object> transaction) {
        Object amount = transaction.get("amount");
        if (!(amount instanceof Map<?, ?> amountMap) || amountMap.get("total") == null) {
            return null;
        }
        return new BigDecimal(String.valueOf(amountMap.get("total"))).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureWechatEnabled(AssistantProperties.Payment.Wechat wechat) {
        List<String> missing = new ArrayList<>();
        addMissing(missing, "WECHAT_PAY_APP_ID", wechat.getAppId());
        addMissing(missing, "WECHAT_PAY_MCH_ID", wechat.getMchId());
        addMissing(missing, "WECHAT_PAY_API_V3_KEY", wechat.getApiV3Key());
        addMissing(missing, "WECHAT_PAY_PRIVATE_KEY_PATH", wechat.getPrivateKeyPath());
        addMissing(missing, "WECHAT_PAY_CERT_SERIAL_NO", wechat.getCertSerialNo());
        if (!wechat.isEnabled() || !missing.isEmpty()) {
            throw new IllegalStateException("微信支付尚未完成配置" + (missing.isEmpty() ? "" : "：" + String.join("、", missing)));
        }
    }

    private void ensureAlipayEnabled(AssistantProperties.Payment.Alipay alipay) {
        List<String> missing = new ArrayList<>();
        addMissing(missing, "ALIPAY_APP_ID", alipay.getAppId());
        addMissing(missing, "ALIPAY_PRIVATE_KEY", alipay.getPrivateKey());
        addMissing(missing, "ALIPAY_PUBLIC_KEY", alipay.getAlipayPublicKey());
        if (!alipay.isEnabled() || !missing.isEmpty()) {
            throw new IllegalStateException("支付宝尚未完成配置" + (missing.isEmpty() ? "" : "：" + String.join("、", missing)));
        }
    }

    private void addMissing(List<String> missing, String key, String value) {
        if (value == null || value.isBlank()) {
            missing.add(key);
        }
    }

    private String parseMockOrderId(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("回调内容为空");
        }
        int index = body.indexOf("orderId");
        if (index < 0) {
            index = body.indexOf("out_trade_no");
        }
        if (index < 0) {
            return body.trim();
        }
        int colon = body.indexOf(':', index);
        int equals = body.indexOf('=', index);
        int splitIndex = colon >= 0 && (equals < 0 || colon < equals) ? colon : equals;
        if (splitIndex < 0) {
            return body.trim();
        }
        String value = body.substring(splitIndex + 1).replaceAll("[{}\\\"' ]", "").split("[,&]", 2)[0];
        return value.trim();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private int toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
