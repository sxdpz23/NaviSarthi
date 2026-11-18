import SwiftUI
import SwiftData
import NaviSarthiCore
import UIKit
import UserNotifications
#if canImport(FirebaseCore) && canImport(FirebaseMessaging)
import FirebaseCore
import FirebaseMessaging
#endif

final class NaviSarthiDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application:UIApplication,didFinishLaunchingWithOptions launchOptions:[UIApplication.LaunchOptionsKey:Any]? = nil) -> Bool {
        DriveBackgroundBackup.register()
        ProjectionRuntime.shared.register()
        UNUserNotificationCenter.current().delegate = self
#if canImport(FirebaseCore) && canImport(FirebaseMessaging)
        if Bundle.main.url(forResource:"GoogleService-Info",withExtension:"plist") != nil {
            if FirebaseApp.app() == nil { FirebaseApp.configure() }
            Messaging.messaging().delegate = self
            Messaging.messaging().isAutoInitEnabled = UserDefaults.standard.bool(forKey:"pushEnabled")
            if UserDefaults.standard.bool(forKey:"pushEnabled") { application.registerForRemoteNotifications() }
        }
#endif
        DriveBackgroundBackup.schedule(enabled:UserDefaults.standard.bool(forKey:"driveDaily")); return true
    }
    func application(_ application:UIApplication,didRegisterForRemoteNotificationsWithDeviceToken deviceToken:Data) {
#if canImport(FirebaseCore) && canImport(FirebaseMessaging)
        if FirebaseApp.app() != nil { Messaging.messaging().apnsToken = deviceToken }
#endif
    }
    func userNotificationCenter(_ center:UNUserNotificationCenter,willPresent notification:UNNotification,withCompletionHandler completionHandler:@escaping (UNNotificationPresentationOptions)->Void) { completionHandler([.banner,.list,.sound]) }
    func userNotificationCenter(_ center:UNUserNotificationCenter,didReceive response:UNNotificationResponse,withCompletionHandler completionHandler:@escaping ()->Void) { completionHandler() }
}
#if canImport(FirebaseCore) && canImport(FirebaseMessaging)
extension NaviSarthiDelegate:MessagingDelegate {
    func messaging(_ messaging:Messaging,didReceiveRegistrationToken fcmToken:String?) {
        // Match Android's receiver scope: retain privately, no account registry or logging.
        if let fcmToken { try? KeychainStore.write(Data(fcmToken.utf8),key:"fcmRegistration") }
    }
}
#endif

@main @MainActor struct NaviSarthiApp: App {
    @UIApplicationDelegateAdaptor(NaviSarthiDelegate.self) private var delegate
    @StateObject private var settings = Settings()
    @StateObject private var nav = NavigationSession()
    @StateObject private var dash = DashConnection()
    @StateObject private var pod = PodConnection()
    @StateObject private var comms = CommsClient()
    @StateObject private var cloud = CloudBackupClient()
    @StateObject private var music = ConsoleMusicBridge()
    @StateObject private var calls = ConsoleCallMonitor()
    private let storage: Result<ModelContainer,Error> = Result { try AppStorageSchema.container() }
    var body: some Scene {
        WindowGroup {
            switch storage {
            case .success(let container):
                RootView().modelContainer(container)
                    .environmentObject(settings).environmentObject(nav).environmentObject(dash)
                    .environmentObject(pod).environmentObject(comms)
                    .environmentObject(cloud)
                    .environmentObject(music).environmentObject(calls)
                    .preferredColorScheme(.dark).tint(.blue)
            case .failure(let error):
                ContentUnavailableView("Storage could not open",systemImage:"externaldrive.badge.exclamationmark",description:Text(error.localizedDescription))
            }
        }
    }
}

enum AppTab: String, CaseIterable {
    case console = "Console", nav = "Nav", garage = "Garage", comms = "Comms", settings = "Settings"
    var icon: String {
        switch self {
        case .console: return "speedometer"
        case .nav: return "location.north.line"
        case .garage: return "wrench.and.screwdriver"
        case .comms: return "person.2.wave.2"
        case .settings: return "gearshape"
        }
    }
}

struct GlassPanel: ViewModifier {
    func body(content: Content) -> some View {
        content.padding(16).background(.ultraThinMaterial,in:RoundedRectangle(cornerRadius:24))
            .overlay(RoundedRectangle(cornerRadius:24).stroke(.white.opacity(0.12),lineWidth:0.5))
    }
}
extension View { func glassPanel() -> some View { modifier(GlassPanel()) } }

struct RootView: View {
    @EnvironmentObject private var settings: Settings
    @EnvironmentObject private var nav: NavigationSession
    @EnvironmentObject private var dash: DashConnection
    @EnvironmentObject private var pod: PodConnection
    @EnvironmentObject private var comms: CommsClient
    @EnvironmentObject private var cloud:CloudBackupClient
    @EnvironmentObject private var music:ConsoleMusicBridge
    @EnvironmentObject private var calls:ConsoleCallMonitor
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab = AppTab.console
    @State private var shared:SharedRouteItem?
    @State private var showShared = false
    var body: some View {
        Group {
            switch tab {
            case .console: ConsoleView()
            case .nav: NavView()
            case .garage: GarageView()
            case .comms: CommsView()
            case .settings: SettingsView()
            }
        }
        .safeAreaInset(edge:.bottom,spacing:8) {
            HStack(spacing:0) {
                ForEach(AppTab.allCases,id:\.self) { item in
                    Button {
                        tab = item
                        if item != .nav { nav.phoneMapVisible = false }
                    } label: {
                        VStack(spacing:5) {
                            Image(systemName:item.icon).font(.system(size:20))
                            Text(item == .console ? settings.target.rawValue : item.rawValue).font(.caption2)
                        }.foregroundStyle(tab == item ? Color.blue : Color.secondary)
                            .frame(maxWidth:.infinity).padding(.vertical,6).contentShape(Rectangle())
                    }.buttonStyle(.plain).accessibilityAddTraits(tab == item ? .isSelected : [])
                }
            }.glassPanel().padding(.horizontal,16)
        }
        .task {
            nav.voiceEnabled = settings.voice
            nav.routeColor = settings.routeColor
            nav.trafficEnabled = settings.traffic
            nav.terrain = settings.terrain
            nav.selectedVehicleID = UUID(uuidString:settings.vehicleID)
            nav.onGuidance = { _,turn,remaining in
                if settings.target == .pod { pod.guidance(turn:turn,remaining:remaining,glyph:nav.podGlyph) }
            }
            nav.onStop = { dash.stopStream(completed:nav.remaining < 20) }
            nav.onFix = { fix in Task { await comms.publish(fix) } }
            nav.requestLocation()
            music.changed = { enabled,title,album,artist,playing in dash.displayMusic(enabled:enabled,title:title,album:album,artist:artist,playing:playing) }
            calls.changed = { label in dash.displayCall(label) }
            calls.refresh()
            dash.onButton = { code in if !calls.ringing { music.joystick(code) } }
            dash.onNavigationExit = { nav.stop(context:context) }
        }
        .task { await comms.run() }
        .task { await music.run() }
        .task { await cloud.runSync(context:context) }
        .task { checkShareInbox() }
        .task {
            if let id = UserDefaults.standard.string(forKey:"activeRoadRegion") {
                do { let pack = try await Task.detached { try OfflineRegionStore.load(id) }.value; try nav.useOfflinePack(pack) }
                catch { nav.error = "Offline region could not load: \(error.localizedDescription)" }
            }
        }
        .onChange(of:comms.sharing) { _,sharing in nav.setGroupSharing(sharing) }
        .onChange(of:settings.voice) { _,value in nav.voiceEnabled = value }
        .onChange(of:settings.routeColour) { _,_ in nav.routeColor = settings.routeColor }
        .onChange(of:settings.vehicleAppearance) { _,_ in nav.routeColor = settings.routeColor }
        .onChange(of:settings.traffic) { _,value in nav.trafficEnabled = value }
        .onChange(of:settings.terrain) { _,value in nav.terrain = value }
        .onChange(of:settings.vehicleID) { _,value in nav.selectedVehicleID = UUID(uuidString:value) }
        .onChange(of:nav.active) { _,active in
            AppDiagnostics.shared.record(.navigation,code:active ? 1 : 0)
            if active && settings.target == .dash { dash.start(nav) }
            if !active { checkShareInbox() }
        }
        .onChange(of:dash.authenticated) { _,connected in
            AppDiagnostics.shared.record(.dashLink,code:connected ? 1 : 0)
            if connected && nav.active && settings.target == .dash { dash.start(nav) }
            if connected { music.refresh(force:true); dash.displayCall(calls.active ? (calls.ringing ? "Incoming call" : "Call active") : nil) }
        }
        .onChange(of:pod.ready) { _,ready in AppDiagnostics.shared.record(.podLink,code:ready ? 1 : 0) }
        .onChange(of:comms.realtime) { _,ready in AppDiagnostics.shared.record(.commsLink,code:ready ? 1 : 0) }
        .onChange(of:settings.target) { _,target in
            dash.stopStream()
            if nav.active && target == .dash { dash.start(nav) }
        }
        .onChange(of:scenePhase) { _,phase in
            if phase == .background { dash.sceneChanged(background:true) }
            if phase == .active { dash.sceneChanged(background:false) }
            if phase == .active && nav.active && settings.target == .dash { dash.start(nav) }
            if phase == .active { checkShareInbox() }
        }
        .onOpenURL { url in if url.isFileURL || url.scheme == "navisarthi" { tab = .nav; nav.open(url) } }
        .confirmationDialog("Preview shared destination in Nav?",isPresented:$showShared,titleVisibility:.visible) {
            Button("Preview in Nav") {
                guard let shared else { return }; tab = .nav
                nav.error = nil
                Task {
                    if shared.kind == "gpx", let data = shared.data { do { try nav.importGPX(data,title:shared.title) } catch { nav.error = error.localizedDescription } }
                    else if let text = shared.text { await nav.acceptSharedText(text) }
                    if nav.error == nil { do { try ShareInbox.remove(shared); self.shared = nil } catch { nav.error = error.localizedDescription } }
                }
            }
            Button("Discard",role:.destructive) { if let shared { do { try ShareInbox.remove(shared); self.shared = nil } catch { nav.error = error.localizedDescription } } }
            Button("Later",role:.cancel) { }
        } message: { Text(shared?.title ?? "Shared destination") }
        .alert("Navigation",isPresented:Binding(get:{ nav.error != nil },set:{ if !$0 { nav.error = nil } })) {
            Button("OK",role:.cancel) { nav.error = nil }
        } message: { Text(nav.error ?? "") }
    }
    private func checkShareInbox() {
        guard !nav.active, !showShared else { return }
        do { shared = try ShareInbox.peek(); showShared = shared != nil }
        catch { nav.error = error.localizedDescription }
    }
}
