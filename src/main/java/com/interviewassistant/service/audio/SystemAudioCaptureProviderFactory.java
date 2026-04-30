package com.interviewassistant.service.audio;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

public class SystemAudioCaptureProviderFactory {
    public static PcmAudioCaptureProvider create(String mixerName) {
        File helper = resolveNativeHelper();
        if (helper != null && helper.exists()) {
            return new ExternalPcmProcessCaptureProvider(helper, buildHelperDisplayName(helper));
        }
        return new JavaSoundLoopbackCaptureProvider(mixerName);
    }

    public static boolean hasNativeHelper() {
        File helper = resolveNativeHelper();
        return helper != null && helper.exists();
    }

    private static File resolveNativeHelper() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String helperName;
        if (os.contains("win")) {
            helperName = "native/windows/wasapi-loopback-capture.exe";
        } else if (os.contains("mac")) {
            helperName = "native/macos/system-audio-capture";
        } else {
            helperName = "native/linux/system-audio-capture";
        }

        File base = getApplicationBaseDirectory();
        File direct = new File(base, helperName);
        if (direct.exists()) {
            return direct;
        }

        File fromWorkingDir = new File(System.getProperty("user.dir", "."), helperName);
        if (fromWorkingDir.exists()) {
            return fromWorkingDir;
        }

        return direct;
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
