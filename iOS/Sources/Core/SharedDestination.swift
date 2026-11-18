import Foundation

public enum DestinationInput: Equatable {
    case coordinate(Coordinate)
    case query(String)
    case shortLink(URL)
}
public struct SharedRouteItem: Codable, Identifiable {
    public var version:Int
    public var id:String
    public var createdMs:Double
    public var kind:String
    public var title:String
    public var text:String?
    public var data:Data?
}
public enum SharedDestination {
    public static let shortHosts:Set<String> = ["maps.app.goo.gl","goo.gl"]
    public static let mapHosts:Set<String> = ["maps.app.goo.gl","goo.gl","maps.google.com","www.google.com","google.com","maps.google.co.in","www.google.co.in","maps.apple.com"]
    public static func parse(_ input:String) throws -> DestinationInput {
        let text = input.trimmingCharacters(in:.whitespacesAndNewlines)
        guard !text.isEmpty, text.utf8.count <= 10000 else { throw WireError.malformed }
        if let point = coordinate(text) { return .coordinate(point) }
        let regex = try NSRegularExpression(pattern:"https://[^\\s<>]+",options:.caseInsensitive)
        let range = NSRange(text.startIndex..<text.endIndex,in:text)
        var candidate = text
        if let match = regex.firstMatch(in:text,range:range), let value = Range(match.range,in:text) { candidate = String(text[value]).trimmingCharacters(in:CharacterSet(charactersIn:".,")) }
        if let url = URL(string:candidate), let scheme = url.scheme?.lowercased() {
            if scheme == "geo" {
                if let query = URLComponents(string:candidate)?.queryItems?.first(where:{ $0.name == "q" })?.value {
                    if let point = coordinate(query) { return .coordinate(point) }; return .query(query)
                }
                let parts = candidate.dropFirst(4).split(separator:"?",maxSplits:1)
                if let first = parts.first, let point = coordinate(String(first)) { return .coordinate(point) }
            }
            guard let components = URLComponents(url:url,resolvingAgainstBaseURL:false) else { throw WireError.malformed }
            if scheme == "https" {
                guard let host = url.host?.lowercased(), mapHosts.contains(host), url.user == nil, url.password == nil else { throw WireError.malformed }
                if shortHosts.contains(host) {
                    guard host != "goo.gl" || url.path.hasPrefix("/maps") else { throw WireError.malformed }
                    return .shortLink(url)
                }
            } else if scheme != "navisarthi" { throw WireError.malformed }
            let items = components.queryItems ?? []
            for key in ["destination","daddr","q","query","ll"] {
                if let value = items.first(where:{ $0.name == key })?.value, !value.isEmpty {
                    if let point = coordinate(value) { return .coordinate(point) }
                    return .query(value)
                }
            }
            let path = components.percentEncodedPath.removingPercentEncoding ?? components.path
            if path.contains("/place/") {
                let expression = try NSRegularExpression(pattern:"!3d(-?[0-9]+(?:\\.[0-9]+)?)!4d(-?[0-9]+(?:\\.[0-9]+)?)")
                if let match = expression.firstMatch(in:path,range:NSRange(path.startIndex..<path.endIndex,in:path)), let latRange = Range(match.range(at:1),in:path), let lonRange = Range(match.range(at:2),in:path), let point = coordinate(String(path[latRange]) + "," + String(path[lonRange])) { return .coordinate(point) }
                if let marker = path.range(of:"/place/") {
                    let name = path[marker.upperBound...].split(separator:"/").first.map(String.init) ?? ""
                    if !name.isEmpty { return .query(name.replacingOccurrences(of:"+",with:" ")) }
                }
            }
            if path.contains("/dir/") {
                let stops = path.components(separatedBy:"/dir/").last?.split(separator:"/").prefix(while:{ !$0.hasPrefix("@") && !$0.hasPrefix("data=") }) ?? []
                if let last = stops.last { if let point = coordinate(String(last)) { return .coordinate(point) }; return .query(String(last).replacingOccurrences(of:"+",with:" ")) }
            }
            throw WireError.malformed
        }
        return .query(text)
    }
    private static func coordinate(_ text:String) -> Coordinate? {
        let expression = try! NSRegularExpression(pattern:"^\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s*,\\s*(-?[0-9]+(?:\\.[0-9]+)?)(?:\\s*\\([^\\n]*\\))?\\s*$")
        guard let match = expression.firstMatch(in:text,range:NSRange(text.startIndex..<text.endIndex,in:text)),
              let a = Range(match.range(at:1),in:text), let b = Range(match.range(at:2),in:text), let lat = Double(text[a]), let lon = Double(text[b]), Coordinate(lat,lon).isValid else { return nil }
        return Coordinate(lat,lon)
    }
}
