package com.interviewassistant.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class InterviewAssistantServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(InterviewAssistantServerApplication.class, args);
    }
}
