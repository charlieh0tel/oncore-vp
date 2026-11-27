# Ghidra setup

Rebuilds a Ghidra project for the 58503A receiver's flash with the
running memory map from `docs/memory-map.md`.  Tested with Ghidra
12.1.4.  Needs the LFS files checked out.

```
GHIDRA=~/apps/ghidra_12.1.4_PUBLIC
$GHIDRA/support/analyzeHeadless ~/ghidra-projects oncore-vp \
    -import third_party/moto/B4121P1115-flash.bin \
    -loader BinaryLoader -loader-baseAddr 0x80000 \
    -processor 68000:BE:32:CPU32 \
    -scriptPath tools/ghidra \
    -preScript OncoreSetup.java $PWD/eeprom/B4121P1115-SSG0220999-eeprom.bin \
    -preScript OncoreNoSwitch.java \
    -postScript OncoreSwitches.java \
    -postScript OncoreFloat.java \
    -postScript OncoreLibNames.java \
    -postScript OncoreNames.java \
    -postScript OncoreStackParameters.java
```

Result: 64 switches recovered, 19 soft-float entries named, 76 run-time
library names (OncoreLibNames.java), 272 functions
given narrow stack parameters, none failing to decompile.

Run from the repository root.  The project lives outside the repository.

| Script | What |
|---|---|
| `OncoreSetup.java` | Memory blocks (RAM, EEPROM with its dump, CS9/CS10, CS0-CS7 windows, on-chip registers), SIM/QSM/GPT register labels, vector table |
| `OncoreReport.java` | References into the EEPROM and each chip-select window |
| `OncoreVectors.java` | Vector targets and CS7 register usage |
| `OncoreDecompile.java` | Decompile functions to C files; list references to address ranges |
| `OncoreEeprom.java` | EEPROM references by function |
| `OncoreCallers.java` | Call sites of functions, with the pushes before each |
| `OncoreNoSwitch.java` | Turns off Ghidra's switch analysis before auto-analysis (from smartclock) |
| `OncoreSwitches.java` | Recovers the compiler's switch tables and records jump-table overrides (from smartclock's SmartClockSetup.java) |
| `OncoreFloat.java` | Names the soft double routines (add64, sub64, sub64r, mul64, div64, div64r, p/pp variants, add64z) and sets their register interfaces |
| `OncoreLibNames.java` | Names the B3640 run-time library routines and data (table from `tools/libsig.py`) |
| `OncoreNames.java` | Names functions and RAM variables identified in `docs/memory-map.md` (command handlers, RTC, QSPI, EEPROM, oscillator, time, status) |
| `OncoreExportC.java` | Decompiles every function to one C file; prints named/unnamed counts |
| `OncoreStackParameters.java` | Narrow stack parameters (from smartclock); run last |

Report scripts on an existing project:

```
$GHIDRA/support/analyzeHeadless ~/ghidra-projects oncore-vp \
    -process B4121P1115-flash.bin -noanalysis -readOnly \
    -scriptPath tools/ghidra -postScript OncoreVectors.java
```

The CPU32 has a 24-bit bus; code reaches $FFxxxx with sign-extended
short addresses, so CS0-CS7 and the on-chip registers are placed at
$FFFFxxxx, with byte-mapped aliases at $00FFxxxx.

`OncoreNoSwitch.java`, `OncoreSwitches.java` and `OncoreStackParameters.java`
come from smartclock and are GPL-3.0 (`LICENSE-GPL-3.0`).
