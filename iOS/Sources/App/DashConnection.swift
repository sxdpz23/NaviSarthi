import Foundation
import Combine
import NetworkExtension
import Security
import Darwin
import UIKit
import MapKit
import NaviSarthiCore
import BackgroundTasks

/// Foreground hardware-validation adapter. No screen-off guarantee on iOS.
@MainActor final class DashConnection: ObservableObject {
    @Published var status = "Disconnected"
    @Published var authenticated = false
    @Published var streaming = false
    @Published var frames = 0
    @Published var zoom = 1200.0
    @Published var screenOffRequested = UserDefaults.standard.bool(forKey:"screenOffProjection") { didSet { UserDefaults.standard.set(screenOffRequested,forKey:"screenOffProjection") } }
    private var backgroundRendering = false
    var onButton:((UInt8)->Void)?
    var onNavigationExit:(()->Void)?
    private var callLabel:String?
    func displayCall(_ label:String?) {
        callLabel = label
        if authenticated && label == nil { try? send([TLV(5,0x22,[0])]) }
    }
    func displayMusic(enabled:Bool,title:String,album:String,artist:String,playing:Bool) {
        guard authenticated else { return }
        var fields = [TLV(5,0x17,[enabled ? 0x55 : 0xaa]),TLV(5,0x19,[playing ? 0x55 : 0xaa])]
        if enabled { fields.append(TLV(5,0x0d,ConsoleMediaWire.nowPlaying(album:album,title:title,artist:artist))) }
        try? send(fields)
    }
    private var rx: Int32 = -1
    private var tx: Int32 = -1
    private var video: Int32 = -1
    private var sequence: UInt8 = 0
    private var modulus: [UInt8]?
    private var exponent: [UInt8]?
    private var keySent = false
    private var ssid = ""
    private var timer: Timer?
    private var ticks = 0
    private var retries = 0
    private var lastReceive = Date()
    private var rendering = false
    private var snapshotter: MKMapSnapshotter?
    private var generation = UUID()
    private var streamGeneration = UUID()
    private var encoder: H264Encoder?
    private var navigation: NavigationSession?

    func connect(ssid: String, password: String) async {
        guard ssid.hasPrefix("RE_"), password.count >= 8 else { status = "Enter your RE_ Wi-Fi name and password"; return }
        disconnect(); self.ssid = ssid; status = "Joining dash Wi-Fi…"
        let token = generation
        let configuration = NEHotspotConfiguration(ssid: ssid, passphrase: password, isWEP: false)
        // joinOnce disconnects on device sleep; persistent configuration is intentional.
        configuration.joinOnce = false
        do {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                NEHotspotConfigurationManager.shared.apply(configuration) { error in
                    if let error, (error as NSError).code != NEHotspotConfigurationError.alreadyAssociated.rawValue { continuation.resume(throwing: error) }
                    else { continuation.resume() }
                }
            }
            guard generation == token else { return }
            // Bind RX BEFORE transmitting; required by the dash state machine.
            rx = try socketBound(port: 2002); tx = try socketBound(port: 2000); video = try socketBound(port: 0)
            var enabled: Int32 = 1
            guard setsockopt(tx, SOL_SOCKET, SO_BROADCAST, &enabled, socklen_t(MemoryLayout<Int32>.size)) == 0 else { throw socketError() }
            status = "Authenticating…"; lastReceive = Date(); initialBurst()
            timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in Task { @MainActor in self?.tick() } }
        } catch { disconnect(); status = error.localizedDescription }
    }
    func disconnect() {
        generation = UUID(); streamGeneration = UUID(); timer?.invalidate(); timer = nil
        snapshotter?.cancel(); snapshotter = nil; rendering = false
        if authenticated { try? send([TLV(5, 0x56, [0xaa]), TLV(6, 5, [0xaa])]) }
        for fd in [rx, tx, video] where fd >= 0 { Darwin.close(fd) }
        rx = -1; tx = -1; video = -1; authenticated = false; streaming = false
        encoder?.stop(); encoder = nil; navigation = nil; frames = 0; backgroundRendering = false; ProjectionRuntime.shared.finish(success:false)
        modulus = nil; exponent = nil; keySent = false; ticks = 0; retries = 0; status = "Disconnected"
    }
    func start(_ session: NavigationSession) {
        guard authenticated, session.active, !streaming else { return }
        streamGeneration = UUID()
        let streamToken = streamGeneration
        navigation = session
        do {
            encoder = try H264Encoder(failure:{ [weak self] error in
                Task { @MainActor in
                    guard let self, self.streamGeneration == streamToken else { return }
                    self.stopStream(); self.status = "Projection encoder stopped: \(error.localizedDescription)"
                    AppDiagnostics.shared.record(.encoderFailure,code:(error as NSError).code)
                }
            }) { [weak self] packets in
                Task { @MainActor in
                    guard let self, self.streaming, self.streamGeneration == streamToken else { return }
                    for packet in packets { try? self.sendDatagram(packet, fd: self.video, host: "192.168.1.1", port: 5000) }
                    self.frames += 1
                }
            }
            for _ in 0..<4 { try routeCard() }
            try send([TLV(5, 0x56, [0x55])]); try send([TLV(6, 0x80, [0x0b])]); try routeCard()
            streaming = true; status = "Streaming · foreground validation"
            if screenOffRequested {
                ProjectionRuntime.shared.begin { [weak self] in self?.stopStream(); self?.status = "iOS ended background projection; reopen the app to resume" }
            }
        } catch { status = error.localizedDescription; encoder?.stop(); encoder = nil }
    }
    func stopStream(completed:Bool = false) {
        ProjectionRuntime.shared.finish(success:completed); backgroundRendering = false
        streamGeneration = UUID(); streaming = false; encoder?.stop(); encoder = nil; snapshotter?.cancel(); rendering = false
        if authenticated { try? send([TLV(5, 0x56, [0xaa]), TLV(6, 5, [0xaa])]) }
        navigation = nil; status = authenticated ? "Connected" : "Disconnected"
    }
    func sceneChanged(background:Bool) {
        if background {
            guard screenOffRequested, ProjectionRuntime.shared.granted, streaming else {
                if streaming { stopStream(); status = "Screen-off projection was not granted by iOS" }; return
            }
            snapshotter?.cancel(); rendering = false; backgroundRendering = true
            status = "Screen-off CPU projection · device validation pending"
        } else { backgroundRendering = false }
    }
    private func initialBurst() {
        let cal = Calendar.current, now = Date()
        let packets: [[TLV]] = [
            [TLV(8,4,[1])], [TLV(6,0x0b,Array("NaviSarthi iOS".utf8) + [0])],
            [TLV(6,6,[UInt8(cal.component(.hour,from:now)),UInt8(cal.component(.minute,from:now)),UInt8(cal.component(.second,from:now))])],
            [TLV(5,0x57,[0x55])], [TLV(5,0x56,[0xaa])], [TLV(6,5,[0xaa])], [TLV(5,0x17,[0xaa])],
            [TLV(10,2,[0xaa,0x55,0,0,0,0,0,0])],
            [TLV(6,8,[0xff]),TLV(6,3,[0x55]),TLV(6,4,[0xa2]),TLV(6,15,[0xaa]),TLV(6,1,[1]),TLV(5,0x4c,[0x13]),TLV(5,0x2d,[0,0]),TLV(5,0x1b,[0x19]),TLV(5,0x21,[0x32]),TLV(5,0x4d,[0x32])]
        ]
        for packet in packets { try? send(packet) }
    }
    private func tick() {
        do {
            for _ in 0..<32 {
                var bytes = [UInt8](repeating: 0, count: 65535)
                var sender = sockaddr_in(); var length = socklen_t(MemoryLayout<sockaddr_in>.size)
                let count = withUnsafeMutablePointer(to: &sender) { pointer in
                    pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { address in
                        bytes.withUnsafeMutableBytes { buffer in recvfrom(rx, buffer.baseAddress, buffer.count, 0, address, &length) }
                    }
                }
                if count < 0 { if errno == EAGAIN || errno == EWOULDBLOCK { break }; throw socketError() }
                guard sender.sin_addr.s_addr == inet_addr("192.168.1.1") else { continue }
                guard let segments = try? K1G.incoming(Array(bytes.prefix(count))) else { continue }
                lastReceive = Date()
                for segment in segments { try ingest(segment) }
            }
            ticks += 1
            if !authenticated && ticks % 12 == 0 {
                retries += 1; guard retries <= 5 else { throw NSError(domain: "Dash", code: 1, userInfo: [NSLocalizedDescriptionKey:"Dash authentication timed out. Check Local Network access, multicast entitlement and Wi-Fi."]) }
                modulus = nil; exponent = nil; keySent = false; initialBurst()
            }
            if authenticated && Date().timeIntervalSince(lastReceive) > 20 { throw NSError(domain:"Dash",code:2,userInfo:[NSLocalizedDescriptionKey:"Dash stopped responding. Reconnect or power-cycle the dash."]) }
            if authenticated && ticks % 4 == 0 {
                try send([TLV(6,8,[5]),TLV(6,3,[0x55]),TLV(6,4,[0xa2]),TLV(6,1,[1])])
                if streaming { try routeCard() }
                if let callLabel { try send([TLV(5,0x22,Array(callLabel.utf8.prefix(19)) + [0])]) }
            }
            if streaming {
                try send([TLV(5,0x56,[0x55])])
                let moving = (navigation?.fix?.speed ?? 0) >= 1
                if moving || ticks % 2 == 0 { renderFrame() }
                if let navigation, let route = navigation.route { ProjectionRuntime.shared.progress(remaining:navigation.remaining,total:route.distance) }
            }
        } catch { disconnect(); status = error.localizedDescription }
    }
    private func ingest(_ tlv: TLV) throws {
        if tlv.type == 7 {
            if tlv.sub == 0 { modulus = tlv.value }
            if tlv.sub == 3 { exponent = tlv.value }
            if tlv.sub == 1 {
                if tlv.value.first == 1 { authenticated = true; status = "Connected" }
                else { keySent = false; modulus = nil; exponent = nil; try send([TLV(8,4,[1])]) }
            }
            if !keySent, let modulus, let exponent {
                let keyData = Data(derSequence(derInteger(modulus) + derInteger(exponent)))
                var error: Unmanaged<CFError>?
                let attributes: [CFString: Any] = [kSecAttrKeyType:kSecAttrKeyTypeRSA,kSecAttrKeyClass:kSecAttrKeyClassPublic,kSecAttrKeySizeInBits:1024]
                guard let key = SecKeyCreateWithData(keyData as CFData, attributes as CFDictionary, &error) else { if let error { throw error.takeRetainedValue() as Error }; throw WireError.malformed }
                var aes = [UInt8](repeating:0,count:32)
                guard SecRandomCopyBytes(kSecRandomDefault,aes.count,&aes) == errSecSuccess else { throw WireError.malformed }
                guard let encrypted = SecKeyCreateEncryptedData(key,.rsaEncryptionPKCS1,Data(Array(ssid.utf8) + aes) as CFData,&error) else { if let error { throw error.takeRetainedValue() as Error }; throw WireError.malformed }
                try send([TLV(8,0,[UInt8](encrypted as Data))]); keySent = true
            }
        }
        if tlv.type == 9 {
            if tlv.sub == 6 { try send([TLV(6,0x11,[0x55])]) }
            if tlv.sub == 4 { try send([TLV(6,0x12,[0x55])]) }
            if tlv.sub == 0, let code = tlv.value.last {
                try send([TLV(6,0x80,[code])])
                if ConsoleMediaWire.navigationExit(tlv) { onNavigationExit?(); return }
                if code == 0x14 { zoom = min(10000, zoom * 1.25) }
                if code == 0x13 { zoom = max(200, zoom / 1.25) }
                onButton?(code)
            }
        }
    }
    private func routeCard() throws {
        let title = navigation?.instruction ?? "NaviSarthi"
        let text = Array(title.utf8.prefix(60)) + [UInt8(0)]
        let remaining = min(65535, Int((navigation?.remaining ?? 0) / 100))
        let arrival = Date().addingTimeInterval(navigation?.eta ?? 0), cal = Calendar.current
        let hhmm = String(format:"%02d%02d",cal.component(.hour,from:arrival),cal.component(.minute,from:arrival))
        try send([TLV(5,1,text),TLV(5,2,[navigation?.maneuverGlyph ?? 0xff]),TLV(5,3,[0xff]),TLV(5,5,[0,0]),TLV(5,6,[0x30]),TLV(5,7,[0x30]),TLV(5,8,Array(hhmm.utf8)),TLV(5,0x54,[0x30]),TLV(5,9,be16(remaining)),TLV(5,0x46,[0x10]),TLV(5,10,[0x55]),TLV(5,12,[4]),TLV(5,11,Array("000000".utf8)),TLV(5,0x55,[0x20]),TLV(6,5,[0x55]),TLV(6,13,[0xaa])])
    }
    private func renderFrame() {
        guard !rendering, let navigation, let fix = navigation.fix else { return }
        if backgroundRendering || navigation.route?.source == "Offline motorcycle" && navigation.offlinePack != nil {
            do { try encoder?.encode(routeOnlyFrame(navigation,fix:fix)) } catch { status = error.localizedDescription }
            return
        }
        rendering = true; let token = streamGeneration
        let options = MKMapSnapshotter.Options()
        options.region = MKCoordinateRegion(center:fix.coordinate,latitudinalMeters:zoom,longitudinalMeters:zoom * 1.75)
        options.size = CGSize(width:526,height:300); options.scale = 1; options.traitCollection = UITraitCollection(userInterfaceStyle:.dark)
        if navigation.satellite { options.preferredConfiguration = MKImageryMapConfiguration(elevationStyle:navigation.terrain ? .realistic : .flat) }
        else { let configuration = MKStandardMapConfiguration(elevationStyle:navigation.terrain ? .realistic : .flat); configuration.showsTraffic = navigation.trafficEnabled; options.preferredConfiguration = configuration }
        if navigation.followPerspective { options.camera = MKMapCamera(lookingAtCenter:fix.coordinate,fromDistance:zoom,pitch:50,heading:max(0,fix.course)) }
        let snapshotter = MKMapSnapshotter(options:options); self.snapshotter = snapshotter
        snapshotter.start { [weak self] snapshot, error in
            Task { @MainActor in
                guard let self, self.streamGeneration == token else { return }
                defer { self.rendering = false }
                guard self.streaming else { return }
                guard let snapshot else {
                    self.status = "Streaming route-only frame · basemap unavailable"
                    do { try self.encoder?.encode(self.routeOnlyFrame(navigation,fix:fix)) } catch { self.status = error.localizedDescription }
                    return
                }
                let image = UIGraphicsImageRenderer(size:options.size).image { context in
                    snapshot.image.draw(at:.zero)
                    if let route = navigation.route {
                        let line = route.polyline
                        let path = UIBezierPath()
                        for i in 0..<line.pointCount {
                            let point = snapshot.point(for:line.points()[i].coordinate)
                            if i == 0 { path.move(to:point) } else { path.addLine(to:point) }
                        }
                        UIColor(navigation.routeColor).setStroke(); path.lineWidth = 8; path.lineCapStyle = .round; path.stroke()
                        if navigation.trafficEnabled {
                            for stroke in route.trafficStrokes {
                                let trafficPath = UIBezierPath()
                                for (index,coordinate) in stroke.points.enumerated() {
                                    let point = snapshot.point(for:coordinate)
                                    if index == 0 { trafficPath.move(to:point) } else { trafficPath.addLine(to:point) }
                                }
                                UIColor(stroke.color).setStroke(); trafficPath.lineWidth = 8; trafficPath.lineCapStyle = .round; trafficPath.stroke()
                            }
                        }
                    }
                    let point = snapshot.point(for:fix.coordinate)
                    UIColor.white.setFill(); context.cgContext.fillEllipse(in:CGRect(x:point.x-7,y:point.y-7,width:14,height:14))
                }
                do { try self.encoder?.encode(image) } catch { self.stopStream(); self.status = error.localizedDescription }
            }
        }
    }
    private func routeOnlyFrame(_ navigation:NavigationSession,fix:CLLocation) -> UIImage {
        UIGraphicsImageRenderer(size:CGSize(width:526,height:300)).image { context in
            UIColor(white:0.05,alpha:1).setFill(); context.fill(CGRect(x:0,y:0,width:526,height:300))
            let scale = 300 / zoom
            func project(_ c:Coordinate) -> CGPoint {
                CGPoint(x:263 + (c.longitude - fix.coordinate.longitude) * 111195 * cos(fix.coordinate.latitude * .pi / 180) * scale,y:180 - (c.latitude - fix.coordinate.latitude) * 111195 * scale)
            }
            if (navigation.route?.source == "Offline motorcycle" || backgroundRendering), let pack = navigation.offlinePack {
                let roads = UIBezierPath()
                for edge in navigation.visibleRoads(center:Coordinate(fix.coordinate.latitude,fix.coordinate.longitude),meters:zoom,aspect:1.76) {
                    guard let a = navigation.offlineCoordinate(edge.from), let b = navigation.offlineCoordinate(edge.to) else { continue }
                    let start = project(a), end = project(b)
                    guard max(start.x,end.x) >= 0, min(start.x,end.x) <= 526, max(start.y,end.y) >= 0, min(start.y,end.y) <= 300 else { continue }
                    roads.move(to:start); roads.addLine(to:end)
                }
                UIColor(white:0.3,alpha:1).setStroke(); roads.lineWidth = 2; roads.stroke()
            }
            if let route = navigation.route {
                let path = UIBezierPath()
                for (index,c) in route.coordinates.enumerated() { if index == 0 { path.move(to:project(c)) } else { path.addLine(to:project(c)) } }
                UIColor(navigation.routeColor).setStroke(); path.lineWidth = 8; path.lineCapStyle = .round; path.stroke()
            }
            UIColor.white.setFill(); context.cgContext.fillEllipse(in:CGRect(x:256,y:173,width:14,height:14))
            let attributes: [NSAttributedString.Key:Any] = [.font:UIFont.systemFont(ofSize:16,weight:.semibold),.foregroundColor:UIColor.white]
            let title = navigation.offlinePack != nil && (backgroundRendering || navigation.route?.source == "Offline motorcycle") ? "Offline road map" : "Route only · basemap unavailable"
            (title as NSString).draw(at:CGPoint(x:20,y:15),withAttributes:attributes)
        }
    }
    private func send(_ segments: [TLV]) throws {
        let packet = try K1G.build(segments,sequence:sequence); sequence &+= 1
        try sendDatagram(packet,fd:tx,host:"192.168.1.255",port:2000)
    }
    private func socketBound(port: UInt16) throws -> Int32 {
        let fd = Darwin.socket(AF_INET,SOCK_DGRAM,IPPROTO_UDP)
        guard fd >= 0 else { throw socketError() }
        var address = sockaddr_in(); address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET); address.sin_port = port.bigEndian; address.sin_addr.s_addr = INADDR_ANY
        let result = withUnsafePointer(to:&address) { $0.withMemoryRebound(to:sockaddr.self,capacity:1) { Darwin.bind(fd,$0,socklen_t(MemoryLayout<sockaddr_in>.size)) } }
        guard result == 0, fcntl(fd,F_SETFL,O_NONBLOCK) >= 0 else { let error = socketError(); Darwin.close(fd); throw error }
        return fd
    }
    private func sendDatagram(_ bytes: [UInt8], fd: Int32, host: String, port: UInt16) throws {
        guard fd >= 0 else { throw WireError.malformed }
        var address = sockaddr_in(); address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET); address.sin_port = port.bigEndian; address.sin_addr.s_addr = inet_addr(host)
        let count = withUnsafePointer(to:&address) { pointer in
            pointer.withMemoryRebound(to:sockaddr.self,capacity:1) { sendto(fd,bytes,bytes.count,0,$0,socklen_t(MemoryLayout<sockaddr_in>.size)) }
        }
        guard count == bytes.count else { throw socketError() }
    }
    private func socketError() -> NSError { NSError(domain:NSPOSIXErrorDomain,code:Int(errno)) }
    private func derLength(_ count: Int) -> [UInt8] { count < 128 ? [UInt8(count)] : (count < 256 ? [0x81, UInt8(count)] : [0x82] + be16(count)) }
    private func derInteger(_ bytes: [UInt8]) -> [UInt8] {
        var value = Array(bytes.drop(while: { $0 == 0 })); if value.isEmpty { value = [0] }
        if value[0] & 0x80 != 0 { value.insert(0,at:0) }; return [2] + derLength(value.count) + value
    }
    private func derSequence(_ bytes: [UInt8]) -> [UInt8] { [0x30] + derLength(bytes.count) + bytes }
}

/// Finite, user-started navigation work with system progress and cancellation on iOS 26.
/// This is not an unlimited execution entitlement and does not claim lock-screen hardware support.
@MainActor final class ProjectionRuntime {
    static let shared = ProjectionRuntime()
    static let identifier = (Bundle.main.bundleIdentifier ?? "com.navisarthi.ios") + ".projection"
    private var task:BGTask?
    private var ended:(()->Void)?
    private var requested = false
    var granted:Bool { task != nil }
    func register() {
#if compiler(>=6.2)
        if #available(iOS 26.0,*) {
            BGTaskScheduler.shared.register(forTaskWithIdentifier:Self.identifier,using:nil) { task in
                Task { @MainActor in
                    guard let continued = task as? BGContinuedProcessingTask, self.requested else { task.setTaskCompleted(success:false); return }
                    self.task = continued; continued.progress.totalUnitCount = 1000
                    continued.expirationHandler = { Task { @MainActor in
                        let handler = self.ended; self.finish(success:false); handler?()
                    } }
                }
            }
        }
#endif
    }
    func begin(ended:@escaping ()->Void) {
        guard UIApplication.shared.applicationState == .active, !requested else { return }
        self.ended = ended
#if compiler(>=6.2)
        if #available(iOS 26.0,*) {
            let request = BGContinuedProcessingTaskRequest(identifier:Self.identifier,title:"Motorcycle navigation",subtitle:"Updating the selected console")
            request.strategy = .fail; requested = true
            do { try BGTaskScheduler.shared.submit(request) }
            catch { requested = false; UserDefaults.standard.set("iOS declined continued processing",forKey:"projectionRuntimeStatus") }
            return
        }
#endif
        UserDefaults.standard.set("Screen-off projection requires the iOS 26/Xcode 26 path",forKey:"projectionRuntimeStatus")
    }
    func progress(remaining:Double,total:Double) {
#if compiler(>=6.2)
        if #available(iOS 26.0,*), let task = task as? BGContinuedProcessingTask, remaining.isFinite, total.isFinite, total > 0 {
            let completed = Int64(max(0,min(999,(1 - remaining / total) * 1000)))
            task.progress.completedUnitCount = max(task.progress.completedUnitCount,completed)
        }
#endif
    }
    func finish(success:Bool) {
        let current = task; task = nil; requested = false; ended = nil
        current?.expirationHandler = nil; current?.setTaskCompleted(success:success)
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier:Self.identifier)
    }
}
