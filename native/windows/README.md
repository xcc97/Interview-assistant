# Windows 系统音频采集组件

本目录包含 Windows WASAPI Loopback 采集 helper 源码。

Java 客户端会自动检测：

```text
native/windows/wasapi-loopback-capture.exe
```

如果存在该文件，程序会优先使用它捕获 Windows 默认播放设备的系统声音，而不是依赖 Stereo Mix、VB-CABLE 或麦克风。

## 构建

需要安装 Visual Studio Build Tools 和 CMake，然后执行：

```powershell
cd native/windows/wasapi-loopback-capture
./build.ps1
```

脚本会生成并复制：

```text
native/windows/wasapi-loopback-capture.exe
```

## 进程协议

Java 启动 helper 时会传入：

```text
--format pcm_s16le --sample-rate 16000 --channels 1
```

helper 会将 16kHz、单声道、signed 16-bit little-endian PCM 原始字节持续写入 stdout。

stderr 输出诊断日志，Java 会继承 stderr 便于诊断。

## 能力

- C++ / WASAPI Loopback Capture
- 捕获默认 render endpoint
- 支持音响、有线耳机、蓝牙耳机、USB 耳机、HDMI 等当前默认播放设备
- 将设备原始格式重采样为 16k mono s16le
- stdout 输出裸 PCM，不输出 WAV header

这样用户只需要安装本产品客户端，不需要安装 VB-CABLE。
