/* $0A1A0E-$0A1A2F */
#include "oncore.h"

void mem_copy(byte *src, byte *dst, short n)
{
    while (n--) {
        *dst = *src;
        src++;
        dst++;
    }
}
