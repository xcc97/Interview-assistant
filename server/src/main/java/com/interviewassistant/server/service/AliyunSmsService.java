package com.interviewassistant.server.service;

public interface AliyunSmsService {
    void sendRegisterCode(String phone, String code);

    boolean isConfigured();

    boolean isDebugEnabled();
}
