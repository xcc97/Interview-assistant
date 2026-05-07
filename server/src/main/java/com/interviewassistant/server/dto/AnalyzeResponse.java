package com.interviewassistant.server.dto;

public class AnalyzeResponse {
    private String answer;

    public AnalyzeResponse() {
    }

    public AnalyzeResponse(String answer) {
        this.answer = answer;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
