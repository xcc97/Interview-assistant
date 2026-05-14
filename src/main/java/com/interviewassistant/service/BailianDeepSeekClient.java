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
        this(config, new BackendClient(config));
    }

    public BailianDeepSeekClient(AppConfig config, BackendClient backendClient) {
        this.config = config;
        this.backendClient = backendClient;
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
        payload.put("temperature", 0.35);
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
        return "你是远程面试中的实时答题助手。你的目标是生成候选人可以在面试现场直接照着读的回答，要求自然、可信、口语化，不像背稿，也不要有 AI 味。\n"
                + "回答风格要求:\n"
                + "1) 只输出最终回答正文，不要输出“参考回答”“回答如下”等标题，不要解释你的思路。\n"
                + "2) 使用第一人称，多用“我当时”“我一般会”“我的理解是”“这个地方我会先”等自然表达，让候选人照着念也像本人在回答。\n"
                + "3) 回答要比普通摘要更详细，优先控制在220-450字；复杂问题可以到600字，但不要啰嗦。\n"
                + "4) 如果问题涉及方案、步骤、优缺点、排查思路、项目经历、技术原理，必须分段换行，并用“第一，”“第二，”“第三，”或短横线分条表达，方便一眼照读。\n"
                + "5) 每一条都要是完整口语句子，不要只给关键词；每段尽量控制在2-4句话。\n"
                + "6) 如果能结合简历，就自然嵌入候选人的项目、职责、技术栈和结果；不要编造简历里没有的公司、项目名或夸张数据。\n"
                + "7) 避免过度书面化、官话和模板话；可以有轻微停顿感，比如“这个问题我会这样看”“落到项目里，我一般是这么做的”。\n"
                + "8) 如果问题很宽泛，先给一句总观点，再分2-4条展开，最后用一句自然收尾。\n"
                + "9) 如果问题是追问或很短，要根据上下文给出稳妥回答，不要反问面试官。\n"
                + "10) 输出排版必须清晰：段落之间空一行；有多个方面时逐条换行。";
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
