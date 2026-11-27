# oncore-vp

The Motorola VP Oncore GPS engine, as found in the HP 58503A (8-channel)
and the HP Z3801A and Z3805A (6-channel): its board, parts, memory map,
clocking and firmware, read over BDM.

Pages: <https://charlieh0tel.github.io/oncore-vp/>

| Path | What |
|---|---|
| [`index.html`](index.html) | Summary page: clock chain, memory map, dumps, firmware, cold start, oscillator tuning, registers, boards, photos, pins |
| [`oncore-vp-test-points.html`](oncore-vp-test-points.html) | The test points and the CPU's lower-edge pins labeled on a photo, and the wiring to a 10-pin BDM header |
| [`mc68331-clock-pins.html`](mc68331-clock-pins.html) | MC68331 pins 51-83, with the clock pins |
| [`docs/memory-map.md`](docs/memory-map.md) | Memory map, chip selects, registers, serial, clock, EEPROM layout, firmware (compiler, tracking, navigation, host commands, date and time, QSPI devices), parts of all three boards, pinouts, open items |
| [`docs/firmware.md`](docs/firmware.md) | Firmware overview: one diagram of tasks, tracking, navigation, I/O, DCXO and RTC, and where the details are |
| [`docs/messages.md`](docs/messages.md) | Binary output message payloads, from the firmware |
| [`tools/ghidra/`](tools/ghidra/) | Ghidra scripts and name/type tables that rebuild the fully named project from the flash image ([README](tools/ghidra/README.md)) |
| [`third_party/moto/decompiled/B4121P1115.c`](third_party/moto/decompiled/B4121P1115.c) | Decompiled C of the whole firmware (Ghidra output, all 574 functions named; derived from the vendor image, see [NOTICE](third_party/NOTICE)) |
| [`tools/loader/`](tools/loader/dump.py) | Reads flash or EEPROM over the host port through the firmware's factory loader (`@@Tq`), with a 72-byte RAM stub |
| [`tools/oncore.py`](tools/oncore.py) | Host-port tool: decode @@Ea/@@Ba, set date and time, read the DCXO record and the clock drift, self-test, time to first fix |
| [`tools/libsig.py`](tools/libsig.py) | Matches HP B3640 run-time library modules (IEEE-695) against the flash |
| [`docs/board.md`](docs/board.md) | The 6-channel board: parts, test points and their positions, BDM header wiring, flash-read procedure |
| [`docs/images/`](docs/images/) | Photos and a 600 dpi scan used by `docs/board.md` |
| [`photos/`](photos/) | Photos of all three boards, both sides ([index](photos/README.md)); `thumbs/` for the pages |
| [`eeprom/B4121P1115-SSG0220999-eeprom.bin`](eeprom/B4121P1115-SSG0220999-eeprom.bin) | EEPROM dump, 8 KB, from the 58503A's receiver (B4121P1115, SSG0220999) |
| [`third_party/`](third_party/) | Vendor files: the 58503A receiver's flash image, datasheets, VP engineering notes ([NOTICE](third_party/NOTICE)) |

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
