import UIKit
import Social
import UniformTypeIdentifiers

/// Saves a bounded, private inbox item. Never tries to launch the host through private APIs.
final class ShareViewController: SLComposeServiceViewController {
    private var sending = false
    override func isContentValid() -> Bool { !sending }
    override func didSelectPost() {
        guard !sending else { return }; sending = true; validateContent()
        Task {
            do {
                let object = try await payload()
                guard let group = Bundle.main.object(forInfoDictionaryKey:"NaviSarthiAppGroup") as? String, group.hasPrefix("group."),
                      let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier:group) else { throw error("Enable the same App Group for NaviSarthi and its Share Extension") }
                let inbox = container.appendingPathComponent("ShareInbox",isDirectory:true)
                try FileManager.default.createDirectory(at:inbox,withIntermediateDirectories:true)
                let files = try FileManager.default.contentsOfDirectory(at:inbox,includingPropertiesForKeys:nil)
                guard files.filter({ $0.pathExtension == "json" }).count < 20 else { throw error("Open NaviSarthi and process pending shared destinations first") }
                let id = UUID().uuidString
                var result = object
                result["version"] = 1; result["id"] = id; result["createdMs"] = Date().timeIntervalSince1970 * 1000
                let bytes = try JSONSerialization.data(withJSONObject:result)
                guard bytes.count <= 15000000 else { throw error("Shared file is too large") }
                try bytes.write(to:inbox.appendingPathComponent(id + ".json"),options:[.atomic,.completeFileProtectionUntilFirstUserAuthentication])
                extensionContext?.completeRequest(returningItems:nil)
            } catch {
                sending = false; validateContent()
                let alert = UIAlertController(title:"Could not save to NaviSarthi",message:error.localizedDescription,preferredStyle:.alert)
                alert.addAction(UIAlertAction(title:"OK",style:.default)); present(alert,animated:true)
            }
        }
    }
    private func payload() async throws -> [String:Any] {
        let items = extensionContext?.inputItems as? [NSExtensionItem] ?? []
        for item in items {
            for provider in item.attachments ?? [] {
                if provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) {
                    if let url = try await load(provider,type:UTType.fileURL.identifier) as? URL, url.isFileURL {
                        guard url.pathExtension.lowercased() == "gpx" else { throw error("Only GPX files, map links and destination text are supported") }
                        let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                        guard (try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0) <= 10000000 else { throw error("Shared GPX files must be under 10 MB") }
                        let handle = try FileHandle(forReadingFrom:url); defer { try? handle.close() }
                        let data = try handle.read(upToCount:10000001) ?? Data()
                        guard data.count <= 10000000 else { throw error("Shared GPX files must be under 10 MB") }
                        return ["kind":"gpx","title":url.deletingPathExtension().lastPathComponent,"data":data.base64EncodedString()]
                    }
                }
                if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier), let url = try await load(provider,type:UTType.url.identifier) as? URL {
                    guard ["https","geo"].contains(url.scheme?.lowercased() ?? "") else { throw error("Share a secure map link or destination text") }
                    return ["kind":"text","title":item.attributedContentText?.string ?? "Shared map destination","text":url.absoluteString]
                }
                if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                    let value = try await load(provider,type:UTType.plainText.identifier)
                    let text = (value as? String) ?? (value as? Data).flatMap { String(data:$0,encoding:.utf8) } ?? ""
                    guard !text.isEmpty, text.utf8.count <= 10000 else { throw error("Destination text is empty or too long") }
                    return ["kind":"text","title":"Shared destination","text":text]
                }
            }
        }
        let text = contentText.trimmingCharacters(in:.whitespacesAndNewlines)
        guard !text.isEmpty, text.utf8.count <= 10000 else { throw error("Share a map link, destination text or GPX file") }
        return ["kind":"text","title":"Shared destination","text":text]
    }
    private func load(_ provider:NSItemProvider,type:String) async throws -> Any {
        try await withCheckedThrowingContinuation { (continuation:CheckedContinuation<Any,Error>) in
            provider.loadItem(forTypeIdentifier:type,options:nil) { item,error in
                if let error { continuation.resume(throwing:error) }
                else if let item { continuation.resume(returning:item) }
                else { continuation.resume(throwing:self.error("The sharing app returned no content")) }
            }
        }
    }
    private nonisolated func error(_ message:String) -> NSError { NSError(domain:"NaviSarthi.Share",code:1,userInfo:[NSLocalizedDescriptionKey:message]) }
}
