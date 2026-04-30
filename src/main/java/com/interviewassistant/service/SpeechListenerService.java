package com.interviewassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewassistant.service.audio.PcmAudioCaptureProvider;
import com.interviewassistant.service.audio.SystemAudioCaptureProviderFactory;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        if (line != null) {
            line.stop();
            line.close();
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
                captureProvider = SystemAudioCaptureProviderFactory.create(mixerName);
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
        String needle = stripUiSuffix(mixerName.trim()).toLowerCase();
        if (needle.isEmpty()) {
            return null;
        }
        try {
            Mixer.Info[] infos = AudioSystem.getMixerInfo();
            Mixer fallback = null;
            for (int i = 0; i < infos.length; i++) {
                Mixer.Info info = infos[i];
                Mixer candidate = AudioSystem.getMixer(info);
                if (!isInputCapable(candidate)) {
                    continue;
                }
                String haystack = ((info.getName() == null ? "" : info.getName()) + " "
                        + (info.getDescription() == null ? "" : info.getDescription())).toLowerCase();
                if (!haystack.contains(needle) && !needle.contains(info.getName() == null ? "" : info.getName().toLowerCase())) {
                    continue;
                }
                if (fallback == null) {
                    fallback = candidate;
                }
                if (!isPortMixer(info)) {
                    return candidate;
                }
            }
            return fallback;
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
        }

        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < infos.length; i++) {
                Mixer m = AudioSystem.getMixer(infos[i]);
                if (!isInputCapable(m) || !isSystemAudioInput(infos[i])) {
                    continue;
                }
                if (preferredMixer != null && sameMixer(preferredMixer, m)) {
                    continue;
                }
                if (pass == 0 && isPortMixer(infos[i])) {
                    continue;
                }
                CaptureSession session = tryOpenOnMixer(m, targetFormat, candidateFormats);
                if (session != null) {
                    return session;
                }
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

    private boolean isPortMixer(Mixer.Info info) {
        String text = ((info.getName() == null ? "" : info.getName()) + " "
                + (info.getDescription() == null ? "" : info.getDescription())).toLowerCase();
        return text.contains("port mixer") || text.startsWith("port ");
    }

    private String stripUiSuffix(String mixerName) {
        if (mixerName == null) {
            return "";
        }
        int detailStart = mixerName.indexOf("  [");
        String cleaned = detailStart >= 0 ? mixerName.substring(0, detailStart) : mixerName;
        return cleaned.replace("（麦克风/普通输入，不建议）", "").trim();
    }

    private boolean sameMixer(Mixer a, Mixer b) {
        if (a == null || b == null || a.getMixerInfo() == null || b.getMixerInfo() == null) {
            return false;
        }
        return a.getMixerInfo().getName().equalsIgnoreCase(b.getMixerInfo().getName());
    }

    private String describeMixer(Mixer mixer) {
        if (mixer == null || mixer.getMixerInfo() == null) {
            return "<未匹配到指定 Mixer，将自动选择系统声音输入源>";
        }
        Mixer.Info info = mixer.getMixerInfo();
        return info.getName() + " | " + info.getDescription() + " | " + info.getVendor() + " | " + info.getVersion();
    }

    private boolean isSystemAudioInput(Mixer.Info info) {
        String text = getMixerSearchText(info);
        return isVirtualCableText(text)
                || text.contains("stereo mix")
                || text.contains("立体声混音")
                || text.contains("立體聲混音")
                || text.contains("loopback")
                || text.contains("blackhole")
                || text.contains("soundflower")
                || text.contains("what u hear")
                || text.contains("what you hear")
                || text.contains("wave out mix")
                || text.contains("monitor of")
                || text.contains("wasapi");
    }

    private boolean isVirtualCableText(String text) {
        return text.contains("vb-audio")
                || text.contains("vb audio")
                || text.contains("cable output")
                || text.contains("cable-output")
                || text.contains("cable out")
                || text.contains("virtual cable")
                || text.contains("virtual-audio");
    }

    private String getMixerSearchText(Mixer.Info info) {
        return ((info.getName() == null ? "" : info.getName()) + " "
                + (info.getDescription() == null ? "" : info.getDescription())).toLowerCase();
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

    private String listSystemAudioInputNames() {
        java.util.ArrayList<String> names = new java.util.ArrayList<String>();
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        for (int i = 0; i < infos.length; i++) {
            Mixer m = AudioSystem.getMixer(infos[i]);
            if (isInputCapable(m) && isSystemAudioInput(infos[i]) && infos[i].getName() != null && !infos[i].getName().trim().isEmpty()) {
                names.add(infos[i].getName().trim());
            }
        }
        if (names.isEmpty()) {
            return "无";
        }
        return String.join(", ", names);
    }

    private String buildAudioDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("[AUDIO-DIAG] os.name=").append(System.getProperty("os.name", "")).append('\n');
        sb.append("[AUDIO-DIAG] java.version=").append(System.getProperty("java.version", "")).append('\n');
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        sb.append("[AUDIO-DIAG] mixer.count=").append(infos.length).append('\n');
        for (int i = 0; i < infos.length; i++) {
            Mixer.Info info = infos[i];
            Mixer mixer = AudioSystem.getMixer(info);
            sb.append("[AUDIO-DIAG] mixer[").append(i).append("] name=").append(info.getName())
                    .append(", vendor=").append(info.getVendor())
                    .append(", version=").append(info.getVersion())
                    .append(", desc=").append(info.getDescription()).append('\n');
            appendLineInfos(sb, "target", mixer.getTargetLineInfo());
            appendLineInfos(sb, "source", mixer.getSourceLineInfo());
        }
        return sb.toString();
    }

    private void appendLineInfos(StringBuilder sb, String label, Line.Info[] lineInfos) {
        if (lineInfos == null || lineInfos.length == 0) {
            sb.append("[AUDIO-DIAG]   ").append(label).append(" lines=<none>").append('\n');
            return;
        }
        for (int i = 0; i < lineInfos.length; i++) {
            Line.Info lineInfo = lineInfos[i];
            sb.append("[AUDIO-DIAG]   ").append(label).append("[").append(i).append("] class=")
                    .append(lineInfo.getLineClass().getName()).append(", info=").append(lineInfo).append('\n');
            if (lineInfo instanceof DataLine.Info) {
                AudioFormat[] formats = ((DataLine.Info) lineInfo).getFormats();
                if (formats == null || formats.length == 0) {
                    sb.append("[AUDIO-DIAG]     formats=<unspecified>").append('\n');
                } else {
                    for (int j = 0; j < formats.length && j < 8; j++) {
                        sb.append("[AUDIO-DIAG]     format[").append(j).append("]=").append(formats[j]).append('\n');
                    }
                    if (formats.length > 8) {
                        sb.append("[AUDIO-DIAG]     ... total formats=").append(formats.length).append('\n');
                    }
                }
            }
        }
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
