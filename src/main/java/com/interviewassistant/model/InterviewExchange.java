package com.interviewassistant.model;

public class InterviewExchange {
    private final int sequence;
    private final String question;
    private final String questionIntent;
    private final String keyPoints;
    private final String suggestedAnswer;

    public InterviewExchange(int sequence, String question, String questionIntent, String keyPoints, String suggestedAnswer) {
        this.sequence = sequence;
        this.question = question;
        this.questionIntent = questionIntent;
        this.keyPoints = keyPoints;
        this.suggestedAnswer = suggestedAnswer;
    }

    public int getSequence() {
        return sequence;
    }

    public String getQuestion() {
        return question;
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
