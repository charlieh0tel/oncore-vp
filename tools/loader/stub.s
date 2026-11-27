| Sends the return address (4 bytes), then LEN bytes from BASE, over the SCI.
| Runs from the factory loader's @@Sg (jsr); returns to it.
    .text
start:
    move.l  (%sp),%d0
    bsr.s   sendl
    movea.l base(%pc),%a0
    move.l  len(%pc),%d1
    beq.s   done
1:  move.b  (%a0)+,%d0
    bsr.s   sendb
    subq.l  #1,%d1
    bne.s   1b
done:
    rts
sendl:
    moveq   #3,%d3
2:  rol.l   #8,%d0
    bsr.s   sendb
    dbra    %d3,2b
    rts
sendb:
    move.w  0xfffffc0c:w,%d2
    move.w  #0x55,0xfffffa26:w
    move.w  #0xaa,0xfffffa26:w
    btst    #8,%d2
    beq.s   sendb
    move.b  %d0,0xfffffc0f:w
    rts
    .even
base: .long 0
len:  .long 0
