package com.interviewassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewassistant.model.InterviewAnalysis;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class BackendClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AppConfig config;
    private final OkHttpClient httpClient;

    public BackendClient(AppConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(45))
                .build();
    }

    public AsrCredential fetchAsrCredential() throws IOException {
        Request request = authorizedRequest(config.getBackendBaseUrl() + "/api/client/asr/token")
                .post(RequestBody.create("{}", JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("后端获取 ASR Token 失败: HTTP " + response.code() + " " + body);
            }
            JsonNode root = OBJECT_MAPPER.readTree(body);
            return new AsrCredential(
                    root.path("appKey").asText(""),
                    root.path("token").asText(""),
                    root.path("endpoint").asText(""),
                    root.path("expireTime").asLong(0L)
            );
        }
    }

    public InterviewAnalysis analyzeQuestion(String question, String resumeText) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("question", question);
        payload.put("resumeText", resumeText == null ? "" : resumeText);

        String body = OBJECT_MAPPER.writeValueAsString(payload);
        Request request = authorizedRequest(config.getBackendBaseUrl() + "/api/client/interview/analyze")
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("后端生成回答失败: HTTP " + response.code() + " " + responseBody);
            }
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            String answer = root.path("answer").asText("").trim();
            if (answer.isEmpty()) {
                throw new IOException("后端返回回答为空");
            }
            return new InterviewAnalysis("", "", answer);
        }
    }

    private Request.Builder authorizedRequest(String url) throws IOException {
        String clientSecret = config.getBackendClientSecret();
        if (clientSecret.isEmpty()) {
            throw new IOException("未配置后端客户端密钥，请设置 INTERVIEW_ASSISTANT_CLIENT_SECRET 或 application.properties 里的 backend.clientSecret");
        }
        return new Request.Builder()
                .url(url)
                .addHeader("X-Client-Secret", clientSecret)
                .addHeader("Content-Type", "application/json");
    }

    public static final class AsrCredential {
        private final String appKey;
        private final String token;
        private final String endpoint;
        private final long expireTime;

        public AsrCredential(String appKey, String token, String endpoint, long expireTime) {
            this.appKey = appKey == null ? "" : appKey.trim();
            this.token = token == null ? "" : token.trim();
            this.endpoint = endpoint == null ? "" : endpoint.trim();
            this.expireTime = expireTime;
        }

        public String getAppKey() {
            return appKey;
        }

        public String getToken() {
            return token;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public long getExpireTime() {
            return expireTime;
        }
    }
}
