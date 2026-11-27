"""Tests for tools/matchdecomp/compare.py: names, relocations, placement."""

import compare
import libsig

BASE = 0x80000
JSR_ABS_L = b"\x4e\xb9"
RTS = b"\x4e\x75"
NOP = b"\x4e\x71"


def make_module(code, publics, relocs=(), ext=None):
    """A one-section module (section 1, "prog") with known bytes except relocated fields."""
    m = libsig.Mod("t")
    m.secs[1] = "prog"
    m.data[1] = bytearray(code)
    m.mask[1] = bytearray(b"\1" * len(code))
    m.relocs[1] = list(relocs)
    for off, _expr, size in relocs:
        m.mask[1][off : off + size] = bytes(size)
    m.val = {n: (1, off) for n, off in publics.items()}
    m.ext = dict(ext or {})
    return m


def flash_with(addr, code, size=0x100):
    """Flash image of FF with code at addr."""
    f = bytearray(b"\xff" * size)
    f[addr - BASE : addr - BASE + len(code)] = code
    return bytes(f)


def test_load_names_order(tmp_path):
    ghidra = tmp_path / "ghidra"
    ghidra.mkdir()
    (ghidra / "OncoreLibNames.java").write_text('        {"0B0E08", "add64", "F"},  // faddsub64\n')
    (ghidra / "names-a.tsv").write_text(
        "# address\tkind\tname\n0A1A22\tF\tmem_copy\tnote\n0008E9\tL\tsettings_dirty\n008FDA+054\tS\tsum\n"
    )
    (ghidra / "names-b.tsv").write_text("0A0000\tX\tgone\n")
    overrides = tmp_path / "names.tsv"
    overrides.write_text("0A1A0E\tF\tmem_copy\tentry\n")
    names = compare.load_names(ghidra, overrides)
    assert names == {"add64": 0x0B0E08, "mem_copy": 0x0A1A0E, "settings_dirty": 0x08E9}


def test_lookup_strips_underscore():
    names = {"sum8": 0x09C7CC, "__fp_status": 0xCC14}
    assert compare.lookup(names, "_sum8") == 0x09C7CC
    assert compare.lookup(names, "__fp_status") == 0xCC14
    assert compare.lookup(names, "_missing") is None


def test_evaluate():
    ext = {3: 0x1234, 4: None}
    assert compare.evaluate(["X3"], 0, {}, ext) == 0x1234
    assert compare.evaluate(["R1", 6, "+"], 0, {1: 0x80100}, ext) == 0x80106
    assert compare.evaluate(["X3", "P", "-"], 0x1000, {}, ext) == 0x234
    assert compare.evaluate(["X4"], 0, {}, ext) is None
    assert compare.evaluate(["R2"], 0, {1: 0}, ext) is None


def test_match_with_checked_relocation():
    code = JSR_ABS_L + (0x0A5696).to_bytes(4, "big") + NOP + RTS
    m = make_module(code, {"_f": 0}, [(2, ["X1"], 4)], {1: "_g"})
    names = {"f": 0x80010, "g": 0x0A5696}
    (r,) = compare.compare_section(flash_with(0x80010, code), BASE, m, 1, names)
    assert (r.name, r.start, r.length, r.bad, r.masked, r.in_place) == ("_f", 0x80010, 10, [], 0, True)


def test_wrong_relocation_target_differs():
    code = JSR_ABS_L + (0x0A5696).to_bytes(4, "big") + RTS
    m = make_module(code, {"_f": 0}, [(2, ["X1"], 4)], {1: "_g"})
    (r,) = compare.compare_section(flash_with(0x80010, code), BASE, m, 1, {"f": 0x80010, "g": 0x0A5698})
    assert r.bad == [2, 3, 4, 5]


def test_unknown_relocation_masked():
    code = JSR_ABS_L + bytes(4) + RTS
    image = JSR_ABS_L + (0x0A5696).to_bytes(4, "big") + RTS
    m = make_module(code, {"_f": 0}, [(2, ["X1"], 4)], {1: "_unnamed"})
    (r,) = compare.compare_section(flash_with(0x80010, image), BASE, m, 1, {"f": 0x80010})
    assert (r.bad, r.masked) == ([], 1)


def test_byte_difference_and_split():
    code = RTS + NOP + RTS
    image = RTS + NOP + NOP
    m = make_module(code, {"_a": 0, "_b": 2})
    a, b = compare.compare_section(flash_with(0x80020, image), BASE, m, 1, {"a": 0x80020})
    assert (a.bad, b.start, b.length, b.bad, b.in_place) == ([], 0x80022, 4, [3], True)


def test_not_contiguous():
    code = RTS + RTS
    m = make_module(code, {"_a": 0, "_b": 2})
    image = flash_with(0x80020, RTS)
    image = image[:0x40] + RTS + image[0x42:]
    a, b = compare.compare_section(image, BASE, m, 1, {"a": 0x80020, "b": 0x80040})
    assert (a.bad, a.in_place, b.bad, b.in_place) == ([], True, [], False)


def test_unnamed_section_start():
    code = NOP + RTS + RTS
    m = make_module(code, {"_f": 4})
    first, f = compare.compare_section(flash_with(0x80030, code), BASE, m, 1, {"f": 0x80034})
    assert (first.name, first.start, first.length, f.start) == ("(section start)", 0x80030, 4, 0x80034)
