#!/usr/bin/env python3
"""Generate tools/ghidra-6ch name tables from the 8ch tables and the match.
Usage: gen_names.py m-funcs.tsv m-globals.tsv repo"""
import sys, os, glob, collections

funcs, globs, repo = sys.argv[1:4]
d8 = os.path.join(repo, 'tools/ghidra')
files = sorted(glob.glob(d8 + '/names-*.tsv'), key=lambda p: (not p.endswith('names-core.tsv'), p))
names8 = {}   # addr -> (kind, name)
for p in files:
    for line in open(p):
        if not line.strip() or line.startswith('#'): continue
        c = line.rstrip('\n').split('\t')
        if len(c) < 3 or c[1] not in ('F', 'L'): continue
        names8[int(c[0], 16)] = (c[1], c[2])

out = os.path.join(repo, 'tools/ghidra-6ch')
hdr = ('# 6ch (B1121P1114) names carried over from the 8ch tables (tools/ghidra/names-*.tsv)\n'
       '# by instruction-stream matching of the two Ghidra projects.  Columns: 6ch address,\n'
       '# kind, name, note (8ch address; match: exact = identical instruction stream,\n'
       '# call = reached from a matched caller, fuzzy = similarity ratio).  Generated;\n'
       '# corrections go in names-manual.tsv, which is read later and wins.\n')
used6 = set()
with open(os.path.join(out, 'names-core.tsv'), 'w') as w:
    w.write(hdr)
    for line in open(funcs):
        a8, n8, a6, n6, how, r = line.split('\t')[:6]
        a8i, a6i = int(a8, 16), int(a6, 16)
        name = names8.get(a8i, ('F', n8))[1]
        if name.startswith('FUN_'): continue
        note = f'8ch ${a8}' + ('' if how == 'exact' else f', {how}' + ('' if how == 'call' else f' {r}'))
        w.write(f'{a6}\tF\t{name}\t{note}\n')
        used6.add(a6i)

cand = collections.defaultdict(list)
for line in open(globs):
    a8, a6, n, tot = line.split()
    a8i, a6i, n, tot = int(a8, 16), int(a6, 16), int(n), int(tot)
    if a8i not in names8 or names8[a8i][0] != 'L': continue
    if not (n == tot and n >= 2 or n >= 3 and n >= 0.8 * tot): continue
    cand[a6i].append((n, a8i))
with open(os.path.join(out, 'names-globals.tsv'), 'w') as w:
    w.write(hdr.replace('names carried over', 'labels carried over')
            + '# Labels: only where every (or >= 80% and >= 3) matched reference agrees.\n')
    k = 0
    for a6i in sorted(cand):
        c = sorted(cand[a6i], reverse=True)
        if len(c) > 1 and c[0][0] == c[1][0]: continue
        n, a8i = c[0]
        if a6i in used6: continue
        w.write(f'{a6i:06X}\tL\t{names8[a8i][1]}\t8ch ${a8i:06X}\n'); k += 1
print(len(used6), 'functions', k, 'labels')
