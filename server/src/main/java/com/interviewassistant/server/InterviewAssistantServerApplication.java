package com.interviewassistant.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.interviewassistant.server.mapper")
@SpringBootApplication
public class InterviewAssistantServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(InterviewAssistantServerApplication.class, args);
    }
}
