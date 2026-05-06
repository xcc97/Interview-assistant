package com.interviewassistant.service.audio;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

public class SystemAudioCaptureProviderFactory {
    public static PcmAudioCaptureProvider create() {
        File helper = resolveNativeHelper();
        if (isRunnableHelper(helper)) {
            return new ExternalPcmProcessCaptureProvider(helper, buildHelperDisplayName(helper));
        }
        return new JavaSoundMicrophoneCaptureProvider(resolveRequestedMixer());
    }

    public static boolean hasNativeHelper() {
        return isRunnableHelper(resolveNativeHelper());
    }

    private static File resolveNativeHelper() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return resolveWindowsHelper();
        }
        if (os.contains("mac")) {
            return resolveMacHelper();
        }
        return resolveLinuxHelper();
    }

    private static File resolveWindowsHelper() {
        return findExistingFile(
                "native/windows/wasapi-loopback-capture.exe",
                "native/windows/wasapi-loopback-capture/build/Release/wasapi-loopback-capture.exe",
                "native/windows/wasapi-loopback-capture/build/wasapi-loopback-capture.exe"
        );
    }

    private static File resolveMacHelper() {
        return findExistingFile(
                "native/macos/system-audio-capture",
                "native/macos/system-audio-capture/.build/release/system-audio-capture"
        );
    }

    private static File resolveLinuxHelper() {
        return findExistingFile(
                "native/linux/system-audio-capture"
        );
    }

    private static File findExistingFile(String... candidates) {
        File base = getApplicationBaseDirectory();
        File workingDir = new File(System.getProperty("user.dir", "."));
        for (int i = 0; i < candidates.length; i++) {
            File fromBase = new File(base, candidates[i]);
            if (isRunnableHelper(fromBase)) {
                return fromBase;
            }
            File fromWorkingDir = new File(workingDir, candidates[i]);
            if (isRunnableHelper(fromWorkingDir)) {
                return fromWorkingDir;
            }
        }
        return new File(workingDir, candidates[0]);
    }

    private static boolean isRunnableHelper(File helper) {
        return helper != null && helper.exists() && helper.isFile();
    }

    private static String buildHelperDisplayName(File helper) {
        String os = System.getProperty("os.name", "");
        if (os.toLowerCase().contains("win")) {
            return "Windows WASAPI Loopback";
        }
        if (os.toLowerCase().contains("mac")) {
            return "macOS ScreenCaptureKit System Audio";
        }
        return helper.getName();
    }

    private static String resolveRequestedMixer() {
        String mixer = System.getProperty("interviewassistant.audio.mixer", "");
        if (mixer == null || mixer.trim().isEmpty()) {
            mixer = System.getenv("INTERVIEW_ASSISTANT_AUDIO_MIXER");
        }
        return mixer == null ? "" : mixer.trim();
    }

    private static File getApplicationBaseDirectory() {
        try {
            URL location = SystemAudioCaptureProviderFactory.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                File base = new File(location.toURI());
                if (base.isFile()) {
                    File parent = base.getParentFile();
                    if (parent != null) {
                        return parent;
                    }
                }
                return base;
            }
        } catch (URISyntaxException ignored) {
        }
        return new File(System.getProperty("user.dir", "."));
    }
}
