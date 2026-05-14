package com.interviewassistant.ui;

import com.interviewassistant.model.InterviewAnalysis;
import com.interviewassistant.service.AppConfig;
import com.interviewassistant.service.BackendClient;
import com.interviewassistant.service.BailianDeepSeekClient;
import com.interviewassistant.service.PdfResumeParser;
import com.interviewassistant.service.SpeechListenerService;
import com.interviewassistant.service.audio.SystemAudioCaptureProviderFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Image;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.prefs.Preferences;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainFrame extends JFrame {
    private static final String OFFICIAL_WEBSITE_URL = "http://121.43.243.42";
    private static final String REGISTER_URL = OFFICIAL_WEBSITE_URL + "/login";
    private static final String ORDERS_URL = OFFICIAL_WEBSITE_URL + "/billing";
    private static final String TERMS_URL = OFFICIAL_WEBSITE_URL + "/terms";
    private static final String PRIVACY_URL = OFFICIAL_WEBSITE_URL + "/privacy";
    private static final String COMPLIANCE_ACCEPTED_KEY = "complianceAccepted.v1";

    private final JLabel statusLabel = new JLabel("");
    private final JLabel accountInfoLabel = new JLabel("账号：未登录");
    private final JLabel resumeInfoLabel = new JLabel("简历：未导入");
    private final JLabel audioStatusLabel = new JLabel("系统音频采集组件检测中...");
    private final JPanel conversationListPanel = new JPanel();
    private final JScrollPane conversationScrollPane = new JScrollPane();

    private final AppConfig config = new AppConfig();
    private final PdfResumeParser pdfResumeParser = new PdfResumeParser();
    private final BackendClient backendClient = new BackendClient(config);
    private final BailianDeepSeekClient deepSeekClient = new BailianDeepSeekClient(config, backendClient);
    private final SpeechListenerService speechListenerService = new SpeechListenerService(config, backendClient);
    private final Preferences preferences = Preferences.userNodeForPackage(MainFrame.class);
    private final ExecutorService analyzeExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService usageExecutor = Executors.newSingleThreadExecutor();

    private final List<ConversationEntry> entries = new ArrayList<ConversationEntry>();
    private String resumeText = "";
    private String activeUsageSessionId = "";
    private long listeningStartedAtMillis = 0L;
    private int remainingSecondsAtStart = 0;
    private Timer billingTimer;
    private BackendClient.UserProfile currentUserProfile;
    private JButton loginButton;
    private JButton importResumeButton;
    private JButton listenButton;
    private JButton analyzeButton;

    public MainFrame() {
        super("nod");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(new Dimension(1180, 760));
        setMinimumSize(new Dimension(980, 660));
        setLocationRelativeTo(null);
        applyAppIcon();
        initUi();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showComplianceNoticeIfNeeded();
            }
        });
    }

    private void applyAppIcon() {
        URL iconUrl = MainFrame.class.getResource("/icons/nod.png");
        if (iconUrl != null) {
            Image icon = new ImageIcon(iconUrl).getImage();
            setIconImage(icon);
        }
    }

    private void initUi() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(16, 18, 12, 18));
        root.setBackground(new Color(245, 247, 251));
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("nod 点头 · 表达训练与复盘工具");
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 24));
        title.setForeground(new Color(15, 23, 42));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        infoRow.setOpaque(false);
        infoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        setupInfoLabel(accountInfoLabel);
        setupInfoLabel(resumeInfoLabel);
        infoRow.add(accountInfoLabel);
        infoRow.add(resumeInfoLabel);

        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(8));
        titleBlock.add(infoRow);

        header.add(titleBlock, BorderLayout.WEST);
        header.add(buildToolbar(), BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        conversationListPanel.setOpaque(false);
        conversationListPanel.setLayout(new BoxLayout(conversationListPanel, BoxLayout.Y_AXIS));

        JPanel listContainer = new JPanel(new BorderLayout());
        listContainer.setOpaque(false);
        listContainer.add(conversationListPanel, BorderLayout.NORTH);

        conversationScrollPane.setViewportView(listContainer);
        conversationScrollPane.setBorder(BorderFactory.createEmptyBorder());
        conversationScrollPane.getViewport().setBackground(new Color(245, 247, 251));

        root.add(createMainCard("表达练习与复盘记录", conversationScrollPane), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        statusLabel.setForeground(new Color(55, 65, 81));
        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(audioStatusLabel, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        refreshSystemAudioStatus();
        renderConversationCards();
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        toolbar.setOpaque(true);
        toolbar.setBackground(new Color(255, 255, 255, 210));
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                new EmptyBorder(6, 6, 6, 6)
        ));

        loginButton = createButton("登录", new Color(248, 250, 252), new Color(15, 23, 42));
        importResumeButton = createButton("导入简历", new Color(238, 242, 255), new Color(67, 56, 202));
        listenButton = createButton("开始练习", new Color(22, 163, 74), Color.WHITE);
        analyzeButton = createButton("手动补充", new Color(239, 246, 255), new Color(37, 99, 235));
        JButton websiteButton = createButton("官网注册", new Color(239, 246, 255), new Color(37, 99, 235));
        JButton clearButton = createButton("清空记录", new Color(248, 250, 252), new Color(100, 116, 139));

        toolbar.add(loginButton);
        toolbar.add(websiteButton);
        toolbar.add(importResumeButton);
        toolbar.add(listenButton);
        toolbar.add(analyzeButton);
        toolbar.add(clearButton);

        loginButton.addActionListener(e -> onLoginOrLogout());
        websiteButton.addActionListener(e -> openExternalUrl(REGISTER_URL));
        importResumeButton.addActionListener(e -> onImportResume());
        analyzeButton.addActionListener(e -> onAnalyze());
        clearButton.addActionListener(e -> clearResult());
        listenButton.addActionListener(e -> toggleListening());
        return toolbar;
    }

    private void setupInfoLabel(JLabel label) {
        label.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        label.setForeground(new Color(100, 116, 139));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                new EmptyBorder(4, 9, 4, 9)
        ));
        label.setOpaque(true);
        label.setBackground(new Color(255, 255, 255, 180));
    }

    private JButton createButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13));
        button.setPreferredSize(new Dimension(104, 38));
        button.setMinimumSize(new Dimension(104, 38));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                new EmptyBorder(9, 14, 9, 14)
        ));
        return button;
    }

    private JPanel createMainCard(String title, JScrollPane content) {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);

        JLabel label = new JLabel(title);
        label.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 18));
        label.setForeground(new Color(17, 24, 39));

        JLabel tip = new JLabel("记录会保存到账号中，可在官网个人中心查看和删除", SwingConstants.RIGHT);
        tip.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        tip.setForeground(new Color(100, 116, 139));

        heading.add(label, BorderLayout.WEST);
        heading.add(tip, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel createConversationCard(ConversationEntry entry) {
        JPanel shadow = new JPanel(new BorderLayout());
        shadow.setOpaque(true);
        shadow.setBackground(new Color(226, 232, 240));
        shadow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        shadow.setAlignmentX(Component.LEFT_ALIGNMENT);
        shadow.setBorder(new EmptyBorder(0, 0, 2, 0));

        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setOpaque(true);
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JPanel questionPanel = createBubblePanel(new Color(239, 246, 255), new Color(147, 197, 253), "练习问题", entry.question);
        JPanel answerPanel = createBubblePanel(entry.answerBackground(), entry.answerBorder(), entry.answerTitle(), entry.answerText());

        card.add(questionPanel, BorderLayout.NORTH);
        card.add(answerPanel, BorderLayout.CENTER);
        shadow.add(card, BorderLayout.CENTER);
        return shadow;
    }

    private JPanel createBubblePanel(Color background, Color borderColor, String title, String text) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(background);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JLabel label = new JLabel(title);
        label.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        label.setForeground(new Color(31, 41, 55));

        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 15));
        textArea.setForeground(new Color(30, 41, 59));
        textArea.setBorder(null);

        panel.add(label, BorderLayout.NORTH);
        panel.add(textArea, BorderLayout.CENTER);
        return panel;
    }

    private void refreshSystemAudioStatus() {
        if (SystemAudioCaptureProviderFactory.hasNativeHelper()) {
            audioStatusLabel.setText("音频采集已就绪");
            audioStatusLabel.setForeground(new Color(22, 101, 52));
        } else {
            audioStatusLabel.setText("未检测到音频采集组件");
            audioStatusLabel.setForeground(new Color(185, 28, 28));
        }
    }

    private void onLoginOrLogout() {
        if (currentUserProfile != null) {
            if (speechListenerService.isRunning()) {
                JOptionPane.showMessageDialog(this, "请先结束当前练习，再退出登录。", "练习进行中", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int result = JOptionPane.showConfirmDialog(
                    this,
                    "确定要退出当前账号吗？",
                    "退出登录",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
            backendClient.logout();
            currentUserProfile = null;
            activeUsageSessionId = "";
            updateUserStatus();
            statusLabel.setText("已退出登录");
            return;
        }
        onLogin();
    }

    private void onLogin() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 8, 8));
        JTextField phoneField = new JTextField(config.getBackendPhone(), 18);
        JPasswordField passwordField = new JPasswordField(config.getBackendPassword(), 18);
        panel.add(new JLabel("手机号"));
        panel.add(phoneField);
        panel.add(new JLabel("密码"));
        panel.add(passwordField);

        int result = JOptionPane.showConfirmDialog(this, panel, "登录 nod 点头账号", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();
        if (phone.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入手机号和密码", "登录失败", JOptionPane.WARNING_MESSAGE);
            return;
        }

        loginButton.setEnabled(false);
        statusLabel.setText("正在登录...");
        usageExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    BackendClient.AuthResult authResult = backendClient.login(phone, password);
                    currentUserProfile = authResult.getUserProfile();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            loginButton.setEnabled(true);
                            updateUserStatus();
                            statusLabel.setText("登录成功");
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            loginButton.setEnabled(true);
                            statusLabel.setText("登录失败");
                            JOptionPane.showMessageDialog(MainFrame.this, ex.getMessage(), "登录失败", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        });
    }

    private void showComplianceNoticeIfNeeded() {
        if (preferences.getBoolean(COMPLIANCE_ACCEPTED_KEY, false)) {
            return;
        }
        JTextArea notice = createNoticeArea(
                "欢迎使用 nod 点头。\n\n"
                        + "nod 点头定位为面试表达训练与复盘辅助工具。请仅在遵守法律法规、平台规则、招聘方要求，"
                        + "并获得必要授权或同意的前提下使用录音、转写、内容生成和记录保存能力。\n\n"
                        + "请勿在明确禁止录音、转写、AI 工具或外部辅助的面试、考试、测评和会议中使用本产品；"
                        + "请勿上传、记录或传播他人隐私、未公开商业秘密、保密项目信息或其他你无权处理的内容。\n\n"
                        + "如果你还没有账号，请点击客户端右上角“官网注册”前往官网注册并购买套餐。"
        );
        Object[] options = {"我已阅读并同意", "查看用户协议", "退出"};
        while (true) {
            int choice = JOptionPane.showOptionDialog(
                    this,
                    new JScrollPane(notice),
                    "合法合规使用提示",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            if (choice == 0) {
                preferences.putBoolean(COMPLIANCE_ACCEPTED_KEY, true);
                return;
            }
            if (choice == 1) {
                openExternalUrl(TERMS_URL);
                continue;
            }
            dispose();
            System.exit(0);
        }
    }

    private JTextArea createNoticeArea(String text) {
        JTextArea notice = new JTextArea(text, 11, 48);
        notice.setEditable(false);
        notice.setLineWrap(true);
        notice.setWrapStyleWord(true);
        notice.setOpaque(false);
        notice.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        notice.setBorder(new EmptyBorder(8, 8, 8, 8));
        return notice;
    }

    private boolean confirmBeforeStartingListening() {
        JTextArea notice = createNoticeArea(
                "开始前请确认：\n\n"
                        + "1. 当前场景允许你使用录音、转写、AI 辅助和记录保存能力。\n"
                        + "2. 你已获得相关对话参与方的必要授权或同意。\n"
                        + "3. 当前内容不包含他人隐私、商业秘密、保密资料或你无权处理的信息。\n"
                        + "4. AI 生成内容仅供表达训练和复盘参考，最终表达应基于你的真实经历并由你自行判断。"
        );
        int choice = JOptionPane.showConfirmDialog(
                this,
                new JScrollPane(notice),
                "开始练习前确认",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return choice == JOptionPane.OK_OPTION;
    }

    private void openExternalUrl(String url) {
        try {
            if (!Desktop.isDesktopSupported()) {
                JOptionPane.showMessageDialog(this, "请在浏览器中打开：" + url, "无法自动打开浏览器", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "请在浏览器中打开：" + url, "无法自动打开浏览器", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void updateUserStatus() {
        if (currentUserProfile == null) {
            loginButton.setText("登录");
            loginButton.setBackground(new Color(248, 250, 252));
            loginButton.setForeground(new Color(15, 23, 42));
            loginButton.setToolTipText(null);
            accountInfoLabel.setText("账号：未登录");
            accountInfoLabel.setForeground(new Color(100, 116, 139));
            return;
        }
        int remainingSeconds = currentUserProfile.getRemainingSeconds();
        String accountText = currentUserProfile.getPhone() + " · 剩余 " + formatDuration(remainingSeconds);
        loginButton.setText("已登录");
        loginButton.setBackground(currentUserProfile.getRemainingMinutes() > 0 ? new Color(220, 252, 231) : new Color(254, 226, 226));
        loginButton.setForeground(currentUserProfile.getRemainingMinutes() > 0 ? new Color(22, 101, 52) : new Color(153, 27, 27));
        loginButton.setToolTipText(accountText);
        accountInfoLabel.setText("账号：" + accountText);
        accountInfoLabel.setForeground(currentUserProfile.getRemainingMinutes() > 0 ? new Color(22, 101, 52) : new Color(153, 27, 27));
    }

    private void refreshCurrentUserAsync() {
        usageExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    currentUserProfile = backendClient.fetchCurrentUser();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            updateUserStatus();
                        }
                    });
                } catch (Exception ignored) {
                    // Keep current status.
                }
            }
        });
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
                    String resumeName = selectedFile.getName();
                    importResumeButton.setText("更新简历");
                    importResumeButton.setBackground(new Color(220, 252, 231));
                    importResumeButton.setForeground(new Color(22, 101, 52));
                    importResumeButton.setToolTipText("当前简历: " + resumeName);
                    resumeInfoLabel.setText("简历：" + resumeName);
                    resumeInfoLabel.setForeground(new Color(22, 101, 52));
                    statusLabel.setText("简历导入成功: " + resumeName);
                } catch (Exception ex) {
                    statusLabel.setText("简历解析失败");
                    JOptionPane.showMessageDialog(MainFrame.this, "解析失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void onAnalyze() {
        if (!speechListenerService.isRunning()) {
            JOptionPane.showMessageDialog(this, "请先开始练习，练习中才能使用手动补充。", "请先开始练习", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextArea inputArea = new JTextArea(6, 28);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(inputArea);
        scrollPane.setPreferredSize(new Dimension(420, 180));

        int result = JOptionPane.showConfirmDialog(
                this,
                scrollPane,
                "手动补充练习问题",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String transcript = inputArea.getText();
        if (transcript == null || transcript.trim().isEmpty()) {
            return;
        }
        analyzeText(transcript.trim(), false);
    }

    private void analyzeText(String transcript, boolean autoTriggered) {
        final ConversationEntry entry = new ConversationEntry(transcript.trim());
        entries.add(entry);
        renderConversationCards();
        statusLabel.setText(autoTriggered ? "已识别到新问题，正在生成表达建议..." : "正在生成表达建议...");

        analyzeExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    InterviewAnalysis analysis = deepSeekClient.analyzeQuestion(entry.question, resumeText);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            entry.answer = safeTrim(analysis.getSuggestedAnswer());
                            entry.loading = false;
                            renderConversationCards();
                            statusLabel.setText("表达建议生成完成");
                            saveInterviewRecordAsync(entry.question, entry.answer);
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            entry.loading = false;
                            entry.error = ex.getMessage();
                            renderConversationCards();
                            statusLabel.setText("表达建议生成失败");
                            JOptionPane.showMessageDialog(MainFrame.this, "调用失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        });
    }

    private void saveInterviewRecordAsync(String question, String answer) {
        if (!config.isBackendEnabled() || question == null || question.trim().isEmpty() || answer == null || answer.trim().isEmpty()) {
            return;
        }
        final String sessionId = activeUsageSessionId;
        usageExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    backendClient.createInterviewRecord(sessionId, question, answer);
                } catch (Exception ex) {
                    System.out.println("[INTERVIEW-RECORD] save failed: " + ex.getMessage());
                }
            }
        });
    }

    private void toggleListening() {
        if (speechListenerService.isRunning()) {
            speechListenerService.stop();
            stopBillingTimer();
            listenButton.setText("开始练习");
            listenButton.setBackground(new Color(22, 163, 74));
            listenButton.setForeground(Color.WHITE);
            updateAnalyzeButtonState();
            finishUsageSessionAsync();
            return;
        }

        if (currentUserProfile == null) {
            JOptionPane.showMessageDialog(this, "请先登录 nod 点头账号。", "需要登录", JOptionPane.WARNING_MESSAGE);
            onLoginOrLogout();
            return;
        }
        if (currentUserProfile.getRemainingSeconds() <= 0) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "可用时长不足，请先前往官网购买套餐。\n\n是否现在打开官网套餐页？",
                    "时长不足",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice == JOptionPane.YES_OPTION) {
                openExternalUrl(ORDERS_URL);
            }
            return;
        }

        if (!confirmBeforeStartingListening()) {
            return;
        }

        if (!SystemAudioCaptureProviderFactory.hasNativeHelper()) {
            JOptionPane.showMessageDialog(this,
                    "未检测到内置系统音频采集组件，不能开始练习。\n\n请确认已打包：\nWindows: native/windows/wasapi-loopback-capture.exe\nmacOS: native/macos/system-audio-capture",
                    "缺少音频采集组件",
                    JOptionPane.ERROR_MESSAGE);
            refreshSystemAudioStatus();
            return;
        }

        listenButton.setEnabled(false);
        statusLabel.setText("正在开始练习...");
        usageExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    BackendClient.UsageSession session = backendClient.startUsageSession("DESKTOP_INTERVIEW_ASSIST");
                    activeUsageSessionId = session.getSessionId();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            listenButton.setEnabled(true);
                            startBillingTimer();
                            startSpeechListening();
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            listenButton.setEnabled(true);
                            statusLabel.setText("开始练习失败");
                            JOptionPane.showMessageDialog(MainFrame.this, "开始练习失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        });
    }

    private void startSpeechListening() {
        speechListenerService.start(new SpeechListenerService.Callback() {
            @Override
            public void onStatus(String text) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        String displayText = toUserFacingListeningStatus(text);
                        if (!displayText.isEmpty()) {
                            statusLabel.setText(displayText);
                        }
                        if ("监听已停止".equals(text)) {
                            listenButton.setText("开始练习");
                            listenButton.setBackground(new Color(22, 163, 74));
                        } else if (text.startsWith("监听") || text.startsWith("正在使用")) {
                            listenButton.setText("结束练习");
                            listenButton.setBackground(new Color(239, 68, 68));
                            listenButton.setForeground(Color.WHITE);
                        }
                        updateAnalyzeButtonState();
                    }
                });
            }

            @Override
            public void onPartial(String text) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (!text.trim().isEmpty()) {
                            statusLabel.setText("识别中: " + text.trim());
                        }
                    }
                });
            }

            @Override
            public void onFinalSentence(String text) {
                if (text.length() < config.getMinAutoAnalyzeLength()) {
                    return;
                }
                analyzeText(text, true);
            }

            @Override
            public void onError(String text) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        listenButton.setText("开始练习");
                        listenButton.setBackground(new Color(22, 163, 74));
                        updateAnalyzeButtonState();
                        statusLabel.setText("练习启动失败");
                        stopBillingTimer();
                        finishUsageSessionAsync();
                        JOptionPane.showMessageDialog(MainFrame.this, text, "错误", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        }, "");
    }

    private void startBillingTimer() {
        if (currentUserProfile == null) {
            return;
        }
        stopBillingTimer();
        listeningStartedAtMillis = System.currentTimeMillis();
        remainingSecondsAtStart = Math.max(0, currentUserProfile.getRemainingSeconds());
        billingTimer = new Timer(1000, event -> updateLiveBillingCountdown());
        billingTimer.setInitialDelay(0);
        billingTimer.start();
    }

    private void updateLiveBillingCountdown() {
        if (currentUserProfile == null || remainingSecondsAtStart <= 0) {
            stopListeningBecauseBalanceExhausted();
            return;
        }
        int elapsedSeconds = (int) Math.max(0, (System.currentTimeMillis() - listeningStartedAtMillis) / 1000L);
        int liveRemainingSeconds = Math.max(0, remainingSecondsAtStart - elapsedSeconds);
        String accountText = currentUserProfile.getPhone() + " · 剩余 " + formatDuration(liveRemainingSeconds);
        loginButton.setToolTipText(accountText);
        accountInfoLabel.setText("账号：" + accountText);
        accountInfoLabel.setForeground(liveRemainingSeconds > 0 ? new Color(22, 101, 52) : new Color(153, 27, 27));
        statusLabel.setText("练习进行中 · 已使用 " + formatDuration(elapsedSeconds) + " · 剩余 " + formatDuration(liveRemainingSeconds));
        if (liveRemainingSeconds <= 0) {
            stopListeningBecauseBalanceExhausted();
        }
    }

    private void stopListeningBecauseBalanceExhausted() {
        if (!speechListenerService.isRunning()) {
            stopBillingTimer();
            return;
        }
        stopBillingTimer();
        speechListenerService.stop();
        listenButton.setText("开始练习");
        listenButton.setBackground(new Color(22, 163, 74));
        updateAnalyzeButtonState();
        statusLabel.setText("可用时长已用完，已自动结束练习");
        JOptionPane.showMessageDialog(this, "可用时长已用完，已自动结束练习。请先购买时长后再继续使用。", "时长已用完", JOptionPane.WARNING_MESSAGE);
        finishUsageSessionAsync();
    }

    private void stopBillingTimer() {
        if (billingTimer != null) {
            billingTimer.stop();
            billingTimer = null;
        }
        listeningStartedAtMillis = 0L;
        remainingSecondsAtStart = 0;
    }

    private void updateAnalyzeButtonState() {
        if (analyzeButton != null) {
            boolean running = speechListenerService.isRunning();
            analyzeButton.setEnabled(true);
            analyzeButton.setBackground(running ? new Color(37, 99, 235) : new Color(239, 246, 255));
            analyzeButton.setForeground(running ? Color.WHITE : new Color(37, 99, 235));
        }
    }

    private String toUserFacingListeningStatus(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        if ("监听已停止".equals(text)) {
            return "练习已结束";
        }
        if (text.startsWith("监听") || text.startsWith("正在使用")) {
            return "练习进行中";
        }
        return "";
    }

    private String formatDuration(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }

    private void finishUsageSessionAsync() {
        final String sessionId = activeUsageSessionId;
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }
        final boolean hasActiveInterview = speechListenerService.isRunning();
        activeUsageSessionId = "";
        usageExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    BackendClient.UsageSession session = backendClient.finishUsageSession(sessionId);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (!hasActiveInterview) {
                                statusLabel.setText("练习已结束");
                            }
                            refreshCurrentUserAsync();
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            statusLabel.setText("练习结束失败");
                            JOptionPane.showMessageDialog(MainFrame.this, "结束练习失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        });
    }

    private void renderConversationCards() {
        conversationListPanel.removeAll();

        if (entries.isEmpty()) {
            conversationListPanel.add(createEmptyState());
        } else {
            for (int i = 0; i < entries.size(); i++) {
                JPanel card = createConversationCard(entries.get(i));
                conversationListPanel.add(card);
                if (i < entries.size() - 1) {
                    conversationListPanel.add(Box.createVerticalStrut(14));
                }
            }
        }

        conversationListPanel.revalidate();
        conversationListPanel.repaint();
        scrollConversationToBottom();
    }

    private void scrollConversationToBottom() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                conversationListPanel.revalidate();
                conversationScrollPane.getViewport().revalidate();
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        conversationScrollPane.getVerticalScrollBar().setValue(conversationScrollPane.getVerticalScrollBar().getMaximum());
                    }
                });
            }
        });
    }

    private JPanel createEmptyState() {
        JPanel empty = new JPanel(new BorderLayout());
        empty.setOpaque(false);
        empty.setBorder(new EmptyBorder(80, 20, 80, 20));
        empty.setAlignmentX(Component.LEFT_ALIGNMENT);
        empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        JLabel label = new JLabel("开始练习后，这里会按顺序显示每一轮问题和表达建议", JLabel.CENTER);
        label.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
        label.setForeground(new Color(107, 114, 128));
        empty.add(label, BorderLayout.CENTER);
        return empty;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private void clearResult() {
        if (entries.isEmpty()) {
            statusLabel.setText("当前没有可清空的记录");
            return;
        }
        int result = JOptionPane.showConfirmDialog(
                this,
                "确定要清空当前所有练习记录吗？此操作不可恢复。",
                "清空记录",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        entries.clear();
        renderConversationCards();
        statusLabel.setText("记录已清空");
    }

    private static final class ConversationEntry {
        private final String question;
        private String answer = "";
        private boolean loading = true;
        private String error;

        private ConversationEntry(String question) {
            this.question = question;
        }

        private String answerTitle() {
            return error != null && !error.trim().isEmpty() ? "表达建议生成失败" : "表达建议";
        }

        private String answerText() {
            if (loading) {
                return "正在生成表达建议，请稍候...";
            }
            if (error != null && !error.trim().isEmpty()) {
                return error.trim();
            }
            if (answer == null || answer.trim().isEmpty()) {
                return "本次未生成有效表达建议。";
            }
            return answer.trim();
        }

        private Color answerBackground() {
            if (loading) {
                return new Color(255, 251, 235);
            }
            if (error != null && !error.trim().isEmpty()) {
                return new Color(254, 242, 242);
            }
            return new Color(240, 253, 244);
        }

        private Color answerBorder() {
            if (loading) {
                return new Color(253, 224, 71);
            }
            if (error != null && !error.trim().isEmpty()) {
                return new Color(252, 165, 165);
            }
            return new Color(134, 239, 172);
        }
    }
}
