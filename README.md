# oncore-vp

The Motorola VP Oncore GPS engine, as found in the HP 58503A (8-channel)
and the HP Z3801A and Z3805A (6-channel): boards, parts, memory map,
clocking, RF architecture and firmware.  The 8-channel firmware was read
over BDM, the 6-channel firmware over the host port.

Pages: <https://charlieh0tel.github.io/oncore-vp/>

| Path | What |
|---|---|
| [`index.html`](index.html) | Summary page: receivers, boards, RF architecture, antenna gain, clock chain, pins, memory map, registers, dumps, firmware, cold start, oscillator tuning, 6-channel sensitivity, photos, open items |
| [`oncore-vp-test-points.html`](oncore-vp-test-points.html) | The test points and the CPU's lower-edge pins labeled on a photo, and the wiring to a 10-pin BDM header |
| [`mc68331-clock-pins.html`](mc68331-clock-pins.html) | MC68331 pins 51-83, with the clock pins (8-channel board) |
| [`docs/memory-map.md`](docs/memory-map.md) | Memory map, chip selects, registers, serial, clock, EEPROM layout, firmware (compiler, tracking, navigation, host commands, date and time, QSPI devices), parts of all three boards, pinouts, open items |
| [`docs/firmware.md`](docs/firmware.md) | Firmware overview: one diagram of tasks, tracking, navigation, I/O, DCXO and RTC, and where the details are |
| [`docs/messages.md`](docs/messages.md) | Binary output message payloads, from the firmware |
| [`tools/ghidra/`](tools/ghidra/) | Ghidra scripts and name/type tables that rebuild the fully named 8-channel project from the flash image ([README](tools/ghidra/README.md)) |
| [`tools/ghidra-6ch/`](tools/ghidra-6ch/) | The same for the 6-channel image, names matched from the 8-channel project ([README](tools/ghidra-6ch/README.md)) |
| [`third_party/moto/decompiled/`](third_party/moto/decompiled/) | Decompiled C of both firmwares (Ghidra output, every function named; derived from the vendor images, see [NOTICE](third_party/NOTICE)) |
| [`tools/loader/`](tools/loader/dump.py) | Reads flash or EEPROM over the host port through the firmware's factory loader (`@@Tq`), with a 72-byte RAM stub |
| [`tools/oncore.py`](tools/oncore.py) | Host-port tool: decode @@Ea/@@Ba, set date and time, read the DCXO record and the clock drift, self-test, time to first fix |
| [`tests/`](tests/) | Tests for the Python tools.  Checks: `uv run ruff format`, `uv run ruff check`, `uv run mypy`, `uv run pytest` |
| [`tools/libsig.py`](tools/libsig.py) | Matches HP B3640 run-time library modules (IEEE-695) against the flash |
| [`tools/matchdecomp/`](tools/matchdecomp/) | Builds the hand-written C in [`third_party/moto/rewritten/`](third_party/moto/rewritten/) with the original compiler (B3640 A.04.10, `-p cpu32 -OT`) and compares the objects with the flash ([README](tools/matchdecomp/README.md)) |
| [`docs/board.md`](docs/board.md) | The 6-channel board: parts, test points and their positions, BDM header wiring, flash-read procedure |
| [`docs/images/`](docs/images/) | Photos and a 600 dpi scan used by `docs/board.md`; the 6ch top-can bias-point callouts used by `docs/memory-map.md` |
| [`photos/`](photos/) | Photos of all three boards, both sides ([index](photos/README.md)); `thumbs/` for the pages |
| [`eeprom/`](eeprom/) | EEPROM dumps, 8 KB each: the 58503A's receiver (B4121P1115, SSG0220999) and the two 6-channel receivers (B1121P1114, SSG0163878 and SSG0178541) |
| [`third_party/`](third_party/) | Vendor and vendor-derived files: both flash images, decompiled and rewritten C, datasheets, VP and UT engineering notes ([NOTICE](third_party/NOTICE)) |

License: [CC BY-SA 4.0](LICENSE) for this repository's own material
(notes, photos, pages), except
`tools/ghidra/OncoreNoSwitch.java`, `OncoreSwitches.java` and
`OncoreStackParameters.java`, which are from
[smartclock](https://github.com/charlieh0tel/smartclock) and are
[GPL-3.0](LICENSE-GPL-3.0).  The files in `third_party/` belong to their
owners and are not covered ([NOTICE](third_party/NOTICE)).

Related:

- [smartclock](https://github.com/charlieh0tel/smartclock): the SmartClock
  firmware's side of the link (the @@ messages each instrument sends and
  decodes) and a behavioral model of the engine.
- [mc683xx-bdm](https://github.com/charlieh0tel/mc683xx-bdm): the BDM pod
  and `bdm` tool used to read the receiver.
- [smartclock-oncore-emulator](https://github.com/charlieh0tel/smartclock-oncore-emulator):
  a replacement for the Oncore on a u-blox LEA-M8T.
