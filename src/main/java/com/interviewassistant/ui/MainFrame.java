package com.interviewassistant.ui;

import com.interviewassistant.model.InterviewAnalysis;
import com.interviewassistant.service.AppConfig;
import com.interviewassistant.service.BailianDeepSeekClient;
import com.interviewassistant.service.PdfResumeParser;
import com.interviewassistant.service.SpeechListenerService;
import com.interviewassistant.service.audio.SystemAudioCaptureProviderFactory;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainFrame extends JFrame {
    private static final String NATIVE_SYSTEM_AUDIO_OPTION = "内置系统音频采集（推荐，无需 VB-CABLE）";

    private final JTextField modelField = new JTextField(20);
    private final JTextArea transcriptArea = new JTextArea();
    private final JTextArea resumeArea = new JTextArea();
    private final JTextArea partialSpeechArea = new JTextArea();
    private final JComboBox<String> mixerCombo = new JComboBox<>();
    private final JTextArea intentArea = new JTextArea();
    private final JTextArea pointsArea = new JTextArea();
    private final JTextArea answerArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("状态: 就绪");

    private final AppConfig config = new AppConfig();
    private final PdfResumeParser pdfResumeParser = new PdfResumeParser();
    private final BailianDeepSeekClient deepSeekClient = new BailianDeepSeekClient(config);
    private final SpeechListenerService speechListenerService = new SpeechListenerService(config);
    private final ExecutorService analyzeExecutor = Executors.newSingleThreadExecutor();
    private JButton listenButton;
    private JButton refreshMixerButton;

    public MainFrame() {
        super("远程面试助手 (Java + 阿里云百炼 DeepSeek)");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(new Dimension(1080, 760));
        setLocationRelativeTo(null);
        initUi();
    }

    private void initUi() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton importResumeButton = new JButton("导入简历 PDF");
        JButton analyzeButton = new JButton("分析并生成回答");
        JButton clearButton = new JButton("清空输出");
        JButton audioHelpButton = new JButton("音频设置帮助");
        listenButton = new JButton("开始监听");
        refreshMixerButton = new JButton("刷新输入源");

        modelField.setText(config.getModel());
        modelField.setEditable(false);

        topPanel.add(importResumeButton);
        topPanel.add(new JLabel("监听输入源:"));
        topPanel.setPreferredSize(new Dimension(1080, 30));
        mixerCombo.setPreferredSize(new Dimension(340, 25));
        topPanel.add(mixerCombo);
        topPanel.add(refreshMixerButton);
        topPanel.add(listenButton);
        topPanel.add(new JLabel("当前模型:"));
        topPanel.add(modelField);
        topPanel.add(analyzeButton);
        topPanel.add(clearButton);
        topPanel.add(audioHelpButton);

        transcriptArea.setLineWrap(true);
        transcriptArea.setWrapStyleWord(true);
        transcriptArea.setBorder(BorderFactory.createTitledBorder("面试官问题（自动转写结果/可手动输入）"));

        resumeArea.setLineWrap(true);
        resumeArea.setWrapStyleWord(true);
        resumeArea.setEditable(false);
        resumeArea.setBorder(BorderFactory.createTitledBorder("简历解析结果"));

        partialSpeechArea.setEditable(false);
        partialSpeechArea.setLineWrap(true);
        partialSpeechArea.setWrapStyleWord(true);
        partialSpeechArea.setBorder(BorderFactory.createTitledBorder("实时识别中（未结束）"));

        JSplitPane upperSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(transcriptArea),
                new JScrollPane(resumeArea)
        );
        upperSplit.setResizeWeight(0.5);

        JSplitPane speechSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                upperSplit,
                new JScrollPane(partialSpeechArea)
        );
        speechSplit.setResizeWeight(0.7);

        intentArea.setEditable(false);
        intentArea.setLineWrap(true);
        intentArea.setWrapStyleWord(true);
        intentArea.setBorder(BorderFactory.createTitledBorder("提问意图"));

        pointsArea.setEditable(false);
        pointsArea.setLineWrap(true);
        pointsArea.setWrapStyleWord(true);
        pointsArea.setBorder(BorderFactory.createTitledBorder("回答要点"));

        answerArea.setEditable(false);
        answerArea.setLineWrap(true);
        answerArea.setWrapStyleWord(true);
        answerArea.setBorder(BorderFactory.createTitledBorder("参考回答"));

        JSplitPane lowerTopSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(intentArea),
                new JScrollPane(pointsArea)
        );
        lowerTopSplit.setResizeWeight(0.4);

        JSplitPane lowerSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                lowerTopSplit,
                new JScrollPane(answerArea)
        );
        lowerSplit.setResizeWeight(0.42);

        JSplitPane mainSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                speechSplit,
                lowerSplit
        );
        mainSplit.setResizeWeight(0.45);

        add(topPanel, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        importResumeButton.addActionListener(e -> onImportResume());
        analyzeButton.addActionListener(e -> onAnalyze());
        clearButton.addActionListener(e -> clearResult());
        listenButton.addActionListener(e -> toggleListening());
        refreshMixerButton.addActionListener(e -> refreshMixers());
        audioHelpButton.addActionListener(e -> showAudioSetupHelp());

        refreshMixers();
    }

    private void refreshMixers() {
        mixerCombo.removeAllItems();
        try {
            boolean nativeHelperAvailable = SystemAudioCaptureProviderFactory.hasNativeHelper();
            if (nativeHelperAvailable) {
                mixerCombo.addItem(NATIVE_SYSTEM_AUDIO_OPTION);
            }

            Mixer.Info[] infos = AudioSystem.getMixerInfo();
            java.util.ArrayList<String> loopbackInputs = new java.util.ArrayList<String>();
            java.util.ArrayList<String> otherInputs = new java.util.ArrayList<String>();
            for (int i = 0; i < infos.length; i++) {
                Mixer.Info info = infos[i];
                if (info == null) {
                    continue;
                }
                Mixer mixer = AudioSystem.getMixer(info);
                if (!isInputCapable(mixer)) {
                    continue;
                }
                String name = info.getName();
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                String displayName = buildMixerDisplayName(info);
                if (isVirtualCableInput(info)) {
                    loopbackInputs.add(0, displayName);
                } else if (isSystemAudioInput(info)) {
                    loopbackInputs.add(displayName);
                } else {
                    otherInputs.add(displayName + "（麦克风/普通输入，不建议）");
                }
            }
            for (int i = 0; i < loopbackInputs.size(); i++) {
                mixerCombo.addItem(loopbackInputs.get(i));
            }
            for (int i = 0; i < otherInputs.size(); i++) {
                mixerCombo.addItem(otherInputs.get(i));
            }

            String preferred = config.getAsrMixerName();
            if (nativeHelperAvailable) {
                mixerCombo.setSelectedIndex(0);
                statusLabel.setText("状态: 已启用内置系统音频采集，可直接监听电脑会议声音");
            } else if (preferred != null && !preferred.trim().isEmpty()) {
                for (int i = 0; i < mixerCombo.getItemCount(); i++) {
                    String item = stripUiSuffix(mixerCombo.getItemAt(i));
                    if (preferred.equalsIgnoreCase(item)) {
                        mixerCombo.setSelectedIndex(i);
                        break;
                    }
                }
            } else if (loopbackInputs.size() > 0) {
                mixerCombo.setSelectedIndex(0);
            } else if (mixerCombo.getItemCount() > 0) {
                mixerCombo.setSelectedIndex(0);
                statusLabel.setText("状态: 未发现系统声音/回环输入源，请启用立体声混音、VB-CABLE、BlackHole 或 Loopback");
            }

            if (mixerCombo.getItemCount() == 0) {
                statusLabel.setText("状态: 未发现可用输入源，请检查系统录音设备");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "刷新音频输入源失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean isInputCapable(Mixer mixer) {
        if (mixer == null) {
            return false;
        }
        Line.Info[] lineInfos = mixer.getTargetLineInfo();
        if (lineInfos == null || lineInfos.length == 0) {
            return false;
        }
        for (int i = 0; i < lineInfos.length; i++) {
            if (!(lineInfos[i] instanceof DataLine.Info)) {
                continue;
            }
            DataLine.Info info = (DataLine.Info) lineInfos[i];
            if (TargetDataLine.class.isAssignableFrom(info.getLineClass())) {
                return true;
            }
        }
        return false;
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

    private boolean isVirtualCableInput(Mixer.Info info) {
        return isVirtualCableText(getMixerSearchText(info));
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

    private void showAudioSetupHelp() {
        String message = "商用上线目标：用户不安装 VB-CABLE、BlackHole、Loopback 等第三方工具，只安装你的客户端。\n\n"
                + "实现方式：客户端随包内置系统音频采集组件。\n"
                + "Windows: native/windows/wasapi-loopback-capture.exe，使用 WASAPI Loopback 捕获默认播放设备声音。\n"
                + "macOS: native/macos/system-audio-capture，使用 ScreenCaptureKit 捕获系统声音。\n\n"
                + "如果界面里出现“内置系统音频采集（推荐，无需 VB-CABLE）”，说明 helper 已打包，可直接使用。\n"
                + "如果没有出现，说明当前开发环境还没有 helper 二进制文件，程序只能回退到 JavaSound，所以你才会看到 Stereo Mix/VB-CABLE 这类旧选项。\n\n"
                + "注意：我已经完成 Java 端接入架构，但还需要继续实现并打包 native helper 二进制文件，才能真正做到用户无需安装第三方音频工具。";
        JOptionPane.showMessageDialog(this, message, "音频设置帮助", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onImportResume() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择简历 PDF 文件");
        chooser.setFileFilter(new FileNameExtensionFilter("PDF 文件", "pdf"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selectedFile = chooser.getSelectedFile();
        statusLabel.setText("状态: 正在解析简历...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return pdfResumeParser.extractText(selectedFile);
            }

            @Override
            protected void done() {
                try {
                    String text = get();
                    if (text.trim().isEmpty()) {
                        statusLabel.setText("状态: 简历解析完成，但未提取到文本");
                        JOptionPane.showMessageDialog(MainFrame.this, "未识别到简历文本，请确认 PDF 不是图片扫描版。");
                        return;
                    }
                    resumeArea.setText(text);
                    statusLabel.setText("状态: 简历解析完成");
                } catch (Exception ex) {
                    statusLabel.setText("状态: 简历解析失败");
                    JOptionPane.showMessageDialog(MainFrame.this, "解析失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void onAnalyze() {
        String transcript = transcriptArea.getText().trim();
        if (transcript.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先输入或粘贴面试官问题转写内容。");
            return;
        }
        analyzeText(transcript, false);
    }

    private void analyzeText(String transcript, boolean autoTriggered) {
        String resumeText = resumeArea.getText().trim();
        statusLabel.setText(autoTriggered ? "状态: 已识别一句话，正在自动生成建议..." : "状态: 正在调用 DeepSeek 生成回答建议...");

        analyzeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    InterviewAnalysis analysis = deepSeekClient.analyzeQuestion(transcript, resumeText);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            intentArea.setText(analysis.getQuestionIntent());
                            pointsArea.setText(analysis.getKeyPoints());
                            answerArea.setText(analysis.getSuggestedAnswer());
                            statusLabel.setText("状态: 生成完成");
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            statusLabel.setText("状态: 生成失败");
                            JOptionPane.showMessageDialog(MainFrame.this, "调用失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        });
    }

    private void toggleListening() {
        if (speechListenerService.isRunning()) {
            speechListenerService.stop();
            listenButton.setText("开始监听");
            return;
        }

        String selectedMixer = mixerCombo.getSelectedItem() == null ? "" : mixerCombo.getSelectedItem().toString();
        boolean useNativeSystemAudio = NATIVE_SYSTEM_AUDIO_OPTION.equals(selectedMixer);
        String cleanedMixer = useNativeSystemAudio ? "" : stripUiSuffix(selectedMixer);
        boolean looksLikeMicrophone = !useNativeSystemAudio && (selectedMixer.contains("不建议") || isMicrophoneName(selectedMixer));
        if (selectedMixer.trim().isEmpty() || looksLikeMicrophone) {
            JOptionPane.showMessageDialog(this,
                    "当前选择的不是系统声音/回环输入，不能开始监听。\n\n本项目只监听对方讲话/系统输出声音，不监听你自己的麦克风。\n\n如果是商用上线版本，请打包内置系统音频采集组件：\nWindows: native/windows/wasapi-loopback-capture.exe\nmacOS: native/macos/system-audio-capture\n\n当前未检测到内置采集组件，所以只能显示 JavaSound 可见的回环设备。",
                    "请选择系统声音输入源",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!useNativeSystemAudio && !isVirtualCableName(cleanedMixer) && isStereoMixName(cleanedMixer)) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "你当前选择的是 Stereo Mix/立体声混音。它依赖 Realtek 物理播放链路，无法稳定支持蓝牙耳机、USB耳机、HDMI、无音响台式机等场景。\n\n商用上线版本应随客户端内置系统音频采集组件：Windows 使用 WASAPI Loopback，macOS 使用 ScreenCaptureKit。当前如果没有打包该组件，会回退到 JavaSound/Stereo Mix。\n\n是否仍然用 Stereo Mix 继续测试？",
                    "建议使用内置系统音频采集组件",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        selectedMixer = cleanedMixer;

        speechListenerService.start(new SpeechListenerService.Callback() {
            @Override
            public void onStatus(String text) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("状态: " + text);
                        if ("监听已停止".equals(text)) {
                            listenButton.setText("开始监听");
                        } else if (text.startsWith("监听中")) {
                            listenButton.setText("停止监听");
                        }
                    }
                });
            }

            @Override
            public void onPartial(String text) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        partialSpeechArea.setText(text);
                    }
                });
            }

            @Override
            public void onFinalSentence(String text) {
                if (text.length() < config.getMinAutoAnalyzeLength()) {
                    return;
                }
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        partialSpeechArea.setText("");
                        appendTranscript(text);
                    }
                });
                analyzeText(text, true);
            }

            @Override
            public void onError(String text) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        listenButton.setText("开始监听");
                        statusLabel.setText("状态: 监听失败");
                        JOptionPane.showMessageDialog(MainFrame.this, text, "错误", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        }, selectedMixer);
    }

    private String buildMixerDisplayName(Mixer.Info info) {
        String name = info.getName() == null ? "" : info.getName().trim();
        String description = info.getDescription() == null ? "" : info.getDescription().trim();
        if (description.isEmpty() || name.toLowerCase().contains(description.toLowerCase())) {
            return name;
        }
        return name + "  [" + description + "]";
    }

    private String stripUiSuffix(String mixerName) {
        if (mixerName == null) {
            return "";
        }
        int detailStart = mixerName.indexOf("  [");
        String cleaned = detailStart >= 0 ? mixerName.substring(0, detailStart) : mixerName;
        return cleaned.replace("（麦克风/普通输入，不建议）", "").trim();
    }

    private boolean isVirtualCableName(String mixerName) {
        String lower = mixerName == null ? "" : mixerName.toLowerCase();
        return isVirtualCableText(lower);
    }

    private boolean isStereoMixName(String mixerName) {
        String lower = mixerName == null ? "" : mixerName.toLowerCase();
        return lower.contains("stereo mix")
                || lower.contains("立体声混音")
                || lower.contains("立體聲混音")
                || lower.contains("what u hear")
                || lower.contains("what you hear")
                || lower.contains("wave out mix");
    }

    private boolean isMicrophoneName(String mixerName) {
        String lower = mixerName == null ? "" : mixerName.toLowerCase();
        return lower.contains("microphone")
                || lower.contains("mic")
                || lower.contains("麦克风")
                || lower.contains("麦克風")
                || lower.contains("array");
    }

    private void appendTranscript(String text) {
        String current = transcriptArea.getText();
        if (current.trim().isEmpty()) {
            transcriptArea.setText(text);
            return;
        }
        transcriptArea.setText(current + "\n" + text);
    }

    private void clearResult() {
        intentArea.setText("");
        pointsArea.setText("");
        answerArea.setText("");
        partialSpeechArea.setText("");
        statusLabel.setText("状态: 已清空输出");
    }
}
