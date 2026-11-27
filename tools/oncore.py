#!/usr/bin/env python3
"""Talk to a Motorola VP Oncore over its host port (Motorola binary, 9600 8N1).

  oncore.py [--port P] watch [--seconds N]   decode @@Ea (8ch) or @@Ba (6ch)
  oncore.py [--port P] settime                send @@Ac and @@Aa with UTC from the system clock
  oncore.py [--port P] ta                     read the oscillator record (@@Ta query; changes nothing)
  oncore.py [--port P] ttff [--settime] [--seconds N]
                                              time until the first channel is used in a fix

Field layouts and bit meanings: docs/messages.md, docs/memory-map.md.
"""
import argparse
import datetime
import struct
import sys
import time

import serial

PORT = '/dev/serial/by-id/usb-1a86_USB_Single_Serial_5C8D070211-if00'

# @@Ea receiver status (docs/memory-map.md, "@@Ea receiver status byte").
STATUS = {7: 'b7', 6: 'dop/singular', 5: '3D', 4: '2D', 3: 'acq/hold', 2: 'diff', 1: 'count0', 0: 'no-alm'}
# Channel flags ($6FBE).
FLAGS = {7: 'used', 6: 'alert', 5: 'A-S', 4: 'unhealthy', 3: 'URA>=6', 0: 'parity'}


def frame(cmd, payload=b''):
    c = 0
    for b in cmd + payload:
        c ^= b
    return b'@@' + cmd + payload + bytes([c]) + b'\r\n'


def bits(v, names):
    return ','.join(n for b, n in sorted(names.items(), reverse=True) if v >> b & 1) or '-'


def frames(port, seconds, letters):
    """Yield (time, letters, payload) for checksummed frames with the given letters."""
    end = time.time() + seconds
    buf = b''
    while time.time() < end:
        buf += port.read(256)
        while True:
            i = buf.find(b'@@')
            if i < 0:
                buf = buf[-1:]
                break
            j = buf.find(b'\r\n', i + 4)
            if j < 0:
                buf = buf[i:]
                break
            m, buf = buf[i:j], buf[j + 2:]
            if len(m) < 5 or m[2:4] not in letters:
                continue
            c = 0
            for b in m[2:-1]:
                c ^= b
            if c == m[-1]:
                yield time.time(), m[2:4], m[4:-1]


def decode_pos(p):
    """Ea/Ba payload -> dict.  nch = 8 for Ea, 6 for Ba."""
    nch = (len(p) - 37) // 4
    lat, lon, hg = struct.unpack('>iii', p[11:23])
    chans = [tuple(p[36 + 4 * k:40 + 4 * k]) for k in range(nch)]
    return dict(date='%02d/%02d/%04d' % (p[0], p[1], p[2] << 8 | p[3]),
                time='%02d:%02d:%02d' % (p[4], p[5], p[6]),
                lat=lat / 3.6e6, lon=lon / 3.6e6, h=hg / 100.0,
                vis=p[34], trk=p[35], status=p[36 + 4 * nch], chans=chans)


def cmd_watch(port, a):
    port.reset_input_buffer()
    for t, letters, p in frames(port, a.seconds, (b'Ea', b'Ba')):
        d = decode_pos(p)
        ch = ' '.join('%d:m%d:s%d' % (sv, m, s) + ('*' if f & 0x80 else '') for sv, m, s, f in d['chans'])
        print('%s %s vis%d trk%d st%02x[%s] %.5f %.5f %.1f  %s' % (
            d['date'], d['time'], d['vis'], d['trk'], d['status'], bits(d['status'], STATUS),
            d['lat'], d['lon'], d['h'], ch), flush=True)


def settime(port):
    now = datetime.datetime.now(datetime.timezone.utc)
    port.write(frame(b'Ac', bytes([now.month, now.day]) + now.year.to_bytes(2, 'big')))
    time.sleep(0.3)
    now = datetime.datetime.now(datetime.timezone.utc)
    port.write(frame(b'Aa', bytes([now.hour, now.minute, now.second])))
    print('sent @@Ac/@@Aa', now.isoformat(timespec='seconds'), flush=True)


def cmd_ta(port, a):
    # Every field out of range: nothing is written; the reply carries the record.
    port.reset_input_buffer()
    port.write(frame(b'Ta', b'\xff' * 5))
    for t, letters, p in frames(port, 5, (b'Ta',)):
        drift, setting, hz = struct.unpack('>HBH', p[:5])
        print('drift ref %d  setting $%02X (%d)  %d Hz/LSB at L1' % (drift, setting, setting, hz))
        return
    sys.exit('no @@Ta reply')


def cmd_ttff(port, a):
    t0 = time.time()
    port.reset_input_buffer()
    if a.settime:
        settime(port)
    last = None
    for t, letters, p in frames(port, a.seconds, (b'Ea', b'Ba')):
        d = decode_pos(p)
        used = sum(1 for c in d['chans'] if c[3] & 0x80)
        state = (d['vis'], d['trk'], d['status'], used)
        if state != last:
            print('%7.1f s  %s %s vis%d trk%d st%02x[%s] used%d' % (
                t - t0, d['date'], d['time'], d['vis'], d['trk'], d['status'],
                bits(d['status'], STATUS), used), flush=True)
            last = state
        if used:
            print('first fix after %.1f s' % (t - t0))
            return
    sys.exit('no fix within %d s' % a.seconds)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--port', default=PORT)
    sp = ap.add_subparsers(dest='cmd', required=True)
    w = sp.add_parser('watch')
    w.add_argument('--seconds', type=float, default=1e9)
    sp.add_parser('settime')
    sp.add_parser('ta')
    f = sp.add_parser('ttff')
    f.add_argument('--settime', action='store_true')
    f.add_argument('--seconds', type=float, default=3600)
    a = ap.parse_args()
    port = serial.Serial(a.port, 9600, timeout=0.2)
    {'watch': cmd_watch, 'settime': lambda p, a: settime(p), 'ta': cmd_ta, 'ttff': cmd_ttff}[a.cmd](port, a)


if __name__ == '__main__':
    main()
