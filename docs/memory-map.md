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
| $087A | 1 | | Setting written by the @@AD handler ($0AC5CC, second byte if < $80); default $20 ($0A29BC).  The ID text starts at $087B |
| $0906 + n | 1 each | | Output rate of binary message n, written by the B and E rate commands through $0ADE56 (RAM copy at $C61E + 2n) |
| $091F | 1 | | Set to 1 the first time the protocol mode changes ($0A6C3C, $0A6CB2) |
| $0920 | 1 | | Protocol mode: 0 = Motorola binary, 1 = NMEA, 2 = third mode (RAM $C650) |
| $0921 | 1 | | @@AE setting, 0 or 1 ($0ACBFC); default 0 |
| $0922 + n | 1 each | | Per-message setting written through $0ADE12 (RAM $CAF8) |
| $0932, $0933 | 1 + 1 | | Test bytes: written $00 and $80 and read back at start ($09C096); bit 7 polled for write completion ($09B2A6) |
| $0934 | 884 | $0CA8 | Almanac bank 0 |
| $0CAC | 884 | $1020 | Almanac bank 1 |
| $1024 | 1 | | Active almanac bank (0 or 1); dump: 1 |
| $1026 | 28 | $1042 | Unknown: $01 $02, doubles 1.0, 12.0, 6.0, $0A $AA |
| $1046 | 78 | $1094 | 10 bytes 0; 3 doubles 0; 1/f; a; datum number $31 (49 = WGS-84 in Oncore numbering, inferred); pad; doubles 12.65, lat 0.6523 rad, lon -2.1309 rad; $01 $00 |
| $1098 | 6 | $109E | Unknown: 01 01 01 01 02 AA |
| $10A2 | 42 | $10CC | Unknown: $01 $AA, rest 0 |
| $10D0 | 42 | $10FA | Unknown: mixed bytes incl. $03E8 (1000) |
| $10FE | 42 | $1128 | 3 doubles 0; 1/f; a; $32 |
| $112C | 42 | $1156 | 3 doubles 0; 1/f; a; $33 |
| $115A | 12 | $1166 | Unknown: 00 07 01 00 40 AC F0 00 00 00 00 00 |
| $116A | 6 | $1170 | Record 0: byte, word, word ($50, $00B9, $5055) |
| $1174 | 6 | $117A | Record 1: byte, word, word ($50, $00B9, $501B) |
| $117E | 1 | | Active record (0 or 1); dump: 1.  If invalid, defaults $67, $015E, $5091 ($09C096) |
| $1180 | 8 | $1188 | Unknown: 11 FE FE 02 3C F5 FE 0C |
| $11CC | 14 | $11DA | Unknown: 12 12 24 8E 89 07 00 00 00 00 00 00 00 01 |

Almanac bank ($09BEDE): 32 records of 24 bytes at bank + $18 x SV
(SV 1-32), then 2 records of 24 bytes at bank + $318, then 44 bytes at
bank + $348; copied to RAM when the checksum matches.

Block loaders ($09E88E, $09EA04, $09ECDE, $09EE0E, $09EF44, $09F226,
$09F52A, $09F636, $09F72A) dispatch on an argument: 2 = load from
EEPROM, 3 = defaults, 4 = save changed fields ($09F81E).

Block $1046 ($09EA04): +0-9 -> RAM $633C (written by @@Au, @@Av);
+10-49, five doubles -> RAM $7592, defaults 0, 0, 0, 298.257223563,
6378137.0 (datum: three offsets, 1/f, a), +50 datum number, default 49
-> $7592+40 (written by @@Ao); +52-75, three doubles -> RAM $81F0
(written by $0ACC96, which also fills +60/+68 lat/lon order as
stored), +76 -> $8208 (written by @@At), +77 -> $8209.

RAM copies and the commands whose handlers write them:

| Block | RAM | Written by |
|---|---|---|
| $1026 | $633A, $79F6-$79FF, $8FAE-$8FB1 | @@An ($633A), @@AJ ($79F6), @@Ak ($79F8-$79FF) |
| $1098 | $369A, $81C2, $8216, $8EB8-$8EBD, $9D86 | @@Ar ($369A, $8216), @@Aq ($81C2), @@Aa ($8EBC-$8EBD), @@Aw ($9D86) |
| $10A2 | $7A3A, $81DA, $8F88-$8F8A, $9D0C | @@AD ($7A3A), @@Ar, @@Ce ($81DA), @@Ah ($9D0C) |
| $10D0 | $9D0E-$9D12, $819C-$819E, $820A, $79F6 | @@Ab ($819C), @@Az ($820A), @@Aw ($9D0F), B/E commands ($9D0E-$9D12) |
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

Consistent with HP's B3640 Motorola 68000 Family C Cross Compiler
(manual B3640-97001, Jan 1994), the same as smartclock's finding for
the SmartClock firmware.  Not proven: no version string in the image.

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

### CS7: the tracking channels

8 channels, channel n at $FFE800 + $10 x n.  Read in the level-5
interrupt every C/A code epoch (1 ms, inferred from the counters
below); written by the handler's loops and by channel setup ($088E96).

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
| $0F0 | Written 0 | $088F78 |
| $0F2 | Written 0 first thing in each level-5 interrupt (acknowledge); used to time interrupts against SYNCR in a self-test | $0805B2, $0A1A4E |
| $0F4 | Control word, shadow in RAM $2D8: init $86 then $87; bits 0, 1, 2 set and cleared by $088F78-$088FAA | $088FB8 |

Tracking loop ($0805B2), per channel:

- Epoch counters: RAM $2D6 counts 50, $100 counts 20, $78 is the
  0-19 position in the 20 ms data bit.
- Code phase in L1 cycles, RAM $382 + 4n, modulo $1809FC = 1,575,420
  (L1 cycles per 1 ms code period), advanced by the carrier phase
  read from +$0 (carrier aiding).  Slew steps of $37 = 55 cycles.
- Pairs of 1 ms samples are summed (2 ms coherent integration).
- State per channel, RAM $B0 + n: 0 = search (envelope max + 3/8 min
  against a running noise floor; slews code phase until detection),
  1 = code tracking, 4 = frequency tracking (phase from a 32 x 32
  arctangent table at $088A20), 5 = carrier phase tracking (second
  order filter, coefficients -$7971 and $39C3, output to +$0).
- Requested SV per channel: RAM $E0 + n; $088E96 restarts a channel
  when it differs from RAM $E8 + n.
- Other windows: CS5 $FFE000 read by $08A714; CS0/CS2/CS3, CS1, CS6:
  few references, not yet identified.
- Reset entry $089F5C also sets PEPAR $37, DDRE $C8, PORTE1 $08,
  PFPAR $FB, GPTMCR $2000, DDRGP $DF, PORTGP $C1, CFORC $03.

## Host commands (from the firmware)

Receive path: SCI interrupt ($08048E) -> ring buffer RAM $9DAE-$A52D
(1920 bytes; write pointer $A532, read pointer $A536, count $A53A) ->
line assembly at RAM $A9B2 ($0A65D6) -> on CR LF, validate ($0A69E2)
and dispatch ($0A46DC) by protocol mode (RAM $C650: 0 binary, 1 NMEA,
2 and 3 other; mode 3 frames on byte $DA).

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
| B | $0A4712 | Each rate command saves its rate ($0ADE56), then: Ba $0AA750, Bb $0AAF74, Bc $0A8CD6, Bd $0A8768, Be $0A8560, Bf $0A937E, Bg $0AAE4E, Bh $0AA822, Bi $0A8EA8, Bj $0A9410, Bk $0A8F5C, Bm $0AACA8, Bn $0AAF2C, Bz $0A8D22 |
| C | $0A5288 | Ca, Cf, Cg stop the channels ($088F78) and write the EEPROM; Cb $0A9160, Cd $0A4FC2, Ce $0A508E, Ci $0A6CB2 or $0A6C3C (protocol mode 1 or 2), Cj $0A9F04, Cl $0A51D2, Cm $0A55C4 |
| E | $0A4C44 | If-chain: Ea, Ec, Eg, Ek (save rate, then output), En (TRAIM: validates date and time fields) |
| F | $0A54EA | Jump table, Fa-Fd |
| T | $0A4EDE | Ta $0AD4F4, Tb $0AD754, Tc $0A8B0E, Td $0A8BAA, Th $0AE7CE (writes the ID block), Tq $0A1E90 |

EEPROM write routine $09B2DE (address, source, count): writes only
bytes that differ, waits for completion after each, and sets bit 1 of
RAM $7A38 if a byte reads back wrong.

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
- Devices behind CS0-CS3, CS5, CS6.  CS7 ($FFE800) is per-channel
  hardware (8 x 16-byte blocks, level-5 interrupt); chip not traced
  (XCT38140 likely).
- EEPROM field meanings for the "Unknown" blocks.
- Whether CS9/CS10 have chips fitted.
