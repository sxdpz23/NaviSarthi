import Foundation
import MapKit
import AVFoundation
import SwiftData
import UIKit
import SwiftUI
import NaviSarthiCore

struct GuidanceStep: Codable {
    var instructions: String
    var distance: Double
    var endMeters: Double
    var glyph: UInt8 = 0xff
    var podGlyph: UInt8 = 0xff
}

enum NativeRouter {
    static func offline(_ path:RoadPath) -> JourneyRoute {
        var steps = [GuidanceStep(instructions:"Join \(path.edges.first?.name ?? "the road")",distance:0,endMeters:0,glyph:9,podGlyph:0x3c)]
        var meters = 0.0
        if path.edges.count > 1 {
            for index in 1..<path.edges.count {
                meters += path.edges[index-1].meters
                let previous = path.edges[index-1], next = path.edges[index]
                guard path.decisionNodes.contains(next.from) || previous.name != next.name || previous.roundabout != next.roundabout else { continue }
                func heading(_ a:Coordinate,_ b:Coordinate) -> Double { atan2((b.longitude - a.longitude) * cos(a.latitude * .pi / 180),b.latitude - a.latitude) * 180 / .pi }
                let before = heading(path.coordinates[index-1],path.coordinates[index]), after = heading(path.coordinates[index],path.coordinates[index+1])
                let delta = (after - before + 540).truncatingRemainder(dividingBy:360) - 180
                let modifier = abs(delta) > 155 ? "uturn" : abs(delta) < 20 ? "" : delta > 0 ? (delta < 45 ? "slight right" : delta > 130 ? "sharp right" : "right") : (delta > -45 ? "slight left" : delta < -130 ? "sharp left" : "left")
                let type = previous.roundabout || next.roundabout ? "roundabout" : "turn"
                let glyph = glyphs(type:type,modifier:modifier,exit:nil)
                let cue = type == "roundabout" ? (next.roundabout ? "Enter the roundabout" : "Exit the roundabout onto \(next.name)") : modifier.isEmpty ? "Continue onto \(next.name)" : "\(modifier == "uturn" ? "Make a U-turn" : "Turn " + modifier) onto \(next.name)"
                steps.append(GuidanceStep(instructions:cue,distance:0,endMeters:meters,glyph:glyph.dash,podGlyph:glyph.pod))
            }
        }
        steps.append(GuidanceStep(instructions:"Arrive at destination",distance:0,endMeters:path.meters,glyph:0x3c,podGlyph:0))
        return JourneyRoute(name:"Offline motorcycle route",coordinates:path.coordinates,steps:steps,distance:path.meters,seconds:path.seconds,source:"Offline motorcycle")
    }
    static func join(_ routes:[JourneyRoute],name:String) -> JourneyRoute {
        var points:[Coordinate] = [], steps:[GuidanceStep] = [], meters = 0.0, seconds = 0.0
        for route in routes {
            points += points.isEmpty ? route.coordinates : Array(route.coordinates.dropFirst())
            steps += route.steps.map { var step = $0; step.endMeters += meters; return step }
            meters += route.distance; seconds += route.expectedTravelTime
        }
        return JourneyRoute(name:name,coordinates:points,steps:steps,distance:meters,seconds:seconds,source:routes.first?.source ?? "Route")
    }
    static func calculate(points:[Coordinate],provider:String) async throws -> [JourneyRoute] {
        guard (2...25).contains(points.count), points.allSatisfy(\.isValid) else { throw WireClientError("Choose between 2 and 25 route points") }
        if provider == "Valhalla motorcycle" { return [try await valhalla(points)] }
        guard provider == "Mapbox traffic" else { throw WireClientError("Unknown routing provider") }
        let key = KeychainStore.read("mapboxToken").flatMap { String(data:$0,encoding:.utf8) } ?? ""
        guard !key.isEmpty else { throw WireClientError("Add your Mapbox public access token in Settings") }
        let coordinates = points.map { "\($0.longitude),\($0.latitude)" }.joined(separator:";")
        var url = URLComponents(string:"https://api.mapbox.com/directions/v5/mapbox/driving-traffic/\(coordinates)")!
        url.queryItems = [URLQueryItem(name:"access_token",value:key),URLQueryItem(name:"steps",value:"true"),URLQueryItem(name:"geometries",value:"geojson"),URLQueryItem(name:"overview",value:"full"),URLQueryItem(name:"annotations",value:"congestion_numeric,distance"),URLQueryItem(name:"alternatives",value:points.count == 2 ? "true" : "false")]
        let object = try await fetch(URLRequest(url:url.url!))
        guard object["code"] as? String == "Ok", let routes = object["routes"] as? [[String:Any]] else { throw WireClientError("Mapbox could not find a route") }
        return try routes.map { object in
            guard let geometry = object["geometry"] as? [String:Any], let raw = geometry["coordinates"] as? [[Double]], raw.allSatisfy({ $0.count >= 2 }), let distance = object["distance"] as? Double, let seconds = object["duration"] as? Double, let legs = object["legs"] as? [[String:Any]] else { throw WireClientError("Invalid Mapbox response") }
            let coordinates = raw.map { Coordinate($0[1],$0[0]) }
            var steps: [GuidanceStep] = [], total = 0.0, congestion: [Int?] = []
            for leg in legs {
                if let annotation = leg["annotation"] as? [String:Any], let values = annotation["congestion_numeric"] as? [Any] { congestion += values.map { $0 as? Int } }
                for step in leg["steps"] as? [[String:Any]] ?? [] {
                    guard let maneuver = step["maneuver"] as? [String:Any], let length = step["distance"] as? Double else { continue }
                    let glyph = glyphs(type:maneuver["type"] as? String ?? "",modifier:maneuver["modifier"] as? String ?? "",exit:maneuver["exit"] as? Int)
                    steps.append(GuidanceStep(instructions:maneuver["instruction"] as? String ?? "Continue",distance:length,endMeters:total,glyph:glyph.dash,podGlyph:glyph.pod)); total += length
                }
            }
            let traffic = congestion.count == max(0,coordinates.count - 1) ? congestion.enumerated().map { TrafficEdge(index:$0.offset,congestion:$0.element) } : []
            return try JourneyRoute(name:"Traffic route",coordinates:coordinates,steps:steps,distance:distance,seconds:seconds,source:"Mapbox traffic",traffic:traffic).validated()
        }
    }
    private static func valhalla(_ points:[Coordinate]) async throws -> JourneyRoute {
        guard let endpoint = URL(string:UserDefaults.standard.string(forKey:"valhallaEndpoint") ?? ""), endpoint.scheme == "https", endpoint.host != nil, endpoint.user == nil, endpoint.password == nil else { throw WireClientError("Set your HTTPS Valhalla route endpoint in Settings") }
        var request = URLRequest(url:endpoint); request.httpMethod = "POST"; request.setValue("application/json",forHTTPHeaderField:"Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject:["locations":points.map { ["lat":$0.latitude,"lon":$0.longitude] },"costing":"motorcycle","units":"kilometers","shape_format":"polyline6"])
        let object = try await fetch(request)
        guard let trip = object["trip"] as? [String:Any], let legs = trip["legs"] as? [[String:Any]], let summary = trip["summary"] as? [String:Any], let kilometers = summary["length"] as? Double, let seconds = summary["time"] as? Double else { throw WireClientError("Valhalla could not find a motorcycle route") }
        var all: [Coordinate] = [], steps:[GuidanceStep] = [], offset = 0.0
        for leg in legs {
            guard let shape = leg["shape"] as? String else { throw WireClientError("Valhalla route geometry missing") }
            let points = try Polyline.decode(shape)
            var cumulative = [0.0]
            if points.count > 1 { for i in 1..<points.count { cumulative.append(cumulative.last! + points[i-1].distance(to:points[i])) } }
            for maneuver in leg["maneuvers"] as? [[String:Any]] ?? [] {
                guard let index = maneuver["begin_shape_index"] as? Int, cumulative.indices.contains(index) else { continue }
                let type = maneuver["type"] as? Int ?? 0
                let modifiers = [9:"slight right",10:"right",11:"sharp right",12:"uturn",13:"uturn",14:"sharp left",15:"left",16:"slight left"]
                let kind = (4...6).contains(type) ? "arrive" : type == 26 ? "roundabout" : "turn"
                let glyph = glyphs(type:kind,modifier:modifiers[type] ?? "",exit:maneuver["roundabout_exit_count"] as? Int)
                steps.append(GuidanceStep(instructions:maneuver["instruction"] as? String ?? "Continue",distance:(maneuver["length"] as? Double ?? 0) * 1000,endMeters:offset + cumulative[index],glyph:glyph.dash,podGlyph:glyph.pod))
            }
            all += all.isEmpty ? points : Array(points.dropFirst()); offset += cumulative.last ?? 0
        }
        // Step markers use geometry distance. Match them to the provider's total distance scale.
        if offset > 0 { steps = steps.map { var step = $0; step.endMeters *= kilometers * 1000 / offset; return step } }
        return try JourneyRoute(name:"Motorcycle route",coordinates:all,steps:steps,distance:kilometers * 1000,seconds:seconds,source:"Valhalla motorcycle").validated()
    }
    static func glyphs(type:String,modifier:String,exit:Int?) -> (dash:UInt8,pod:UInt8) {
        if type == "arrive" { return (0x3c,0) }
        if type == "depart" { return (9,0x3c) }
        if type == "roundabout" || type == "rotary" {
            guard let exit, (1...9).contains(exit) else { return (0x31,0x0a) }
            return (UInt8(0x31 + exit),UInt8(0x0a + exit))
        }
        let code: UInt8
        switch modifier { case "left":code = 0x14; case "right":code = 0x15; case "sharp left":code = 0x16; case "sharp right":code = 0x17; case "slight left":code = 0x18; case "slight right":code = 0x19; case "uturn":code = 0x1a; default:code = 9 }
        return (code,code)
    }
    private static func fetch(_ request:URLRequest) async throws -> [String:Any] {
        let config = URLSessionConfiguration.ephemeral; config.timeoutIntervalForRequest = 30
        let session = URLSession(configuration:config,delegate:NoRedirects(),delegateQueue:nil); defer { session.finishTasksAndInvalidate() }
        let (data,response) = try await session.data(for:request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode), data.count <= 20_000_000,
              let object = try JSONSerialization.jsonObject(with:data) as? [String:Any] else { throw WireClientError("Routing request failed. Check connectivity and provider configuration.") }
        return object
    }
}
struct TrafficEdge: Codable, Identifiable {
    var index: Int; var congestion: Int?
    var id: Int { index }
    var color: Color { guard let congestion else { return .blue }; return congestion >= 80 ? .red : congestion >= 50 ? .orange : .blue }
}
struct TrafficStroke: Identifiable {
    var id:Int; var points:[CLLocationCoordinate2D]; var color:Color
}
struct JourneyRoute: Codable {
    var name: String
    var coordinates: [Coordinate]
    var steps: [GuidanceStep]
    var distance: Double
    var expectedTravelTime: Double
    var source: String
    var traffic: [TrafficEdge] = []
    var polyline: MKPolyline { MKPolyline(coordinates:coordinates.map { .init(latitude:$0.latitude,longitude:$0.longitude) },count:coordinates.count) }
    var trafficStrokes: [TrafficStroke] {
        var strokes:[TrafficStroke] = []
        var current:[CLLocationCoordinate2D] = [], band = -1, last = -2
        for edge in traffic {
            guard edge.index >= 0, edge.index < coordinates.count - 1 else { continue }
            let value = edge.congestion.map { $0 >= 80 ? 2 : $0 >= 50 ? 1 : 0 } ?? -1
            if value != band || edge.index != last + 1 {
                if current.count > 1 && band >= 0 { strokes.append(TrafficStroke(id:strokes.count,points:current,color:band == 2 ? .red : band == 1 ? .orange : .blue)) }
                current = [.init(latitude:coordinates[edge.index].latitude,longitude:coordinates[edge.index].longitude)]; band = value
            }
            current.append(.init(latitude:coordinates[edge.index+1].latitude,longitude:coordinates[edge.index+1].longitude)); last = edge.index
        }
        if current.count > 1 && band >= 0 { strokes.append(TrafficStroke(id:strokes.count,points:current,color:band == 2 ? .red : band == 1 ? .orange : .blue)) }
        return strokes
    }
    init(_ route: MKRoute) {
        name = route.name; distance = route.distance; expectedTravelTime = route.expectedTravelTime; source = "Apple Maps"
        coordinates = (0..<route.polyline.pointCount).map { let c = route.polyline.points()[$0].coordinate; return Coordinate(c.latitude,c.longitude) }
        var total = 0.0
        steps = route.steps.compactMap { step in
            total += step.distance
            return step.instructions.isEmpty ? nil : GuidanceStep(instructions:step.instructions,distance:step.distance,endMeters:total)
        }
    }
    init(title:String,points:[Coordinate]) {
        name = title; coordinates = points; distance = RouteMatcher(points:points).length
        expectedTravelTime = 0; source = "GPX track"
        steps = [GuidanceStep(instructions:"Follow the imported track",distance:distance,endMeters:distance)]
    }
    init(name:String,coordinates:[Coordinate],steps:[GuidanceStep],distance:Double,seconds:Double,source:String,traffic:[TrafficEdge] = []) {
        self.name = name; self.coordinates = coordinates; self.steps = steps; self.distance = distance; expectedTravelTime = seconds; self.source = source; self.traffic = traffic
    }
    func validated() throws -> JourneyRoute {
        guard coordinates.count >= 2, coordinates.count <= 100000, coordinates.allSatisfy(\.isValid),
              distance.isFinite, (0...1_000_000_000_000).contains(distance), expectedTravelTime.isFinite, (0...1_000_000_000_000).contains(expectedTravelTime),
              !steps.isEmpty, steps.count <= 100000, steps.allSatisfy({ $0.endMeters.isFinite && (0...1_000_000_000_000).contains($0.endMeters) && $0.distance.isFinite && (0...1_000_000_000_000).contains($0.distance) }) else { throw WireClientError("Invalid saved route") }
        guard traffic.allSatisfy({ $0.index >= 0 && $0.index < coordinates.count - 1 && ($0.congestion.map { (0...100).contains($0) } ?? true) }) else { throw WireClientError("Invalid route traffic geometry") }
        return self
    }
}

/// The sole routing, guidance and voice owner. Consoles consume its output.
@MainActor final class NavigationSession: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var fix: CLLocation?
    @Published var results: [MKMapItem] = []
    @Published var routes: [JourneyRoute] = []
    @Published var selectedRoute = 0
    @Published var destination: MKMapItem?
    @Published var active = false
    @Published var busy = false
    @Published var error: String?
    @Published var instruction = "Ready to ride"
    @Published var turnDistance: Double = 0
    @Published var remaining: Double = 0
    @Published var eta: TimeInterval = 0
    @Published var camera: MapCameraPosition = .userLocation(fallback: .automatic)
    @Published var phoneMapVisible = false
    @Published var recording = false
    @Published var maneuverGlyph: UInt8 = 0xff
    @Published var podGlyph: UInt8 = 0xff
    @Published var followPerspective = false
    @Published var routeColor = Color.blue
    @Published var trafficEnabled = true
    @Published var satellite = false
    @Published var terrain = false
    @Published var waypoints: [MKMapItem] = []
    @Published var offlinePack: RoadPack?
    @Published var offlineRecenter = UUID()
    private var offlineRouter: OfflineRouter?
    func useOfflinePack(_ pack:RoadPack) throws { offlineRouter = try OfflineRouter(pack); offlinePack = pack }
    func clearOfflinePack() { offlineRouter = nil; offlinePack = nil }
    func offlineCoordinate(_ node:Int64) -> Coordinate? { offlineRouter?.coordinate(for:node) }
    func visibleRoads(center:Coordinate,meters:Double,aspect:Double) -> [RoadEdge] { offlineRouter?.visibleEdges(center:center,meters:meters,aspect:aspect) ?? [] }
    private var matcher: RouteMatcher?
    private var routeIdentity: Int = -1
    private var lastMatchedAt: Date?
    var onGuidance: ((String, Double, Double) -> Void)?
    var onStop: (() -> Void)?
    var onFix: ((CLLocation) -> Void)?
    private var groupSharing = false
    func setGroupSharing(_ enabled: Bool) { groupSharing = enabled; location.allowsBackgroundLocationUpdates = active || recording || enabled; if enabled { requestLocation() } }
    var voiceEnabled = true
    private let location = CLLocationManager()
    private let speech = AVSpeechSynthesizer()
    private var directions: MKDirections?
    private var search: MKLocalSearch?
    private var operation = UUID()
    private var stepIndex = 0
    private var spokenIndex = -1
    private var offRouteFixes = 0
    private var lastReroute = Date.distantPast
    private var started = Date()
    private var track: [TrackFix] = []
    private var distanceMeters = 0.0
    private var maxSpeed = 0.0
    private var previous: CLLocation?
    var selectedVehicleID: UUID?
    private var recordingVehicleID: UUID?
    var route: JourneyRoute? { routes.indices.contains(selectedRoute) ? routes[selectedRoute] : nil }

    override init() {
        super.init()
        location.delegate = self
        location.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        location.activityType = .automotiveNavigation
        location.distanceFilter = 5
        location.pausesLocationUpdatesAutomatically = false
        location.showsBackgroundLocationIndicator = true
    }
    func requestLocation() {
        if location.authorizationStatus == .notDetermined { location.requestWhenInUseAuthorization() }
        else if location.authorizationStatus == .denied || location.authorizationStatus == .restricted {
            error = "Enable Location for NaviSarthi in iPhone Settings."
        } else { location.startUpdatingLocation() }
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedAlways || manager.authorizationStatus == .authorizedWhenInUse { manager.startUpdatingLocation() }
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        if (error as? CLError)?.code != .locationUnknown { self.error = error.localizedDescription }
    }
    func searchPlaces(_ query: String) async {
        guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { results = []; return }
        search?.cancel()
        let token = UUID(); operation = token; busy = true
        defer { if operation == token { busy = false } }
        let request = MKLocalSearch.Request(); request.naturalLanguageQuery = query
        if let fix { request.region = MKCoordinateRegion(center: fix.coordinate, latitudinalMeters: 30000, longitudinalMeters: 30000) }
        let task = MKLocalSearch(request: request); search = task
        do {
            if UserDefaults.standard.string(forKey:"routingProvider") == "Offline motorcycle", let pack = offlinePack {
                let categories:[String:Set<String>] = ["fuel":["fuel"],"food":["restaurant","cafe","fast_food"],"stay":["hotel","motel","guest_house","camp_site"],"repair":["motorcycle_repair","car_repair","motorcycle"],"parking":["parking"]]
                let category = categories[query.lowercased()]
                var matches = (pack.nodes + pack.places).filter { node in node.name?.localizedCaseInsensitiveContains(query) == true || category?.contains(node.category ?? "") == true }
                var names = Set(matches.compactMap(\.name))
                for edge in pack.edges where edge.name != "Road" && edge.name.localizedCaseInsensitiveContains(query) && names.insert(edge.name).inserted {
                    if let coordinate = offlineRouter?.coordinate(for:edge.from) { matches.append(RoadNode(id:edge.from,coordinate:coordinate,name:edge.name)) }
                }
                if let fix { let origin = Coordinate(fix.coordinate.latitude,fix.coordinate.longitude); matches.sort { $0.coordinate.distance(to:origin) < $1.coordinate.distance(to:origin) } }
                results = matches.prefix(40).map { node in let item = MKMapItem(placemark:MKPlacemark(coordinate:.init(latitude:node.coordinate.latitude,longitude:node.coordinate.longitude))); item.name = node.name; return item }
                if results.isEmpty { error = "No matching named road nodes. Tap the offline map to select a destination." }; return
            }
            let response = try await task.start(); if operation == token { results = response.mapItems }
        }
        catch { if operation == token { self.error = error.localizedDescription } }
    }
    func plan(to item: MKMapItem, reroute: Bool = false) async {
        guard let fix, fix.horizontalAccuracy >= 0, Date().timeIntervalSince(fix.timestamp) < 30 else {
            error = "Waiting for a fresh GPS fix before planning."; requestLocation(); return
        }
        guard reroute || !active else { error = "End the current navigation before choosing another destination."; return }
        directions?.cancel()
        let token = UUID(); operation = token; busy = true
        defer { if operation == token { busy = false } }
        let request = MKDirections.Request()
        request.source = MKMapItem(placemark: MKPlacemark(coordinate: fix.coordinate))
        request.destination = item; request.transportType = .automobile
        request.requestsAlternateRoutes = !reroute
        let task = MKDirections(request: request); directions = task
        do {
            let provider = UserDefaults.standard.string(forKey:"routingProvider") ?? "Apple Maps"
            if provider == "Offline motorcycle" {
                guard let router = offlineRouter else { throw WireClientError("Import or activate a road region in Settings > Offline regions") }
                let points = [Coordinate(fix.coordinate.latitude,fix.coordinate.longitude)] + waypoints.map { Coordinate($0.placemark.coordinate.latitude,$0.placemark.coordinate.longitude) } + [Coordinate(item.placemark.coordinate.latitude,item.placemark.coordinate.longitude)]
                let preferPaved = UserDefaults.standard.object(forKey:"offlinePreferPaved") as? Bool ?? true
                let avoidTolls = UserDefaults.standard.bool(forKey:"offlineAvoidTolls")
                let routeName = item.name ?? "Offline route"
                let planned = try await Task.detached(priority:.userInitiated) {
                    var pieces:[JourneyRoute] = []
                    for index in 1..<points.count {
                        let path = try router.route(from:points[index-1],to:points[index],preferPaved:preferPaved,avoidTolls:avoidTolls)
                        pieces.append(NativeRouter.offline(path))
                    }
                    return NativeRouter.join(pieces,name:routeName)
                }.value
                guard operation == token else { return }
                routes = [planned]; selectedRoute = 0; routeIdentity = -1; destination = item; results = []; stepIndex = 0; spokenIndex = -1; offRouteFixes = 0
                remaining = planned.distance; eta = planned.expectedTravelTime; camera = .rect(planned.polyline.boundingMapRect)
                if reroute { updateGuidance(fix) }; return
            }
            if provider != "Apple Maps" {
                let points = [Coordinate(fix.coordinate.latitude,fix.coordinate.longitude)] + waypoints.map { Coordinate($0.placemark.coordinate.latitude,$0.placemark.coordinate.longitude) } + [Coordinate(item.placemark.coordinate.latitude,item.placemark.coordinate.longitude)]
                let planned = try await NativeRouter.calculate(points:points,provider:provider)
                guard operation == token else { return }
                routes = planned; selectedRoute = 0; routeIdentity = -1; destination = item; results = []; stepIndex = 0; spokenIndex = -1; offRouteFixes = 0
                guard let route = planned.first else { throw WireClientError("No route returned") }
                remaining = route.distance; eta = route.expectedTravelTime; camera = .rect(route.polyline.boundingMapRect)
                if reroute { updateGuidance(fix) }; return
            }
            if !waypoints.isEmpty {
                var origin = request.source!, pieces: [JourneyRoute] = []
                for stop in waypoints + [item] {
                    let leg = MKDirections.Request(); leg.source = origin; leg.destination = stop; leg.transportType = .automobile
                    let worker = MKDirections(request:leg); directions = worker
                    guard let result = try await worker.calculate().routes.first else { throw WireClientError("A stop is unreachable") }
                    guard operation == token else { return }
                    pieces.append(JourneyRoute(result)); origin = stop
                }
                let joined = NativeRouter.join(pieces,name:item.name ?? "Route")
                routes = [joined]; selectedRoute = 0; routeIdentity = -1; destination = item; results = []; stepIndex = 0; spokenIndex = -1
                remaining = joined.distance; eta = joined.expectedTravelTime; camera = .rect(joined.polyline.boundingMapRect); return
            }
            let response = try await task.calculate()
            guard operation == token else { return }
            guard !response.routes.isEmpty else { error = "No driving route is available."; return }
            routes = try response.routes.map { try JourneyRoute($0).validated() }; selectedRoute = 0; routeIdentity = -1; destination = item; results = []
            stepIndex = 0; spokenIndex = -1; offRouteFixes = 0
            remaining = response.routes[0].distance; eta = response.routes[0].expectedTravelTime
            camera = .rect(response.routes[0].polyline.boundingMapRect)
            if reroute { updateGuidance(fix) }
        } catch { if operation == token { self.error = error.localizedDescription } }
    }
    func start() {
        guard route != nil else { return }
        active = true; phoneMapVisible = false; stepIndex = 0; spokenIndex = -1; routeIdentity = -1
        location.allowsBackgroundLocationUpdates = true
        startRecording(); requestLocation()
        if let fix { updateGuidance(fix) }
    }
    func startRecording() {
        guard !recording else { return }
        recording = true; recordingVehicleID = selectedVehicleID; started = Date(); track = []; previous = nil; distanceMeters = 0; maxSpeed = 0
        location.allowsBackgroundLocationUpdates = true; requestLocation()
    }
    func stop(context: ModelContext) {
        operation = UUID(); directions?.cancel(); active = false; phoneMapVisible = false
        speech.stopSpeaking(at: .immediate); instruction = "Ready to ride"; onStop?()
        stopRecording(context: context)
    }
    func stopRecording(context: ModelContext) {
        guard recording else { return }
        do {
            if track.count > 1 {
                let data = try JSONEncoder().encode(track)
                let transaction = ModelContext(context.container)
                let ride = RecordedRide(started: started, ended: Date(), distanceMeters: distanceMeters, maxSpeedMps: maxSpeed, track: data)
                ride.vehicleID = recordingVehicleID
                transaction.insert(ride)
                if let id = recordingVehicleID {
                    let descriptor = FetchDescriptor<Vehicle>(predicate:#Predicate { $0.id == id })
                    if let vehicle = try transaction.fetch(descriptor).first { vehicle.odometerKm += distanceMeters / 1000 }
                }
                try transaction.save()
            }
            recording = false; previous = nil; track = []
            location.allowsBackgroundLocationUpdates = active || groupSharing
        } catch { self.error = "Could not save ride: \(error.localizedDescription)" }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        for point in locations where CLLocationCoordinate2DIsValid(point.coordinate) && point.horizontalAccuracy >= 0 && point.horizontalAccuracy <= 50 && abs(point.timestamp.timeIntervalSinceNow) < 15 {
            guard fix == nil || point.timestamp > fix!.timestamp else { continue }
            fix = point
            onFix?(point)
            if active, let stop = waypoints.first, point.horizontalAccuracy <= 20,
               point.distance(from:CLLocation(latitude:stop.placemark.coordinate.latitude,longitude:stop.placemark.coordinate.longitude)) <= 30 { waypoints.removeFirst() }
            if recording {
                var fixSpeed = point.speed.isFinite && point.speed >= 0 ? min(80,point.speed) : 0
                if let previous {
                    let elapsed = point.timestamp.timeIntervalSince(previous.timestamp)
                    let gap = point.distance(from: previous)
                    let knownSpeed = point.speed >= 0 && previous.speed >= 0
                    let moving = knownSpeed ? max(point.speed,previous.speed) >= 0.8 : gap > max(3,min(point.horizontalAccuracy,previous.horizontalAccuracy) * 0.5)
                    if elapsed > 0 && elapsed < 30 && gap / elapsed < 80 && moving { distanceMeters += gap; fixSpeed = max(fixSpeed,gap / elapsed) }
                }
                previous = point; maxSpeed = max(maxSpeed,fixSpeed)
                track.append(TrackFix(latitude: point.coordinate.latitude, longitude: point.coordinate.longitude, time: point.timestamp, speed:fixSpeed))
            }
            if active { updateGuidance(point) }
        }
    }
    private func updateGuidance(_ point: CLLocation) {
        guard let route else { return }
        let steps = route.steps.filter { !$0.instructions.isEmpty }
        guard !steps.isEmpty else { return }
        if routeIdentity != selectedRoute { matcher = RouteMatcher(points:route.coordinates); routeIdentity = selectedRoute; lastMatchedAt = nil }
        guard let projection = matcher?.match(Coordinate(point.coordinate.latitude,point.coordinate.longitude),course:point.speed >= 2 ? point.course : nil,accuracy:point.horizontalAccuracy) else { return }
        let progress = matcher?.progress ?? projection.alongMeters
        stepIndex = min(stepIndex, steps.count - 1)
        let scaledProgress = progress * route.distance / max(1,matcher?.length ?? route.distance)
        while stepIndex < steps.count - 1 && steps[stepIndex].endMeters < scaledProgress + 10 { stepIndex += 1 }
        turnDistance = max(0,steps[stepIndex].endMeters - scaledProgress)
        instruction = steps[stepIndex].instructions
        maneuverGlyph = steps[stepIndex].glyph
        podGlyph = steps[stepIndex].podGlyph
        remaining = max(0,route.distance - scaledProgress)
        eta = route.expectedTravelTime * min(1, remaining / max(1, route.distance))
        if voiceEnabled && spokenIndex != stepIndex {
            spokenIndex = stepIndex
            speech.speak(AVSpeechUtterance(string: instruction))
        }
        onGuidance?(instruction, turnDistance, remaining)
        if stepIndex == steps.count - 1 && remaining < 20 { instruction = "Arrived — end navigation to save your ride"; maneuverGlyph = 0x3c; podGlyph = 0 }
        if followPerspective && phoneMapVisible { camera = .camera(MapCamera(centerCoordinate:point.coordinate,distance:900,heading:max(0,point.course),pitch:55)) }
        offRouteFixes = projection.crossTrackMeters > max(60, point.horizontalAccuracy * 2) ? offRouteFixes + 1 : 0
        if route.source == "GPX track" && offRouteFixes >= 3 { instruction = "Off the imported track — return to the highlighted line"; return }
        if offRouteFixes >= 3 && !busy && Date().timeIntervalSince(lastReroute) > 30, let destination {
            lastReroute = Date(); Task { await plan(to: destination, reroute: true) }
        }
    }
    func load(_ route: JourneyRoute) throws {
        guard !active else { throw WireClientError("End navigation before loading another route") }
        _ = try route.validated(); directions?.cancel(); operation = UUID(); busy = false
        routes = [route]; selectedRoute = 0; routeIdentity = -1; stepIndex = 0; results = []
        let end = route.coordinates.last!
        destination = MKMapItem(placemark:MKPlacemark(coordinate:.init(latitude:end.latitude,longitude:end.longitude))); destination?.name = route.name
        camera = .rect(route.polyline.boundingMapRect); remaining = route.distance; eta = route.expectedTravelTime
    }
    func importGPX(_ data: Data,title:String) throws { try load(JourneyRoute(title:title,points:GPXReader.read(data))) }
    func saveRoute(_ context: ModelContext) throws {
        guard let route else { return }
        context.insert(StoredRoute(title:destination?.name ?? route.name,payload:try JSONEncoder().encode(route)))
        try context.save()
    }
    func open(_ url: URL) {
        Task {
            if url.isFileURL {
                let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                do {
                    guard url.pathExtension.lowercased() == "gpx", (try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0) <= 20000000 else { throw WireClientError("Open a GPX file under 20 MB") }
                    try importGPX(BoundedFile.read(url,limit:20000000),title:url.deletingPathExtension().lastPathComponent)
                } catch { self.error = error.localizedDescription }
            } else { await acceptSharedText(url.absoluteString) }
        }
    }
    func acceptSharedText(_ text:String) async {
        guard !active else { error = "End navigation before previewing a shared destination"; return }
        error = nil
        do {
            var input = try SharedDestination.parse(text)
            if case .shortLink(let url) = input { input = try SharedDestination.parse(await resolveMapLink(url).absoluteString) }
            switch input {
            case .coordinate(let point):
                let item = MKMapItem(placemark:MKPlacemark(coordinate:.init(latitude:point.latitude,longitude:point.longitude))); item.name = "Shared destination"
                await plan(to:item)
            case .query(let query): await searchPlaces(query)
            case .shortLink: throw WireClientError("This shortened map link could not be resolved. Paste the place name or coordinates instead.")
            }
        } catch { self.error = error.localizedDescription }
    }
    private func resolveMapLink(_ original:URL) async throws -> URL {
        let configuration = URLSessionConfiguration.ephemeral; configuration.timeoutIntervalForRequest = 15
        let session = URLSession(configuration:configuration,delegate:NoRedirects(),delegateQueue:nil); defer { session.finishTasksAndInvalidate() }
        var current = original
        for _ in 0..<6 {
            guard current.scheme == "https", let host = current.host?.lowercased(), SharedDestination.mapHosts.contains(host), current.user == nil, current.password == nil else { throw WireClientError("Map link redirected outside supported map hosts") }
            var request = URLRequest(url:current); request.httpMethod = "HEAD"
            let (_,response) = try await session.data(for:request)
            guard let response = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
            if (300..<400).contains(response.statusCode), let location = response.value(forHTTPHeaderField:"Location"), let next = URL(string:location,relativeTo:current)?.absoluteURL { current = next; continue }
            guard (200..<300).contains(response.statusCode) else { throw WireClientError("Map link is unavailable") }
            return current
        }
        throw WireClientError("Too many map-link redirects")
    }
}
