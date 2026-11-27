/* $09C782-$09C929: almanac week check, byte sums, date to GPS time. */
#include "oncore.h"

/* $09C782 */
int almanac_week_ok(short have, short want)
{
    short d;

    while (want - have >= 128)
        have += 256;
    while (have - want > 2)
        want += 256;
    d = want - have;
    if (d >= -2 && d <= 127)
        return 1;
    return 0;
}

/* $09C7CC */
long sum8(byte *p, short n)
{
    long s = 0;

    while (n--)
        s += *p++;
    return s;
}

/* $09C7EE */
long checksum32(byte *p, short n)
{
    long s = -1;

    eeprom_wait();
    while (n--)
        s -= *p++;
    return s;
}

/* $09C81A */
void date_to_gps(struct date_rec *d, word *week, double *tow)
{
    word years;
    word leaps;
    byte leap;
    word days;
    byte m;
    unsigned long n;

    years = d->year - 1980;
    if (d->year > 1980)
        leaps = (years - 1) / 4 + 1;
    else
        leaps = 0;
    leap = d->year % 4 ? 0 : 1;
    days = d->day;
    for (m = 1; m < d->month; m++)
        days += month_days[leap][m];
    n = years * 365 + days + leaps - 6;
    *week = n / 7 & 0x3ff;
    *tow = (double)(n % 7 * 86400) + (double)(unsigned long)(d->hour * 3600)
        + (double)(unsigned long)(d->minute * 60) + d->second;
}
