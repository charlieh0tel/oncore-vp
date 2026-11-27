/* $0A6A18-$0A6AA1 */
#include "oncore.h"

byte xor_sum(byte *p, word n)
{
    byte x;
    word i;

    eeprom_wait();
    x = 0;
    for (i = 0; i < n; i++)
        x ^= *p++;
    return x;
}

/* Low n bytes of (long)(v * scale), most significant first. */
void fmt_scaled(byte *dst, double v, double scale, byte n)
{
    double t;
    long x;
    byte *p;
    byte i;
    byte j;

    t = v * scale;
    x = t;
    p = (byte *)&x;
    for (i = 0, j = 4 - n; j < 4; i++, j++)
        dst[i] = p[j];
}
