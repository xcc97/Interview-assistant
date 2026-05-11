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
        payload.put("temperature", 0.35);
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
