import SwiftUI
import MapKit
import SwiftData
import UniformTypeIdentifiers
import NaviSarthiCore

struct NavView: View {
    @EnvironmentObject private var nav: NavigationSession
    @EnvironmentObject private var settings: Settings
    @Environment(\.modelContext) private var context
    @Query(sort:\SavedDestination.title) private var saved: [SavedDestination]
    @Query(sort:\StoredRoute.created,order:.reverse) private var storedRoutes: [StoredRoute]
    @State private var query = ""
    @State private var showSaved = false
    @State private var importing = false
    @AppStorage("routingProvider") private var provider = "Apple Maps"
    var body: some View {
        ZStack(alignment:.top) {
            if !nav.active || nav.phoneMapVisible {
                if provider == "Offline motorcycle" {
                    if let pack = nav.offlinePack { OfflineRoadMap(pack:pack).ignoresSafeArea(edges:.top) }
                    else { ContentUnavailableView("Activate an offline region",systemImage:"map",description:Text("Import a road pack in Settings > Offline regions.")) }
                } else {
                Map(position:$nav.camera) {
                    UserAnnotation()
                    if let route = nav.route {
                        MapPolyline(route.polyline).stroke(nav.routeColor,lineWidth:7)
                        if settings.traffic { ForEach(route.trafficStrokes) { stroke in MapPolyline(coordinates:stroke.points).stroke(stroke.color,lineWidth:7) } }
                    }
                    if let destination = nav.destination { Marker(destination.name ?? "Destination",coordinate:destination.placemark.coordinate) }
                }
                .mapStyle(nav.satellite ? .imagery(elevation:nav.terrain ? .realistic : .flat) : .standard(elevation:nav.terrain ? .realistic : .flat,showsTraffic:settings.traffic))
                .mapControls { MapCompass(); MapScaleView() }
                .ignoresSafeArea(edges:.top)
                }
            } else {
                Color(.systemBackground).ignoresSafeArea()
                VStack(spacing:20) {
                    Image(systemName:"location.north.circle").font(.system(size:72)).foregroundStyle(.blue)
                    Text("Navigation on \(settings.target.rawValue)").font(.title2.bold())
                    Text(nav.instruction).font(.title3).multilineTextAlignment(.center)
                    Button("View phone map temporarily") { nav.phoneMapVisible = true }
                    Text("Dash video currently requires the app in the foreground.").font(.footnote).foregroundStyle(.secondary)
                }.padding(32).frame(maxHeight:.infinity)
            }
            if !nav.active {
                VStack(spacing:10) {
                    HStack {
                        Image(systemName:"magnifyingglass").foregroundStyle(.secondary)
                        TextField("Search places or paste coordinates",text:$query).submitLabel(.search)
                            .onSubmit { search() }
                        if nav.busy { ProgressView() }
                        if !query.isEmpty { Button { query = ""; nav.results = [] } label: { Image(systemName:"xmark.circle.fill") } }
                    }.glassPanel()
                    HStack {
                        Button("Saved Destinations",systemImage:"bookmark") { showSaved = true }
                        Menu {
                            Button("Import GPX track",systemImage:"square.and.arrow.down") { importing = true }
                            if nav.route != nil { Button("Save route for offline guidance") { do { try nav.saveRoute(context) } catch { nav.error = error.localizedDescription } } }
                        } label: { Image(systemName:"ellipsis.circle") }.accessibilityLabel("Route files")
                        Spacer()
                        Button { nav.satellite.toggle() } label: { Image(systemName:"square.3.layers.3d") }.accessibilityLabel("Toggle satellite map")
                        Button { nav.camera = .userLocation(fallback:.automatic); nav.offlineRecenter = UUID(); nav.requestLocation() } label: { Image(systemName:"location.fill") }.accessibilityLabel("My location")
                    }.glassPanel()
                    ScrollView(.horizontal,showsIndicators:false) {
                        HStack(spacing:8) {
                            ForEach(["Fuel","Food","Stay","Repair","Parking"],id:\.self) { category in
                                Button(category) { query = category; Task { await nav.searchPlaces(category) } }
                                    .font(.subheadline).padding(.horizontal,14).padding(.vertical,10).background(.ultraThinMaterial,in:Capsule())
                            }
                        }
                    }
                    if !nav.results.isEmpty {
                        ScrollView {
                            VStack(alignment:.leading,spacing:0) {
                                ForEach(Array(nav.results.enumerated()),id:\.offset) { _,item in
                                    HStack { Button { Task { await nav.plan(to:item) } } label: {
                                        VStack(alignment:.leading,spacing:4) {
                                            Text(item.name ?? "Place").foregroundStyle(.primary)
                                            Text(item.placemark.title ?? "").font(.caption).foregroundStyle(.secondary).lineLimit(2)
                                        }.frame(maxWidth:.infinity,alignment:.leading).padding(.vertical,12)
                                    }
                                    Button("Stop",systemImage:"plus.circle") { if nav.waypoints.count < 23 { nav.waypoints.append(item) } }.labelStyle(.iconOnly).accessibilityLabel("Add \(item.name ?? "place") as a stop")
                                    }
                                    Divider()
                                }
                            }
                        }.frame(maxHeight:300).glassPanel()
                    }
                    if !nav.waypoints.isEmpty {
                        VStack(alignment:.leading) {
                            Text("Stops before destination").font(.caption).foregroundStyle(.secondary)
                            ForEach(Array(nav.waypoints.enumerated()),id:\.offset) { index,item in
                                HStack { Text("\(index + 1). \(item.name ?? "Stop")"); Spacer(); Button("Remove",systemImage:"xmark") { nav.waypoints.remove(at:index) }.labelStyle(.iconOnly) }
                            }
                        }.glassPanel()
                    }
                }.padding(.horizontal,16).padding(.top,8)
            }
        }
        .safeAreaInset(edge:.bottom,spacing:0) {
            if let route = nav.route {
                VStack(alignment:.leading,spacing:12) {
                    if nav.active {
                        Text(nav.instruction).font(.headline)
                        HStack { Text(settings.distance(nav.turnDistance)); Spacer(); Text("\(nav.eta > 0 ? "\(Int(nav.eta / 60)) min · " : "")\(settings.distance(nav.remaining))") }
                            .font(.subheadline).foregroundStyle(.secondary)
                        HStack {
                            if nav.phoneMapVisible { Button("Return to console view") { nav.phoneMapVisible = false } }
                            Button(nav.followPerspective ? "Top-down view" : "Perspective view") { nav.followPerspective.toggle() }
                            Spacer(); Button("End navigation",role:.destructive) { nav.stop(context:context) }
                        }
                    } else {
                        Text(nav.destination?.name ?? "Destination").font(.headline)
                        if let address = nav.destination?.placemark.title { Text(address).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
                        HStack {
                            if let phone = nav.destination?.phoneNumber, let url = URL(string:"tel:" + phone.filter { $0.isNumber || $0 == "+" }) { Link("Call",destination:url) }
                            if let url = nav.destination?.url { Link("Website",destination:url) }
                        }.font(.subheadline)
                        if nav.routes.count > 1 {
                            Picker("Route",selection:$nav.selectedRoute) {
                                ForEach(Array(nav.routes.enumerated()),id:\.offset) { index,route in Text("\(Int(route.expectedTravelTime / 60)) min").tag(index) }
                            }.pickerStyle(.segmented)
                        }
                        Text("\(settings.distance(route.distance)) · \(route.source)\(route.expectedTravelTime > 0 ? " · \(Int(route.expectedTravelTime / 60)) min" : " · No turn-by-turn directions")")
                            .font(.subheadline).foregroundStyle(.secondary)
                        HStack {
                            Button("Save",systemImage:"bookmark") { saveDestination() }
                            Spacer()
                            Button("Start navigation",systemImage:"location.north.fill") { nav.start() }.buttonStyle(.borderedProminent)
                        }
                    }
                }.glassPanel().padding(.horizontal,16).padding(.bottom,4)
            }
        }
        .sheet(isPresented:$showSaved) {
            NavigationStack {
                List {
                    if saved.isEmpty { ContentUnavailableView("No saved destinations",systemImage:"bookmark",description:Text("Find a place and tap Save.")) }
                    Section("Places") { ForEach(saved) { place in
                        Button(place.title) {
                            showSaved = false
                            let item = MKMapItem(placemark:MKPlacemark(coordinate:place.coordinate)); item.name = place.title
                            Task { await nav.plan(to:item) }
                        }
                    }.onDelete { indexes in
                        for index in indexes { context.delete(saved[index]) }
                        do { try context.save() } catch { nav.error = error.localizedDescription }
                    } }
                    Section("Offline guidance routes") {
                        ForEach(storedRoutes) { stored in
                            Button(stored.title) {
                                do { try nav.load(JSONDecoder().decode(JourneyRoute.self,from:stored.payload)); showSaved = false }
                                catch { nav.error = error.localizedDescription }
                            }
                        }.onDelete { indexes in for index in indexes { context.delete(storedRoutes[index]) }; do { try context.save() } catch { nav.error = error.localizedDescription } }
                        Text("Stored geometry and instructions work without a route request. Apple basemap tiles and off-route recalculation still require connectivity.").font(.caption).foregroundStyle(.secondary)
                    }
                }.navigationTitle("Saved Destinations")
                    .toolbar { Button("Done") { showSaved = false } }
            }.preferredColorScheme(.dark)
        }
        .fileImporter(isPresented:$importing,allowedContentTypes:[UTType(filenameExtension:"gpx") ?? .xml,.xml]) { result in
            do {
                let url = try result.get(); let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                let size = try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
                guard size <= 20_000_000 else { throw WireClientError("GPX files must be under 20 MB") }
                try nav.importGPX(BoundedFile.read(url,limit:20000000),title:url.deletingPathExtension().lastPathComponent)
            } catch { nav.error = error.localizedDescription }
        }
    }
    private func search() {
        Task { await nav.acceptSharedText(query) }
    }
    private func saveDestination() {
        guard let item = nav.destination else { return }
        if saved.contains(where:{ abs($0.latitude - item.placemark.coordinate.latitude) < 0.00001 && abs($0.longitude - item.placemark.coordinate.longitude) < 0.00001 }) { return }
        context.insert(SavedDestination(title:item.name ?? "Destination",coordinate:item.placemark.coordinate))
        do { try context.save() } catch { nav.error = error.localizedDescription }
    }
}

enum ShareInbox {
    static func directory() -> URL? {
        guard let group = Bundle.main.object(forInfoDictionaryKey:"NaviSarthiAppGroup") as? String, group.hasPrefix("group."),
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier:group) else { return nil }
        return container.appendingPathComponent("ShareInbox",isDirectory:true)
    }
    static func peek() throws -> SharedRouteItem? {
        guard let directory = directory(), FileManager.default.fileExists(atPath:directory.path) else { return nil }
        let files = try FileManager.default.contentsOfDirectory(at:directory,includingPropertiesForKeys:[.contentModificationDateKey,.fileSizeKey]).filter { $0.pathExtension == "json" }.sorted { $0.lastPathComponent < $1.lastPathComponent }
        guard let first = files.first else { return nil }
        guard (try first.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0) <= 15000000 else { throw WireClientError("Shared item is too large") }
        let item = try JSONDecoder().decode(SharedRouteItem.self,from:BoundedFile.read(first,limit:15000000))
        guard item.version == 1, UUID(uuidString:item.id) != nil, item.createdMs.isFinite, first.lastPathComponent == item.id + ".json",
              ["text","gpx"].contains(item.kind), (item.text?.utf8.count ?? 0) <= 10000, (item.data?.count ?? 0) <= 10000000,
              item.kind == "text" ? item.text?.isEmpty == false : item.data?.isEmpty == false else { throw WireClientError("Invalid share inbox item") }
        return item
    }
    static func remove(_ item:SharedRouteItem) throws {
        guard UUID(uuidString:item.id) != nil, let directory = directory() else { return }
        try FileManager.default.removeItem(at:directory.appendingPathComponent(item.id + ".json"))
    }
}

struct OfflineRoadMap: View {
    let pack:RoadPack
    @EnvironmentObject private var nav:NavigationSession
    @State private var center = Coordinate(0,0)
    @State private var meters = 3000.0
    @State private var dragStart:Coordinate?
    @State private var zoomStart:Double?
    @State private var follow = true
    private func point(_ coordinate:Coordinate,size:CGSize) -> CGPoint {
        let scale = Double(size.height) / meters
        return CGPoint(x:Double(size.width) / 2 + (coordinate.longitude - center.longitude) * 111195 * cos(center.latitude * .pi / 180) * scale,
                       y:Double(size.height) / 2 - (coordinate.latitude - center.latitude) * 111195 * scale)
    }
    var body: some View {
        GeometryReader { geometry in
            Canvas { context,size in
                context.fill(Path(CGRect(origin:.zero,size:size)),with:.color(Color(white:0.055)))
                var roads = Path()
                for edge in nav.visibleRoads(center:center,meters:meters,aspect:Double(size.width / max(1,size.height))) {
                    guard let a = nav.offlineCoordinate(edge.from), let b = nav.offlineCoordinate(edge.to) else { continue }
                    let start = point(a,size:size), end = point(b,size:size)
                    guard max(start.x,end.x) >= 0, min(start.x,end.x) <= size.width, max(start.y,end.y) >= 0, min(start.y,end.y) <= size.height else { continue }
                    roads.move(to:start); roads.addLine(to:end)
                }
                context.stroke(roads,with:.color(Color(white:0.3)),lineWidth:2)
                if let route = nav.route {
                    var path = Path()
                    for (index,c) in route.coordinates.enumerated() { if index == 0 { path.move(to:point(c,size:size)) } else { path.addLine(to:point(c,size:size)) } }
                    context.stroke(path,with:.color(nav.routeColor),style:StrokeStyle(lineWidth:7,lineCap:.round,lineJoin:.round))
                }
                if let fix = nav.fix {
                    let p = point(Coordinate(fix.coordinate.latitude,fix.coordinate.longitude),size:size)
                    context.fill(Path(ellipseIn:CGRect(x:p.x-7,y:p.y-7,width:14,height:14)),with:.color(.white))
                }
                if let end = nav.destination {
                    let p = point(Coordinate(end.placemark.coordinate.latitude,end.placemark.coordinate.longitude),size:size)
                    context.fill(Path(ellipseIn:CGRect(x:p.x-6,y:p.y-6,width:12,height:12)),with:.color(.orange))
                }
            }
            .gesture(DragGesture(minimumDistance:8).onChanged { value in
                follow = false
                let start = dragStart ?? center; dragStart = start
                let scale = Double(geometry.size.height) / meters
                center = Coordinate(start.latitude + Double(value.translation.height) / scale / 111195,start.longitude - Double(value.translation.width) / scale / (111195 * max(0.01,cos(start.latitude * .pi / 180))))
            }.onEnded { _ in dragStart = nil })
            .simultaneousGesture(MagnifyGesture().onChanged { value in
                let start = zoomStart ?? meters; zoomStart = start; meters = max(250,min(100000,start / value.magnification))
            }.onEnded { _ in zoomStart = nil })
            .simultaneousGesture(SpatialTapGesture().onEnded { value in
                guard !nav.active else { return }
                let scale = Double(geometry.size.height) / meters
                let c = Coordinate(center.latitude - (Double(value.location.y) - Double(geometry.size.height) / 2) / scale / 111195,
                                   center.longitude + (Double(value.location.x) - Double(geometry.size.width) / 2) / scale / (111195 * max(0.01,cos(center.latitude * .pi / 180))))
                guard c.isValid else { return }
                let item = MKMapItem(placemark:MKPlacemark(coordinate:.init(latitude:c.latitude,longitude:c.longitude))); item.name = "Offline map destination"
                Task { await nav.plan(to:item) }
            })
            .overlay(alignment:.bottomLeading) { Text(pack.attribution).font(.caption2).padding(6).background(.ultraThinMaterial,in:Capsule()).padding(10) }
        }
        .onAppear {
            if let fix = nav.fix, pack.nodes.contains(where:{ $0.coordinate.distance(to:Coordinate(fix.coordinate.latitude,fix.coordinate.longitude)) < 10000 }) { center = Coordinate(fix.coordinate.latitude,fix.coordinate.longitude) }
            else if let first = pack.nodes.first { center = first.coordinate; follow = false }
        }
        .onChange(of:nav.fix?.timestamp) { _,_ in if follow, let fix = nav.fix { center = Coordinate(fix.coordinate.latitude,fix.coordinate.longitude) } }
        .onChange(of:nav.offlineRecenter) { _,_ in if let fix = nav.fix { center = Coordinate(fix.coordinate.latitude,fix.coordinate.longitude); follow = true } }
        .accessibilityLabel("Offline road map. Drag to pan, pinch to zoom, tap to route to a point.")
    }
}
