import Foundation
import SwiftUI
import SwiftData
import CoreLocation
import UserNotifications
import NaviSarthiCore
import CryptoKit

enum ConsoleTarget: String, CaseIterable, Identifiable {
    case dash = "Dash", pod = "Pod"
    var id: String { rawValue }
}
enum VehicleAppearance:String,CaseIterable,Identifiable {
    case meteor = "Meteor 350", himalayan = "Himalayan 450", guerrilla = "Guerrilla 450", bear = "Bear 650", himalayan750 = "Himalayan 750"
    var id:String { rawValue }
    var accent:Color {
        let value:Int
        switch self { case .meteor:value = 0xE7A0B0; case .himalayan:value = 0xE9B949; case .guerrilla:value = 0x65ADFF; case .bear:value = 0x48D2AC; case .himalayan750:value = 0xFF793E }
        return Color(red:Double((value >> 16) & 255) / 255,green:Double((value >> 8) & 255) / 255,blue:Double(value & 255) / 255)
    }
    static func matching(_ name:String) -> VehicleAppearance? {
        let value = name.lowercased().replacingOccurrences(of:" ",with:"")
        return allCases.first { value.contains($0.rawValue.lowercased().replacingOccurrences(of:" ",with:"")) }
    }
}

@Model final class Vehicle {
    var id: UUID
    var name: String
    var registration: String
    var odometerKm: Double
    var console: String
    var vin: String = ""
    var engineNumber: String = ""
    var colour: String = ""
    init(name: String, registration: String = "", odometerKm: Double = 0, console: String = "Dash") {
        id = UUID(); self.name = name; self.registration = registration
        self.odometerKm = odometerKm; self.console = console
    }
}

@Model final class FuelEntry {
    var id: UUID
    var vehicleID: UUID
    var date: Date
    var litres: Double
    var cost: Double
    var odometerKm: Double
    var fullTank: Bool
    init(vehicleID: UUID, date: Date, litres: Double, cost: Double, odometerKm: Double, fullTank: Bool) {
        id = UUID(); self.vehicleID = vehicleID; self.date = date; self.litres = litres
        self.cost = cost; self.odometerKm = odometerKm; self.fullTank = fullTank
    }
}

@Model final class ServiceEntry {
    var id: UUID
    var vehicleID: UUID
    var title: String
    var date: Date
    var odometerKm: Double
    var cost: Double
    var notes: String
    var nextDueKm: Double
    var nextDueDate: Date?
    init(vehicleID: UUID, title: String, date: Date, odometerKm: Double, cost: Double, notes: String, nextDueKm: Double, nextDueDate: Date?) {
        id = UUID(); self.vehicleID = vehicleID; self.title = title; self.date = date
        self.odometerKm = odometerKm; self.cost = cost; self.notes = notes
        self.nextDueKm = nextDueKm; self.nextDueDate = nextDueDate
    }
}

@Model final class SavedDestination {
    var id: UUID
    var title: String
    var latitude: Double
    var longitude: Double
    init(title: String, coordinate: CLLocationCoordinate2D) {
        id = UUID(); self.title = title; latitude = coordinate.latitude; longitude = coordinate.longitude
    }
    var coordinate: CLLocationCoordinate2D { .init(latitude: latitude, longitude: longitude) }
}

struct TrackFix: Codable {
    var latitude: Double
    var longitude: Double
    var time: Date
    var speed: Double
}

@Model final class RecordedRide {
    var id: UUID
    var started: Date
    var ended: Date
    var distanceMeters: Double
    var maxSpeedMps: Double
    var track: Data
    var vehicleID: UUID?
    init(started: Date, ended: Date, distanceMeters: Double, maxSpeedMps: Double, track: Data) {
        id = UUID(); self.started = started; self.ended = ended
        self.distanceMeters = distanceMeters; self.maxSpeedMps = maxSpeedMps; self.track = track
    }
    var fixes: [TrackFix] { (try? JSONDecoder().decode([TrackFix].self, from: track)) ?? [] }
    var duration: TimeInterval { max(0, ended.timeIntervalSince(started)) }
    var gpx: String {
        let iso = ISO8601DateFormatter()
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"NaviSarthi\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>Ride</name><trkseg>" + fixes.map {
            "<trkpt lat=\"\($0.latitude)\" lon=\"\($0.longitude)\"><time>\(iso.string(from: $0.time))</time></trkpt>"
        }.joined() + "</trkseg></trk></gpx>"
    }
}

@Model final class VehicleDocument {
    var id: UUID = UUID()
    var vehicleID: UUID
    var title: String
    var kind: String
    var number: String
    var expiry: Date?
    var filename: String
    @Attribute(.externalStorage) var attachment: Data?
    init(vehicleID:UUID,title:String,kind:String,number:String,expiry:Date?,filename:String,attachment:Data?) {
        self.vehicleID = vehicleID; self.title = title; self.kind = kind; self.number = number
        self.expiry = expiry; self.filename = filename; self.attachment = attachment
    }
}
@Model final class ExpenseEntry {
    var id: UUID = UUID()
    var vehicleID: UUID
    var date: Date
    var category: String
    var amount: Double
    var notes: String
    init(vehicleID:UUID,date:Date,category:String,amount:Double,notes:String) {
        self.vehicleID = vehicleID; self.date = date; self.category = category; self.amount = amount; self.notes = notes
    }
}
@Model final class StoredRoute {
    var id: UUID = UUID()
    var title: String
    var created: Date = Date()
    var payload: Data
    init(title:String,payload:Data) { self.title = title; self.payload = payload }
}
@Model final class MaintenanceRule {
    var id:UUID = UUID()
    var vehicleID:UUID
    var name:String
    var intervalKm:Double
    var intervalMonths:Int
    var lastKm:Double
    var lastDate:Date
    var scheduled:Bool
    var completed:Bool = false
    init(vehicleID:UUID,name:String,intervalKm:Double,intervalMonths:Int,lastKm:Double,lastDate:Date,scheduled:Bool = false) {
        self.vehicleID = vehicleID; self.name = name; self.intervalKm = intervalKm; self.intervalMonths = intervalMonths; self.lastKm = lastKm; self.lastDate = lastDate; self.scheduled = scheduled
    }
    var dueKm:Double? { intervalKm > 0 ? (scheduled ? intervalKm : lastKm + intervalKm) : nil }
    var dueDate:Date? { intervalMonths > 0 ? Calendar.current.date(byAdding:.month,value:intervalMonths,to:lastDate) : nil }
}
@Model final class CloudRecordLink {
    var owner:String
    var scope:UUID
    var kind:String
    var localID:UUID
    var remoteID:String
    var localHash:String
    var remoteVersion:String?
    init(owner:String,scope:UUID,kind:String,localID:UUID,remoteID:String,localHash:String,remoteVersion:String?) {
        self.owner = owner; self.scope = scope; self.kind = kind; self.localID = localID; self.remoteID = remoteID
        self.localHash = localHash; self.remoteVersion = remoteVersion
    }
}

enum RideReminders {
    static func requestPermission() async throws -> Bool {
        try await UNUserNotificationCenter.current().requestAuthorization(options:[.alert,.sound,.badge])
    }
    static func cancel(_ id: UUID) { UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers:[id.uuidString]) }
    static func schedule(id:UUID,title:String,body:String,due:Date,daysBefore:Int = 0) async throws {
        cancel(id)
        let content = UNMutableNotificationContent(); content.title = title; content.body = body; content.sound = .default
        let triggerDate = Calendar.current.date(byAdding:.day,value:-daysBefore,to:due) ?? due
        let date = max(triggerDate,Date().addingTimeInterval(60))
        var components = Calendar.current.dateComponents([.year,.month,.day],from:date)
        components.hour = 9; components.minute = 0
        let trigger: UNNotificationTrigger = date.timeIntervalSinceNow < 120 ? UNTimeIntervalNotificationTrigger(timeInterval:60,repeats:false) : UNCalendarNotificationTrigger(dateMatching:components,repeats:false)
        try await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier:id.uuidString,content:content,trigger:trigger))
    }
}

enum AppStorageSchema {
    static let schema = Schema([Vehicle.self, FuelEntry.self, ServiceEntry.self, SavedDestination.self, RecordedRide.self, VehicleDocument.self, ExpenseEntry.self, StoredRoute.self, MaintenanceRule.self, CloudRecordLink.self])
    static func container() throws -> ModelContainer {
        try ModelContainer(for: schema, configurations: [ModelConfiguration(schema: schema, cloudKitDatabase: .none)])
    }
}

struct BackupRow: Codable {
    var kind: String
    var id: UUID
    var parent: UUID? = nil
    var title: String = ""
    var text: String = ""
    var extra: String = ""
    var date: Date? = nil
    var end: Date? = nil
    var a: Double = 0
    var b: Double = 0
    var c: Double = 0
    var flag: Bool = false
    var payload: Data? = nil
}
struct LocalBackup: Codable {
    var format = "navisarthi-ios"
    var version = 1
    var created = Date()
    var rows: [BackupRow]
    @MainActor static func capture(_ context: ModelContext) throws -> LocalBackup {
        var rows: [BackupRow] = []
        for v in try context.fetch(FetchDescriptor<Vehicle>()) {
            let identity = [v.registration,v.console,v.vin,v.engineNumber,v.colour]
            rows.append(BackupRow(kind:"vehicle",id:v.id,title:v.name,a:v.odometerKm,payload:try JSONEncoder().encode(identity)))
        }
        for v in try context.fetch(FetchDescriptor<FuelEntry>()) { rows.append(BackupRow(kind:"fuel",id:v.id,parent:v.vehicleID,date:v.date,a:v.odometerKm,b:v.litres,c:v.cost,flag:v.fullTank)) }
        for v in try context.fetch(FetchDescriptor<ServiceEntry>()) { rows.append(BackupRow(kind:"service",id:v.id,parent:v.vehicleID,title:v.title,text:v.notes,date:v.date,end:v.nextDueDate,a:v.odometerKm,b:v.cost,c:v.nextDueKm)) }
        for v in try context.fetch(FetchDescriptor<SavedDestination>()) { rows.append(BackupRow(kind:"saved",id:v.id,title:v.title,a:v.latitude,b:v.longitude)) }
        for v in try context.fetch(FetchDescriptor<RecordedRide>()) { rows.append(BackupRow(kind:"ride",id:v.id,parent:v.vehicleID,date:v.started,end:v.ended,a:v.distanceMeters,b:v.maxSpeedMps,payload:v.track)) }
        for v in try context.fetch(FetchDescriptor<VehicleDocument>()) { rows.append(BackupRow(kind:"document",id:v.id,parent:v.vehicleID,title:v.title,text:v.number,extra:v.kind,date:v.expiry,payload:try JSONEncoder().encode(DocumentPayload(filename:v.filename,data:v.attachment)))) }
        for v in try context.fetch(FetchDescriptor<ExpenseEntry>()) { rows.append(BackupRow(kind:"expense",id:v.id,parent:v.vehicleID,title:v.category,text:v.notes,date:v.date,a:v.amount)) }
        for v in try context.fetch(FetchDescriptor<StoredRoute>()) { rows.append(BackupRow(kind:"route",id:v.id,title:v.title,date:v.created,payload:v.payload)) }
        for v in try context.fetch(FetchDescriptor<MaintenanceRule>()) { rows.append(BackupRow(kind:"rule",id:v.id,parent:v.vehicleID,title:v.name,extra:v.scheduled ? "scheduled" : "recurring",date:v.lastDate,a:v.intervalKm,b:Double(v.intervalMonths),c:v.lastKm,flag:v.completed)) }
        return LocalBackup(rows:rows)
    }
    struct DocumentPayload: Codable { var filename: String; var data: Data? }
    static func read(_ data: Data) throws -> LocalBackup {
        guard data.count <= 100_000_000 else { throw WireClientError("Backup exceeds 100 MB") }
        if let header = try JSONSerialization.jsonObject(with:data) as? [String:Any], header["format"] as? String == "navisarthi-backup" { return try read(JSONEncoder().encode(AndroidBackup.convert(header))) }
        let backup = try JSONDecoder().decode(LocalBackup.self,from:data)
        guard backup.format == "navisarthi-ios", backup.version == 1, backup.rows.count <= 100000 else { throw WireClientError("Unsupported backup format") }
        var keys = Set<String>()
        let kinds: Set<String> = ["vehicle","fuel","service","saved","ride","document","expense","route","rule"]
        let vehicles = Set(backup.rows.filter { $0.kind == "vehicle" }.map(\.id))
        for row in backup.rows {
            guard kinds.contains(row.kind), keys.insert(row.kind + row.id.uuidString).inserted,
                  row.a.isFinite, row.b.isFinite, row.c.isFinite,
                  abs(row.a) <= 9_000_000_000_000_000, abs(row.b) <= 9_000_000_000_000_000, abs(row.c) <= 9_000_000_000_000_000,
                  row.title.count <= 10000, row.text.count <= 100000,
                  row.date?.timeIntervalSince1970.isFinite ?? true,
                  row.end?.timeIntervalSince1970.isFinite ?? true else { throw WireClientError("Invalid backup record") }
            if ["fuel","service","document","expense","rule"].contains(row.kind) { guard let parent = row.parent, vehicles.contains(parent) else { throw WireClientError("Backup contains an orphan garage record") } }
            if ["vehicle","fuel","service","ride","expense"].contains(row.kind) { guard row.a >= 0, row.b >= 0, row.c >= 0 else { throw WireClientError("Negative garage or ride value") } }
            if row.kind == "saved" { guard Coordinate(row.a,row.b).isValid else { throw WireClientError("Invalid saved destination") } }
            if row.kind == "fuel" { guard row.date != nil, row.b > 0 else { throw WireClientError("Invalid fuel record") } }
            if ["service","ride","expense"].contains(row.kind), row.date == nil { throw WireClientError("Missing record date") }
            if row.kind == "ride" {
                guard let payload = row.payload, let end = row.end, end >= row.date! else { throw WireClientError("Invalid ride") }
                let points = try JSONDecoder().decode([TrackFix].self,from:payload)
                guard points.count <= 500000, points.allSatisfy({ Coordinate($0.latitude,$0.longitude).isValid && $0.speed.isFinite && $0.speed >= 0 }) else { throw WireClientError("Invalid ride track") }
            }
            if row.kind == "vehicle" { guard let payload = row.payload, try JSONDecoder().decode([String].self,from:payload).count == 5 else { throw WireClientError("Invalid vehicle identity") } }
            if row.kind == "document" { guard let payload = row.payload else { throw WireClientError("Invalid document") }; _ = try JSONDecoder().decode(DocumentPayload.self,from:payload) }
            if row.kind == "rule" { guard row.date != nil, row.a >= 0, row.b >= 0, row.b <= 1200, row.c >= 0 else { throw WireClientError("Invalid maintenance rule") } }
        }
        return backup
    }
    /// Add missing IDs only. Never overwrite current records or import account/Comms credentials.
    @MainActor func restore(into container: ModelContainer) throws -> Int {
        let context = ModelContext(container); context.autosaveEnabled = false
        let existing = try Self.capture(context)
        let known = Set(existing.rows.map { $0.kind + $0.id.uuidString })
        var count = 0
        for row in rows where !known.contains(row.kind + row.id.uuidString) {
            switch row.kind {
            case "vehicle":
                let identity = try JSONDecoder().decode([String].self,from:row.payload!)
                let v = Vehicle(name:row.title,registration:identity[0],odometerKm:row.a,console:identity[1]); v.id = row.id; v.vin = identity[2]; v.engineNumber = identity[3]; v.colour = identity[4]; context.insert(v)
            case "fuel":
                let v = FuelEntry(vehicleID:row.parent!,date:row.date!,litres:row.b,cost:row.c,odometerKm:row.a,fullTank:row.flag); v.id = row.id; context.insert(v)
            case "service":
                let v = ServiceEntry(vehicleID:row.parent!,title:row.title,date:row.date!,odometerKm:row.a,cost:row.b,notes:row.text,nextDueKm:row.c,nextDueDate:row.end); v.id = row.id; context.insert(v)
            case "saved":
                let v = SavedDestination(title:row.title,coordinate:.init(latitude:row.a,longitude:row.b)); v.id = row.id; context.insert(v)
            case "ride":
                let v = RecordedRide(started:row.date!,ended:row.end!,distanceMeters:row.a,maxSpeedMps:row.b,track:row.payload!); v.id = row.id; v.vehicleID = row.parent; context.insert(v)
            case "document":
                let data = try JSONDecoder().decode(DocumentPayload.self,from:row.payload!)
                let v = VehicleDocument(vehicleID:row.parent!,title:row.title,kind:row.extra,number:row.text,expiry:row.date,filename:data.filename,attachment:data.data); v.id = row.id; context.insert(v)
            case "expense":
                let v = ExpenseEntry(vehicleID:row.parent!,date:row.date!,category:row.title,amount:row.a,notes:row.text); v.id = row.id; context.insert(v)
            case "route":
                let v = StoredRoute(title:row.title,payload:row.payload ?? Data()); v.id = row.id; v.created = row.date ?? created; context.insert(v)
            case "rule":
                let v = MaintenanceRule(vehicleID:row.parent!,name:row.title,intervalKm:row.a,intervalMonths:Int(row.b),lastKm:row.c,lastDate:row.date!,scheduled:row.extra == "scheduled"); v.id = row.id; v.completed = row.flag; context.insert(v)
            default: throw WireClientError("Unsupported backup record")
            }
            count += 1
        }
        try context.save(); return count
    }
}

enum AndroidBackup {
    static func stableID(_ table:String,_ value:String) -> UUID {
        if let uuid = UUID(uuidString:value) { return uuid }
        let hex = SHA256.hash(data:Data(("navisarthi-android:" + table + ":" + value).utf8)).prefix(16).map { String(format:"%02x",$0) }.joined()
        let chars = Array(hex)
        return UUID(uuidString:[String(chars[0..<8]),String(chars[8..<12]),String(chars[12..<16]),String(chars[16..<20]),String(chars[20..<32])].joined(separator:"-"))!
    }
    static func convert(_ object:[String:Any]) throws -> LocalBackup {
        guard object["schemaVersion"] as? Int == 1, let records = object["records"] as? [String:Any], let attachments = object["attachments"] as? [String:[String:Any]] else { throw WireClientError("Unsupported Android backup") }
        let tables:Set<String> = ["ride","service_record","scheduled_service","document","bike_identity","fuel_fillup","maintenance_item","bike_state","saved_location"]
        guard Set(records.keys) == tables else { throw WireClientError("Unknown Android backup tables; no data imported") }
        func rows(_ table:String) throws -> [[String:Any]] { guard let rows = records[table] as? [[String:Any]], rows.count <= 100000 else { throw WireClientError("Invalid Android table: \(table)") }; return rows }
        func text(_ row:[String:Any],_ key:String) -> String { row[key] as? String ?? "" }
        func number(_ row:[String:Any],_ key:String) -> Double { (row[key] as? NSNumber)?.doubleValue ?? 0 }
        func date(_ row:[String:Any],_ key:String) -> Date { Date(timeIntervalSince1970:number(row,key) / 1000) }
        func id(_ row:[String:Any],_ table:String) -> UUID { stableID(table,text(row,"sid").isEmpty ? String(describing:row["id"] ?? "0") : text(row,"sid")) }
        var attachmentBytes = 0, files:[String:LocalBackup.DocumentPayload] = [:]
        for (key,value) in attachments {
            guard let encoded = value["base64"] as? String, encoded.count <= 14_000_000, let bytes = Data(base64Encoded:encoded), bytes.count <= 10 * 1024 * 1024 else { throw WireClientError("Invalid Android attachment") }
            attachmentBytes += bytes.count; guard attachmentBytes <= 40 * 1024 * 1024 else { throw WireClientError("Android attachments exceed 40 MB") }
            files[key] = LocalBackup.DocumentPayload(filename:(text(value,"name") as NSString).lastPathComponent,data:bytes)
        }
        let identity = try rows("bike_identity").first ?? [:]
        let identityKey = !text(identity,"vin").isEmpty ? text(identity,"vin") : !text(identity,"reg_no").isEmpty ? text(identity,"reg_no") : "default"
        let vehicleID = stableID("vehicle",identityKey)
        let odo = try rows("bike_state").first.map { number($0,"odometer_km") } ?? 0
        var output = [BackupRow(kind:"vehicle",id:vehicleID,title:"Imported motorcycle",a:odo,payload:try JSONEncoder().encode([text(identity,"reg_no"),"Dash",text(identity,"vin"),text(identity,"engine_no"),text(identity,"colour")]))]
        let serviceRows = try rows("service_record")
        let completed = Set(serviceRows.map { text($0,"scheduled_key") }.filter { !$0.isEmpty })
        for row in try rows("fuel_fillup") {
            // Older Android snapshots have no full-tank flag; preserve that uncertainty.
            output.append(BackupRow(kind:"fuel",id:id(row,"fuel"),parent:vehicleID,date:date(row,"date_ms"),a:number(row,"odometer_km"),b:number(row,"litres"),c:number(row,"cost"),flag:false))
        }
        for row in serviceRows {
            let title = text(row,"title").isEmpty ? text(row,"name") : text(row,"title")
            output.append(BackupRow(kind:"service",id:id(row,"service"),parent:vehicleID,title:title,text:text(row,"note"),date:date(row,"date_ms"),a:number(row,"odometer_km"),b:number(row,"cost")))
            let path = text(row,"invoice_path")
            if !path.isEmpty {
                guard let file = files[path] else { throw WireClientError("Android invoice attachment missing") }
                output.append(BackupRow(kind:"document",id:stableID("invoice",id(row,"service").uuidString),parent:vehicleID,title:title + " invoice",extra:"Invoice",payload:try JSONEncoder().encode(file)))
            }
        }
        for row in try rows("document") {
            let path = text(row,"file_path")
            guard path.isEmpty || files[path] != nil else { throw WireClientError("Android document attachment missing") }
            let file = files[path] ?? LocalBackup.DocumentPayload(filename:"",data:nil)
            output.append(BackupRow(kind:"document",id:id(row,"document"),parent:vehicleID,title:text(row,"title"),text:text(row,"number"),extra:text(row,"type").capitalized,date:number(row,"expiry_ms") > 0 ? date(row,"expiry_ms") : nil,payload:try JSONEncoder().encode(file)))
        }
        for row in try rows("saved_location") { output.append(BackupRow(kind:"saved",id:id(row,"saved"),title:text(row,"name"),a:number(row,"lat"),b:number(row,"lng"))) }
        for row in try rows("maintenance_item") {
            output.append(BackupRow(kind:"rule",id:id(row,"maintenance"),parent:vehicleID,title:text(row,"name"),extra:"recurring",date:date(row,"last_done_date_ms"),a:number(row,"interval_km"),b:number(row,"interval_months"),c:number(row,"last_done_odo_km")))
        }
        for row in try rows("scheduled_service") {
            output.append(BackupRow(kind:"rule",id:id(row,"schedule"),parent:vehicleID,title:text(row,"label"),extra:"scheduled",date:date(identity,"purchase_ms"),a:number(row,"target_km"),b:number(identity,"purchase_ms") > 0 ? number(row,"target_months") : 0,flag:completed.contains(text(row,"sid"))))
        }
        for row in try rows("ride") {
            let encoded = text(row,"track"), points = encoded.isEmpty ? [] : try Polyline.decode(encoded,precision:5)
            let start = date(row,"start_ms"), end = date(row,"end_ms")
            let track = points.enumerated().map { index,c in TrackFix(latitude:c.latitude,longitude:c.longitude,time:start.addingTimeInterval(end.timeIntervalSince(start) * Double(index) / Double(max(1,points.count - 1))),speed:0) }
            output.append(BackupRow(kind:"ride",id:id(row,"ride"),parent:vehicleID,date:start,end:end,a:number(row,"distance_m"),b:number(row,"max_speed"),payload:try JSONEncoder().encode(track)))
        }
        return LocalBackup(rows:output)
    }
}

@MainActor final class Settings: ObservableObject {
    @Published var target: ConsoleTarget { didSet { UserDefaults.standard.set(target.rawValue, forKey: "console") } }
    @Published var miles: Bool { didSet { UserDefaults.standard.set(miles, forKey: "miles") } }
    @Published var voice: Bool { didSet { UserDefaults.standard.set(voice, forKey: "voice") } }
    @Published var traffic: Bool { didSet { UserDefaults.standard.set(traffic, forKey: "traffic") } }
    @Published var vehicleID: String { didSet { UserDefaults.standard.set(vehicleID,forKey:"selectedVehicle") } }
    @Published var routeColour: String { didSet { UserDefaults.standard.set(routeColour,forKey:"routeColour") } }
    @Published var vehicleAppearance:VehicleAppearance { didSet { UserDefaults.standard.set(vehicleAppearance.rawValue,forKey:"vehicleAppearance") } }
    @Published var terrain:Bool { didSet { UserDefaults.standard.set(terrain,forKey:"terrain") } }
    init() {
        target = ConsoleTarget(rawValue: UserDefaults.standard.string(forKey: "console") ?? "Dash") ?? .dash
        miles = UserDefaults.standard.bool(forKey: "miles")
        voice = UserDefaults.standard.object(forKey: "voice") as? Bool ?? true
        traffic = UserDefaults.standard.object(forKey: "traffic") as? Bool ?? true
        vehicleID = UserDefaults.standard.string(forKey:"selectedVehicle") ?? ""
        routeColour = UserDefaults.standard.string(forKey:"routeColour") ?? "Blue"
        vehicleAppearance = VehicleAppearance(rawValue:UserDefaults.standard.string(forKey:"vehicleAppearance") ?? "") ?? .himalayan
        terrain = UserDefaults.standard.bool(forKey:"terrain")
    }
    func distance(_ meters: Double) -> String {
        if miles { return String(format: "%.1f mi", meters / 1609.344) }
        return meters < 1000 ? "\(Int(meters)) m" : String(format: "%.1f km", meters / 1000)
    }
    var routeColor: Color { routeColour == "Gold" ? Color(red:0.95,green:0.7,blue:0.25) : (routeColour == "Vehicle" ? vehicleAppearance.accent : .blue) }
}
