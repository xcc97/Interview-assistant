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

        String osName = System.getProperty("os.name", "").toLowerCase();
        String resourcePath;
        String fileName;
        if (osName.contains("mac")) {
            resourcePath = "/darwin/libvosk.dylib";
            fileName = "libvosk.dylib";
        } else if (osName.contains("linux")) {
            resourcePath = "/linux-x86-64/libvosk.so";
            fileName = "libvosk.so";
        } else {
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
}
