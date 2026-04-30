package com.interviewassistant.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final String DEFAULT_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-v3";
    private static final int DEFAULT_MIN_TEXT_LENGTH = 6;

    private final Properties properties = new Properties();

    public AppConfig() {
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Keep defaults and environment variables.
        }
    }

    public String getApiKey() {
        String fromEnv = System.getenv("BAILIAN_API_KEY");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        return properties.getProperty("bailian.apiKey", "").trim();
    }

    public String getApiEndpoint() {
        String endpoint = properties.getProperty("bailian.endpoint", DEFAULT_ENDPOINT).trim();
        return endpoint.isEmpty() ? DEFAULT_ENDPOINT : endpoint;
    }

    public String getModel() {
        String model = properties.getProperty("bailian.model", DEFAULT_MODEL).trim();
        return model.isEmpty() ? DEFAULT_MODEL : model;
    }

    public String getVoskModelPath() {
        String fromEnv = System.getenv("VOSK_MODEL_PATH");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        return properties.getProperty("asr.voskModelPath", "").trim();
    }

    public int getMinAutoAnalyzeLength() {
        String value = properties.getProperty("asr.minAutoAnalyzeLength", String.valueOf(DEFAULT_MIN_TEXT_LENGTH)).trim();
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : DEFAULT_MIN_TEXT_LENGTH;
        } catch (NumberFormatException ex) {
            return DEFAULT_MIN_TEXT_LENGTH;
        }
    }

    public String getAsrMixerName() {
        String fromEnv = System.getenv("ASR_MIXER_NAME");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        return properties.getProperty("asr.mixerName", "").trim();
    }
}
