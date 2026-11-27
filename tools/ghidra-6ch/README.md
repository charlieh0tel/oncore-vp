# Ghidra setup, 6-channel firmware

Rebuilds a Ghidra project for the 6ch flash `third_party/moto/B1121P1114-flash.bin`
(SFTW 98-P39972M VER 8 REV 4, 13 Jul 1995).  Uses the generic scripts in
`tools/ghidra` and the 6ch scripts and tables here.  Tested with Ghidra 12.1.4.

```
GHIDRA=~/apps/ghidra_12.1.4_PUBLIC
$GHIDRA/support/analyzeHeadless ~/ghidra-projects oncore-6ch \
    -import third_party/moto/B1121P1114-flash.bin \
    -loader BinaryLoader -loader-baseAddr 0x80000 \
    -processor 68000:BE:32:CPU32 \
    -scriptPath "tools/ghidra;tools/ghidra-6ch" \
    -preScript OncoreSetup.java $PWD/eeprom/B1121P1114-SSG0163878-eeprom.bin \
    -preScript OncoreNoSwitch.java \
    -postScript OncoreSwitches.java \
    -postScript Oncore6Float.java \
    -postScript Oncore6LibNames.java \
    -postScript Oncore6Names.java \
    -postScript OncoreStackParameters.java \
    -postScript Oncore6ExportC.java $PWD/third_party/moto/decompiled/B1121P1114.c
```

Result: 62 switches recovered, 557 functions, all named, none failing to
decompile; 645 labels.  Run from the repository root.

| Script | What |
|---|---|
| `Oncore6Float.java` | `tools/ghidra/OncoreFloat.java` at the 6ch addresses (8ch - $3F7E) |
| `Oncore6LibNames.java` | B3640 run-time library names; table from `tools/libsig.py --flash third_party/moto/B1121P1114-flash.bin --java` (ABCPU32.LIB: same 37 sections as the 8ch, at $0ACB54-$0B1540), plus `_fabs` $0B0B56 |
| `Oncore6Names.java` | `tools/ghidra/OncoreNames.java` reading the tables here; also clears data defined over a named function's entry |
| `Oncore6ExportC.java` | `tools/ghidra/OncoreExportC.java` with the 6ch header and the notes here |
| `names-core.tsv` | Functions: 8ch names carried over by matching instruction streams (8ch address and match kind in the note) |
| `names-globals.tsv` | Labels: 8ch labels carried over where the matched instructions' references agree |
| `names-manual.tsv` | Names found by hand (tasks, functions auto-analysis missed); read last, wins |

`OncoreTypes.java`, `OncoreFixups.java` and the 8ch tables are 8ch-address
specific and are not used.

Function matching (8ch project vs 6ch project): 546 of the 6ch's 550
auto-analysis functions paired with 8ch functions, all in the same address
order; 352 identical instruction streams, 117 reached from matched callers,
77 by similarity.  8ch functions with no 6ch counterpart found: @@E* and @@F*
commands and messages (`cmd_E`, `cmd_F`, `msg_Ea`, `msg_Ec`, `msg_Eg`,
`msg_Ek`, `msg_En`, length tables), `msg_Bh_ch67`, `sets_7sv`, `sets_8ofn`,
`task_timing_probe`, `pps_enable_update`; `qsm_init` is inline in
`reset_entry`.

Regenerating `names-core.tsv` and `names-globals.tsv` (needs the 8ch
project from `tools/ghidra`): run `gen/OncoreFingerprint.java` on both
projects (`-process ... -noanalysis -readOnly`, argument: output file),
then `gen/match.py fp8.txt fp6.txt OUT` and `gen/gen_names.py`.
