package com.interviewassistant.server.service;

public interface SmsVerificationService {
    String sendCode(String phone);

    void verifyCode(String phone, String code);
}
