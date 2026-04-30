# macOS 系统音频采集组件

本目录包含 macOS ScreenCaptureKit 系统音频采集 helper 源码。

Java 客户端会自动检测：

```text
native/macos/system-audio-capture
```

如果存在该文件，程序会优先使用它捕获 macOS 系统声音，而不是依赖 BlackHole、Loopback 或麦克风。

## 构建

需要 macOS 13+、Xcode Command Line Tools 或 Xcode。

```bash
xcode-select --install
cd native/macos/system-audio-capture
chmod +x build.sh
./build.sh
```

脚本会生成并复制：

```text
native/macos/system-audio-capture
```

## 进程协议

Java 启动 helper 时会传入：

```text
--format pcm_s16le --sample-rate 16000 --channels 1
```

helper 会将 16kHz、单声道、signed 16-bit little-endian PCM 原始字节持续写入 stdout。

stderr 输出诊断日志，Java 会继承 stderr 便于诊断。

## 权限

ScreenCaptureKit 捕获系统音频需要用户授权。首次运行时 macOS 可能要求在：

```text
系统设置 > 隐私与安全性 > 屏幕录制
```

给你的 App/Java/终端授权。正式打包时应给最终 App 签名、公证，并引导用户授权。

## 能力

- Swift / ScreenCaptureKit
- 捕获 macOS 系统音频
- 重采样为 16k mono s16le
- stdout 输出裸 PCM，不输出 WAV header
- 用户不需要安装 BlackHole/Loopback
