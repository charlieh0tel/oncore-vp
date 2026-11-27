# Binary output messages (from the firmware)

Frame: `@@`, 2 letters, payload, XOR checksum (letters + payload, `out_end` $0AB0D2), CR LF.  Length = payload + 7.  Fields big-endian.  "x s" = double x s, rounded (`out_double` $0AB166, `fmt_scaled` $0A6A40).  Angles: `rad_to_mas` $0A6B08 (milliarcseconds).  Channel record: $6FBC + $B6 n.

## Slots

`out_ptr[n]` $C518 + 4n, rate $C61E + 2n, countdown $C61F + 2n.  A message is built only when `out_ptr[n]` equals its buffer start (previous frame flushed).

| n | Msg | Builder | Buffer | Trigger in bin_output_sched |
|---|---|---|---|---|
| 0 | Ba | $0AA750 | $BCDE | rate |
| 1 | Bk | $0A8F5C | $BB49 | rate |
| 2 | Bg | $0AAE4E | $BD56 | rate; option C/D/E |
| 3 | Bh | $0AA822 | $BD22 | rate; option B |
| 4 | Bb | $0AAF74 | $BE0B | rate byte on; $37A0 changed |
| 5 | Bc | $0A8CD6 | $B70E | rate byte on; $37A0 changed |
| 6 | Bd | $0A8768 | $B6F7 | rate byte on; almanac id (RAM or EEPROM) changed |
| 7 | Bf (ephemeris) | $0A8EA8 | $B8C9 | rate byte on (@@Bi); per channel when SV or IODE changed and $7A3C valid |
| 8 | Be | $0A8560 | $B295 | rate byte on; almanac id changed |
| 10 | replies | various | ring $C12A-$C511 | |
| 11 | Ez | $0A8D22 | $B760 | $C61B |
| 12 | Bj | $0A9410 | $BCD6 | query only |
| 13 | Tc | $0A8B0E | $BE67 | $C619 |
| 14 | Td | $0A8BAA | $BEA4 | $C61A |
| 15 | Bl | $0A88BA | $BB8E | $C63C and $6346 = 1; ch 0-7 |
| 17 | Bm | $0AACA8 | $BEDB | rate; option G; scheduled entry sets letters but does not call the builder |
| 18 | Bn | $0AAF2C | $BDD0 | rate; option I |
| 19 | Ea | $0AA772 | $BFC7 | rate |
| 20 | Ek | $0A8F82 | $BF80 | rate |
| 21 | Eg | $0AAE72 | $C047 | rate; option C/D/E |
| 22 | Bh (ch 6-7) | $0AA9A0 | $C013 | rate (set by @@Bh); option B |
| 23 | Ec | $0A8CFC | $BF1C | rate byte on; $37A0 changed |
| 24 | En | $0AAF50 | $C0E5 | rate; option I |

## Ba / Ea ($0A9BEC; Ba 6 ch, 68 bytes; Ea 8 ch, 76 bytes)

| Off | Size | Field | RAM | Scale |
|---|---|---|---|---|
| 4 | 1 | Month | $8EBA | |
| 5 | 1 | Day | $8EBB | |
| 6 | 2 | Year | $8EBE | |
| 8 | 1 | Hour | $8EBC | |
| 9 | 1 | Minute | $8EBD | |
| 10 | 1 | Seconds | $8EC0 | x1 |
| 11 | 4 | Fraction of second | $8EC0 | ns |
| 15 | 4 | Latitude | $8EDE | mas |
| 19 | 4 | Longitude | $8EE6 | mas |
| 23 | 4 | Height (GPS) | $8ED6 | x100 (cm) |
| 27 | 4 | Height (MSL) | $8ED6 - $7574 | x100 |
| 31 | 2 | Speed | $8F40 | x100 |
| 33 | 2 | Heading | $8F38 | x10 |
| 35 | 2 | DOP | $81E4 | x10 |
| 37 | 1 | DOP type | $81EC != 2 | |
| 38 | 1 | Visible | $37A4 + $B14 x $37A0 (0 if $37A0 > 1) | |
| 39 | 1 | Tracked | $81D0 | |
| 40 | 4 x n | Per channel: SV, mode, signal, status | chan +0, +1, +$86, +2 | |
| 40+4n | 1 | Receiver status | $8F1E, bit 2 = ($75C2 != 1) | |

## Bb (92 bytes; $0AAF74 -> $0A8FA8)

| Off | Size | Field | RAM (bank b = $37A0, stride $B14) |
|---|---|---|---|
| 4 | 1 | Visible count | $37A4 |
| 5 | 7 x 12 | Per visible SV ($0A8FA8), below; unused entries zero | SVs with $37F0[sv] set; per-SV records $3862 + $24 x SV |

| +Off | Size | Field | Source |
|---|---|---|---|
| 0 | 1 | SV | |
| 1 | 2 | Doppler, Hz (signed) | rec +6 = -range rate x 5.2550 (1/lambda), $08EA8E |
| 3 | 1 | Elevation, deg (signed) | rec +4 |
| 4 | 2 | Azimuth, deg | rec +2 |
| 6 | 1 | 0 enabled, 1 enabled + @@Am ignored, 2 disabled, 3 disabled + ignored | enable $383C[sv] ($8FAF copy), ignore $3816[sv] ($8F88 copy) |

Zero-filled when $37A0 > 1.

## Bc / Ec ($0A966A; Bc 6, 82 bytes; Ec 8, 100 bytes)

| Off | Size | Field | RAM (bank $37A0) |
|---|---|---|---|
| 4 | 1 | DOP type of the set ranking: 0 GDOP, 1 PDOP, 2 HDOP, 3 VDOP, 4 TDOP (@@Aj code) | dop_kind $3DBB (bank +$619; internal 0 G, 1 H, 2 P, 3 T, 4 V; map $0A96D4) |
| 5 | 1 | SV set count, max 9 | $37A2 |
| 6 | 1 | SVs per set: 4, or 8 if @@Ar bit 0 | select_mode $3DBA (bank +$618, copy of $81DA) |
| 7 | (2 + n) x 9 | Per set: DOP x 10 (word), then n SVs | $3DC4 + 10k, $3DBC + 10k |

## Bd (23 bytes; $0A8768, $0AAB44)

| Off | Size | Field | Source |
|---|---|---|---|
| 4 | 8 | RAM almanac: 1, week ($36F), ..., $36E, page flags ($348..) as bits; zeros if $3722 = 0 | almanac_ptr |
| 12 | 8 | EEPROM almanac: same; zeros if $21024 = 2 | bank $20934/$20CAC (+$36E/$36F, $348) |

## Be (33 bytes each, 34 frames; $0A8560)

| Off | Size | Field | Source |
|---|---|---|---|
| 4 | 1 | Subframe / page id | tables $0B079E (32 SV pages), $0B091E (2 pages) |
| 5 | 1 | SV or page number | loop index |
| 6 | 24 | 8 words x 3 bytes | almanac_ptr + $18 x SV; + $318 |

If $3722 != 1: one frame, word 0 + 6 longs 0.

## Bf, ephemeris output (80 bytes; $0A8EA8, letters set to Bf by the scheduler, Bi when answering @@Bi)

| Off | Size | Field | Source |
|---|---|---|---|
| 4 | 1 | SV | chan +0 |
| 5 | 72 | 24 x 3 bytes (subframes 1-3 words) | $8FDB + ... per SV |

## Bg / Eg ($0AA21A; Bg 6 ch, 122 bytes; Eg 8 ch, 158 bytes)

| Off | Size | Field | RAM | Scale |
|---|---|---|---|---|
| 4 | 3 | Measurement time, whole s | $81A6 | x1 |
| 7 | 4 | Measurement time, fraction | $81A6 | ns |
| 11 | 18 x n | Per channel, below | | |

| +Off | Size | Field | RAM |
|---|---|---|---|
| 0 | 1 | SV | chan +0 |
| 1 | 1 | Mode (max 4 if $81DB[ch] = 0) \| $80 ($C609[ch] = 0) \| $40 ($C611[ch] = 0) | chan +1 |
| 2 | 3 | Transmit time, whole s | $81A6 + ($8298[ch] - $8218[ch]) / -c; 0 unless option C or D |
| 5 | 4 | Transmit time, ns | same |
| 9 | 4 | Carrier phase | chan +$8C; 0 unless option C |
| 13 | 3 | Code phase, L1 cycles (low 24 bits) | chan +$88 |
| 16 | 2 | Code discriminator | chan +$84 |

## Bh, corrections output (Ce format, 52 bytes; $0AA822 ch 0-5, $0AA9A0 ch 6-7)

| Off | Size | Field | RAM | Scale |
|---|---|---|---|---|
| 4 | 3 | Time | gps_seconds $819E | x10 |
| 7 | 7 x 6 | SV, PRC (3), RRC (2), IODE (1) | chan +0; $8218[ch]; $8258[ch] - clock_drift $81D2; $7A3D + $EC ch | PRC x100, RRC x1000 |

Entry sent only if $81DB[ch] = 1 and chan +$A0 > $C652 (EEPROM $087A); else 7 zero bytes.  $0AA9A0: entries 1-2 = ch 6, 7 ($8248, $81E1, $7400, $7FC5); 3-6 zero.

## Bj (8 bytes; $0A9410)

| Off | Size | Field | RAM |
|---|---|---|---|
| 4 | 1 | Leap second status | $81C6 |

## Bk / Ek ($0A9866; Bk 6, 69 bytes; Ek 8, 71 bytes)

| Off | Size | Field | RAM | Scale |
|---|---|---|---|---|
| 4 | 2 x 5 | GDOP, PDOP, HDOP, VDOP, TDOP | $7A10, $7A18, $7A20, $7A28, $7A30 | x10 |
| 14 | 2 | Magnetic variation, deg | mag_var $81B2 | x10 |
| 16 | 2 x 3 | Velocity north, east, up, m/s | $8F28, $8F30, $8F20 | x10 |
| 22 | 2 | Correction age, s | gps_seconds - $75C4/10 (mod 604800); 0 if $75C2 != 0 | x10 |
| 24 | 4 x 3 | ECEF X, Y, Z, m | $8EEE, $8EF6, $8EFE | x100 |
| 36 | n | Per channel URA index (subframe 1) | chan +4 | |
| 36+n | 2 x 10 | Covariance (H^T H)^-1, upper triangle; order clock, E, N, U: tt, tE, tN, tU, EE, EN, EU, NN, NU, UU | nav_cov $369C [0][0], [0][1], [0][2], [0][3], [1][1], [1][2], [1][3], [2][2], [2][3], [3][3] | x10 |
| 56+n | 2 | Clock bias correction, m | $756C (s) x c | x10 |
| 58+n | 2 | Clock drift, m/s | $81D2 | x10 |

## Bl (41 bytes; $0A88BA, one frame per channel)

| Off | Size | Field | Source |
|---|---|---|---|
| 4 | 1 | Channel + 1 | |
| 5 | 1 | SV | chan +$45 |
| 6 | 1 | (word count << 4) \| type | chan +$4A..., +$52 |
| 7 | 1 | | |
| 8 | 30 | 10 x 3 bytes | channel subframe buffer |

## Bm (28 bytes; $0AACA8)

Option G only (@@Bm, @@Cl, @@Cm; not set on this unit, options "IB").  No reader of $3605-$3642 outside @@Bm, the A/C replies, the EEPROM block $115A and the clear at boot ($09C72E); @@AF ($3624) is in no output.

| Off | Size | Field | RAM | Scale |
|---|---|---|---|---|
| 4 | 1 | @@AH value, 0-2 | $3627 | |
| 5 | 2 | @@AI value (stored x 0.5) | $3628 | x2 |
| 7 | 2 | Always 0 | $3614 | x2 |
| 9 | 1 | @@AG value, 0/1 | $3626 != 0 | |
| 10 | 2 | @@Cm word 1, signed (> -1001) | $3630 | x1 |
| 12 | 2 | @@Cm word 2 (stored x 0.5) | $363A | x2 |
| 14 | 1 | @@Cm flag, 0/1 | $3638 != 0 | |
| 15 | 6 | Always 0 | $3605-$360A | |
| 21 | 2 | Always 0 | $360C | x2 |
| 23 | 2 | HDOP | $7A20 | x10 |

## Bn / En ($0AA4CE; Bn 6, 59 bytes; En 8, 69 bytes)

| Off | Size | Field | RAM | Scale |
|---|---|---|---|---|
| 4 | 1 | Rate | $C61E + 2 slot | |
| 5 | 1 | TRAIM enable, 0/1 | $9D11 | |
| 6 | 2 | Alarm limit (input word; default 10) | $9D16 / 100 | |
| 8 | 1 | 1PPS mode: 0 off, 1 on, 2 when tracking, 3 when TRAIM ok ($095180) | $9D0E | |
| 9 | 3 | Always 1 (set by every accepted @@Bn/@@En; default 1; no reader) | $9D12 | |
| 12 | 1 | Month, always 0 | $9D1A | |
| 13 | 1 | Day, always 0 | $9D1B | |
| 14 | 2 | Year, always 0 | $9D1E | |
| 16 | 1 | Hour, always 0 | $9D1C | |
| 17 | 1 | Minute, always 0 | $9D1D | |
| 18 | 1 | Seconds, always 0 | $9D20 (double) | x1 |
| 19 | 1 | 1PPS output enabled now | pps_enable $9D10 | |
| 20 | 1 | 1 = GPS time, 0 = UTC (complement of $9D86, written only by @@Aw) | $9D0F | |
| 21 | 1 | TRAIM status: 0 none removed, 1 SV removed, 2 too few SVs | $9D2C | |
| 22 | 1 | TRAIM alarm: 0 ok, 1 alarm, 2 unknown | $9D28 | |
| 23 | 2 | Time error estimate ($FFFF unknown) | $9D2A | |
| 25 | 1 | 1PPS quantization error, ns: (sub-tick remainder - 0.5) / tick length | $9D29 (pps_schedule $091BFA) | |
| 26 | 5 x n | SV; fractional second of (rx time - Sagnac / c), ns | chan +0; svst +$160, $8F48 + 8 ch | x1e9 |

Input @@Bn/@@En (22 bytes, option I; $0A4712, $0A4C44): rate; TRAIM 0/1; alarm word > 2 (stored x100); 1PPS mode < 4; 3-byte value < 86401; month, day, year (word), hour, minute, second, checked only if any is nonzero (1-12, 1-31, 1980-2079, <= 23, <= 59, <= 60).  The 3-byte value and the date/time are not stored ($9D12 = 1, $9D1A-$9D20 = 0).

## Ez (361 bytes; $0A8D22; letters forced to E z, also for @@Bz)

Raw channel reports (chan_report, built each second by the 50 ms task).

| +Off | Size | Field | RAM ($27BC + $2C ch, 8 channels) |
|---|---|---|---|
| 0 | 1 | ms within the 20 ms data bit at the epoch | +$19 |
| 1 | 16 | Data bits at the epoch | +$1A..+$29 |
| 17 | 1 | ISR state: 0 search, 1 code acquire, 2 settle, 3 freq pull-in, 4 FLL/bit sync, 5 PLL | +$01 |
| 18 | 1 | Bit data usable | +$2A |
| 19 | 1 | SV | +$00 |
| 20 | 2 | Code discriminator | +$14 |
| 22 | 1 | PLL lock count, max 20 | +$17 |
| 23 | 1 | PLL loss count | +$18 |
| 24 | 2 | Amplitude at the epoch (clipped $FFFF) | +$02 |
| 26 | 4 | Carrier NCO at the epoch (x 0.012934 = Hz) | +$08 |
| 30 | 4 | Code phase, L1 cycles mod $1809FC | +$04 |
| 34 | 4 | Carrier phase at the epoch | +$0C |
| 38 | 4 | Carrier phase at ms 800 | +$10 |
| 42 | 1 | Lock count, max 20 | +$16 |
| 43 | 1 | Code dither sign (signed) | +$2B |

Then 2 bytes epoch ms counter ms_count $2922.  Payload starts at offset 4.

## Tc (61 bytes; $0A8B0E) / Td (55 bytes; $0A8BAA)

Search-state snapshot, filled once per second by `srch_status_snapshot` $08A8A2 (from `task_meas_1s`).  @@Tc input: 1 byte, continuous on/off ($C619).  @@Td input: on/off ($C61A), channel a ($C61C, < 8), channel b ($C61D, < 8).

| Msg | Off | Size | Source |
|---|---|---|---|
| Tc, Td | 4 | 12 | Header $6347, below |
| Tc | 16 | 6 x 7 | Channels 0-5, $6353 + 7 ch, below (8 filled, 6 sent) |
| Td | 16 | 18 | Channel record $638B + 18 x $C61C, below |
| Td | 34 | 18 | Channel record $638B + 18 x $C61D |

Header ($6347):

| +Off | Size | Field | Source |
|---|---|---|---|
| 0 | 1 | Doppler bin table select (@@AB, 0-4) | $8EB8 |
| 1 | 1 | SV selection mode as @@Ah: 0 auto, 1 manual (@@Ai), 2 by elevation | $9CEA, $9D0C |
| 2 | 2 | Velocity north, m/s (unsigned conversion) | search_ctl +$14 ($8F28 copy, when fix_valid) |
| 4 | 2 | Velocity east, m/s | search_ctl +$1C ($8F30 copy) |
| 6 | 2 | Velocity up, m/s | search_ctl +$0C ($8F20 copy) |
| 8 | 2 | Search center (oscillator drift, units of osc_drift_ref) | $113C |
| 10 | 1 | Center updated this pass | $113E |
| 11 | 1 | Cold strategy exhausted | $113F |

Tc channel entry ($6353 + 7 ch):

| +Off | Size | Field | Source ($CF6 + $28 ch) |
|---|---|---|---|
| 0 | 1 | SV | +0 |
| 1 | 1 | Search state: 0 idle, 1 searching, 2 reacquire, 3 tracking | +1 |
| 2 | 1 | Strategy 0-5 | +3 |
| 3 | 2 | Current Doppler bin offset, Hz (signed) | bin table byte [+$0A] x 100 |
| 5 | 2 | Commanded Doppler, Hz | chan_cmd +2 x 3.3111572265625 |

Td channel record ($638B + 18 ch):

| +Off | Size | Field | Source |
|---|---|---|---|
| 0 | 1 | Channel | |
| 1 | 1 | SV | $CF6 + $28 ch |
| 2 | 1 | Search state | +1 |
| 3 | 1 | Strategy | +3 |
| 4 | 1 | Bin stepping allowed | +9 |
| 5 | 1 | Doppler bin index | +$0A |
| 6 | 2 | Doppler bin offset, Hz | bin table byte x 100 |
| 8 | 2 | Commanded Doppler, Hz | chan_cmd +2 x 3.3111572265625 |
| 10 | 4 | Code window width, L1 cycles | chan_cmd +8 |
| 14 | 4 | Code window start, L1 cycles | chan_cmd +4 |

## Reply messages (slot 10 ring)

| Msg | Builder | Payload |
|---|---|---|
| Ca / Fa | $0AAE96 | self-test word (C: & $3FFF; F: bits rearranged) |
| Cc | $0A36F8 | ack of @@Bf |
| Cf | $0A8C76 | none |
| Cg | $0AA796 | $C651 != 0 |
| Ch | $0A3660 | ack of @@Cb |
| Cj | $0A9F04 | copyright and ID text (table $0B06A2) |
| Ck | $0A93B0 | none; after @@Ce or RTCM corrections |
| Ap | $0A2DFA | reply to @@Ao |

# Other protocols

## Mode-3 $C6 output frame ($0AD7E8), 23 bytes

7-bit fields: each byte carries 7 bits, most significant group first.

| Offset | Size | Field | RAM source | Scale |
|---|---|---|---|---|
| 0 | 1 | $C6 start | | |
| 1 | 4 | Latitude, 28 bits | $8EDE (double, rad) | rad_to_mas / 60 = 0.001 arcmin |
| 5 | 4 | Longitude, 28 bits | $8EE6 (double, rad) | 0.001 arcmin |
| 9 | 1 | Hour | $8EBC | |
| 10 | 1 | Minute | $8EBD | |
| 11 | 1 | Seconds | $8EC0 (double), truncated | 1 s |
| 12 | 2 | Height, 14 bits | $8ED6 - $7574, clamped <= $2000 | 1 m |
| 14 | 2 | Speed, 14 bits | $8F40 | x10 |
| 16 | 2 | Heading, 14 bits | $8F38 | x10 |
| 18 | 1 | Status | $81D0 << 4, bit 3 if $81EC = 2 | |
| 19 | 1 | Count, clamped <= 31 | double (source not resolved) | |
| 20 | 1 | Fix flag | $10 if rcvr_status & $30 | |
| 21 | 1 | $DA end | | |
| 22 | 1 | Checksum | XOR bytes 1-20 ^ $DA ($0AD7B6) | |

## Mode-2 terse report ($0A5E9A)

`%s%03d%03d%d\r\n`, position part `%02d%02d%02d%c%03d%02d%02d%c` ($0A627E).

| Field | Format | RAM source | Scale |
|---|---|---|---|
| Latitude | DD MM hh, N/S | $8EDE | degrees, minutes, hundredths of a minute (rounded, $0A60CE) |
| Longitude | DDD MM hh, E/W | $8EE6 | same |
| Speed | %03d | $8F40, clamped to 514.0 | x 2.236936292 (m/s -> mph) |
| Heading | %03d | $8F38 | degrees |
| Fix flag | %d | rcvr_status & $30 != 0 | |

## Text-protocol rates (word pairs at $CAF8 + 4n, EEPROM $0922 + 2n)

| n | Output | EEPROM |
|---|---|---|
| 0 | $GPGGA | $0922 |
| 1 | $GPGLL | $0924 |
| 2 | $GPGSA | $0926 |
| 3 | $GPGSV | $0928 |
| 4 | $GPRMC | $092A |
| 5 | $GPVTG | $092C |
| 6 | $GPZDA | $092E |
| 7 | Terse report | $0930 |
