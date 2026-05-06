package com.interviewassistant.ui;

import com.interviewassistant.model.InterviewAnalysis;
import com.interviewassistant.service.AppConfig;
import com.interviewassistant.service.BailianDeepSeekClient;
import com.interviewassistant.service.PdfResumeParser;
import com.interviewassistant.service.SpeechListenerService;
import com.interviewassistant.service.audio.SystemAudioCaptureProviderFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainFrame extends JFrame {
    private final JTextArea transcriptArea = new JTextArea();
    private final JTextArea pointsArea = new JTextArea();
    private final JTextArea answerArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("就绪");
    private final JLabel resumeStatusLabel = new JLabel("未导入简历");
    private final JLabel audioStatusLabel = new JLabel("系统音频采集组件检测中...");

    private final AppConfig config = new AppConfig();
    private final PdfResumeParser pdfResumeParser = new PdfResumeParser();
    private final BailianDeepSeekClient deepSeekClient = new BailianDeepSeekClient(config);
    private final SpeechListenerService speechListenerService = new SpeechListenerService(config);
    private final ExecutorService analyzeExecutor = Executors.newSingleThreadExecutor();

    private String resumeText = "";
    private JButton listenButton;

    public MainFrame() {
        super("远程面试助手");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(new Dimension(1180, 760));
        setMinimumSize(new Dimension(980, 660));
        setLocationRelativeTo(null);
        initUi();
    }

    private void initUi() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(16, 18, 12, 18));
        root.setBackground(new Color(245, 247, 251));
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);
        JLabel title = new JLabel("远程面试助手");
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 24));
        JLabel subtitle = new JLabel("自动监听电脑会议声音，生成回答要点与参考回答");
        subtitle.setForeground(new Color(90, 99, 115));
        subtitle.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        JPanel titlePanel = new JPanel(new GridLayout(2, 1));
        titlePanel.setOpaque(false);
        titlePanel.add(title);
        titlePanel.add(subtitle);
        header.add(titlePanel, BorderLayout.WEST);
        header.add(buildToolbar(), BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        transcriptArea.setLineWrap(true);
        transcriptArea.setWrapStyleWord(true);
        transcriptArea.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 15));
        transcriptArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        pointsArea.setEditable(false);
        pointsArea.setLineWrap(true);
        pointsArea.setWrapStyleWord(true);
        pointsArea.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 15));
        pointsArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        answerArea.setEditable(false);
        answerArea.setLineWrap(true);
        answerArea.setWrapStyleWord(true);
        answerArea.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 15));
        answerArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JSplitPane leftSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                createCard("面试官问题", new JScrollPane(transcriptArea)),
                createCard("回答要点", new JScrollPane(pointsArea))
        );
        leftSplit.setResizeWeight(0.55);
        leftSplit.setBorder(null);
        leftSplit.setDividerSize(8);

        JSplitPane mainSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftSplit,
                createCard("参考回答", new JScrollPane(answerArea))
        );
        mainSplit.setResizeWeight(0.42);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(8);
        root.add(mainSplit, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        statusLabel.setForeground(new Color(55, 65, 81));
        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(audioStatusLabel, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        refreshSystemAudioStatus();
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        toolbar.setOpaque(false);

        JButton importResumeButton = createButton("导入简历", new Color(238, 242, 255), new Color(67, 56, 202));
        JButton analyzeButton = createButton("生成回答", new Color(37, 99, 235), Color.WHITE);
        JButton clearButton = createButton("清空", new Color(243, 244, 246), new Color(55, 65, 81));
        JButton audioHelpButton = createButton("音频说明", new Color(243, 244, 246), new Color(55, 65, 81));
        listenButton = createButton("开始监听", new Color(22, 163, 74), Color.WHITE);

        resumeStatusLabel.setForeground(new Color(107, 114, 128));
        resumeStatusLabel.setBorder(new EmptyBorder(0, 0, 0, 8));

        toolbar.add(resumeStatusLabel);
        toolbar.add(importResumeButton);
        toolbar.add(listenButton);
        toolbar.add(analyzeButton);
        toolbar.add(clearButton);
        toolbar.add(audioHelpButton);

        importResumeButton.addActionListener(e -> onImportResume());
        analyzeButton.addActionListener(e -> onAnalyze());
        clearButton.addActionListener(e -> clearResult());
        listenButton.addActionListener(e -> toggleListening());
        audioHelpButton.addActionListener(e -> showAudioSetupHelp());
        return toolbar;
    }

    private JButton createButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                new EmptyBorder(7, 13, 7, 13)
        ));
        return button;
    }

    private JPanel createCard(String title, JScrollPane content) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                new EmptyBorder(12, 12, 12, 12)
        ));
        JLabel label = new JLabel(title);
        label.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 15));
        label.setForeground(new Color(17, 24, 39));
        content.setBorder(BorderFactory.createLineBorder(new Color(241, 245, 249)));
        card.add(label, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void refreshSystemAudioStatus() {
        if (SystemAudioCaptureProviderFactory.hasNativeHelper()) {
            audioStatusLabel.setText("音频: 内置系统采集已就绪");
            audioStatusLabel.setForeground(new Color(22, 101, 52));
        } else {
            audioStatusLabel.setText("音频: 未检测到内置采集组件");
            audioStatusLabel.setForeground(new Color(185, 28, 28));
        }
    }

    private void showAudioSetupHelp() {
        JOptionPane.showMessageDialog(this,
                "本版本只保留主方案：内置系统音频采集。\n\n"
                        + "Windows: native/windows/wasapi-loopback-capture.exe\n"
                        + "macOS: native/macos/system-audio-capture\n\n"
                        + "如果底部显示未检测到内置采集组件，请重新打包客户端或检查 native helper 是否存在。",
                "音频说明",
                JOptionPane.INFORMATION_MESSAGE);
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
        statusLabel.setText("正在解析简历...");
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
                        statusLabel.setText("简历解析完成，但未提取到文本");
                        JOptionPane.showMessageDialog(MainFrame.this, "未识别到简历文本，请确认 PDF 不是图片扫描版。");
                        return;
                    }
                    resumeText = text;
                    resumeStatusLabel.setText("简历已导入");
                    resumeStatusLabel.setForeground(new Color(22, 101, 52));
                    statusLabel.setText("简历导入成功");
                } catch (Exception ex) {
                    statusLabel.setText("简历解析失败");
                    JOptionPane.showMessageDialog(MainFrame.this, "解析失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void onAnalyze() {
        String transcript = transcriptArea.getText().trim();
        if (transcript.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先输入或等待识别到面试官问题。");
            return;
        }
        analyzeText(transcript, false);
    }

    private void analyzeText(String transcript, boolean autoTriggered) {
        statusLabel.setText(autoTriggered ? "已识别一句话，正在生成回答..." : "正在生成回答...");

        analyzeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    InterviewAnalysis analysis = deepSeekClient.analyzeQuestion(transcript, resumeText);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            pointsArea.setText(analysis.getKeyPoints());
                            answerArea.setText(analysis.getSuggestedAnswer());
                            statusLabel.setText("生成完成");
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            statusLabel.setText("生成失败");
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
            listenButton.setBackground(new Color(22, 163, 74));
            return;
        }

        if (!SystemAudioCaptureProviderFactory.hasNativeHelper()) {
            JOptionPane.showMessageDialog(this,
                    "未检测到内置系统音频采集组件，不能开始监听。\n\n请确认已打包：\nWindows: native/windows/wasapi-loopback-capture.exe\nmacOS: native/macos/system-audio-capture",
                    "缺少音频采集组件",
                    JOptionPane.ERROR_MESSAGE);
            refreshSystemAudioStatus();
            return;
        }

        speechListenerService.start(new SpeechListenerService.Callback() {
            @Override
            public void onStatus(String text) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText(text);
                        if ("监听已停止".equals(text)) {
                            listenButton.setText("开始监听");
                            listenButton.setBackground(new Color(22, 163, 74));
                        } else if (text.startsWith("监听") || text.startsWith("正在使用")) {
                            listenButton.setText("停止监听");
                            listenButton.setBackground(new Color(220, 38, 38));
                        }
                    }
                });
            }

            @Override
            public void onPartial(String text) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (!text.trim().isEmpty()) {
                            appendTranscript(text.trim(), true);
                        }
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
                        appendTranscript(text, false);
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
                        listenButton.setBackground(new Color(22, 163, 74));
                        statusLabel.setText("监听失败");
                        JOptionPane.showMessageDialog(MainFrame.this, text, "错误", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        }, "");
    }

    private void appendTranscript(String text, boolean partial) {
        String current = transcriptArea.getText();
        if (partial) {
            statusLabel.setText("识别中: " + text);
            return;
        }
        if (current.trim().isEmpty()) {
            transcriptArea.setText(text);
        } else {
            transcriptArea.setText(current + "\n" + text);
        }
    }

    private void clearResult() {
        transcriptArea.setText("");
        pointsArea.setText("");
        answerArea.setText("");
        statusLabel.setText("已清空");
    }
}
