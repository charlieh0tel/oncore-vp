# Matching rewrite

Hand-written C for functions of `third_party/moto/B4121P1115-flash.bin`
that the original compiler turns into the same bytes as the flash.
The C is in `third_party/moto/rewritten/`: derived from the vendor
image, not covered by this repository's license.

## Build and compare

Toolchain: HP B3640 A.04.10 under DOSBox-X (`~/src/b3640`, `cc68k` on
`PATH`).

```
tools/matchdecomp/build.sh /tmp/match          # cc68k -p cpu32 -OT -c -I DIR FILE.c, each third_party/moto/rewritten/*.c
uv run tools/matchdecomp/compare.py /tmp/match/*.obj [--dis]
```

`compare.py` parses the IEEE-695 objects with `tools/libsig.py`, splits
`prog` at public symbols, and compares each function at the address of
its name in `tools/ghidra/OncoreLibNames.java`, `tools/ghidra/names-*.tsv`,
then `names.tsv` here (later wins).  Relocated fields are checked
against the named address; unnamed ones are masked.  A function not
contiguous with the previous one in the same object fails.  `--dis`
disassembles both sides of a difference (`m68k-linux-gnu-objdump`).
About 2.5 s per source file.

## Compiler options

| Option | Effect on the 22 functions |
|---|---|
| `-p cpu32 -OT` | 22/22 match |
| `-p cpu32 -O` | 17/22: `bcd_to_bin`, `pos_restore`, `date_to_gps`, `fmt_scaled` differ (`x*10` as `MULS.L`, register choice), one shifted |
| no `-O`, `-OG` | 0/22: `LINK A6` frame on every function, `NOP` padding |

Traits of `-OT` output that the flash shows:

- No frame pointer unless a function has stack locals (`LINK A6,#-n`);
  arguments read at `(n,A7)`.
- Callee-saved registers single `MOVE.L Dn,-(A7)` or `MOVEM.L`; `NOP`
  before `RTS` in some functions (compiler's choice, reproduced).
- Calls to a function defined earlier in the same source file:
  `JSR (d16,PC)` (the assembler shortens backward references); all other
  calls `JSR abs.L`.  Globals and EEPROM: absolute long.  Absolute short
  (`$xxxx.W`) only in assembly (embedded with `#pragma ASM`).
- Switch: `SUBQ`/`BHI.W`, `MOVE.W (table,PC,D0.W*2)`, `NOP`,
  `JMP (table,PC,D0.W)`.
- Plain `char` is signed.

What decides a match besides the statements:

- Declaration order of locals and of `extern` objects (address register
  for each global address: `pos_restore` needed `ee_pos_rec` declared
  before `pos_saved`).
- Condition polarity (`if (a && b) return 1; return 0;` vs the inverse),
  `p = &p[2]` (`LEA`) vs `p += 2` (`ADDQ`), statement order inside a
  loop body.

## Results

| Address | Function | Bytes | First version matched |
|---|---|---|---|
| $09B0F6 | `bcd_to_bin` | 32 | no (local order) |
| $09B116 | `bin_to_bcd` | 34 | no (local order) |
| $09B2A6 | `eeprom_wait` | 56 | yes |
| $09B2DE | `eeprom_write` | 60 | yes |
| $09C3E8 | `pos_restore` | 324 | no (extern order) |
| $09C782 | `almanac_week_ok` | 74 | no (condition polarity) |
| $09C7CC | `sum8` | 34 | yes |
| $09C7EE | `checksum32` | 44 | yes |
| $09C81A | `date_to_gps` | 272 | no (`?:` vs `==`) |
| $09FB0A | `vis_predict_init` | 24 | yes |
| $09FE06 | `task_rtc_write_date` (C + `#pragma ASM` exit) | 36 | yes |
| $0A1A0E | `mem_copy` | 34 | no (statement order) |
| $0A6A18 | `xor_sum` | 40 | no (local order) |
| $0A6A40 | `fmt_scaled` | 98 | yes |
| $0A6BC8 | `get_be24` | 42 | no (`LEA` vs `ADDQ`) |
| $0A6BF2 | `get_be24s` | 46 | no (`v = c; v += f()` vs `v = c + f()`) |
| $0A6C20 | `get_be16` | 28 | no (expression shape) |
| $0AC73C | `cmd_AJ` | 40 | yes |
| $0ACDB4 | `cmd_AH` (switch) | 78 | no (store in each case) |
| $0ACE02 | `cmd_AF` | 46 | yes |
| $0ACE30 | `cmd_Ai` | 60 | yes |
| $0ACE6C | `cmd_Ag` | 34 | yes |

22 functions, 1,536 bytes, all match; 11 on the first version, the
rest after 1-5 variants.  All relocations checked (none masked).
Contiguous runs in one source file: $09B2A6-$09B319, $09C782-$09C929,
$0A6A18-$0A6AA1, $0A6BC8-$0A6C3B, $0ACDB4-$0ACE8D.

`eewait.obj` linked alone reproduces flash $09B2A6-$09B319:

```
CHIP 68332
FORMAT S
ORDER prog
SECT prog=$9B2A6
PUBLIC _eeprom_error=$7A38
END
```

(`cc68k -p cpu32 -k link.k -o out.abs eewait.obj`; the output is
S-records.)

## Image layout

| Range | Bytes | Contents |
|---|---|---|
| $080000-$0803FF | 1,024 | Vector table |
| $080400-$0890BF | 36,032 | Mostly assembly: RAM clear, `sci_isr`, `chan_isr` (33,900 bytes, absolute short), `vec_default`, CS7 control, `sched_start` |
| $0890C0-$0AE955 | 153,750 | C (488 functions), with assembly modules among them (reset $089F5C, `sched_dispatch` $08A13A) and `#pragma ASM` task exits |
| $0AE956-$0B0AD1 | 8,572 | Constants (`const` sections: tables, doubles, strings) |
| $0B0AD2-$0B54BD | 18,924 | Run-time library (72 functions; ABCPU32.LIB, library order) |
| $0B54BE-$0BFFF7 | | $FF |
| $0BFFF8-$0BFFFF | 8 | Even and odd byte sums |

- Same order as the B3640 default linker command file
  (`ORDER env,prog,const,lib,libc,libm`).
- No ROM copy of initialized RAM data; the startup only fills and
  tests RAM.  RAM globals can be pinned with linker `PUBLIC` lines.
- Module boundaries: 253 backward `JSR abs.L` and 166 `JSR (d16,PC)`
  call pairs in $080400-$0B0AD1, no conflict with "PC-relative iff same
  file"; at least 11 source files in the C region.
