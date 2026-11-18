import SwiftUI
import SwiftData
import UniformTypeIdentifiers
import NaviSarthiCore
import QuickLook

struct GarageView: View {
    @Environment(\.modelContext) private var context
    @Query(sort:\Vehicle.name) private var vehicles: [Vehicle]
    @State private var adding = false
    @State private var error: String?
    @State private var deleting: Vehicle?
    @EnvironmentObject private var settings: Settings
    var body: some View {
        NavigationStack {
            List {
                if vehicles.isEmpty {
                    ContentUnavailableView("Your garage",systemImage:"motorcycle",description:Text("Add your motorcycle to record fuel and maintenance."))
                    Button("Add motorcycle") { adding = true }
                }
                ForEach(vehicles) { vehicle in
                    NavigationLink {
                        VehicleDetailView(vehicle:vehicle)
                    } label: {
                        VStack(alignment:.leading,spacing:6) {
                            Text(vehicle.name).font(.headline)
                            Text("\(vehicle.registration) · \(vehicle.odometerKm.formatted(.number.precision(.fractionLength(0)))) km").font(.subheadline).foregroundStyle(.secondary)
                        }.padding(.vertical,8)
                    }
                    .swipeActions { Button("Delete",role:.destructive) { deleting = vehicle } }
                }
            }.navigationTitle("Garage").toolbar { Button("Add",systemImage:"plus") { adding = true } }
                .sheet(isPresented:$adding) { VehicleEditor() }
                .confirmationDialog("Delete motorcycle and its garage records?",isPresented:Binding(get:{ deleting != nil },set:{ if !$0 { deleting = nil } }),titleVisibility:.visible) {
                    Button("Delete",role:.destructive) {
                        guard let vehicle = deleting else { return }
                        do {
                            for row in try context.fetch(FetchDescriptor<FuelEntry>()) where row.vehicleID == vehicle.id { context.delete(row) }
                            for row in try context.fetch(FetchDescriptor<ServiceEntry>()) where row.vehicleID == vehicle.id { RideReminders.cancel(row.id); context.delete(row) }
                            for row in try context.fetch(FetchDescriptor<VehicleDocument>()) where row.vehicleID == vehicle.id { RideReminders.cancel(row.id); context.delete(row) }
                            for row in try context.fetch(FetchDescriptor<ExpenseEntry>()) where row.vehicleID == vehicle.id { context.delete(row) }
                            for row in try context.fetch(FetchDescriptor<MaintenanceRule>()) where row.vehicleID == vehicle.id { RideReminders.cancel(row.id); context.delete(row) }
                            context.delete(vehicle); try context.save()
                            if settings.vehicleID == vehicle.id.uuidString { settings.vehicleID = "" }
                        } catch { self.error = error.localizedDescription }
                        deleting = nil
                    }
                }
                .alert("Garage",isPresented:Binding(get:{ error != nil },set:{ if !$0 { error = nil } })) { Button("OK") { error = nil } } message: { Text(error ?? "") }
        }
    }
}

struct VehicleEditor: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var registration = ""
    @State private var odometer = ""
    @State private var console = "Dash"
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                TextField("Motorcycle model",text:$name)
                TextField("Registration",text:$registration).textInputAutocapitalization(.characters)
                TextField("Odometer (km)",text:$odometer).keyboardType(.decimalPad)
                Picker("Console",selection:$console) { Text("Dash").tag("Dash"); Text("Pod").tag("Pod") }
                if let error { Text(error).foregroundStyle(.red) }
            }.navigationTitle("Add motorcycle").toolbar {
                ToolbarItem(placement:.cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement:.confirmationAction) { Button("Save") { save() }.disabled(name.trimmingCharacters(in:.whitespaces).isEmpty) }
            }
        }
    }
    private func save() {
        guard let km = Double(odometer), km.isFinite, km >= 0, km <= 9_000_000_000_000_000 else { error = "Enter a valid odometer reading in km."; return }
        context.insert(Vehicle(name:name.trimmingCharacters(in:.whitespacesAndNewlines),registration:registration,odometerKm:km,console:console))
        do { try context.save(); dismiss() } catch { self.error = error.localizedDescription }
    }
}

struct VehicleDetailView: View {
    @Environment(\.modelContext) private var context
    @Bindable var vehicle: Vehicle
    @Query(sort:\FuelEntry.date,order:.reverse) private var allFuel: [FuelEntry]
    @Query(sort:\ServiceEntry.date,order:.reverse) private var allServices: [ServiceEntry]
    @State private var addingFuel = false
    @State private var addingService = false
    @State private var error: String?
    @State private var editedName = ""
    @State private var editedRegistration = ""
    @State private var editedOdometer = ""
    @State private var editingFuel: FuelEntry?
    @State private var editingService: ServiceEntry?
    @EnvironmentObject private var settings: Settings
    @EnvironmentObject private var nav: NavigationSession
    private var fuel: [FuelEntry] { allFuel.filter { $0.vehicleID == vehicle.id } }
    private var services: [ServiceEntry] { allServices.filter { $0.vehicleID == vehicle.id } }
    var body: some View {
        List {
            Section("Motorcycle") {
                Button(settings.vehicleID == vehicle.id.uuidString ? "Selected for ride recording" : "Select for ride recording") {
                    settings.vehicleID = vehicle.id.uuidString
                    settings.target = ConsoleTarget(rawValue:vehicle.console) ?? .dash
                    if let appearance = VehicleAppearance.matching(vehicle.name) { settings.vehicleAppearance = appearance }
                }.disabled(nav.active || nav.recording)
                TextField("Name",text:$editedName)
                TextField("Registration",text:$editedRegistration)
                LabeledContent("Odometer (km)") { TextField("km",text:$editedOdometer).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                Button("Save changes") {
                    guard let km = Double(editedOdometer), km.isFinite, km >= 0, km <= 9_000_000_000_000_000, !editedName.trimmingCharacters(in:.whitespaces).isEmpty else { error = "Enter a name and valid odometer."; return }
                    vehicle.name = editedName.trimmingCharacters(in:.whitespacesAndNewlines)
                    vehicle.registration = editedRegistration; vehicle.odometerKm = km
                    do { try context.save() } catch { self.error = error.localizedDescription }
                }
            }
            Section {
                ForEach(services) { entry in
                    VStack(alignment:.leading,spacing:5) {
                        Text(entry.title).font(.headline)
                        Text("\(entry.date.formatted(date:.abbreviated,time:.omitted)) · \(Int(entry.odometerKm)) km · \(entry.cost.formatted())").font(.caption).foregroundStyle(.secondary)
                        if !entry.notes.isEmpty { Text(entry.notes).font(.subheadline) }
                        if entry.nextDueKm > 0 {
                            Text(entry.nextDueKm <= vehicle.odometerKm ? "Due for service" : "Next at \(Int(entry.nextDueKm)) km")
                                .font(.caption).foregroundStyle(entry.nextDueKm <= vehicle.odometerKm ? Color.orange : Color.secondary)
                        }
                        if let date = entry.nextDueDate { Text("Due \(date.formatted(date:.abbreviated,time:.omitted))").font(.caption).foregroundStyle(date < Date() ? Color.orange : Color.secondary) }
                    }.padding(.vertical,4)
                    .swipeActions(edge:.leading) { Button("Edit") { editingService = entry }.tint(.blue) }
                }.onDelete { indexes in remove(indexes.map { services[$0] }) }
                Button("Log service",systemImage:"plus") { addingService = true }
            } header: { Text("Maintenance") }
            Section {
                if let efficiency = FuelEconomy.kilometersPerLitre(fuel.map { FuelSample(odometer:$0.odometerKm,litres:$0.litres,full:$0.fullTank) }) {
                    LabeledContent("Full-to-full economy",value:String(format:"%.1f km/L",efficiency))
                }
                ForEach(fuel) { entry in
                    VStack(alignment:.leading,spacing:5) {
                        Text("\(entry.litres.formatted()) L · \(entry.cost.formatted())").font(.headline)
                        Text("\(entry.date.formatted(date:.abbreviated,time:.omitted)) · \(Int(entry.odometerKm)) km\(entry.fullTank ? " · Full tank" : "")").font(.caption).foregroundStyle(.secondary)
                    }.padding(.vertical,4)
                    .swipeActions(edge:.leading) { Button("Edit") { editingFuel = entry }.tint(.blue) }
                }.onDelete { indexes in remove(indexes.map { fuel[$0] }) }
                Button("Add fill-up",systemImage:"plus") { addingFuel = true }
            } header: { Text("Fuel diary") } footer: { Text("Costs use your entered local currency. Odometer storage is always in kilometres.") }
            Section {
                NavigationLink("Documents and invoices",destination:VehicleDocumentsView(vehicle:vehicle))
                NavigationLink("Expenses",destination:ExpensesView(vehicle:vehicle))
                NavigationLink("Recurring maintenance and service schedule",destination:MaintenanceRulesView(vehicle:vehicle))
                Button("Enable maintenance and expiry reminders") {
                    Task {
                        do {
                            guard try await RideReminders.requestPermission() else { error = "Enable notifications in iPhone Settings."; return }
                            for service in services {
                                if let due = service.nextDueDate { try await RideReminders.schedule(id:service.id,title:vehicle.name,body:"\(service.title) is due",due:due) }
                            }
                            for document in try context.fetch(FetchDescriptor<VehicleDocument>()) where document.vehicleID == vehicle.id {
                                if let due = document.expiry { try await RideReminders.schedule(id:document.id,title:vehicle.name,body:"\(document.title) expires soon",due:due,daysBefore:30) }
                            }
                            for rule in try context.fetch(FetchDescriptor<MaintenanceRule>()) where rule.vehicleID == vehicle.id && !rule.completed {
                                if let due = rule.dueDate { try await RideReminders.schedule(id:rule.id,title:vehicle.name,body:"\(rule.name) is due",due:due) }
                            }
                        } catch { self.error = error.localizedDescription }
                    }
                }
            }
        }.navigationTitle(vehicle.name)
            .onAppear { editedName = vehicle.name; editedRegistration = vehicle.registration; editedOdometer = String(vehicle.odometerKm) }
            .onChange(of:vehicle.odometerKm) { _,value in editedOdometer = String(value) }
            .sheet(isPresented:$addingFuel) { LogEntryEditor(vehicle:vehicle,fuel:true) }
            .sheet(isPresented:$addingService) { LogEntryEditor(vehicle:vehicle,fuel:false) }
            .sheet(item:$editingFuel) { entry in LogEntryEditor(vehicle:vehicle,fuel:true,existingFuel:entry) }
            .sheet(item:$editingService) { entry in LogEntryEditor(vehicle:vehicle,fuel:false,existingService:entry) }
            .alert("Garage",isPresented:Binding(get:{ error != nil },set:{ if !$0 { error = nil } })) { Button("OK") { error = nil } } message: { Text(error ?? "") }
    }
    private func remove<T: PersistentModel>(_ entries: [T]) {
        for entry in entries { if let service = entry as? ServiceEntry { RideReminders.cancel(service.id) }; context.delete(entry) }
        do { try context.save() } catch { self.error = error.localizedDescription }
    }
}

struct LogEntryEditor: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    let vehicle: Vehicle
    let fuel: Bool
    var existingFuel: FuelEntry? = nil
    var existingService: ServiceEntry? = nil
    @State private var title = ""
    @State private var date = Date()
    @State private var odo = ""
    @State private var cost = ""
    @State private var litres = ""
    @State private var full = true
    @State private var notes = ""
    @State private var nextKm = ""
    @State private var hasDate = false
    @State private var nextDate = Date().addingTimeInterval(90 * 86400)
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                DatePicker("Date",selection:$date,in:...Date(),displayedComponents:.date)
                if !fuel { TextField("Service title",text:$title) }
                TextField("Odometer (km)",text:$odo).keyboardType(.decimalPad)
                TextField("Cost",text:$cost).keyboardType(.decimalPad)
                if fuel {
                    TextField("Litres",text:$litres).keyboardType(.decimalPad)
                    Toggle("Full tank",isOn:$full)
                } else {
                    TextField("Notes",text:$notes,axis:.vertical)
                    TextField("Next due at km (optional)",text:$nextKm).keyboardType(.decimalPad)
                    Toggle("Next due date",isOn:$hasDate)
                    if hasDate { DatePicker("Due",selection:$nextDate,displayedComponents:.date) }
                }
                if let error { Text(error).foregroundStyle(.red) }
            }.navigationTitle(fuel ? "Add fill-up" : "Log maintenance")
                .onAppear {
                    odo = String(vehicle.odometerKm)
                    if let entry = existingFuel { date = entry.date; odo = String(entry.odometerKm); cost = String(entry.cost); litres = String(entry.litres); full = entry.fullTank }
                    if let entry = existingService { title = entry.title; date = entry.date; odo = String(entry.odometerKm); cost = String(entry.cost); notes = entry.notes; nextKm = entry.nextDueKm > 0 ? String(entry.nextDueKm) : ""; hasDate = entry.nextDueDate != nil; nextDate = entry.nextDueDate ?? Date() }
                }
                .toolbar {
                    ToolbarItem(placement:.cancellationAction) { Button("Cancel") { dismiss() } }
                    ToolbarItem(placement:.confirmationAction) { Button("Save") { save() } }
                }
        }
    }
    private func save() {
        guard let odometer = Double(odo), odometer.isFinite, odometer >= 0, odometer <= 9_000_000_000_000_000,
              let expense = Double(cost), expense.isFinite, expense >= 0 else { error = "Enter a valid odometer and cost."; return }
        if fuel {
            guard let volume = Double(litres), volume.isFinite, volume > 0 else { error = "Litres must be greater than zero."; return }
            if let entry = existingFuel { entry.date = date; entry.litres = volume; entry.cost = expense; entry.odometerKm = odometer; entry.fullTank = full }
            else { context.insert(FuelEntry(vehicleID:vehicle.id,date:date,litres:volume,cost:expense,odometerKm:odometer,fullTank:full)) }
        } else {
            guard !title.trimmingCharacters(in:.whitespaces).isEmpty else { error = "Enter a service title."; return }
            let next = nextKm.isEmpty ? 0 : Double(nextKm) ?? -1
            guard next.isFinite, next >= 0, next <= 9_000_000_000_000_000 else { error = "Enter a valid next service reading."; return }
            let entry = existingService ?? ServiceEntry(vehicleID:vehicle.id,title:title,date:date,odometerKm:odometer,cost:expense,notes:notes,nextDueKm:next,nextDueDate:hasDate ? nextDate : nil)
            entry.title = title; entry.date = date; entry.odometerKm = odometer; entry.cost = expense; entry.notes = notes; entry.nextDueKm = next; entry.nextDueDate = hasDate ? nextDate : nil
            if existingService == nil { context.insert(entry) }
        }
        vehicle.odometerKm = max(vehicle.odometerKm,odometer)
        do {
            try context.save()
            if let service = existingService { RideReminders.cancel(service.id) }
            Task {
                if !fuel {
                    let matches = try? context.fetch(FetchDescriptor<ServiceEntry>())
                    if let entry = matches?.first(where:{ $0.vehicleID == vehicle.id && $0.title == title && $0.date == date }), let due = entry.nextDueDate {
                        try? await RideReminders.schedule(id:entry.id,title:vehicle.name,body:"\(entry.title) is due",due:due)
                    }
                }
            }
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}

struct VehicleDocumentsView: View {
    let vehicle: Vehicle
    @Environment(\.modelContext) private var context
    @Query(sort:\VehicleDocument.title) private var records: [VehicleDocument]
    @State private var adding = false
    @State private var editing: VehicleDocument?
    @State private var preview: URL?
    @State private var error: String?
    private var documents: [VehicleDocument] { records.filter { $0.vehicleID == vehicle.id } }
    var body: some View {
        List {
            ForEach(documents) { document in
                VStack(alignment:.leading,spacing:5) {
                    Text(document.title).font(.headline)
                    Text("\(document.kind) · \(document.number)").font(.caption).foregroundStyle(.secondary)
                    if let expiry = document.expiry { Text("Expires \(expiry.formatted(date:.abbreviated,time:.omitted))").foregroundStyle(expiry < Date() ? Color.orange : Color.secondary).font(.caption) }
                    if let attachment = document.attachment {
                        Button("Open attachment",systemImage:"doc") {
                            do {
                                let ext = (document.filename as NSString).pathExtension
                                let url = FileManager.default.temporaryDirectory.appendingPathComponent(document.id.uuidString).appendingPathExtension(ext)
                                try attachment.write(to:url,options:[.atomic,.completeFileProtection]); preview = url
                            } catch { self.error = error.localizedDescription }
                        }
                    }
                }.swipeActions(edge:.leading) { Button("Edit") { editing = document } }
            }.onDelete { offsets in
                for offset in offsets { RideReminders.cancel(documents[offset].id); context.delete(documents[offset]) }
                do { try context.save() } catch { self.error = error.localizedDescription }
            }
            if documents.isEmpty { Text("Keep insurance, registration, licence, PUC and service invoices here.").foregroundStyle(.secondary) }
        }.navigationTitle("Documents").toolbar { Button("Add",systemImage:"plus") { adding = true } }
            .sheet(isPresented:$adding) { DocumentEditor(vehicle:vehicle) }
            .sheet(item:$editing) { document in DocumentEditor(vehicle:vehicle,existing:document) }
            .quickLookPreview($preview)
            .alert("Documents",isPresented:Binding(get:{ error != nil },set:{ if !$0 { error = nil } })) { Button("OK") { error = nil } } message: { Text(error ?? "") }
    }
}
struct DocumentEditor: View {
    let vehicle: Vehicle
    var existing: VehicleDocument? = nil
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var kind = "Insurance"
    @State private var number = ""
    @State private var hasExpiry = true
    @State private var expiry = Date().addingTimeInterval(365 * 86400)
    @State private var filename = ""
    @State private var attachment: Data?
    @State private var importing = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                TextField("Title",text:$title)
                Picker("Type",selection:$kind) { ForEach(["Insurance","Registration","Licence","PUC","Invoice","Warranty","Other"],id:\.self) { Text($0) } }
                TextField("Document number",text:$number)
                Toggle("Expiry date",isOn:$hasExpiry)
                if hasExpiry { DatePicker("Expires",selection:$expiry,displayedComponents:.date) }
                Button(filename.isEmpty ? "Attach PDF or image" : filename,systemImage:"paperclip") { importing = true }
                if attachment != nil { Button("Remove attachment",role:.destructive) { attachment = nil; filename = "" } }
                if let error { Text(error).foregroundStyle(.red) }
            }.navigationTitle("Vehicle document").toolbar {
                ToolbarItem(placement:.cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement:.confirmationAction) { Button("Save") { save() }.disabled(title.trimmingCharacters(in:.whitespaces).isEmpty) }
            }.onAppear { if let existing { title = existing.title; kind = existing.kind; number = existing.number; hasExpiry = existing.expiry != nil; expiry = existing.expiry ?? Date(); filename = existing.filename; attachment = existing.attachment } }
                .fileImporter(isPresented:$importing,allowedContentTypes:[.pdf,.image]) { result in
                    do {
                        let url = try result.get(); let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                        let size = try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
                        guard size <= 20_000_000 else { throw WireClientError("Attachments must be under 20 MB") }
                        attachment = try BoundedFile.read(url,limit:20000000); filename = url.lastPathComponent
                    } catch { self.error = error.localizedDescription }
                }
        }
    }
    private func save() {
        let document = existing ?? VehicleDocument(vehicleID:vehicle.id,title:title,kind:kind,number:number,expiry:hasExpiry ? expiry : nil,filename:filename,attachment:attachment)
        document.title = title; document.kind = kind; document.number = number; document.expiry = hasExpiry ? expiry : nil; document.filename = filename; document.attachment = attachment
        if existing == nil { context.insert(document) }
        do {
            try context.save(); RideReminders.cancel(document.id)
            Task { if let due = document.expiry { try? await RideReminders.schedule(id:document.id,title:vehicle.name,body:"\(document.title) expires soon",due:due,daysBefore:30) } }
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}

struct ExpensesView: View {
    let vehicle: Vehicle
    @Environment(\.modelContext) private var context
    @Query(sort:\ExpenseEntry.date,order:.reverse) private var all: [ExpenseEntry]
    @State private var adding = false
    @State private var editing: ExpenseEntry?
    private var entries: [ExpenseEntry] { all.filter { $0.vehicleID == vehicle.id } }
    var body: some View {
        List {
            LabeledContent("Other expenses",value:entries.reduce(0) { $0 + $1.amount }.formatted())
            Text("Fuel and service costs remain in their own logs; this total covers other expenses only.").font(.caption).foregroundStyle(.secondary)
            ForEach(entries) { entry in
                Button { editing = entry } label: {
                    VStack(alignment:.leading) { Text("\(entry.category) · \(entry.amount.formatted())"); Text("\(entry.date.formatted(date:.abbreviated,time:.omitted)) · \(entry.notes)").font(.caption).foregroundStyle(.secondary) }
                }
            }.onDelete { indexes in for index in indexes { context.delete(entries[index]) }; try? context.save() }
        }.navigationTitle("Expenses").toolbar { Button("Add",systemImage:"plus") { adding = true } }
            .sheet(isPresented:$adding) { ExpenseEditor(vehicle:vehicle) }
            .sheet(item:$editing) { ExpenseEditor(vehicle:vehicle,existing:$0) }
    }
}
struct ExpenseEditor: View {
    let vehicle: Vehicle
    var existing: ExpenseEntry? = nil
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var date = Date()
    @State private var category = "Accessories"
    @State private var amount = ""
    @State private var notes = ""
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                DatePicker("Date",selection:$date,displayedComponents:.date)
                Picker("Category",selection:$category) { ForEach(["Accessories","Repairs","Tyres","Insurance","Parking","Tolls","Cleaning","Other"],id:\.self) { Text($0) } }
                TextField("Amount",text:$amount).keyboardType(.decimalPad)
                TextField("Notes",text:$notes,axis:.vertical)
                if let error { Text(error).foregroundStyle(.red) }
            }.navigationTitle("Expense").toolbar {
                ToolbarItem(placement:.cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement:.confirmationAction) { Button("Save") {
                    guard let value = Double(amount), value.isFinite, value >= 0 else { error = "Enter a valid amount"; return }
                    let entry = existing ?? ExpenseEntry(vehicleID:vehicle.id,date:date,category:category,amount:value,notes:notes)
                    entry.date = date; entry.category = category; entry.amount = value; entry.notes = notes
                    if existing == nil { context.insert(entry) }
                    do { try context.save(); dismiss() } catch { self.error = error.localizedDescription }
                } }
            }.onAppear { if let existing { date = existing.date; category = existing.category; amount = String(existing.amount); notes = existing.notes } }
        }
    }
}

struct MaintenanceRulesView: View {
    let vehicle:Vehicle
    @Environment(\.modelContext) private var context
    @Query(sort:\MaintenanceRule.name) private var all:[MaintenanceRule]
    @State private var adding = false
    @State private var error:String?
    private var rules:[MaintenanceRule] { all.filter { $0.vehicleID == vehicle.id } }
    var body: some View {
        List {
            ForEach(rules) { rule in
                VStack(alignment:.leading,spacing:7) {
                    Text(rule.name).font(.headline)
                    if rule.completed { Text("Completed").foregroundStyle(.secondary) }
                    else {
                        if let km = rule.dueKm { Text(km <= vehicle.odometerKm ? "Due now · \(Int(km)) km" : "Due at \(Int(km)) km").foregroundStyle(km <= vehicle.odometerKm ? Color.orange : Color.secondary) }
                        if let due = rule.dueDate { Text("Due \(due.formatted(date:.abbreviated,time:.omitted))").foregroundStyle(due < Date() ? Color.orange : Color.secondary) }
                        Button("Mark serviced at current odometer") {
                            let now = Date()
                            context.insert(ServiceEntry(vehicleID:vehicle.id,title:rule.name,date:now,odometerKm:vehicle.odometerKm,cost:0,notes:"Completed scheduled maintenance",nextDueKm:0,nextDueDate:nil))
                            if rule.scheduled { rule.completed = true } else { rule.lastKm = vehicle.odometerKm; rule.lastDate = now }
                            do {
                                try context.save(); RideReminders.cancel(rule.id)
                                if !rule.completed, let due = rule.dueDate { Task { try? await RideReminders.schedule(id:rule.id,title:vehicle.name,body:"\(rule.name) is due",due:due) } }
                            } catch { self.error = error.localizedDescription }
                        }
                    }
                }
            }.onDelete { indexes in for index in indexes { RideReminders.cancel(rules[index].id); context.delete(rules[index]) }; do { try context.save() } catch { self.error = error.localizedDescription } }
            Text("Service is due by distance OR date, whichever comes first. Enter intervals from your motorcycle manual. Imported Android schedules retain their completion state.").font(.caption).foregroundStyle(.secondary)
        }.navigationTitle("Maintenance schedule").toolbar { Button("Add",systemImage:"plus") { adding = true } }
            .sheet(isPresented:$adding) { MaintenanceRuleEditor(vehicle:vehicle) }
            .alert("Maintenance",isPresented:Binding(get:{ error != nil },set:{ if !$0 { error = nil } })) { Button("OK") { error = nil } } message: { Text(error ?? "") }
    }
}
struct MaintenanceRuleEditor: View {
    let vehicle:Vehicle
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var km = ""
    @State private var months = ""
    @State private var lastKm = ""
    @State private var lastDate = Date()
    @State private var error:String?
    var body: some View {
        NavigationStack {
            Form {
                TextField("Maintenance item",text:$name)
                TextField("Distance interval (km), 0 for time only",text:$km).keyboardType(.decimalPad)
                TextField("Month interval, 0 for distance only",text:$months).keyboardType(.numberPad)
                TextField("Last serviced at km",text:$lastKm).keyboardType(.decimalPad)
                DatePicker("Last serviced",selection:$lastDate,in:...Date(),displayedComponents:.date)
                if let error { Text(error).foregroundStyle(.red) }
            }.navigationTitle("Recurring maintenance").onAppear { lastKm = String(vehicle.odometerKm) }
                .toolbar {
                    ToolbarItem(placement:.cancellationAction) { Button("Cancel") { dismiss() } }
                    ToolbarItem(placement:.confirmationAction) { Button("Save") {
                        guard let distance = Double(km), distance.isFinite, distance >= 0, distance <= 9_000_000_000_000_000, let interval = Int(months), (0...1200).contains(interval), distance > 0 || interval > 0,
                              let odo = Double(lastKm), odo.isFinite, odo >= 0, odo <= 9_000_000_000_000_000, !name.trimmingCharacters(in:.whitespaces).isEmpty else { error = "Enter a name, valid last reading and at least one interval"; return }
                        let rule = MaintenanceRule(vehicleID:vehicle.id,name:name,intervalKm:distance,intervalMonths:interval,lastKm:odo,lastDate:lastDate)
                        context.insert(rule)
                        do { try context.save(); if let due = rule.dueDate { Task { try? await RideReminders.schedule(id:rule.id,title:vehicle.name,body:"\(rule.name) is due",due:due) } }; dismiss() }
                        catch { self.error = error.localizedDescription }
                    } }
                }
        }
    }
}
