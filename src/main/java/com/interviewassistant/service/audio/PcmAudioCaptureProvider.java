package com.interviewassistant.service.audio;

import java.io.IOException;

public interface PcmAudioCaptureProvider {
    interface Listener {
        void onStatus(String text);

        void onFrame(byte[] pcm16le, int length) throws IOException;
    }

    String getName();

    boolean isAvailable();

    void start(Listener listener) throws Exception;

    void stop();
}
