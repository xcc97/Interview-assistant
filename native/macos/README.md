# macOS 系统音频采集组件

上线版本应在本目录打包 `system-audio-capture`。

Java 客户端会自动检测：

```text
native/macos/system-audio-capture
```

如果存在该文件，程序会优先使用它捕获 macOS 系统声音，而不是依赖 BlackHole、Loopback 或麦克风。

## 进程协议

Java 启动 helper 时会传入：

```text
--format pcm_s16le --sample-rate 16000 --channels 1
```

helper 需要将 16kHz、单声道、signed 16-bit little-endian PCM 原始字节持续写入 stdout。

stderr 可输出日志，Java 会继承 stderr 便于诊断。

## 推荐实现

- Swift / Objective-C
- macOS 13+ 推荐 ScreenCaptureKit 捕获系统音频
- 输出裸 PCM 到 stdout，不要输出 WAV header
- 首次运行需要引导用户授予系统权限

注意：macOS 系统音频捕获受系统权限限制。用户不需要安装第三方虚拟声卡，但仍需要授权本产品客户端。
