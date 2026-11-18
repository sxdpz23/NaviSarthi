import SwiftUI
import UIKit
import SwiftData
import UniformTypeIdentifiers
import CryptoKit
import NaviSarthiCore
import AuthenticationServices
import Security
import BackgroundTasks
#if canImport(FirebaseCore) && canImport(FirebaseMessaging)
import FirebaseCore
import FirebaseMessaging
#endif

struct SettingsView: View {
    @EnvironmentObject private var settings: Settings
    @EnvironmentObject private var nav: NavigationSession
    @EnvironmentObject private var dash: DashConnection
    @EnvironmentObject private var pod: PodConnection
    var body: some View {
        NavigationStack {
            Form {
                Section("Console") {
                    Picker("Selected console",selection:$settings.target) { ForEach(ConsoleTarget.allCases) { Text($0.rawValue).tag($0) } }
                        .disabled(nav.active)
                    if nav.active { Text("End navigation before changing console.").font(.caption) }
                }
                Section("Navigation") {
                    NavigationLink("Routing provider",destination:RoutingSettingsView())
                    NavigationLink("Offline regions",destination:OfflineRegionsView())
                    Toggle("Voice instructions",isOn:$settings.voice)
                    Toggle("Miles",isOn:$settings.miles)
                    Toggle("Traffic on phone map",isOn:$settings.traffic)
                    Toggle("3D terrain on online maps",isOn:$settings.terrain)
                    Picker("Route colour",selection:$settings.routeColour) { ForEach(["Blue","Vehicle","Gold"],id:\.self) { Text($0) } }
                    Picker("Vehicle accent",selection:$settings.vehicleAppearance) { ForEach(VehicleAppearance.allCases) { Text($0.rawValue).tag($0) } }
                    Text("Apple Maps works without a key. Mapbox supplies traffic routes; Valhalla and imported road regions provide motorcycle costing. Offline regions calculate routes and draw roads locally.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Your data") {
                    NavigationLink("Ride history",destination:RidesView())
                    NavigationLink("Backup and restore",destination:BackupView())
                    NavigationLink("Cloud account and backup",destination:CloudBackupView())
                    NavigationLink("Google Drive backup",destination:DriveBackupView())
                    Text("Garage, documents, expenses, saved routes and rides are stored locally. Backups never include account passwords or Comms credentials.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("iPhone permissions") {
                    NavigationLink("Push notifications and updates",destination:DistributionSettingsView())
                    Button("Open iPhone Settings",systemImage:"arrow.up.forward.app") {
                        if let url = URL(string:UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                    Text("Location is used for navigation and recording. Bluetooth connects the Pod. Local Network and Hotspot Configuration connect the Dash.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Port status") {
                    NavigationLink("Diagnostics",destination:DiagnosticsView())
                    LabeledContent("Version",value:"0.7.1 · iOS development")
                    Text("Source project prepared on Windows; Xcode compilation, simulator tests, signing and bike validation are pending.")
                    Text("Screen-off Dash projection has an experimental iOS 26 path; system cancellation and encoder limits still need device tests. Structured turn glyphs are available with Mapbox, Valhalla or offline regions. Music controls cover Apple's Music app; call display has state only.")
                        .font(.footnote).foregroundStyle(.secondary)
                    Link("Apple background execution documentation",destination:URL(string:"https://developer.apple.com/documentation/uikit/about-the-background-execution-sequence")!)
                }
                Section {
                    Text("Independent project. Not affiliated with or endorsed by Royal Enfield. Compatibility names belong to their respective owners.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }.navigationTitle("Settings")
        }
    }
}

struct DistributionSettingsView:View {
    @AppStorage("pushEnabled") private var push = false
    @AppStorage("storeAppID") private var appID = ""
    @AppStorage("testFlightInvite") private var testFlight = ""
    @State private var status = ""
    @State private var updateURL:URL?
    @State private var version = ""
    @State private var busy = false
    var body: some View {
        Form {
            Section("Optional Firebase push") {
                Button(push ? "Disable remote notifications" : "Enable remote notifications") { Task { await changePush(!push) } }
                Text("Requires the Firebase Messaging SDK, your iOS GoogleService-Info.plist and APNs configuration. Messages open this app; they never start or replace navigation.").font(.caption).foregroundStyle(.secondary)
            }
            Section("App Store updates") {
                TextField("Published App Store numeric ID",text:$appID).keyboardType(.numberPad)
                Button("Check for updates") { Task { await checkUpdate() } }.disabled(busy)
                if let updateURL { Link("Open App Store · \(version)",destination:updateURL) }
            }
            Section("TestFlight") {
                TextField("TestFlight invitation URL",text:$testFlight).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                if let url = URL(string:testFlight), url.scheme == "https", url.host == "testflight.apple.com", url.path.hasPrefix("/join/") { Link("Open TestFlight",destination:url) }
                Text("iOS installs updates through Apple distribution channels. No Android APK updater or unsigned IPA installer is used.").font(.caption).foregroundStyle(.secondary)
            }
            if busy { ProgressView() }
            if !status.isEmpty { Text(status).font(.footnote) }
        }.navigationTitle("Notifications and updates")
    }
    private func changePush(_ enabled:Bool) async {
#if canImport(FirebaseCore) && canImport(FirebaseMessaging)
        guard FirebaseApp.app() != nil else { status = "Bundle your iOS Firebase configuration first"; return }
        do {
            if enabled {
                guard try await UNUserNotificationCenter.current().requestAuthorization(options:[.alert,.badge,.sound]) else { status = "Enable notifications in iPhone Settings"; return }
                push = true; Messaging.messaging().isAutoInitEnabled = true; UIApplication.shared.registerForRemoteNotifications()
            } else {
                push = false; Messaging.messaging().isAutoInitEnabled = false; UIApplication.shared.unregisterForRemoteNotifications()
                try await Messaging.messaging().deleteToken(); KeychainStore.remove("fcmRegistration")
            }
            status = enabled ? "Push registration requested" : "Remote notifications disabled"
        } catch { status = error.localizedDescription }
#else
        status = "Run tools/setup-push-macos.sh on your Mac and configure APNs to enable this feature"
#endif
    }
    private func checkUpdate() async {
        guard !appID.isEmpty, appID.utf8.allSatisfy({ (48...57).contains($0) }) else { status = "Enter the numeric ID after this app is published"; return }
        busy = true; updateURL = nil; defer { busy = false }
        var components = URLComponents(string:"https://itunes.apple.com/lookup")!; components.queryItems = [URLQueryItem(name:"id",value:appID)]
        let session = URLSession(configuration:.ephemeral,delegate:NoRedirects(),delegateQueue:nil); defer { session.finishTasksAndInvalidate() }
        do {
            let (data,response) = try await session.data(from:components.url!)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200, data.count <= 1000000,
                  let object = try JSONSerialization.jsonObject(with:data) as? [String:Any], let first = (object["results"] as? [[String:Any]])?.first,
                  first["bundleId"] as? String == Bundle.main.bundleIdentifier, let remote = first["version"] as? String,
                  let link = first["trackViewUrl"] as? String, let url = URL(string:link), url.scheme == "https", ["apps.apple.com","itunes.apple.com"].contains(url.host ?? "") else { throw WireClientError("No matching published iOS app found for this bundle ID") }
            let current = Bundle.main.object(forInfoDictionaryKey:"CFBundleShortVersionString") as? String ?? "0"
            version = remote; updateURL = url; status = remote.compare(current,options:.numeric) == .orderedDescending ? "An update is available" : "This version is current"
        } catch { status = error.localizedDescription }
    }
}

struct DiagnosticsView:View {
    @ObservedObject private var diagnostics = AppDiagnostics.shared
    @EnvironmentObject private var cloud:CloudBackupClient
    @State private var exporting = false
    @State private var document = BackupDocument()
    @State private var status = ""
    var body: some View {
        List {
            Section {
                Button("Export diagnostics") { do { document = BackupDocument(data:try diagnostics.export()); exporting = true } catch { status = error.localizedDescription } }
                Button("Upload to my Firebase account") { Task { await cloud.uploadDiagnostics(); status = cloud.status } }.disabled(!cloud.signedIn || cloud.busy)
                Button("Clear local diagnostics",role:.destructive) { diagnostics.clear() }
                Text("Records event codes and counters only. No coordinates, routes, names, call identifiers or authentication credentials. Nothing is uploaded automatically.").font(.caption).foregroundStyle(.secondary)
                if !status.isEmpty { Text(status).font(.caption) }
            }
            ForEach(diagnostics.entries.reversed()) { entry in HStack { Text(entry.kind.rawValue); Spacer(); Text("\(entry.code)").monospacedDigit(); Text(entry.at.formatted(date:.omitted,time:.shortened)).font(.caption).foregroundStyle(.secondary) } }
        }.navigationTitle("Diagnostics")
            .fileExporter(isPresented:$exporting,document:document,contentType:.json,defaultFilename:"NaviSarthi-diagnostics.json") { result in if case .failure(let error) = result { status = error.localizedDescription } }
    }
}
extension CloudBackupClient {
    func uploadDiagnostics() async {
        guard signedIn, !busy else { return }; busy = true; defer { busy = false }
        do {
            let auth = try await token(), data = try AppDiagnostics.shared.export()
            guard data.count <= 500000 else { throw WireClientError("Diagnostics report exceeds the upload limit") }
            let fields = ["report":["stringValue":String(decoding:data,as:UTF8.self)],"createdAt":["timestampValue":ISO8601DateFormatter().string(from:Date())]]
            _ = try await call(cloudURL("users/\(auth.uid)/iosDiagnostics/\(UUID().uuidString)"),method:"PATCH",body:["fields":fields],bearer:auth.idToken)
            status = "Diagnostics uploaded to your own account"
        } catch { status = error.localizedDescription }
    }
}

struct RoutingSettingsView: View {
    @AppStorage("routingProvider") private var provider = "Apple Maps"
    @AppStorage("valhallaEndpoint") private var endpoint = ""
    @State private var token = ""
    @State private var status = ""
    var body: some View {
        Form {
            Picker("Provider",selection:$provider) { ForEach(["Apple Maps","Mapbox traffic","Valhalla motorcycle","Offline motorcycle"],id:\.self) { Text($0) } }
            Section("Mapbox") {
                SecureField("Public access token",text:$token).textInputAutocapitalization(.never).autocorrectionDisabled()
                Button("Save token securely") { do { if token.isEmpty { KeychainStore.remove("mapboxToken") } else { try KeychainStore.write(Data(token.utf8),key:"mapboxToken") }; status = "Token updated" } catch { status = error.localizedDescription } }
            }
            Section("Your Valhalla server") {
                TextField("HTTPS route endpoint",text:$endpoint).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                Text("Use your server's complete /route URL. The motorcycle costing model must be enabled on that server.").font(.caption)
            }
            Section("Offline motorcycle") {
                NavigationLink("Import and activate a region",destination:OfflineRegionsView())
                OfflineCostOptions()
            }
            Text("Requests send your route points to the selected provider. Mapbox usage is billed to your token. Provider settings take effect on the next route calculation.").font(.footnote).foregroundStyle(.secondary)
            if !status.isEmpty { Text(status) }
        }.navigationTitle("Routing provider").onAppear { token = KeychainStore.read("mapboxToken").flatMap { String(data:$0,encoding:.utf8) } ?? "" }
    }
}

struct OfflineCostOptions: View {
    @AppStorage("offlinePreferPaved") private var paved = true
    @AppStorage("offlineAvoidTolls") private var tolls = false
    var body: some View { Group { Toggle("Prefer paved roads",isOn:$paved); Toggle("Avoid toll roads",isOn:$tolls) } }
}
struct OfflineRegion: Codable, Identifiable {
    var id:String; var title:String; var nodes:Int; var edges:Int; var attribution:String; var warnings:[String]
}
enum OfflineRegionStore {
    private static func directory() throws -> URL {
        let url = try FileManager.default.url(for:.applicationSupportDirectory,in:.userDomainMask,appropriateFor:nil,create:true).appendingPathComponent("RoadRegions",isDirectory:true)
        try FileManager.default.createDirectory(at:url,withIntermediateDirectories:true); return url
    }
    static func installed() throws -> [OfflineRegion] {
        let url = try directory().appendingPathComponent("index.json")
        guard FileManager.default.fileExists(atPath:url.path) else { return [] }
        let data = try Data(contentsOf:url); guard data.count < 1000000 else { throw OfflineError.invalid }
        return try JSONDecoder().decode([OfflineRegion].self,from:data)
    }
    private static func file(_ id:String) throws -> URL {
        guard id.count == 64, id.utf8.allSatisfy({ (48...57).contains($0) || (97...102).contains($0) }) else { throw OfflineError.invalid }
        return try directory().appendingPathComponent(id + ".json")
    }
    static func load(_ id:String) throws -> RoadPack {
        let url = try file(id), size = try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
        guard size <= 100000000 else { throw OfflineError.tooLarge }
        let data = try BoundedFile.read(url,limit:100000000)
        guard SHA256.hash(data:data).map({ String(format:"%02x",$0) }).joined() == id else { throw WireClientError("Offline region checksum mismatch") }
        let pack = try JSONDecoder().decode(RoadPack.self,from:data); try pack.validate(); return pack
    }
    static func install(_ data:Data,title:String) throws -> (OfflineRegion,RoadPack) {
        guard data.count <= 100000000 else { throw OfflineError.tooLarge }
        let pack:RoadPack
        if data.first(where:{ ![9,10,13,32].contains($0) }) == 60 { pack = try OSMRoadImporter.read(data,title:title) }
        else { pack = try JSONDecoder().decode(RoadPack.self,from:data) }
        try pack.validate()
        let encoder = JSONEncoder(); encoder.outputFormatting = .sortedKeys
        let encoded = try encoder.encode(pack), id = SHA256.hash(data:encoded).map { String(format:"%02x",$0) }.joined()
        try encoded.write(to:file(id),options:[.atomic,.completeFileProtectionUntilFirstUserAuthentication])
        var index = try installed(); index.removeAll { $0.id == id }
        let summary = OfflineRegion(id:id,title:pack.title,nodes:pack.nodes.count,edges:pack.edges.count,attribution:pack.attribution,warnings:pack.warnings)
        index.append(summary); try JSONEncoder().encode(index).write(to:directory().appendingPathComponent("index.json"),options:.atomic)
        return (summary,pack)
    }
    static func remove(_ region:OfflineRegion) throws {
        let path = try file(region.id)
        if FileManager.default.fileExists(atPath:path.path) { try FileManager.default.removeItem(at:path) }
        let remaining = try installed().filter { $0.id != region.id }
        try JSONEncoder().encode(remaining).write(to:directory().appendingPathComponent("index.json"),options:.atomic)
    }
}
struct OfflineRegionsView: View {
    @EnvironmentObject private var nav:NavigationSession
    @State private var regions:[OfflineRegion] = []
    @State private var importing = false
    @State private var busy = false
    @State private var status = ""
    @State private var downloadURL = ""
    @State private var checksum = ""
    var body: some View {
        Form {
            Section("Installed regions") {
                ForEach(regions) { region in
                    VStack(alignment:.leading,spacing:7) {
                        Text(region.title).font(.headline)
                        Text("\(region.nodes) road points · \(region.edges) directed segments").font(.caption).foregroundStyle(.secondary)
                        Text(region.attribution).font(.caption2)
                        ForEach(region.warnings,id:\.self) { Text($0).font(.caption).foregroundStyle(.orange) }
                        Button(UserDefaults.standard.string(forKey:"activeRoadRegion") == region.id ? "Active region" : "Activate") {
                            busy = true
                            Task {
                                do {
                                    let pack = try await Task.detached { try OfflineRegionStore.load(region.id) }.value
                                    try nav.useOfflinePack(pack); UserDefaults.standard.set(region.id,forKey:"activeRoadRegion"); UserDefaults.standard.set("Offline motorcycle",forKey:"routingProvider"); status = "Activated \(region.title)"
                                } catch { status = error.localizedDescription }; busy = false
                            }
                        }.disabled(nav.active || busy)
                    }
                }.onDelete { indices in
                    guard !nav.active else { status = "End navigation before removing a region"; return }
                    do {
                        for index in indices {
                            let region = regions[index]; try OfflineRegionStore.remove(region)
                            if UserDefaults.standard.string(forKey:"activeRoadRegion") == region.id { nav.clearOfflinePack(); UserDefaults.standard.removeObject(forKey:"activeRoadRegion") }
                        }
                        regions = try OfflineRegionStore.installed()
                    } catch { status = error.localizedDescription }
                }
                Button("Import OSM XML or road-pack JSON",systemImage:"square.and.arrow.down") { importing = true }.disabled(busy)
            }
            Section("Download a road pack") {
                TextField("HTTPS pack URL",text:$downloadURL).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                TextField("Publisher SHA-256",text:$checksum).textInputAutocapitalization(.never).autocorrectionDisabled()
                Button("Download and verify") { Task { await download() } }.disabled(busy)
                Text("Use a regional OSM XML extract or compatible road pack from a trusted source. No map subscription or download server is preconfigured.").font(.caption).foregroundStyle(.secondary)
            }
            Section("Offline routing") { OfflineCostOptions() }
            if busy { ProgressView("Processing region…") }
            if !status.isEmpty { Text(status).font(.footnote) }
            Text("Imported roads supply both the basemap and native motorcycle routing. OBF/PBF files are not accepted. Access and turn restrictions are conservative; unsupported ways are excluded. See docs/OFFLINE.md for limits.").font(.caption).foregroundStyle(.secondary)
        }.navigationTitle("Offline regions").onAppear { do { regions = try OfflineRegionStore.installed() } catch { status = error.localizedDescription } }
            .fileImporter(isPresented:$importing,allowedContentTypes:[.json,.xml,UTType(filenameExtension:"osm") ?? .xml]) { result in
                Task {
                    busy = true; defer { busy = false }
                    do {
                        let url = try result.get(); let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                        guard (try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0) <= 100000000 else { throw OfflineError.tooLarge }
                        let data = try BoundedFile.read(url,limit:100000000), title = url.deletingPathExtension().lastPathComponent
                        let installed = try await Task.detached { try OfflineRegionStore.install(data,title:title) }.value
                        regions = try OfflineRegionStore.installed(); status = "Imported \(installed.0.title). Activate it before offline navigation."
                    } catch { status = error.localizedDescription }
                }
            }
    }
    private func download() async {
        guard let url = URL(string:downloadURL), url.scheme == "https", url.user == nil, url.password == nil, checksum.count == 64, checksum.utf8.allSatisfy({ (48...57).contains($0) || (97...102).contains($0) }) else { status = "Enter an HTTPS URL and lowercase SHA-256"; return }
        busy = true; defer { busy = false }
        let configuration = URLSessionConfiguration.ephemeral; configuration.timeoutIntervalForResource = 180
        let session = URLSession(configuration:configuration,delegate:NoRedirects(),delegateQueue:nil); defer { session.finishTasksAndInvalidate() }
        do {
            let (file,response) = try await session.download(from:url)
            guard let response = response as? HTTPURLResponse, response.statusCode == 200, (try file.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0) <= 100000000 else { throw OfflineError.tooLarge }
            let data = try BoundedFile.read(file,limit:100000000)
            guard SHA256.hash(data:data).map({ String(format:"%02x",$0) }).joined() == checksum else { throw WireClientError("Downloaded region checksum mismatch") }
            let installed = try await Task.detached { try OfflineRegionStore.install(data,title:url.deletingPathExtension().lastPathComponent) }.value
            regions = try OfflineRegionStore.installed(); status = "Downloaded and verified \(installed.0.title)"
        } catch { status = error.localizedDescription }
    }
}

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data:Data = Data()) { self.data = data }
    init(configuration:ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration:WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents:data) }
}
struct BackupView: View {
    @Environment(\.modelContext) private var context
    @State private var exporting = false
    @State private var importing = false
    @State private var document = BackupDocument()
    @State private var pending: LocalBackup?
    @State private var confirm = false
    @State private var status = ""
    var body: some View {
        Form {
            Section("Local backup") {
                Button("Export all local data",systemImage:"square.and.arrow.up") {
                    do { try context.save(); document = BackupDocument(data:try JSONEncoder().encode(LocalBackup.capture(context))); exporting = true }
                    catch { status = error.localizedDescription }
                }
                Button("Import backup",systemImage:"square.and.arrow.down") { importing = true }
                Text("Includes documents and invoices. Keep the exported file private. Import adds missing records and preserves existing IDs and edits.").font(.footnote).foregroundStyle(.secondary)
            }
            if !status.isEmpty { Text(status) }
        }.navigationTitle("Backup and restore")
            .fileExporter(isPresented:$exporting,document:document,contentType:.json,defaultFilename:"NaviSarthi-backup.json") { result in
                if case .failure(let error) = result { status = error.localizedDescription }
            }
            .fileImporter(isPresented:$importing,allowedContentTypes:[.json]) { result in
                do {
                    let url = try result.get(); let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                    let size = try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
                    guard size <= 100_000_000 else { throw WireClientError("Backup exceeds 100 MB") }
                    pending = try LocalBackup.read(BoundedFile.read(url,limit:100000000)); confirm = true
                } catch { status = error.localizedDescription }
            }
            .confirmationDialog("Import \(pending?.rows.count ?? 0) backup records?",isPresented:$confirm,titleVisibility:.visible) {
                Button("Import missing records") {
                    do { if let pending { status = "Imported \(try pending.restore(into:context.container)) new records." } }
                    catch { status = error.localizedDescription }; pending = nil
                }
                Button("Cancel",role:.cancel) { pending = nil }
            }
    }
}

struct FirebaseLogin: Codable {
    var uid: String; var idToken: String; var refreshToken: String; var expires: Date
}
@MainActor final class CloudBackupClient: ObservableObject {
    @Published var signedIn = false
    @Published var busy = false
    @Published var status = ""
    @Published var project = UserDefaults.standard.string(forKey:"firebaseProject") ?? ""
    @Published var apiKey = UserDefaults.standard.string(forKey:"firebasePublicKey") ?? ""
    @Published var syncEnabled = UserDefaults.standard.bool(forKey:"cloudSyncEnabled") { didSet { UserDefaults.standard.set(syncEnabled,forKey:"cloudSyncEnabled") } }
    @Published var syncVehicle = UserDefaults.standard.string(forKey:"cloudSyncVehicle") ?? "" { didSet { UserDefaults.standard.set(syncVehicle,forKey:"cloudSyncVehicle") } }
    @Published var syncStatus = "Automatic sync is off"
    @Published var syncConflicts:[String] = []
    private var lastSync = Date.distantPast
    private var lastLocalDigest = ""
    private var login: FirebaseLogin?
    private let session: URLSession
    init() {
        let config = URLSessionConfiguration.ephemeral; config.timeoutIntervalForRequest = 30
        session = URLSession(configuration:config,delegate:NoRedirects(),delegateQueue:nil)
        if let data = KeychainStore.read("firebaseLogin"), let value = try? JSONDecoder().decode(FirebaseLogin.self,from:data) { login = value; signedIn = true }
    }
    func signIn(email:String,password:String,create:Bool = false) async {
        guard !busy else { return }; busy = true; defer { busy = false }
        do {
            try validateConfiguration()
            let url = try authURL(host:"identitytoolkit.googleapis.com",path:"/v1/accounts:\(create ? "signUp" : "signInWithPassword")")
            let data = try await call(url,method:"POST",body:["email":email,"password":password,"returnSecureToken":true])
            struct Response: Decodable { var localId:String; var idToken:String; var refreshToken:String; var expiresIn:String }
            let result = try JSONDecoder().decode(Response.self,from:data)
            guard !result.localId.contains("/"), !result.localId.isEmpty else { throw WireClientError("Unsupported account ID") }
            let login = FirebaseLogin(uid:result.localId,idToken:result.idToken,refreshToken:result.refreshToken,expires:Date().addingTimeInterval(Double(result.expiresIn) ?? 3600))
            try save(login); signedIn = true; status = "Signed in. Cloud backup is explicit; no data uploaded yet."
            syncEnabled = false; syncConflicts = []; lastLocalDigest = ""
            UserDefaults.standard.set(project,forKey:"firebaseProject"); UserDefaults.standard.set(apiKey,forKey:"firebasePublicKey")
        } catch { status = error.localizedDescription }
    }
    func resetPassword(email:String) async {
        guard !busy else { return }; busy = true; defer { busy = false }
        do { try validateConfiguration(); _ = try await call(authURL(host:"identitytoolkit.googleapis.com",path:"/v1/accounts:sendOobCode"),method:"POST",body:["requestType":"PASSWORD_RESET","email":email]); status = "If the account exists, a reset email will be sent." }
        catch { status = error.localizedDescription }
    }
    func signOut() { syncEnabled = false; syncConflicts = []; lastLocalDigest = ""; login = nil; signedIn = false; KeychainStore.remove("firebaseLogin"); status = "Signed out" }
    private func validateConfiguration() throws {
        guard (6...63).contains(project.count), project.utf8.allSatisfy({ (97...122).contains($0) || (48...57).contains($0) || $0 == 45 }), !apiKey.isEmpty else { throw WireClientError("Enter your Firebase project ID and public Web API key") }
    }
    private func authURL(host:String,path:String) throws -> URL {
        var parts = URLComponents(); parts.scheme = "https"; parts.host = host; parts.path = path; parts.queryItems = [URLQueryItem(name:"key",value:apiKey)]
        guard let url = parts.url else { throw URLError(.badURL) }; return url
    }
    private func save(_ value: FirebaseLogin) throws { try KeychainStore.write(JSONEncoder().encode(value),key:"firebaseLogin"); login = value }
    private func token() async throws -> FirebaseLogin {
        guard let login else { throw WireClientError("Sign in first") }
        if login.expires.timeIntervalSinceNow > 60 { return login }
        let data = try await call(authURL(host:"securetoken.googleapis.com",path:"/v1/token"),method:"POST",body:["grant_type":"refresh_token","refresh_token":login.refreshToken])
        struct Refresh:Decodable { var id_token:String; var refresh_token:String; var expires_in:String; var user_id:String }
        let response = try JSONDecoder().decode(Refresh.self,from:data)
        guard response.user_id == login.uid else { throw WireClientError("Account changed; sign in again") }
        let renewed = FirebaseLogin(uid:login.uid,idToken:response.id_token,refreshToken:response.refresh_token,expires:Date().addingTimeInterval(Double(response.expires_in) ?? 3600))
        try save(renewed); return renewed
    }
    private var documentsPath: String { "projects/\(project)/databases/(default)/documents" }
    private func cloudURL(_ path:String) -> URL { URL(string:"https://firestore.googleapis.com/v1/\(documentsPath)/\(path)")! }
    func upload(_ backup:LocalBackup) async {
        guard !busy else { return }; busy = true; defer { busy = false }
        do {
            let auth = try await token(), data = try JSONEncoder().encode(backup)
            guard data.count <= 4_000_000 else { throw WireClientError("Cloud backups are limited to 4 MB. Use local file export for larger backups with attachments.") }
            let latest = "users/\(auth.uid)/iosState/latest"
            let previous = try await getDocument(latest,token:auth.idToken,allowMissing:true)
            let generation = UUID().uuidString
            let count = (data.count + 299999) / 300000
            var writes: [[String:Any]] = []
            for index in 0..<count {
                let chunk = data.subdata(in:(index * 300000)..<min(data.count,(index + 1) * 300000))
                let name = "\(documentsPath)/users/\(auth.uid)/iosBackups/\(generation)-\(index)"
                writes.append(["update":["name":name,"fields":["data":["bytesValue":chunk.base64EncodedString()]]],"currentDocument":["exists":false]])
            }
            let checksum = SHA256.hash(data:data).map { String(format:"%02x",$0) }.joined()
            let fields: [String:Any] = ["generation":["stringValue":generation],"chunks":["integerValue":String(count)],"sha256":["stringValue":checksum],"createdAt":["timestampValue":ISO8601DateFormatter().string(from:Date())]]
            let precondition: [String:Any]
            if let time = previous?["updateTime"] as? String { precondition = ["updateTime":time] }
            else { precondition = ["exists":false] }
            writes.append(["update":["name":"\(documentsPath)/\(latest)","fields":fields],"currentDocument":precondition])
            _ = try await call(URL(string:"https://firestore.googleapis.com/v1/\(documentsPath):commit")!,method:"POST",body:["writes":writes],bearer:auth.idToken)
            status = "Cloud backup uploaded atomically. Older backup chunks are retained; manage storage in Firebase."
        } catch { status = error.localizedDescription }
    }
    func download() async throws -> LocalBackup {
        guard !busy else { throw WireClientError("Another cloud operation is in progress") }; busy = true; defer { busy = false }
        let auth = try await token()
        guard let document = try await getDocument("users/\(auth.uid)/iosState/latest",token:auth.idToken), let fields = document["fields"] as? [String:Any], let generation = (fields["generation"] as? [String:Any])?["stringValue"] as? String,
              UUID(uuidString:generation) != nil, let countText = (fields["chunks"] as? [String:Any])?["integerValue"] as? String, let count = Int(countText), (1...14).contains(count), let checksum = (fields["sha256"] as? [String:Any])?["stringValue"] as? String else { throw WireClientError("Invalid cloud backup manifest") }
        var bytes = Data()
        for index in 0..<count {
            guard let chunk = try await getDocument("users/\(auth.uid)/iosBackups/\(generation)-\(index)",token:auth.idToken), let fields = chunk["fields"] as? [String:Any], let text = (fields["data"] as? [String:Any])?["bytesValue"] as? String, let data = Data(base64Encoded:text), data.count <= 300000 else { throw WireClientError("Missing cloud backup chunk") }
            bytes.append(data)
        }
        guard SHA256.hash(data:bytes).map({ String(format:"%02x",$0) }).joined() == checksum else { throw WireClientError("Cloud backup checksum mismatch") }
        return try LocalBackup.read(bytes)
    }
    private func getDocument(_ path:String,token:String,allowMissing:Bool = false) async throws -> [String:Any]? {
        do { return try JSONSerialization.jsonObject(with:await call(cloudURL(path),method:"GET",bearer:token)) as? [String:Any] }
        catch let error as WireClientError { if allowMissing && error.message.hasPrefix("HTTP 404") { return nil }; throw error }
    }
    private func call(_ url:URL,method:String,body:[String:Any]? = nil,bearer:String? = nil) async throws -> Data {
        var request = URLRequest(url:url); request.httpMethod = method
        if let body {
            if url.host == "securetoken.googleapis.com" {
                let encoded = body.map { key,value in
                    (key.addingPercentEncoding(withAllowedCharacters:.alphanumerics) ?? "") + "=" + (String(describing:value).addingPercentEncoding(withAllowedCharacters:.alphanumerics) ?? "")
                }.joined(separator:"&")
                request.httpBody = Data(encoded.utf8); request.setValue("application/x-www-form-urlencoded",forHTTPHeaderField:"Content-Type")
            } else { request.httpBody = try JSONSerialization.data(withJSONObject:body); request.setValue("application/json",forHTTPHeaderField:"Content-Type") }
        }
        if let bearer { request.setValue("Bearer \(bearer)",forHTTPHeaderField:"Authorization") }
        let (data,response) = try await session.data(for:request)
        guard let response = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        guard (200..<300).contains(response.statusCode) else { throw WireClientError("HTTP \(response.statusCode) — check Firebase credentials, email/password provider and owner-only Firestore rules") }
        return data
    }
}

private struct SyncRemote {
    var row:BackupRow
    var id:String
    var version:String
    var digest:String
}
private enum CollectionWire {
    static let collections = ["vehicle":"state","fuel":"fuel","service":"services","saved":"saved","ride":"rides","rule":"maintenance"]
    static func key(_ row:BackupRow) -> String { row.kind + ":" + row.id.uuidString }
    static func hash(_ row:BackupRow) throws -> String {
        let data = try JSONSerialization.data(withJSONObject:fields(row),options:[.sortedKeys,.withoutEscapingSlashes])
        return SHA256.hash(data:data).map { String(format:"%02x",$0) }.joined()
    }
    static func fields(_ row:BackupRow) throws -> [String:Any] {
        switch row.kind {
        case "vehicle": return ["odometerKm":row.a]
        case "fuel": return ["dateMs":(row.date!.timeIntervalSince1970 * 1000).rounded(),"litres":row.b,"cost":row.c,"odometerKm":floor(row.a)]
        case "service": return ["title":row.title,"odometerKm":floor(row.a),"dateMs":(row.date!.timeIntervalSince1970 * 1000).rounded(),"cost":row.b,"note":row.text]
        case "saved": return ["name":row.title,"lat":row.a,"lng":row.b]
        case "rule": return ["name":row.title,"intervalKm":floor(row.a),"intervalMonths":floor(row.b),"lastDoneOdoKm":floor(row.c),"lastDoneDateMs":(row.date!.timeIntervalSince1970 * 1000).rounded()]
        case "ride":
            let fixes = try JSONDecoder().decode([TrackFix].self,from:row.payload!)
            let start = (row.date!.timeIntervalSince1970 * 1000).rounded(), end = (row.end!.timeIntervalSince1970 * 1000).rounded()
            let duration = max(0,(end - start) / 1000)
            let shape = try Polyline.encode(fixes.map { Coordinate($0.latitude,$0.longitude) })
            let normalized = shape.isEmpty ? [] : try Polyline.decode(shape,precision:5)
            return ["startMs":start,"endMs":end,"distanceMeters":row.a,"durationSec":floor(duration),"avgSpeedMps":duration > 0 ? row.a / duration : 0,"maxSpeedMps":row.b,"track":shape,"startLat":normalized.first?.latitude ?? 0,"startLng":normalized.first?.longitude ?? 0,"endLat":normalized.last?.latitude ?? 0,"endLng":normalized.last?.longitude ?? 0]
        default: throw WireClientError("Unsupported sync collection")
        }
    }
    static func encodeFields(_ fields:[String:Any]) throws -> [String:Any] {
        var result:[String:Any] = [:]
        for (key,value) in fields {
            if let value = value as? String { result[key] = ["stringValue":value] }
            else if let value = value as? NSNumber {
                let number = value.doubleValue
                guard number.isFinite else { throw WireClientError("Non-finite sync value") }
                result[key] = number.rounded() == number && abs(number) < 9_000_000_000_000_000 ? ["integerValue":String(format:"%.0f",number)] : ["doubleValue":number]
            } else { throw WireClientError("Unsupported sync value type") }
        }
        return result
    }
    static func decode(_ document:[String:Any],kind:String,scope:UUID) throws -> SyncRemote {
        guard let name = document["name"] as? String, let id = name.split(separator:"/").last.map(String.init), !id.isEmpty,
              let version = document["updateTime"] as? String, let fields = document["fields"] as? [String:[String:Any]] else { throw WireClientError("Invalid Firestore document") }
        func string(_ key:String) -> String { fields[key]?["stringValue"] as? String ?? "" }
        func number(_ key:String) throws -> Double {
            let raw = fields[key]?["doubleValue"] as? Double ?? (fields[key]?["integerValue"] as? String).flatMap(Double.init) ?? 0
            guard raw.isFinite, abs(raw) <= 9_000_000_000_000_000, raw >= 0 || ["lat","lng","startLat","startLng","endLat","endLng"].contains(key) else { throw WireClientError("Invalid cloud number") }
            return raw
        }
        func date(_ key:String) throws -> Date { Date(timeIntervalSince1970:try number(key) / 1000) }
        let table = kind == "rule" ? "maintenance" : kind
        let localID = kind == "vehicle" ? scope : AndroidBackup.stableID(table,id)
        var row = BackupRow(kind:kind,id:localID,parent:scope)
        switch kind {
        case "vehicle": row.a = try number("odometerKm")
        case "fuel": row.date = try date("dateMs"); row.a = try number("odometerKm"); row.b = try number("litres"); row.c = try number("cost"); guard row.b > 0 else { throw WireClientError("Cloud fuel litres must be positive") }
        case "service": row.title = string("title"); row.text = string("note"); row.date = try date("dateMs"); row.a = try number("odometerKm"); row.b = try number("cost")
        case "saved": row.title = string("name"); row.a = try number("lat"); row.b = try number("lng"); guard Coordinate(row.a,row.b).isValid else { throw WireClientError("Invalid cloud destination") }
        case "rule": row.title = string("name"); row.date = try date("lastDoneDateMs"); row.a = try number("intervalKm"); row.b = try number("intervalMonths"); row.c = try number("lastDoneOdoKm"); guard row.b <= 1200 else { throw WireClientError("Invalid cloud maintenance interval") }
        case "ride":
            row.date = try date("startMs"); row.end = try date("endMs"); row.a = try number("distanceMeters"); row.b = try number("maxSpeedMps")
            guard row.end! >= row.date! else { throw WireClientError("Invalid cloud ride times") }
            let shape = string("track"), coordinates = shape.isEmpty ? [] : try Polyline.decode(shape,precision:5)
            let fixes = coordinates.enumerated().map { index,c in TrackFix(latitude:c.latitude,longitude:c.longitude,time:row.date!.addingTimeInterval(row.end!.timeIntervalSince(row.date!) * Double(index) / Double(max(1,coordinates.count - 1))),speed:0) }
            row.payload = try JSONEncoder().encode(fixes)
        default: throw WireClientError("Unsupported cloud collection")
        }
        return SyncRemote(row:row,id:id,version:version,digest:try hash(row))
    }
    static func localRows(_ context:ModelContext,scope:UUID) throws -> [BackupRow] {
        guard let vehicle = try context.fetch(FetchDescriptor<Vehicle>()).first(where:{ $0.id == scope }) else { throw WireClientError("Select an existing motorcycle for cloud sync") }
        var rows = [BackupRow(kind:"vehicle",id:scope,a:vehicle.odometerKm)]
        for v in try context.fetch(FetchDescriptor<FuelEntry>()) where v.vehicleID == scope { rows.append(BackupRow(kind:"fuel",id:v.id,parent:scope,date:v.date,a:v.odometerKm,b:v.litres,c:v.cost)) }
        for v in try context.fetch(FetchDescriptor<ServiceEntry>()) where v.vehicleID == scope { rows.append(BackupRow(kind:"service",id:v.id,parent:scope,title:v.title,text:v.notes,date:v.date,a:v.odometerKm,b:v.cost)) }
        for v in try context.fetch(FetchDescriptor<SavedDestination>()) { rows.append(BackupRow(kind:"saved",id:v.id,title:v.title,a:v.latitude,b:v.longitude)) }
        for v in try context.fetch(FetchDescriptor<RecordedRide>()) where v.vehicleID == scope { rows.append(BackupRow(kind:"ride",id:v.id,parent:scope,date:v.started,end:v.ended,a:v.distanceMeters,b:v.maxSpeedMps,payload:v.track)) }
        for v in try context.fetch(FetchDescriptor<MaintenanceRule>()) where v.vehicleID == scope && !v.scheduled { rows.append(BackupRow(kind:"rule",id:v.id,parent:scope,title:v.name,date:v.lastDate,a:v.intervalKm,b:Double(v.intervalMonths),c:v.lastKm)) }
        return rows
    }
    static func apply(_ row:BackupRow?,kind:String,id:UUID,scope:UUID,context:ModelContext) throws {
        switch kind {
        case "vehicle":
            if let vehicle = try context.fetch(FetchDescriptor<Vehicle>()).first(where:{ $0.id == scope }) { vehicle.odometerKm = row?.a ?? 0 }
        case "fuel":
            let old = try context.fetch(FetchDescriptor<FuelEntry>()).first { $0.id == id }
            guard let row else { if let old { context.delete(old) }; return }
            let v = old ?? FuelEntry(vehicleID:scope,date:row.date!,litres:row.b,cost:row.c,odometerKm:row.a,fullTank:false)
            v.id = id; v.date = row.date!; v.litres = row.b; v.cost = row.c; v.odometerKm = row.a
            if old == nil { context.insert(v) }
        case "service":
            let old = try context.fetch(FetchDescriptor<ServiceEntry>()).first { $0.id == id }
            guard let row else { if let old { RideReminders.cancel(old.id); context.delete(old) }; return }
            let v = old ?? ServiceEntry(vehicleID:scope,title:row.title,date:row.date!,odometerKm:row.a,cost:row.b,notes:row.text,nextDueKm:0,nextDueDate:nil)
            v.id = id; v.title = row.title; v.date = row.date!; v.odometerKm = row.a; v.cost = row.b; v.notes = row.text
            if old == nil { context.insert(v) }
        case "saved":
            let old = try context.fetch(FetchDescriptor<SavedDestination>()).first { $0.id == id }
            guard let row else { if let old { context.delete(old) }; return }
            let v = old ?? SavedDestination(title:row.title,coordinate:.init(latitude:row.a,longitude:row.b))
            v.id = id; v.title = row.title; v.latitude = row.a; v.longitude = row.b
            if old == nil { context.insert(v) }
        case "ride":
            let old = try context.fetch(FetchDescriptor<RecordedRide>()).first { $0.id == id }
            guard let row else { if let old { context.delete(old) }; return }
            let v = old ?? RecordedRide(started:row.date!,ended:row.end!,distanceMeters:row.a,maxSpeedMps:row.b,track:row.payload!)
            v.id = id; v.vehicleID = scope; v.started = row.date!; v.ended = row.end!; v.distanceMeters = row.a; v.maxSpeedMps = row.b; v.track = row.payload!
            if old == nil { context.insert(v) }
        case "rule":
            let old = try context.fetch(FetchDescriptor<MaintenanceRule>()).first { $0.id == id }
            guard let row else { if let old { RideReminders.cancel(old.id); context.delete(old) }; return }
            let v = old ?? MaintenanceRule(vehicleID:scope,name:row.title,intervalKm:row.a,intervalMonths:Int(row.b),lastKm:row.c,lastDate:row.date!)
            v.id = id; v.name = row.title; v.intervalKm = row.a; v.intervalMonths = Int(row.b); v.lastKm = row.c; v.lastDate = row.date!
            if old == nil { context.insert(v) }
        default: throw WireClientError("Unsupported sync collection")
        }
    }
}

extension CloudBackupClient {
    func runSync(context:ModelContext) async {
        while !Task.isCancelled {
            if syncEnabled && signedIn && !busy && syncConflicts.isEmpty, let scope = UUID(uuidString:syncVehicle) {
                do {
                    let rows = try CollectionWire.localRows(context,scope:scope)
                    let digest = try rows.map { CollectionWire.key($0) + (try CollectionWire.hash($0)) }.sorted().joined()
                    if digest != lastLocalDigest || Date().timeIntervalSince(lastSync) >= 120 { await synchronize(context:context) }
                } catch { syncStatus = error.localizedDescription }
            }
            do { try await Task.sleep(for:.seconds(10)) } catch { return }
        }
    }
    func synchronize(context:ModelContext,resolution:String = "Ask") async {
        guard !busy, syncEnabled, let scope = UUID(uuidString:syncVehicle) else { return }
        busy = true; defer { busy = false }; syncConflicts = []
        struct Work {
            var kind:String; var localID:UUID; var remoteID:String; var local:BackupRow?; var remote:SyncRemote?; var link:CloudRecordLink?; var action:SyncAction; var version:String?
        }
        do {
            try context.save()
            let auth = try await token()
            let originalRows = try CollectionWire.localRows(context,scope:scope)
            let locals = Dictionary(uniqueKeysWithValues:originalRows.map { (CollectionWire.key($0),$0) })
            let transaction = ModelContext(context.container); transaction.autosaveEnabled = false
            let links = try transaction.fetch(FetchDescriptor<CloudRecordLink>()).filter { $0.owner == auth.uid && $0.scope == scope }
            var linkMap:[String:CloudRecordLink] = [:], remotes:[String:SyncRemote] = [:]
            for link in links {
                let key = link.kind + ":" + link.localID.uuidString
                guard linkMap[key] == nil else { throw WireClientError("Duplicate cloud sync metadata") }; linkMap[key] = link
            }
            for (kind,collection) in CollectionWire.collections.sorted(by:{ $0.key < $1.key }) {
                if kind == "vehicle" {
                    if let document = try await getDocument("users/\(auth.uid)/state/bike",token:auth.idToken,allowMissing:true) {
                        let remote = try CollectionWire.decode(document,kind:kind,scope:scope); remotes[CollectionWire.key(remote.row)] = remote
                    }
                } else {
                    for document in try await listCollection(collection,uid:auth.uid,token:auth.idToken) {
                        let remote = try CollectionWire.decode(document,kind:kind,scope:scope)
                        let key = CollectionWire.key(remote.row)
                        guard remotes[key] == nil else { throw WireClientError("Cloud IDs collide after migration") }; remotes[key] = remote
                    }
                }
            }
            var work:[Work] = []
            // List pagination is not a deletion proof. Re-read known missing IDs directly.
            for link in links {
                let key = link.kind + ":" + link.localID.uuidString
                if remotes[key] == nil, let collection = CollectionWire.collections[link.kind],
                   let document = try await readSyncDocument(collection:collection,id:link.remoteID,uid:auth.uid,token:auth.idToken) {
                    let remote = try CollectionWire.decode(document,kind:link.kind,scope:scope)
                    guard CollectionWire.key(remote.row) == key else { throw WireClientError("Cloud sync ID mismatch") }; remotes[key] = remote
                }
            }
            for key in Set(locals.keys).union(remotes.keys).union(linkMap.keys).sorted() {
                let local = locals[key], remote = remotes[key], link = linkMap[key]
                guard let kind = local?.kind ?? remote?.row.kind ?? link?.kind, let id = local?.id ?? remote?.row.id ?? link?.localID else { continue }
                let digest = try local.map { try CollectionWire.hash($0) }
                var action = SyncPolicy.decide(local:digest,remote:remote?.digest,baseline:link?.localHash,remoteVersion:remote?.version,baselineVersion:link?.remoteVersion)
                if kind == "vehicle" && remote == nil && link != nil { action = .conflict }
                if action == .conflict {
                    if resolution == "Local" { action = local == nil ? .deleteRemote : .upload }
                    else if resolution == "Cloud" { action = remote == nil ? .deleteLocal : .download }
                    else { syncConflicts.append("\(kind): \(local?.title.isEmpty == false ? local!.title : id.uuidString)") }
                }
                work.append(Work(kind:kind,localID:id,remoteID:remote?.id ?? link?.remoteID ?? (kind == "vehicle" ? "bike" : id.uuidString.lowercased()),local:local,remote:remote,link:link,action:action,version:remote?.version))
            }
            guard syncConflicts.isEmpty else { syncStatus = "\(syncConflicts.count) conflict(s). Choose which version to keep before continuing."; return }
            guard syncEnabled, login?.uid == auth.uid, UUID(uuidString:syncVehicle) == scope else { return }
            var pending:[(index:Int,write:[String:Any])] = [], bytes = 0
            func flush() async throws {
                guard !pending.isEmpty else { return }
                let data = try await call(URL(string:"https://firestore.googleapis.com/v1/\(documentsPath):commit")!,method:"POST",body:["writes":pending.map { $0.write }],bearer:auth.idToken)
                guard let response = try JSONSerialization.jsonObject(with:data) as? [String:Any], let results = response["writeResults"] as? [[String:Any]], results.count == pending.count else { throw WireClientError("Unexpected cloud commit result") }
                for (position,item) in pending.enumerated() { work[item.index].version = results[position]["updateTime"] as? String }
                pending = []; bytes = 0
            }
            for index in work.indices {
                let item = work[index]
                guard item.action == .upload || item.action == .deleteRemote else { continue }
                let collection = CollectionWire.collections[item.kind]!
                let name = "\(documentsPath)/users/\(auth.uid)/\(collection)/\(item.remoteID)"
                let precondition:[String:Any] = item.remote.map { ["updateTime":$0.version] } ?? ["exists":false]
                let write:[String:Any]
                if item.action == .upload, let row = item.local {
                    let fields = try CollectionWire.fields(row), encoded = try CollectionWire.encodeFields(fields)
                    let size = try JSONSerialization.data(withJSONObject:encoded).count
                    guard size < 900000 else { throw WireClientError("A ride exceeds Firestore's record limit. Keep it in local/cloud backup instead.") }
                    write = ["update":["name":name,"fields":encoded],"updateMask":["fieldPaths":Array(fields.keys).sorted()],"currentDocument":precondition]
                } else {
                    guard item.remote != nil else { continue }
                    write = ["delete":name,"currentDocument":precondition]
                }
                let size = try JSONSerialization.data(withJSONObject:write).count
                if bytes + size > 7000000 || pending.count >= 300 { try await flush() }
                pending.append((index,write)); bytes += size
            }
            try await flush()
            guard syncEnabled, login?.uid == auth.uid else { return }
            // Re-read after network awaits; never overwrite an edit made during this sync pass.
            let current = Dictionary(uniqueKeysWithValues:try CollectionWire.localRows(context,scope:scope).map { (CollectionWire.key($0),$0) })
            var deferred = 0
            for item in work {
                let key = item.kind + ":" + item.localID.uuidString
                if item.action == .download || item.action == .deleteLocal {
                    let before = try item.local.map { try CollectionWire.hash($0) }, now = try current[key].map { try CollectionWire.hash($0) }
                    if before != now { deferred += 1; continue }
                    try CollectionWire.apply(item.action == .download ? item.remote?.row : nil,kind:item.kind,id:item.localID,scope:scope,context:transaction)
                }
                if item.action == .forget || item.action == .deleteRemote || item.action == .deleteLocal { if let link = item.link { transaction.delete(link) }; continue }
                let acknowledged = item.action == .download ? item.remote?.row : item.local
                guard let row = acknowledged else { continue }
                let digest = try CollectionWire.hash(row)
                if let link = item.link { link.localHash = digest; link.remoteVersion = item.version; link.remoteID = item.remoteID }
                else { transaction.insert(CloudRecordLink(owner:auth.uid,scope:scope,kind:item.kind,localID:item.localID,remoteID:item.remoteID,localHash:digest,remoteVersion:item.version)) }
            }
            try transaction.save()
            lastSync = Date()
            lastLocalDigest = try CollectionWire.localRows(context,scope:scope).map { CollectionWire.key($0) + (try CollectionWire.hash($0)) }.sorted().joined()
            syncStatus = deferred > 0 ? "Synced; \(deferred) in-flight local edit(s) preserved for the next pass" : "Synced with Android at \(Date().formatted(date:.omitted,time:.shortened))"
        } catch { syncStatus = "Sync paused: \(error.localizedDescription). Local data is preserved." }
    }
    private func listCollection(_ collection:String,uid:String,token:String) async throws -> [[String:Any]] {
        var result:[[String:Any]] = [], next:String?
        repeat {
            var components = URLComponents(url:cloudURL("users/\(uid)/\(collection)"),resolvingAgainstBaseURL:false)!
            components.queryItems = [URLQueryItem(name:"pageSize",value:"200")]
            if let next { components.queryItems!.append(URLQueryItem(name:"pageToken",value:next)) }
            let data = try await call(components.url!,method:"GET",bearer:token)
            guard let response = try JSONSerialization.jsonObject(with:data) as? [String:Any] else { throw WireClientError("Invalid cloud collection response") }
            result += response["documents"] as? [[String:Any]] ?? []
            guard result.count <= 50000 else { throw WireClientError("Cloud collection is too large for this sync pass") }
            next = response["nextPageToken"] as? String
        } while next?.isEmpty == false
        return result
    }
    private func readSyncDocument(collection:String,id:String,uid:String,token:String) async throws -> [String:Any]? {
        guard !id.isEmpty, !id.contains("/"), id != ".", id != ".." else { throw WireClientError("Invalid cloud document ID") }
        let url = cloudURL("users/\(uid)/\(collection)").appendingPathComponent(id)
        do { return try JSONSerialization.jsonObject(with:await call(url,method:"GET",bearer:token)) as? [String:Any] }
        catch let error as WireClientError { if error.message.hasPrefix("HTTP 404") { return nil }; throw error }
    }
}

struct CloudSyncControls: View {
    @EnvironmentObject private var cloud:CloudBackupClient
    @Environment(\.modelContext) private var context
    @Query(sort:\Vehicle.name) private var vehicles:[Vehicle]
    @State private var confirmEnable = false
    @State private var resolveLocal = false
    @State private var resolveCloud = false
    var body: some View {
        Picker("Android-linked motorcycle",selection:$cloud.syncVehicle) {
            Text("Choose motorcycle").tag("")
            ForEach(vehicles) { Text($0.name).tag($0.id.uuidString) }
        }.disabled(cloud.syncEnabled || cloud.busy)
        Toggle("Automatic collection sync",isOn:Binding(get:{ cloud.syncEnabled },set:{ value in if value { confirmEnable = true } else { cloud.syncEnabled = false; cloud.syncConflicts = [] } })).disabled(cloud.busy || cloud.syncVehicle.isEmpty)
            .confirmationDialog("Sync this motorcycle, saved destinations and its ride history with your Android account?",isPresented:$confirmEnable,titleVisibility:.visible) { Button("Enable sync") { cloud.syncEnabled = true } }
        Text("Synchronizes the Android fuel, services, maintenance, saved, rides and odometer collections. Foreground sync uploads changes and polls cloud data every two minutes. Firestore reads/writes may incur charges. Attachments and iOS-only fields stay in backups.").font(.caption).foregroundStyle(.secondary)
        Text(cloud.syncStatus).font(.footnote)
        Button("Sync now") { Task { await cloud.synchronize(context:context) } }.disabled(!cloud.syncEnabled || cloud.busy)
        if !cloud.syncConflicts.isEmpty {
            ForEach(cloud.syncConflicts,id:\.self) { Text($0).font(.caption).foregroundStyle(.orange) }
            Button("Resolve conflicts using this iPhone") { resolveLocal = true }
                .confirmationDialog("Keep this iPhone's versions of all conflicting records? Cloud edits/deletions in those conflicts will be replaced.",isPresented:$resolveLocal,titleVisibility:.visible) { Button("Keep iPhone versions",role:.destructive) { Task { await cloud.synchronize(context:context,resolution:"Local") } } }
            Button("Resolve conflicts using cloud") { resolveCloud = true }
                .confirmationDialog("Keep cloud versions of all conflicting records? Local edits/deletions in those conflicts will be replaced.",isPresented:$resolveCloud,titleVisibility:.visible) { Button("Keep cloud versions",role:.destructive) { Task { await cloud.synchronize(context:context,resolution:"Cloud") } } }
        }
    }
}

struct GoogleGrant: Codable {
    var accessToken:String; var refreshToken:String; var idToken:String?; var expiry:Date; var scope:String
}
@MainActor final class GoogleOAuthClient: NSObject, ObservableObject, ASWebAuthenticationPresentationContextProviding {
    @Published var connected = false
    private var browser:ASWebAuthenticationSession?
    private var grant:GoogleGrant?
    var clientID:String { Bundle.main.object(forInfoDictionaryKey:"NaviSarthiGoogleClientID") as? String ?? "" }
    var configured:Bool { clientID.hasSuffix(".apps.googleusercontent.com") }
    private var key:String { "googleOAuth:" + clientID }
    override init() {
        super.init()
        if let data = KeychainStore.read(key), let saved = try? JSONDecoder().decode(GoogleGrant.self,from:data) { grant = saved; connected = true }
    }
    func presentationAnchor(for session:ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where:\.isKeyWindow) ?? ASPresentationAnchor()
    }
    private func random() throws -> String {
        var bytes = [UInt8](repeating:0,count:32)
        guard SecRandomCopyBytes(kSecRandomDefault,bytes.count,&bytes) == errSecSuccess else { throw WireClientError("Secure random generation failed") }
        return base64(Data(bytes))
    }
    private func base64(_ data:Data) -> String { data.base64EncodedString().replacingOccurrences(of:"+",with:"-").replacingOccurrences(of:"/",with:"_").replacingOccurrences(of:"=",with:"") }
    func authorize(drive:Bool) async throws -> GoogleGrant {
        guard configured else { throw WireClientError("Configure the iOS Google OAuth client on the receiving Mac; see docs/GOOGLE.md") }
        guard browser == nil else { throw WireClientError("Google authorization is already open") }
        let scheme = clientID.split(separator:".").reversed().joined(separator:".")
        let redirect = scheme + ":/oauthredirect", state = try random(), verifier = try random()
        let challenge = base64(Data(SHA256.hash(data:Data(verifier.utf8))))
        var url = URLComponents(string:"https://accounts.google.com/o/oauth2/v2/auth")!
        let scopes = "openid email profile" + (drive ? " https://www.googleapis.com/auth/drive.file" : "")
        url.queryItems = ["client_id":clientID,"redirect_uri":redirect,"response_type":"code","scope":scopes,"state":state,"code_challenge":challenge,"code_challenge_method":"S256","access_type":"offline","prompt":"consent"].map { URLQueryItem(name:$0.key,value:$0.value) }
        defer { browser = nil }
        let callback = try await withCheckedThrowingContinuation { (continuation:CheckedContinuation<URL,Error>) in
            let session = ASWebAuthenticationSession(url:url.url!,callbackURLScheme:scheme) { url,error in
                if let error { continuation.resume(throwing:error) }
                else if let url { continuation.resume(returning:url) }
                else { continuation.resume(throwing:WireClientError("Google authorization returned no callback")) }
            }
            session.presentationContextProvider = self; browser = session
            if !session.start() { browser = nil; continuation.resume(throwing:WireClientError("Could not open Google authorization")) }
        }
        browser = nil
        let values = URLComponents(url:callback,resolvingAgainstBaseURL:false)?.queryItems ?? []
        guard callback.scheme == scheme, values.first(where:{ $0.name == "state" })?.value == state, let code = values.first(where:{ $0.name == "code" })?.value else { throw WireClientError("Google authorization state mismatch or access denied") }
        let response = try await tokenRequest(["client_id":clientID,"redirect_uri":redirect,"grant_type":"authorization_code","code":code,"code_verifier":verifier])
        guard let refresh = response.refresh_token, !refresh.isEmpty else { throw WireClientError("Google did not grant offline access. Reconnect and approve access.") }
        let value = GoogleGrant(accessToken:response.access_token,refreshToken:refresh,idToken:response.id_token,expiry:Date().addingTimeInterval(response.expires_in),scope:response.scope ?? scopes)
        try persist(value); return value
    }
    func access(drive:Bool) async throws -> String {
        guard let grant, !drive || grant.scope.contains("https://www.googleapis.com/auth/drive.file") else { throw WireClientError("Reconnect Google Drive to grant backup access") }
        if grant.expiry.timeIntervalSinceNow > 60 { return grant.accessToken }
        let result = try await tokenRequest(["client_id":clientID,"grant_type":"refresh_token","refresh_token":grant.refreshToken])
        let updated = GoogleGrant(accessToken:result.access_token,refreshToken:result.refresh_token ?? grant.refreshToken,idToken:result.id_token,expiry:Date().addingTimeInterval(result.expires_in),scope:result.scope ?? grant.scope)
        try persist(updated); return updated.accessToken
    }
    func forget() { browser?.cancel(); browser = nil; grant = nil; connected = false; KeychainStore.remove(key) }
    private func persist(_ value:GoogleGrant) throws { try KeychainStore.write(JSONEncoder().encode(value),key:key); grant = value; connected = true }
    private struct TokenResponse:Decodable { var access_token:String; var refresh_token:String?; var id_token:String?; var expires_in:Double; var scope:String? }
    private func tokenRequest(_ fields:[String:String]) async throws -> TokenResponse {
        var request = URLRequest(url:URL(string:"https://oauth2.googleapis.com/token")!); request.httpMethod = "POST"
        let encoded = fields.map { ($0.key.addingPercentEncoding(withAllowedCharacters:.alphanumerics) ?? "") + "=" + ($0.value.addingPercentEncoding(withAllowedCharacters:.alphanumerics) ?? "") }.joined(separator:"&")
        request.httpBody = Data(encoded.utf8); request.setValue("application/x-www-form-urlencoded",forHTTPHeaderField:"Content-Type")
        let session = URLSession(configuration:.ephemeral,delegate:NoRedirects(),delegateQueue:nil); defer { session.finishTasksAndInvalidate() }
        let (data,response) = try await session.data(for:request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw WireClientError("Google token exchange failed. Reconnect your account.") }
        return try JSONDecoder().decode(TokenResponse.self,from:data)
    }
}
extension CloudBackupClient {
    func signInGoogle() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        do {
            try validateConfiguration()
            let google = GoogleOAuthClient(), grant = try await google.authorize(drive:false)
            guard let idToken = grant.idToken else { throw WireClientError("Google returned no identity token") }
            let body = "id_token=" + (idToken.addingPercentEncoding(withAllowedCharacters:.alphanumerics) ?? "") + "&providerId=google.com"
            let data = try await call(authURL(host:"identitytoolkit.googleapis.com",path:"/v1/accounts:signInWithIdp"),method:"POST",body:["postBody":body,"requestUri":"http://localhost","returnSecureToken":true,"returnIdpCredential":true])
            struct Response:Decodable { var localId:String; var idToken:String; var refreshToken:String; var expiresIn:String }
            let result = try JSONDecoder().decode(Response.self,from:data)
            guard !result.localId.isEmpty, !result.localId.contains("/") else { throw WireClientError("Unsupported account ID") }
            try save(FirebaseLogin(uid:result.localId,idToken:result.idToken,refreshToken:result.refreshToken,expires:Date().addingTimeInterval(Double(result.expiresIn) ?? 3600)))
            signedIn = true; syncEnabled = false; syncConflicts = []; lastLocalDigest = ""; status = "Signed in with Google"
            UserDefaults.standard.set(project,forKey:"firebaseProject"); UserDefaults.standard.set(apiKey,forKey:"firebasePublicKey")
        } catch { status = error.localizedDescription }
    }
}

struct DriveFile:Decodable,Identifiable { var id:String; var name:String; var size:String? }
@MainActor final class DriveBackupClient:ObservableObject {
    @Published var busy = false
    @Published var status = ""
    @Published var files:[DriveFile] = []
    let google = GoogleOAuthClient()
    var linked:Bool { UserDefaults.standard.string(forKey:"drivePermissionID") != nil }
    private func url(_ path:String,query:[URLQueryItem] = []) -> URL {
        var components = URLComponents(string:"https://www.googleapis.com/drive/v3/" + path)!
        components.queryItems = query; return components.url!
    }
    func connect() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        do {
            _ = try await google.authorize(drive:true)
            let token = try await google.access(drive:true), owner = try await permissionID(token)
            UserDefaults.standard.set(owner,forKey:"drivePermissionID"); status = "Google Drive connected"
        } catch { status = error.localizedDescription }
    }
    func disconnect() { UserDefaults.standard.removeObject(forKey:"drivePermissionID"); UserDefaults.standard.set(false,forKey:"driveDaily"); files = []; status = "Drive disconnected locally" }
    private func permissionID(_ token:String) async throws -> String {
        let data = try await request(url("about",query:[URLQueryItem(name:"fields",value:"user(permissionId)")]),token:token)
        guard let object = try JSONSerialization.jsonObject(with:data) as? [String:Any], let user = object["user"] as? [String:Any], let id = user["permissionId"] as? String else { throw WireClientError("Drive account identity unavailable") }; return id
    }
    private func checkedToken() async throws -> String {
        let token = try await google.access(drive:true)
        guard let expected = UserDefaults.standard.string(forKey:"drivePermissionID"), try await permissionID(token) == expected else { throw WireClientError("Reconnect the Google account originally used for Drive backup") }; return token
    }
    private func list(_ query:String,token:String) async throws -> [DriveFile] {
        struct Page:Decodable { var files:[DriveFile]; var nextPageToken:String? }
        var all:[DriveFile] = [], next:String?
        repeat {
            var items = [URLQueryItem(name:"q",value:query),URLQueryItem(name:"fields",value:"nextPageToken,files(id,name,size)"),URLQueryItem(name:"orderBy",value:"createdTime desc"),URLQueryItem(name:"pageSize",value:"100")]
            if let next { items.append(URLQueryItem(name:"pageToken",value:next)) }
            let page = try JSONDecoder().decode(Page.self,from:await request(url("files",query:items),token:token)); all += page.files; next = page.nextPageToken
            guard all.count <= 10000 else { throw WireClientError("Too many Drive backups; manage older snapshots in Drive") }
        } while next?.isEmpty == false
        return all
    }
    private func folder(_ token:String,create:Bool) async throws -> String? {
        let folders = try await list("'root' in parents and name='NaviSarthi' and mimeType='application/vnd.google-apps.folder' and trashed=false",token:token)
        guard folders.count <= 1 else { throw WireClientError("Multiple NaviSarthi folders found. Rename duplicates in Drive first.") }
        if let first = folders.first { return first.id }
        guard create else { return nil }
        let body = try JSONSerialization.data(withJSONObject:["name":"NaviSarthi","mimeType":"application/vnd.google-apps.folder"])
        let data = try await request(url("files"),token:token,method:"POST",body:body,contentType:"application/json")
        guard let value = try JSONSerialization.jsonObject(with:data) as? [String:Any], let id = value["id"] as? String else { throw WireClientError("Could not create Drive backup folder") }; return id
    }
    func refresh() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        do {
            let token = try await checkedToken()
            guard let folder = try await folder(token,create:false) else { files = []; status = "No backup folder found"; return }
            guard validID(folder) else { throw WireClientError("Invalid Drive folder ID") }
            files = try await list("'\(folder)' in parents and mimeType='application/json' and trashed=false",token:token).filter { ($0.name.hasPrefix("backup-") || $0.name.hasPrefix("ios-backup-")) && $0.name.hasSuffix(".json") }
        } catch { status = error.localizedDescription }
    }
    func backup(_ snapshot:LocalBackup) async throws {
        let token = try await checkedToken()
        guard let folder = try await folder(token,create:true) else { throw WireClientError("No Drive backup folder") }
        let bytes = try JSONEncoder().encode(snapshot)
        guard bytes.count <= 64 * 1024 * 1024 else { throw WireClientError("Drive backups are limited to 64 MB; use local export") }
        let boundary = "navisarthi-" + UUID().uuidString
        let name = "ios-backup-" + ISO8601DateFormatter().string(from:Date()).replacingOccurrences(of:":",with:"-") + ".json"
        let metadata = try JSONSerialization.data(withJSONObject:["name":name,"parents":[folder],"mimeType":"application/json"])
        let temporary = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".multipart")
        defer { try? FileManager.default.removeItem(at:temporary) }
        FileManager.default.createFile(atPath:temporary.path,contents:nil,attributes:[.protectionKey:FileProtectionType.completeUntilFirstUserAuthentication])
        let handle = try FileHandle(forWritingTo:temporary)
        try handle.write(contentsOf:Data("--\(boundary)\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".utf8)); try handle.write(contentsOf:metadata)
        try handle.write(contentsOf:Data("\r\n--\(boundary)\r\nContent-Type: application/json\r\n\r\n".utf8)); try handle.write(contentsOf:bytes)
        try handle.write(contentsOf:Data("\r\n--\(boundary)--\r\n".utf8)); try handle.close()
        var request = URLRequest(url:URL(string:"https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")!); request.httpMethod = "POST"
        request.setValue("Bearer \(token)",forHTTPHeaderField:"Authorization"); request.setValue("multipart/related; boundary=\(boundary)",forHTTPHeaderField:"Content-Type")
        let configuration = URLSessionConfiguration.ephemeral; configuration.timeoutIntervalForResource = 180
        let session = URLSession(configuration:configuration,delegate:NoRedirects(),delegateQueue:nil); defer { session.finishTasksAndInvalidate() }
        let (_,response) = try await session.upload(for:request,fromFile:temporary)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw WireClientError("Drive upload failed") }
        UserDefaults.standard.set(Date().timeIntervalSince1970,forKey:"driveLastBackup"); UserDefaults.standard.removeObject(forKey:"driveBackupPaused")
        status = "Saved to My Drive / NaviSarthi"
    }
    func download(_ file:DriveFile) async throws -> LocalBackup {
        guard !busy else { throw WireClientError("A Drive operation is already running") }; busy = true; defer { busy = false }
        guard validID(file.id), let size = UInt64(file.size ?? ""), size <= 64 * 1024 * 1024 else { throw WireClientError("Invalid or oversized Drive backup") }
        let token = try await checkedToken()
        return try LocalBackup.read(await request(url("files/" + file.id,query:[URLQueryItem(name:"alt",value:"media")]),token:token))
    }
    private func validID(_ id:String) -> Bool { !id.isEmpty && id.utf8.allSatisfy({ (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) || $0 == 45 || $0 == 95 }) }
    private func request(_ url:URL,token:String,method:String = "GET",body:Data? = nil,contentType:String? = nil) async throws -> Data {
        var request = URLRequest(url:url); request.httpMethod = method; request.httpBody = body
        request.setValue("Bearer \(token)",forHTTPHeaderField:"Authorization"); if let contentType { request.setValue(contentType,forHTTPHeaderField:"Content-Type") }
        let session = URLSession(configuration:.ephemeral,delegate:NoRedirects(),delegateQueue:nil); defer { session.finishTasksAndInvalidate() }
        let (file,response) = try await session.download(for:request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode), (try file.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0) <= 64 * 1024 * 1024 else { throw WireClientError("Drive request failed or response exceeded 64 MB") }
        return try BoundedFile.read(file,limit:64 * 1024 * 1024)
    }
}
struct DriveBackupView: View {
    @StateObject private var drive = DriveBackupClient()
    @Environment(\.modelContext) private var context
    @State private var pending:LocalBackup?
    @State private var restore = false
    @State private var upload = false
    @AppStorage("driveDaily") private var daily = false
    var body: some View {
        Form {
            if drive.linked {
                Button("Reconnect Google Drive") { Task { await drive.connect() } }.disabled(drive.busy)
                Button("Back up now") { upload = true }.disabled(drive.busy)
                Button("List available backups") { Task { await drive.refresh() } }.disabled(drive.busy)
                Toggle("Opportunistic daily backup",isOn:$daily).onChange(of:daily) { _,enabled in DriveBackgroundBackup.schedule(enabled:enabled) }
                Text("iOS chooses background execution times; a daily run is not guaranteed. Reconnect if Google consent expires.").font(.caption).foregroundStyle(.secondary)
                ForEach(drive.files) { file in Button(file.name) { Task { do { pending = try await drive.download(file); restore = true } catch { drive.status = error.localizedDescription } } } }
                Button("Disconnect Drive",role:.destructive) { drive.disconnect(); DriveBackgroundBackup.schedule(enabled:false) }.disabled(drive.busy)
            } else { Button("Connect Google Drive") { Task { await drive.connect() } }.disabled(drive.busy) }
            if !drive.google.configured { Text("An iOS OAuth client ID and callback scheme must be configured on the Mac. See docs/GOOGLE.md.").font(.caption) }
            if drive.busy { ProgressView() }
            Text(drive.status).font(.footnote)
            Text("Drive access is limited to files this application can access. Android snapshots can be imported. iOS snapshots include iOS-only data and are named ios-backup- so Android will not mistake them for its own restore format.").font(.caption).foregroundStyle(.secondary)
        }.navigationTitle("Google Drive backup")
            .confirmationDialog("Upload your local data and attachments to Google Drive?",isPresented:$upload,titleVisibility:.visible) { Button("Back up") {
                do { try context.save(); let snapshot = try LocalBackup.capture(context); Task { drive.busy = true; defer { drive.busy = false }; do { try await drive.backup(snapshot) } catch { drive.status = error.localizedDescription } } }
                catch { drive.status = error.localizedDescription }
            } }
            .confirmationDialog("Restore missing records from this Drive snapshot?",isPresented:$restore,titleVisibility:.visible) { Button("Import missing records") {
                do { if let pending { drive.status = "Imported \(try pending.restore(into:context.container)) records" } } catch { drive.status = error.localizedDescription }; pending = nil
            } }
    }
}

enum DriveBackgroundBackup {
    static let identifier = (Bundle.main.bundleIdentifier ?? "com.navisarthi.ios") + ".drive-backup"
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier:identifier,using:nil) { task in
            let work = Task { @MainActor in
                var success = false
                do {
                    if UserDefaults.standard.bool(forKey:"driveDaily") {
                        let container = try AppStorageSchema.container(), context = ModelContext(container)
                        let snapshot = try LocalBackup.capture(context)
                        try Task.checkCancellation()
                        try await DriveBackupClient().backup(snapshot); success = true
                    } else { success = true }
                } catch { UserDefaults.standard.set("Reconnect Google Drive or retry from Settings",forKey:"driveBackupPaused") }
                task.expirationHandler = nil; task.setTaskCompleted(success:success)
                schedule(enabled:UserDefaults.standard.bool(forKey:"driveDaily"))
            }
            task.expirationHandler = { work.cancel() }
        }
    }
    static func schedule(enabled:Bool) {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier:identifier)
        guard enabled else { return }
        let request = BGProcessingTaskRequest(identifier:identifier); request.earliestBeginDate = Date().addingTimeInterval(86400); request.requiresNetworkConnectivity = true
        do { try BGTaskScheduler.shared.submit(request) }
        catch { UserDefaults.standard.set("Background backup could not be scheduled; open Settings to retry",forKey:"driveBackupPaused") }
    }
}

struct CloudBackupView: View {
    @EnvironmentObject private var cloud:CloudBackupClient
    @Environment(\.modelContext) private var context
    @State private var email = ""
    @State private var password = ""
    @State private var pending:LocalBackup?
    @State private var confirm = false
    @State private var uploading = false
    var body: some View {
        Form {
            Section("Your Firebase project") {
                TextField("Project ID",text:$cloud.project).textInputAutocapitalization(.never).autocorrectionDisabled().disabled(cloud.signedIn)
                TextField("Public Web API key",text:$cloud.apiKey).textInputAutocapitalization(.never).autocorrectionDisabled().disabled(cloud.signedIn)
                Text("Use your existing Firebase project. This uses Firebase end-user authentication and your owner-only Firestore rules; no service-account key is needed.").font(.caption)
            }
            if cloud.signedIn {
                Section("Android and iOS collection sync") { CloudSyncControls() }
                Section("Cloud backup") {
                    Button("Upload local backup") { uploading = true }.disabled(cloud.busy)
                    Button("Download and preview cloud backup") {
                        Task {
                            do { pending = try await cloud.download(); confirm = true }
                            catch { cloud.status = error.localizedDescription }
                        }
                    }.disabled(cloud.busy)
                    Text("Snapshots are separate from automatic collection sync. Local files support larger backups. Cloud operations may incur Firebase read/write charges.").font(.caption).foregroundStyle(.secondary)
                    Button("Sign out",role:.destructive) { cloud.signOut() }.disabled(cloud.busy)
                }
            } else {
                Section("Account") {
                    TextField("Email",text:$email).keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                    SecureField("Password",text:$password)
                    Button("Sign in") { Task { await cloud.signIn(email:email,password:password); password = "" } }.disabled(cloud.busy)
                    Button("Sign in with Google") { Task { await cloud.signInGoogle() } }.disabled(cloud.busy)
                    Button("Create account") { Task { await cloud.signIn(email:email,password:password,create:true); password = "" } }.disabled(cloud.busy)
                    Button("Reset password") { Task { await cloud.resetPassword(email:email) } }.disabled(email.isEmpty)
                }
            }
            if cloud.busy { ProgressView() }
            if !cloud.status.isEmpty { Text(cloud.status).font(.footnote).textSelection(.enabled) }
        }.navigationTitle("Cloud backup")
            .confirmationDialog("Upload your garage, attachments, routes and rides to your Firebase account?",isPresented:$uploading,titleVisibility:.visible) {
                Button("Upload backup") {
                    do { try context.save(); let backup = try LocalBackup.capture(context); Task { await cloud.upload(backup) } }
                    catch { cloud.status = error.localizedDescription }
                }
            }
            .confirmationDialog("Import \(pending?.rows.count ?? 0) cloud backup records?",isPresented:$confirm,titleVisibility:.visible) {
                Button("Import missing records") {
                    do { if let pending { cloud.status = "Imported \(try pending.restore(into:context.container)) new records" } }
                    catch { cloud.status = error.localizedDescription }; pending = nil
                }
            }
    }
}
