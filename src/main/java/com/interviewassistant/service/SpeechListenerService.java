package com.interviewassistant.service;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import com.interviewassistant.service.audio.PcmAudioCaptureProvider;
import com.interviewassistant.service.audio.SystemAudioCaptureProviderFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeechListenerService {
    private static final String DEFAULT_NLS_ENDPOINT = "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1";

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
    private volatile SpeechTranscriber transcriber;
    private volatile NlsClient nlsClient;

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
        if (config.getAliyunAsrAppKey().isEmpty()) {
            callback.onError("未配置阿里云语音识别 AppKey，请设置 ALIYUN_ASR_APP_KEY 或 application.properties 里的 asr.aliyun.appKey");
            return;
        }
        if (config.getAliyunAsrToken().isEmpty()
                && (config.getAliyunAsrAccessKeyId().isEmpty() || config.getAliyunAsrAccessKeySecret().isEmpty())) {
            callback.onError("未配置阿里云 AccessKey，请设置 ALIYUN_ACCESS_KEY_ID / ALIYUN_ACCESS_KEY_SECRET，或 application.properties 里的 asr.aliyun.accessKeyId / asr.aliyun.accessKeySecret。也可以临时设置 asr.aliyun.token 兜底。");
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
        SpeechTranscriber currentTranscriber = transcriber;
        if (currentTranscriber != null) {
            try {
                currentTranscriber.stop();
            } catch (Exception ignored) {
            }
            currentTranscriber.close();
        }
        if (captureProvider != null) {
            captureProvider.stop();
        }
        NlsClient currentClient = nlsClient;
        if (currentClient != null) {
            currentClient.shutdown();
        }
    }

    private void doListen(Callback callback, String mixerName) {
        callback.onStatus("正在连接阿里云语音识别...");
        System.out.println("[ASR-DIAG] user.dir=" + System.getProperty("user.dir", ""));
        System.out.println("[ASR-DIAG] os.name=" + System.getProperty("os.name", ""));
        System.out.println("[AUDIO-CAPTURE] requestedMixer=" + mixerName);
        try {
            String endpoint = config.getAliyunAsrEndpoint();
            String token = resolveAliyunAsrToken(callback);
            nlsClient = new NlsClient(endpoint.isEmpty() ? DEFAULT_NLS_ENDPOINT : endpoint, token);
            transcriber = new SpeechTranscriber(nlsClient, buildListener(callback));
            transcriber.setAppKey(config.getAliyunAsrAppKey());
            transcriber.setFormat(InputFormatEnum.PCM);
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            transcriber.setEnableIntermediateResult(true);
            transcriber.setEnablePunctuation(true);
            transcriber.setEnableITN(true);
            transcriber.start();

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
                    if (!running.get() || transcriber == null) {
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
                    transcriber.send(pcm16le, length);
                }
            });

            if (running.get()) {
                callback.onStatus("监听已停止");
            }
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
            }
            if (transcriber != null) {
                transcriber.close();
                transcriber = null;
            }
            if (nlsClient != null) {
                nlsClient.shutdown();
                nlsClient = null;
            }
        }
    }

    private String resolveAliyunAsrToken(Callback callback) throws IOException {
        String accessKeyId = config.getAliyunAsrAccessKeyId();
        String accessKeySecret = config.getAliyunAsrAccessKeySecret();
        if (!accessKeyId.isEmpty() && !accessKeySecret.isEmpty()) {
            callback.onStatus("正在使用 AccessKey 获取阿里云 NLS Token...");
            AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
            accessToken.apply();
            String token = accessToken.getToken();
            if (token == null || token.trim().isEmpty()) {
                throw new IOException("阿里云 NLS Token 获取失败，返回为空");
            }
            callback.onStatus("阿里云 NLS Token 获取成功，有效期至: " + accessToken.getExpireTime());
            return token.trim();
        }

        String configuredToken = config.getAliyunAsrToken();
        if (!configuredToken.isEmpty()) {
            callback.onStatus("未配置 AccessKey，使用已配置的阿里云 NLS Token");
            return configuredToken;
        }

        throw new IOException("未配置阿里云 AccessKey 或 NLS Token");
    }

    private SpeechTranscriberListener buildListener(Callback callback) {
        return new SpeechTranscriberListener() {
            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                callback.onStatus("阿里云语音识别已连接，开始监听");
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                callback.onStatus("监听中...");
            }

            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                String text = safeText(response);
                if (!text.isEmpty()) {
                    callback.onFinalSentence(text);
                }
            }

            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                String text = safeText(response);
                if (!text.isEmpty()) {
                    callback.onPartial(text);
                }
            }

            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                callback.onStatus("监听已停止");
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                callback.onError("阿里云语音识别失败: " + safeText(response));
            }
        };
    }

    private String safeText(SpeechTranscriberResponse response) {
        if (response == null || response.getTransSentenceText() == null) {
            return "";
        }
        return response.getTransSentenceText().trim();
    }

    private String buildFriendlyErrorMessage(Throwable ex) {
        String message = ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
        return "语音监听失败: " + message + "\n\n当前识别链路已移除 VOSK，只使用系统音频采集 + 阿里云实时语音识别。请检查阿里云 AppKey、AccessKeyId、AccessKeySecret、网络，以及音频采集组件是否可用。";
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
