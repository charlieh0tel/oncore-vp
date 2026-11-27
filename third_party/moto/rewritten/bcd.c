/* $09B0F6-$09B137: BCD conversion. */
#include "oncore.h"

char bcd_to_bin(byte b)
{
    byte lo;
    int hi;

    lo = b & 15;
    hi = b >> 4;
    return hi * 10 + lo;
}

byte bin_to_bcd(byte b)
{
    byte tens = 0;
    byte n;

    n = b;
    while (n >= 10) {
        n -= 10;
        tens++;
    }
    return (tens << 4) + n;
}
