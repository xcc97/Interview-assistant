# 远程面试助手（Java）

这是一个桌面端远程面试辅助工具原型，核心能力：

- 导入 PDF 简历并解析文本
- 自动监听会议系统音频并实时转写（阿里云实时 ASR）
- 自动判定一句话结束后触发回答生成
- 手动补充漏识别的问题
- 调用阿里云百炼兼容模式下的 DeepSeek 模型生成可直接口述的简洁回答

## 技术栈

- Java 21
- Swing（桌面 UI）
- Apache PDFBox（PDF 文本提取）
- OkHttp + Jackson（调用百炼 DeepSeek API）
- 阿里云 NLS 实时语音识别

## 快速开始

1. 安装 Java 21+
2. 配置百炼 API Key
3. 配置阿里云 NLS 实时语音识别
4. 构建并运行桌面端

### 1) 配置百炼 API Key

Windows PowerShell：

```powershell
$env:BAILIAN_API_KEY="你的阿里云百炼APIKey"
```

macOS / zsh：

```bash
export BAILIAN_API_KEY="你的阿里云百炼APIKey"
```

### 2) 配置阿里云 NLS

推荐开发阶段直接用环境变量；正式商业化时建议由你自己的后端签发临时 Token，不要把 AccessKey 固化在客户端。

方式 A：直接提供临时 Token

```bash
export ALIYUN_NLS_APP_KEY="你的NLS AppKey"
export ALIYUN_NLS_TOKEN="你的NLS Token"
```

方式 B：提供 AccessKey，由客户端运行时换 Token

```bash
export ALIYUN_NLS_APP_KEY="你的NLS AppKey"
export ALIYUN_ACCESS_KEY_ID="你的AccessKeyId"
export ALIYUN_ACCESS_KEY_SECRET="你的AccessKeySecret"
```

如有需要，也可以设置：

```bash
export ALIYUN_NLS_ENDPOINT="wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1"
```

### 3) 运行

如果本机安装了 Maven：

```bash
mvn compile exec:java
```

如果你主要在 IntelliJ 中运行，也可以直接运行 `com.interviewassistant.Main`。

## 使用步骤

1. 点击 `导入简历`，选择你的简历 PDF
2. 点击 `开始监听`
3. 当识别到一句完整问题后，系统会自动：
   - 生成一张新的问答卡片
   - 调用 DeepSeek 生成口语化回答
4. 如果语音漏识别，也可以点击 `手动补充` 手动输入问题

## 音频说明

本项目仍然使用本地系统音频采集 helper：

- Windows：`native/windows/wasapi-loopback-capture.exe`
- macOS：`native/macos/system-audio-capture`

它们负责抓取会议系统声音；真正的语音转文字已经切换为阿里云实时 ASR，不再使用本地 Vosk 模型。
