package com.interviewassistant.service.audio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ExternalPcmProcessCaptureProvider implements PcmAudioCaptureProvider {
    private static final int BUFFER_SIZE = 4096;

    private final File executable;
    private final String displayName;
    private volatile Process process;
    private volatile boolean running;

    public ExternalPcmProcessCaptureProvider(File executable, String displayName) {
        this.executable = executable;
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return displayName;
    }

    @Override
    public boolean isAvailable() {
        return executable != null && executable.exists() && executable.isFile();
    }

    @Override
    public void start(Listener listener) throws Exception {
        if (!isAvailable()) {
            throw new IOException("系统音频采集组件不存在: " + (executable == null ? "<null>" : executable.getAbsolutePath()));
        }

        List<String> command = new ArrayList<String>();
        command.add(executable.getAbsolutePath());
        command.add("--format");
        command.add("pcm_s16le");
        command.add("--sample-rate");
        command.add("16000");
        command.add("--channels");
        command.add("1");
        if (isWindowsWasapiHelper()) {
            command.add("--role");
            command.add(resolveWasapiRole());
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = builder.start();
        running = true;
        listener.onStatus("正在使用系统音频采集组件: " + displayName);

        InputStream input = process.getInputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running) {
            int read = input.read(buffer);
            if (read < 0) {
                break;
            }
            if (read > 0) {
                listener.onFrame(buffer, read);
            }
        }

        int exitCode = process.waitFor();
        if (running && exitCode != 0) {
            throw new IOException("系统音频采集组件异常退出，exitCode=" + exitCode);
        }
    }

    private boolean isWindowsWasapiHelper() {
        return executable != null && executable.getName().toLowerCase().contains("wasapi");
    }

    private String resolveWasapiRole() {
        String role = System.getProperty("interviewassistant.wasapi.role", "");
        if (role == null || role.trim().isEmpty()) {
            role = System.getenv("INTERVIEW_ASSISTANT_WASAPI_ROLE");
        }
        if (role == null || role.trim().isEmpty()) {
            return "console";
        }
        String normalized = role.trim().toLowerCase();
        if ("communications".equals(normalized) || "communication".equals(normalized) || "comm".equals(normalized)) {
            return "communications";
        }
        if ("multimedia".equals(normalized) || "media".equals(normalized)) {
            return "multimedia";
        }
        return "console";
    }

    @Override
    public void stop() {
        running = false;
        Process current = process;
        if (current != null) {
            current.destroy();
            try {
                if (current.isAlive()) {
                    current.destroyForcibly();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
