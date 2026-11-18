import SwiftUI
import UIKit
import MediaPlayer
import CallKit
import NaviSarthiCore

struct ConsoleView: View {
    @EnvironmentObject private var settings: Settings
    @EnvironmentObject private var nav: NavigationSession
    @EnvironmentObject private var dash: DashConnection
    @EnvironmentObject private var pod: PodConnection
    @EnvironmentObject private var music:ConsoleMusicBridge
    @EnvironmentObject private var calls:ConsoleCallMonitor
    @State private var ssid = UserDefaults.standard.string(forKey:"dashSSID") ?? ""
    @State private var password = "12345678"
    @State private var pin = ""
    @State private var connecting = false
    @State private var holdScreen = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing:24) {
                    ZStack {
                        Circle().fill(Color(.secondarySystemBackground)).overlay(Circle().stroke(Color.blue.opacity(0.4),lineWidth:2))
                        VStack(spacing:12) {
                            Image(systemName:nav.active ? "location.north.fill" : "speedometer").font(.system(size:42)).foregroundStyle(.blue)
                            Text(nav.active ? nav.instruction : "Ready when you are").font(.title3.bold()).multilineTextAlignment(.center).lineLimit(3)
                            Text(nav.active ? settings.distance(nav.turnDistance) : settings.target.rawValue).foregroundStyle(.secondary)
                        }.padding(32)
                    }.frame(width:280,height:280)
                    VStack(alignment:.leading,spacing:14) {
                        Label(settings.target == .dash ? dash.status : pod.status,systemImage:"antenna.radiowaves.left.and.right")
                        if settings.target == .dash {
                            TextField("Dash Wi-Fi name (RE_…)",text:$ssid).textInputAutocapitalization(.never).autocorrectionDisabled()
                            SecureField("Wi-Fi password",text:$password)
                            HStack {
                                Button(connecting ? "Joining…" : "Connect") {
                                    connecting = true; UserDefaults.standard.set(ssid,forKey:"dashSSID")
                                    Task { await dash.connect(ssid:ssid,password:password); connecting = false }
                                }.buttonStyle(.borderedProminent).disabled(connecting || dash.authenticated)
                                Button("Disconnect") { dash.disconnect() }.disabled(!dash.authenticated && !connecting)
                            }
                            Button(nav.followPerspective ? "TDV · Top-down view" : "POV · Perspective view") { nav.followPerspective.toggle() }
                            if dash.streaming { Text("\(dash.frames) frames sent").font(.caption.monospacedDigit()) }
                            HStack {
                                Button("Zoom out",systemImage:"minus.magnifyingglass") { dash.zoom = min(10000,dash.zoom * 1.25) }
                                Spacer()
                                Button("Zoom in",systemImage:"plus.magnifyingglass") { dash.zoom = max(200,dash.zoom / 1.25) }
                            }
                            Toggle("Keep phone screen awake",isOn:$holdScreen)
                                .onChange(of:holdScreen) { _,value in UIApplication.shared.isIdleTimerDisabled = value }
                            Toggle("Screen-off projection (experimental)",isOn:$dash.screenOffRequested).disabled(nav.active)
                            Text("On iOS 26, requests system-controlled background work for this trip and uses the CPU road renderer. iOS may decline or cancel it; lock-screen encoder support still needs testing on your iPhone and dash.")
                                .font(.footnote).foregroundStyle(.secondary)
                        } else {
                            HStack {
                                Button("Connect Pod") { pod.connect() }.buttonStyle(.borderedProminent).disabled(pod.ready)
                                Button("Disconnect") { pod.disconnect() }
                            }
                            if pod.needsPIN {
                                TextField("Six-digit PIN",text:$pin).keyboardType(.numberPad)
                                Button("Authorize") { pod.authorize(pin) }
                            }
                            if !pod.ready { ForEach(pod.devices) { device in Button("\(device.name) · \(device.rssi) dBm") { pod.choose(device.id) } } }
                            Text("Select a Pod when pairing. Structured-provider turns, distance guidance and bounded reconnect are implemented; hardware validation remains pending.")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }.glassPanel()
                    NavigationLink("Ride history",destination:RidesView()).buttonStyle(.bordered)
                    VStack(alignment:.leading,spacing:12) {
                        if calls.active { Label(calls.ringing ? "Incoming call" : "Call active",systemImage:"phone.fill"); Text("Use your iPhone or helmet controls to answer/end cellular calls.").font(.caption).foregroundStyle(.secondary) }
                        Text("Apple Music").font(.headline)
                        if music.enabled {
                            Text(music.title.isEmpty ? "No Music app track selected" : music.title)
                            Text(music.artist).font(.caption).foregroundStyle(.secondary)
                            HStack {
                                Button("Previous",systemImage:"backward.end.fill") { music.command(.previous) }
                                Spacer()
                                Button(music.playing ? "Pause" : "Play",systemImage:music.playing ? "pause.fill" : "play.fill") { music.command(.toggle) }
                                Spacer()
                                Button("Next",systemImage:"forward.end.fill") { music.command(.next) }
                            }.labelStyle(.iconOnly).disabled(calls.ringing)
                            Button("Disable music access") { music.disable() }
                        } else { Button("Enable Apple Music controls") { Task { await music.enable() } } }
                        Text(music.status).font(.caption).foregroundStyle(.secondary)
                        Text("Controls the iPhone Music app through public APIs. Other apps' now-playing data, caller names and call history are not exposed here.").font(.caption).foregroundStyle(.secondary)
                    }.glassPanel()
                }.padding(20)
            }.navigationTitle(settings.target.rawValue)
        }
    }
}

@MainActor final class ConsoleMusicBridge:ObservableObject {
    @Published var enabled = false
    @Published var title = ""
    @Published var album = ""
    @Published var artist = ""
    @Published var playing = false
    @Published var status = ""
    var changed:((Bool,String,String,String,Bool)->Void)?
    private var last = ""
    private var player:MPMusicPlayerController { MPMusicPlayerController.systemMusicPlayer }
    func enable() async {
        let authorization = await withCheckedContinuation { (continuation:CheckedContinuation<MPMediaLibraryAuthorizationStatus,Never>) in MPMediaLibrary.requestAuthorization { continuation.resume(returning:$0) } }
        guard authorization == .authorized else { status = "Allow Music access in iPhone Settings"; return }
        UserDefaults.standard.set(true,forKey:"appleMusicEnabled"); enabled = true; refresh(force:true)
    }
    func disable() { UserDefaults.standard.set(false,forKey:"appleMusicEnabled"); enabled = false; title = ""; album = ""; artist = ""; playing = false; changed?(false,"","","",false) }
    func refresh(force:Bool = false) {
        let wasEnabled = enabled
        enabled = UserDefaults.standard.bool(forKey:"appleMusicEnabled") && MPMediaLibrary.authorizationStatus() == .authorized
        guard enabled else { if wasEnabled { title = ""; artist = ""; album = ""; playing = false; changed?(false,"","","",false) }; return }
        title = player.nowPlayingItem?.title ?? ""; album = player.nowPlayingItem?.albumTitle ?? ""; artist = player.nowPlayingItem?.artist ?? ""; playing = player.playbackState == .playing
        let digest = title + "\0" + album + "\0" + artist + (playing ? "1" : "0")
        if force || digest != last { last = digest; changed?(true,title,album,artist,playing) }
    }
    func command(_ action:ConsoleMusicCommand) {
        guard enabled else { return }
        switch action { case .previous:player.skipToPreviousItem(); case .next:player.skipToNextItem(); case .toggle: if player.playbackState == .playing { player.pause() } else { player.play() } }
        // The next player read supplies actual state; no optimistic playing toggle.
    }
    func joystick(_ code:UInt8) {
        if let action = ConsoleMediaWire.command(code) { command(action) }
        else if code == 6 || code == 7 { status = "Use iPhone or helmet volume controls" }
    }
    func run() async {
        while !Task.isCancelled { refresh(); do { try await Task.sleep(for:.seconds(2)) } catch { return } }
    }
}
@MainActor final class ConsoleCallMonitor:NSObject,ObservableObject,CXCallObserverDelegate {
    @Published var active = false
    @Published var ringing = false
    var changed:((String?)->Void)?
    private let observer = CXCallObserver()
    override init() { super.init(); observer.setDelegate(self,queue:.main); refresh() }
    func callObserver(_ callObserver:CXCallObserver,callChanged call:CXCall) {
        refresh()
    }
    func refresh() {
        let calls = observer.calls.filter { !$0.hasEnded }
        active = !calls.isEmpty; ringing = calls.contains { !$0.isOutgoing && !$0.hasConnected }
        changed?(active ? (ringing ? "Incoming call" : "Call active") : nil)
    }
}

enum DiagnosticKind:String,Codable { case navigation, dashLink, podLink, commsLink, encoderFailure, projectionStopped }
struct DiagnosticEntry:Codable,Identifiable { var id:UUID; var at:Date; var kind:DiagnosticKind; var code:Int }
@MainActor final class AppDiagnostics:ObservableObject {
    static let shared = AppDiagnostics()
    @Published private(set) var entries:[DiagnosticEntry] = []
    private var url:URL? {
        (try? FileManager.default.url(for:.applicationSupportDirectory,in:.userDomainMask,appropriateFor:nil,create:true))?.appendingPathComponent("diagnostics.json")
    }
    private init() {
        if let url, let data = try? BoundedFile.read(url,limit:500000), let saved = try? JSONDecoder().decode([DiagnosticEntry].self,from:data) { entries = Array(saved.suffix(300)) }
    }
    func record(_ kind:DiagnosticKind,code:Int) {
        entries.append(DiagnosticEntry(id:UUID(),at:Date(),kind:kind,code:code)); entries = Array(entries.suffix(300))
        if let url, let data = try? JSONEncoder().encode(entries) { try? data.write(to:url,options:[.atomic,.completeFileProtectionUntilFirstUserAuthentication]) }
    }
    func export() throws -> Data {
        struct Report:Encodable { var version:String; var os:String; var entries:[DiagnosticEntry] }
        return try JSONEncoder().encode(Report(version:Bundle.main.object(forInfoDictionaryKey:"CFBundleShortVersionString") as? String ?? "development",os:ProcessInfo.processInfo.operatingSystemVersionString,entries:entries))
    }
    func clear() { entries = []; if let url { try? FileManager.default.removeItem(at:url) } }
}
