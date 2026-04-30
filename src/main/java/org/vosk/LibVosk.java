package org.vosk;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Local override for a known macOS symbol mismatch in some Vosk artifacts.
 * The bundled dylib may not export vosk_recognizer_set_grm, so we keep a
 * Java-level fallback method instead of binding that symbol via JNA.
 */
public class LibVosk {

    private static void unpackDll(File targetDir, String lib) throws IOException {
        InputStream source = LibVosk.class.getResourceAsStream("/win32-x86-64/" + lib + ".dll");
        Files.copy(source, new File(targetDir, lib + ".dll").toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    static {
        if (Platform.isWindows()) {
            try {
                File tmpFile = Native.extractFromResourcePath("/win32-x86-64/empty", LibVosk.class.getClassLoader());
                File tmpDir = tmpFile.getParentFile();
                new File(tmpDir, tmpFile.getName() + ".x").createNewFile();
                unpackDll(tmpDir, "libwinpthread-1");
                unpackDll(tmpDir, "libgcc_s_seh-1");
                unpackDll(tmpDir, "libstdc++-6");
            } catch (IOException ignored) {
                // registration may still fail later if dependencies are unavailable
            } finally {
                Native.register(LibVosk.class, "libvosk");
            }
        } else {
            Native.register(LibVosk.class, "vosk");
        }
    }

    public static native void vosk_set_log_level(int level);

    public static native Pointer vosk_model_new(String path);

    public static native void vosk_model_free(Pointer model);

    public static native Pointer vosk_spk_model_new(String path);

    public static native void vosk_spk_model_free(Pointer model);

    public static native Pointer vosk_recognizer_new(Model model, float sample_rate);

    public static native Pointer vosk_recognizer_new_spk(Pointer model, float sample_rate, Pointer spk_model);

    public static native Pointer vosk_recognizer_new_grm(Pointer model, float sample_rate, String grammar);

    public static native void vosk_recognizer_set_max_alternatives(Pointer recognizer, int max_alternatives);

    public static native void vosk_recognizer_set_words(Pointer recognizer, boolean words);

    public static native void vosk_recognizer_set_partial_words(Pointer recognizer, boolean partial_words);

    public static native void vosk_recognizer_set_spk_model(Pointer recognizer, Pointer spk_model);

    public static native boolean vosk_recognizer_accept_waveform(Pointer recognizer, byte[] data, int len);

    public static native boolean vosk_recognizer_accept_waveform_s(Pointer recognizer, short[] data, int len);

    public static native boolean vosk_recognizer_accept_waveform_f(Pointer recognizer, float[] data, int len);

    public static native String vosk_recognizer_result(Pointer recognizer);

    public static native String vosk_recognizer_final_result(Pointer recognizer);

    public static native String vosk_recognizer_partial_result(Pointer recognizer);

    public static native void vosk_recognizer_reset(Pointer recognizer);

    public static native void vosk_recognizer_free(Pointer recognizer);

    /**
     * Fallback for older native builds without vosk_recognizer_set_grm symbol.
     * Grammar updates can be applied by recreating recognizer with new_grm.
     */
    public static void vosk_recognizer_set_grm(Pointer recognizer, String grammar) {
        // no-op for compatibility with mismatched native symbols
    }

    public static void setLogLevel(LogLevel loglevel) {
        vosk_set_log_level(loglevel.getValue());
    }
}
