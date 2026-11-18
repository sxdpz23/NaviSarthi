import Foundation
import UIKit
import VideoToolbox
import NaviSarthiCore

/// All methods and encoder callbacks are serialized on `queue`.
final class H264Encoder {
    private let queue = DispatchQueue(label: "com.navisarthi.h264")
    private var session: VTCompressionSession?
    private var packetizer = RTPPacketizer()
    private var index: Int64 = 0
    private var active = true
    private let output: ([[UInt8]]) -> Void
    private let failure:(Error)->Void
    private let epoch = ProcessInfo.processInfo.systemUptime
    private var lastKeyframe = -1.0
    init(failure:@escaping (Error)->Void = { _ in },output: @escaping ([[UInt8]]) -> Void) throws {
        self.output = output; self.failure = failure
        let status = VTCompressionSessionCreate(allocator:kCFAllocatorDefault,width:526,height:300,codecType:kCMVideoCodecType_H264,encoderSpecification:nil,imageBufferAttributes:nil,compressedDataAllocator:nil,outputCallback: { refcon, _, status, _, sample in
            guard let refcon else { return }
            let encoder = Unmanaged<H264Encoder>.fromOpaque(refcon).takeUnretainedValue()
            guard status == noErr else { encoder.failure(H264Encoder.error(status)); return }
            guard let sample else { return }
            encoder.queue.async { encoder.consume(sample) }
        },refcon:Unmanaged.passUnretained(self).toOpaque(),compressionSessionOut:&session)
        guard status == noErr, let session else { throw Self.error(status) }
        let properties: [CFString: Any] = [
            kVTCompressionPropertyKey_RealTime:true,
            kVTCompressionPropertyKey_ProfileLevel:kVTProfileLevel_H264_Baseline_AutoLevel,
            kVTCompressionPropertyKey_AverageBitRate:200000,
            kVTCompressionPropertyKey_ExpectedFrameRate:4,
            kVTCompressionPropertyKey_MaxKeyFrameInterval:4,
            kVTCompressionPropertyKey_MaxKeyFrameIntervalDuration:1,
            kVTCompressionPropertyKey_AllowFrameReordering:false
        ]
        for (key,value) in properties {
            let code = VTSessionSetProperty(session,key:key,value:value as CFTypeRef)
            guard code == noErr else { VTCompressionSessionInvalidate(session); self.session = nil; throw Self.error(code) }
        }
        let prepared = VTCompressionSessionPrepareToEncodeFrames(session)
        guard prepared == noErr else { VTCompressionSessionInvalidate(session); self.session = nil; throw Self.error(prepared) }
    }
    func encode(_ image: UIImage) throws {
        var buffer: CVPixelBuffer?
        let attributes: [CFString:Any] = [kCVPixelBufferCGImageCompatibilityKey:true,kCVPixelBufferCGBitmapContextCompatibilityKey:true]
        let status = CVPixelBufferCreate(kCFAllocatorDefault,526,300,kCVPixelFormatType_32ARGB,attributes as CFDictionary,&buffer)
        guard status == kCVReturnSuccess, let buffer, let cgImage = image.cgImage else { throw Self.error(status) }
        CVPixelBufferLockBaseAddress(buffer,[])
        guard let context = CGContext(data:CVPixelBufferGetBaseAddress(buffer),width:526,height:300,bitsPerComponent:8,bytesPerRow:CVPixelBufferGetBytesPerRow(buffer),space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGImageAlphaInfo.noneSkipFirst.rawValue) else {
            CVPixelBufferUnlockBaseAddress(buffer,[]); throw Self.error(-1)
        }
        context.draw(cgImage,in:CGRect(x:0,y:0,width:526,height:300)); CVPixelBufferUnlockBaseAddress(buffer,[])
        queue.async { [self] in
            guard active, let session else { return }
            let elapsed = ProcessInfo.processInfo.systemUptime - epoch
            let pts = CMTime(seconds:elapsed,preferredTimescale:90000)
            let force = index < 4 || elapsed - lastKeyframe >= 1
            if force { lastKeyframe = elapsed }
            let properties = [kVTEncodeFrameOptionKey_ForceKeyFrame:force] as CFDictionary
            index += 1
            let code = VTCompressionSessionEncodeFrame(session,imageBuffer:buffer,presentationTimeStamp:pts,duration:.invalid,frameProperties:properties,sourceFrameRefcon:nil,infoFlagsOut:nil)
            if code != noErr { failure(Self.error(code)) }
        }
    }
    private func consume(_ sample: CMSampleBuffer) {
        guard active, CMSampleBufferDataIsReady(sample), let format = CMSampleBufferGetFormatDescription(sample), let block = CMSampleBufferGetDataBuffer(sample) else { return }
        let attachments = CMSampleBufferGetSampleAttachmentsArray(sample,createIfNecessary:false) as? [[CFString:Any]]
        let keyframe = !(attachments?.first?[kCMSampleAttachmentKey_NotSync] as? Bool ?? false)
        var nals: [[UInt8]] = []
        if keyframe {
            for i in 0..<2 {
                var pointer: UnsafePointer<UInt8>?, size = 0, count = 0
                if CMVideoFormatDescriptionGetH264ParameterSetAtIndex(format,parameterSetIndex:i,parameterSetPointerOut:&pointer,parameterSetSizeOut:&size,parameterSetCountOut:&count,nalUnitHeaderLengthOut:nil) == noErr, let pointer {
                    nals.append(Array(UnsafeBufferPointer(start:pointer,count:size)))
                }
            }
        }
        let length = CMBlockBufferGetDataLength(block)
        var bytes = [UInt8](repeating:0,count:length)
        guard CMBlockBufferCopyDataBytes(block,atOffset:0,dataLength:length,destination:&bytes) == noErr else { return }
        var offset = 0
        while offset + 4 <= bytes.count {
            let size = (0..<4).reduce(0) { $0 << 8 | Int(bytes[offset + $1]) }
            offset += 4
            guard size > 0, offset + size <= bytes.count else { return }
            nals.append(Array(bytes[offset..<(offset + size)])); offset += size
        }
        let seconds = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample))
        guard seconds.isFinite else { return }
        let timestamp = UInt32(truncatingIfNeeded:UInt64(max(0,seconds) * 90000))
        output(packetizer.packets(nals:nals,timestamp:timestamp))
    }
    func stop() {
        queue.sync {
            active = false
            if let session { VTCompressionSessionCompleteFrames(session,untilPresentationTimeStamp:.invalid); VTCompressionSessionInvalidate(session) }
            session = nil
        }
    }
    deinit { if let session { VTCompressionSessionInvalidate(session) } }
    private static func error(_ status: OSStatus) -> NSError {
        NSError(domain:"VideoToolbox",code:Int(status),userInfo:[NSLocalizedDescriptionKey:"H.264 encoder error \(status)"])
    }
}
