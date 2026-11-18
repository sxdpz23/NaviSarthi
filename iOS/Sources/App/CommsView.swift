import SwiftUI
import CoreImage.CIFilterBuiltins
import MapKit
import VisionKit
import Vision
import AVFoundation

struct CommsView: View {
    @EnvironmentObject private var comms: CommsClient
    @State private var nickname = ""
    @State private var name = ""
    @State private var invite = ""
    @State private var expiry = Date().addingTimeInterval(12 * 3600)
    @State private var text = ""
    @State private var preview: InvitePreview?
    @State private var showJoin = false
    @State private var showLeave = false
    @State private var scanner = false
    @State private var removing: RideMember?
    @EnvironmentObject private var nav: NavigationSession
    @EnvironmentObject private var settings: Settings
    var body: some View {
        NavigationStack {
            Group {
                if let snapshot = comms.snapshot {
                    List {
                        Section(snapshot.group.name) {
                            Text("Ends \(Date(timeIntervalSince1970:snapshot.group.expiresAt / 1000).formatted())").font(.caption).foregroundStyle(.secondary)
                            Label(comms.realtime ? "Live updates connected" : "Reconnecting · polling fallback",systemImage:comms.realtime ? "bolt.horizontal.circle" : "arrow.triangle.2.circlepath").font(.caption)
                            Button("Extend by one day") {
                                Task { await comms.extend(until:Date(timeIntervalSince1970:min(snapshot.group.expiresAt + 86400000,snapshot.group.createdAt + 14 * 86400000) / 1000)) }
                            }.disabled(snapshot.group.expiresAt >= snapshot.group.createdAt + 14 * 86400000)
                            ForEach(snapshot.group.members) { member in
                                HStack {
                                    Label(member.nickname,systemImage:member.id == snapshot.group.ownerId ? "crown" : "person")
                                    Spacer()
                                    if snapshot.memberId == snapshot.group.ownerId && member.id != snapshot.memberId {
                                        Button("Make lead") { Task { await comms.transfer(to:member.id) } }.font(.caption)
                                    }
                                }
                                .swipeActions { if snapshot.memberId == snapshot.group.ownerId && member.id != snapshot.memberId { Button("Remove",role:.destructive) { removing = member } } }
                            }
                        }
                        if let invitation = comms.invitation {
                            Section("Invite riders") {
                                if let image = qr(invitation) { Image(uiImage:image).interpolation(.none).resizable().scaledToFit().frame(maxWidth:220).padding().frame(maxWidth:.infinity).background(.white,in:RoundedRectangle(cornerRadius:16)) }
                                ShareLink(item:payload(invitation)) { Label("Share ride invitation",systemImage:"square.and.arrow.up") }
                                if snapshot.memberId == snapshot.group.ownerId { Button("Replace invitation") { Task { await comms.rotateInvitation() } } }
                            }
                        }
                        Section("Riders nearby") {
                            TimelineView(.periodic(from:.now,by:5)) { _ in VStack(spacing:12) {
                            Map {
                                UserAnnotation()
                                ForEach(snapshot.group.members) { member in
                                    if let position = snapshot.positions[member.id], comms.nowMs - position.receivedAt < 120000 {
                                        Annotation(member.nickname,coordinate:.init(latitude:position.lat,longitude:position.lng)) {
                                            Image(systemName:"motorcycle").padding(8).background(comms.nowMs - position.receivedAt > 30000 ? Color.gray : Color.blue,in:Circle())
                                        }
                                    }
                                }
                            }.frame(height:240).clipShape(RoundedRectangle(cornerRadius:16))
                            ForEach(snapshot.group.members) { member in
                                if let position = snapshot.positions[member.id], comms.nowMs - position.receivedAt < 120000 {
                                    HStack {
                                        Text(member.nickname); Spacer()
                                        if let fix = nav.fix { Text(settings.distance(fix.distance(from:CLLocation(latitude:position.lat,longitude:position.lng)))) }
                                        Text(comms.nowMs - position.receivedAt > 30000 ? "Stale" : "Live").font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                            }
                            } }
                            Toggle("Share my live location",isOn:Binding(get:{ comms.sharing },set:{ value in
                                Task { if value { nav.requestLocation(); await comms.startSharing() } else { await comms.stopSharing() } }
                            })).disabled(comms.stoppingSharing)
                            if comms.stoppingSharing { Text("Removing your server location; retrying when connected.").font(.caption) }
                            Text("Sharing is explicit and never resumes automatically after app restart. Distances are straight-line estimates.").font(.caption).foregroundStyle(.secondary)
                        }
                        Section("Ride audio") {
                            LabeledContent("Audio",value:comms.audioStatus)
                            if comms.audioConnected {
                                Toggle("Mute microphone",isOn:Binding(get:{ comms.audioMuted },set:{ value in Task { await comms.muteAudio(value) } }))
                                Button("Leave audio",role:.destructive) { comms.leaveAudio() }
                            } else {
                                Button("Join audio") { Task { await comms.joinAudio() } }.disabled(!snapshot.group.audioAvailable || comms.audioStatus != "Off")
                                if !snapshot.group.audioAvailable { Text("Audio is not configured on this ride server.").font(.caption) }
                            }
                        }
                        Section("Ride chat") {
                            ForEach(comms.messages) { message in
                                VStack(alignment:.leading,spacing:5) {
                                    Text(snapshot.group.members.first(where:{ $0.id == message.memberId })?.nickname ?? "Rider").font(.caption).foregroundStyle(.secondary)
                                    Text(message.text).textSelection(.enabled)
                                }.padding(.vertical,4)
                            }
                            HStack(alignment:.bottom) {
                                TextField("Message",text:$text,axis:.vertical).lineLimit(1...5)
                                Button("Send",systemImage:"arrow.up.circle.fill") {
                                    Task { if await comms.send(text) { text = "" } }
                                }.labelStyle(.iconOnly).disabled(comms.busy || text.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty)
                            }
                        }
                        Section {
                            Button(snapshot.memberId == snapshot.group.ownerId ? "End group for everyone" : "Leave ride",role:.destructive) { showLeave = true }
                        }
                    }.refreshable { await comms.refresh() }
                } else if comms.admission != nil {
                    ContentUnavailableView {
                        Label("Reconnect to your ride",systemImage:"person.2.wave.2")
                    } description: { Text("Your membership is stored securely on this iPhone.") } actions: {
                        Button("Refresh") { Task { await comms.refresh() } }
                    }
                } else {
                    Form {
                        Section {
                            TextField("Your nickname",text:$nickname)
                        } footer: { Text("No account required. Groups are temporary and private.") }
                        if comms.pendingAdmission {
                            Section("Unfinished admission") {
                                Text("A create/join response was interrupted. Retry the original request to recover your membership.")
                                Button("Retry admission") { Task { await comms.retryAdmission() } }.disabled(comms.busy)
                            }
                        } else {
                            Section("Create a ride") {
                                TextField("Ride name",text:$name)
                                DatePicker("Ends",selection:$expiry,in:Date()...Date().addingTimeInterval(14 * 86400))
                                Button("Create ride") { Task { await comms.create(nickname:nickname,name:name,expiry:expiry) } }
                                    .disabled(comms.busy || nickname.trimmingCharacters(in:.whitespaces).isEmpty || name.trimmingCharacters(in:.whitespaces).isEmpty)
                            }
                            Section("Join a ride") {
                                TextField("Paste invitation or code",text:$invite,axis:.vertical).textInputAutocapitalization(.never).autocorrectionDisabled()
                                if DataScannerViewController.isSupported {
                                    Button("Scan ride QR",systemImage:"qrcode.viewfinder") {
                                        Task {
                                            if await AVCaptureDevice.requestAccess(for:.video) { scanner = true }
                                            else { comms.error = "Allow Camera access in iPhone Settings to scan invitations." }
                                        }
                                    }
                                }
                                Button("Preview invitation") { Task { preview = await comms.resolve(invite); showJoin = preview != nil } }
                                    .disabled(comms.busy || invite.isEmpty || nickname.trimmingCharacters(in:.whitespaces).isEmpty)
                            }
                        }
                    }
                }
            }.navigationTitle("Comms")
                .overlay(alignment:.top) { if comms.busy { ProgressView().padding(12).background(.regularMaterial,in:Capsule()) } }
                .onAppear { if let pending = comms.pendingText, text.isEmpty { text = pending } }
                .sheet(isPresented:$scanner) { RideQRScanner { value in invite = value; scanner = false; Task { preview = await comms.resolve(value); showJoin = preview != nil } } }
                .alert("Remove rider?",isPresented:Binding(get:{ removing != nil },set:{ if !$0 { removing = nil } })) {
                    Button("Remove",role:.destructive) { if let member = removing { Task { await comms.remove(member:member.id) } }; removing = nil }
                    Button("Cancel",role:.cancel) { removing = nil }
                } message: { Text("\(removing?.nickname ?? "This rider") will lose access to this group.") }
                .alert("Join ride?",isPresented:$showJoin) {
                    Button("Join") { Task { await comms.join(nickname:nickname,invitation:invite) } }
                    Button("Cancel",role:.cancel) { }
                } message: { Text("\(preview?.name ?? "Ride")\nEnds \(Date(timeIntervalSince1970:(preview?.expiresAt ?? 0) / 1000).formatted())") }
                .confirmationDialog("Leave this ride?",isPresented:$showLeave,titleVisibility:.visible) {
                    Button("Confirm",role:.destructive) {
                        let owner = comms.snapshot?.group.ownerId == comms.snapshot?.memberId
                        Task { await comms.leave(endGroup:owner) }
                    }
                }
                .alert("Comms",isPresented:Binding(get:{ comms.error != nil },set:{ if !$0 { comms.error = nil } })) {
                    Button("OK",role:.cancel) { comms.error = nil }
                } message: { Text(comms.error ?? "") }
        }
    }
    private func payload(_ token: String) -> String { "{\"v\":1,\"type\":\"navisarthi-ride\",\"inviteToken\":\"\(token)\"}" }
    private func qr(_ token: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator(); filter.message = Data(payload(token).utf8)
        guard let output = filter.outputImage, let cg = CIContext().createCGImage(output.transformed(by:CGAffineTransform(scaleX:8,y:8)),from:output.extent.applying(CGAffineTransform(scaleX:8,y:8))) else { return nil }
        return UIImage(cgImage:cg)
    }
}

struct RideQRScanner: UIViewControllerRepresentable {
    var scanned: (String) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(scanned:scanned) }
    func makeUIViewController(context:Context) -> DataScannerViewController {
        let controller = DataScannerViewController(recognizedDataTypes:[.barcode(symbologies:[.qr])],qualityLevel:.balanced,recognizesMultipleItems:false,isGuidanceEnabled:true,isHighlightingEnabled:true)
        controller.delegate = context.coordinator
        do { try controller.startScanning() } catch { context.coordinator.scanned("") }
        return controller
    }
    func updateUIViewController(_ controller:DataScannerViewController,context:Context) {}
    static func dismantleUIViewController(_ controller:DataScannerViewController,coordinator:Coordinator) { controller.stopScanning() }
    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        var scanned: (String) -> Void; var finished = false
        init(scanned:@escaping (String)->Void) { self.scanned = scanned }
        func dataScanner(_ dataScanner:DataScannerViewController,didAdd addedItems:[RecognizedItem],allItems:[RecognizedItem]) {
            guard !finished else { return }
            for item in addedItems {
                if case .barcode(let code) = item, let text = code.payloadStringValue { finished = true; dataScanner.stopScanning(); scanned(text); break }
            }
        }
    }
}
