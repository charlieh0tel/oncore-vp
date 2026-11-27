"""Tests for tools/oncore.py: framing, decoding and bit names."""

import struct

import oncore


class FakePort:
    """Returns the given bytes in chunks on read(), then nothing."""

    def __init__(self, data, chunk=7):
        self.data = data
        self.chunk = chunk

    def read(self, n):
        out, self.data = self.data[: min(n, self.chunk)], self.data[min(n, self.chunk) :]
        return out


def ea_payload(status=0x20, chans=8):
    """An @@Ea/@@Ba-shaped payload: date, time, position, counts, channels, status."""
    p = bytearray(36 + 4 * chans + 1)
    p[0:4] = bytes([11, 27, 2026 >> 8, 2026 & 0xFF])
    p[4:7] = bytes([18, 22, 55])
    p[11:23] = struct.pack(">iii", int(37.5 * 3.6e6), int(-122.25 * 3.6e6), 1270)
    p[34], p[35] = 9, 5
    for k in range(chans):
        p[36 + 4 * k : 40 + 4 * k] = bytes([k + 1, 8, 40 + k, 0x80 if k < 5 else 0])
    p[36 + 4 * chans] = status
    return bytes(p)


def test_frame_checksum_is_xor_of_letters_and_payload():
    f = oncore.frame(b"Cg", b"\x01")
    assert f == b"@@Cg\x01" + bytes([ord("C") ^ ord("g") ^ 1]) + b"\r\n"


def test_frame_ta_query_matches_documented_bytes():
    assert oncore.frame(b"Ta", b"\xff" * 5) == bytes.fromhex("40405461ffffffffffca0d0a")


def test_frames_yields_only_good_frames_with_wanted_letters():
    good = oncore.frame(b"Ea", ea_payload())
    bad = bytearray(oncore.frame(b"Ea", ea_payload()))
    bad[-3] ^= 0xFF  # corrupt the checksum
    other = oncore.frame(b"Cg", b"\x01")
    port = FakePort(b"junk" + bytes(bad) + other + good)
    got = list(oncore.frames(port, 0.5, (b"Ea",)))
    assert len(got) == 1
    assert got[0][1] == b"Ea" and got[0][2] == ea_payload()


def test_decode_pos_eight_channel():
    d = oncore.decode_pos(ea_payload())
    assert d["date"] == "11/27/2026" and d["time"] == "18:22:55"
    assert abs(d["lat"] - 37.5) < 1e-9 and abs(d["lon"] + 122.25) < 1e-9 and d["h"] == 12.7
    assert d["vis"] == 9 and d["trk"] == 5 and d["status"] == 0x20
    assert len(d["chans"]) == 8 and d["chans"][0] == (1, 8, 40, 0x80)


def test_decode_pos_six_channel():
    d = oncore.decode_pos(ea_payload(status=0x08, chans=6))
    assert len(d["chans"]) == 6 and d["status"] == 0x08


def test_bits_lists_set_bits_high_first_or_dash():
    assert oncore.bits(0x21, oncore.STATUS) == "3D,no-alm"
    assert oncore.bits(0, oncore.STATUS) == "-"


def test_unpack_selftest_maps_fa_bits_to_ca_order():
    assert oncore.unpack_selftest(0x003F) == 0x003F  # channels 1-6 unchanged
    assert oncore.unpack_selftest(0x00C0) == 0xC000  # Fa bits 6-7 -> channels 7-8
    assert oncore.unpack_selftest(0x0100) == 0x0040  # Fa bit 8 -> 1 kHz interrupt
    assert oncore.unpack_selftest(0x8000) == 0x2000  # Fa bit 15 -> RTC
