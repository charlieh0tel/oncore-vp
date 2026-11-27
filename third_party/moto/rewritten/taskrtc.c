/* $09FE06-$09FE29: scheduler task 11.  The exit is assembly: mask
   interrupts, clear the task's running flag ($08CD), restore its saved
   SP ($0922) and jump to sched_dispatch ($08A13A). */
#include "oncore.h"

void task_rtc_write_date(void)
{
    rtc_write_from_time();
    time_known = 1;
#pragma ASM
	ORI.W	#$0700,SR
	CLR.B	($08CD).W
	MOVEA.L	($0922).W,A7
	BRA.L	$08A13A
#pragma END_ASM
}
