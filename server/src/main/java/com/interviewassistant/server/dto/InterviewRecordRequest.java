package com.interviewassistant.server.dto;

import jakarta.validation.constraints.NotBlank;

public class InterviewRecordRequest {
    private String usageSessionId;

    @NotBlank(message = "面试官问题不能为空")
    private String question;

    @NotBlank(message = "生成答案不能为空")
    private String answer;

    public String getUsageSessionId() {
        return usageSessionId;
    }

    public void setUsageSessionId(String usageSessionId) {
        this.usageSessionId = usageSessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
