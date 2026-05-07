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
    private final BackendClient backendClient;
    private final OkHttpClient httpClient;

    public BailianDeepSeekClient(AppConfig config) {
        this.config = config;
        this.backendClient = new BackendClient(config);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(45))
                .build();
    }

    public InterviewAnalysis analyzeQuestion(String transcript, String resumeText) throws IOException {
        if (config.isBackendEnabled()) {
            return backendClient.analyzeQuestion(transcript, resumeText);
        }
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
        return "你是远程面试中的实时答题助手。你的目标是生成候选人可以直接照着说的口语化回答。\n"
                + "回答风格要求:\n"
                + "1) 只输出最终回答，不要标题、不要编号、不要分点、不要解释你的思路。\n"
                + "2) 使用第一人称，像真实候选人在面试中自然表达。\n"
                + "3) 内容简洁，控制在80-180字之间，优先两到三小段。\n"
                + "4) 如果问题适合结合简历，就自然带到相关项目或经历；不要生硬堆简历。\n"
                + "5) 不要使用“首先、其次、最后”这种模板化表达，避免 AI 味。\n"
                + "6) 如果问题很宽泛，直接给一个稳妥、可信、可落地的回答。";
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
        String answer = content == null ? "" : content.trim();
        String structuredAnswer = section(answer, "参考回答");
        if (!structuredAnswer.trim().isEmpty()) {
            answer = structuredAnswer.trim();
        }
        answer = cleanupAnswer(answer);
        return new InterviewAnalysis("", "", answer);
    }

    private String cleanupAnswer(String answer) {
        String cleaned = answer == null ? "" : answer.trim();
        String[] prefixes = {"参考回答：", "参考回答:", "回答：", "回答:", "最终回答：", "最终回答:"};
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : prefixes) {
                if (cleaned.startsWith(prefix)) {
                    cleaned = cleaned.substring(prefix.length()).trim();
                    changed = true;
                }
            }
        }
        return cleaned;
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
