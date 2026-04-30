package com.interviewassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.File;
import java.io.IOException;
import java.util.List;
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
    private TargetDataLine line;

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
        File modelDir = new File(modelPath);
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            callback.onError("VOSK 模型目录不存在: " + modelPath);
            return;
        }

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
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    private void doListen(File modelDir, Callback callback, String mixerName) {
        callback.onStatus("正在加载语音模型...");
        AudioFormat targetFormat = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        List<AudioFormat> candidateFormats = buildCandidateFormats();

        Mixer mixer = resolveMixer(mixerName);
        try (Model model = new Model(modelDir.getAbsolutePath());
             Recognizer recognizer = new Recognizer(model, SAMPLE_RATE)) {
            CaptureSession session = openBestCaptureSession(targetFormat, mixer, candidateFormats);
            TargetDataLine captureLine = session == null ? null : session.line;
            AudioFormat captureFormat = session == null ? null : session.captureFormat;
            AudioInputStream convertedStream = session == null ? null : session.convertedStream;

            if (captureLine == null || captureFormat == null || convertedStream == null) {
                throw new IOException("未找到可用的录音设备格式（或无法转码为 16k 单声道）。请：1) 确认已启用麦克风/立体声混音；2) 在系统音频设置里切换输入设备；3) 如果要听腾讯会议/飞书对方声音，优先启用并选择 `Stereo Mix/立体声混音`。当前可用输入源: " + listInputMixerNames());
            }

            this.line = captureLine;
            captureLine.start();
            callback.onStatus("监听中：设备格式 " + captureFormat.getSampleRate() + "Hz/" + captureFormat.getSampleSizeInBits()
                    + "bit/" + captureFormat.getChannels() + "ch，转码为 16k 单声道识别");

            byte[] buffer = new byte[4096]; // 必须能被 2bytes/frame（单声道16bit）整除
            while (running.get()) {
                int read = convertedStream.read(buffer);
                if (read <= 0) {
                    continue;
                }

                boolean sentenceFinished = recognizer.acceptWaveForm(buffer, read);
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

            String last = parseText(recognizer.getFinalResult());
            if (!last.trim().isEmpty()) {
                callback.onFinalSentence(last.trim());
            }
            callback.onStatus("监听已停止");
            try {
                convertedStream.close();
            } catch (IOException ignored) {
            }
        } catch (Exception ex) {
            if (running.get()) {
                callback.onError("语音监听失败: " + ex.getMessage());
            }
        } finally {
            running.set(false);
            if (line != null) {
                line.stop();
                line.close();
            }
        }
    }

    private Mixer resolveMixer(String mixerName) {
        if (mixerName == null) {
            return null;
        }
        String needle = mixerName.trim();
        if (needle.isEmpty()) {
            return null;
        }
        try {
            Mixer.Info[] infos = AudioSystem.getMixerInfo();
            for (int i = 0; i < infos.length; i++) {
                Mixer.Info info = infos[i];
                if (info.getName() != null && info.getName().toLowerCase().contains(needle.toLowerCase())) {
                    return AudioSystem.getMixer(info);
                }
                if (info.getDescription() != null && info.getDescription().toLowerCase().contains(needle.toLowerCase())) {
                    return AudioSystem.getMixer(info);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<AudioFormat> buildCandidateFormats() {
        // 不同声卡/驱动支持的 PCM 输入格式差异很大；
        // 这里尽量覆盖常见组合，找到一个可打开的输入后再转码给 VOSK。
        Float[] sampleRates = new Float[]{48000.0f, 44100.0f, 32000.0f, 16000.0f, 8000.0f};
        Integer[] sampleSizes = new Integer[]{16, 24, 32};
        Integer[] channels = new Integer[]{1, 2};
        Boolean[] signedOptions = new Boolean[]{true, false};
        Boolean[] bigEndianOptions = new Boolean[]{false, true};

        java.util.ArrayList<AudioFormat> formats = new java.util.ArrayList<AudioFormat>();
        for (int i = 0; i < sampleRates.length; i++) {
            for (int j = 0; j < sampleSizes.length; j++) {
                for (int k = 0; k < channels.length; k++) {
                    for (int a = 0; a < signedOptions.length; a++) {
                        for (int b = 0; b < bigEndianOptions.length; b++) {
                            formats.add(new AudioFormat(sampleRates[i], sampleSizes[j], channels[k], signedOptions[a], bigEndianOptions[b]));
                        }
                    }
                }
            }
        }
        return formats;
    }

    private CaptureSession openBestCaptureSession(AudioFormat targetFormat, Mixer preferredMixer, List<AudioFormat> candidateFormats) {
        if (preferredMixer != null) {
            CaptureSession preferred = tryOpenOnMixer(preferredMixer, targetFormat, candidateFormats);
            if (preferred != null) {
                return preferred;
            }
        } else {
            CaptureSession def = tryOpenDefaultLine(targetFormat, candidateFormats);
            if (def != null) {
                return def;
            }
        }

        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        for (int i = 0; i < infos.length; i++) {
            Mixer m = AudioSystem.getMixer(infos[i]);
            if (!isInputCapable(m)) {
                continue;
            }
            if (preferredMixer != null && sameMixer(preferredMixer, m)) {
                continue;
            }
            CaptureSession session = tryOpenOnMixer(m, targetFormat, candidateFormats);
            if (session != null) {
                return session;
            }
        }
        return null;
    }

    private CaptureSession tryOpenDefaultLine(AudioFormat targetFormat, List<AudioFormat> candidateFormats) {
        for (int i = 0; i < candidateFormats.size(); i++) {
            AudioFormat fmt = candidateFormats.get(i);
            TargetDataLine tryLine = null;
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                tryLine = (TargetDataLine) AudioSystem.getLine(info);
                tryLine.open(fmt);
                AudioInputStream sourceStream = new AudioInputStream(tryLine);
                AudioInputStream converted = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
                return new CaptureSession(tryLine, fmt, converted);
            } catch (Exception ignored) {
                closeQuietly(tryLine);
            }
        }
        return null;
    }

    private CaptureSession tryOpenOnMixer(Mixer mixer, AudioFormat targetFormat, List<AudioFormat> candidateFormats) {
        List<AudioFormat> orderedFormats = new java.util.ArrayList<AudioFormat>();
        orderedFormats.addAll(getMixerFormats(mixer));
        for (int i = 0; i < candidateFormats.size(); i++) {
            AudioFormat fmt = candidateFormats.get(i);
            if (!containsEquivalentFormat(orderedFormats, fmt)) {
                orderedFormats.add(fmt);
            }
        }

        for (int i = 0; i < orderedFormats.size(); i++) {
            AudioFormat fmt = orderedFormats.get(i);
            TargetDataLine tryLine = null;
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                if (!mixer.isLineSupported(info)) {
                    continue;
                }
                tryLine = (TargetDataLine) mixer.getLine(info);
                tryLine.open(fmt);
                AudioInputStream sourceStream = new AudioInputStream(tryLine);
                AudioInputStream converted = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
                return new CaptureSession(tryLine, fmt, converted);
            } catch (Exception ignored) {
                closeQuietly(tryLine);
            }
        }
        return null;
    }

    private List<AudioFormat> getMixerFormats(Mixer mixer) {
        java.util.ArrayList<AudioFormat> formats = new java.util.ArrayList<AudioFormat>();
        Line.Info[] lineInfos = mixer.getTargetLineInfo();
        for (int i = 0; i < lineInfos.length; i++) {
            if (!(lineInfos[i] instanceof DataLine.Info)) {
                continue;
            }
            AudioFormat[] supported = ((DataLine.Info) lineInfos[i]).getFormats();
            for (int j = 0; j < supported.length; j++) {
                AudioFormat fmt = supported[j];
                if (fmt != null && !containsEquivalentFormat(formats, fmt)) {
                    formats.add(fmt);
                }
            }
        }
        return formats;
    }

    private boolean containsEquivalentFormat(List<AudioFormat> formats, AudioFormat target) {
        for (int i = 0; i < formats.size(); i++) {
            AudioFormat f = formats.get(i);
            if (f.getSampleRate() == target.getSampleRate()
                    && f.getSampleSizeInBits() == target.getSampleSizeInBits()
                    && f.getChannels() == target.getChannels()
                    && f.isBigEndian() == target.isBigEndian()
                    && f.getEncoding().equals(target.getEncoding())) {
                return true;
            }
        }
        return false;
    }

    private void closeQuietly(TargetDataLine line) {
        if (line == null) {
            return;
        }
        try {
            line.close();
        } catch (Exception ignored) {
        }
    }

    private boolean isInputCapable(Mixer mixer) {
        if (mixer == null) {
            return false;
        }
        Line.Info[] targets = mixer.getTargetLineInfo();
        if (targets == null || targets.length == 0) {
            return false;
        }
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] instanceof DataLine.Info) {
                DataLine.Info info = (DataLine.Info) targets[i];
                if (TargetDataLine.class.isAssignableFrom(info.getLineClass())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean sameMixer(Mixer a, Mixer b) {
        if (a == null || b == null || a.getMixerInfo() == null || b.getMixerInfo() == null) {
            return false;
        }
        return a.getMixerInfo().getName().equalsIgnoreCase(b.getMixerInfo().getName());
    }

    private String listInputMixerNames() {
        java.util.ArrayList<String> names = new java.util.ArrayList<String>();
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        for (int i = 0; i < infos.length; i++) {
            Mixer m = AudioSystem.getMixer(infos[i]);
            if (isInputCapable(m) && infos[i].getName() != null && !infos[i].getName().trim().isEmpty()) {
                names.add(infos[i].getName().trim());
            }
        }
        if (names.isEmpty()) {
            return "无";
        }
        return String.join(", ", names);
    }

    private static class CaptureSession {
        private final TargetDataLine line;
        private final AudioFormat captureFormat;
        private final AudioInputStream convertedStream;

        private CaptureSession(TargetDataLine line, AudioFormat captureFormat, AudioInputStream convertedStream) {
            this.line = line;
            this.captureFormat = captureFormat;
            this.convertedStream = convertedStream;
        }
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
