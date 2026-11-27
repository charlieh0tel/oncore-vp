/* $0ACDB4-$0ACE8D: @@AH, @@AF, @@Ai, @@Ag. */
#include "oncore.h"

void cmd_AH(byte *p)
{
    switch (*p) {
    case 0:
        cfg_block.ah = 0;
        settings_dirty = 1;
        break;
    case 1:
        cfg_block.ah = 1;
        settings_dirty = 1;
        break;
    case 2:
        cfg_block.ah = 2;
        settings_dirty = 1;
        break;
    }
    reply_AH();
}

/* $0ACE02: @@AF */
void cmd_AF(byte *p)
{
    word v;

    v = get_be16(p);
    if (v <= 899) {
        cfg_block.af = v;
        settings_dirty = 1;
    }
    reply_AF();
}

/* $0ACE30: @@Ai */
void cmd_Ai(byte *p)
{
    byte ch;

    if (sv_manual[0] == 1) {
        ch = *p++;
        if (ch >= 1 && ch <= 8) {
            if (*p <= 37) {
                sv_manual[ch + 8] = *p;
                sv_manual[ch] = 0;
            }
            reply_Ai(ch);
        }
    }
}

/* $0ACE6C: @@Ag */
void cmd_Ag(byte *p)
{
    byte v = *p;

    if (v <= 89) {
        el_mask = v;
        settings_dirty = 1;
    }
    reply_Ag();
}
