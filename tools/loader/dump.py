#!/usr/bin/env python3
"""Read memory from a VP Oncore (6ch or 8ch) over its host port, through the
firmware's factory loader (@@Tq; docs/memory-map.md, "Factory loader").

  dump.py [--port P] BASE LENGTH OUT     e.g. 080000 40000 flash.bin, 020000 2000 eeprom.bin

Enters the loader, writes stub.s (assembled below) to RAM at $C000 with @@Sa,
sets BASE and LENGTH, runs it with @@Sg, and saves what it sends.  The receiver
stays in the loader; power-cycle it afterwards (the stub overwrites RAM).
9600 baud: 256 KB takes about 4.6 minutes.  For flash, check the image's last
8 bytes (even- and odd-byte sums over the rest).
"""
import argparse
import struct
import time

import serial

# m68k-linux-gnu-as -mcpu=cpu32 stub.s; objcopy -O binary.  BASE at +$40, LENGTH at +$44.
STUB = bytes.fromhex('20176114207a003a223a003a670810186112538166f84e757603e198610651cbfffa4e753438fc0c31fc0055fa2631fc00aafa260802000867ea11c0fc0f4e750000000000000000')
RAM = 0xC000
PORT = '/dev/serial/by-id/usb-1a86_USB_Single_Serial_5C8D070210-if00'


def xor(bs):
    c = 0
    for b in bs:
        c ^= b
    return c


class Loader:
    def __init__(self, port):
        self.p = serial.Serial(port, 9600, timeout=0.5)

    def read_exact(self, n, timeout):
        end, buf = time.time() + timeout, b''
        while len(buf) < n and time.time() < end:
            buf += self.p.read(n - len(buf))
        return buf

    def reply(self, letters, timeout=3):
        want, buf, end = b'@@' + letters, b'', time.time() + timeout
        while time.time() < end:
            buf += self.p.read(64)
            i = buf.find(want)
            if i >= 0 and len(buf) >= i + 8:
                if buf[i + 6:i + 8] != b'\r\n':
                    raise RuntimeError('bad reply %r' % buf[i:i + 8])
                return buf[i + 4]
        raise RuntimeError('no @@%s reply' % letters.decode())

    def frame(self, letter, addr, data=b''):
        body = bytes([1 + len(addr) + len(data)]) + addr + data
        return b'@@S' + letter + body + bytes([ord('S') ^ letter[0] ^ xor(body)]) + b'\r\n'

    def enter(self):
        # Already in the loader?  @@Sd (no action) answers there.  Sending @@Tq
        # inside the loader would start a frame that swallows what follows.
        self.p.reset_input_buffer()
        self.p.write(self.frame(b'd', b''))
        try:
            self.reply(b'Sd', 1.5)
            return 'already in the loader'
        except RuntimeError:
            pass
        self.p.reset_input_buffer()
        self.p.write(b'@@Tq' + bytes([ord('T') ^ ord('q')]) + b'\r\n')
        self.reply(b'Tq')
        return 'entered the loader'

    def write(self, addr, data, chunk=64):
        for o in range(0, len(data), chunk):
            self.p.write(self.frame(b'a', struct.pack('>H', addr + o), data[o:o + chunk]))
            if self.reply(b'Sa'):
                raise RuntimeError('@@Sa rejected at %04X' % (addr + o))

    def go(self, addr, n, timeout):
        self.p.write(self.frame(b'g', struct.pack('>H', addr)))
        out = self.read_exact(n, timeout)
        return out, self.reply(b'Sg', 5)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--port', default=PORT)
    ap.add_argument('base', type=lambda s: int(s, 16))
    ap.add_argument('length', type=lambda s: int(s, 16))
    ap.add_argument('out')
    a = ap.parse_args()
    L = Loader(a.port)
    print(L.enter())
    L.write(RAM, STUB[:0x40] + struct.pack('>II', a.base, a.length))
    t = time.time()
    data, st = L.go(RAM, 4 + a.length, a.length * 10 / 9600 * 1.3 + 10)
    body = data[4:]
    print('loader at %s, %d of %d bytes, status %d, %.0f s' % (data[:4].hex(), len(body), a.length, st, time.time() - t))
    open(a.out, 'wb').write(body)
    if len(body) >= 8 and a.length == 0x40000:
        ev, od = sum(body[0:-8:2]) & 0xFFFFFFFF, sum(body[1:-8:2]) & 0xFFFFFFFF
        ok = struct.unpack('>II', body[-8:]) == (ev, od)
        print('flash sums', 'match' if ok else 'DO NOT match')


if __name__ == '__main__':
    main()
