# Motorola Oncore VP 8-channel (from HP 58503A)

Read over BDM (mc683xx-bdm, `bdm` 0.4.0, pod firmware 0.5.0),
2026-11-24.  Firmware: SFTW P/N 98-P36830P, VER 8, REV 8, dated
06 Aug 1996.

## Memory map at reset

| Range | Chip select | Contents |
|---|---|---|
| $000000-$0FFFFF | CSBOOT, 1 MB, 16-bit | Boot flash, 256 KB, mirrored 4 times |

- Reset vectors: SSP $00020000, PC $00089F5C.
- CSPAR0 $3FFF, CSPAR1 $03FF, CSBARBT $0007, CSORBT $7B70, CS0-CS10
  $0000.

## Memory map running

| Range | Size | CS | Width | Access | Contents |
|---|---|---|---|---|---|
| $000000-$01FFFF | 128 KB | CS8 | 8 | R/W | RAM |
| $020000-$021FFF | 8 KB | CS4 | 8 | R/W, 2 wait | EEPROM |
| $080000-$0FFFFF | 512 KB | CSBOOT | 16 | R/W, 1 wait | Boot flash |
| $100000-$17FFFF | 512 KB | CS9 | 16 | R/W, 1 wait | Reads all $FF |
| $180000-$1FFFFF | 512 KB | CS10 | 16 | R/W, 1 wait | Reads all $FF |
| $FFC800-$FFCFFF | 2 KB | CS0 wr, CS2/CS3 rd | 16 | DS strobe | Not read |
| $FFD000-$FFD7FF | 2 KB | CS1 | 16 | write, DS strobe | Not read |
| $FFD800-$FFDFFF | 2 KB | CS6 | 8 | R/W | Not read |
| $FFE000-$FFE7FF | 2 KB | CS5 | 16 | R/W, DS strobe | Not read |
| $FFE800-$FFEFFF | 2 KB | CS7 | 16 | R/W | Not read |
| $FFF000-$FFFFFF | 4 KB | on-chip | | | Module registers (MM = 1) |

All chip selects asynchronous, S+U space, IPL 0, AVEC 0.

```
CSPAR0 $3BFF  CSPAR1 $03EE
CSBOOT $0806 $7870   CS0 $FFC8 $7470   CS1 $FFD0 $7430   CS2 $FFC8 $6C30
CS3    $FFC8 $6C30   CS4 $0201 $78B0   CS5 $FFE0 $7C70   CS6 $FFD8 $7830
CS7    $FFE8 $7830   CS8 $0004 $7830   CS9 $1006 $7870   CS10 $1806 $7870
```

Bus monitor is off (SYPCR $00).  A read outside every chip select
(e.g. $022000) hangs the BDM link; only `bdm reset` recovers it.

## Boot flash

- 256 KB.  `third_party/moto/B4121P1115-flash.bin`, SHA-256
  `48ea2488486df858bd81c76d9c52acf761d9ea9389581ac1443a8d44d8e75890`.
  Three reads identical.
- Last 8 bytes: $00C5B910 = sum of even-addressed bytes $00000-$3FFF7;
  $00EC4899 = sum of odd-addressed bytes.  Both verified.

## EEPROM

- CS4, $020000-$021FFF (set up only after the firmware runs).  The 6ch
  board has an AT28C64B; the 8ch part not yet seen.
- `eeprom/B4121P1115-SSG0220999-eeprom.bin`, 8 KB, SHA-256
  `003e9f533d66fb448ad2a5504a70d76f016abdbb2a0c19043d4a818589a44261`.
  Three reads identical.
- $0000-$0879, $11DE-$1FFF: $AA fill.
- $087A-$0904: ID text.  MODEL # B4121P1115, HDWR P/N # blank,
  SERIAL # SSG0220999, MANUFACTUR DATE 7D01, OPTIONS LIST IB.
- $0944-$0C4F and $0CC4-$0FC7: two tables of 24-byte per-satellite
  records.
- $1020-$11DD: IEEE doubles, including WGS-84 a = 6378137.0,
  1/f = 298.257223563, and a stored position: lat 0.6523 rad,
  lon -2.1309 rad, 12.65.

## On-chip registers (running)

| Register | Address | Value | Decoded |
|---|---|---|---|
| SIMCR | $FFFA00 | $E041 | EXOFF 1 (CLKOUT off), FRZSW 1, FRZBM 1, SUPV 0, MM 1, IARB 1.  Reset value $60CF. |
| SYNCR | $FFFA04 | $D10A | W 1, X 1, Y 17: x576.  SLOCK 1.  Reset value $3F08 (x256). |
| SYPCR | $FFFA21 | $00 | Watchdog, bus monitor, halt monitor off |
| PICR / PITR | $FFFA22 | $000F / $0000 | PIT off |
| QSMCR | $FFFC00 | $4002 | FRZ1 1, IARB 2 |
| QILR / QIVR | $FFFC04 | $06 / $41 | SCI level 6, vector $41 |
| SCCR0 | $FFFC08 | $0037 | Baud divider 55 |
| SCCR1 | $FFFC0A | $002C | TE, RE, RIE; 8N1 |
| GPTMCR | $FFF900 | $2000 | |
| TMSK2 | $FFF921 | $00 | GPT prescaler /4 |
| DDRGP / PORTGP | $FFF906 | $DF / $E1 | |

## Serial

- Host port is the SCI: 9600 baud 8N1, Motorola binary, on CH9102
  adapter 5C8D070211 (`/dev/ttyACM5`).  A byte written to SCDR over BDM
  appeared on that port.

## Clock

- CLKOUT at reset (halted, SYNCR x256): 7.4505 MHz (scope, 46,000
  averages, std dev 5.15 kHz).
- f_sys running (SYNCR x576): 16.769 MHz ± 0.03%, from GPT TCNT
  rollovers timed by the host.
- PLL reference: 7.4505 MHz / 256 = 29,103.5 Hz; 16.769 MHz / 576 =
  29.11 kHz.
- SCI rate: 29,103.5 Hz x 576 / (32 x 55) = 9525 baud.
- MC68HC68T1 (SOIC-16, CKAC9703).  Its CLKOUT options are XTAL, /2,
  /4, /8, 1 Hz, 2 Hz, 64 Hz, or low.  Supported crystals: 4.194304,
  2.097152, 1.048576 MHz, 32.768 kHz.
- Crystal next to the T1, marked "32.768 VT", "MC 9710": probed,
  32.768 kHz sine.
- Firmware reset entry ($089F5C): SR $2700, SP $20000, SIMCR $E041,
  SYPCR $00, SYNCR $D102 (x576), wait for SLOCK, then CSPAR0/1.
- Firmware SCCR0 table ($0A20A2): 109, 218, 437, 27, default 55
  (4800, 2400, 1200, 19200, 9600 baud).  Rounded to nearest, all five
  fit only f_sys = 16.762-16.781 MHz, i.e. reference = f_sys / 576 =
  29.10-29.13 kHz.  The measured 29.10-29.11 kHz is within this.
- EXTAL (pin 62), probed: 29.110 kHz square wave.  x256 = 7.452 MHz, x576 =
  16.767 MHz.
- Hybrid on the 8ch: 24M02, 19.09Mhz.  On the 6ch: 24M01, 19.09575M.  19.09575 MHz x 82.5 =
  1575.42 MHz (GPS L1).  19.09575 MHz / 656 = 29,109.4 Hz, matching
  EXTAL.  Owner: the /656 is almost certainly inside the hybrid; not
  yet verified.
- Nominal clocks from 19.09575 MHz: EXTAL 29,109.38 Hz; f_sys at reset
  (x256) 7.45200 MHz; f_sys running (x576) 16.76700 MHz.

## 6-channel board *7VGN4*, from the Z3801A (owner's photos)

Top side (marked TM-AB):

| Part | Marking | Notes |
|---|---|---|
| CPU | MC68331CFC16 G91H DENU9621 | 132-pin QFP, 16.78 MHz grade; hand-marked '01A |
| SRAM | Hitachi HM628128BLFPI-8, 9629 | 128K x 8 |
| RTC | MC68HC68T1 CKBG9631 | |
| Crystal | 32.768 VT, MC 9634 | |
| Hybrid (in RF can, leaded package) | 24M01, 19.09575M, Motorola 9630 | |

Bottom side (marked BM-AB, MOTOROLA 84D43579):

| Part | Marking | Notes |
|---|---|---|
| EEPROM | Atmel AT28C64B 20JI 9626 | 8K x 8 |
| Flash (x2) | TN28F010 120 U63135J3 | 128K x 8 each; 256 KB total |
| ASIC | Motorola XCT38140FD04 AARX9552 | |
| RF can | 58M01 9631 (Motorola); part marked 95U01 / 627 (SAW filter) | |
| Header | 10-pin, 2 x 5 | |
| Label | *7VGN4* 01 | |

## 6-channel board *7VENR*, from the Z3805A (photos 2026-10-11)

Same layout as the 6ch board (TM-AB / BM-AB, 84D43579M03).

| Part | Marking |
|---|---|
| CPU | MC68331CFC16 G91H DEVT9614, hand-marked '05A |
| SRAM | Hitachi HM628128LFPI-8, 9621 |
| RTC | MC68HC68T1 CKAN9620 |
| Crystal | 32.768 VT, MC 9619 |
| Hybrid | 24M01, 19.09 M, 9601 |
| EEPROM | Atmel AT28C64B 20JI 9612 |
| Flash (x2) | TN28F010 -120 U54362J1 |
| ASIC | XCT38140FD04 AASS9603 |
| RF can | 58M01 9623; 95U01 / 624 (SAW filter); A06 |

## Host instruments

CPU hand marks (owner): '03A = from the HP 58503A, '01A = Z3801A,
'05A = Z3805A.  The 58503A held the 8-channel receiver; the Z3801A and
Z3805A held 6-channel receivers.

| Board | CPU mark | Instrument | Channels |
|---|---|---|---|
| *3W1VE* (on the jig) | faint, not legible in photos | HP 58503A | 8 |
| *7VGN4* | '01A | HP Z3801A | 6 |
| *7VENR* | '05A | HP Z3805A | 6 |


## 8-channel board *3W1VE*, from the 58503A (on the jig)

Top side marked TM-AA.  BDM wired by hand from the CPU pins to a
header.  Jig: carrier with USB-C, +5V/GND, RXD/TXD/GND headers; pod on
a 10-pin ribbon.

| Part | Marking | Notes |
|---|---|---|
| CPU | MC68331CFC16 0E93N DEUZ9702 | |
| SRAM | Toshiba TC551001BFI-85L, 9706HAK | 128K x 8 |
| RTC | MC68HC68T1 CKAC9703 | |
| Crystal | 32.768 VT, MC 9710 | Probed: 32.768 kHz sine |
| Hybrid (in RF can, metal package) | 24M02, 19.09Mhz, Motorola 9711 | |
| Reset supervisor (SOIC-8) | 33064: MC33064 undervoltage sensor, originally Motorola (third_party/onsemi/MC34064-D.PDF). Trips at 4.6 V; SOIC-8 pins 1 Reset, 2 Input, 4 Ground | Also on the 6ch boards |

Bottom side marked BM-AA, MOTOROLA 84D44800M01:

| Part | Marking | Notes |
|---|---|---|
| EEPROM | Atmel AT28C64B 20JI 9705 | 8K x 8 |
| Flash (x2) | TN28F010 120 U65109J2 | 128K x 8 each; 256 KB total |
| ASIC | Motorola XCT38140FD04 AAYW9711 | |
| RF can | 58M01 9706; 95U01 / 711 (SAW filter); A06 | |
| Header | 10-pin, 2 x 5 | |
| Label | *3W1VE* | |

## Bottom-side RF can (from photos)

Same on all three boards: 95U01 SAW filter (owner), 58M01 in a 32-pad
leadless carrier, A06 in SOT-23, plus 2R2 / 220 / R68 inductors and
tantalum capacitors.  The SAW filter's two end terminals sit next to
the 58M01 and the A06.  58M01: likely the RF front end / downconverter
(owner); A06 position fits a first LNA stage.  Not traced.

## From the smartclock repo (github.com/charlieh0tel/smartclock)

Source: docs/oncore-vp-board.md and oncore-vp-test-points.html, per
the sc session.  Boards covered: DENU9621 (= *7VGN4*, photographed)
and DEVT9614 (= *7VENR*, traced and scanned at 600 dpi).  The doc
marks each item traced, buzzed or "to confirm".

- Test points B5-B11: GND, IPIPE/DSO (54), IFETCH/DSI (55),
  BKPT/DSCLK (56), FREEZE (58), CLKOUT (66), RESET (68).  L6 = +5 V.
  BERR (70) and DS (85) have no test point.
- 10k pull-ups on DSI, BKPT, HALT, BERR, IRQ7-IRQ1 (tied together),
  MODCLK.
- The doc also has test-point positions in mm, a 10-pin BDM header
  wiring table, and a flash-read procedure (set SIMCR FRZSW, then read
  0-$3FFFF).
- Other Oncore material there is host-side only: docs/firmware/gps.md,
  docs/firmware/oncore/*.txt (@@ messages each SmartClock image uses),
  docs/firmware/oncore/models.md (model strings, e.g. b1121, b4121,
  r5122), docs/oncore-leap-week.md.
- Not covered there: XCT38140, hybrids, RF parts, chip-select map,
  peripherals, EEPROM formats, Oncore firmware analysis.

## Pins

MC68331, 132-pin QFP (Motorola technical data, Figure 2):

51 VSS, 52 TXD, 53 RXD, 54 IPIPE/DSO, 55 IFETCH/DSI, 56 BKPT/DSCLK,
57 TSC, 58 FREEZE/QUOT, 59 VSS, 60 XTAL, 61 VDDSYN, 62 EXTAL, 63 VDD,
64 XFC, 65 VDD, 66 CLKOUT, 67 VSS, 68 RESET, 69 HALT, 70 BERR,
71-77 IRQ7-IRQ1, 78 PF0/MODCLK, 79 R/W, 80 SIZ1, 81 SIZ0, 82 PE4/AS,
83 VSS.

MC68HC68T1, 16-pin (third_party/moto/MC68HC68.pdf, REV 2):

1 CLKOUT, 2 CPUR, 3 INT, 4 SCK, 5 MOSI, 6 MISO, 7 SS, 8 VSS, 9 PSE,
10 POR, 11 LINE, 12 VSYS, 13 VBATT, 14 XTALin, 15 XTALout, 16 VDD.

## Open

- Confirm the RF chain antenna -> A06 -> 95U01 SAW -> 58M01 by
  continuity.
- Confirm the /656 is in the hybrid (29.110 kHz on a hybrid pin, traced
  to EXTAL pin 62).
- TCXO frequency error (needs a counter, or a long host-timed count).
- Devices behind CS0-CS3, CS5-CS7.
- EEPROM record formats.
- Whether CS9/CS10 have chips fitted.
