package com.interviewassistant.service;

import java.io.Closeable;
import java.io.IOException;

public interface AsrClient extends Closeable {
    interface Listener {
        void onStatus(String text);

        void onPartial(String text);

        void onFinalSentence(String text);

        void onError(String text);
    }

    void start(Listener listener) throws Exception;

    void sendAudio(byte[] pcm16le, int length) throws IOException;

    @Override
    void close();
}
