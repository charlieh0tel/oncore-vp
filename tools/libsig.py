#!/usr/bin/env python3
"""Find HP B3640 run-time library modules in a flash image.

Usage: tools/libsig.py LIB|OBJ ... [--flash FILE] [--base HEX] [--min N] [--java]
  e.g. tools/libsig.py $HPCC68K/lib/ABCPU32.LIB $HPCC68K/lib/AB68000.LIB
  ($HPCC68K: ~/src/b3640/build/share/hp68k/dos/hpcc68k).  Run from the repo root.

Parses the IEEE-695 modules, masks relocated bytes, and searches the flash
for each code section.  A section is placed when it matches once with at
least N known bytes and its intra-section relocations agree; or, failing
that, when a match abuts a placed section or sits where a placed section's
relocation points at one of its names.  Earlier libraries take precedence.
Prints placed sections and names (F function, L label); --java prints the
table rows for tools/ghidra/OncoreLibNames.java.
"""

import argparse
import re
from collections.abc import Container

# An RPN token: a number, an operator ("+", "-", else hex of the byte), or a
# variable letter and index ("P<n>" the field address, "R1" section 1's base,
# "X3" external 3).
Token = int | str
# An RPN expression.
Expr = list[Token]
# A relocated field: (offset in section, RPN expr, size in bytes).
Reloc = tuple[int, Expr, int]
# A placed section's key: (module name, section name).
Key = tuple[str, str]
# A placed section: (address, length, known bytes, how placed, module, section index).
Placed = tuple[int, int, int, str, "Mod", int]


class Mod:
    def __init__(self, name: str) -> None:
        self.name = name
        self.secs: dict[int, str] = {}  # section index -> name
        self.size: dict[int, int] = {}  # section index -> size
        self.data: dict[int, bytearray] = {}  # section index -> bytes
        self.mask: dict[int, bytearray] = {}  # section index -> 1 per known byte
        self.relocs: dict[int, list[Reloc]] = {}  # section index -> relocated fields
        self.pub: dict[int, str] = {}  # symbol index -> name
        self.ext: dict[int, str] = {}  # symbol index -> name
        self.val: dict[str, tuple[int, int]] = {}  # public name -> (section, offset)


class Reader:
    def __init__(self, b: bytes, p: int) -> None:
        self.b, self.p = b, p

    def byte(self) -> int:
        v = self.b[self.p]
        self.p += 1
        return v

    def peek(self) -> int:
        return self.b[self.p]

    def num(self) -> int:
        v = self.byte()
        if v < 0x80:
            return v
        n = v - 0x80
        r = int.from_bytes(self.b[self.p : self.p + n], "big")
        self.p += n
        return r

    def string(self) -> str:
        n = self.byte()
        if n == 0xDE:
            n = self.byte()
        elif n == 0xDF:
            n = self.byte() << 8 | self.byte()
        s = self.b[self.p : self.p + n].decode("latin-1")
        self.p += n
        return s

    def expr(self, stop: Container[int]) -> Expr:
        """RPN expression as a list of tokens, up to a byte in stop."""
        out: Expr = []
        while self.peek() not in stop:
            c = self.peek()
            if c <= 0x88:
                out.append(self.num())
            elif 0xC0 <= c <= 0xDA:
                self.byte()
                out.append(chr(c - 0x80) + str(self.num()))
            elif 0xA0 <= c <= 0xBF:
                out.append({0xA5: "+", 0xA6: "-"}.get(self.byte(), hex(c)))
            else:
                raise ValueError(f"expr byte {c:#x} at {self.p:#x}")
        return out


def is_rec(c: int) -> bool:
    return c >= 0xE0


def parse_module(b: bytes, start: int) -> Mod:
    r = Reader(b, start)
    assert r.byte() == 0xE0
    r.string()
    m = Mod(r.string())
    w: dict[int, int] = {}  # W-record index -> part offset from start
    while True:
        c = r.peek()
        if c == 0xEC:
            r.byte()
            r.num()
            r.num()
            if 0xC0 <= r.peek() <= 0xDA:
                r.byte()
        elif c == 0xE2 and b[r.p + 1] == 0xD7:
            r.p += 2
            i = r.num()
            w[i] = r.num()
        else:
            break
    # Section part (W2..W3) and external part (W3..W4).
    r.p = start + w[2]
    while r.p < start + w[4]:
        c = r.byte()
        if c == 0xE6:
            i = r.num()
            while 0xC0 <= r.peek() <= 0xDA:
                r.byte()
            m.secs[i] = r.string() if r.peek() < 0x80 else ""
        elif c == 0xE7:
            r.num()
            r.num()
        elif c == 0xE2:
            v = r.byte()
            i = r.num()
            e = r.expr(range(0xE0, 0x100))
            if v == 0xD3:
                size = e[0]
                assert isinstance(size, int)
                m.size[i] = size
            elif v == 0xC9:
                # R<sec> <off> +   or   R<sec>
                ref = e[0]
                off = e[1] if len(e) > 1 else 0
                assert isinstance(ref, str) and isinstance(off, int)
                m.val[m.pub[i]] = (int(ref[1:]), off)
        elif c == 0xE8:
            i = r.num()
            m.pub[i] = r.string()
        elif c == 0xE9:
            i = r.num()
            m.ext[i] = r.string()
        elif c == 0xF1:
            r.expr(range(0xE0, 0x100))
        else:
            raise ValueError(f"{m.name}: record {c:#x} at {r.p - 1:#x}")
    # Data part (W5..W6).
    r.p = start + w[5]
    sec: int | None = None
    pos = 0
    while w[5] and r.p < start + w[6]:
        c = r.peek()
        if c == 0xE5:
            r.byte()
            sec = r.num()
            m.data.setdefault(sec, bytearray(m.size.get(sec, 0)))
            m.mask.setdefault(sec, bytearray(m.size.get(sec, 0)))
            m.relocs.setdefault(sec, [])
            pos = 0
        elif c == 0xE2:
            r.byte()
            r.byte()
            r.num()
            e = r.expr(range(0xE0, 0x100))
            off = e[1] if len(e) > 2 else 0
            assert isinstance(off, int)
            pos = off
        elif c == 0xE4:
            r.byte()
            assert sec is not None
            while not is_rec(r.peek()):
                if r.peek() < 0x80:
                    n = r.byte()
                    m.data[sec][pos : pos + n] = b[r.p : r.p + n]
                    m.mask[sec][pos : pos + n] = b"\1" * n
                    r.p += n
                    pos += n
                elif r.peek() in (0xBA, 0xBE):
                    close = r.byte() + 1
                    e = r.expr([close])
                    r.byte()
                    n = 4
                    depth = 0
                    for t in e:
                        depth += -1 if t in ("+", "-") else 1
                    if depth == 2:
                        size = e.pop()
                        assert isinstance(size, int)
                        n = size
                    m.relocs[sec].append((pos, e, n))
                    pos += n
                else:
                    raise ValueError(f"{m.name}: LR byte {r.peek():#x} at {r.p:#x}")
        elif c == 0xED:
            r.byte()
            assert sec is not None
            n = r.num()
            m.data[sec][pos : pos + n] = b[r.p : r.p + n]
            m.mask[sec][pos : pos + n] = b"\1" * n
            r.p += n
            pos += n
        elif c == 0xE1:
            break
        else:
            raise ValueError(f"{m.name}: data record {c:#x} at {r.p:#x}")
    return m


def modules(path: str) -> list[Mod]:
    b = open(path, "rb").read()
    if b[2:9] != b"LIBRARY":
        return [parse_module(b, 0)]
    # Library directory, before the first module: F8 14 00 00 83 <offset> 84.
    d = [
        (x.start(), int.from_bytes(x.group(1), "big")) for x in re.finditer(rb"\xf8\x14\x00\x00\x83(...)\x84", b, re.S)
    ]
    first = min(o for p, o in d if o < len(b) and b[o] == 0xE0)
    starts = [o for p, o in d if p < first]
    return [parse_module(b, s) for s in sorted(set(starts))]


def search(flash: bytes, data: bytearray, mask: bytearray) -> list[int]:
    """Offsets where data matches flash on the masked bytes."""
    # Anchor on the longest run of known bytes.
    best, i = (0, 0), 0
    while i < len(data):
        if mask[i]:
            j = i
            while j < len(data) and mask[j]:
                j += 1
            best = max(best, (j - i, i))
            i = j
        else:
            i += 1
    n, a = best
    if n < 4:
        return []
    anchor = bytes(data[a : a + n])
    hits: list[int] = []
    p = flash.find(anchor)
    while p >= 0:
        s = p - a
        if (
            s >= 0
            and s + len(data) <= len(flash)
            and all(not mask[k] or flash[s + k] == data[k] for k in range(len(data)))
        ):
            hits.append(s)
        p = flash.find(anchor, p + 1)
    return hits


def check(flash: bytes, base: int, m: Mod, sec: int, s: int) -> int:
    """Count intra-section relocations that resolve to s; -1 if one fails."""
    good = 0
    for off, e, n in m.relocs[sec]:
        if n == 4 and e[0] == f"R{sec}":
            addend = e[1] if len(e) > 2 else 0
            assert isinstance(addend, int)
            want = base + s + addend
            got = int.from_bytes(flash[s + off : s + off + 4], "big")
            if got != want:
                return -1
            good += 1
    return good


def evaluate(e: Expr, field: int, sec_addr: dict[int, int]) -> tuple[int, str | None]:
    """Value of e with its one X or foreign R term set to 0, and that term."""
    st: list[int] = []
    term: str | None = None
    for t in e:
        if t == "+":
            b = st.pop()
            st.append(st.pop() + b)
        elif t == "-":
            b = st.pop()
            st.append(st.pop() - b)
        elif isinstance(t, int):
            st.append(t)
        elif t[0] == "P":
            st.append(field)
        elif t[0] == "R" and int(t[1:]) in sec_addr:
            st.append(sec_addr[int(t[1:])])
        else:
            term = t
            st.append(0)
    return st[0], term


def resolve(flash: bytes, base: int, m: Mod, sec: int, addr: int) -> list[tuple[str, int, int, int]]:
    """(term, address, field, size) for each external relocation."""
    out: list[tuple[str, int, int, int]] = []
    for off, e, n in m.relocs[sec]:
        f = addr + off
        v0, term = evaluate(e, f, {sec: addr})
        if term is None:
            continue
        raw = int.from_bytes(flash[f - base : f - base + n], "big")
        x = (raw - v0) % (1 << 8 * n)
        if n < 4:
            x = f + ((x - f + (1 << 8 * n - 1)) % (1 << 8 * n)) - (1 << 8 * n - 1)
        out.append((term, x, f, n))
    return out


CODE = ("lib", "libc", "libm", "const", "env")
# Data objects in code sections.
DATA = {"__ctype", "__infinity", "__hex_NaN", "__hex_NaNf"}
# Not in the A.04.10 libraries (a different fabs): between cos and floor in
# library order, same __fp_error code ($D04000) as the library's fabs.
EXTRA = [(0x0B4AD4, "_fabs", "F", "by position and error code")]


def jump(flash: bytes, base: int, f: int, n: int) -> bool:
    """True if the field at f is the operand of jsr/jmp abs.l or bsr/bra."""
    op = int.from_bytes(flash[f - base - 2 : f - base], "big")
    return op in (0x4EB9, 0x4EF9) if n == 4 else op in (0x6100, 0x6000, 0x4EBA, 0x4EFA)


def implications(flash: bytes, base: int, placed: dict[Key, Placed]) -> dict[str, dict[int, set[bool]]]:
    """name -> {address: {is jump target}} from relocations of placed sections."""
    implied: dict[str, dict[int, set[bool]]] = {}
    for _key, (addr, _ln, _known, _how, m, sec) in placed.items():
        for term, x, f, n in resolve(flash, base, m, sec, addr):
            j = jump(flash, base, f, n)
            if term[0] == "X":
                implied.setdefault(m.ext[int(term[1:])], {}).setdefault(x, set()).add(j)
            else:
                for nm, (s_, o) in m.val.items():
                    if s_ == int(term[1:]):
                        implied.setdefault(nm, {}).setdefault(x + o, set()).add(False)
    return implied


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("libs", nargs="+")
    ap.add_argument("--flash", default="third_party/moto/B4121P1115-flash.bin")
    ap.add_argument("--base", default="80000")
    ap.add_argument("--min", type=int, default=16, help="minimum known bytes for a lone match")
    ap.add_argument("--java", action="store_true")
    a = ap.parse_args()
    flash = open(a.flash, "rb").read()
    base = int(a.base, 16)
    libs = [(lib.split("/")[-1], modules(lib)) for lib in a.libs]
    placed: dict[Key, Placed] = {}

    def end(k: Key) -> int:
        x = placed[k][0] + placed[k][1]
        return x + x % 2

    # (library, key, module, section index, flash addresses that match)
    allunits: list[tuple[str, Key, Mod, int, list[int]]] = []
    for lib, mods in libs:
        units: list[tuple[Key, Mod, int, list[int]]] = []
        for m in mods:
            for sec, data in m.data.items():
                key = (m.name, m.secs[sec])
                if m.secs[sec] in CODE and len(data) and key not in placed:
                    hits = [base + h for h in search(flash, data, m.mask[sec]) if check(flash, base, m, sec, h) >= 0]
                    units.append((key, m, sec, hits))
        allunits += [(lib,) + u for u in units]
        for key, m, sec, hits in units:
            if len(hits) == 1 and sum(m.mask[sec]) >= a.min:
                placed[key] = (hits[0], len(m.data[sec]), sum(m.mask[sec]), lib, m, sec)
        # Linked sections are contiguous: accept a short or repeated match
        # that starts at the end of a placed section or ends at the start of
        # one.  Identical modules with the same such hits go in library order.
        changed = True
        while changed:
            changed = False
            # matching addresses -> sections that match only there
            todo: dict[tuple[int, ...], list[tuple[Key, Mod, int]]] = {}
            for key, m, sec, hits in units:
                if key in placed:
                    continue
                ln = len(m.data[sec])
                ok = tuple(h for h in hits if any(h == end(k) or h + ln + ln % 2 == placed[k][0] for k in placed))
                if ok:
                    todo.setdefault(ok, []).append((key, m, sec))
            for ok, us in todo.items():
                if len(us) == len(ok):
                    for h, (key, m, sec) in zip(ok, us, strict=False):
                        how = lib + (" adjacent" if len(ok) == 1 else " adjacent, library order")
                        placed[key] = (h, len(m.data[sec]), sum(m.mask[sec]), how, m, sec)
                    changed = True
    # A section that matches where a relocation of a placed section points
    # at one of its names is placed there.
    while True:
        implied = implications(flash, base, placed)
        new = 0
        for lib, key, m, sec, hits in allunits:
            if key in placed:
                continue
            for n, (s_, o) in m.val.items():
                xs = implied.get(n, {})
                if s_ == sec and len(xs) == 1 and next(iter(xs)) - o in hits:
                    h = next(iter(xs)) - o
                    placed[key] = (h, len(m.data[sec]), sum(m.mask[sec]), lib + " relocation target", m, sec)
                    new += 1
                    break
        if not new:
            break
    # Names defined in placed sections.
    names: dict[int, list[tuple[str, str, str]]] = {}  # address -> [(name, kind, how)]
    seen: set[str] = set()
    for key, (addr, _ln, _known, _how, m, sec) in sorted(placed.items(), key=lambda x: x[1][0]):
        for n, (s_, o) in sorted(m.val.items(), key=lambda x: x[1][1]):
            if s_ == sec and n not in seen:
                seen.add(n)
                kind = "L" if key[1] == "const" or n in DATA else "F"
                names.setdefault(addr + o, []).append((n, kind, key[0]))
    for x, n, kind, src in EXTRA:
        seen.add(n)
        names.setdefault(x, []).append((n, kind, src))
    conflicts: list[tuple[str, list[int]]] = []  # (name, addresses)
    for nm, xs in sorted(implied.items()):
        if len(xs) > 1:
            conflicts.append((nm, sorted(xs)))
            continue
        x, js = next(iter(xs.items()))
        if nm in seen:
            if not any(nm == e[0] for e in names.get(x, [])):
                conflicts.append((nm, [x]))
            continue
        seen.add(nm)
        names.setdefault(x, []).append((nm, "F" if True in js else "L", "relocation"))
    if a.java:
        for addr in sorted(names):
            for n, kind, src in names[addr]:
                print(f'        {{"{addr:06X}", "{n}", "{kind}"}},  // {src}')
        return
    print("# placed sections: start end module section length known how")
    for key, (addr, ln, known, how, _m, _sec) in sorted(placed.items(), key=lambda x: x[1][0]):
        print(f"{addr:06X} {addr + ln:06X} {key[0]:14} {key[1]:6} {ln:5} {known:5} {how}")
    print("# names: address name kind source")
    for addr in sorted(names):
        for n, kind, src in names[addr]:
            print(f"{addr:06X} {n:18} {kind} {src}")
    for nm, addrs in conflicts:
        print("# conflict", nm, " ".join(f"{x:06X}" for x in addrs))
    for m in libs[0][1]:
        for sec, data in m.data.items():
            if m.secs[sec] in CODE and len(data) and (m.name, m.secs[sec]) not in placed:
                print("# not placed", m.name, m.secs[sec], len(data))


if __name__ == "__main__":
    main()
