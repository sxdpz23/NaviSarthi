import Foundation
import Combine
import CoreBluetooth
import NaviSarthiCore

struct PodDevice: Identifiable { var id:UUID; var name:String; var rssi:Int }

@MainActor final class PodConnection: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    @Published var status = "Disconnected"
    @Published var needsPIN = false
    @Published var ready = false
    @Published var devices:[PodDevice] = []
    @Published var reconnecting = false
    private var discovered:[UUID:CBPeripheral] = [:]
    private var wantsConnection = false
    private var reconnectAttempts = 0
    private var reconnectTask:Task<Void,Never>?
    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var uart: CBCharacteristic?
    private var queue: [(data:Data,guidance:Bool)] = []
    private var writing = false
    private var scanTimeout: Timer?
    private var authTimeout: Timer?
    private let service = CBUUID(string: PodWire.service)
    override init() { super.init(); central = CBCentralManager(delegate:self,queue:.main,options:[CBCentralManagerOptionRestoreIdentifierKey:"com.navisarthi.pod.central"]) }
    func connect() {
        guard central.state == .poweredOn else { status = "Enable Bluetooth and allow access in Settings"; return }
        disconnect(); wantsConnection = true; reconnectAttempts = 0; beginConnection()
    }
    private func beginConnection() {
        if let saved = UserDefaults.standard.string(forKey:"pairedPod"), let id = UUID(uuidString:saved), let cached = central.retrievePeripherals(withIdentifiers:[id]).first {
            select(cached); return
        }
        status = "Scanning for Tripper Pod…"; devices = []; discovered = [:]
        central.scanForPeripherals(withServices: [service], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        scanTimeout = Timer.scheduledTimer(withTimeInterval: 15, repeats: false) { [weak self] _ in
            Task { @MainActor in
                self?.central.stopScan()
                self?.status = self?.devices.isEmpty == false ? "Select your Pod below" : "No Pod found. Enable pairing on your bike."
            }
        }
    }
    func choose(_ id:UUID) { if let device = discovered[id] { select(device) } }
    private func select(_ device:CBPeripheral) {
        central.stopScan(); scanTimeout?.invalidate(); peripheral = device; device.delegate = self
        status = "Connecting to \(device.name ?? "Pod")…"; central.connect(device)
        authTimeout?.invalidate()
        authTimeout = Timer.scheduledTimer(withTimeInterval:20,repeats:false) { [weak self] _ in
            Task { @MainActor in
                guard let self, !self.ready, self.peripheral?.identifier == device.identifier else { return }
                self.disconnect(manual:false); self.status = "Pod connection timed out"; self.scheduleReconnect()
            }
        }
    }
    func disconnect(manual:Bool = true) {
        if manual { wantsConnection = false; reconnectAttempts = 0; reconnectTask?.cancel(); reconnectTask = nil; reconnecting = false }
        central.stopScan(); scanTimeout?.invalidate(); authTimeout?.invalidate()
        if let peripheral { central.cancelPeripheralConnection(peripheral) }
        peripheral = nil; uart = nil; queue = []; writing = false; ready = false; needsPIN = false; status = "Disconnected"
    }
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state != .poweredOn { ready = false; status = "Bluetooth unavailable" }
        else if wantsConnection { beginConnection() }
    }
    func centralManager(_ central:CBCentralManager,willRestoreState dict:[String:Any]) {
        guard let restored = (dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral])?.first else { return }
        peripheral = restored; restored.delegate = self; wantsConnection = true
        if restored.state == .connected { restored.discoverServices([service]) } else { central.connect(restored) }
    }
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard self.peripheral == nil else { return }
        discovered[peripheral.identifier] = peripheral
        let row = PodDevice(id:peripheral.identifier,name:peripheral.name ?? "Tripper Pod",rssi:RSSI.intValue)
        if let index = devices.firstIndex(where:{ $0.id == row.id }) { devices[index] = row } else { devices.append(row) }
        if UserDefaults.standard.string(forKey:"pairedPod") == peripheral.identifier.uuidString { select(peripheral) }
    }
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        authTimeout?.invalidate()
        status = "Discovering Pod service…"; peripheral.discoverServices([service])
        authTimeout = Timer.scheduledTimer(withTimeInterval: 30, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self, !self.ready else { return }
                self.disconnect(); self.status = "Pod authorization timed out. Try pairing again."
            }
        }
    }
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        disconnect(manual:false); status = error?.localizedDescription ?? "Pod connection failed"; scheduleReconnect()
    }
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        guard self.peripheral?.identifier == peripheral.identifier else { return }
        disconnect(manual:false); if let error { status = error.localizedDescription }; scheduleReconnect()
    }
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil, let found = peripheral.services?.first(where: { $0.uuid == service }) else { status = error?.localizedDescription ?? "Pod service missing"; return }
        peripheral.discoverCharacteristics([CBUUID(string: PodWire.characteristic)], for: found)
    }
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard error == nil, let found = service.characteristics?.first(where: { $0.uuid == CBUUID(string: PodWire.characteristic) }) else { status = error?.localizedDescription ?? "Pod UART missing"; return }
        uart = found; peripheral.setNotifyValue(true, for: found)
    }
    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        guard error == nil, characteristic.isNotifying else { status = error?.localizedDescription ?? "Pod notifications unavailable"; return }
        let known = UserDefaults.standard.string(forKey: "pairedPod") == peripheral.identifier.uuidString
        enqueue(try? PodWire.packet([0x21, known ? 0 : 1])); needsPIN = !known; status = known ? "Authorizing…" : "Enter the six-digit PIN shown on the Pod"
    }
    func authorize(_ pin: String) {
        do { enqueue(try PodWire.pin(pin)); status = "Checking PIN…" }
        catch { status = "PIN must contain six digits" }
    }
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard error == nil, let value = characteristic.value else { return }
        let packet = [UInt8](value)
        guard PodWire.valid(packet), packet[0] == 0x20 else { return }
        ready = packet[1] == 1; needsPIN = !ready
        if ready {
            status = "Connected to Pod"; authTimeout?.invalidate()
            reconnecting = false; reconnectAttempts = 0
            UserDefaults.standard.set(peripheral.identifier.uuidString, forKey: "pairedPod")
            let cal = Calendar.current
            enqueue(try? PodWire.packet([0x50, UInt8(cal.component(.hour, from: Date())), UInt8(cal.component(.minute, from: Date()))]))
        } else { status = "PIN rejected. Try again." }
    }
    func guidance(turn: Double, remaining: Double, glyph: UInt8 = 0xff) {
        guard ready else { return }
        // Unknown maneuver glyph rather than an invented turn: MapKit has no public maneuver enum.
        let packet = try? PodWire.guidance(icon: glyph, turnMeters: turn, remainingMeters: remaining, night: true)
        queue.removeAll { $0.guidance }
        enqueue(packet,guidance:true)
    }
    private func enqueue(_ bytes: [UInt8]?,guidance:Bool = false) {
        guard let bytes else { return }; queue.append((Data(bytes),guidance)); flush()
    }
    private func flush() {
        guard !writing, let peripheral, let uart, !queue.isEmpty else { return }
        if uart.properties.contains(.write) {
            writing = true; peripheral.writeValue(queue.removeFirst().data, for: uart, type: .withResponse)
        } else if uart.properties.contains(.writeWithoutResponse), peripheral.canSendWriteWithoutResponse {
            peripheral.writeValue(queue.removeFirst().data, for: uart, type: .withoutResponse); flush()
        }
    }
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        writing = false
        if let error { queue = []; status = error.localizedDescription } else { flush() }
    }
    func peripheralIsReady(toSendWriteWithoutResponse peripheral: CBPeripheral) { flush() }
    private func scheduleReconnect() {
        guard wantsConnection, reconnectAttempts < 5 else { reconnecting = false; return }
        reconnectAttempts += 1; reconnecting = true
        let delay = pow(2,Double(reconnectAttempts - 1))
        status = "Pod disconnected · retry \(reconnectAttempts) of 5"
        reconnectTask?.cancel()
        reconnectTask = Task { [weak self] in
            do { try await Task.sleep(for:.seconds(delay)) } catch { return }
            guard let self, self.wantsConnection, self.central.state == .poweredOn else { return }
            self.beginConnection()
        }
    }
}
