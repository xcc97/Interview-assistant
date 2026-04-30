# 远程面试助手（Java）

这是一个桌面端远程面试辅助工具原型，核心能力：

- 导入 PDF 简历并解析文本
- 自动监听语音并实时转写（VOSK 本地识别）
- 自动判定一句话结束后触发回答生成
- 输入/粘贴面试官语音转写内容（兼容手动模式）
- 调用阿里云百炼兼容模式下的 DeepSeek 模型生成：
  - 提问意图
  - 回答要点
  - 参考回答（可直接口述）

## 技术栈

- Java 8+
- Swing（桌面 UI）
- Apache PDFBox（PDF 文本提取）
- OkHttp + Jackson（调用百炼 DeepSeek API）
- VOSK（本地语音识别）

## 快速开始

1. 安装 Java 8+ 与 Maven 3.9+
2. 配置 API Key（推荐环境变量）

Windows PowerShell：

```powershell
$env:BAILIAN_API_KEY="你的阿里云百炼APIKey"
```

macOS / zsh：

```bash
export BAILIAN_API_KEY="你的阿里云百炼APIKey"
```

也可以在 `src/main/resources/application.properties` 中填写 `bailian.apiKey`。

3. 下载 VOSK 中文模型（示例：`vosk-model-small-cn-0.22`），解压到本机目录，例如：

Windows：`D:\models\vosk-model-small-cn-0.22`

macOS：`/Users/yourname/models/vosk-model-small-cn-0.22`

4. 配置模型路径（二选一）

Windows PowerShell：

```powershell
$env:VOSK_MODEL_PATH="D:\models\vosk-model-small-cn-0.22"
```

macOS / zsh：

```bash
export VOSK_MODEL_PATH="/Users/yourname/models/vosk-model-small-cn-0.22"
```

也可以在 `src/main/resources/application.properties` 填 `asr.voskModelPath`。

5. 运行

```bash
mvn compile exec:java
```

## 使用步骤

1. 点击 `导入简历 PDF`，选择你的简历
2. 点击 `开始监听`
3. 当识别到一句完整话语（中间短停顿不会触发，结束停顿会触发）后，系统会自动：
   - 追加到“面试官问题”区域
   - 调用 DeepSeek 生成提问意图、回答要点、参考回答
4. 你也可手动输入文本并点击 `分析并生成回答`

## 会议场景建议

- 如果你希望“扬声器和耳机都能通用”（不管你把面试声音输出到哪里），关键是：让程序识别到“系统输出回环”的音频，而不是普通麦克风。
- Windows：尽量在系统里启用录音设备 `立体声混音 / Stereo Mix`（或声卡提供的 Loopback），然后在程序窗口的 `监听输入源` 下拉框里选择它。
- mac：通常需要安装虚拟音频驱动（如 BlackHole / Loopback），把它创建出的输入设备选为 `监听输入源`，这样程序才能识别系统输出（扬声器/耳机都通）。
- 如果你没有回环设备：可以退而求其次选择麦克风输入（你说话/环境里的声音能否采到对方取决于你现场）。
