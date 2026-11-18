import Foundation
import Combine
import Security
import CoreLocation
import AVFoundation
#if canImport(LiveKit)
import LiveKit
#endif

enum KeychainStore {
    private static let service = "com.navisarthi.ios.comms"
    static func read(_ key: String) -> Data? {
        let query: [CFString:Any] = [kSecClass:kSecClassGenericPassword,kSecAttrService:service,kSecAttrAccount:key,kSecReturnData:true,kSecMatchLimit:kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary,&result) == errSecSuccess else { return nil }
        return result as? Data
    }
    static func write(_ data: Data, key: String) throws {
        let query: [CFString:Any] = [kSecClass:kSecClassGenericPassword,kSecAttrService:service,kSecAttrAccount:key]
        let updates: [CFString:Any] = [kSecValueData:data,kSecAttrAccessible:kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        var status = SecItemUpdate(query as CFDictionary,updates as CFDictionary)
        if status == errSecItemNotFound { status = SecItemAdd(query.merging(updates) { _,new in new } as CFDictionary,nil) }
        guard status == errSecSuccess else { throw NSError(domain:NSOSStatusErrorDomain,code:Int(status)) }
    }
    static func remove(_ key: String) {
        SecItemDelete([kSecClass:kSecClassGenericPassword,kSecAttrService:service,kSecAttrAccount:key] as CFDictionary)
    }
}

struct Admission: Codable {
    var groupId: String; var memberId: String; var credential: String
    var expiresAt: Double; var inviteToken: String?
}
struct PendingAdmission: Codable { var id: String; var path: String; var body: Data }
struct RideMember: Decodable, Identifiable { var id: String; var nickname: String }
struct RideGroup: Decodable {
    var id: String; var name: String; var ownerId: String; var expiresAt: Double; var createdAt: Double; var members: [RideMember]; var audioAvailable: Bool; var revision:Int
}
struct RidePosition: Decodable { var lat: Double; var lng: Double; var accuracyM: Double; var receivedAt: Double }
struct GroupSnapshot: Decodable { var group: RideGroup; var memberId: String; var positions: [String:RidePosition] }
struct ChatMessage: Codable, Identifiable { var id: String; var memberId: String; var text: String; var seq: Int; var sentAt: Double }
struct MessagePage: Decodable { var messages: [ChatMessage]; var nextAfter: Int }
struct InvitePreview: Decodable { var name: String; var expiresAt: Double }
struct APIError: Decodable { var error: String }
struct PendingMessage: Codable { var id: String; var text: String }

final class NoRedirects: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        // Admission headers and membership credentials must never follow a redirect.
        completionHandler(nil)
    }
}

@MainActor final class CommsClient: ObservableObject {
    @Published var admission: Admission?
    @Published var snapshot: GroupSnapshot?
    @Published var messages: [ChatMessage] = []
    @Published var error: String?
    @Published var busy = false
    @Published var pendingAdmission = false
    @Published var invitation: String?
    @Published var realtime = false
    @Published var sharing = false
    @Published var stoppingSharing = false
    @Published var audioStatus = "Off"
    @Published var audioMuted = false
    @Published var audioConnected = false
    private let origin = URL(string:"https://navisarthi.onrender.com/")!
    private let session: URLSession
    private var pending: PendingAdmission?
    private var cursor = 0
    private var messageAttempt: PendingMessage?
    private var socket: URLSessionWebSocketTask?
    private var receiver: Task<Void,Never>?
    private var nextSocketAttempt = Date.distantPast
    private var socketFailures = 0
    private var sharingID: String?
    private var shareGeneration = UUID()
    private var publishing = false
    private var lastPublish = Date.distantPast
    private var serverOffset = 0.0
    private var audioGeneration = UUID()
#if canImport(LiveKit)
    private var room: Room?
#endif
    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20; configuration.urlCache = nil
        session = URLSession(configuration:configuration,delegate:NoRedirects(),delegateQueue:nil)
        if let data = KeychainStore.read("membership") { admission = try? JSONDecoder().decode(Admission.self,from:data) }
        if let data = KeychainStore.read("admission") { pending = try? JSONDecoder().decode(PendingAdmission.self,from:data) }
        pendingAdmission = pending != nil; invitation = admission?.inviteToken
        if let data = KeychainStore.read("outbox") { messageAttempt = try? JSONDecoder().decode(PendingMessage.self,from:data) }
        stoppingSharing = UserDefaults.standard.bool(forKey:"commsStopPending")
    }
    private func installationSecret() throws -> String {
        if let data = KeychainStore.read("installation"), let value = String(data:data,encoding:.utf8) { return value }
        var bytes = [UInt8](repeating:0,count:32)
        guard SecRandomCopyBytes(kSecRandomDefault,bytes.count,&bytes) == errSecSuccess else { throw URLError(.cannotCreateFile) }
        let value = Data(bytes).base64EncodedString().replacingOccurrences(of:"+",with:"-").replacingOccurrences(of:"/",with:"_").replacingOccurrences(of:"=",with:"")
        try KeychainStore.write(Data(value.utf8),key:"installation"); return value
    }
    func resolve(_ input: String) async -> InvitePreview? {
        do {
            let token = try inviteToken(input)
            let data = try await request("v1/invitations/resolve",body:["inviteToken":token],authenticated:false)
            return try JSONDecoder().decode(InvitePreview.self,from:data)
        } catch { self.error = error.localizedDescription; return nil }
    }
    func create(nickname: String, name: String, expiry: Date) async {
        await admit(path:"v1/groups",body:["nickname":nickname.trimmingCharacters(in:.whitespacesAndNewlines),"name":name.trimmingCharacters(in:.whitespacesAndNewlines),"expiresAt":Int64(expiry.timeIntervalSince1970 * 1000)])
    }
    func join(nickname: String, invitation: String) async {
        do { await admit(path:"v1/groups/join",body:["nickname":nickname.trimmingCharacters(in:.whitespacesAndNewlines),"inviteToken":try inviteToken(invitation)]) }
        catch { self.error = error.localizedDescription }
    }
    private func admit(path: String,body: [String:Any]) async {
        guard !busy, admission == nil else { return }
        do {
            if pending == nil {
                let attempt = PendingAdmission(id:UUID().uuidString,path:path,body:try JSONSerialization.data(withJSONObject:body,options:.sortedKeys))
                try KeychainStore.write(JSONEncoder().encode(attempt),key:"admission"); pending = attempt; pendingAdmission = true
            }
            await retryAdmission()
        } catch { self.error = error.localizedDescription }
    }
    func retryAdmission() async {
        guard !busy, let pending else { return }; busy = true; defer { busy = false }
        do {
            let secret = try installationSecret()
            let data = try await request(pending.path,rawBody:pending.body,authenticated:false,headers:["Idempotency-Key":pending.id,"X-Installation-Secret":secret])
            let result = try JSONDecoder().decode(Admission.self,from:data)
            try KeychainStore.write(JSONEncoder().encode(result),key:"membership")
            admission = result; invitation = result.inviteToken
            KeychainStore.remove("admission"); self.pending = nil; pendingAdmission = false
            await refresh()
        } catch { self.error = error.localizedDescription }
    }
    func refresh() async {
        guard admission != nil else { return }
        do {
            let data = try await request("v1/session",method:"GET")
            applySnapshot(try JSONDecoder().decode(GroupSnapshot.self,from:data))
            if let snapshot, nowMs >= snapshot.group.expiresAt { clear(); return }
            let page = try JSONDecoder().decode(MessagePage.self,from:await request("v1/messages/list",body:["afterSeq":cursor,"limit":100]))
            let known = Set(messages.map(\.id)); messages += page.messages.filter { !known.contains($0.id) }; messages.sort { $0.seq < $1.seq }; cursor = max(cursor,page.nextAfter)
        } catch { self.error = error.localizedDescription }
    }
    func send(_ text: String) async -> Bool {
        let text = text.trimmingCharacters(in:.whitespacesAndNewlines)
        guard !text.isEmpty, text.count <= 2000, !busy else { return false }
        if let attempt = messageAttempt, attempt.text != text { error = "Retry the pending message before sending different text."; return false }
        busy = true; defer { busy = false }
        let attempt = messageAttempt ?? PendingMessage(id:UUID().uuidString,text:text); messageAttempt = attempt
        do {
            try KeychainStore.write(JSONEncoder().encode(attempt),key:"outbox")
            _ = try await request("v1/messages",body:["id":attempt.id,"text":attempt.text])
            messageAttempt = nil; KeychainStore.remove("outbox"); await refresh(); return true
        } catch { self.error = error.localizedDescription; return false }
    }
    func leave(endGroup: Bool) async {
        guard !busy else { return }; busy = true; defer { busy = false }
        do { _ = try await request(endGroup ? "v1/session/end" : "v1/session/leave"); clear() }
        catch { self.error = error.localizedDescription }
    }
    func transfer(to member: String) async {
        do { _ = try await request("v1/session/transfer",body:["memberId":member]); await refresh() }
        catch { self.error = error.localizedDescription }
    }
    private func clear() {
        closeRealtime(); leaveAudio(); shareGeneration = UUID(); sharingID = nil; sharing = false; stoppingSharing = false
        UserDefaults.standard.removeObject(forKey:"commsStopPending"); KeychainStore.remove("outbox")
        KeychainStore.remove("membership"); admission = nil; snapshot = nil; messages = []; cursor = 0; invitation = nil; messageAttempt = nil
    }
    private func inviteToken(_ input: String) throws -> String {
        var token = input.trimmingCharacters(in:.whitespacesAndNewlines)
        if token.hasPrefix("{") {
            guard let object = try JSONSerialization.jsonObject(with:Data(token.utf8)) as? [String:Any], object["v"] as? Int == 1, object["type"] as? String == "navisarthi-ride", let value = object["inviteToken"] as? String else { throw WireClientError("Invalid ride invitation") }
            token = value
        }
        guard token.utf8.count == 32, token.utf8.allSatisfy({ (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) }) else { throw WireClientError("Paste a valid ride invitation or its 32-character code") }
        return token
    }
    private func request(_ path: String, method: String = "POST", body: [String:Any]? = nil, rawBody: Data? = nil, authenticated: Bool = true, headers: [String:String] = [:]) async throws -> Data {
        let credential = admission?.credential
        var request = URLRequest(url:origin.appendingPathComponent(path)); request.httpMethod = method
        if let rawBody { request.httpBody = rawBody }
        else if let body { request.httpBody = try JSONSerialization.data(withJSONObject:body) }
        if request.httpBody != nil { request.setValue("application/json",forHTTPHeaderField:"Content-Type") }
        if authenticated {
            guard let admission else { throw WireClientError("Join a ride first") }
            request.setValue("Bearer \(admission.credential)",forHTTPHeaderField:"Authorization")
        }
        for (key,value) in headers { request.setValue(value,forHTTPHeaderField:key) }
        let (data,response) = try await session.data(for:request)
        if authenticated && admission?.credential != credential { throw CancellationError() }
        guard let response = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        guard (200..<300).contains(response.statusCode) else {
            let code = (try? JSONDecoder().decode(APIError.self,from:data).error) ?? "HTTP_\(response.statusCode)"
            if authenticated && ["SESSION_ENDED","INVALID_CREDENTIAL","CREDENTIAL_REVOKED"].contains(code) { clear() }
            throw WireClientError(code)
        }
        return data
    }

    var nowMs: Double { Date().timeIntervalSince1970 * 1000 + serverOffset }
    var pendingText: String? { messageAttempt?.text }
    /// Lives at the app root, so changing tabs never stops ride updates.
    func run() async {
        defer { closeRealtime() }
        var cycle = 0
        while !Task.isCancelled {
#if canImport(LiveKit)
            if let room {
                if room.connectionState == .connected { audioStatus = "Connected" }
                else if room.connectionState == .reconnecting { audioStatus = "Reconnecting" }
                else if room.connectionState == .disconnected && audioConnected { leaveAudio(); error = "Ride audio disconnected. Join again when ready." }
            }
#endif
            if let admission {
                if nowMs >= (snapshot?.group.expiresAt ?? admission.expiresAt) { clear() }
                else {
                    if stoppingSharing { await stopSharing() }
                    if !realtime || cycle % 6 == 0 { await refresh() }
                    if socket == nil && Date() >= nextSocketAttempt { await openRealtime() }
                    if let socket {
                        socket.sendPing { [weak self] failure in
                            if failure != nil { Task { @MainActor in if self?.socket === socket { self?.closeRealtime() } } }
                        }
                    }
                }
            }
            cycle += 1
            do { try await Task.sleep(for:.seconds(10)) } catch { return }
        }
    }
    private func openRealtime() async {
        guard socket == nil, admission != nil else { return }
        let credential = admission?.credential
        do {
            let response = try await request("v1/realtime-ticket")
            struct Ticket: Decodable { var ticket: String }
            let ticket = try JSONDecoder().decode(Ticket.self,from:response)
            guard admission?.credential == credential, socket == nil else { return }
            var components = URLComponents(url:origin.appendingPathComponent("v1/events"),resolvingAgainstBaseURL:false)!
            components.scheme = "wss"
            let connection = session.webSocketTask(with:components.url!); connection.maximumMessageSize = 1_048_576
            socket = connection; connection.resume()
            let auth = try JSONSerialization.data(withJSONObject:["type":"auth","ticket":ticket.ticket,"afterSeq":cursor])
            try await connection.send(.string(String(decoding:auth,as:UTF8.self)))
            receiver = Task { [weak self] in
                do {
                    while !Task.isCancelled {
                        let frame = try await connection.receive()
                        guard let self, self.socket === connection else { return }
                        let data: Data
                        switch frame { case .string(let text): data = Data(text.utf8); case .data(let bytes): data = bytes; @unknown default: continue }
                        try self.receive(data)
                        self.socketFailures = 0; self.realtime = true
                    }
                } catch {
                    guard let self, self.socket === connection else { return }
                    self.closeRealtime(); self.socketFailures += 1
                    self.nextSocketAttempt = Date().addingTimeInterval(min(60,pow(2,Double(min(6,self.socketFailures)))))
                }
            }
        } catch {
            closeRealtime(); nextSocketAttempt = Date().addingTimeInterval(15)
        }
    }
    private func receive(_ data: Data) throws {
        guard let object = try JSONSerialization.jsonObject(with:data) as? [String:Any], let type = object["type"] as? String else { return }
        if type == "snapshot" {
            let incoming = try JSONDecoder().decode(GroupSnapshot.self,from:data)
            guard incoming.memberId == admission?.memberId else { throw WireClientError("Unexpected session") }
            if let time = object["serverTime"] as? Double { serverOffset = time - Date().timeIntervalSince1970 * 1000 }
            applySnapshot(incoming)
        } else if type == "messages" {
            struct Rows: Decodable { var messages: [ChatMessage] }
            let rows = try JSONDecoder().decode(Rows.self,from:data).messages
            let known = Set(messages.map(\.id))
            messages += rows.filter { !known.contains($0.id) }; messages.sort { $0.seq < $1.seq }
            cursor = max(cursor, rows.map(\.seq).max() ?? 0)
        }
    }
    private func applySnapshot(_ incoming:GroupSnapshot) {
        guard incoming.memberId == admission?.memberId else { return }
        if let previous = snapshot, previous.group.id == incoming.group.id, previous.group.revision > incoming.group.revision { return }
        snapshot = incoming
    }
    private func closeRealtime() {
        receiver?.cancel(); receiver = nil; socket?.cancel(with:.goingAway,reason:nil); socket = nil; realtime = false
    }
    func rotateInvitation() async {
        do {
            struct Invitation: Decodable { var inviteToken: String }
            invitation = try JSONDecoder().decode(Invitation.self,from:await request("v1/session/invitation")).inviteToken
            if var member = admission { member.inviteToken = invitation; try KeychainStore.write(JSONEncoder().encode(member),key:"membership"); admission = member }
        } catch { self.error = error.localizedDescription }
    }
    func remove(member: String) async {
        do { _ = try await request("v1/session/remove",body:["memberId":member]); await refresh() }
        catch { self.error = error.localizedDescription }
    }
    func extend(until date: Date) async {
        do { _ = try await request("v1/session/extend",body:["expiresAt":Int64(date.timeIntervalSince1970 * 1000)]); await refresh() }
        catch { self.error = error.localizedDescription }
    }
    func startSharing() async {
        guard admission != nil, !sharing, !stoppingSharing else { return }
        let generation = UUID(); shareGeneration = generation
        do {
            struct Run: Decodable { var sharingId: String }
            let run = try JSONDecoder().decode(Run.self,from:await request("v1/location/start"))
            guard shareGeneration == generation else { return }
            sharingID = run.sharingId; sharing = true; lastPublish = .distantPast
        } catch { self.error = error.localizedDescription }
    }
    func stopSharing() async {
        shareGeneration = UUID(); sharingID = nil; sharing = false; stoppingSharing = true
        UserDefaults.standard.set(true,forKey:"commsStopPending")
        guard admission != nil else { stoppingSharing = false; UserDefaults.standard.removeObject(forKey:"commsStopPending"); return }
        do {
            _ = try await request("v1/location",method:"DELETE")
            stoppingSharing = false; UserDefaults.standard.removeObject(forKey:"commsStopPending")
        } catch { self.error = "Sharing stopped locally. Server removal will retry: \(error.localizedDescription)" }
    }
    func publish(_ fix: CLLocation) async {
        guard sharing, !publishing, let sharingID, fix.horizontalAccuracy >= 0, fix.horizontalAccuracy <= 100,
              abs(fix.timestamp.timeIntervalSinceNow) < 15,
              Date().timeIntervalSince(lastPublish) >= (fix.speed > 1 ? 5 : 30) else { return }
        let token = shareGeneration; publishing = true; defer { publishing = false }
        do {
            _ = try await request("v1/location",method:"PUT",body:["lat":fix.coordinate.latitude,"lng":fix.coordinate.longitude,"accuracyM":fix.horizontalAccuracy,"sharingId":sharingID])
            if token == shareGeneration { lastPublish = Date() }
        } catch { self.error = error.localizedDescription }
    }
    func joinAudio() async {
        guard !audioConnected, audioStatus == "Off", admission != nil else { return }
#if canImport(LiveKit)
        let generation = UUID(); audioGeneration = generation; audioStatus = "Connecting"
        do {
            let allowed = await AVAudioApplication.requestRecordPermission()
            guard audioGeneration == generation else { return }
            guard allowed else { throw WireClientError("Allow Microphone access in iPhone Settings") }
            struct AudioGrant: Decodable { var url: String; var token: String }
            let grant = try JSONDecoder().decode(AudioGrant.self,from:await request("v1/audio/token"))
            guard URL(string:grant.url)?.scheme == "wss", audioGeneration == generation else { throw WireClientError("Secure audio connection unavailable") }
            let connectedRoom = Room(); room = connectedRoom
            try await connectedRoom.connect(url:grant.url,token:grant.token)
            guard audioGeneration == generation else { await connectedRoom.disconnect(); return }
            try await connectedRoom.localParticipant.setMicrophone(enabled:true)
            guard audioGeneration == generation else { await connectedRoom.disconnect(); return }
            audioConnected = true; audioMuted = false; audioStatus = "Connected"
        } catch { if audioGeneration == generation { leaveAudio(); self.error = error.localizedDescription } }
#else
        error = "Link the LiveKit Swift package in Xcode to enable ride audio. See iOS/docs/DEPENDENCIES.md."
#endif
    }
    func muteAudio(_ muted: Bool) async {
#if canImport(LiveKit)
        guard let room, audioConnected else { return }
        do { try await room.localParticipant.setMicrophone(enabled:!muted); audioMuted = muted }
        catch { self.error = error.localizedDescription }
#endif
    }
    func leaveAudio() {
        audioGeneration = UUID(); audioConnected = false; audioStatus = "Off"; audioMuted = false
#if canImport(LiveKit)
        let old = room; room = nil; Task { await old?.disconnect() }
#endif
    }
}
struct WireClientError: LocalizedError { var message: String; init(_ message: String) { self.message = message }; var errorDescription: String? { message } }
