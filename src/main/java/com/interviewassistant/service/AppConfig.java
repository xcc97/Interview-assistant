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

    public String getAliyunAsrAppKey() {
        String fromEnv = System.getenv("ALIYUN_ASR_APP_KEY");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        return properties.getProperty("asr.aliyun.appKey", "").trim();
    }

    public String getAliyunAsrAccessKeyId() {
        String fromEnv = System.getenv("ALIYUN_ACCESS_KEY_ID");
        if (fromEnv == null || fromEnv.trim().isEmpty()) {
            fromEnv = System.getenv("ALIYUN_ASR_ACCESS_KEY_ID");
        }
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        String value = properties.getProperty("asr.aliyun.accessKeyId", "").trim();
        if (value.isEmpty()) {
            value = properties.getProperty("aliyun.accessKeyId", "").trim();
        }
        return value;
    }

    public String getAliyunAsrAccessKeySecret() {
        String fromEnv = System.getenv("ALIYUN_ACCESS_KEY_SECRET");
        if (fromEnv == null || fromEnv.trim().isEmpty()) {
            fromEnv = System.getenv("ALIYUN_ASR_ACCESS_KEY_SECRET");
        }
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        String value = properties.getProperty("asr.aliyun.accessKeySecret", "").trim();
        if (value.isEmpty()) {
            value = properties.getProperty("aliyun.accessKeySecret", "").trim();
        }
        return value;
    }

    public String getAliyunAsrToken() {
        String fromEnv = System.getenv("ALIYUN_ASR_TOKEN");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        return properties.getProperty("asr.aliyun.token", "").trim();
    }

    public String getAliyunAsrEndpoint() {
        String fromEnv = System.getenv("ALIYUN_ASR_ENDPOINT");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        return properties.getProperty("asr.aliyun.endpoint", "").trim();
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

    public String buildStartupDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("[启动诊断]\n");
        sb.append("os.name = ").append(System.getProperty("os.name", "")).append('\n');
        sb.append("os.arch = ").append(System.getProperty("os.arch", "")).append('\n');
        sb.append("java.version = ").append(System.getProperty("java.version", "")).append('\n');
        sb.append("user.dir = ").append(System.getProperty("user.dir", "")).append('\n');
        sb.append("app.config.aliyunAppKey = ").append(mask(getAliyunAsrAppKey())).append('\n');
        sb.append("env.ALIYUN_ASR_APP_KEY = ").append(mask(safeEnv("ALIYUN_ASR_APP_KEY"))).append('\n');
        sb.append("resolved.aliyunAccessKeyId = ").append(mask(getAliyunAsrAccessKeyId())).append('\n');
        sb.append("env.ALIYUN_ACCESS_KEY_ID = ").append(mask(safeEnv("ALIYUN_ACCESS_KEY_ID"))).append('\n');
        sb.append("resolved.aliyunToken = ").append(mask(getAliyunAsrToken())).append('\n');
        sb.append("resolved.aliyunEndpoint = ").append(getAliyunAsrEndpoint()).append('\n');
        sb.append("resolved.asrMixerName = ").append(getAsrMixerName()).append('\n');
        return sb.toString();
    }

    private String safeEnv(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private String mask(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
