import SwiftUI
import SwiftData
import MapKit
import UniformTypeIdentifiers

struct GPXDocument: FileDocument {
    static var readableContentTypes: [UTType] { [UTType(filenameExtension:"gpx") ?? .xml] }
    var text: String
    init(text: String) { self.text = text }
    init(configuration:ReadConfiguration) throws { text = String(data:configuration.file.regularFileContents ?? Data(),encoding:.utf8) ?? "" }
    func fileWrapper(configuration:WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents:Data(text.utf8)) }
}
struct RidesView: View {
    @Environment(\.modelContext) private var context
    @EnvironmentObject private var nav: NavigationSession
    @EnvironmentObject private var settings: Settings
    @Query(sort:\RecordedRide.started,order:.reverse) private var rides: [RecordedRide]
    var body: some View {
        List {
            Section {
                if nav.recording {
                    Button("Stop and save recording",role:.destructive) { nav.stopRecording(context:context) }.disabled(nav.active)
                    if nav.active { Text("End navigation in Nav to save this ride.").font(.caption) }
                } else { Button("Record a ride",systemImage:"record.circle") { nav.startRecording() } }
            }
            if rides.isEmpty { ContentUnavailableView("No rides yet",systemImage:"point.topleft.down.to.point.bottomright.curvepath",description:Text("Navigation automatically records your ride. You can also record without a route.")) }
            ForEach(rides) { ride in
                NavigationLink {
                    RideDetailView(ride:ride)
                } label: {
                    VStack(alignment:.leading,spacing:5) {
                        Text(ride.started.formatted(date:.abbreviated,time:.shortened)).font(.headline)
                        Text("\(settings.distance(ride.distanceMeters)) · \(Int(ride.duration / 60)) min").foregroundStyle(.secondary)
                    }
                }
            }.onDelete { indexes in
                for index in indexes { context.delete(rides[index]) }
                do { try context.save() } catch { nav.error = error.localizedDescription }
            }
        }.navigationTitle("Rides")
    }
}
struct RideDetailView: View {
    let ride: RecordedRide
    @EnvironmentObject private var settings: Settings
    @State private var exporting = false
    @State private var error: String?
    var body: some View {
        ScrollView {
            VStack(alignment:.leading,spacing:20) {
                Map {
                    MapPolyline(coordinates:ride.fixes.map { .init(latitude:$0.latitude,longitude:$0.longitude) }).stroke(.blue,lineWidth:5)
                }.frame(height:320).clipShape(RoundedRectangle(cornerRadius:24))
                LabeledContent("Distance",value:settings.distance(ride.distanceMeters))
                LabeledContent("Duration",value:"\(Int(ride.duration / 60)) min")
                LabeledContent("Average speed",value:speed(ride.distanceMeters / max(1,ride.duration)))
                LabeledContent("Maximum speed",value:speed(ride.maxSpeedMps))
                Button("Export GPX",systemImage:"square.and.arrow.up") { exporting = true }.buttonStyle(.borderedProminent)
            }.padding(20)
        }.navigationTitle(ride.started.formatted(date:.abbreviated,time:.omitted))
            .fileExporter(isPresented:$exporting,document:GPXDocument(text:ride.gpx),contentType:UTType(filenameExtension:"gpx") ?? .xml,defaultFilename:"NaviSarthi-\(ride.id.uuidString).gpx") { result in
                if case .failure(let failure) = result { error = failure.localizedDescription }
            }
            .alert("Export",isPresented:Binding(get:{ error != nil },set:{ if !$0 { error = nil } })) { Button("OK") { error = nil } } message: { Text(error ?? "") }
    }
    private func speed(_ mps: Double) -> String { String(format:"%.1f %@",mps * (settings.miles ? 2.236936 : 3.6),settings.miles ? "mph" : "km/h") }
}
