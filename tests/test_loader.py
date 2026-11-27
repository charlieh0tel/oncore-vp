"""Tests for tools/loader/dump.py: loader framing and reply parsing."""

import pytest

import dump


class ScriptPort:
    """Records writes; answers each write with the next scripted reply."""

    def __init__(self, replies):
        self.replies = list(replies)
        self.written = []
        self.pending = b""

    def write(self, data):
        self.written.append(data)
        if self.replies:
            self.pending += self.replies.pop(0)

    def read(self, n):
        out, self.pending = self.pending[:n], self.pending[n:]
        return out

    def reset_input_buffer(self):
        self.pending = b""


def reply(letters, status=0):
    seed = letters[0] ^ letters[1]
    return b"@@" + letters + bytes([status, seed ^ status]) + b"\r\n"


def test_loader_frame_sa():
    f = dump.loader_frame(b"a", b"\xc0\x00", b"\x4e\x75")
    body = b"\x05\xc0\x00\x4e\x75"
    assert f == b"@@Sa" + body + bytes([0x32 ^ 0x05 ^ 0xC0 ^ 0x00 ^ 0x4E ^ 0x75]) + b"\r\n"


def test_loader_frame_sd_and_sg():
    assert dump.loader_frame(b"d", b"") == b"@@Sd\x01" + bytes([ord("S") ^ ord("d") ^ 1]) + b"\r\n"
    assert dump.loader_frame(b"g", b"\xc0\x00")[4:7] == b"\x03\xc0\x00"


def test_stub_parameters_at_documented_offsets():
    assert len(dump.STUB) == 0x48
    assert dump.STUB[0x40:] == bytes(8)  # BASE and LENGTH, patched per run


def test_write_chunks_and_addresses():
    port = ScriptPort([reply(b"Sa")] * 3)
    dump.Loader(port).write(0xC000, bytes(range(130)), chunk=64)
    assert [w[5:7] for w in port.written] == [b"\xc0\x00", b"\xc0\x40", b"\xc0\x80"]
    assert [w[4] for w in port.written] == [67, 67, 5]


def test_write_raises_on_rejected_frame():
    port = ScriptPort([reply(b"Sa", status=1)])
    with pytest.raises(RuntimeError, match="rejected"):
        dump.Loader(port).write(0xC000, b"\x00")


def test_enter_detects_loader_already_running():
    port = ScriptPort([reply(b"Sd")])
    assert dump.Loader(port).enter() == "already in the loader"
    assert len(port.written) == 1  # no @@Tq sent


def test_enter_sends_tq_when_not_in_loader():
    port = ScriptPort([b"", reply(b"Tq")])
    assert dump.Loader(port).enter() == "entered the loader"
    assert port.written[1] == b"@@Tq%\r\n"
