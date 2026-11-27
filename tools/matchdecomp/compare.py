#!/usr/bin/env python3
"""Compare B3640 object code with the VP Oncore flash image.

Usage: tools/matchdecomp/compare.py OBJ ... [--flash FILE] [--base HEX] [--dis]
  Run from the repo root.

Code sections are split at public symbols.  Each function is compared at
the address its name (minus the leading "_") has in OncoreLibNames.java,
tools/ghidra/names-*.tsv or tools/matchdecomp/names.tsv (later wins), else
right after the previous function; a function not contiguous with the
previous one fails.  Plain bytes must be equal.  A relocated field is
checked when its value is known (a named external, or a reference into the
same section); otherwise it is masked.  --dis prints both disassemblies of a
function that differs (needs m68k-linux-gnu-objdump).
"""

import argparse
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

TOOLS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOLS))

import libsig  # noqa: E402

GHIDRA = TOOLS / "ghidra"
OVERRIDES = Path(__file__).resolve().parent / "names.tsv"
CODE_SECTIONS = ("prog",)
OBJDUMP = "m68k-linux-gnu-objdump"
ROW_KINDS = ("F", "L")


def load_names(ghidra: Path, overrides: Path) -> dict[str, int]:
    """Symbol name -> address: library table, name tables, then overrides."""
    names: dict[str, int] = {}
    java = (ghidra / "OncoreLibNames.java").read_text()
    for addr, name in re.findall(r'\{"([0-9A-F]{6})", "(\w+)", "[FL]"\}', java):
        names[name] = int(addr, 16)
    for tsv in [*sorted(ghidra.glob("names-*.tsv")), overrides]:
        for line in tsv.read_text().splitlines():
            if line.startswith("#") or not line.strip():
                continue
            cols = line.split("\t")
            if len(cols) >= 3 and cols[1] in ROW_KINDS and "+" not in cols[0]:
                names[cols[2]] = int(cols[0], 16)
    return names


def lookup(names: dict[str, int], symbol: str) -> int | None:
    """Address of a C or library symbol."""
    if symbol in names:
        return names[symbol]
    if symbol.startswith("_"):
        return names.get(symbol[1:])
    return None


@dataclass
class Result:
    """Comparison of one function."""

    name: str
    offset: int  # offset in the object's section
    start: int  # address in the flash
    length: int
    in_place: bool  # start follows the previous function in the flash
    bad: list[int] = field(default_factory=list)  # differing offsets from start
    masked: int = 0  # relocated fields not checked


def evaluate(expr: list, pc: int, sections: dict[int, int], ext: dict[int, int | None]) -> int | None:
    """Value of a relocation's RPN expression, or None if a term is unknown."""
    stack: list[int] = []
    for t in expr:
        if t in ("+", "-"):
            b = stack.pop()
            stack.append(stack.pop() + b if t == "+" else stack.pop() - b)
        elif isinstance(t, int):
            stack.append(t)
        elif t[0] == "P":
            stack.append(pc)
        elif t[0] == "R" and int(t[1:]) in sections:
            stack.append(sections[int(t[1:])])
        elif t[0] == "X" and ext.get(int(t[1:])) is not None:
            stack.append(ext[int(t[1:])])  # type: ignore[arg-type]
        else:
            return None
    return stack[0]


def split(m: libsig.Mod, sec: int, names: dict[str, int]) -> list[Result]:
    """Functions of a section, each at its table address, else after the previous one.

    Unnamed code before the first named function is placed back from it.
    """
    publics = sorted((off, n) for n, (s, off) in m.val.items() if s == sec)
    if not publics or publics[0][0]:
        publics.insert(0, (0, "(section start)"))
    known = [(lookup(names, n), off) for off, n in publics]
    origin = next((a - off for a, off in known if a is not None), None)
    if origin is None:
        print(f"{m.name}: no function with a known address", file=sys.stderr)
        return []
    ends = [off for off, _ in publics[1:]] + [len(m.data[sec])]
    results: list[Result] = []
    for (off, n), (addr, _), end in zip(publics, known, ends, strict=True):
        follow = results[-1].start + results[-1].length if results else origin
        start = follow if addr is None else addr
        results.append(Result(n, off, start, end - off, start == follow))
    return results


def compare_section(flash: bytes, base: int, m: libsig.Mod, sec: int, names: dict[str, int]) -> list[Result]:
    """Per-function results for one code section."""
    data, mask = m.data[sec], m.mask[sec]
    ext = {i: lookup(names, n) for i, n in m.ext.items()}
    results = split(m, sec, names)
    for r in results:
        image = flash[r.start - base : r.start - base + r.length]
        for k in range(r.length):
            if mask[r.offset + k] and (k >= len(image) or image[k] != data[r.offset + k]):
                r.bad.append(k)
        for off, expr, size in m.relocs[sec]:
            k = off - r.offset
            if not 0 <= k < r.length:
                continue
            value = evaluate(expr, r.start + k, {sec: r.start - r.offset}, ext)
            if value is None:
                r.masked += 1
            elif int.from_bytes(image[k : k + size], "big") != value % (1 << 8 * size):
                r.bad.extend(range(k, k + size))
    return results


def disassemble(code: bytes, addr: int) -> str:
    """objdump listing of code at addr."""
    with tempfile.NamedTemporaryFile(suffix=".bin") as tmp:
        tmp.write(code)
        tmp.flush()
        out = subprocess.run(
            [OBJDUMP, "-D", "-b", "binary", "-m", "m68k:cpu32", f"--adjust-vma={addr:#x}", tmp.name],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    return "\n".join(line for line in out.splitlines() if re.match(r"\s+[0-9a-f]+:", line))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("objs", nargs="+", type=Path)
    ap.add_argument("--flash", type=Path, default=Path("third_party/moto/B4121P1115-flash.bin"))
    ap.add_argument("--base", default="80000")
    ap.add_argument("--dis", action="store_true", help="disassemble functions that differ")
    a = ap.parse_args()
    flash = a.flash.read_bytes()
    base = int(a.base, 16)
    names = load_names(GHIDRA, OVERRIDES)
    total = matched = 0
    for obj in a.objs:
        for m in libsig.modules(str(obj)):
            for sec, sname in m.secs.items():
                if sname not in CODE_SECTIONS or not m.data.get(sec):
                    continue
                for r in compare_section(flash, base, m, sec, names):
                    total += 1
                    ok = not r.bad and r.in_place
                    matched += ok
                    status = "match" if not r.bad else f"DIFF {len(r.bad)} bytes"
                    if not r.in_place:
                        status += ", not contiguous with the previous function"
                    print(f"${r.start:06X} {r.length:5d} {r.name:28s} {status} ({r.masked} relocations masked)")
                    if r.bad and a.dis and shutil.which(OBJDUMP):
                        print("--- object")
                        print(disassemble(bytes(m.data[sec][r.offset : r.offset + r.length]), r.start))
                        print("--- flash")
                        print(disassemble(flash[r.start - base : r.start - base + r.length], r.start))
    print(f"{matched}/{total} functions match")
    return 0 if matched == total else 1


if __name__ == "__main__":
    sys.exit(main())
