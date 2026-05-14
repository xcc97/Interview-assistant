package com.interviewassistant.server.dto;

import jakarta.validation.constraints.NotBlank;

public class AnalyzeRequest {
    @NotBlank(message = "question 不能为空")
    private String question;
    private String resumeText;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getResumeText() {
        return resumeText;
    }

    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }
}
