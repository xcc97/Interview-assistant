#include <windows.h>
#include <mmdeviceapi.h>
#include <audioclient.h>
#include <avrt.h>
#include <functiondiscoverykeys_devpkey.h>
#include <stdint.h>
#include <fcntl.h>
#include <io.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

namespace {

constexpr int kTargetSampleRate = 16000;
constexpr int kTargetChannels = 1;
constexpr int kTargetBytesPerSample = 2;

std::atomic_bool g_running{true};

void PrintError(const char* message, HRESULT hr) {
    std::fprintf(stderr, "[WASAPI] %s failed: 0x%08lx\n", message, static_cast<unsigned long>(hr));
}

void PrintInfo(const char* message) {
    std::fprintf(stderr, "[WASAPI] %s\n", message);
}

BOOL WINAPI ConsoleCtrlHandler(DWORD ctrlType) {
    switch (ctrlType) {
        case CTRL_C_EVENT:
        case CTRL_CLOSE_EVENT:
        case CTRL_BREAK_EVENT:
        case CTRL_LOGOFF_EVENT:
        case CTRL_SHUTDOWN_EVENT:
            g_running = false;
            return TRUE;
        default:
            return FALSE;
    }
}

struct ComInit {
    HRESULT hr;
    ComInit() : hr(CoInitializeEx(nullptr, COINIT_MULTITHREADED)) {}
    ~ComInit() {
        if (SUCCEEDED(hr)) {
            CoUninitialize();
        }
    }
};

template <typename T>
void SafeRelease(T** ptr) {
    if (ptr && *ptr) {
        (*ptr)->Release();
        *ptr = nullptr;
    }
}

int ReadIntArg(int argc, char** argv, const char* name, int defaultValue) {
    for (int i = 1; i + 1 < argc; ++i) {
        if (std::strcmp(argv[i], name) == 0) {
            return std::atoi(argv[i + 1]);
        }
    }
    return defaultValue;
}

bool HasArgValue(int argc, char** argv, const char* name, const char* value) {
    for (int i = 1; i + 1 < argc; ++i) {
        if (std::strcmp(argv[i], name) == 0 && std::strcmp(argv[i + 1], value) == 0) {
            return true;
        }
    }
    return false;
}

std::string ReadStringArg(int argc, char** argv, const char* name, const char* defaultValue) {
    for (int i = 1; i + 1 < argc; ++i) {
        if (std::strcmp(argv[i], name) == 0) {
            return argv[i + 1];
        }
    }
    return defaultValue;
}

ERole ParseRole(const std::string& role) {
    if (role == "communications" || role == "communication" || role == "comm") {
        return eCommunications;
    }
    if (role == "multimedia" || role == "media") {
        return eMultimedia;
    }
    return eConsole;
}

std::string WideToUtf8(const wchar_t* text) {
    if (text == nullptr) {
        return "";
    }
    int size = WideCharToMultiByte(CP_UTF8, 0, text, -1, nullptr, 0, nullptr, nullptr);
    if (size <= 0) {
        return "";
    }
    std::string result(static_cast<size_t>(size - 1), '\0');
    WideCharToMultiByte(CP_UTF8, 0, text, -1, &result[0], size, nullptr, nullptr);
    return result;
}

std::string GetDeviceFriendlyName(IMMDevice* device) {
    if (device == nullptr) {
        return "<null>";
    }
    IPropertyStore* props = nullptr;
    HRESULT hr = device->OpenPropertyStore(STGM_READ, &props);
    if (FAILED(hr)) {
        return "<OpenPropertyStore failed>";
    }
    PROPVARIANT value;
    PropVariantInit(&value);
    hr = props->GetValue(PKEY_Device_FriendlyName, &value);
    std::string name = FAILED(hr) ? "<PKEY_Device_FriendlyName failed>" : WideToUtf8(value.pwszVal);
    PropVariantClear(&value);
    props->Release();
    return name;
}

int16_t ClampToInt16(float sample) {
    sample = std::max(-1.0f, std::min(1.0f, sample));
    return static_cast<int16_t>(std::lrintf(sample * 32767.0f));
}

float ReadSampleAsFloat(const BYTE* frame, const WAVEFORMATEX* format, int channel) {
    if (format->wFormatTag == WAVE_FORMAT_IEEE_FLOAT && format->wBitsPerSample == 32) {
        const float* samples = reinterpret_cast<const float*>(frame);
        return samples[channel];
    }

    if (format->wFormatTag == WAVE_FORMAT_PCM && format->wBitsPerSample == 16) {
        const int16_t* samples = reinterpret_cast<const int16_t*>(frame);
        return static_cast<float>(samples[channel]) / 32768.0f;
    }

    if (format->wFormatTag == WAVE_FORMAT_PCM && format->wBitsPerSample == 24) {
        const BYTE* p = frame + channel * 3;
        int32_t v = (static_cast<int32_t>(p[0]) | (static_cast<int32_t>(p[1]) << 8) | (static_cast<int32_t>(p[2]) << 16));
        if (v & 0x00800000) {
            v |= 0xFF000000;
        }
        return static_cast<float>(v) / 8388608.0f;
    }

    if (format->wFormatTag == WAVE_FORMAT_PCM && format->wBitsPerSample == 32) {
        const int32_t* samples = reinterpret_cast<const int32_t*>(frame);
        return static_cast<float>(samples[channel]) / 2147483648.0f;
    }

    return 0.0f;
}

bool IsExtensibleFloat(const WAVEFORMATEX* format) {
    if (format->wFormatTag != WAVE_FORMAT_EXTENSIBLE) {
        return false;
    }
    const WAVEFORMATEXTENSIBLE* ext = reinterpret_cast<const WAVEFORMATEXTENSIBLE*>(format);
    return IsEqualGUID(ext->SubFormat, KSDATAFORMAT_SUBTYPE_IEEE_FLOAT) && format->wBitsPerSample == 32;
}

bool IsExtensiblePcm(const WAVEFORMATEX* format) {
    if (format->wFormatTag != WAVE_FORMAT_EXTENSIBLE) {
        return false;
    }
    const WAVEFORMATEXTENSIBLE* ext = reinterpret_cast<const WAVEFORMATEXTENSIBLE*>(format);
    return IsEqualGUID(ext->SubFormat, KSDATAFORMAT_SUBTYPE_PCM);
}

float ReadExtensibleSampleAsFloat(const BYTE* frame, const WAVEFORMATEX* format, int channel) {
    if (IsExtensibleFloat(format)) {
        const float* samples = reinterpret_cast<const float*>(frame);
        return samples[channel];
    }
    if (IsExtensiblePcm(format)) {
        WAVEFORMATEX pcm = *format;
        pcm.wFormatTag = WAVE_FORMAT_PCM;
        return ReadSampleAsFloat(frame, &pcm, channel);
    }
    return ReadSampleAsFloat(frame, format, channel);
}

class LinearResampler {
public:
    explicit LinearResampler(int inputRate) : inputRate_(inputRate), sourcePosition_(0.0), lastSample_(0.0f), hasLast_(false) {}

    void Convert(const BYTE* data, UINT32 frameCount, const WAVEFORMATEX* format, bool silent, std::vector<int16_t>& out) {
        if (inputRate_ <= 0) {
            return;
        }

        const int channels = std::max<int>(1, format->nChannels);
        const int blockAlign = std::max<int>(1, format->nBlockAlign);
        std::vector<float> mono;
        mono.reserve(frameCount + 1);

        if (hasLast_) {
            mono.push_back(lastSample_);
        }

        for (UINT32 i = 0; i < frameCount; ++i) {
            float sum = 0.0f;
            if (!silent) {
                const BYTE* frame = data + i * blockAlign;
                for (int c = 0; c < channels; ++c) {
                    sum += ReadExtensibleSampleAsFloat(frame, format, c);
                }
                sum /= static_cast<float>(channels);
            }
            mono.push_back(sum);
        }

        if (mono.size() < 2) {
            if (!mono.empty()) {
                lastSample_ = mono.back();
                hasLast_ = true;
            }
            return;
        }

        const double step = static_cast<double>(inputRate_) / static_cast<double>(kTargetSampleRate);
        while (sourcePosition_ + 1.0 < static_cast<double>(mono.size())) {
            int idx = static_cast<int>(sourcePosition_);
            double frac = sourcePosition_ - static_cast<double>(idx);
            float s = static_cast<float>(mono[idx] + (mono[idx + 1] - mono[idx]) * frac);
            out.push_back(ClampToInt16(s));
            sourcePosition_ += step;
        }

        sourcePosition_ -= static_cast<double>(mono.size() - 1);
        lastSample_ = mono.back();
        hasLast_ = true;
    }

private:
    int inputRate_;
    double sourcePosition_;
    float lastSample_;
    bool hasLast_;
};

bool WriteAllStdout(const int16_t* samples, size_t count) {
    const BYTE* bytes = reinterpret_cast<const BYTE*>(samples);
    size_t total = count * sizeof(int16_t);
    while (total > 0) {
        DWORD chunk = static_cast<DWORD>(std::min<size_t>(total, 1 << 20));
        DWORD written = 0;
        if (!WriteFile(GetStdHandle(STD_OUTPUT_HANDLE), bytes, chunk, &written, nullptr)) {
            return false;
        }
        if (written == 0) {
            return false;
        }
        bytes += written;
        total -= written;
    }
    return true;
}

int RunCapture(ERole role) {
    SetConsoleCtrlHandler(ConsoleCtrlHandler, TRUE);
    _setmode(_fileno(stdout), _O_BINARY);

    ComInit com;
    if (FAILED(com.hr)) {
        PrintError("CoInitializeEx", com.hr);
        return 2;
    }

    IMMDeviceEnumerator* enumerator = nullptr;
    IMMDevice* device = nullptr;
    IAudioClient* audioClient = nullptr;
    IAudioCaptureClient* captureClient = nullptr;
    WAVEFORMATEX* mixFormat = nullptr;
    HANDLE samplesReadyEvent = nullptr;

    HRESULT hr = CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_ALL,
                                  __uuidof(IMMDeviceEnumerator), reinterpret_cast<void**>(&enumerator));
    if (FAILED(hr)) {
        PrintError("CoCreateInstance(MMDeviceEnumerator)", hr);
        return 3;
    }

    hr = enumerator->GetDefaultAudioEndpoint(eRender, role, &device);
    if (FAILED(hr)) {
        PrintError("GetDefaultAudioEndpoint", hr);
        SafeRelease(&enumerator);
        return 4;
    }

    std::string deviceName = GetDeviceFriendlyName(device);
    std::fprintf(stderr, "[WASAPI] endpoint role=%s device=%s\n",
                 role == eCommunications ? "communications" : (role == eMultimedia ? "multimedia" : "console"),
                 deviceName.c_str());

    hr = device->Activate(__uuidof(IAudioClient), CLSCTX_ALL, nullptr, reinterpret_cast<void**>(&audioClient));
    if (FAILED(hr)) {
        PrintError("IMMDevice::Activate(IAudioClient)", hr);
        SafeRelease(&device);
        SafeRelease(&enumerator);
        return 5;
    }

    hr = audioClient->GetMixFormat(&mixFormat);
    if (FAILED(hr)) {
        PrintError("IAudioClient::GetMixFormat", hr);
        SafeRelease(&audioClient);
        SafeRelease(&device);
        SafeRelease(&enumerator);
        return 6;
    }

    std::fprintf(stderr, "[WASAPI] mix format: rate=%lu channels=%u bits=%u blockAlign=%u tag=%u\n",
                 static_cast<unsigned long>(mixFormat->nSamplesPerSec), mixFormat->nChannels,
                 mixFormat->wBitsPerSample, mixFormat->nBlockAlign, mixFormat->wFormatTag);

    REFERENCE_TIME bufferDuration = 10000000; // 1s
    samplesReadyEvent = CreateEvent(nullptr, FALSE, FALSE, nullptr);
    if (!samplesReadyEvent) {
        std::fprintf(stderr, "[WASAPI] CreateEvent failed: %lu\n", GetLastError());
        CoTaskMemFree(mixFormat);
        SafeRelease(&audioClient);
        SafeRelease(&device);
        SafeRelease(&enumerator);
        return 7;
    }

    DWORD streamFlags = AUDCLNT_STREAMFLAGS_LOOPBACK | AUDCLNT_STREAMFLAGS_EVENTCALLBACK;
    hr = audioClient->Initialize(AUDCLNT_SHAREMODE_SHARED, streamFlags, bufferDuration, 0, mixFormat, nullptr);
    if (FAILED(hr)) {
        PrintError("IAudioClient::Initialize", hr);
        CloseHandle(samplesReadyEvent);
        CoTaskMemFree(mixFormat);
        SafeRelease(&audioClient);
        SafeRelease(&device);
        SafeRelease(&enumerator);
        return 8;
    }

    hr = audioClient->SetEventHandle(samplesReadyEvent);
    if (FAILED(hr)) {
        PrintError("IAudioClient::SetEventHandle", hr);
        CloseHandle(samplesReadyEvent);
        CoTaskMemFree(mixFormat);
        SafeRelease(&audioClient);
        SafeRelease(&device);
        SafeRelease(&enumerator);
        return 9;
    }

    hr = audioClient->GetService(__uuidof(IAudioCaptureClient), reinterpret_cast<void**>(&captureClient));
    if (FAILED(hr)) {
        PrintError("IAudioClient::GetService(IAudioCaptureClient)", hr);
        CloseHandle(samplesReadyEvent);
        CoTaskMemFree(mixFormat);
        SafeRelease(&audioClient);
        SafeRelease(&device);
        SafeRelease(&enumerator);
        return 10;
    }

    DWORD taskIndex = 0;
    HANDLE avrtHandle = AvSetMmThreadCharacteristicsW(L"Pro Audio", &taskIndex);

    LinearResampler resampler(static_cast<int>(mixFormat->nSamplesPerSec));
    std::vector<int16_t> output;
    output.reserve(kTargetSampleRate / 5);

    hr = audioClient->Start();
    if (FAILED(hr)) {
        PrintError("IAudioClient::Start", hr);
        if (avrtHandle) AvRevertMmThreadCharacteristics(avrtHandle);
        SafeRelease(&captureClient);
        CloseHandle(samplesReadyEvent);
        CoTaskMemFree(mixFormat);
        SafeRelease(&audioClient);
        SafeRelease(&device);
        SafeRelease(&enumerator);
        return 11;
    }

    PrintInfo("started");

    while (g_running) {
        DWORD waitResult = WaitForSingleObject(samplesReadyEvent, 200);
        if (waitResult != WAIT_OBJECT_0 && waitResult != WAIT_TIMEOUT) {
            std::fprintf(stderr, "[WASAPI] WaitForSingleObject failed: %lu\n", GetLastError());
            break;
        }

        while (g_running) {
            UINT32 packetFrames = 0;
            hr = captureClient->GetNextPacketSize(&packetFrames);
            if (FAILED(hr)) {
                PrintError("IAudioCaptureClient::GetNextPacketSize", hr);
                g_running = false;
                break;
            }
            if (packetFrames == 0) {
                break;
            }

            BYTE* data = nullptr;
            UINT32 framesAvailable = 0;
            DWORD flags = 0;
            hr = captureClient->GetBuffer(&data, &framesAvailable, &flags, nullptr, nullptr);
            if (FAILED(hr)) {
                PrintError("IAudioCaptureClient::GetBuffer", hr);
                g_running = false;
                break;
            }

            bool silent = (flags & AUDCLNT_BUFFERFLAGS_SILENT) != 0;
            output.clear();
            resampler.Convert(data, framesAvailable, mixFormat, silent, output);
            hr = captureClient->ReleaseBuffer(framesAvailable);
            if (FAILED(hr)) {
                PrintError("IAudioCaptureClient::ReleaseBuffer", hr);
                g_running = false;
                break;
            }

            if (!output.empty() && !WriteAllStdout(output.data(), output.size())) {
                std::fprintf(stderr, "[WASAPI] stdout write failed\n");
                g_running = false;
                break;
            }
        }
    }

    audioClient->Stop();
    if (avrtHandle) {
        AvRevertMmThreadCharacteristics(avrtHandle);
    }
    SafeRelease(&captureClient);
    CloseHandle(samplesReadyEvent);
    CoTaskMemFree(mixFormat);
    SafeRelease(&audioClient);
    SafeRelease(&device);
    SafeRelease(&enumerator);
    PrintInfo("stopped");
    return 0;
}

} // namespace

int main(int argc, char** argv) {
    if (!HasArgValue(argc, argv, "--format", "pcm_s16le")) {
        std::fprintf(stderr, "[WASAPI] only --format pcm_s16le is supported\n");
        return 64;
    }
    int sampleRate = ReadIntArg(argc, argv, "--sample-rate", kTargetSampleRate);
    int channels = ReadIntArg(argc, argv, "--channels", kTargetChannels);
    if (sampleRate != kTargetSampleRate || channels != kTargetChannels) {
        std::fprintf(stderr, "[WASAPI] only 16000 Hz mono output is supported\n");
        return 64;
    }
    std::string roleName = ReadStringArg(argc, argv, "--role", "console");
    return RunCapture(ParseRole(roleName));
}
