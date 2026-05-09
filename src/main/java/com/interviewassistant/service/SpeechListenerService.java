package com.interviewassistant.service;

import com.interviewassistant.service.audio.PcmAudioCaptureProvider;
import com.interviewassistant.service.audio.SystemAudioCaptureProviderFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeechListenerService {
    public interface Callback {
        void onStatus(String text);

        void onPartial(String text);

        void onFinalSentence(String text);

        void onError(String text);
    }

    private final AppConfig config;
    private final BackendClient backendClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private PcmAudioCaptureProvider captureProvider;
    private AsrClient asrClient;

    public SpeechListenerService(AppConfig config) {
        this(config, new BackendClient(config));
    }

    public SpeechListenerService(AppConfig config, BackendClient backendClient) {
        this.config = config;
        this.backendClient = backendClient;
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

        running.set(true);
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                doListen(callback, mixerName);
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
        if (asrClient != null) {
            asrClient.close();
        }
    }

    private void doListen(Callback callback, String mixerName) {
        System.out.println("[ASR-DIAG] provider=" + config.getAsrProvider());
        System.out.println("[ASR-DIAG] user.dir=" + System.getProperty("user.dir", ""));
        System.out.println("[ASR-DIAG] os.name=" + System.getProperty("os.name", ""));
        System.out.println("[AUDIO-CAPTURE] requestedMixer=" + mixerName);
        callback.onStatus("正在启动实时识别...");
        try {
            asrClient = createAsrClient();
            asrClient.start(new AsrClient.Listener() {
                @Override
                public void onStatus(String text) {
                    callback.onStatus(text);
                }

                @Override
                public void onPartial(String text) {
                    callback.onPartial(text);
                }

                @Override
                public void onFinalSentence(String text) {
                    callback.onFinalSentence(text);
                }

                @Override
                public void onError(String text) {
                    callback.onError(text);
                }
            });

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
                    asrClient.sendAudio(pcm16le, length);
                }
            });
            callback.onStatus("监听已停止");
        } catch (Throwable ex) {
            System.out.println("[ASR-DIAG] failed: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            if (running.get()) {
                callback.onError(buildFriendlyErrorMessage(ex));
            }
        } finally {
            running.set(false);
            if (captureProvider != null) {
                captureProvider.stop();
                captureProvider = null;
            }
            if (asrClient != null) {
                asrClient.close();
                asrClient = null;
            }
        }
    }

    private AsrClient createAsrClient() throws IOException {
        String provider = config.getAsrProvider();
        if ("aliyun".equalsIgnoreCase(provider)) {
            return new AliyunNlsAsrClient(config, backendClient);
        }
        throw new IOException("暂不支持的识别服务: " + provider + "，当前仅支持 aliyun");
    }

    private String buildFriendlyErrorMessage(Throwable ex) {
        String message = ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
        return "实时语音识别失败: " + message;
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
}
