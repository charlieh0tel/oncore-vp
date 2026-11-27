/*
 * Types and externals for the matching rewrite of B4121P1115.
 * Names from tools/ghidra/names-*.tsv; addresses in the comments.
 * Derived from the vendor flash image; not covered by this repository's
 * license.
 */
#ifndef ONCORE_H
#define ONCORE_H

typedef unsigned char byte;
typedef unsigned short word;

/* EEPROM ($020000, CS4): write-cycle status bytes compared by eeprom_wait. */
#define EEPROM_POLL_A (*(volatile byte *)0x20932)
#define EEPROM_POLL_B (*(volatile byte *)0x20933)

/* Date record ($8EBA and the time routines). */
struct date_rec {
    byte month;
    byte day;
    byte hour;
    byte minute;
    word year;
    double second;
};

/* Position: height (m), latitude, longitude (rad). */
struct llh_rec {
    double h;
    double lat;
    double lon;
};

/* Saved position with checksum (RAM $2598, EEPROM $1192 and $11AE). */
struct pos_rec {
    struct llh_rec p;
    long sum;
};

/* Prediction inputs at $0C70 (only the members used here). */
struct pred_in_rec {
    byte rsvd_00[0x54];
    byte pos_known;     /* $0CC4 */
};

/* Settings block at $3624 (@@AF-@@AI). */
struct cfg_af_ai {
    word af;    /* $3624 @@AF, < 900 */
    byte ag;    /* $3626 @@AG 0/1 */
    byte ah;    /* $3627 @@AH 0-2 */
};

extern byte settings_dirty;          /* $08E9 */
extern byte time_known;              /* $0CC5 */
extern struct pred_in_rec pred_in;   /* $0C70 */
extern byte pred_work_bank;          /* $0CC6 */
extern byte vis_busy;                /* $0CC7 */
extern struct cfg_af_ai cfg_block;   /* $3624 */
extern byte vis_state;               /* $37A0 */
extern struct llh_rec pos_llh;       /* $8ED6 */
extern byte el_mask;                 /* $8FAE @@Ag, degrees */
/* $9CEA: [0] @@Ah manual SV selection; [1-8] per-channel flags; [9-16]
   @@Ai SV per channel 1-8. */
extern byte sv_manual[17];
extern byte eeprom_error;            /* $7A38 */
extern byte dgps_max_age;            /* $79F6 */

extern struct pos_rec ee_pos_rec[2]; /* EEPROM $21192 */
extern byte ee_pos_sel;              /* EEPROM $211CA */
extern struct pos_rec pos_saved;     /* $2598 */
extern const byte month_days[2][13]; /* $0AF018: [leap][month] */

void eeprom_wait(void);              /* $09B2A6 */
long sum8(byte *p, short n);         /* $09C7CC */
long checksum32(byte *p, short n);   /* $09C7EE */
void ee_position_save(void);         /* $09F94A */
void rtc_write_from_time(void);      /* $09FE2A */
void reply_AJ(void);                 /* $0A3054 */
void reply_AH(void);                 /* $0A3DDE */
void reply_Ai(byte channel);         /* $0A38BC */
void reply_AF(void);                 /* $0A3EFA */
void reply_Ag(void);                 /* $0A40D4 */
short get_be16(byte *p);             /* $0A6C20 */

#endif
