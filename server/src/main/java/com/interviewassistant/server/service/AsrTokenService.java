package com.interviewassistant.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewassistant.server.config.AssistantProperties;
import com.interviewassistant.server.dto.AsrTokenResponse;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class AsrTokenService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NLS_META_DOMAIN = "nls-meta.cn-shanghai.aliyuncs.com";
    private static final String NLS_META_REGION = "cn-shanghai";
    private static final String NLS_META_VERSION = "2019-02-28";

    private final AssistantProperties properties;
    private final HttpClient httpClient;

    public AsrTokenService(AssistantProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public AsrTokenResponse createToken() throws Exception {
        AssistantProperties.Aliyun aliyun = properties.getAliyun();
        if (aliyun.getNlsAppKey().isEmpty()) {
            throw new IllegalStateException("服务端未配置 ALIYUN_NLS_APP_KEY");
        }
        if (aliyun.getAccessKeyId().isEmpty() || aliyun.getAccessKeySecret().isEmpty()) {
            throw new IllegalStateException("服务端未配置 ALIYUN_ACCESS_KEY_ID / ALIYUN_ACCESS_KEY_SECRET");
        }

        TokenResult tokenResult = requestNlsToken(aliyun.getAccessKeyId(), aliyun.getAccessKeySecret());

        AsrTokenResponse response = new AsrTokenResponse();
        response.setAppKey(aliyun.getNlsAppKey());
        response.setEndpoint(aliyun.getNlsEndpoint());
        response.setToken(tokenResult.token);
        response.setExpireTime(tokenResult.expireTime);
        return response;
    }

    private TokenResult requestNlsToken(String accessKeyId, String accessKeySecret) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("AccessKeyId", accessKeyId);
        params.put("Action", "CreateToken");
        params.put("Format", "JSON");
        params.put("RegionId", NLS_META_REGION);
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("Timestamp", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now()));
        params.put("Version", NLS_META_VERSION);

        String canonicalizedQuery = canonicalizedQuery(params);
        String stringToSign = "GET&" + percentEncode("/") + "&" + percentEncode(canonicalizedQuery);
        String signature = sign(stringToSign, accessKeySecret + "&");
        String url = "http://" + NLS_META_DOMAIN + "/?Signature=" + percentEncode(signature) + "&" + canonicalizedQuery;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("获取阿里云 NLS Token 被中断", ex);
        }

        String body = response.body() == null ? "" : response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("获取阿里云 NLS Token 失败: HTTP " + response.statusCode() + " " + body);
        }

        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode tokenNode = root.path("Token");
        String token = tokenNode.path("Id").asText("").trim();
        long expireTime = tokenNode.path("ExpireTime").asLong(0L);
        if (token.isEmpty()) {
            throw new IOException("获取阿里云 NLS Token 失败，响应中没有 Token.Id: " + body);
        }
        return new TokenResult(token, expireTime);
    }

    private String canonicalizedQuery(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(percentEncode(entry.getKey())).append('=').append(percentEncode(entry.getValue()));
        }
        return builder.toString();
    }

    private String percentEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private String sign(String content, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static final class TokenResult {
        private final String token;
        private final long expireTime;

        private TokenResult(String token, long expireTime) {
            this.token = token;
            this.expireTime = expireTime;
        }
    }
}
