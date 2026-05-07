package com.interviewassistant.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewassistant.server.config.AssistantProperties;
import com.interviewassistant.server.dto.AnalyzeRequest;
import com.interviewassistant.server.dto.AnalyzeResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
public class BailianAnswerService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AssistantProperties properties;
    private final HttpClient httpClient;

    public BailianAnswerService(AssistantProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public AnalyzeResponse analyze(AnalyzeRequest request) throws IOException {
        AssistantProperties.Bailian bailian = properties.getBailian();
        if (bailian.getApiKey().isEmpty()) {
            throw new IllegalStateException("服务端未配置 BAILIAN_API_KEY");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", bailian.getModel());
        payload.put("temperature", 0.2);
        payload.put("messages", Arrays.asList(
                message("system", buildSystemPrompt()),
                message("user", buildUserPrompt(request.getQuestion(), request.getResumeText()))
        ));

        String requestBody = OBJECT_MAPPER.writeValueAsString(payload);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(bailian.getEndpoint()))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + bailian.getApiKey())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        String raw;
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            raw = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("百炼请求失败: HTTP " + response.statusCode() + " " + raw);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("百炼请求被中断", ex);
        }

        String answer = parseAnswer(raw);
        return new AnalyzeResponse(answer);
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

    private String buildUserPrompt(String question, String resumeText) {
        String normalizedResume = resumeText == null || resumeText.trim().isEmpty() ? "暂无简历内容" : resumeText.trim();
        if (normalizedResume.length() > 6000) {
            normalizedResume = normalizedResume.substring(0, 6000);
        }
        return String.format("【面试官问题转写】\n%s\n\n【候选人简历文本】\n%s\n", question, normalizedResume);
    }

    private String parseAnswer(String responseJson) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(responseJson == null ? "{}" : responseJson);
        String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
        if (content.isEmpty()) {
            throw new IOException("模型返回为空，请检查模型名称或接口地址");
        }
        return cleanupAnswer(content);
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
}
