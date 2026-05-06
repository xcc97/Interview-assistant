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
    private var droppedSamples = 0

    init(sampleRate: Double, channels: AVAudioChannelCount) {
        outputFormat = AVAudioFormat(commonFormat: .pcmFormatInt16,
                                     sampleRate: sampleRate,
                                     channels: channels,
                                     interleaved: true)!
    }

    func convert(sampleBuffer: CMSampleBuffer) -> Data? {
        guard let formatDescription = CMSampleBufferGetFormatDescription(sampleBuffer),
              let streamDescriptionPointer = CMAudioFormatDescriptionGetStreamBasicDescription(formatDescription),
              let inputFormat = AVAudioFormat(streamDescription: streamDescriptionPointer) else {
            logDrop("missing audio format description")
            return nil
        }

        let frameCount = AVAudioFrameCount(CMSampleBufferGetNumSamples(sampleBuffer))
        guard frameCount > 0 else {
            logDrop("empty sample buffer")
            return nil
        }

        if converter == nil || converter?.inputFormat != inputFormat {
            converter = AVAudioConverter(from: inputFormat, to: outputFormat)
            StderrLogger.info("input format sampleRate=\(inputFormat.sampleRate) channels=\(inputFormat.channelCount) interleaved=\(inputFormat.isInterleaved) commonFormat=\(inputFormat.commonFormat.rawValue)")
        }
        guard let converter else {
            logDrop("failed to create audio converter")
            return nil
        }

        guard let inputBuffer = AVAudioPCMBuffer(pcmFormat: inputFormat, frameCapacity: frameCount) else {
            logDrop("failed to allocate input buffer")
            return nil
        }
        inputBuffer.frameLength = frameCount

        var bufferListSizeNeeded = 0
        var sizeStatus = CMSampleBufferGetAudioBufferListWithRetainedBlockBuffer(sampleBuffer,
                                                                                bufferListSizeNeededOut: &bufferListSizeNeeded,
                                                                                bufferListOut: nil,
                                                                                bufferListSize: 0,
                                                                                blockBufferAllocator: nil,
                                                                                blockBufferMemoryAllocator: nil,
                                                                                flags: 0,
                                                                                blockBufferOut: nil)
        if sizeStatus != noErr && bufferListSizeNeeded <= 0 {
            logDrop("failed to query audio buffer list size status=\(sizeStatus)")
            return nil
        }
        if bufferListSizeNeeded <= 0 {
            bufferListSizeNeeded = MemoryLayout<AudioBufferList>.size + MemoryLayout<AudioBuffer>.size * Int(max(inputFormat.channelCount, 1) - 1)
        }

        let rawBufferList = UnsafeMutableRawPointer.allocate(byteCount: bufferListSizeNeeded,
                                                            alignment: MemoryLayout<AudioBufferList>.alignment)
        defer {
            rawBufferList.deallocate()
        }
        let audioBufferList = rawBufferList.bindMemory(to: AudioBufferList.self, capacity: 1)

        var blockBuffer: CMBlockBuffer?
        let flags = UInt32(kCMSampleBufferFlag_AudioBufferList_Assure16ByteAlignment)
        let status = CMSampleBufferGetAudioBufferListWithRetainedBlockBuffer(sampleBuffer,
                                                                             bufferListSizeNeededOut: nil,
                                                                             bufferListOut: audioBufferList,
                                                                             bufferListSize: bufferListSizeNeeded,
                                                                             blockBufferAllocator: kCFAllocatorDefault,
                                                                             blockBufferMemoryAllocator: kCFAllocatorDefault,
                                                                             flags: flags,
                                                                             blockBufferOut: &blockBuffer)
        guard status == noErr else {
            logDrop("CMSampleBufferGetAudioBufferList failed status=\(status) size=\(bufferListSizeNeeded) queryStatus=\(sizeStatus)")
            return nil
        }

        let sourceBuffers = UnsafeMutableAudioBufferListPointer(audioBufferList)
        let targetBuffers = UnsafeMutableAudioBufferListPointer(inputBuffer.mutableAudioBufferList)
        guard sourceBuffers.count > 0, targetBuffers.count > 0 else {
            logDrop("audio buffer list is empty")
            return nil
        }

        if inputFormat.isInterleaved {
            guard let src = sourceBuffers[0].mData,
                  let dst = targetBuffers[0].mData else {
                logDrop("interleaved buffer data is nil")
                return nil
            }
            let byteCount = Int(sourceBuffers[0].mDataByteSize)
            memcpy(dst, src, byteCount)
            targetBuffers[0].mDataByteSize = sourceBuffers[0].mDataByteSize
        } else {
            let copyCount = min(sourceBuffers.count, targetBuffers.count)
            if copyCount == 0 {
                logDrop("non-interleaved buffer data is empty")
                return nil
            }
            for index in 0..<copyCount {
                guard let src = sourceBuffers[index].mData,
                      let dst = targetBuffers[index].mData else {
                    logDrop("non-interleaved channel \(index) data is nil")
                    return nil
                }
                let byteCount = Int(sourceBuffers[index].mDataByteSize)
                memcpy(dst, src, byteCount)
                targetBuffers[index].mDataByteSize = sourceBuffers[index].mDataByteSize
            }
        }

        let ratio = outputFormat.sampleRate / inputFormat.sampleRate
        let outputCapacity = AVAudioFrameCount(Double(frameCount) * ratio + 1024)
        guard let outputBuffer = AVAudioPCMBuffer(pcmFormat: outputFormat, frameCapacity: outputCapacity) else {
            logDrop("failed to allocate output buffer")
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

        let conversionStatus = converter.convert(to: outputBuffer, error: &conversionError, withInputFrom: inputBlock)
        if let conversionError {
            logDrop("converter error: \(conversionError.localizedDescription)")
            return nil
        }
        if outputBuffer.frameLength == 0 {
            logDrop("converter returned zero frames status=\(conversionStatus.rawValue)")
            return nil
        }

        let bytes = Int(outputBuffer.frameLength) * Int(outputFormat.streamDescription.pointee.mBytesPerFrame)
        let outputBuffers = UnsafeMutableAudioBufferListPointer(outputBuffer.mutableAudioBufferList)
        guard outputBuffers.count > 0,
              let outData = outputBuffers[0].mData else {
            logDrop("output buffer data is nil")
            return nil
        }
        return Data(bytes: outData, count: bytes)
    }

    private func logDrop(_ message: String) {
        droppedSamples += 1
        if droppedSamples <= 5 || droppedSamples % 50 == 0 {
            StderrLogger.info("dropping audio sample: \(message)")
        }
    }
}

@available(macOS 13.0, *)
final class SystemAudioCapture: NSObject, SCStreamOutput, SCStreamDelegate {
    private let resampler = PcmResampler(sampleRate: 16000, channels: 1)
    private var stream: SCStream?
    private let output = FileHandle.standardOutput
    private var audioSampleCount = 0

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
        audioSampleCount += 1
        if audioSampleCount <= 3 || audioSampleCount % 200 == 0 {
            StderrLogger.info("received audio sample #\(audioSampleCount), numSamples=\(CMSampleBufferGetNumSamples(sampleBuffer))")
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
