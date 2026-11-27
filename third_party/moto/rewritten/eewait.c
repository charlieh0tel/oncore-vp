/* $09B2A6-$09B319: EEPROM writes. */
#include "oncore.h"

void eeprom_wait(void)
{
    long n;

    n = 0x97e;
    do {
        if ((EEPROM_POLL_A & 0x80) != (EEPROM_POLL_B & 0x80))
            break;
    } while (--n > 0);
    if (n == 0)
        eeprom_error |= 4;
}

/* $09B2DE */
void eeprom_write(byte *dst, byte *src, short n)
{
    eeprom_wait();
    while (n--) {
        if (*dst != *src) {
            *dst = *src;
            eeprom_wait();
        }
        if (*dst++ != *src++)
            eeprom_error |= 2;
    }
}
