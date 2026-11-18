import Foundation
#if canImport(FoundationXML)
import FoundationXML
#endif

public enum BoundedFile {
    public static func read(_ url:URL,limit:Int) throws -> Data {
        guard limit > 0, limit < Int.max else { throw WireError.oversized }
        let handle = try FileHandle(forReadingFrom:url); defer { try? handle.close() }
        let data = try handle.read(upToCount:limit + 1) ?? Data()
        guard data.count <= limit else { throw WireError.oversized }; return data
    }
}

public struct FuelSample {
    public var odometer: Double; public var litres: Double; public var full: Bool
    public init(odometer: Double, litres: Double, full: Bool) { self.odometer = odometer; self.litres = litres; self.full = full }
}
public enum FuelEconomy {
    /// Partial fills count toward the next full-to-full interval; the first tank is a baseline.
    public static func kilometersPerLitre(_ samples: [FuelSample]) -> Double? {
        let samples = samples.sorted { $0.odometer < $1.odometer }
        guard samples.allSatisfy({ $0.odometer.isFinite && $0.litres.isFinite && $0.odometer >= 0 && $0.litres > 0 }) else { return nil }
        var baseline: Double?, litres = 0.0, completedLitres = 0.0, distance = 0.0
        for sample in samples {
            if let start = baseline {
                guard sample.odometer > start else { return nil }
                litres += sample.litres
                if sample.full {
                    distance += sample.odometer - start; completedLitres += litres
                    baseline = sample.odometer; litres = 0
                }
            } else if sample.full { baseline = sample.odometer }
        }
        return completedLitres > 0 ? distance / completedLitres : nil
    }
}

public struct RouteProjection: Equatable {
    public var segment: Int
    public var alongMeters: Double
    public var crossTrackMeters: Double
    public var coordinate: Coordinate
}
public struct RouteMatcher {
    private let points: [Coordinate]
    private let cumulative: [Double]
    public private(set) var progress = 0.0
    public init(points: [Coordinate]) {
        self.points = points
        var distances = [0.0]
        if points.count > 1 { for i in 1..<points.count { distances.append(distances.last! + points[i-1].distance(to: points[i])) } }
        cumulative = distances
    }
    public var length: Double { cumulative.last ?? 0 }
    public mutating func match(_ fix: Coordinate, course: Double? = nil, accuracy: Double = 10) -> RouteProjection? {
        guard fix.isValid, points.count >= 2, accuracy.isFinite, accuracy >= 0 else { return nil }
        let scaleY = 111_195.0, scaleX = 111_195 * cos(fix.latitude * .pi / 180)
        var best: RouteProjection?, bestScore = Double.infinity
        for i in 1..<points.count {
            // Search locally after acquiring the route. Avoid jumps across loops/flyovers.
            if progress > 0 && (cumulative[i] < progress - 100 || cumulative[i-1] > progress + 2000) { continue }
            let ax = (points[i-1].longitude - fix.longitude) * scaleX
            let ay = (points[i-1].latitude - fix.latitude) * scaleY
            let bx = (points[i].longitude - fix.longitude) * scaleX
            let by = (points[i].latitude - fix.latitude) * scaleY
            let dx = bx - ax, dy = by - ay, squared = dx * dx + dy * dy
            guard squared > 0.01 else { continue }
            let t = min(1, max(0, -(ax * dx + ay * dy) / squared))
            let cross = hypot(ax + t * dx, ay + t * dy)
            let along = cumulative[i-1] + t * (cumulative[i] - cumulative[i-1])
            var score = cross + max(0, progress - along - 20) * 0.8
            if let course, course.isFinite, course >= 0 {
                let heading = atan2(dx, dy) * 180 / .pi
                let delta = abs((heading - course + 540).truncatingRemainder(dividingBy: 360) - 180)
                score += delta / 180 * 35
            }
            if score < bestScore {
                bestScore = score
                best = RouteProjection(segment: i - 1, alongMeters: along, crossTrackMeters: cross,
                    coordinate: Coordinate(points[i-1].latitude + t * (points[i].latitude - points[i-1].latitude), points[i-1].longitude + t * (points[i].longitude - points[i-1].longitude)))
            }
        }
        if let best, best.crossTrackMeters <= max(40, accuracy * 2) { progress = max(progress, best.alongMeters) }
        return best
    }
}

public enum GPXError: Error { case invalid, tooLarge, discontinuous }
public enum Polyline {
    public static func encode(_ coordinates:[Coordinate],precision:Int = 5) throws -> String {
        guard (5...6).contains(precision), coordinates.count <= 500000, coordinates.allSatisfy(\.isValid) else { throw WireError.malformed }
        let scale = pow(10,Double(precision)); var lat:Int64 = 0, lon:Int64 = 0, bytes:[UInt8] = []
        func append(_ delta:Int64) {
            var value = UInt64(delta < 0 ? ~(delta << 1) : delta << 1)
            while value >= 32 { bytes.append(UInt8((value & 31) | 32) + 63); value >>= 5 }
            bytes.append(UInt8(value) + 63)
        }
        for coordinate in coordinates {
            let nextLat = Int64((coordinate.latitude * scale).rounded()), nextLon = Int64((coordinate.longitude * scale).rounded())
            append(nextLat - lat); append(nextLon - lon); lat = nextLat; lon = nextLon
        }
        return String(decoding:bytes,as:UTF8.self)
    }
    public static func decode(_ encoded:String,precision:Int = 6) throws -> [Coordinate] {
        guard (5...6).contains(precision), encoded.utf8.count <= 4_000_000 else { throw WireError.malformed }
        let bytes = Array(encoded.utf8); var index = 0, lat: Int64 = 0, lon: Int64 = 0
        func value() throws -> Int64 {
            var result: Int64 = 0, shift: Int64 = 0
            while index < bytes.count && shift <= 50 {
                let raw = bytes[index]; index += 1
                guard (63...126).contains(raw) else { throw WireError.malformed }
                let byte = Int64(raw - 63); result |= (byte & 31) << shift; shift += 5
                if byte < 32 { return result & 1 != 0 ? ~(result >> 1) : result >> 1 }
            }
            throw WireError.malformed
        }
        var points: [Coordinate] = []; let scale = pow(10,Double(precision))
        while index < bytes.count {
            lat += try value(); lon += try value()
            let point = Coordinate(Double(lat)/scale,Double(lon)/scale)
            guard point.isValid, points.count < 100000 else { throw WireError.malformed }; points.append(point)
        }
        return points
    }
}
public final class GPXReader: NSObject, XMLParserDelegate {
    private var points: [Coordinate] = []
    private var invalid = false
    private var segments = 0
    public static func read(_ data: Data) throws -> [Coordinate] {
        guard data.count <= 20_000_000 else { throw GPXError.tooLarge }
        let reader = GPXReader(), parser = XMLParser(data: data)
        parser.shouldResolveExternalEntities = false; parser.delegate = reader
        guard parser.parse(), !reader.invalid, reader.points.count >= 2 else { throw GPXError.invalid }
        // Separate tracks/segments may represent gaps, never invent a connecting road.
        guard reader.segments <= 1 else { throw GPXError.discontinuous }
        return reader.points
    }
    public func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes: [String:String]) {
        let element = elementName.split(separator: ":").last.map(String.init) ?? elementName
        if element == "trkseg" { segments += 1 }
        guard element == "trkpt" || element == "rtept" else { return }
        guard points.count < 100_000, let lat = attributes["lat"].flatMap(Double.init), let lon = attributes["lon"].flatMap(Double.init), Coordinate(lat,lon).isValid else { invalid = true; parser.abortParsing(); return }
        let point = Coordinate(lat,lon)
        if points.last != point { points.append(point) }
    }
}
