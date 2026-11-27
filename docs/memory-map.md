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
| $100000-$17FFFF | 512 KB | CS9 | 16 | R/W, 1 wait | Reads all $FF; never accessed; pin 124 has no visible trace |
| $180000-$1FFFFF | 512 KB | CS10 | 16 | R/W, 1 wait | Reads all $FF; never accessed; pin 125 has no visible trace |
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

Single-stepping (BDM, gdb `stepi`) with interrupts unmasked enters
`chan_isr` $0805B2, whose `move.w #0,($E8F2).w` write to CS7 never
ends its bus cycle while stepping (FREEZE low); only `bdm reset`
recovers (2026-11-27, pod 0.5.0).  Mask interrupts first
(`set $ps = 0x2700` in gdb).

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
- $087B-$0904: ID text (byte $087A is a setting; see below).  MODEL # B4121P1115, HDWR P/N # blank,
  SERIAL # SSG0220999, MANUFACTUR DATE 7D01, OPTIONS LIST IB.
- $0944-$0C4F and $0CC4-$0FC7: two tables of 24-byte per-satellite
  records.
- $1020-$11DD: IEEE doubles, including WGS-84 a = 6378137.0,
  1/f = 298.257223563, and a stored position: lat 0.6523 rad,
  lon -2.1309 rad, 12.65.

### EEPROM layout (from the firmware; checksums verified on the dump)

Checksum ($09C7EE): 32-bit, start $FFFFFFFF, subtract each byte.  Every
block below has its checksum as a big-endian long right after it, and
all match the dump.

| Offset | Length | Checksum at | Contents |
|---|---|---|---|
| $087A | 1 | | @@Bh channel threshold ($C652): a channel is sent if chan_rec+$A0 > it.  Written by @@AD ($0AC5CC, second byte if < $80); default $20 ($0A29BC).  The ID text starts at $087B |
| $0906 + n | 1 each | | Output rate of binary message n, written by the B and E rate commands through $0ADE56 (RAM copy at $C61E + 2n) |
| $091F | 1 | | Position-fix/idle mode flag, RAM $C651: set by `fix_mode_enter` $0AB430 (and the protocol-change handlers), cleared by @@Cg 0, @@Ca, @@Cf |
| $0920 | 1 | | Protocol mode: 0 = Motorola binary, 1 = NMEA, 2 = third mode (RAM $C650) |
| $0921 | 1 | | @@AE setting, 0 or 1 ($0ACBFC); default 0 |
| $0922 + 2n | 2 each | | Output rates, 8 words written through $0ADE12 (RAM $CAF8); the eighth ($0930) is the terse rate |
| $0932, $0933 | 1 + 1 | | Test bytes: written $00 and $80 and read back at start ($09C096); bit 7 polled for write completion ($09B2A6) |
| $0934 | 884 | $0CA8 | Almanac bank 0 |
| $0CAC | 884 | $1020 | Almanac bank 1 |
| $1024 | 1 | | Almanac bank state: 0 or 1 = active bank; 2 = no valid almanac (written by `almanac_load` when neither bank checks, and by @@Cf).  A save writes bank 0 if the state is 1 or 2, else bank 1, then sets the state to that bank.  Dump: 1 |
| $1026 | 28 | $1042 | @@An $1026, @@Aj $1027 (stored code), @@Ak $1028 (double), 2D HDOP limit $1030 (@@AC), 3D PDOP limit $1038 (@@Al), mask angle $1040 (@@Ag); dump = defaults 01 02 1.0 12.0 6.0 $0A |
| $1046 | 78 | $1094 | 10 bytes 0; 3 doubles 0; 1/f; a; datum number $31 (49 = WGS-84 in Oncore numbering, inferred); pad; doubles 12.65, lat 0.6523 rad, lon -2.1309 rad; $01 $00 |
| $1098 | 6 | $109E | $369A (@@Ar b6), $9D86 (@@Aw), $81C2 (@@Aq), $8216 (@@Ar b7), $8EB8; dump = defaults 01 01 01 01 02 |
| $10A2 | 42 | $10CC | $10A2 @@Ar b0 ($81DA), $10A4-$10C8 SV ignore list 1-37 ($8F89-$8FAD, @@Am), $10C9 @@Ah ($9D0C), $10CA @@AD ($7A3A) |
| $10D0 | 42 | $10FA | Loader $09EF44: $10D0 @@AJ max correction age (90), $10D2/$10D3 local offset h/min (@@Ab), $10D4 @@Ax (word), $10D6 @@Az cable delay (long), $10DC @@Ay 1PPS offset (long), $10E0 1PPS mode (1), $10E1 UTC/GPS ($9D0F), $10E3 TRAIM on (1), $10E4 $9D12 (1), $10E8 TRAIM alarm limit (1000 ns), $10EC-$10F9 @@En date/time (0).  Dump: all defaults except TRAIM on |
| $10FE | 42 | $1128 | 3 doubles 0; 1/f; a; $32 |
| $112C | 42 | $1156 | 3 doubles 0; 1/f; a; $33 |
| $115A | 12 | $1166 | @@AF word ($3624) 7, @@AG ($3626) 1, @@AH ($3627) 0, @@AI double ($3628) 3704.0: 00 07 01 00 40 AC F0 00 00 00 00 00 |
| $116A | 6 | $1170 | Oscillator record 0: search center, bin step, learned drift ($50, $00B9, $5055); see "Learned oscillator offset" |
| $1174 | 6 | $117A | Oscillator record 1: ($50, $00B9, $501B) |
| $117E | 1 | | Active record (0 or 1); dump: 1.  If invalid, defaults $67, $015E, $5091 ($09C096) |
| $1180 | 8 | $1188 | Ionosphere model, Klobuchar alpha0-3, beta0-3, one byte each (scales 2^-30, 2^-27, 2^-24, 2^-24; 2^11, 2^14, 2^16, 2^16, used in $08B5CE): 11 FE FE 02 3C F5 FE 0C |
| $118C, $118E | 2 + 2 | | Year hint, two slots (word, binary); $1190 selects (0 = $118C, 1 = $118E).  Written only by @@Ac, alternating slots ($09FE2A).  Invalid selector or year outside 1980-2070: $118C = 1992, $1190 = 0 ($09C52C).  Dump: $07EA, $07EA (2026) |
| $1190 | 1 | | Year slot selector; dump 0 |
| $1192, $11AE | 28 + 28 | $11AA, $11C6 | Position records: height, lat, lon (doubles), saved by $09F94A, restored by $09C3E8; selector $11CA.  Dump: selector 1; record 0 h = 0; record 1 h = 12.65, lat/lon as block $1046; both checksums verify |
| $11CC | 14 | $11DA | UTC parameters ($08BF50: 604800, 86400, 2^12, 2^-50 scales): bytes 0, 1 = delta-t LS and delta-t LSF = 18, 18; bytes 3, 4 handled as 8-bit week numbers; dump 12 12 24 8E 89 07 00 00 00 00 00 00 00 01 |

Almanac bank ($09BEDE): 32 records of 24 bytes at bank + $18 x SV
(SV 1-32), then 2 records of 24 bytes at bank + $318, then 44 bytes at
bank + $348; copied to RAM when the checksum matches.

Block loaders ($09E88E, $09EA04, $09ECDE, $09EE0E, $09EF44, $09F226,
$09F52A, $09F636, $09F72A) dispatch on an argument: 0 = load from
EEPROM, 1 = defaults then save, 2 = save changed fields ($09F81E).
$09F3A8 has no loader for block $112C (missing).  Position save slots:
EEPROM $1192 and $11AE, selected by $11CA.  ID checksum at $0905 (XOR
of $087B-$0904); on mismatch all output rates are cleared.

Block $1046 ($09EA04): +0-9 -> RAM $633C (written by @@Au, @@Av);
+10-49, five doubles -> RAM $7592, defaults 0, 0, 0, 298.257223563,
6378137.0 (datum: three offsets, 1/f, a), +50 datum number, default 49
-> $7592+40 (written by @@Ao); +52-75, three doubles -> RAM $81F0
(written by $0ACC96, which also fills +60/+68 lat/lon order as
stored), +76 -> $8208 (written by @@At), +77 -> $8209.

RAM copies and the commands whose handlers write them:

| Block | RAM | Written by |
|---|---|---|
| $1026 | $633A, $79F7-$79FF, $7A00, $7A08, $8FAE | @@An ($633A), @@Aj ($79F7), @@Ak ($79F8), @@AC ($7A00), @@Al ($7A08), @@Ag ($8FAE) |
| $1098 | $369A, $81C2, $8216, $8EB8, $9D86 | @@Ar ($369A, $8216), @@Aq ($81C2), @@Aw ($9D86) |
| $10A2 | $7A3A, $81DA, $8F89-$8FAD, $9D0C | @@AD ($7A3A), @@Ar b0 / @@Ce ($81DA), @@Am ($8F89-$8FAD), @@Ah ($9D0C) |
| $10D0 | $9D0E-$9D12, $819C-$819E, $820A, $79F6 (@@AJ), $81C8, $8210, $9D11-$9D24 | @@Ab ($819C), @@Az ($820A), @@Aw ($9D0F), B/E commands ($9D0E-$9D12) |
| $115A | $3624-$362F | @@AF, @@AH ($3624), @@AG ($3626), @@AI ($3628, $362C) |
| $116A/$1174 | $75BC, $75BE, $75C0 | @@Ta ($75BC, $75C0) |
| $1180 | $2426-$242D | No command: saved by $08B248 from a table at $243A + 8n |
| $11CC | $2622-$2631 | No command: saved by $08B248 from a table at $263C + 14n |

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
- f_sys running (SYNCR x576): 16.769 MHz +- 0.03%, from GPT TCNT
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
  1575.399375 MHz, exactly 20625 Hz below GPS L1: the firmware's drift
  target.  So L1 is mixed down by a total of 82.5 x the reference,
  leaving a 20.625 kHz carrier for the correlator (frequency plan
  inferred; stage count and LO frequencies not traced).  19.09575 MHz / 656 = 29,109.4 Hz, matching
  EXTAL.  EXTAL (CPU pin 62) goes to the XCT38140 ASIC (its top-left
  corner pin; continuity, 6ch), so the ASIC divides the TCXO by 656.
- Nominal clocks from 19.09575 MHz: EXTAL 29,109.38 Hz; f_sys at reset
  (x256) 7.45200 MHz; f_sys running (x576) 16.76700 MHz.

## Firmware (Ghidra)

Ghidra 12.1.4, language 68000:BE:32:CPU32, flash loaded at $080000.
Project setup: `tools/ghidra/` (README there).  557 functions after
auto-analysis.

- Short absolute addresses ($xxxx.W) reach $FFxxxx: CS0-CS7 and the
  on-chip registers appear at $FFFFxxxx in the disassembly.
- Vector table (flash $080000): 250 vectors -> $088A1E (`rte`).
  Others: 1 reset -> $089F5C; 29 (level-5 autovector) -> $0805B2;
  64 -> $08048E.
- $08048E, vector 64: SCI interrupt handler.  Reads SCSR ($FFFC0C),
  tests TDRE, writes SCDR ($FFFC0F) from a buffer in RAM.
- $0805B2, level-5 autovector, 33,880 bytes: per-channel handler for
  the CS7 window.  Starts by writing 0 to $FFE8F2, then reads 8 blocks
  of 16 bytes ($FFE800-$FFE87F; long at +0, words at +6, +8, +A) into
  RAM arrays.
- CS7 ($FFE800) offsets referenced: $000-$07A (8 x 16-byte blocks),
  $0E0, $0F0, $0F2, $0F4, $4FC, $7FF.  Also used by $088E96-$08907C
  and $0A1268-$0A1A4E.

### Compiler

HP's B3640 Motorola 68000 Family C Cross Compiler (manual B3640-97001,
Jan 1994), the same as smartclock's finding for the SmartClock firmware.
No version string in the image.  The soft-double routines match the
B3640 A.04.10 absolute-addressing run-time libraries (AB68000, AB68020,
AB68030, ABCPU32.LIB) byte for byte up to each object-record break
(sub64r: 93 bytes, div64r: 38); the library module `faddsub64` carries
the names sub64r, sub64p, add64pp.  The PC/PI (position-independent)
libraries differ in some 8-byte windows of the first 64 bytes; the AB
libraries match all of them.

Options: `cc68k -p cpu32 -OT`; hand-written C for 22 functions
(`third_party/moto/rewritten/`) compiles to the flash's bytes
(`tools/matchdecomp/`).  `-O` differs (`x*10` as
`muls.l`).

Run-time library in the image ($0B0AD2-$0B54BE), placed by
`tools/libsig.py` from ABCPU32.LIB (IEEE-695 modules, relocated bytes
masked); 37 sections, named by `tools/ghidra/OncoreLibNames.java`.
AB68000 places 30 (misses CPU32-only code), so the build used the
CPU32 libraries.

| Library | Modules |
|---|---|
| lib | faddsub32, faddsub64, fcmp64, fdiv32, fdiv64, dbl_to_flt, dbl_to_int, dbl_to_uint, flt_to_dbl, int_to_dbl, uint_to_dbl, int_to_flt, fmul32, fmul64, fnorm32, fnorm64, fperror |
| libc | abs, atoi/atol, ctype, labs, memcpy, memset, dbl_to_str, doprnt, sprintf, swrite, data_gen |
| libm | asin, atan (atan2), cos, floorceil, fmod, mathmisc, sin, sqrt |

- fabs ($0B4AD4), frexp ($0B4DF8), modf ($0B4E8A): a different build
  from A.04.10; fabs placed by position and error code, the other two by
  relocations.
- abs/labs and atoi/atol have identical code; abs/labs assigned by
  library order, the single atoi/atol copy named `_atoi`.
- RAM: `__fp_status` $CC14, `__fp_control` $CC16, `_errno` $CC1C.
- `trap` $0A02AA (called by `__fp_error`): `jmp $089F5C`, the reset
  entry; a floating-point error resets the receiver.
- No environment startup code (ENV.LIB, CRT0/CRT1) matches.

| Trait | Firmware | B3640 manual |
|---|---|---|
| Prototyped char/short arguments | Not widened: `move.b`/`move.w -(sp)`, 2-byte slot, value at slot start; callee reads (8,A6) | Not widened with prototypes (pp. 37-38); Fig. 4-5 `MOVE.B D0,-(A7)` (p. 75) |
| Return | D0 (pointers too) | D0, D0:D1 (p. 70) |
| Saved / scratch | D2-D7, A2-A4 / D0, D1, A0, A1 | Same (pp. 77-78) |
| Caller cleanup | addq/lea, odd multiples of 2 | Caller removes parameters (p. 70) |
| Soft double routines | $0B0DEE/$0B0E04/$0B0E08 and $0B1EA4/$0B1EA8/$0B1EAC: three entries each (operands via (A0) and (A1), via (A1), in A0:A1 and D0:D1) | `op64pp`, `op64p`, `op64` interfaces (ch. 11, pp. 276-288) |
| 32-bit mul/div | Native CPU32 `muls.l`/`divs.l` | Targets include 68331/CPU32 (p. 19) |
| Library strings | "Prog end, returned", "fp ... error", routine-name table: absent | Present in the SmartClock images |

Whitesmiths C edition 2.2 (MC68000 interface manual, March 1983,
p. III.a-1) is ruled out: char/short widened to long, return in D7,
D3-D5/A3-A5 saved.

Decompilation (`tools/ghidra/README.md`): switch tables recovered
first (64), then the soft-float routines given register interfaces,
then narrow stack parameters (272 functions); none fail to decompile.

Soft double routines (B3640 ch. 11 names; identified from entry code):

| Routine | pp / p / plain entry | How identified |
|---|---|---|
| add64 | $0B0E00 / $0B0E04 / $0B0E08 | shared add code |
| sub64 | $0B0DBC / $0B0DC0 / $0B0DC4 | flips y's sign, joins add |
| sub64r | $0B0DAA / $0B0DAE / $0B0DB2 | swaps x and y, jumps to sub64 |
| mul64 | $0B1EA4 / $0B1EA8 / $0B1EAC | XORs signs |
| div64 | $0B14B0 / $0B14B4 / $0B14B8 | XORs signs |
| div64r | $0B149E / $0B14A2 / $0B14A6 | swaps x and y, jumps to div64 |
| add64z | $0B0DEE | adds D0:D1 into the double at (A0) |

### Learned oscillator offset

RAM $75BC (byte), $75BE (word), $75C0 (word), saved to the EEPROM
records at $116A/$1174 (active one selected by $117E); defaults $67,
$015E, $5091.

- $08C252 computes a drift as RAM $81D2 (double) x 5.2550354685707275
  (= 1575.42 MHz / c, L1 cycles per meter), rounded: Hz at L1 if $81D2
  is the clock drift in m/s (the 4th state of the velocity solution,
  used x 1/c).
- $08C252 runs from task $08B248 when $81EE = 2, at most once per
  power-up ($1143, cleared by $08B1A4).  Needs > 10 fixes in a row
  ($1142), each drift within 20 of the previous ($980); modes 2/3 need
  >= 4 SVs.

| Case | Action |
|---|---|
| $26B6 = 0, \|drift - 20625\| < 3131, \|drift - $75C0\| < 151 | None |
| $26B6 = 0, \|drift - 20625\| < 3131 | $75C0 += 20% of (drift - $75C0) |
| Otherwise | $75BC -= (drift - 20625) / $75BE (no clamp); $75C0 = 20625 |

  Then the record is saved ($09F876).  Not sent to PCS1 until the next
  boot, @@Ta or search step.  20625 is the drift target; higher $75BC =
  faster oscillator (inferred from the sign).
- $75BE: L1 Hz per LSB of $75BC; written only by the EEPROM load and
  @@Ta (default 350, this unit 185).
- $08FC68 seeds $81D2 = $75C0 / 5.2550354685707275; $097A02 seeds the
  channel search center from $75C0 (from $81D2 once $81EE != 0).
- Oscillator search ($097464, from $0957BC at the end of each SV-list
  pass with < 3 search records in state 3, tracking): n = 0 steps to the next list
  entry, stores it in $75BC and sends it to PCS1, cyclic, with no limit
  flag; n = 1-2 sets $26B6 = 2 and stops stepping ($095446 resets it;
  callers $08A774, $0A03A4).
- Doppler bins per setting: table $0AEEA2[$8EB8 x 6 + 1] = $0AEBCA,
  31 signed bytes x 100 Hz: 0, +-5 ... +-75 (+-7.5 kHz, 500 Hz steps).
- List: s1 = 7500 / $75BE; $75BC + k x 1.5 s1 for k = +1, -1, +2, ...,
  kept in 0-$D6, then $75BC.  $50/185: 140, 20, 200, 80.  $67/350:
  134, 72, 165, 41, 196, 10, 103.
- Range 0-$D6: 39.8 kHz at L1 (25 ppm) at 185 Hz/LSB; 75 kHz (48 ppm)
  at 350.
- @@Ta ($0AD4F4): payload drift (word), $75BC (byte), $75BE (word);
  all or nothing: stored only if word < $8000, byte < $D8 and word <
  $8000.  A stored set raises $9D83 and $8E9; the settings task $0A02C0
  runs on the next scheduler pass, writes the EEPROM record ($09F876
  osc_record_save, alternate bank, checksum) and then sends $75BC to
  PCS1.  The boot trim ($08C252) and the EEPROM-load default
  ($09C096) call the same save directly.  HP 58503B labels: Offs
  (drift reference), Warp (setting), Step (Hz/LSB); its
  :DIAGnostic:GPSystem:DCXO allows a Step of 0-$FFFF, so $8000-$FFFF
  is ignored here.  Reply ($0A2FBE): $75C0, $75BC,
  $75BE.  Query without change: `@@Ta FF FF FF FF FF CA 0D 0A`.
- Dump: active record 1 = $50, $00B9, $501B (20507, 118 below the
  default 20625); record 0 = $50, $00B9, $5055 (20565).
- Record layout: byte +0, byte +1 (not read by the code above; $00 in
  record 0, $06 in record 1), word +2, word +4, then the 4-byte
  checksum.
- Live read, 2026-11-26, receiver not tracking (no satellites; all
  channels in search): $81D2 = 3902.352348076068; x 5.2550354685707275
  = 20507.0000 = $75C0 = the active EEPROM record.  With no fixes,
  $81D2 holds $75C0 / 5.2550354685707275, i.e. the same quantity in
  the other unit.  Unchanged over 8 minutes.
- 2026-11-27, after tracking in position hold: `@@Ta` query reply
  $75C0 = 20625, $75BC = $50, $75BE = 185 (was 20507 in the dump;
  consistent with the coarse case after search stepping with
  \|drift - 20625\| < 185, inferred).
- Live, 2026-11-27, 10 min tracking 1-3 SVs (BDM samples every 10 s):
  drift 20338-20369 Hz at L1, mean 20355, 270 Hz below the 20625
  target.  That boot's fine update set $75C0 = 20572 (rule: 20625 + 20%
  of (20355 - 20625) = 20571); $75BC stayed $50.  With 20625 Hz the
  nominal carrier (82.5 x 19.09575 MHz below L1), the reference is off
  by 0.17 ppm, about 3.3 Hz at 19.09575 MHz; the sign depends on the
  sense of the conversion, not determined.

### CS7: the tracking channels

8 channels, channel n at $FFE800 + $10 x n.  Read in the level-5
interrupt `chan_isr` $0805B2 (vector 29), a fixed 1 ms tick (1000 per
second, not tied to a channel's code epoch); written by the handler's
loops and by channel setup ($088E96).

| Offset | Read | Write | Evidence |
|---|---|---|---|
| +$0 (long) | Accumulated carrier phase; upper 16 bits = cycles | Carrier NCO rate (Doppler), from the PLL filter; clamped to $26A7EF | $0805B2; $088E96 writes 0 at start |
| +$4 | | $D0A1 at channel start | $088E96 |
| +$6 | Prompt I | Code-phase slew: high byte = step count, low byte from table $089E68; $00A1 at start | I^2+Q^2, I^2-Q^2 in $0805B2 |
| +$8 | Prompt Q | PRN select: G2 tap pair, two 0-based nibbles, from table $088E20 | table matches GPS ICD taps for PRN 1-37 |
| +$A | Code discriminator (early-late), accumulated with the sign of I | Control: $800F reset, then $F, $E, $6, $2, $0 over successive epochs | $0805B2, $088E96, $088FB8 |

Global registers:

| Offset | Use | Evidence |
|---|---|---|
| $0E0 | Written 0 at correlator init | $088FB8 |
| $0F0 | Written 0; written $2DE by the interrupt at the PPS millisecond ($2DC, if $103 set; $081342): PPS timing (inferred) | $088F78, $0805B2 |
| $0F2 | Written 0 first thing in each level-5 interrupt (acknowledge); used to time interrupts against SYNCR in a self-test | $0805B2, $0A1A4E |
| $0F4 | Control word, shadow in RAM $2D8: init $86 then $87; bits 0, 1, 2 set and cleared by $088F78-$088FAA; bit 2 cleared at the PPS millisecond | $088FB8, $0805B2 |

Tracking loop ($0805B2), per channel:

- Epoch counters: RAM $2D6 counts 50, $100 counts 20, $78 is the
  0-19 position in the 20 ms data bit.
- Code phase in L1 cycles, RAM $382 + 4n, modulo $1809FC = 1,575,420
  (L1 cycles per 1 ms code period), advanced by the carrier phase
  read from +$0 (carrier aiding).  Slew steps of $37 = 55 cycles.
- Pairs of 1 ms samples are summed (2 ms coherent integration).
- State per channel, RAM $B0 + n, transitions in `task_chan_50ms`
  $089BA0:

| State | Per ms (even ms, paired samples) | Per 10 ms block |
|---|---|---|
| 0 search | Envelope (max + 3/8 min of \|I\|, \|Q\|) against the noise floor `chan_amp`, detection in `chan_detect`; +550 L1 cycles per dwell | |
| 1 code acquire | `chan_disc_acc` $164 += disc x sign(I) | +-1 code step (55 cycles) from the sign of the 10 ms discriminator |
| 2 frequency pull-in | As 1, plus phase difference from the 32 x 32 arctangent table $088A20 into `chan_fll_acc` $1A4 | Code step; NCO correction from $1B4 / $5E2 |
| 3 ($08985C: Doppler +$12E02, -$25C04, gives up at count 6) | Carrier loop: -Q sign(I) x weight $204 x AGC $114, filter $124/$134 (-$7971, $39C3), + FLL aid $2E2, integrator $862/$882; $862 >> 8 to CS7 +0 | Code step; 50 ms and 1 s sums |
| 4 bit sync | Carrier loop; bit-edge search from I sign changes ($F0, $F8; 4 hits -> `chan_bitsync`) | 50 ms and 1 s sums |
| 5 phase lock | Carrier loop | Fluctuation metric ($6E2/$6C2) sets the weight $1F4 if `trk_adapt_on`; FLL discriminator from the cross product of 10 ms vectors ($602/$622) drives the FLL aid; dot product -> $642 -> `chan_pll_lock`; data bit to $B8 and the shift register $4E2 (4 longs per channel) |

- One pending 10 ms block is processed per interrupt (first channel
  only); `chan_blk_ready` $68 marks a block at bit_ms 0 or 10.
- Code loop once per second ($08916C): normalized early-late
  ($0890C0) / $800, clamped to +-2 steps of 55 L1 cycles; +-1 dither in
  state 5.  Carrier aiding of $382 once per second at the epoch.
- Second epoch in the interrupt: snapshots code phase, carrier phase,
  NCO rate, amplitude ($7E2 -> $802), data bits ($4E2, 4 longs per
  channel -> $562); $2D6
  reduced by $2E0 (from $2924, `$091BFA`) each second to steer the epoch.
- Task interface: command block $275C (8 x $C: SV, Doppler word, code
  window start, width $1809FC full / $133B3 1/20 ms) loaded by $08960C;
  report $27BC (8 x $2C) built each second by $089670.
- Search records $CF6 (8 x $28): state 0 idle, 1 searching,
  2 reacquire, 3 tracking (interrupt state >= 4); strategy 0-5 selects
  a Doppler bin table ($0AEEA2 + $8EB8 x 24 + 4 x strategy).
  Tables ($68 bytes: up to 100 signed bin bytes x 100 Hz, +$64 dwells,
  +$65 bin count, +$66 passes; passes 0 = repeat):

| Strategy | Air | Handheld, Marine, Static | Land | Use |
|---|---|---|---|---|
| 0 | $0AEB62: 100 bins, +-4.5 kHz, 1 pass | $0AEAFA: 16 bins, +-2 kHz, 1 pass | $0AEA92: 100 bins, +-2 kHz, 2 passes | Almanac-aided search around the predicted Doppler; then strategy 1 |
| 1 | $0AEBCA: 31 bins, 0, +-0.5 ... +-7.5 kHz, 1 pass | same | same | Full-sky search, per DCXO setting |
| 2 | $0AEC32: +-600 Hz | same | same | Reacquisition (repeating) |
| 3 | $0AEC9A: +-300 Hz | same | same | Reacquisition (repeating) |
| 4 | $0AED6A: +-4.5 kHz | $0AED02: +-2 kHz | $0AED02 | Reacquisition; SV marked failed after its passes ($25FB) |
| 5 | $0AEE3A: +-7.5 kHz | $0AEDD2: +-3 kHz | $0AEDD2 | Reacquisition (repeating) |

  One bin is a full code sweep: $1809FC / 550 = 2864 dwells (about
  5.7 s at 2 ms, inferred).  Strategy 0 is then about 19 min per SV for
  Land (200 sweeps) and 1.5 min for Static (16); the reference gives
  30 min and 2 min of initial search.  This unit's EEPROM has Land.
- Search center $113C: seeded by $097A02, then moved each second by
  $097786 toward the drift implied by a tracked channel's Doppler, at
  most +-3130 per step.
- Tasks (scheduler $08A13A, pending flags $8DC-$8F5): $8DD
  `task_chan_50ms`, $8DE `task_chan_1s` $08916C, $8E0 `task_meas_1s`
  $08A7AE, $8E6 `task_vis` $09FB22.
- @@Ea channel mode ($6FBD, `chan_status_update` $099C86): 0-4
  interrupt state, 5 phase lock, 6 preamble sync, 7 TOW verified,
  8 ephemeris ready.  Flags ($6FBE): b7 used in fix, b6 alert, b5
  anti-spoof (HOW), b4 unhealthy, b3 URA >= 6, b0 parity error.
- Requested SV per channel: RAM $E0 + n; $088E96 restarts a channel
  when it differs from RAM $E8 + n.
- Other windows: no accesses to CS0, CS1, CS2, CS3 or CS6 anywhere in
  the image (absolute, lea/movea, immediates, pointer constants).  CS5
  $FFE000: one read, value discarded, at $08A752 in the boot
  continuation $08A714, before the task scheduler starts (a strobe,
  inferred).
- Discrete pins (ports E, F, GP, QS) are written once at reset and
  never again.
- PAI input low at reset ($08A09C): RAM filled with $FF and EEPROM
  $0000-$087A and $0906-$1FFF erased to $FF, keeping the ID block
  $087B-$0905 (a factory clear strap, inferred).
- Reset entry $089F5C also sets PEPAR $37, DDRE $C8, PORTE1 $08,
  PFPAR $FB, GPTMCR $2000, DDRGP $DF, PORTGP $C1, CFORC $03.

## Navigation (from the firmware)

1 Hz pipeline: `task_meas_1s` $08A7AE -> $097D2A (subframe decode,
pseudoranges) -> $095582 -> `nav_step` $08FD50 -> `rcvr_status_build`
-> TRAIM $0A053A.

| Item | Detail |
|---|---|
| Pseudorange | code phase x lambda / 256 -> $704C ($09843C); Hatch carrier smoothing, N doubling to 128 -> $7054; range rate = carrier x lambda / 2^14 -> $706A ($098700); transmit time = ms of week x 0.001 - range/c -> $7062 ($098538) |
| Ephemeris | Raw per SV $8FDA + $58 x SV (checksummed; accepted if \|t - toe\| < 4 h, $09A10C); decoded per channel $7A3C + $EC x ch ($098C68), with n, sqrt(1-e^2), Omegadot - omega_e, Omega0 - omega_e toe, sin/cos omega, A, e sqrtA precomputed |
| SV position | Angles propagated by rotating precomputed sin/cos pairs ($094066 init, $094418 step); full reinit on new ephemeris or one channel every 10th epoch ($091A1C, counter $CF4); Kepler by series iteration and ICD-200 harmonic corrections ($094660) |
| Solver | Unweighted least squares, not a Kalman filter: H rows [1, LOS.E, LOS.N, LOS.U] at $A04, normal matrix $984, inverse $369C ($0904A4); corrections applied to lat/lon/height and clock bias ($093362); velocity from Doppler residuals, then smoothing ($09528E, $092B56) and a position complementary filter ($0921EC) |
| DOP | $7A10 GDOP, $7A18 PDOP, $7A20 HDOP, $7A28 VDOP, $7A30 TDOP |
| Residuals | $8218 pseudorange, $8258 range rate, per channel ($091140; DGPS correction subtracted inside) |
| Modes ($093906, table $093FF2) | 1 before first fix (set by `nav_step`); 2 3D (>= 4 SVs, PDOP <= $7A08, @@Al); 3 2D (HDOP <= $7A00, @@AC; 3 SVs or altitude hold $6344); 4 position hold ($8208); 5 < 3 SVs; 6 DOP over mask or singular.  Velocity zeroed after 6 epochs in mode 5/6 ($2596) |
| Init state $81EE | 0 no fix, 1 three SVs, 2 navigating; first position = mean of SV positions projected to the ellipsoid ($090F80), up to 4 iterations ($0930D0) |
| Position hold | Time only: clock correction = mean residual; residuals folded modulo 1 ms once $81EE = 2 ($091140) |
| Corrections | Klobuchar per SV ($08B5CE -> $22F6 + 8 x SV) if $81C2 = 1 (@@Aq); Sagnac via LOS ($09175A -> $8F48); relativistic F e sqrtA sinE and TGD ($09150C); DGPS PRC/RRC with IODE match and age ($09BA1A, $09BB5E, $09B94C; sets $75C4 + $E x SV, previous $77D8).  No troposphere model |
| Almanac | Three RAM buffers $58DE, $5C52, $5FC6 (active `almanac_ptr`, collecting $81CA, last saved $8FD6); new almanac needs >= 15 healthy SVs and a common toa ($08B062, $08B0E2); written to the inactive EEPROM bank by $08AE9E |
| Visibility | Banks of $B14 at $37A2, $42B6 (selector $37A0), $4DCA; Kepler from the expanded almanac $116A + $88 x SV ($08E714, $08EA8E); SV sets ranked by DOP ($08E310, DOP $08C7DE); search order $08F55E |
| GPS - UTC ($08BF50 -> $26AE) | Block $2624: +0 dtLS, +1 dtLSF, +2 tot / 4096, +3 WNt, +4 WNLSF, +5 DN, +6 A0 (long, 2^-30), +$A A1 (long, 2^-50); +-480 s window around the leap sets the direction in $81C6 |
| TRAIM ($0A053A) | Residual spread tests with 32.9 / sqrt(n) and 98.7; worst SV excluded by clearing $8FAF[SV] (reset hourly by `nav_task`); time recomputed ($0A0BF2, $0A0DBA, $0A0F88); $9D28 alarm, $9D2A error estimate, $9D2C removed/insufficient; on/off $9D11 |
| 1PPS ($091BFA) | ms and sub-ms counts $291C, $291E in ticks of 9.548 MHz (31.398 m); clock bias and drift, cable delay $820A (@@Az), offset $81C8 (@@Ax), hardware delay $81BA (set in `boot_time_set`, includes -113.5 ns); mode $9D0E: 0 off, 1 on, 2 only when tracking, 3 only when TRAIM ok ($095180) |
| Limits ($0920BE) | Height > 18288 m and speed > 514.4 m/s: both clamped; height < -1000 m clamped |
| Other | Magnetic variation ($09D456, $09D7DA -> $81B2); 10 degree geoid grid ($09E310 -> $7574); position saved to RAM $2598 (checksum $25B0) and EEPROM ($09F94A) |

$097EE4 (parity) returns its result in D0 under a void signature, so
Ghidra decompiled it empty; `tools/ghidra/OncoreFixups.java` sets the
return type.

## Host commands (from the firmware)

Output payload layouts: [messages.md](messages.md).

Commands in the firmware but not in the VP Oncore Command Reference
(Synergy Systems, 05OCT01): @@AF-@@AI, @@Bm, @@Cl, @@Cm (option G),
@@Bz/@@Ez, @@Ta (DCXO record), @@Tb, @@Tc, @@Td, @@Th (ID block write),
@@Tq (factory loader, with @@Sa-@@Sg; `tq_match_cmd` $0A1E68).  The
reference's @@Ea status and channel-flag bits match the firmware.

Factory loader (@@Tq, $0A1E90): masks interrupts, saves all registers,
replies `@@Tq`, then runs its own polled SCI loop (watchdog serviced,
buffer 256 bytes below the stack) until @@Se.  Frames are `@@S?`,
count byte, address, data, XOR checksum (seeded with the letters),
CR LF; each is answered with its letters, a status byte (0 good,
1 checksum, 2 no CR LF) and CR LF.

| Cmd | Address bytes | Action |
|---|---|---|
| @@Sa | 2 | Write the data bytes to memory at the address |
| @@Sb | 3 | Same |
| @@Sc | 4 | Same |
| @@Sd | 0 | Read and acknowledge; no action |
| @@Se | 0 | Leave the loader; back to the firmware |
| @@Sg | 2 | jsr to the address ($0000-$FFFF: RAM) |

Writes are plain CPU stores, so they change RAM (and the EEPROM).  The
firmware has no flash erase/program sequence, and the 28F010s ignore
commands unless VPP is at 12 V; reprogramming flash needs a program
loaded into RAM with @@Sa-@@Sc, started with @@Sg, and 12 V on the
header's VPP pin (pin 4, see "Pins").

Configuration commands (consumers from the firmware):

| Cmd | RAM (default) | Consumer | Effect |
|---|---|---|---|
| @@AC | $7A00 (12.0) | nav_solve_mode $093906 | 2D HDOP limit; also resets the position-smoothing HDOP limit $0B92 |
| @@Al | $7A08 (6.0) | nav_solve_mode | 3D PDOP limit |
| @@Ak | $79F8 (1.0) | svset_select $095624, sv_select_nav $096474 | Switch SV set only if new DOP x 10 + Ak x 10 < current |
| @@An | $633A (1) | almanac_switch $08AF9A | 1: adopt almanacs collected from SVs; 0: only @@Cb almanacs |
| @@Aj | $79F7 (2) | task_vis_predict, dop_compute $08C7DE; @@Bc byte 4 | DOP used to rank SV sets; host 0-4 stored as 0, 2, 1, 4, 3 (internal 0 GDOP, 1 HDOP, 2 PDOP, 3 TDOP, 4 VDOP) |
| @@AJ | $79F6 (90) | diff_age_check $09BC0A | Corrections stale at age >= AJ x 10 |
| @@AD | $7A3A (0), $C652 ($20) | sv_select_nav; msg_Bh | Channel out of the fix while smoothing count < byte 0; @@Bh sends a channel only if count > byte 1 |
| @@Ar b0 | $81DA (1) | SV selection, svset_find, @@Bc | 1: 8-SV sets, all channels in the fix; 0: best 4-SV set.  @@Ce, RTCM and fresh corrections force 1 |
| @@Ar b6 | $369A = !b6 (1) | chan_isr, chan_pll_check $089AC4 | b6 = 1: adaptive state-5 weighting off; PLL loss limit 6 instead of 90 |
| @@Ar b7 | $8216 = !b7 (1) | pos_smooth $0921EC | b7 = 1: position smoothing off |
| @@Aq | $81C2 (1) | sv_clock_update $09150C | Klobuchar correction on/off |
| @@Ax | $81C8 (0) | pps_schedule $091BFA | Measurement epoch Ax ms after the second; removed again in the PPS delay |
| @@Ay, @@Az | $8210, $820A (0) | pps_schedule (timing option) | PPS delay = ... + Ay - Az + hardware delay.  Ay 4041960 enables the channel-state test, 888888888 the timing probe ($09FFC8) |
| @@Ah | $9CEA, $9D0C | sv_search_select, vis_list_default $08F55E | 0 auto; 1 manual list from @@Ai (not saved); 2 search in elevation order |
| @@AF-@@AI | $3624-$3628 (7, 1, 0, 3704.0) | Replies; AG-AI also in @@Bm | No functional consumer |
| @@Cm, @@Cl | $3630-$3642, $3644-$3687 | msg_Bm, reply_Cm, reply_Cl | Stored, reported, echoed only (option G); the @@Cl task $09FAF4 is empty |
| @@Cd | $3725-$37A0 | task -> reply_Cd $0A945E | Visibility prediction for a given date, time, position and mask |
| @@Ce | $75C8 table, $81DA = 1 | DGPS | Up to 6 corrections; PRC and RRC negated when stored |


Receive path: SCI interrupt ($08048E) -> ring buffer RAM $9DAE-$A52D
(1920 bytes; write pointer $A532, read pointer $A536, count $A53A) ->
line assembly at RAM $A9B2 ($0A65D6) -> on CR LF, validate ($0A69E2)
and dispatch ($0A46DC) by protocol mode (RAM $C650; see "Protocol
modes").

Binary frames ($0A68C0): `@@`, two command letters, payload, XOR of
the letters and payload, CR LF.  Frame length comes from per-letter
tables; a frame of the wrong length is not accepted.  Dispatch by
first letter ($0A4644), then second.

| Command: total frame length in bytes |
|---|
| Aa 10, Ab 10, Ac 11, Ad 11, Ae 11, Af 12, Ag 8, Ah 8, Ai 9, Aj 8, Ak 9, Al 9, Am 12, An 8, Ao 8, Ap 25, Aq 8, Ar 8, As 20, At 8, Au 12, Av 8, Aw 8, Ax 9, Ay 11, Az 11 |
| AA 8, AB 8, AC 9, AD 9, AE 8, AF 9, AG 8, AH 8, AI 9, AJ 8 |
| Ba 8, Bb 8, Bc 8, Bd 8, Be 8, Bf 80, Bg 8, Bh 8, Bi 8, Bj 8, Bk 8, Bl 8, Bm 8, Bn 22, Bz 8 |
| Ca 7, Cb 33, Cd 27, Ce 52, Cf 7, Cg 8, Ci 8, Cj 7, Cl 33, Cm 12 |
| Ea 8, Ec 8, Eg 8, Ek 8, En 22, Ez 8 |
| Fa 7, Fd 27 |
| Ta 12, Tb 11, Tc 8, Td 10, Th 73, Tq 7 |

Length tables: A $0B03E6 (a-z, A-J), B $0B040A, C $0B0424, E $0B0431,
F $0B044B, T $0B044F; the value is the frame length minus 6.

Handlers (first call in each case of the second-level dispatch):

| Letter | Dispatch | Handlers |
|---|---|---|
| A | $0AD03E | Aa $0ACF46, Ab $0AC9F0, Ac $0AC61C, Ad $0ACAEA, Ae $0ACB32, Af $0ACA4E, Ah $0ACF02, Ai $0ACE30, Aj $0AC85A, Ak $0AC764, Al $0AC806, Am $0ACE8E, An $0AC226, Ao $0AC32A, Ap $0AC39C, Aq $0AC90A, Ar $0ACC30, At $0AC938, Au $0AC254, Av $0AC8B6, Aw $0AD2B6, Ax $0ACB7A, Ay $0ACBD2, Az $0ACBA8, AA $0AC8E4, AB $0AC2CE, AC $0AC7B2, AD $0AC5CC, AE $0ACBFC, AF $0ACE02, AG $0AC966, AH $0ACDB4, AI $0ACD6C, AJ $0AC73C |
| B | $0A4712 | Each rate command saves its rate ($0ADE56), then: Ba $0AA750, Bb $0AAF74, Bc $0A8CD6, Bd $0A8768, Be $0A8560 (sends 34 @@Cb-format frames: the almanac pages), Bf $0A937E (ephemeris input, acknowledged with @@Cc), Bg $0AAE4E, Bh $0AA822, Bi $0A8EA8 (ephemeris output; sent as @@Bf by the scheduler), Bj $0A9410, Bk $0A8F5C, Bm $0AACA8, Bn $0AAF2C, Bz $0A8D22 (always @@Ez, 8 x $2C records from $27BC) |
| C | $0A5288 | In fix mode ($C651 = 1) Ca, Cf, Cg stop the channels ($088F78), write the EEPROM and go idle; when idle, Ca runs the self-test and Cf invalidates every EEPROM checksum (+$11111111, `eeprom_invalidate_all` $09AEA8) so defaults load, Cg 1 enters fix mode; Cb $0A9160, Cd $0A4FC2, Ce $0A508E, Ci $0A6CB2 or $0A6C3C (protocol mode 1 or 2), Cj $0A9F04, Cl $0A51D2, Cm $0A55C4 |
| E | $0A4C44 | If-chain: Ea, Ec, Eg, Ek (save rate, then output), En (TRAIM: validates date and time fields) |
| F | $0A54EA | Jump table, Fa-Fd |
| T | $0A4EDE | Ta $0AD4F4, Tb $0AD754, Tc $0A8B0E, Td $0A8BAA (output builders, called by the scheduler), Th $0AE7CE (writes the ID block), Tq $0A1E90 |

EEPROM write routine $09B2DE (address, source, count): writes only
bytes that differ, waits for completion after each, and sets bit 1 of
RAM $7A38 if a byte reads back wrong.

### Date and time

HP's 58503A host (3704) power-up script sends no @@Ac or @@Aa
(smartclock docs/firmware/oncore/58503a-3704.txt, script $53: Cg, Cj,
Ab, Ah 2, Aj 0, Ak 10, Al 60, An 1, Ao 49, Aq 1, Ar 1, Ar 65, Av 0,
Aw 0, Ax 0, Ay 0, AB 4, AC 120, AD 0 32, output rates, Cg 1), so the
receiver's boot date in an instrument comes from the T1 alone.

That script in firmware terms (values in the reference's units):

| Command | Value | Effect in the firmware |
|---|---|---|
| @@Ah | 2 | Search in elevation order ($9D0C = 1) |
| @@Aj | 0 | Rank SV sets by GDOP (reference: 0 GDOP, 1 PDOP, 2 HDOP, 3 VDOP, 4 TDOP; the firmware stores them as internal 0, 2, 1, 4, 3) |
| @@Ak | 10 | Set-switch hysteresis 1.0 |
| @@Al | 60 | 3D PDOP limit 6.0 |
| @@AC | 120 | 2D HDOP limit 12.0 |
| @@AD | 0, 32 | Smoothing count needed for a fix 0; @@Bh threshold 32 |
| @@An | 1 | Adopt almanacs collected from the satellites |
| @@Ao | 49 | WGS-84 |
| @@Aq | 1 | Klobuchar on |
| @@Ar | 1, then 65 to software 8.4 only | 1 = all in view.  65 also sets bit 6, not in the reference: adaptive state-5 weighting off, PLL loss limit 6 instead of 90 |
| @@Aw | 0 | GPS time |
| @@AB | 4 | Static: `doppler_bin_sel` ($8EB8) = 4.  The reference gives about 2 min of almanac-aided search before the full-sky search for Static, 30 min for Land (2, the default and this unit's EEPROM value) |

@@AB values: 0 Air, 1 Handheld, 2 Land, 3 Marine, 4 Static
(`cmd_AB` $0AC2CE); $8EB8 picks the Doppler bin tables ($0AEEA2 +
$8EB8 x 24) and is saved in EEPROM block $1098.  This unit is software
VER 8 REV 8, so the 3704 host would send it only @@Ar 1.

Record (RAM $8EBA, and on the stack): +0 month, +1 day, +2 hour,
+3 minute, +4 year (word), +6 seconds (double).  $09C81A(record) ->
GPS week mod 1024 ($81AE) and seconds ($819E); $09CE90 does the
reverse, with year hint $81C4 choosing the 1024-week epoch and $9D86
= 1 applying UTC.

Boot ($08A714 -> $09B4AC RTC init, $09BD66 -> $09C52C, then the
almanac check in $09BEDE):

| Step | Detail |
|---|---|
| Year hint | $81C4 = EEPROM $118C/$118E per $1190; default 1992 |
| RTC read | $09B138: no RTC ($2754 = 0) -> month 13, day 32, 25:60:60, 1999; else $09B5DE, year < 50 -> 20xx, else 19xx |
| Valid | 1980-2070, month 1-12, day 1-31, h <= 23, min <= 59, 0 <= s <= 60.0: $CC5 = 1, week/seconds from the RTC + 2.0 s |
| Invalid | $CC5 = 0, 01/01/<$81C4> 12:00:00.0 ($09C69C) |
| Display | $08B1A4 rebuilds $8EBA from $81AE/$819E, $81C4, $819C, $9D86 |

RTC write ($09FE2A): $8EBA minus local offset $819C/$819D; if
$9D86 = 1, plus GPS - UTC ($26AE), passed to `gps_to_date` as its last
argument ($09D438); then $09B202 (date) and $09B25A (time).  The RTC
holds GPS time.  If $9D82 = 1, $81C4 is saved to the other EEPROM year
slot and $1190 flipped.

| Trigger | Flags | Task |
|---|---|---|
| @@Ac (valid) | $81C4 = year, $9D82 = 1, $8E7 | $09FE06: $09FE2A, then $CC5 = 1 |
| @@Aa (valid) | $8E8 | $09FF78: $09FE2A (date from $8EBA, time from the command) |
| Mode-3 $C6 input ($0ADA48) | $8E7, $8E8 | Both |
| GPS time, $08C67C (from $08B248 while $25FA = 0) | $25FA = 1, $8E7, $8E8 | Both; needs a channel state byte ($6FBD + $B6 x n) > 6 and $22F2 = 1 (subframe-1 week stored, $09A816); once per $08B1A4 |

No periodic RTC write.  No time or week stored in EEPROM.

| RAM | Use |
|---|---|
| $81C4 | Year hint (word) |
| $CC5 | Time known: RTC valid at boot, @@Ac task, or navigation with $22F2 set ($08FD50); gates the almanac recheck ($08C718) |
| $9D82 | Save year to EEPROM; set by @@Ac, cleared by $09FDFC (init) and $09FE2A |
| $9D86 | 1 = UTC, 0 = GPS; @@Aw ($0AD2B6), protocol-mode entries ($0A63F2, $0A6CB2, $0A6D36); EEPROM block $1098 |
| $25FA | GPS-time RTC write done; cleared by $08B1A4 |
| $22F2 | Week from subframe 1 stored ($09A816); cleared by $097BDE |

Readers of the RTC after boot: @@Ac/@@Aa replies in binary mode ($C651
!= 1; tasks $0AB3B2 -> $0A2CEE, $0AB406 -> $0A41D2) report the RTC
date/time plus $819C/$819D; with $C651 = 1 they report $8EBA.  @@Ea
uses $8EBA.  Self-test $0A1D24 (from $0A1D8C): two RTC reads ~120 x
$09D446 apart, equal seconds -> $2000.

2026-11-26 date 01/03/2026 ~08:22 = week 2399 (95 mod 256) = the
fallback 01/01/2026 12:00:00 (EEPROM year 2026) + 44 h 22 min
(inferred: RTC first time-up set at power-up).

### @@Ea receiver status byte

Built by $0A9BEC (via $0AA772) from RAM $8F1E, which $08ABD4 assembles:

| Bit | Source | Meaning |
|---|---|---|
| 7 | $25F7 = 1 | Set by $08FD50 (navigation step) |
| 6 | mode 6 | Navigation mode ($25F6, copy of $0B6C) |
| 5 | mode 2 | 3D fix |
| 4 | mode 3 | 2D fix |
| 3 | mode 1 or 4 | Mode 4: position hold ($8208 set; $093906) |
| 2 | $75C2 != 1 | Replaces $8F1E bit 2 ($25F8) at output; $75C2 cleared while corrections are younger than $79F6 x 10 ($09BC0A) |
| 1 | count = 0 | $37A2 (or $42B6) zero |
| 0 | $25F4 = 1 | Almanac not usable |

Navigation mode to bit (table at $08AC48): 0 none, 1 bit 3, 2 bit 5,
3 bit 4, 4 bit 3, 5 none, 6 bit 6.

Almanac check $09C782(almanac week, current week $81AE), both mod
256: usable if the current week is at most 2 weeks before and at most
127 weeks after the almanac week.  Run at almanac load ($09BEDE);
if it fails there, rerun once when the time is known ($08C718).
Otherwise the receiver searches without the almanac.

@@Ac (date, $0AC61C) accepts month 1-12, day 1-31, year 1990-2050;
@@Aa (time, $0ACF46) accepts h < 24, m < 60, s < 60.  Both update the
GPS week ($81AE) and seconds ($819E) through $09C81A.

2026-11-26, 8ch on the jig, antenna connected: status $09, 0 visible
for 12.5 min; receiver date 01/03/2026 (week 95), EEPROM almanac week
byte 142 (week 2446), rejected.  After @@Ac/@@Aa with UTC: status $08,
9 visible, 6 tracked (mode 8, flags $A0) within 12 s; $25F6 = 4,
$81EE = 2.  No antenna-sense bit in @@Ea.

2026-11-27, jig power cut (length not logged), Static, DCXO record $50 /
185 / 20625, no @@Ac/@@Aa: the RTC kept the date; time 8 s behind
UTC until the first satellite.  9 visible at once; first channel
used in a fix about 48 s after power-up (port back to fix).

### Protocol modes

RAM $C650, saved at EEPROM $0920 (this unit: 0).  Name table at
$0B0460: "BIN", "NMEA", "T", "Z".  Baud select $0A20A2(n): 0 9600,
1 4800, 2 2400, 3 1200, 4 19200.

| Mode | Serial | Entered by | Validate / dispatch / periodic |
|---|---|---|---|
| 0 Motorola binary | 9600 8N1 | $0A82AE (from NMEA `FOR,0`, mode-2 `BIN`) | $0A68C0 / $0A4644 / $0A21B4 |
| 1 NMEA | 4800 8N1 | @@Ci 1 ($0A6CB2); mode-2 `NMEA` ($0A63F2) | $0A839A / $0A7BCC / $0A80A6 |
| 2 terse ASCII | 1200 8N1 | @@Ci 2 ($0A6C3C); NMEA `$PMOTG,FOR,2` | $0A643C / $0A6006 / $0A6322 |
| 3 7-bit framed binary | 2400 8E1 | EEPROM $0920 = 3 at boot only; setter $0A6D36 has no callers | $0ADC86 / $0AD9FE / $0ADA36 |

Mode 1 (NMEA) input: `$PMOTG,<id>,<n>[*hh]` only; table at $0B04AC
(PMOTG, GGA, GLL, GSA, GSV, RMC, VTG, ZDA, FOR).  For GGA-ZDA, n is the
output rate 0-9999 (0 = off), saved at EEPROM $0922 + 2n ($0ADE12),
and the sentence is sent once at once.  `FOR,0` -> binary, `FOR,2` ->
mode 2.  Outputs: $GPGGA ($0A6EC8), $GPGLL ($0A7158), $GPGSA
($0A720E), $GPGSV ($0A739C), $GPRMC ($0A76E2), $GPVTG ($0A78E0),
$GPZDA ($0A7ACA); format strings $0B04F1-$0B0646, ending `*%02X\r\n`.

Mode 2 input (CR LF, no checksum): `BIN` -> mode 0; `NMEA` -> mode 1;
`T<n>` sets the report rate (EEPROM $0930) and echoes `T%04d`; `Z`
sends one report.  Report ($0A5E9A): degrees, minutes and hundredths
of a minute, N/S, E/W; speed in mph (RAM $8F40 x 2.236936292; the 514
cap applies to m/s before conversion), heading (RAM $8F38, max 999),
fix flag.

Mode 3: frames start with a byte with bit 7 set, carry 7-bit payload
bytes, end with $DA, then XOR(payload) ^ $DA.  Poll: $CD "OTOROLA"
$DA $9E ("MOTOROLA" with bit 7 on the M).  Output frame $C6, 23 bytes
($0AD7E8): lat and lon (28-bit, 0.001 arcmin), time, height, speed x10,
heading x10, status, a count, fix flag.  Input $C6 frame ($0ADA48) sets
position and time; as decoded, its latitude parse looks off by one
(unverified).

### QSPI devices

All transfers polled, with interrupts masked; the QSPI interrupt is
never enabled.

| Device | Chip select | SPCR0 | Functions |
|---|---|---|---|
| MC68HC68T1 RTC | PCS0, active high | $A104 (8-bit, CPHA 1, ~2.1 MHz) | $09B4AC init and presence test (T1 RAM $00-$02), $09B5DE read time and date, $09B76C write date, $09B844 write time |
| DCXO control (HP's name; tunes the reference) | PCS1, active low | $A008 (8-bit, CPOL 0, CPHA 0, 1.05 MHz) | $09B31A(v), below |

RTC (MC68HC68T1, PCS0):

| Routine | Transfers |
|---|---|
| $09B4AC init | Write $80-$82 = FF 55 AA, read $00-$02; match: $2754 = 1, $B2 = $00 then $20 (power sense on; watchdog, power-down, alarm, periodic off); else $2754 = 0 |
| $09B5DE read | Read status $30; bit 4 (first time-up) clear: $B1 = $34 (stop), burst read $20-$26, $B1 = $B4 (start); set: BCD 60 s, 60 min, 25 h, day 32, month 13, year 99, then re-read status until bit 4 clears |
| $09B76C write date | $B1 = $34; $A4-$A6 = day, month, year mod 100; $B1 = $B4 |
| $09B844 write time | $B1 = $34; $A0-$A2 = s, min, h (24-hour); $B1 = $B4 |

- Clock control $34/$B4: 32.768 kHz crystal, CLKOUT low, stop/start.
  Not written at init; after a T1 power-on reset it stays $00
  (stopped) until the next read with first time-up clear or a write.
- Never written: day of week ($A3), alarms ($A8-$AA).
- RAM: $2754 present flag; BCD $2759 s, $2757 min, $2756 h, $2755 day,
  $2758 month, $275A year.
- BCD helpers: $09B0F6 (BCD to binary), $09B116 (binary to BCD).

QSM pins ($08A064): QPDR $F0, QPAR $7B, QDDR $FE; PCS0 idles low,
PCS1-3 high.  Command RAM values in the image: $7C (PCS1), $FF/$7F
(RTC); PCS2, PCS3 never asserted.

PCS1 transfer, $09B31A(v): six single-byte queues, each SPCR0 $A008,
SPCR2 $0000, CR $7C (CONT 0, BITSE, DT, DSCK), SPCR1 $CC01 (DSCKL 76
clocks = 4.5 us, DTL 32 clocks = 1.9 us), SPIF polled with interrupts
masked; dbf 15238 (~5.4 ms) after each.

| # | MOSI | MISO |
|---|---|---|
| 1 | $81 | |
| 2 | $00 | d1 |
| 3 | $C0 \| (v & $0F) << 1 | d2 |
| 4 | $01 | |
| 5 | $C1 \| (v >> 4) << 1 | |
| 6 | $01 | |

Returns (d1 << 4) \| (d2 & $0F): the previous value.

| Caller | When | Value |
|---|---|---|
| $09BD66 | Boot, after the EEPROM load $09C096 | $75BC |
| $097464 | Oscillator search step | Next list entry (stored to $75BC) |
| $0A02C0 | Task (flag $8E9) after @@Ta ($9D83) | $75BC |
| $0A1696 | Self-test $0A1D8C(1) | $5A, then $75BC; readback not $5A sets result bit $1000 |

Self-test result bits: $1000 PCS1 readback, $2000 RTC not advancing
($0A1D24), $100/$80 flash even/odd sum ($0A1CD4), $400/$200 RAM
($0A1C60), $C03F CS7 timing ($0A1A4E).

Not a commodity serial DAC or pot (CS per byte, nibble data,
readback).  QSPI SCK, MOSI and MISO (RTC pins 4, 5 and 6) and PCS1 (CPU pin 47) go
to the 24M01 hybrid (continuity, 6ch SSG0178541, *7VGN4*).  The DCXO
control is in the hybrid.
HP calls it the DCXO: the 58503B 1.01.04 host sends @@Ta for
`:DIAGnostic:GPSystem:DCXO`, and HP's console self-test lists "DCXO SPI"
(smartclock docs/firmware/gps.md, console.md).

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

## 6-channel receivers

| | SSG0163878 | SSG0178541 |
|---|---|---|
| Instrument | Z3805A (3625A01487) | Z3801A (3542A01548) |
| Board label | *7VENR* | *7VGN4* |
| Model (@@Cj) | B1121P1114 | B1121P1114 |
| Manufactured | 6G09 | 6J25 |
| Software | 98-P39972M VER 8 REV 4, 13 JUL 1995 | same |
| Options | IB | IB |
| DCXO record (@@Ta) | 20549, $5F, 351 Hz/LSB | 20749, $5C, 339 Hz/LSB |
| Self-test (@@Ca) | $0000, all pass | $0000, all pass |

Source for instrument and label: smartclock docs and pForth captures
(label pairing assumes each module stayed in its instrument).

- Host port: 9600 binary; no output until polled.
- Track only with about 20 dB more antenna gain than the 8ch needs
  (smartclock logs; bench: no track on the distribution-amplifier feed,
  tracking on a higher-gain feed).  SSG0178541 is the weaker.
- Top-side DC bias agrees between the boards at every node; the loss is
  in the RF path (A06, SAW filter, coupling, 58M01 input; inferred).

Top-side DC to ground, volts, powered from a USB-fed jig (ends A/B as
in the bias worksheet's photo callouts):

| Point | SSG0178541 | SSG0163878 |
|---|---|---|
| Supply | 4.75 | 4.80 |
| Antenna center pin | 4.67 | 4.73 |
| 2R2 by the 24M01, A / B | 4.73 / 4.69 | 4.80 / 4.75 |
| 2R2 middle, A / B | 4.69 / 4.67 | 4.75 / 4.75 |
| 2R2 bottom left, A / B | 0.765 / 0.766 | 0.765 / 0.767 |
| R27, A / B | 2.36 / 2.36 | 2.22 / 2.23 |
| R56, A / B | 2.36 / 2.36 | 2.23 / 2.23 |

## 6-channel firmware and EEPROM (read 2026-11-27)

Read from SSG0163878 (and the EEPROM of SSG0178541) over the host port with `tools/loader/dump.py`
(factory loader @@Tq, stub at $C000; the 6ch loader returns to $09EBBC).

| Image | Size | SHA-256 |
|---|---|---|
| `third_party/moto/B1121P1114-flash.bin` ($080000) | 256 KB | `7342ef6684d609daeb4d2b02461d89631837ee524c2799440407060d52384607` |
| `eeprom/B1121P1114-SSG0163878-eeprom.bin` ($020000) | 8 KB | `fe98b0fe0c92f5d6a03dac8001cc5c81e8d7bccb74efed6b9b5e7a3d2c94590d` |
| `eeprom/B1121P1114-SSG0178541-eeprom.bin` ($020000) | 8 KB | `06a4f02fb5a53a434812c59430084c127f3f42ca59b71e61dda2b1d73f68382c` |

- Flash trailer: even-byte sum $00E270E2, odd-byte sum $00FF0E12, both
  match the image.  SSG0178541's flash, read the same way, is identical.  Reset vectors SSP $00020000, PC $0008797C.
- ID strings at flash $2C74E: SFTW P/N 98-P39972M, VER 8, REV 4,
  13 JUL 1995.  EEPROM ID text at $087A, as on the 8ch: B1121P1114,
  SSG0163878, 6G09, options IB.

### 6ch firmware vs 8ch (Ghidra)

Project setup: `tools/ghidra-6ch/` (README there); decompiled C:
`third_party/moto/decompiled/B1121P1114.c`.  Same compiler and library
(ABCPU32.LIB, all 37 sections, at $0ACB54-$0B1540; 8ch - $3F7E).  557
functions: 546 paired with 8ch functions by instruction stream, in the
same order; 11 named by hand.  Names below are the 8ch names.

| Item | 6ch | 8ch |
|---|---|---|
| Reset entry | $08797C | $089F5C |
| Channels | 6 (loops `< 6`, e.g. `osc_offset_update` $089B7C) | 8 |
| Commands | Same A, B, C, T sets and frame lengths (tables $0AC48A, $0AC4AE, $0AC4C8, $0AC4D5); no @@E*, @@F* (`bin_dispatch` $0A1130) | + E, F |
| Factory loader @@Tq | `cmd_Tq` $09EB54 | $0A1E90 |
| Scheduler tasks | 26, request flags $630-$649, same order | $8C2-$8DB |
| Application type (@@AB) | `cmd_AB` $0A8582, RAM $804A, EEPROM $1096, default 2 Land (`ee_blk_1098` $09B9DA) | $0AC2CE, $8EB8, $109C |
| Doppler strategy tables | Pointers $0AAF82 + type x 24 + 4 x strategy; tables $0AAB72-$0AAF81 | $0AEEA2; $0AEA92-$0AEEA1, byte-identical (pointers 8ch - $3F20) |
| @@Ta | `cmd_Ta` $0A97A8: same payload, same limits (word < $8000, byte < $D8, word < $8000) | $0AD4F4 |
| DCXO RAM | setting $6960, Hz/LSB $6962, drift reference $6964 | $75BC, $75BE, $75C0 |
| DCXO default (no valid record) | $67, 350, 20625 (`eeprom_load_all` $098E3E) | same |
| Boot trim | `osc_offset_update` $089B7C: same rule (>10 fixes, \|drift - 20625\| < 3131, < 151 no change, else 20% step; otherwise setting -= (drift - 20625) / Hz/LSB) | $08C252 |
| Fix/idle mode | RAM $B457, EEPROM $0919; restored at boot (`io_init` $0A2020); `boot_init` $088134 starts the channels only if 1 | $C651, $091F |
| Year hint | EEPROM $1186/$1188, selector $118A; invalid: 1992 | $118C/$118E, $1190 |
| RTC at boot | `boot_time_set` $0992D4: same validity test and fallback 01/01/<year hint> 12:00:00; no +2.0 s and no PPS hardware delay | $09C52C |
| Almanac week check | `almanac_week_ok` $09947E: -2..+127 weeks, identical code; called from `almanac_load` $098C86 and the recheck $08A042 | $09C782 |
| RTC write tasks | `task_rtc_write_date` $09CB02, `task_rtc_write_time` $09CC74 (`rtc_write_from_time` $09CB26) | $09FE06, $09FF78 |
| @@Cf | `eeprom_invalidate_all` $097C8E: blocks $1020-$1154, both almanac banks, position records, UTC, ionosphere (checksum + $11111111); not the DCXO records | $09AEA8 |

### 6ch EEPROM layout (from the firmware; checksums verified on both dumps)

Same checksum ($FFFFFFFF minus each byte, long after the block).  Up to
$092D the 8ch layout less 6 bytes of message rates; from $1020 every 8ch
block moved down by 6.

| Offset | Length | Checksum at | Contents | SSG0163878 | SSG0178541 |
|---|---|---|---|---|---|
| $087A | 1 | | @@Bh threshold (`bh_min`; > $7F reset to $20) | $20 | $20 |
| $087B-$0904 | 138 | $0905 (XOR) | ID text | B1121P1114, SSG0163878, 6G09, IB | B1121P1114, SSG0178541, 6J25, IB |
| $0906 + n | 19 x 1 | | Binary output rates (8ch: 25) | 0 | 0 |
| $0919 | 1 | | Fix/idle mode (1 = fix) | 1 | 1 |
| $091A | 1 | | Protocol mode | 0 | 0 |
| $091B | 1 | | 8ch $0921 setting | 0 | 0 |
| $091C + 2n | 8 x 2 | | NMEA/terse rates | 0 | 0 |
| $092C, $092D | 2 | | Test bytes | 00 80 | 00 80 |
| $092E | 884 | $0CA2 | Almanac bank 0; week byte at +$36F | valid, week 138 | valid, week 138 |
| $0CA6 | 884 | $101A | Almanac bank 1 | invalidated (+$11111111) | valid, week 138 |
| $101E | 1 | | Almanac bank state | 0 | 1 |
| $1020 | 28 | $103C | 8ch $1026 (An, Aj, Ak, AC, Al, Ag) | defaults 01 02 1.0 12.0 6.0 $0A | same |
| $1040 | 78 | $108E | 8ch $1046 (datum, position) | datum 49, a, 1/f; position 0 | same |
| $1092 | 6 | $1098 | 8ch $1098: @@Ar b6, UTC, @@Aq, @@Ar b7, application type ($1096), pad | 01 01 01 01 02 | same |
| $109C | 42 | $10C6 | 8ch $10A2 (@@Ar b0, ignore list, @@Ah, @@AD) | defaults | same |
| $10CA | 42 | $10F4 | 8ch $10D0 (AJ, Ab, Ax, Az, Ay, 1PPS, TRAIM, En) | defaults, TRAIM off | same |
| $10F8, $1126 | 42 + 42 | $1122, $1150 | 8ch $10FE, $112C (datums $32, $33) | defaults | same |
| $1154 | 12 | $1160 | 8ch $115A (@@AF-@@AI) | 7, 1, 0, 3704.0 | same |
| $1164 | 6 | $116A | DCXO record 0: setting, byte, Hz/LSB, drift reference | $5F, $06, 351, 20549 | $5C, $03, 339, 20749 |
| $116E | 6 | $1174 | DCXO record 1 | $5F, $01, 351, 20584 | $5C, $08, 339, 20789 |
| $1178 | 1 | | Active DCXO record | 0 | 0 |
| $117A | 8 | $1182 | Ionosphere | invalidated | invalidated |
| $1186, $1188 | 2 + 2 | | Year slots | 2026, 2026 | 2026, 2026 |
| $118A | 1 | | Year selector | 0 | 0 |
| $118C, $11A8 | 28 + 28 | $11A4, $11C0 | Position records (h, lat, lon) | rec 1 = the bench position copied from the 8ch (@@Ad-@@Af, 2026-11-27); rec 0 partial | rec 0 = the same; rec 1 partial |
| $11C4 | 1 | | Position selector | 1 | 0 |
| $11C6 | 14 | $11D4 | UTC parameters | invalidated | invalidated |

- Active DCXO records match the live @@Ta replies (20549/$5F/351,
  20749/$5C/339).
- Invalidated blocks (ionosphere, UTC, one almanac bank on SSG0163878):
  the @@Cf mark (inferred: not rewritten since).
- Fix mode is saved at $0919 and restored at boot; 0 boots idle until
  @@Cg 1 (which writes 1).  @@Cg 0, and @@Ca or @@Cf in fix mode, write
  0 (`cmd_C` $0A1A82).  Idle: no measurement/navigation tasks, so time
  does not advance (inferred).
- Host instruments (smartclock docs/firmware/gps.md, 58503A 3704,
  Z3801A 3543, Z3805A 3543B): every power-up sends @@Cg 1 (self-test
  script after @@Ca, init script after @@Cj and set-up echoes, hold
  script); @@Cg 0 only as the first half of a 0-then-1 pair.  An idle
  6ch here answered @@Cj, @@Ba and @@Ca, so an instrument takes an idle
  engine out of idle at power-up.
- An RTC year of 44 reads as 2044, inside the 1980-2070 check, so it is
  accepted (inferred).

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

### Antenna gain (Motorola notes in third_party/moto)

| | VP Oncore (vponcoreengnotesarchive.pdf, p. 5) | UT Oncore (ut-oncore-engnotes.pdf) |
|---|---|---|
| Net gain at the receiver input | Preamplifier 18 dB minimum, including 6 dB cable loss | 10 to 33 dB; "performance will degrade" outside |
| Noise figure | 2.5 dB maximum | 2.2 dB maximum (system 4 dB) |
| Antenna power at the connector | +5 V, 25 mA available | 80 mA maximum, 45 mA short circuit |
| Thresholds (0 dBic antenna, no cable) | Acquisition -137 dBm, tracking -143 dBm | |

No onboard LNA unless the passive-antenna option (23 dB) is fitted.

### RF architecture (hypothesis, all inferred)

```
feed -> A06 -> 95U01 SAW -> 58M01 ---------------------------> XCT38140 (CS7)
        LNA    L1 band-     LNA? mixer(s) IF/AGC quantizer      8 channels
                            ^ LO synthesizer (PLL)              ^ clock f/2
24M0x hybrid: TCXO f = 19.09575 MHz, PCS1 trim; ASIC: /656 -> EXTAL -+
```

| Part | Basis |
|---|---|
| A06 first LNA | SOT-23 at the SAW's antenna end (photos) |
| 58M01 LNA, mixer, synthesizer, IF, 1-2 bit quantizer | 32-pad custom part at the SAW's other end; the correlator needs sampled bits and no other part can supply them |
| LO and sample clock locked to f, total x82.5 | 82.5 f = L1 - 20625 Hz exactly, the drift target |
| Digital clock f/2 = 9.547875 MHz | 1PPS arithmetic uses 9548000.0 ($091BFA) |
| Trim on the TCXO | 185 Hz at L1 per LSB = 2.2 Hz at f (0.12 ppm), range +-25 ppm: a crystal-pulling range |

Frequency plans that give 82.5:

- A: LO = 82 f = 1565.85 MHz, IF 9.568 MHz = f/2 + 20.625 kHz,
  aliased to 20.625 kHz by sampling at f/2 (needs I/Q or another rate:
  real sampling folds a 2 MHz band this close to 0).
- B: two conversions, e.g. LO1 = 80 f = 1527.66 MHz (IF 47.76 MHz),
  LO2 = 2.5 f.

Photos (6ch, top side): the antenna center pad's trace enters the
top-side RF can beside the 2R2, R27 and R56 inductors (bias chokes,
inferred), so the first RF stage may be on the top side, not the A06.
An empty 8-pad footprint in that can on both 6ch boards (option,
inferred).  The 8ch board (TM-AA, 84D44800) has a different RF layout
from the 6ch (TM-AB, 84D43579), so its voltages are not a pin-for-pin
reference.  SOT-23s beside the top antenna pads: antenna DC feed
(inferred); the SOIC-8 there is the MC33064 reset supervisor.

Tests: LO leakage near the open can (1565.85 vs 1527.66 MHz); clock and
data on the 58M01-to-ASIC lines (9.548 MHz clock expected); DC at both
ends of each top-can inductor on the two 6ch boards; DC on the antenna
center pin (feed circuit only).

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

MC68331 pin numbers used in these notes (MC68331 user's manual, Figure
3-2, 132-pin package): 43 MISO, 44 MOSI, 45 SCK, 46 PCS0, 47 PCS1,
62 EXTAL, 124 CS9, 125 CS10.  Test-point pins: docs/board.md.

Receiver 10-pin power/I/O header (2 x 5, beside the CPU; Oncore
user's guide pinout, supplied by the owner):

| Pin | Name | Function |
|---|---|---|
| 1 | BATT | Externally applied backup power (RTC, RAM); not supplied by the jigs or the 58503A |
| 2 | PWR | 5 V main power |
| 3 | GND | Ground |
| 4 | VPP | Flash reprogramming voltage |
| 5 | N/A | Not used |
| 6 | 1PPS | Timing pulse |
| 7 | 1PPS RTN | Timing pulse return |
| 8 | TTL TXD1 | Primary output |
| 9 | TTL RXD1 | Primary input |
| 10 | TTL RTN | Return |

MC68HC68T1, 16-pin (third_party/moto/MC68HC68.pdf, REV 2):

1 CLKOUT, 2 CPUR, 3 INT, 4 SCK, 5 MOSI, 6 MISO, 7 SS, 8 VSS, 9 PSE,
10 POR, 11 LINE, 12 VSYS, 13 VBATT, 14 XTALin, 15 XTALout, 16 VDD.

## Open

- 6ch sensitivity loss: RF path from the antenna pin to the 58M01
  (continuity, A06 bias on the bottom side), with the board off the jig.
- Confirm the RF chain antenna -> A06 -> 95U01 SAW -> 58M01 by
  continuity.
- TCXO frequency error: 0.17 ppm from the drift (sign not determined);
  a counter on the hybrid would confirm.
- Hardware behind CS0-CS3, CS5, CS6 (unused by this firmware, except
  one CS5 read).  CS7 chip not traced (XCT38140 likely).
- 6ch receivers: why they don't acquire with almanac, position and date
  set (20 dB inline gain test pending).
