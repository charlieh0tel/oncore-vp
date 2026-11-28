#!/usr/bin/env python3
"""Match 8ch functions to 6ch functions from OncoreFingerprint dumps.
Usage: match.py fp8.txt fp6.txt outprefix"""

import collections
import difflib
import sys

# An instruction: (address, mnemonic and operands with addresses and large
# constants abstracted, ";"-terminated refs: c<hex> call, r<hex> data, s<hex> scalar).
Instruction = tuple[int, str, str]
# A function: (name, instructions).
Function = tuple[str, list[Instruction]]


def load(p: str) -> dict[int, Function]:
    """Function entry -> function, from an OncoreFingerprint dump."""
    funcs: dict[int, Function] = {}
    cur: int | None = None
    for line in open(p):
        c = line.rstrip("\n").split("\t")
        if c[0] == "F":
            cur = int(c[1])
            funcs[cur] = (c[2], [])
        else:
            assert cur is not None
            funcs[cur][1].append((int(c[1], 16), c[2], c[3]))
    return funcs


f8, f6 = load(sys.argv[1]), load(sys.argv[2])
out = sys.argv[3]
norm8 = {a: tuple(i[1] for i in v[1]) for a, v in f8.items()}
norm6 = {a: tuple(i[1] for i in v[1]) for a, v in f6.items()}
m: dict[int, tuple[int, str, float]] = {}  # 8ch entry -> (6ch entry, how, ratio)
used: set[int] = set()  # 6ch entries already matched


def add(a8: int, a6: int, how: str, r: float) -> bool:
    if a8 in m or a6 in used or a6 not in f6 or a8 not in f8:
        return False
    m[a8] = (a6, how, r)
    used.add(a6)
    return True


# 1. exact unique
by6: collections.defaultdict[tuple[str, ...], list[int]] = collections.defaultdict(list)  # stream -> 6ch entries
for a, n in norm6.items():
    by6[n].append(a)
by8: collections.defaultdict[tuple[str, ...], list[int]] = collections.defaultdict(list)  # stream -> 8ch entries
for a, n in norm8.items():
    by8[n].append(a)
for n, a8s in by8.items():
    if len(a8s) == 1 and len(by6.get(n, [])) == 1 and len(n) > 3:
        add(a8s[0], by6[n][0], "exact", 1.0)


def calls(ins: Instruction) -> list[int]:
    r: list[int] = []
    for x in ins[2].split(";"):
        if x.startswith("c"):
            r.append(int(x[1:], 16))
    return r


def align(a8: int, a6: int) -> tuple[difflib.SequenceMatcher[str], list[tuple[Instruction, Instruction, bool]]]:
    s = difflib.SequenceMatcher(None, norm8[a8], norm6[a6], autojunk=False)
    pairs: list[tuple[Instruction, Instruction, bool]] = []  # (8ch, 6ch, equal)
    for tag, i1, i2, j1, j2 in s.get_opcodes():
        if tag == "equal" or (tag == "replace" and i2 - i1 == j2 - j1):
            for k in range(i2 - i1):
                pairs.append((f8[a8][1][i1 + k], f6[a6][1][j1 + k], tag == "equal"))
    return s, pairs


# 2. propagate via calls
changed = True
while changed:
    changed = False
    for a8, (a6, _how, _r) in list(m.items()):
        s, pairs = align(a8, a6)
        for i8, i6, eq in pairs:
            if not eq:
                continue
            c8, c6 = calls(i8), calls(i6)
            if len(c8) == 1 and len(c6) == 1 and c8[0] in f8 and c6[0] in f6 and c8[0] not in m and c6[0] not in used:
                rr = difflib.SequenceMatcher(None, norm8[c8[0]], norm6[c6[0]], autojunk=False).ratio()
                if rr > 0.5:
                    add(c8[0], c6[0], "call", round(rr, 3))
                    changed = True

# 3. fuzzy for the rest, by size
rest6 = [a for a in f6 if a not in used]
for a8 in sorted(f8):
    if a8 in m:
        continue
    best: tuple[float, int | None] = (0.0, None)
    n8 = norm8[a8]
    for a6 in rest6:
        if a6 in used:
            continue
        n6 = norm6[a6]
        if not n8 or not n6 or min(len(n8), len(n6)) / max(len(n8), len(n6)) < 0.7:
            continue
        s = difflib.SequenceMatcher(None, n8, n6, autojunk=False)
        if s.real_quick_ratio() < best[0] or s.quick_ratio() < best[0]:
            continue
        r = s.ratio()
        if r > best[0]:
            best = (r, a6)
    if best[0] > 0.75 and best[1] is not None:
        add(a8, best[1], "fuzzy", round(best[0], 3))

# globals: vote on refs at aligned equal instructions
votes: collections.defaultdict[int, collections.Counter[int]] = collections.defaultdict(collections.Counter)
for a8, (a6, _how, _r) in m.items():
    s, pairs = align(a8, a6)
    for i8, i6, eq in pairs:
        if not eq:
            continue
        r8 = [x for x in i8[2].split(";") if x and x[0] in "rs"]
        r6 = [x for x in i6[2].split(";") if x and x[0] in "rs"]
        if len(r8) == len(r6):
            for x, y in zip(r8, r6, strict=False):
                if x[0] == y[0]:
                    votes[int(x[1:], 16)][int(y[1:], 16)] += 1
with open(out + "-funcs.tsv", "w") as w:
    for a8 in sorted(m):
        a6, how, r = m[a8]
        w.write(f"{a8:06X}\t{f8[a8][0]}\t{a6:06X}\t{f6[a6][0]}\t{how}\t{r}\t{len(norm8[a8])}\t{len(norm6[a6])}\n")
with open(out + "-globals.tsv", "w") as w:
    for a8 in sorted(votes):
        ((a6, count),) = votes[a8].most_common(1)
        tot = sum(votes[a8].values())
        w.write(f"{a8:06X}\t{a6:06X}\t{count}\t{tot}\n")
print(len(m), "of", len(f8), "8ch matched;", len(used), "of", len(f6), "6ch")
print(collections.Counter(h for _, h, _ in m.values()))
