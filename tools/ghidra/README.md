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
    -preScript OncoreSetup.java $PWD/eeprom/B4121P1115-SSG0220999-eeprom.bin
```

Run from the repository root.  The project lives outside the repository.

| Script | What |
|---|---|
| `OncoreSetup.java` | Memory blocks (RAM, EEPROM with its dump, CS9/CS10, CS0-CS7 windows, on-chip registers), SIM/QSM/GPT register labels, vector table |
| `OncoreReport.java` | References into the EEPROM and each chip-select window |
| `OncoreVectors.java` | Vector targets and CS7 register usage |

Report scripts on an existing project:

```
$GHIDRA/support/analyzeHeadless ~/ghidra-projects oncore-vp \
    -process B4121P1115-flash.bin -noanalysis -readOnly \
    -scriptPath tools/ghidra -postScript OncoreVectors.java
```

The CPU32 has a 24-bit bus; code reaches $FFxxxx with sign-extended
short addresses, so CS0-CS7 and the on-chip registers are placed at
$FFFFxxxx, with byte-mapped aliases at $00FFxxxx.
