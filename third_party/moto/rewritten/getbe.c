/* $0A6BC8-$0A6C3B: big-endian fields. */
#include "oncore.h"

long get_be24(byte *p)
{
    long v;

    v = (word)get_be16(p);
    v <<= 8;
    p = &p[2];
    return v + *p;
}

long get_be24s(byte *p)
{
    long v;

    if (*p > 127) {
        v = 0xff000000;
        v += get_be24(p);
    } else
        v = get_be24(p);
    return v;
}

short get_be16(byte *p)
{
    word hi = *p++;

    hi <<= 8;
    return hi + *p;
}
