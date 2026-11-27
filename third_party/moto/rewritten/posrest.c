/* $09C3E8-$09C52B: boot position from RAM, else EEPROM, else zero. */
#include "oncore.h"

void pos_restore(void)
{
    byte sel;
    byte ok;

    ok = 0;
    if (-1 - sum8((byte *)&pos_saved, 24) == pos_saved.sum) {
        pos_llh.h = pos_saved.p.h;
        pos_llh.lat = pos_saved.p.lat;
        pos_llh.lon = pos_saved.p.lon;
        pred_in.pos_known = 1;
    } else {
        if (ee_pos_sel == 0 || ee_pos_sel == 1) {
            sel = ee_pos_sel;
            if (checksum32((byte *)&ee_pos_rec[sel], 24) == ee_pos_rec[sel].sum)
                ok = 1;
        }
        if (ok) {
            pos_llh.h = ee_pos_rec[sel].p.h;
            pos_llh.lat = ee_pos_rec[sel].p.lat;
            pos_llh.lon = ee_pos_rec[sel].p.lon;
            if (pos_llh.h == 0 && pos_llh.lat == 0 && pos_llh.lon == 0)
                pred_in.pos_known = 0;
            else
                pred_in.pos_known = 1;
        } else {
            pos_llh.h = 0;
            pos_llh.lat = 0;
            pos_llh.lon = 0;
            pred_in.pos_known = 0;
            ee_position_save();
        }
    }
}
