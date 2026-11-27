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
| 5 | 7 x 12 | SV, word, signed byte ($3866), word, status byte (from $383C, $3816); Doppler, elevation, azimuth, health in Motorola order (inferred) | SVs with $37F0[sv] set; per-SV records stride $24 |

Zero-filled when $37A0 > 1.

## Bc / Ec ($0A966A; Bc 6, 82 bytes; Ec 8, 100 bytes)

| Off | Size | Field | RAM (bank $37A0) |
|---|---|---|---|
| 4 | 1 | Mode 0-4 | from $3DBB (4-8) |
| 5 | 1 | Count, max 9 | $37A2 |
| 6 | 1 | 4 or 8 | $3DBA != 0 -> 8 |
| 7 | (2 + n) x 9 | word, then n bytes | $3DC4 + 10k, $3DBC + 10k |

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
| 13 | 3 | | chan +$88 |
| 16 | 2 | | chan +$84 |

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
| 4 | 2 x 5 | | $7A10, $7A18, $7A20, $7A28, $7A30 | x10 |
| 14 | 2 | | $81B2 | x10 |
| 16 | 2 x 3 | | $8F28, $8F30, $8F20 | x10 |
| 22 | 2 | Correction age | gps_seconds - $75C4/10 (mod 604800); 0 if $75C2 != 0 | x10 |
| 24 | 4 x 3 | | $8EEE, $8EF6, $8EFE | x100 |
| 36 | n | Per channel byte | chan +4 | |
| 36+n | 2 x 10 | | $369C, $36A4, $36AC, $36B4, $36C4, $36CC, $36D4, $36EC, $36F4, $3714 | x10 |
| 56+n | 2 | Clock bias | $756C x c | x10 |
| 58+n | 2 | Clock drift | $81D2 | x10 |

## Bl (41 bytes; $0A88BA, one frame per channel)

| Off | Size | Field | Source |
|---|---|---|---|
| 4 | 1 | Channel + 1 | |
| 5 | 1 | SV | chan +$45 |
| 6 | 1 | (word count << 4) \| type | chan +$4A..., +$52 |
| 7 | 1 | | |
| 8 | 30 | 10 x 3 bytes | channel subframe buffer |

## Bm (28 bytes; $0AACA8)

| Off | Size | Field | RAM | Scale |
|---|---|---|---|---|
| 4 | 1 | @@AH | $3627 | |
| 5 | 2 | @@AI | $3628 | x2 |
| 7 | 2 | | $3614 | x2 |
| 9 | 1 | @@AG | $3626 != 0 | |
| 10 | 2 | @@Cm value 1 | $3630 | x1 |
| 12 | 2 | @@Cm value 2 | $363A | x2 |
| 14 | 1 | @@Cm flag | $3638 != 0 | |
| 15 | 6 | | $3605-$360A | |
| 21 | 2 | | $360C | x2 |
| 23 | 2 | | $7A20 | x10 |

## Bn / En ($0AA4CE; Bn 6, 59 bytes; En 8, 69 bytes)

| Off | Size | Field | RAM | Scale |
|---|---|---|---|---|
| 4 | 1 | Rate | $C61E + 2 slot | |
| 5 | 1 | TRAIM enable | $9D11 | |
| 6 | 2 | Alarm limit | $9D16 / 100 | |
| 8 | 1 | Mode | $9D0E | |
| 9 | 3 | | $9D12 | |
| 12 | 1 | | $9D1A | |
| 13 | 1 | | $9D1B | |
| 14 | 2 | | $9D1E | |
| 16 | 1 | | $9D1C | |
| 17 | 1 | | $9D1D | |
| 18 | 1 | | $9D20 | x1 |
| 19 | 1 | | $9D10 | |
| 20 | 1 | | $9D0F | |
| 21 | 1 | | $9D2C | |
| 22 | 1 | | $9D28 | |
| 23 | 2 | | $9D2A | |
| 25 | 1 | | $9D29 | |
| 26 | 5 x n | SV, 4-byte value | chan +0 | |

## Ez (361 bytes; $0A8D22; letters forced to E z, also for @@Bz)

| +Off | Size | RAM ($27BC + $2C ch, 8 channels) |
|---|---|---|
| 0 | 1 | +$19 |
| 1 | 16 | +$1A..+$29 |
| 17 | 1 | +$01 |
| 18 | 1 | +$2A |
| 19 | 1 | +$00 |
| 20 | 2 | +$14 |
| 22 | 1 | +$17 |
| 23 | 1 | +$18 |
| 24 | 2 | +$02 |
| 26 | 4 | +$08 |
| 30 | 4 | +$04 |
| 34 | 4 | +$0C |
| 38 | 4 | +$10 |
| 42 | 1 | +$16 |
| 43 | 1 | +$2B (signed) |

Then 2 bytes $2922.  Payload starts at offset 4.

## Tc (61 bytes; $0A8B0E) / Td (55 bytes; $0A8BAA)

| Msg | Off | Size | Source |
|---|---|---|---|
| Tc | 4 | 12 | $6347 |
| Tc | 16 | 6 x 7 | $6353 |
| Td | 4 | 12 | $6347 |
| Td | 16 | 18 | $638B + 18 x $C61C |
| Td | 34 | 18 | $638B + 18 x $C61D |

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
