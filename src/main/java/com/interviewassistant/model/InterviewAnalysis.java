package com.interviewassistant.model;

public class InterviewAnalysis {
    private final String questionIntent;
    private final String keyPoints;
    private final String suggestedAnswer;

    public InterviewAnalysis(String questionIntent, String keyPoints, String suggestedAnswer) {
        this.questionIntent = questionIntent;
        this.keyPoints = keyPoints;
        this.suggestedAnswer = suggestedAnswer;
    }

    public String getQuestionIntent() {
        return questionIntent;
    }

    public String getKeyPoints() {
        return keyPoints;
    }

    public String getSuggestedAnswer() {
        return suggestedAnswer;
    }
}
