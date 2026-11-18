import Foundation
#if canImport(FoundationXML)
import FoundationXML
#endif

public enum OfflineError: LocalizedError {
    case invalid, tooLarge, outsideRegion, disconnected, noRoads
    public var errorDescription: String? {
        switch self {
        case .invalid: return "Invalid offline road pack"
        case .tooLarge: return "Road pack exceeds the supported regional size"
        case .outsideRegion: return "A route point is more than 250 m from the imported road network"
        case .disconnected: return "No legal motorcycle route connects these points in this pack"
        case .noRoads: return "No motorcycle-accessible roads were found"
        }
    }
}
public struct RoadNode: Codable {
    public var id: Int64
    public var coordinate: Coordinate
    public var name: String?
    public var category:String?
    public init(id:Int64,coordinate:Coordinate,name:String? = nil,category:String? = nil) { self.id = id; self.coordinate = coordinate; self.name = name; self.category = category }
}
public struct RoadEdge: Codable {
    public var id: Int
    public var from: Int64
    public var to: Int64
    public var meters: Double
    public var speedKph: Double
    public var name: String
    public var unpaved: Bool
    public var toll: Bool
    public var roundabout: Bool
    public init(id:Int,from:Int64,to:Int64,meters:Double,speedKph:Double,name:String = "Road",unpaved:Bool = false,toll:Bool = false,roundabout:Bool = false) {
        self.id = id; self.from = from; self.to = to; self.meters = meters; self.speedKph = speedKph
        self.name = name; self.unpaved = unpaved; self.toll = toll; self.roundabout = roundabout
    }
}
public struct RoadRestriction: Codable {
    public var fromEdge: Int; public var toEdge: Int; public var only: Bool
    public init(fromEdge:Int,toEdge:Int,only:Bool) { self.fromEdge = fromEdge; self.toEdge = toEdge; self.only = only }
}
public struct RoadPack: Codable {
    public var format = "navisarthi-roadpack"
    public var version = 1
    public var title: String
    public var attribution: String
    public var warnings: [String]
    public var nodes: [RoadNode]
    public var edges: [RoadEdge]
    public var restrictions: [RoadRestriction]
    public var places:[RoadNode]
    public init(title:String,attribution:String,nodes:[RoadNode],edges:[RoadEdge],restrictions:[RoadRestriction] = [],warnings:[String] = [],places:[RoadNode] = []) {
        self.title = title; self.attribution = attribution; self.nodes = nodes; self.edges = edges; self.restrictions = restrictions; self.warnings = warnings
        self.places = places
    }
    private enum CodingKeys:String,CodingKey { case format,version,title,attribution,warnings,nodes,edges,restrictions,places }
    public init(from decoder:Decoder) throws {
        let values = try decoder.container(keyedBy:CodingKeys.self)
        format = try values.decode(String.self,forKey:.format); version = try values.decode(Int.self,forKey:.version)
        title = try values.decode(String.self,forKey:.title); attribution = try values.decode(String.self,forKey:.attribution)
        warnings = try values.decodeIfPresent([String].self,forKey:.warnings) ?? []
        nodes = try values.decode([RoadNode].self,forKey:.nodes); edges = try values.decode([RoadEdge].self,forKey:.edges)
        restrictions = try values.decodeIfPresent([RoadRestriction].self,forKey:.restrictions) ?? []
        places = try values.decodeIfPresent([RoadNode].self,forKey:.places) ?? []
    }
    public func validate() throws {
        guard format == "navisarthi-roadpack", version == 1, !attribution.isEmpty, attribution.count <= 1000, title.count <= 500, warnings.count <= 30,
              !edges.isEmpty, !nodes.isEmpty else { throw OfflineError.invalid }
        guard nodes.count <= 300000, edges.count <= 600000, restrictions.count <= 300000, places.count <= 100000 else { throw OfflineError.tooLarge }
        let nodeIDs = Set(nodes.map(\.id)), edgeIDs = Set(edges.map(\.id))
        guard nodeIDs.count == nodes.count, edgeIDs.count == edges.count,
              nodes.allSatisfy({ $0.coordinate.isValid }),
              edges.allSatisfy({ $0.id >= 0 && nodeIDs.contains($0.from) && nodeIDs.contains($0.to) && $0.from != $0.to && $0.meters.isFinite && (0.000001...250000).contains($0.meters) && $0.speedKph.isFinite && (1...160).contains($0.speedKph) }),
              restrictions.allSatisfy({ edgeIDs.contains($0.fromEdge) && edgeIDs.contains($0.toEdge) }) else { throw OfflineError.invalid }
        guard places.allSatisfy({ $0.coordinate.isValid && ($0.name?.count ?? 0) <= 500 }), nodes.allSatisfy({ ($0.name?.count ?? 0) <= 500 }) else { throw OfflineError.invalid }
        let lookup = Dictionary(uniqueKeysWithValues:edges.map { ($0.id,$0) })
        guard restrictions.allSatisfy({ lookup[$0.fromEdge]?.to == lookup[$0.toEdge]?.from }) else { throw OfflineError.invalid }
        let positions = Dictionary(uniqueKeysWithValues:nodes.map { ($0.id,$0.coordinate) })
        for edge in edges {
            guard edge.meters >= positions[edge.from]!.distance(to:positions[edge.to]!) * 0.98 else { throw OfflineError.invalid }
        }
    }
}
public struct RoadPath {
    public var coordinates: [Coordinate]
    public var edges: [RoadEdge]
    public var meters: Double
    public var seconds: Double
    public var decisionNodes: Set<Int64>
}
public final class OfflineRouter {
    public let pack: RoadPack
    private let nodes: [Int64:RoadNode]
    private let outgoing: [Int64:[RoadEdge]]
    private let restrictions: [Int:[RoadRestriction]]
    private let maximumSpeed: Double
    private struct Cell:Hashable { var lat:Int; var lon:Int }
    private let cells:[Cell:[Int]]
    private let longEdges:[Int]
    public init(_ pack:RoadPack) throws {
        try pack.validate(); self.pack = pack
        let positionMap = Dictionary(uniqueKeysWithValues:pack.nodes.map { ($0.id,$0) })
        nodes = positionMap
        outgoing = Dictionary(grouping:pack.edges,by:\.from)
        restrictions = Dictionary(grouping:pack.restrictions,by:\.fromEdge)
        maximumSpeed = (pack.edges.map(\.speedKph).max() ?? 160) / 3.6
        var cells:[Cell:[Int]] = [:], long:[Int] = []
        for (index,edge) in pack.edges.enumerated() {
            let a = positionMap[edge.from]!.coordinate, b = positionMap[edge.to]!.coordinate
            let lat0 = Int(floor(min(a.latitude,b.latitude) / 0.02)), lat1 = Int(floor(max(a.latitude,b.latitude) / 0.02))
            let lon0 = Int(floor(min(a.longitude,b.longitude) / 0.02)), lon1 = Int(floor(max(a.longitude,b.longitude) / 0.02))
            if (lat1-lat0+1) * (lon1-lon0+1) > 64 { long.append(index); continue }
            for lat in lat0...lat1 { for lon in lon0...lon1 { cells[Cell(lat:lat,lon:lon),default:[]].append(index) } }
        }
        self.cells = cells; longEdges = long
    }
    private struct State: Hashable { var node:Int64; var incoming:Int }
    private struct QueueEntry { var state:State; var priority:Double; var cost:Double }
    private struct Heap {
        var entries:[QueueEntry] = []
        mutating func push(_ entry:QueueEntry) {
            entries.append(entry); var i = entries.count - 1
            while i > 0 { let parent = (i - 1) / 2; if entries[parent].priority <= entries[i].priority { break }; entries.swapAt(parent,i); i = parent }
        }
        mutating func pop() -> QueueEntry? {
            guard !entries.isEmpty else { return nil }
            if entries.count == 1 { return entries.removeLast() }
            let result = entries[0]; entries[0] = entries.removeLast(); var i = 0
            while i * 2 + 1 < entries.count {
                let left = i * 2 + 1, right = left + 1
                let child = right < entries.count && entries[right].priority < entries[left].priority ? right : left
                if entries[i].priority <= entries[child].priority { break }; entries.swapAt(i,child); i = child
            }
            return result
        }
    }
    public func route(from:Coordinate,to:Coordinate,preferPaved:Bool = true,avoidTolls:Bool = false) throws -> RoadPath {
        guard from.isValid, to.isValid else { throw OfflineError.invalid }
        func closest(_ point:Coordinate) throws -> RoadNode {
            guard let node = pack.nodes.min(by:{ $0.coordinate.distance(to:point) < $1.coordinate.distance(to:point) }), node.coordinate.distance(to:point) <= 250 else { throw OfflineError.outsideRegion }
            return node
        }
        let start = try closest(from), target = try closest(to)
        guard start.id != target.id else { throw OfflineError.noRoads }
        let initial = State(node:start.id,incoming:-1)
        var queue = Heap(), cost:[State:Double] = [initial:0], previous:[State:(State,RoadEdge)] = [:]
        queue.push(QueueEntry(state:initial,priority:0,cost:0)); var visited = 0
        var found:State?
        while let current = queue.pop() {
            if current.cost > (cost[current.state] ?? .infinity) { continue }
            visited += 1
            if visited > 1500000 { throw OfflineError.tooLarge }
            if current.state.node == target.id { found = current.state; break }
            let rules = restrictions[current.state.incoming] ?? []
            let only = Set(rules.filter(\.only).map(\.toEdge))
            for edge in outgoing[current.state.node] ?? [] {
                if avoidTolls && edge.toll { continue }
                if rules.contains(where:{ !$0.only && $0.toEdge == edge.id }) || (!only.isEmpty && !only.contains(edge.id)) { continue }
                let next = State(node:edge.to,incoming:edge.id)
                let travel = edge.meters / (edge.speedKph / 3.6)
                let newCost = current.cost + travel * (preferPaved && edge.unpaved ? 2.5 : 1)
                if newCost < (cost[next] ?? .infinity) {
                    cost[next] = newCost; previous[next] = (current.state,edge)
                    let heuristic = nodes[edge.to]!.coordinate.distance(to:target.coordinate) * 0.98 / maximumSpeed
                    queue.push(QueueEntry(state:next,priority:newCost + heuristic,cost:newCost))
                }
            }
        }
        guard var state = found else { throw OfflineError.disconnected }
        var edges:[RoadEdge] = []
        while let (parent,edge) = previous[state] { edges.append(edge); state = parent }
        edges.reverse()
        let coordinates = [start.coordinate] + edges.map { nodes[$0.to]!.coordinate }
        let decisions = Set(edges.enumerated().compactMap { index,edge -> Int64? in
            guard index > 0 else { return nil }
            return (outgoing[edge.from] ?? []).filter { $0.to != edges[index-1].from }.count > 1 ? edge.from : nil
        })
        return RoadPath(coordinates:coordinates,edges:edges,meters:edges.reduce(0) { $0 + $1.meters },seconds:edges.reduce(0) { $0 + $1.meters / ($1.speedKph / 3.6) },decisionNodes:decisions)
    }
    public func coordinate(for node:Int64) -> Coordinate? { nodes[node]?.coordinate }
    public func visibleEdges(center:Coordinate,meters:Double,aspect:Double) -> [RoadEdge] {
        guard center.isValid, meters.isFinite, (1...2000000).contains(meters), aspect.isFinite, (0.01...10).contains(aspect) else { return [] }
        let latDelta = meters / 111195, lonDelta = meters * aspect / (111195 * max(0.01,cos(center.latitude * .pi / 180)))
        let lat0 = Int(floor((center.latitude-latDelta) / 0.02)), lat1 = Int(floor((center.latitude+latDelta) / 0.02))
        let lon0 = Int(floor((center.longitude-lonDelta) / 0.02)), lon1 = Int(floor((center.longitude+lonDelta) / 0.02))
        guard (lat1-lat0+1) * (lon1-lon0+1) <= 20000 else { return [] }
        var indices = Set(longEdges)
        for lat in lat0...lat1 { for lon in lon0...lon1 { indices.formUnion(cells[Cell(lat:lat,lon:lon)] ?? []) } }
        return indices.map { pack.edges[$0] }
    }
}

/// Regional OSM XML importer. Only explicit, currently supported motorcycle roads enter the graph.
public final class OSMRoadImporter: NSObject, XMLParserDelegate {
    private struct Way { var id:Int64; var refs:[Int64]; var tags:[String:String] }
    private struct Restriction { var from:Int64; var via:Int64; var to:Int64; var only:Bool; var kind:String }
    private var nodes:[Int64:RoadNode] = [:], ways:[Way] = [], restrictions:[Restriction] = []
    private var currentNode:Int64?, currentWay:Int64?, refs:[Int64] = [], tags:[String:String] = [:]
    private var members:[(type:String,ref:Int64,role:String)] = [], relation = false
    private var exceeded = false, conditional = false, unsupported = false
    private var blockedNodes = Set<Int64>(), excludedWays = Set<Int64>()
    public static func read(_ data:Data,title:String) throws -> RoadPack {
        guard data.count <= 100000000 else { throw OfflineError.tooLarge }
        let reader = OSMRoadImporter(), parser = XMLParser(data:data)
        parser.shouldResolveExternalEntities = false; parser.delegate = reader
        guard parser.parse() else { throw reader.exceeded ? OfflineError.tooLarge : OfflineError.invalid }
        return try reader.build(title:title)
    }
    public func parser(_ parser:XMLParser,didStartElement elementName:String,namespaceURI:String?,qualifiedName:String?,attributes:[String:String]) {
        if elementName == "node", let id = attributes["id"].flatMap(Int64.init), let lat = attributes["lat"].flatMap(Double.init), let lon = attributes["lon"].flatMap(Double.init) {
            guard nodes.count < 300000, Coordinate(lat,lon).isValid else { exceeded = true; parser.abortParsing(); return }
            nodes[id] = RoadNode(id:id,coordinate:Coordinate(lat,lon)); currentNode = id; tags = [:]
        } else if elementName == "way" { currentWay = attributes["id"].flatMap(Int64.init); refs = []; tags = [:] }
        else if elementName == "relation" { relation = true; members = []; tags = [:] }
        else if elementName == "nd", let ref = attributes["ref"].flatMap(Int64.init) { refs.append(ref) }
        else if elementName == "tag", let key = attributes["k"], let value = attributes["v"] { tags[key] = value }
        else if elementName == "member", let ref = attributes["ref"].flatMap(Int64.init) { members.append((attributes["type"] ?? "",ref,attributes["role"] ?? "")) }
    }
    public func parser(_ parser:XMLParser,didEndElement elementName:String,namespaceURI:String?,qualifiedName:String?) {
        if elementName == "node", let id = currentNode {
            nodes[id]?.name = tags["name"]
            nodes[id]?.category = tags["amenity"] ?? tags["shop"] ?? tags["tourism"]
            if nodes[id]?.name == nil, let category = nodes[id]?.category { nodes[id]?.name = tags["brand"] ?? category.replacingOccurrences(of:"_",with:" ").capitalized }
            let explicit = tags["motorcycle"] ?? tags["motor_vehicle"] ?? tags["vehicle"] ?? tags["access"]
            if let explicit, !["yes","designated","permissive"].contains(explicit) { blockedNodes.insert(id) }
            if ["bollard","block","cycle_barrier","stile","kissing_gate","fence","gate","lift_gate"].contains(tags["barrier"] ?? ""), explicit == nil { blockedNodes.insert(id) }
            if tags.keys.contains(where:{ $0.contains("conditional") }) { blockedNodes.insert(id); conditional = true }
            currentNode = nil
        }
        if elementName == "way", let id = currentWay {
            if ways.count >= 100000 { exceeded = true; parser.abortParsing(); return }
            ways.append(Way(id:id,refs:refs,tags:tags)); currentWay = nil
        }
        if elementName == "relation" && relation {
            defer { relation = false }
            guard tags["type"] == "restriction", !(tags["except"] ?? "").split(separator:";").contains("motorcycle") else { return }
            if tags.keys.contains(where:{ $0.contains("conditional") }) { conditional = true }
            let restriction = tags["restriction:motorcycle"] ?? tags["restriction"] ?? ""
            if restriction.isEmpty {
                if tags.keys.contains(where:{ $0.contains("conditional") }) { unsupported = true; for member in members where member.type == "way" { excludedWays.insert(member.ref) } }
                return
            }
            if members.filter({ $0.role == "from" }).count != 1 || members.filter({ $0.role == "to" }).count != 1 || members.filter({ $0.role == "via" }).count != 1 {
                unsupported = true; for member in members where member.type == "way" { excludedWays.insert(member.ref) }; return
            }
            guard let from = members.first(where:{ $0.role == "from" && $0.type == "way" }), let via = members.first(where:{ $0.role == "via" && $0.type == "node" }), let to = members.first(where:{ $0.role == "to" && $0.type == "way" }) else {
                unsupported = true; for member in members where member.type == "way" { excludedWays.insert(member.ref) }; return
            }
            guard restriction.hasPrefix("no_") || restriction.hasPrefix("only_") else { excludedWays.insert(from.ref); unsupported = true; return }
            restrictions.append(Restriction(from:from.ref,via:via.ref,to:to.ref,only:restriction.hasPrefix("only_"),kind:restriction))
        }
    }
    private func build(title:String) throws -> RoadPack {
        var edges:[RoadEdge] = [], wayEdges:[Int64:[RoadEdge]] = [:], used = Set<Int64>()
        let supported:Set<String> = ["motorway","motorway_link","trunk","trunk_link","primary","primary_link","secondary","secondary_link","tertiary","tertiary_link","unclassified","residential","living_street","service","track","road"]
        for way in ways {
            if excludedWays.contains(way.id) { continue }
            let highway = way.tags["highway"] ?? ""
            let explicit = way.tags["motorcycle"]
            guard supported.contains(highway) || ["yes","designated","permissive"].contains(explicit ?? "") else { continue }
            let access = explicit ?? way.tags["motor_vehicle"] ?? way.tags["vehicle"] ?? way.tags["access"] ?? "yes"
            guard ["yes","designated","permissive"].contains(access), way.tags["area"] != "yes", way.tags["construction"] == nil else { continue }
            if way.tags.keys.contains(where:{ $0.contains("conditional") }) { conditional = true; continue }
            let direction = way.tags["oneway:motorcycle"] ?? way.tags["oneway"] ?? (way.tags["junction"] == "roundabout" || highway == "motorway" ? "yes" : "no")
            guard ["yes","true","1","-1","no","false","0"].contains(direction) else { continue }
            let rawSpeed = way.tags["maxspeed"] ?? ""
            let parsed = Double(rawSpeed.split(whereSeparator:{ !$0.isNumber && $0 != "." }).first ?? "")
            let speed = max(5,min(130,(parsed ?? (highway.contains("motorway") ? 80 : 40)) * (rawSpeed.contains("mph") ? 1.609344 : 1)))
            let unpaved = ["unpaved","gravel","dirt","ground","sand","mud"].contains(way.tags["surface"] ?? "") || highway == "track"
            guard way.refs.count >= 2 else { continue }
            for index in 1..<way.refs.count {
                let a = way.refs[index-1], b = way.refs[index]
                guard let na = nodes[a], let nb = nodes[b], a != b, !blockedNodes.contains(a), !blockedNodes.contains(b) else { continue }
                let meters = na.coordinate.distance(to:nb.coordinate); guard meters > 0 else { continue }
                func append(_ from:Int64,_ to:Int64) {
                    let edge = RoadEdge(id:edges.count,from:from,to:to,meters:meters,speedKph:speed,name:way.tags["name"] ?? way.tags["ref"] ?? "Road",unpaved:unpaved,toll:way.tags["toll"] == "yes",roundabout:way.tags["junction"] == "roundabout")
                    edges.append(edge); wayEdges[way.id,default:[]].append(edge); used.insert(from); used.insert(to)
                }
                if direction != "-1" { append(a,b) }
                if !["yes","true","1"].contains(direction) { append(b,a) }
                if edges.count > 600000 { throw OfflineError.tooLarge }
            }
        }
        guard !edges.isEmpty else { throw OfflineError.noRoads }
        var rules:[RoadRestriction] = []
        for restriction in restrictions {
            for incoming in wayEdges[restriction.from] ?? [] where incoming.to == restriction.via {
                for outgoing in wayEdges[restriction.to] ?? [] where outgoing.from == restriction.via {
                    if restriction.kind.contains("u_turn"), incoming.from != outgoing.to { continue }
                    if restriction.kind.contains("straight_on"), incoming.from == outgoing.to { continue }
                    rules.append(RoadRestriction(fromEdge:incoming.id,toEdge:outgoing.id,only:restriction.only))
                    guard rules.count <= 300000 else { throw OfflineError.tooLarge }
                }
            }
        }
        var warnings:[String] = []
        if conditional { warnings.append("Conditional-access roads are excluded. Conditional restrictions need review; do not rely on this pack in affected areas.") }
        if unsupported { warnings.append("Ways participating in unsupported turn restrictions were excluded. This can reduce regional connectivity.") }
        let places = nodes.values.filter { !used.contains($0.id) && $0.name?.isEmpty == false }.sorted { $0.id < $1.id }
        let pack = RoadPack(title:title,attribution:"© OpenStreetMap contributors · ODbL",nodes:used.compactMap { nodes[$0] }.sorted { $0.id < $1.id },edges:edges,restrictions:rules,warnings:warnings,places:places)
        try pack.validate(); return pack
    }
}
