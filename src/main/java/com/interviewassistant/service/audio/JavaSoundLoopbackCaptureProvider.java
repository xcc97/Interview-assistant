package com.interviewassistant.service.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JavaSoundLoopbackCaptureProvider implements PcmAudioCaptureProvider {
    private static final float SAMPLE_RATE = 16000.0F;

    private final String mixerName;
    private volatile TargetDataLine line;
    private volatile boolean running;

    public JavaSoundLoopbackCaptureProvider(String mixerName) {
        this.mixerName = mixerName == null ? "" : mixerName.trim();
    }

    @Override
    public String getName() {
        return "JavaSound 回环输入";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void start(Listener listener) throws Exception {
        AudioFormat targetFormat = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        CaptureSession session = openBestCaptureSession(targetFormat, resolveMixer(mixerName), buildCandidateFormats());
        if (session == null || session.line == null || session.convertedStream == null) {
            throw new IOException("未找到可用的系统声音/回环输入源。商用推荐使用内置 WASAPI/ScreenCaptureKit 采集组件；开发回退可使用 VB-CABLE、BlackHole 或 Stereo Mix。");
        }

        line = session.line;
        running = true;
        line.start();
        listener.onStatus("正在使用 JavaSound 回环输入: " + session.captureFormat);

        byte[] buffer = new byte[4096];
        while (running) {
            int read = session.convertedStream.read(buffer);
            if (read > 0) {
                listener.onFrame(buffer, read);
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        TargetDataLine current = line;
        if (current != null) {
            current.stop();
            current.close();
        }
    }

    private Mixer resolveMixer(String mixerName) {
        if (mixerName == null || mixerName.trim().isEmpty()) {
            return null;
        }
        String needle = stripUiSuffix(mixerName).toLowerCase();
        Mixer fallback = null;
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        for (int i = 0; i < infos.length; i++) {
            Mixer.Info info = infos[i];
            Mixer candidate = AudioSystem.getMixer(info);
            if (!isInputCapable(candidate)) {
                continue;
            }
            String haystack = getMixerSearchText(info);
            if (!haystack.contains(needle)) {
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
    }

    private CaptureSession openBestCaptureSession(AudioFormat targetFormat, Mixer preferredMixer, List<AudioFormat> candidateFormats) {
        if (preferredMixer != null) {
            CaptureSession preferred = tryOpenOnMixer(preferredMixer, targetFormat, candidateFormats);
            if (preferred != null) {
                return preferred;
            }
        }

        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        for (int i = 0; i < infos.length; i++) {
            Mixer mixer = AudioSystem.getMixer(infos[i]);
            if (!isInputCapable(mixer) || !isSystemAudioInput(infos[i])) {
                continue;
            }
            if (preferredMixer != null && sameMixer(preferredMixer, mixer)) {
                continue;
            }
            CaptureSession session = tryOpenOnMixer(mixer, targetFormat, candidateFormats);
            if (session != null) {
                return session;
            }
        }
        return null;
    }

    private CaptureSession tryOpenOnMixer(Mixer mixer, AudioFormat targetFormat, List<AudioFormat> candidateFormats) {
        List<AudioFormat> orderedFormats = new ArrayList<AudioFormat>();
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

    private List<AudioFormat> buildCandidateFormats() {
        Float[] sampleRates = new Float[]{48000.0f, 44100.0f, 32000.0f, 16000.0f, 8000.0f};
        Integer[] sampleSizes = new Integer[]{16, 24, 32};
        Integer[] channels = new Integer[]{1, 2};
        Boolean[] signedOptions = new Boolean[]{true, false};
        Boolean[] bigEndianOptions = new Boolean[]{false, true};

        ArrayList<AudioFormat> formats = new ArrayList<AudioFormat>();
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

    private List<AudioFormat> getMixerFormats(Mixer mixer) {
        ArrayList<AudioFormat> formats = new ArrayList<AudioFormat>();
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

    private boolean isPortMixer(Mixer.Info info) {
        String text = getMixerSearchText(info);
        return text.contains("port mixer") || text.startsWith("port ");
    }

    private boolean isSystemAudioInput(Mixer.Info info) {
        String text = getMixerSearchText(info);
        return text.contains("stereo mix")
                || text.contains("立体声混音")
                || text.contains("立體聲混音")
                || text.contains("loopback")
                || text.contains("blackhole")
                || text.contains("soundflower")
                || text.contains("vb-audio")
                || text.contains("vb audio")
                || text.contains("cable output")
                || text.contains("cable-output")
                || text.contains("cable out")
                || text.contains("virtual cable")
                || text.contains("virtual-audio")
                || text.contains("what u hear")
                || text.contains("what you hear")
                || text.contains("wave out mix")
                || text.contains("monitor of")
                || text.contains("wasapi");
    }

    private String getMixerSearchText(Mixer.Info info) {
        return ((info.getName() == null ? "" : info.getName()) + " "
                + (info.getDescription() == null ? "" : info.getDescription())).toLowerCase();
    }

    private String stripUiSuffix(String mixerName) {
        int detailStart = mixerName.indexOf("  [");
        String cleaned = detailStart >= 0 ? mixerName.substring(0, detailStart) : mixerName;
        return cleaned.replace("（麦克风/普通输入，不建议）", "").trim();
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
}
