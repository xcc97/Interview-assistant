package com.interviewassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewassistant.service.audio.PcmAudioCaptureProvider;
import com.interviewassistant.service.audio.SystemAudioCaptureProviderFactory;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeechListenerService {
    private static final float SAMPLE_RATE = 16000.0F;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public interface Callback {
        void onStatus(String text);

        void onPartial(String text);

        void onFinalSentence(String text);

        void onError(String text);
    }

    private final AppConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private PcmAudioCaptureProvider captureProvider;

    public SpeechListenerService(AppConfig config) {
        this.config = config;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start(Callback callback) {
        start(callback, config.getAsrMixerName());
    }

    public void start(Callback callback, String mixerName) {
        if (running.get()) {
            callback.onStatus("监听已在运行");
            return;
        }
        String modelPath = config.getVoskModelPath();
        if (modelPath.isEmpty()) {
            callback.onError("未配置 VOSK 模型路径，请设置 VOSK_MODEL_PATH 或 application.properties 里的 asr.voskModelPath");
            return;
        }
        File modelDir = resolveModelDirectory(modelPath);
        if (modelDir == null) {
            callback.onError("VOSK 模型目录不存在或不完整: " + modelPath + "。请确保你指向的是解压后的模型根目录，而不是压缩包路径，也不是项目根目录。");
            return;
        }
        callback.onStatus("模型目录已解析为: " + modelDir.getAbsolutePath());

        running.set(true);
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                doListen(modelDir, callback, mixerName);
            }
        }, "speech-listener");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running.set(false);
        if (captureProvider != null) {
            captureProvider.stop();
        }
    }

    private void doListen(File modelDir, Callback callback, String mixerName) {
        callback.onStatus("正在加载语音模型...");
        System.out.println("[VOSK-DIAG] user.dir=" + System.getProperty("user.dir", ""));
        System.out.println("[VOSK-DIAG] os.name=" + System.getProperty("os.name", ""));
        System.out.println("[VOSK-DIAG] modelDir=" + modelDir.getAbsolutePath());
        System.out.println("[VOSK-DIAG] modelDir children=" + describeDirectory(modelDir));
        System.out.println("[AUDIO-CAPTURE] requestedMixer=" + mixerName);
        try {
            File runtimeModelDir = prepareRuntimeModelDirectory(modelDir);
            File stagedModelDir = stageModelToAsciiPath(runtimeModelDir);
            callback.onStatus("模型目录已解析为: " + runtimeModelDir.getAbsolutePath());
            callback.onStatus("模型目录内容: " + describeDirectory(runtimeModelDir));
            if (!runtimeModelDir.getAbsolutePath().equals(stagedModelDir.getAbsolutePath())) {
                callback.onStatus("模型已复制到兼容路径: " + stagedModelDir.getAbsolutePath());
            }
            System.out.println("[VOSK-DIAG] runtimeModelDir=" + runtimeModelDir.getAbsolutePath());
            System.out.println("[VOSK-DIAG] stagedModelDir=" + stagedModelDir.getAbsolutePath());
            System.out.println("[VOSK-DIAG] runtimeModelDir children=" + describeDirectory(runtimeModelDir));
            callback.onStatus("准备初始化 VOSK Model...");
            System.out.println("[VOSK-DIAG] initializing Model with path=" + stagedModelDir.getAbsolutePath());
            try (Model model = new Model(stagedModelDir.getAbsolutePath());
                 Recognizer recognizer = new Recognizer(model, SAMPLE_RATE)) {
                captureProvider = SystemAudioCaptureProviderFactory.create();
                callback.onStatus("音频采集方式: " + captureProvider.getName());
                final long[] totalBytes = new long[]{0L};
                final long[] lastLevelLog = new long[]{System.currentTimeMillis()};
                final long[] quietStartedAt = new long[]{lastLevelLog[0]};

                captureProvider.start(new PcmAudioCaptureProvider.Listener() {
                    @Override
                    public void onStatus(String text) {
                        callback.onStatus(text);
                    }

                    @Override
                    public void onFrame(byte[] pcm16le, int length) throws IOException {
                        if (!running.get()) {
                            return;
                        }
                        totalBytes[0] += length;
                        double rms = calculatePcm16LeRms(pcm16le, length);
                        long now = System.currentTimeMillis();
                        if (rms > 50.0D) {
                            quietStartedAt[0] = now;
                        }
                        if (now - lastLevelLog[0] >= 1000L) {
                            System.out.println("[AUDIO-CAPTURE] provider=" + captureProvider.getName()
                                    + ", bytes=" + totalBytes[0]
                                    + ", rms=" + String.format(java.util.Locale.US, "%.2f", rms));
                            if (now - quietStartedAt[0] > 5000L) {
                                callback.onStatus("监听中，但当前系统音频几乎是静音；请确认会议正在播放声音");
                            }
                            lastLevelLog[0] = now;
                        }

                        boolean sentenceFinished = recognizer.acceptWaveForm(pcm16le, length);
                        if (sentenceFinished) {
                            String sentence = parseText(recognizer.getResult());
                            if (!sentence.trim().isEmpty()) {
                                callback.onFinalSentence(sentence.trim());
                            }
                        } else {
                            String partial = parsePartial(recognizer.getPartialResult());
                            if (!partial.trim().isEmpty()) {
                                callback.onPartial(partial.trim());
                            }
                        }
                    }
                });

                String last = parseText(recognizer.getFinalResult());
                if (!last.trim().isEmpty()) {
                    callback.onFinalSentence(last.trim());
                }
                callback.onStatus("监听已停止");
            }
        } catch (Exception ex) {
            System.out.println("[VOSK-DIAG] failed: " + ex.getClass().getName() + ": " + ex.getMessage());
            if (running.get()) {
                callback.onError("语音监听失败: " + ex.getMessage());
            }
        } finally {
            running.set(false);
            if (captureProvider != null) {
                captureProvider.stop();
            }
        }
    }

    private File resolveModelDirectory(String modelPath) {
        if (modelPath == null || modelPath.trim().isEmpty()) {
            return null;
        }

        File direct = new File(modelPath);
        if (direct.exists() && direct.isDirectory() && containsModelFiles(direct)) {
            return direct;
        }

        File packaged = new File(direct, "vosk-model-small-cn-0.22");
        if (packaged.exists() && packaged.isDirectory() && containsModelFiles(packaged)) {
            return packaged;
        }

        File nested = new File(direct, "model");
        if (nested.exists() && nested.isDirectory() && containsModelFiles(nested)) {
            return nested;
        }

        return null;
    }

    private File prepareRuntimeModelDirectory(File modelDir) throws IOException {
        if (containsModelFiles(modelDir)) {
            return modelDir;
        }

        File nestedModel = new File(modelDir, "vosk-model-small-cn-0.22");
        if (containsModelFiles(nestedModel)) {
            return nestedModel;
        }

        File altModel = new File(modelDir, "model");
        if (containsModelFiles(altModel)) {
            return altModel;
        }

        throw new IOException("VOSK 模型目录不完整: " + modelDir.getAbsolutePath() + "，请检查 am/conf/graph/ivector 是否都在同一级目录下。");
    }

    private File stageModelToAsciiPath(File sourceDir) throws IOException {
        File javaTmp = new File(System.getProperty("java.io.tmpdir", "."));
        File stageRoot = new File(javaTmp, "vosk-stage");
        if (!stageRoot.exists() && !stageRoot.mkdirs()) {
            throw new IOException("无法创建临时模型目录: " + stageRoot.getAbsolutePath());
        }

        File targetDir = new File(stageRoot, "vosk-model-small-cn-0.22");
        if (containsModelFiles(targetDir)) {
            return targetDir;
        }
        if (targetDir.exists()) {
            deleteRecursively(targetDir);
        }

        copyRecursively(sourceDir, targetDir);
        return targetDir;
    }

    private void copyRecursively(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("无法创建目录: " + target.getAbsolutePath());
            }
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (int i = 0; i < children.length; i++) {
                copyRecursively(children[i], new File(target, children[i].getName()));
            }
            return;
        }

        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteRecursively(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursively(children[i]);
                }
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    private boolean containsModelFiles(File modelDir) {
        String[] requiredEntries = new String[]{"am", "conf", "graph", "ivector"};
        for (int i = 0; i < requiredEntries.length; i++) {
            File entry = new File(modelDir, requiredEntries[i]);
            if (!entry.exists()) {
                return false;
            }
        }
        return true;
    }

    private String describeDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return "<不存在>";
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return "<空目录>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < files.length && i < 12; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(files[i].getName());
            if (files[i].isDirectory()) {
                sb.append("/");
            }
        }
        if (files.length > 12) {
            sb.append(" ... total=").append(files.length);
        }
        return sb.toString();
    }

    private double calculatePcm16LeRms(byte[] buffer, int length) {
        if (buffer == null || length < 2) {
            return 0.0D;
        }
        long sumSquares = 0L;
        int samples = length / 2;
        for (int i = 0; i + 1 < length; i += 2) {
            int low = buffer[i] & 0xFF;
            int high = buffer[i + 1];
            int sample = (high << 8) | low;
            sumSquares += (long) sample * (long) sample;
        }
        if (samples <= 0) {
            return 0.0D;
        }
        return Math.sqrt(sumSquares / (double) samples);
    }

    private String parseText(String resultJson) throws IOException {
        JsonNode node = OBJECT_MAPPER.readTree(resultJson);
        return node.path("text").asText("");
    }

    private String parsePartial(String resultJson) throws IOException {
        JsonNode node = OBJECT_MAPPER.readTree(resultJson);
        return node.path("partial").asText("");
    }
}
