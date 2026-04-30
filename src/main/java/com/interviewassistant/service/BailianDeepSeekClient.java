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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BailianDeepSeekClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AppConfig config;
    private final OkHttpClient httpClient;

    public BailianDeepSeekClient(AppConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(45))
                .build();
    }

    public InterviewAnalysis analyzeQuestion(String transcript, String resumeText) throws IOException {
        String apiKey = config.getApiKey();
        if (apiKey.trim().isEmpty()) {
            throw new IOException("未检测到 API Key，请在环境变量 BAILIAN_API_KEY 或 application.properties 中配置。");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getModel());
        payload.put("temperature", 0.2);
        payload.put("messages", Arrays.asList(
                message("system", buildSystemPrompt()),
                message("user", buildUserPrompt(transcript, resumeText))
        ));

        String body = OBJECT_MAPPER.writeValueAsString(payload);
        Request request = new Request.Builder()
                .url(config.getApiEndpoint())
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String errorBody = response.body() == null ? "" : response.body().string();
                throw new IOException("百炼请求失败: HTTP " + response.code() + " " + errorBody);
            }
            String raw = response.body().string();
            return parseResponse(raw);
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildSystemPrompt() {
        return "你是远程面试中的实时答题助手。你必须输出结构化内容，帮助候选人在30秒内组织回答。\n"
                + "输出要求:\n"
                + "1) 提问意图: 一句话\n"
                + "2) 回答要点: 3-5条，简洁\n"
                + "3) 参考回答: 120-220字，口语化，第一人称\n"
                + "4) 若用户简历能关联，请优先引用简历经历和项目细节";
    }

    private String buildUserPrompt(String transcript, String resumeText) {
        String normalizedResume = (resumeText == null || resumeText.trim().isEmpty()) ? "暂无简历内容" : resumeText;
        if (normalizedResume.length() > 6000) {
            normalizedResume = normalizedResume.substring(0, 6000);
        }
        return String.format("【面试官问题转写】\n%s\n\n【候选人简历文本】\n%s\n", transcript, normalizedResume);
    }

    private InterviewAnalysis parseResponse(String responseJson) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(responseJson);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.trim().isEmpty()) {
            throw new IOException("模型返回为空，请检查模型名称或接口地址。");
        }
        return parseStructuredContent(content);
    }

    private InterviewAnalysis parseStructuredContent(String content) {
        String intent = section(content, "提问意图");
        String points = section(content, "回答要点");
        String answer = section(content, "参考回答");

        if (intent.trim().isEmpty()) {
            intent = "请查看完整输出";
        }
        if (points.trim().isEmpty()) {
            points = content;
        }
        if (answer.trim().isEmpty()) {
            answer = content;
        }
        return new InterviewAnalysis(intent, points, answer);
    }

    private String section(String text, String title) {
        int idx = text.indexOf(title);
        if (idx < 0) {
            return "";
        }
        int start = text.indexOf(":", idx);
        if (start < 0) {
            start = text.indexOf("：", idx);
        }
        if (start < 0) {
            return "";
        }
        int end = text.length();
        String[] headers = {"提问意图", "回答要点", "参考回答"};
        for (String header : headers) {
            if (header.equals(title)) {
                continue;
            }
            int nextIdx = text.indexOf(header, start + 1);
            if (nextIdx > start && nextIdx < end) {
                end = nextIdx;
            }
        }
        return text.substring(start + 1, end).trim();
    }
}
