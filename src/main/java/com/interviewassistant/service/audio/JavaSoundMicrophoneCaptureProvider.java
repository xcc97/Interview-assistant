package com.interviewassistant.service.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;

public class JavaSoundMicrophoneCaptureProvider implements PcmAudioCaptureProvider {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int BUFFER_SIZE = 4096;

    private final AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE,
            SAMPLE_SIZE_BITS,
            CHANNELS,
            CHANNELS * (SAMPLE_SIZE_BITS / 8),
            SAMPLE_RATE,
            false);

    private volatile boolean running;
    private volatile TargetDataLine line;
    private final String requestedMixer;

    public JavaSoundMicrophoneCaptureProvider(String requestedMixer) {
        this.requestedMixer = requestedMixer == null ? "" : requestedMixer.trim();
    }

    @Override
    public String getName() {
        if (requestedMixer.isEmpty()) {
            return "Java Sound Microphone";
        }
        return "Java Sound Microphone (" + requestedMixer + ")";
    }

    @Override
    public boolean isAvailable() {
        try {
            return resolveLine() != null;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void start(Listener listener) throws Exception {
        TargetDataLine targetLine = resolveLine();
        if (targetLine == null) {
            throw new IOException("未找到可用的麦克风输入设备");
        }

        line = targetLine;
        int openBufferSize = BUFFER_SIZE * 8;
        line.open(format, openBufferSize);
        line.start();
        running = true;
        listener.onStatus("未找到系统音频采集组件，已降级为麦克风采集: " + getName());

        byte[] buffer = new byte[BUFFER_SIZE];
        while (running) {
            int read = line.read(buffer, 0, buffer.length);
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
            try {
                current.stop();
            } catch (Exception ignored) {
            }
            try {
                current.close();
            } catch (Exception ignored) {
            }
        }
    }

    private TargetDataLine resolveLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!requestedMixer.isEmpty()) {
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            for (int i = 0; i < mixerInfos.length; i++) {
                Mixer.Info mixerInfo = mixerInfos[i];
                String text = (mixerInfo.getName() + " " + mixerInfo.getDescription()).toLowerCase();
                if (text.contains(requestedMixer.toLowerCase())) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(info)) {
                        return (TargetDataLine) mixer.getLine(info);
                    }
                }
            }
        }

        if (AudioSystem.isLineSupported(info)) {
            return (TargetDataLine) AudioSystem.getLine(info);
        }

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (int i = 0; i < mixerInfos.length; i++) {
            Mixer mixer = AudioSystem.getMixer(mixerInfos[i]);
            if (mixer.isLineSupported(info)) {
                return (TargetDataLine) mixer.getLine(info);
            }
        }
        return null;
    }
}
