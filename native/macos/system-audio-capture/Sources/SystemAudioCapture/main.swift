import Foundation
import AVFoundation
import CoreMedia
import ScreenCaptureKit

final class StderrLogger {
    static func info(_ message: String) {
        FileHandle.standardError.write(Data("[SC] \(message)\n".utf8))
    }

    static func error(_ message: String) {
        FileHandle.standardError.write(Data("[SC] ERROR \(message)\n".utf8))
    }
}

struct Arguments {
    let format: String
    let sampleRate: Int
    let channels: Int

    static func parse() -> Arguments {
        var values: [String: String] = [:]
        let args = CommandLine.arguments
        var index = 1
        while index + 1 < args.count {
            if args[index].hasPrefix("--") {
                values[args[index]] = args[index + 1]
                index += 2
            } else {
                index += 1
            }
        }
        return Arguments(
            format: values["--format"] ?? "pcm_s16le",
            sampleRate: Int(values["--sample-rate"] ?? "16000") ?? 16000,
            channels: Int(values["--channels"] ?? "1") ?? 1
        )
    }
}

final class PcmResampler {
    private let outputFormat: AVAudioFormat
    private var converter: AVAudioConverter?

    init(sampleRate: Double, channels: AVAudioChannelCount) {
        outputFormat = AVAudioFormat(commonFormat: .pcmFormatInt16,
                                     sampleRate: sampleRate,
                                     channels: channels,
                                     interleaved: true)!
    }

    func convert(sampleBuffer: CMSampleBuffer) -> Data? {
        guard let blockBuffer = CMSampleBufferGetDataBuffer(sampleBuffer),
              let formatDescription = CMSampleBufferGetFormatDescription(sampleBuffer),
              let streamDescriptionPointer = CMAudioFormatDescriptionGetStreamBasicDescription(formatDescription) else {
            return nil
        }

        var lengthAtOffset = 0
        var totalLength = 0
        var dataPointer: UnsafeMutablePointer<Int8>?
        let status = CMBlockBufferGetDataPointer(blockBuffer,
                                                 atOffset: 0,
                                                 lengthAtOffsetOut: &lengthAtOffset,
                                                 totalLengthOut: &totalLength,
                                                 dataPointerOut: &dataPointer)
        guard status == kCMBlockBufferNoErr, let dataPointer else {
            return nil
        }

        let inputASBD = streamDescriptionPointer.pointee
        guard let inputFormat = AVAudioFormat(streamDescription: streamDescriptionPointer) else {
            return nil
        }

        if converter == nil || converter?.inputFormat != inputFormat {
            converter = AVAudioConverter(from: inputFormat, to: outputFormat)
        }
        guard let converter else {
            return nil
        }

        let bytesPerFrame = Int(max(inputASBD.mBytesPerFrame, 1))
        let frameCount = AVAudioFrameCount(totalLength / bytesPerFrame)
        guard frameCount > 0 else {
            return nil
        }

        guard let inputBuffer = AVAudioPCMBuffer(pcmFormat: inputFormat, frameCapacity: frameCount) else {
            return nil
        }
        inputBuffer.frameLength = frameCount

        if inputFormat.isInterleaved {
            guard let dst = inputBuffer.audioBufferList.pointee.mBuffers.mData else {
                return nil
            }
            memcpy(dst, dataPointer, totalLength)
            inputBuffer.audioBufferList.pointee.mBuffers.mDataByteSize = UInt32(totalLength)
        } else {
            // ScreenCaptureKit normally supplies interleaved audio. If not, skip safely.
            return nil
        }

        let ratio = outputFormat.sampleRate / inputFormat.sampleRate
        let outputCapacity = AVAudioFrameCount(Double(frameCount) * ratio + 1024)
        guard let outputBuffer = AVAudioPCMBuffer(pcmFormat: outputFormat, frameCapacity: outputCapacity) else {
            return nil
        }

        var didProvideInput = false
        var conversionError: NSError?
        let inputBlock: AVAudioConverterInputBlock = { _, outStatus in
            if didProvideInput {
                outStatus.pointee = .noDataNow
                return nil
            }
            didProvideInput = true
            outStatus.pointee = .haveData
            return inputBuffer
        }

        converter.convert(to: outputBuffer, error: &conversionError, withInputFrom: inputBlock)
        if conversionError != nil || outputBuffer.frameLength == 0 {
            return nil
        }

        let bytes = Int(outputBuffer.frameLength) * Int(outputFormat.streamDescription.pointee.mBytesPerFrame)
        guard let outData = outputBuffer.audioBufferList.pointee.mBuffers.mData else {
            return nil
        }
        return Data(bytes: outData, count: bytes)
    }
}

@available(macOS 13.0, *)
final class SystemAudioCapture: NSObject, SCStreamOutput, SCStreamDelegate {
    private let resampler = PcmResampler(sampleRate: 16000, channels: 1)
    private var stream: SCStream?
    private let output = FileHandle.standardOutput

    func start() async throws {
        let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: false)
        guard let display = content.displays.first else {
            throw NSError(domain: "SystemAudioCapture", code: 1, userInfo: [NSLocalizedDescriptionKey: "No display available for ScreenCaptureKit"])
        }

        let filter = SCContentFilter(display: display, excludingWindows: [])
        let configuration = SCStreamConfiguration()
        configuration.capturesAudio = true
        configuration.excludesCurrentProcessAudio = false
        configuration.sampleRate = 48000
        configuration.channelCount = 2
        configuration.width = 2
        configuration.height = 2
        configuration.minimumFrameInterval = CMTime(value: 1, timescale: 1)

        let stream = SCStream(filter: filter, configuration: configuration, delegate: self)
        try stream.addStreamOutput(self, type: .audio, sampleHandlerQueue: DispatchQueue(label: "system-audio-capture.audio"))
        self.stream = stream
        try await stream.startCapture()
        StderrLogger.info("started ScreenCaptureKit system audio")
    }

    func stop() async {
        if let stream {
            try? await stream.stopCapture()
        }
        StderrLogger.info("stopped")
    }

    func stream(_ stream: SCStream, didStopWithError error: Error) {
        StderrLogger.error("stream stopped: \(error.localizedDescription)")
        exit(2)
    }

    func stream(_ stream: SCStream, didOutputSampleBuffer sampleBuffer: CMSampleBuffer, of outputType: SCStreamOutputType) {
        guard outputType == .audio, sampleBuffer.isValid else {
            return
        }
        guard let pcm = resampler.convert(sampleBuffer: sampleBuffer), !pcm.isEmpty else {
            return
        }
        do {
            try output.write(contentsOf: pcm)
        } catch {
            StderrLogger.error("stdout write failed: \(error.localizedDescription)")
            exit(3)
        }
    }
}

func installSignalHandlers(_ onSignal: @escaping () -> Void) {
    signal(SIGINT) { _ in exit(0) }
    signal(SIGTERM) { _ in exit(0) }
    _ = onSignal
}

let arguments = Arguments.parse()
if arguments.format != "pcm_s16le" || arguments.sampleRate != 16000 || arguments.channels != 1 {
    StderrLogger.error("only --format pcm_s16le --sample-rate 16000 --channels 1 is supported")
    exit(64)
}

if #available(macOS 13.0, *) {
    let capture = SystemAudioCapture()
    installSignalHandlers {
        Task { await capture.stop() }
    }
    Task {
        do {
            try await capture.start()
        } catch {
            StderrLogger.error(error.localizedDescription)
            exit(1)
        }
    }
    RunLoop.main.run()
} else {
    StderrLogger.error("macOS 13.0 or later is required for ScreenCaptureKit system audio capture")
    exit(70)
}
