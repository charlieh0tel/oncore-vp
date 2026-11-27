/* $09FB0A-$09FB21 */
#include "oncore.h"

void vis_predict_init(void)
{
    pred_work_bank = 0;
    vis_state = 2;
    vis_busy = 0;
}
