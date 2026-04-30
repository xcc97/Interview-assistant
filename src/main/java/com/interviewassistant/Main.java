package com.interviewassistant;

import com.interviewassistant.ui.MainFrame;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class Main {
    public static void main(String[] args) {
        configureVoskNative();

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private static void configureVoskNative() {
        // Avoid loading stale libvosk from system directories.
        System.setProperty("jna.nosys", "true");

        String resourcePath = resolveVoskNativeResourcePath();
        String fileName = resolveNativeFileName(resourcePath);
        if (resourcePath == null || fileName == null) {
            return;
        }

        try (InputStream in = Main.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return;
            }

            File tempDir = Files.createTempDirectory("vosk-native-").toFile();
            tempDir.deleteOnExit();
            File nativeFile = new File(tempDir, fileName);
            Files.copy(in, nativeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            nativeFile.deleteOnExit();

            System.setProperty("jna.library.path", tempDir.getAbsolutePath());
        } catch (IOException ignored) {
            // Fall back to default JNA loading behavior.
        }
    }

    private static String resolveVoskNativeResourcePath() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        if (osName.contains("win")) {
            return arch.contains("64") ? "/windows-x86-64/vosk.dll" : "/windows-x86/vosk.dll";
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return arch.contains("aarch64") || arch.contains("arm64") ? "/darwin-aarch64/libvosk.dylib" : "/darwin/libvosk.dylib";
        }
        if (osName.contains("linux")) {
            return arch.contains("aarch64") || arch.contains("arm64") ? "/linux-aarch64/libvosk.so" : "/linux-x86-64/libvosk.so";
        }
        return null;
    }

    private static String resolveNativeFileName(String resourcePath) {
        if (resourcePath == null) {
            return null;
        }
        if (resourcePath.endsWith(".dll")) {
            return "vosk.dll";
        }
        if (resourcePath.endsWith(".dylib")) {
            return "libvosk.dylib";
        }
        if (resourcePath.endsWith(".so")) {
            return "libvosk.so";
        }
        return null;
    }
}
