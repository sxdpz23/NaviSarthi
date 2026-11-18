import Foundation

public enum SyncAction: Equatable { case unchanged, upload, download, deleteLocal, deleteRemote, forget, conflict }
public enum SyncPolicy {
    /// Compare local content with its last acknowledged baseline, and Firestore's version.
    public static func decide(local:String?,remote:String?,baseline:String?,remoteVersion:String?,baselineVersion:String?) -> SyncAction {
        if local == nil && remote == nil { return .forget }
        if let local, let remote, local == remote { return .unchanged }
        guard let baseline else {
            if local == nil { return .download }
            if remote == nil { return .upload }
            return .conflict
        }
        let localChanged = local != baseline
        let remoteChanged = remoteVersion != baselineVersion
        if local == nil { return remoteChanged ? .conflict : .deleteRemote }
        if remote == nil { return localChanged ? .conflict : .deleteLocal }
        if localChanged && remoteChanged { return .conflict }
        if localChanged { return .upload }
        if remoteChanged { return .download }
        return .unchanged
    }
}
