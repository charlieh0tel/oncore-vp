/* $0AC73C-$0AC763: @@AJ */
#include "oncore.h"

void cmd_AJ(byte *p)
{
    byte v = *p;

    if (v >= 5 && v <= 180) {
        dgps_max_age = v;
        settings_dirty = 1;
    }
    reply_AJ();
}
