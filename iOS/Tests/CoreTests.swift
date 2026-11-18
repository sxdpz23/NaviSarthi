import XCTest
@testable import NaviSarthiCore

final class CoreTests: XCTestCase {
    func testMediaEntryDoesNotToggleAndExitIsExact() {
        XCTAssertNil(ConsoleMediaWire.command(0x18))
        XCTAssertEqual(ConsoleMediaWire.command(0x05),.toggle)
        XCTAssertTrue(ConsoleMediaWire.navigationExit(TLV(9,0,[0x12])))
        XCTAssertFalse(ConsoleMediaWire.navigationExit(TLV(9,0,[1,0x12])))
        XCTAssertEqual(ConsoleMediaWire.nowPlaying(album:"Album",title:"Song",artist:"Artist"),Array("Album\0Song\0Artist".utf8))
    }
    func testSharedLocationDoesNotRouteToMapViewport() throws {
        XCTAssertEqual(try SharedDestination.parse("https://www.google.com/maps/place/Tea+Shop/@12,77,15z"),.query("Tea Shop"))
        XCTAssertEqual(try SharedDestination.parse("https://maps.apple.com/?ll=12.5,77.2"),.coordinate(Coordinate(12.5,77.2)))
        XCTAssertEqual(try SharedDestination.parse("Meet here https://maps.app.goo.gl/abcd"),.shortLink(URL(string:"https://maps.app.goo.gl/abcd")!))
        XCTAssertThrowsError(try SharedDestination.parse("https://evil.example/?q=12,77"))
        XCTAssertEqual(try SharedDestination.parse("navisarthi://nav?q=12,77"),.coordinate(Coordinate(12,77)))
    }
    func testThreeWaySyncProtectsConcurrentEditsAndDeletions() {
        XCTAssertEqual(SyncPolicy.decide(local:"new",remote:"old",baseline:"old",remoteVersion:"1",baselineVersion:"1"),.upload)
        XCTAssertEqual(SyncPolicy.decide(local:"old",remote:"new",baseline:"old",remoteVersion:"2",baselineVersion:"1"),.download)
        XCTAssertEqual(SyncPolicy.decide(local:"mine",remote:"theirs",baseline:"old",remoteVersion:"2",baselineVersion:"1"),.conflict)
        XCTAssertEqual(SyncPolicy.decide(local:nil,remote:"old",baseline:"old",remoteVersion:"1",baselineVersion:"1"),.deleteRemote)
        XCTAssertEqual(SyncPolicy.decide(local:"old",remote:nil,baseline:"old",remoteVersion:nil,baselineVersion:"1"),.deleteLocal)
        XCTAssertEqual(SyncPolicy.decide(local:"edited",remote:nil,baseline:"old",remoteVersion:nil,baselineVersion:"1"),.conflict)
        XCTAssertEqual(SyncPolicy.decide(local:"same",remote:"same",baseline:"old",remoteVersion:"2",baselineVersion:"1"),.unchanged)
    }
    func testOfflineDirectedRoadsAndTurnRestrictions() throws {
        let nodes = [RoadNode(id:1,coordinate:Coordinate(0,0)),RoadNode(id:2,coordinate:Coordinate(0,0.001)),RoadNode(id:3,coordinate:Coordinate(0.001,0.001)),RoadNode(id:4,coordinate:Coordinate(-0.001,0.001))]
        let edges = [RoadEdge(id:0,from:1,to:2,meters:112,speedKph:40),RoadEdge(id:1,from:2,to:3,meters:112,speedKph:40),RoadEdge(id:2,from:2,to:4,meters:112,speedKph:40),RoadEdge(id:3,from:4,to:3,meters:224,speedKph:40)]
        let pack = RoadPack(title:"Fixture",attribution:"Test",nodes:nodes,edges:edges,restrictions:[RoadRestriction(fromEdge:0,toEdge:1,only:false)])
        let router = try OfflineRouter(pack)
        XCTAssertEqual(try router.route(from:nodes[0].coordinate,to:nodes[2].coordinate).edges.map(\.id),[0,2,3])
        XCTAssertThrowsError(try router.route(from:nodes[2].coordinate,to:nodes[0].coordinate))
    }
    func testOSMImporterExcludesPrivateAndRespectsOneWay() throws {
        let xml = "<osm><node id=\"1\" lat=\"0\" lon=\"0\"/><node id=\"2\" lat=\"0\" lon=\"0.001\"/><node id=\"3\" lat=\"0\" lon=\"0.002\"/><way id=\"10\"><nd ref=\"1\"/><nd ref=\"2\"/><tag k=\"highway\" v=\"residential\"/><tag k=\"oneway\" v=\"yes\"/></way><way id=\"11\"><nd ref=\"2\"/><nd ref=\"3\"/><tag k=\"highway\" v=\"service\"/><tag k=\"access\" v=\"private\"/></way></osm>"
        let pack = try OSMRoadImporter.read(Data(xml.utf8),title:"Fixture")
        XCTAssertEqual(pack.edges.count,1)
        XCTAssertEqual(pack.edges[0].from,1)
        XCTAssertEqual(pack.edges[0].to,2)
        XCTAssertEqual(pack.nodes.count,2)
    }
    func testFuelPartialFillsAndUnfinishedTank() {
        let samples = [FuelSample(odometer:100,litres:10,full:true), FuelSample(odometer:200,litres:3,full:false), FuelSample(odometer:400,litres:7,full:true), FuelSample(odometer:450,litres:4,full:false)]
        XCTAssertEqual(FuelEconomy.kilometersPerLitre(samples)!,30,accuracy:0.001)
    }
    func testGPXRejectsInvalidAndSeparateSegments() throws {
        XCTAssertThrowsError(try GPXReader.read(Data("<gpx><trkpt lat=\"nan\" lon=\"1\"/></gpx>".utf8)))
        XCTAssertThrowsError(try GPXReader.read(Data("<gpx><trkseg><trkpt lat=\"1\" lon=\"1\"/></trkseg><trkseg><trkpt lat=\"2\" lon=\"2\"/></trkseg></gpx>".utf8)))
        XCTAssertEqual(try GPXReader.read(Data("<gpx><rte><rtept lat=\"1\" lon=\"1\"/><rtept lat=\"2\" lon=\"2\"/></rte></gpx>".utf8)).count,2)
    }
    func testMatcherComputesDistanceAlongRoute() {
        var matcher = RouteMatcher(points:[Coordinate(0,0),Coordinate(0,0.01),Coordinate(0.01,0.01)])
        let result = matcher.match(Coordinate(0.00001,0.005),course:90)!
        XCTAssertEqual(result.alongMeters,556,accuracy:2)
        XCTAssertLessThan(result.crossTrackMeters,2)
        XCTAssertEqual(matcher.length,2224,accuracy:2)
    }
    func testPolylinePrecisionAndTruncation() throws {
        let points = try Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@",precision:5)
        XCTAssertEqual(points.count,3)
        XCTAssertEqual(points[0].latitude,38.5,accuracy:0.00001)
        XCTAssertEqual(points[2].longitude,-126.453,accuracy:0.00001)
        XCTAssertThrowsError(try Polyline.decode("_"))
        XCTAssertThrowsError(try Polyline.decode("\u{1}"))
        XCTAssertEqual(try Polyline.encode(points,precision:5),"_p~iF~ps|U_ulLnnqC_mqNvxq`@")
    }
    func testAuthRequestMatchesAndroid() throws {
        XCTAssertEqual(try K1G.build([TLV(8, 4, [1])]), [0,22,0,2,0,0,0,0,2,1,0,5,75,49,71,32,0,8,4,0,1,1])
    }
    func testIncomingRejectsTruncatedTLV() {
        XCTAssertThrowsError(try K1G.incoming([0,13,0,1,0,0,0,0,7,1,0,2,1]))
    }
    func testSeparateAuthResponses() throws {
        XCTAssertEqual(try K1G.incoming([0,13,0,1,0,0,0,0,7,1,0,1,1]), [TLV(7,1,[1])])
    }
    func testFragmentationAndSequenceWrap() {
        var rtp = RTPPacketizer(sequence: 65535, ssrc: 1)
        let nal: [UInt8] = [0x65] + Array(repeating: 42, count: 3000)
        let packets = rtp.packets(nals: [nal], timestamp: 90000)
        XCTAssertEqual(packets.count, 3)
        XCTAssertEqual(packets[0][13], 0x85)
        XCTAssertEqual(packets[2][13], 0x45)
        XCTAssertEqual(packets.map { $0[1] }, [0x60, 0x60, 0xe0])
        XCTAssertEqual(Array(packets[1][2...3]), [0, 0])
        XCTAssertTrue(packets.allSatisfy { $0.count <= 1392 })
        XCTAssertEqual(packets.flatMap { Array($0.dropFirst(14)) }, Array(nal.dropFirst()))
    }
    func testMarkerOnlyOnLastNAL() {
        var rtp = RTPPacketizer(sequence: 0, ssrc: 1)
        XCTAssertEqual(rtp.packets(nals: [[0x67], [0x68], [0x65]], timestamp: 1).map { $0[1] }, [0x60,0x60,0xe0])
    }
    func testPodCRCAndPIN() throws {
        XCTAssertEqual(PodWire.crc(Array("123456789".utf8)), 0x29b1)
        XCTAssertTrue(PodWire.valid(try PodWire.pin("123456")))
        XCTAssertThrowsError(try PodWire.pin("１２３４５６"))
        var bytes = try PodWire.packet([0x21, 1]); bytes[3] ^= 1
        XCTAssertFalse(PodWire.valid(bytes))
    }
    func testDistance() {
        XCTAssertEqual(Coordinate(0,0).distance(to: Coordinate(0,1)), 111195, accuracy: 1)
        XCTAssertFalse(Coordinate(.nan, 0).isValid)
    }
}
