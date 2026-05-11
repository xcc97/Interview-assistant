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
    private volatile String accessToken;

    public BackendClient(AppConfig config) {
        this.config = config;
        this.accessToken = config.getBackendAccessToken();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(45))
                .build();
    }

    public synchronized String loginIfNeeded() throws IOException {
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            return accessToken.trim();
        }
        String phone = config.getBackendPhone();
        String password = config.getBackendPassword();
        if (phone.isEmpty() || password.isEmpty()) {
            throw new IOException("请先在客户端登录账号");
        }
        return login(phone, password).getAccessToken();
    }

    public synchronized AuthResult login(String phone, String password) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("phone", phone == null ? "" : phone.trim());
        payload.put("password", password == null ? "" : password.trim());
        String body = OBJECT_MAPPER.writeValueAsString(payload);
        Request request = new Request.Builder()
                .url(config.getBackendBaseUrl() + "/api/auth/login")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("登录失败: HTTP " + response.code() + " " + responseBody);
            }
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            String token = root.path("accessToken").asText("").trim();
            if (token.isEmpty()) {
                throw new IOException("登录成功但未返回 accessToken");
            }
            accessToken = token;
            return new AuthResult(token, parseUserProfile(root.path("user")));
        }
    }

    public UserProfile fetchCurrentUser() throws IOException {
        Request request = authorizedRequest(config.getBackendBaseUrl() + "/api/auth/me")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("获取用户信息失败: HTTP " + response.code() + " " + responseBody);
            }
            return parseUserProfile(OBJECT_MAPPER.readTree(responseBody));
        }
    }

    public synchronized void logout() {
        accessToken = "";
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

    public UsageSession startUsageSession(String scenario) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scenario", scenario == null || scenario.trim().isEmpty() ? "DESKTOP_INTERVIEW_ASSIST" : scenario.trim());
        String body = OBJECT_MAPPER.writeValueAsString(payload);
        Request request = authorizedRequest(config.getBackendBaseUrl() + "/api/usage/start")
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("创建使用会话失败: HTTP " + response.code() + " " + responseBody);
            }
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            return parseUsageSession(root);
        }
    }

    public UsageSession finishUsageSession(String sessionId) throws IOException {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IOException("sessionId 不能为空");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId.trim());
        String body = OBJECT_MAPPER.writeValueAsString(payload);
        Request request = authorizedRequest(config.getBackendBaseUrl() + "/api/usage/finish")
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("结束使用会话失败: HTTP " + response.code() + " " + responseBody);
            }
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            return parseUsageSession(root);
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

    public void createInterviewRecord(String usageSessionId, String question, String answer) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("usageSessionId", usageSessionId == null ? "" : usageSessionId.trim());
        payload.put("question", question == null ? "" : question.trim());
        payload.put("answer", answer == null ? "" : answer.trim());
        String body = OBJECT_MAPPER.writeValueAsString(payload);
        Request request = authorizedRequest(config.getBackendBaseUrl() + "/api/client/interview/records")
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("保存面试记录失败: HTTP " + response.code() + " " + responseBody);
            }
        }
    }

    private Request.Builder authorizedRequest(String url) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json");

        String token = loginIfNeeded();
        if (!token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }

        String clientSecret = config.getBackendClientSecret();
        if (!clientSecret.isEmpty()) {
            builder.addHeader("X-Client-Secret", clientSecret);
        }
        return builder;
    }

    private UsageSession parseUsageSession(JsonNode root) {
        return new UsageSession(
                root.path("sessionId").asText(""),
                root.path("scenario").asText(""),
                root.path("status").asText(""),
                root.path("startedAt").asText(""),
                root.path("endedAt").asText(""),
                root.path("durationSeconds").asInt(0),
                root.path("chargedMinutes").asInt(0),
                root.path("chargedSeconds").asInt(root.path("chargedMinutes").asInt(0) * 60)
        );
    }

    private UserProfile parseUserProfile(JsonNode root) {
        return new UserProfile(
                root.path("userId").asText(""),
                root.path("phone").asText(""),
                root.path("nickname").asText(""),
                root.path("role").asText(""),
                root.path("currentPlanName").asText("暂无套餐"),
                root.path("remainingMinutes").asInt(0),
                root.path("remainingSeconds").asInt(root.path("remainingMinutes").asInt(0) * 60),
                root.path("usedMinutes").asInt(0),
                root.path("usedSeconds").asInt(root.path("usedMinutes").asInt(0) * 60),
                root.path("expiryTime").asText("")
        );
    }

    public static final class AuthResult {
        private final String accessToken;
        private final UserProfile userProfile;

        public AuthResult(String accessToken, UserProfile userProfile) {
            this.accessToken = accessToken == null ? "" : accessToken.trim();
            this.userProfile = userProfile;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public UserProfile getUserProfile() {
            return userProfile;
        }
    }

    public static final class UserProfile {
        private final String userId;
        private final String phone;
        private final String nickname;
        private final String role;
        private final String currentPlanName;
        private final int remainingMinutes;
        private final int remainingSeconds;
        private final int usedMinutes;
        private final int usedSeconds;
        private final String expiryTime;

        public UserProfile(String userId, String phone, String nickname, String role, String currentPlanName,
                           int remainingMinutes, int remainingSeconds, int usedMinutes, int usedSeconds, String expiryTime) {
            this.userId = userId == null ? "" : userId.trim();
            this.phone = phone == null ? "" : phone.trim();
            this.nickname = nickname == null ? "" : nickname.trim();
            this.role = role == null ? "" : role.trim();
            this.currentPlanName = currentPlanName == null ? "" : currentPlanName.trim();
            this.remainingMinutes = remainingMinutes;
            this.remainingSeconds = remainingSeconds;
            this.usedMinutes = usedMinutes;
            this.usedSeconds = usedSeconds;
            this.expiryTime = expiryTime == null ? "" : expiryTime.trim();
        }

        public String getUserId() { return userId; }
        public String getPhone() { return phone; }
        public String getNickname() { return nickname; }
        public String getRole() { return role; }
        public String getCurrentPlanName() { return currentPlanName; }
        public int getRemainingMinutes() { return remainingMinutes; }
        public int getRemainingSeconds() { return remainingSeconds; }
        public int getUsedMinutes() { return usedMinutes; }
        public int getUsedSeconds() { return usedSeconds; }
        public String getExpiryTime() { return expiryTime; }
    }

    public static final class UsageSession {
        private final String sessionId;
        private final String scenario;
        private final String status;
        private final String startedAt;
        private final String endedAt;
        private final int durationSeconds;
        private final int chargedMinutes;
        private final int chargedSeconds;

        public UsageSession(String sessionId, String scenario, String status, String startedAt,
                            String endedAt, int durationSeconds, int chargedMinutes, int chargedSeconds) {
            this.sessionId = sessionId == null ? "" : sessionId.trim();
            this.scenario = scenario == null ? "" : scenario.trim();
            this.status = status == null ? "" : status.trim();
            this.startedAt = startedAt == null ? "" : startedAt.trim();
            this.endedAt = endedAt == null ? "" : endedAt.trim();
            this.durationSeconds = durationSeconds;
            this.chargedMinutes = chargedMinutes;
            this.chargedSeconds = chargedSeconds;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getScenario() {
            return scenario;
        }

        public String getStatus() {
            return status;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public String getEndedAt() {
            return endedAt;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public int getChargedMinutes() {
            return chargedMinutes;
        }

        public int getChargedSeconds() {
            return chargedSeconds;
        }
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
