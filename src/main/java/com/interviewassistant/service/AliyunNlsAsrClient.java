package com.interviewassistant.service;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AliyunNlsAsrClient implements AsrClient {
    private static final String DEFAULT_URL = "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1";
    private static final byte[] SILENCE_FRAME_100MS = new byte[3200];

    private final AppConfig config;
    private final BackendClient backendClient;
    private NlsClient nlsClient;
    private SpeechTranscriber transcriber;
    private ScheduledExecutorService keepAliveExecutor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicLong lastRealAudioAtMillis = new AtomicLong(0L);

    public AliyunNlsAsrClient(AppConfig config) {
        this(config, new BackendClient(config));
    }

    public AliyunNlsAsrClient(AppConfig config, BackendClient backendClient) {
        this.config = config;
        this.backendClient = backendClient;
    }

    @Override
    public void start(Listener listener) throws Exception {
        String appKey;
        String token;
        String endpoint;
        if (config.isBackendEnabled()) {
            BackendClient.AsrCredential credential = backendClient.fetchAsrCredential();
            appKey = credential.getAppKey();
            token = credential.getToken();
            endpoint = credential.getEndpoint();
            listener.onStatus("已从后端获取阿里云实时识别授权");
        } else {
            appKey = config.getAliyunNlsAppKey();
            if (appKey.isEmpty()) {
                throw new IOException("未配置阿里云 NLS AppKey，请设置 ALIYUN_NLS_APP_KEY 或 application.properties 里的 aliyun.nls.appKey");
            }
            token = config.getAliyunNlsToken();
            if (token.isEmpty()) {
                token = createToken();
            }
            endpoint = resolveNlsUrl();
        }
        if (appKey.isEmpty()) {
            throw new IOException("未获取到阿里云 NLS AppKey");
        }
        if (token.isEmpty()) {
            throw new IOException("未获取到阿里云 NLS Token，请检查后端或本地 AccessKey 配置");
        }

        nlsClient = new NlsClient(endpoint.isEmpty() ? DEFAULT_URL : endpoint, token);
        transcriber = new SpeechTranscriber(nlsClient, createListener(listener));
        transcriber.setAppKey(appKey);
        transcriber.setFormat(InputFormatEnum.PCM);
        transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
        transcriber.setEnableIntermediateResult(true);
        transcriber.start();
        lastRealAudioAtMillis.set(System.currentTimeMillis());
        started.set(true);
        startKeepAliveSilenceSender();
        listener.onStatus("阿里云实时语音识别已启动");
    }

    @Override
    public void sendAudio(byte[] pcm16le, int length) throws IOException {
        if (!started.get() || transcriber == null || pcm16le == null || length <= 0) {
            return;
        }
        lastRealAudioAtMillis.set(System.currentTimeMillis());
        if (length == pcm16le.length) {
            transcriber.send(pcm16le);
            return;
        }
        byte[] chunk = new byte[length];
        System.arraycopy(pcm16le, 0, chunk, 0, length);
        transcriber.send(chunk);
    }

    @Override
    public void close() {
        started.set(false);
        stopKeepAliveSilenceSender();
        if (transcriber != null) {
            try {
                transcriber.stop();
            } catch (Exception ignored) {
            }
            transcriber = null;
        }
        if (nlsClient != null) {
            try {
                nlsClient.shutdown();
            } catch (Exception ignored) {
            }
            nlsClient = null;
        }
    }

    private SpeechTranscriberListener createListener(Listener listener) {
        return new SpeechTranscriberListener() {
            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                listener.onStatus("正在实时识别会议声音...");
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                listener.onStatus("识别到语音，正在转写...");
            }

            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                String text = safeText(response);
                if (!text.isEmpty()) {
                    listener.onPartial(text);
                }
            }

            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                String text = safeText(response);
                if (!text.isEmpty()) {
                    listener.onFinalSentence(text);
                }
            }

            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                listener.onStatus("实时识别已结束");
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                listener.onError("阿里云实时识别失败: " + response.getStatusText());
            }
        };
    }

    private void startKeepAliveSilenceSender() {
        stopKeepAliveSilenceSender();
        keepAliveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "aliyun-nls-keepalive");
            thread.setDaemon(true);
            return thread;
        });
        keepAliveExecutor.scheduleAtFixedRate(() -> {
            if (!started.get() || transcriber == null) {
                return;
            }
            long quietMillis = System.currentTimeMillis() - lastRealAudioAtMillis.get();
            if (quietMillis < 5000L) {
                return;
            }
            try {
                sendSilenceFrame();
            } catch (Exception ignored) {
                // The regular ASR listener/error path will surface connection failures.
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void sendSilenceFrame() throws IOException {
        if (!started.get() || transcriber == null) {
            return;
        }
        transcriber.send(SILENCE_FRAME_100MS);
    }

    private void stopKeepAliveSilenceSender() {
        if (keepAliveExecutor != null) {
            keepAliveExecutor.shutdownNow();
            keepAliveExecutor = null;
        }
    }

    private String createToken() throws IOException {
        String accessKeyId = config.getAliyunAccessKeyId();
        String accessKeySecret = config.getAliyunAccessKeySecret();
        if (accessKeyId.isEmpty() || accessKeySecret.isEmpty()) {
            return "";
        }
        try {
            AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
            accessToken.apply();
            return accessToken.getToken();
        } catch (Exception ex) {
            throw new IOException("获取阿里云 NLS Token 失败: " + ex.getMessage(), ex);
        }
    }

    private String resolveNlsUrl() {
        String configured = config.getAliyunNlsEndpoint();
        return configured.isEmpty() ? DEFAULT_URL : configured;
    }

    private String safeText(SpeechTranscriberResponse response) {
        if (response == null || response.getTransSentenceText() == null) {
            return "";
        }
        return response.getTransSentenceText().trim();
    }
}
