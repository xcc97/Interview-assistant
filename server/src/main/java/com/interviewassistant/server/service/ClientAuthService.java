package com.interviewassistant.server.service;

public interface ClientAuthService {
    void verify(String providedSecret);
}
