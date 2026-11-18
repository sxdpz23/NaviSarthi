import XCTest
@testable import NaviSarthiCore

final class CoreTests: XCTestCase {
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
