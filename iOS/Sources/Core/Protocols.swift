import Foundation

public enum WireError: Error { case malformed, oversized, invalidPIN }
public enum ConsoleMusicCommand: Equatable { case previous, next, toggle }
public enum ConsoleMediaWire {
    public static func command(_ code:UInt8) -> ConsoleMusicCommand? {
        switch code { case 0x0a:return .previous; case 0x09:return .next; case 0x05:return .toggle; default:return nil }
    }
    public static func nowPlaying(album:String,title:String,artist:String) -> [UInt8] {
        func field(_ text:String) -> [UInt8] { text.replacingOccurrences(of:"\0",with:" ").unicodeScalars.prefix(19).map { UInt8(exactly:$0.value) ?? 0xff } }
        return field(album) + [0] + field(title) + [0] + field(artist)
    }
    public static func navigationExit(_ tlv:TLV) -> Bool { tlv.type == 9 && tlv.sub == 0 && tlv.value == [0x12] }
}

public struct TLV: Equatable {
    public let type: UInt8
    public let sub: UInt8
    public let value: [UInt8]
    public init(_ type: UInt8, _ sub: UInt8, _ value: [UInt8] = []) {
        self.type = type; self.sub = sub; self.value = value
    }
}

public enum K1G {
    public static func build(_ segments: [TLV], sequence: UInt8 = 0) throws -> [UInt8] {
        let size = 17 + segments.reduce(0) { $0 + 4 + $1.value.count }
        guard size <= 65535, segments.count < 65535 else { throw WireError.oversized }
        var bytes = be16(size) + be16(segments.count + 1)
        bytes += [0, 0, 0, 0, 2, 1, 0, 5, 0x4b, 0x31, 0x47, 0x20, sequence]
        for segment in segments {
            bytes += [segment.type, segment.sub] + be16(segment.value.count) + segment.value
        }
        return bytes
    }

    /// Dash-to-phone uses an eight-byte header, unlike outgoing packets.
    public static func incoming(_ bytes: [UInt8]) throws -> [TLV] {
        guard bytes.count >= 8, read16(bytes, 0) == bytes.count else { throw WireError.malformed }
        let count = read16(bytes, 2)
        var offset = 8
        var result: [TLV] = []
        for _ in 0..<count {
            guard offset + 4 <= bytes.count else { throw WireError.malformed }
            let length = read16(bytes, offset + 2)
            guard offset + 4 + length <= bytes.count else { throw WireError.malformed }
            result.append(TLV(bytes[offset], bytes[offset + 1], Array(bytes[(offset + 4)..<(offset + 4 + length)])))
            offset += 4 + length
        }
        guard offset == bytes.count else { throw WireError.malformed }
        return result
    }
}

public struct RTPPacketizer {
    private var sequence: UInt16
    private let ssrc: UInt32
    public init(sequence: UInt16 = .random(in: .min ... .max), ssrc: UInt32 = .random(in: .min ... .max)) {
        self.sequence = sequence; self.ssrc = ssrc
    }
    /// Raw NALs, no Annex-B prefix. One timestamp per access unit, 90 kHz clock.
    public mutating func packets(nals: [[UInt8]], timestamp: UInt32) -> [[UInt8]] {
        var packets: [[UInt8]] = []
        let nals = nals.filter { !$0.isEmpty }
        for (index, nal) in nals.enumerated() {
            let last = index == nals.count - 1
            if nal.count <= 1380 {
                packets.append(emit(nal, last, timestamp))
            } else {
                var offset = 1
                while offset < nal.count {
                    let end = min(offset + 1378, nal.count)
                    let header = (nal[0] & 31) | (offset == 1 ? 0x80 : 0) | (end == nal.count ? 0x40 : 0)
                    packets.append(emit([(nal[0] & 0xe0) | 28, header] + Array(nal[offset..<end]), last && end == nal.count, timestamp))
                    offset = end
                }
            }
        }
        return packets
    }
    private mutating func emit(_ payload: [UInt8], _ marker: Bool, _ timestamp: UInt32) -> [UInt8] {
        let packet: [UInt8] = [0x80, marker ? 0xe0 : 0x60] + be16(Int(sequence)) + be32(timestamp) + be32(ssrc) + payload
        sequence &+= 1
        return packet
    }
}

public enum PodWire {
    public static let service = "01ff0100-ba5e-f4ee-5ca1-eb1e5e4b1ce0"
    public static let characteristic = "01ff0101-ba5e-f4ee-5ca1-eb1e5e4b1ce0"
    public static func crc(_ bytes: [UInt8]) -> UInt16 {
        var crc: UInt16 = 0xffff
        for byte in bytes {
            crc ^= UInt16(byte) << 8
            for _ in 0..<8 { crc = (crc & 0x8000) != 0 ? (crc &<< 1) ^ 0x1021 : crc &<< 1 }
        }
        return crc
    }
    public static func packet(_ payload: [UInt8]) throws -> [UInt8] {
        guard payload.count <= 18 else { throw WireError.oversized }
        let body = payload + Array(repeating: UInt8(0), count: 18 - payload.count)
        return body + be16(Int(crc(body)))
    }
    public static func valid(_ packet: [UInt8]) -> Bool {
        packet.count == 20 && Int(crc(Array(packet.prefix(18)))) == read16(packet, 18)
    }
    public static func pin(_ text: String) throws -> [UInt8] {
        let digits = Array(text.utf8)
        guard digits.count == 6, digits.allSatisfy({ (48...57).contains($0) }) else { throw WireError.invalidPIN }
        return try packet([0x20] + digits)
    }
    public static func guidance(icon: UInt8, turnMeters: Double, remainingMeters: Double, night: Bool) throws -> [UInt8] {
        guard turnMeters.isFinite, remainingMeters.isFinite, turnMeters >= 0, remainingMeters >= 0 else { throw WireError.malformed }
        func distance(_ meters: Double) -> [UInt8] {
            be16(Int(min(65535, (meters <= 999 ? meters : meters / 100).rounded()))) + [meters <= 999 ? 1 : 2]
        }
        return try packet([0x10, 0x11, icon] + distance(turnMeters) + [night ? 1 : 0, 0xff, 0xff, 0xff, 0x41] + distance(remainingMeters))
    }
}

public struct Coordinate: Codable, Equatable {
    public var latitude: Double
    public var longitude: Double
    public init(_ latitude: Double, _ longitude: Double) { self.latitude = latitude; self.longitude = longitude }
    public var isValid: Bool { latitude.isFinite && longitude.isFinite && abs(latitude) <= 90 && abs(longitude) <= 180 }
    public func distance(to other: Coordinate) -> Double {
        let rad = Double.pi / 180
        let a = pow(sin((other.latitude - latitude) * rad / 2), 2) + cos(latitude * rad) * cos(other.latitude * rad) * pow(sin((other.longitude - longitude) * rad / 2), 2)
        return 6_371_000 * 2 * atan2(sqrt(max(0, min(1, a))), sqrt(max(0, 1 - a)))
    }
}

public func be16(_ value: Int) -> [UInt8] { [UInt8(truncatingIfNeeded: value >> 8), UInt8(truncatingIfNeeded: value)] }
public func be32(_ value: UInt32) -> [UInt8] { [24, 16, 8, 0].map { UInt8(truncatingIfNeeded: value >> $0) } }
public func read16(_ bytes: [UInt8], _ offset: Int) -> Int { Int(bytes[offset]) << 8 | Int(bytes[offset + 1]) }
