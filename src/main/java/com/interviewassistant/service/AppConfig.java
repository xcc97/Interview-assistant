package com.interviewassistant.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
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
        String rawPath;
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            rawPath = fromEnv.trim();
        } else {
            rawPath = properties.getProperty("asr.voskModelPath", "").trim();
        }
        return resolvePath(rawPath);
    }

    private String resolvePath(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return "";
        }

        File[] candidates = new File[]{
                new File(rawPath),
                new File(System.getProperty("user.dir", "."), rawPath),
                new File(getJarDirectory(), rawPath)
        };

        for (int i = 0; i < candidates.length; i++) {
            File candidate = canonicalize(candidates[i]);
            if (candidate.exists()) {
                return candidate.getAbsolutePath();
            }
        }

        String resourcePath = rawPath.replace('\\', '/');
        URL resourceUrl = AppConfig.class.getClassLoader().getResource(resourcePath);
        if (resourceUrl != null) {
            try {
                return canonicalize(new File(resourceUrl.toURI())).getAbsolutePath();
            } catch (Exception ignored) {
                return canonicalize(new File(resourceUrl.getPath())).getAbsolutePath();
            }
        }

        return canonicalize(new File(rawPath)).getAbsolutePath();
    }

    private File getJarDirectory() {
        try {
            URL location = AppConfig.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                File base = new File(location.toURI());
                if (base.isFile()) {
                    File parent = base.getParentFile();
                    if (parent != null) {
                        return parent;
                    }
                }
                return base;
            }
        } catch (URISyntaxException ignored) {
        }
        return new File(System.getProperty("user.dir", "."));
    }

    private File canonicalize(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException ignored) {
            return file.getAbsoluteFile();
        }
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
        sb.append("app.config.model = ").append(properties.getProperty("asr.voskModelPath", "")).append('\n');
        sb.append("env.VOSK_MODEL_PATH = ").append(safeEnv("VOSK_MODEL_PATH")).append('\n');
        sb.append("resolved.voskModelPath = ").append(getVoskModelPath()).append('\n');
        sb.append("resolved.asrMixerName = ").append(getAsrMixerName()).append('\n');
        return sb.toString();
    }

    private String safeEnv(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }
}
