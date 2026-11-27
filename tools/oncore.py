#!/usr/bin/env python3
"""Talk to a Motorola VP Oncore over its host port (Motorola binary, 9600 8N1).

  oncore.py [--port P] watch [--seconds N]   decode @@Ea (8ch) or @@Ba (6ch)
  oncore.py [--port P] settime                send @@Ac and @@Aa with UTC from the system clock
  oncore.py [--port P] ta                     read the oscillator record (@@Ta query; changes nothing)
  oncore.py [--port P] drift [--samples N]     clock drift from @@Ek (8ch) or @@Bk (6ch), polled once each
  oncore.py [--port P] selftest               @@Fa (8ch) or @@Ca (6ch); stops tracking, then @@Cg 1
  oncore.py [--port P] ttff [--settime] [--seconds N]
                                              time until the first channel is used in a fix
  oncore.py [--port P] fix                    @@Cg 1: leave idle and start fixing

  --poll Ba|Ea  for a receiver with continuous output off (HP hosts poll):
                send a one-time @@Ba/@@Ea every second in the background.

Field layouts and bit meanings: docs/messages.md, docs/memory-map.md.
"""

import argparse
import datetime
import struct
import sys
import threading
import time

import serial

PORT = "/dev/serial/by-id/usb-1a86_USB_Single_Serial_5C8D070211-if00"

# @@Ea receiver status (docs/memory-map.md, "@@Ea receiver status byte").
STATUS = {7: "b7", 6: "dop/singular", 5: "3D", 4: "2D", 3: "acq/hold", 2: "diff", 1: "count0", 0: "no-alm"}
# Channel flags ($6FBE).
FLAGS = {7: "used", 6: "alert", 5: "A-S", 4: "unhealthy", 3: "URA>=6", 0: "parity"}


def frame(cmd, payload=b""):
    c = 0
    for b in cmd + payload:
        c ^= b
    return b"@@" + cmd + payload + bytes([c]) + b"\r\n"


def bits(v, names):
    return ",".join(n for b, n in sorted(names.items(), reverse=True) if v >> b & 1) or "-"


def frames(port, seconds, letters):
    """Yield (time, letters, payload) for checksummed frames with the given letters."""
    end = time.time() + seconds
    buf = b""
    while time.time() < end:
        buf += port.read(256)
        while True:
            i = buf.find(b"@@")
            if i < 0:
                buf = buf[-1:]
                break
            j = buf.find(b"\r\n", i + 4)
            if j < 0:
                buf = buf[i:]
                break
            m, buf = buf[i:j], buf[j + 2 :]
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
    lat, lon, hg = struct.unpack(">iii", p[11:23])
    chans = [tuple(p[36 + 4 * k : 40 + 4 * k]) for k in range(nch)]
    return dict(
        date=f"{p[0]:02d}/{p[1]:02d}/{p[2] << 8 | p[3]:04d}",
        time=f"{p[4]:02d}:{p[5]:02d}:{p[6]:02d}",
        lat=lat / 3.6e6,
        lon=lon / 3.6e6,
        h=hg / 100.0,
        vis=p[34],
        trk=p[35],
        status=p[36 + 4 * nch],
        chans=chans,
    )


def cmd_watch(port, a):
    port.reset_input_buffer()
    for _t, _letters, p in frames(port, a.seconds, (b"Ea", b"Ba")):
        d = decode_pos(p)
        ch = " ".join(f"{sv}:m{m}:s{s}" + ("*" if f & 0x80 else "") for sv, m, s, f in d["chans"])
        print(
            f"{d['date']} {d['time']} vis{d['vis']} trk{d['trk']} st{d['status']:02x}[{bits(d['status'], STATUS)}]"
            f" {d['lat']:.5f} {d['lon']:.5f} {d['h']:.1f}  {ch}",
            flush=True,
        )


def settime(port):
    now = datetime.datetime.now(datetime.UTC)
    port.write(frame(b"Ac", bytes([now.month, now.day]) + now.year.to_bytes(2, "big")))
    time.sleep(0.3)
    now = datetime.datetime.now(datetime.UTC)
    port.write(frame(b"Aa", bytes([now.hour, now.minute, now.second])))
    print("sent @@Ac/@@Aa", now.isoformat(timespec="seconds"), flush=True)


def cmd_ta(port, a):
    # Every field out of range: nothing is written; the reply carries the record.
    port.reset_input_buffer()
    port.write(frame(b"Ta", b"\xff" * 5))
    for _t, _letters, p in frames(port, 5, (b"Ta",)):
        drift, setting, hz = struct.unpack(">HBH", p[:5])
        print(f"drift ref {drift}  setting ${setting:02X} ({setting})  {hz} Hz/LSB at L1")
        return
    sys.exit("no @@Ta reply")


L1_PER_M = 5.2550354685707275  # L1 cycles per meter: m/s -> Hz at L1
DRIFT_TARGET = 20625  # Hz at L1, the firmware's drift target ($08C252)


def cmd_drift(port, a):
    # One poll (rate 0) per sample; the stored rate is unchanged.
    port.reset_input_buffer()
    letters = None
    for _t, found, _p in frames(port, 5, (b"Ea", b"Ba")):
        letters = (b"Ek", 8) if found == b"Ea" else (b"Bk", 6)
        break
    if letters is None:
        sys.exit("no @@Ea/@@Ba from the receiver")
    cmd, n = letters
    for _i in range(a.samples):
        port.write(frame(cmd, b"\x00"))
        for _t, _found, p in frames(port, 3, (cmd,)):
            ms = struct.unpack(">H", p[54 + n : 56 + n])[0] / 10.0
            hz = ms * L1_PER_M
            off = hz - DRIFT_TARGET
            print(
                f"{time.strftime('%H:%M:%S')} drift {ms:.1f} m/s = {hz:.1f} Hz at L1"
                f" ({off:+.1f} from {DRIFT_TARGET}, {off / 1575.42:+.3f} ppm)",
                flush=True,
            )
            break
        time.sleep(2)


# Self-test word bits (1 = fail), firmware order; docs/messages.md.
SELFTEST = {
    0: "chan1",
    1: "chan2",
    2: "chan3",
    3: "chan4",
    4: "chan5",
    5: "chan6",
    6: "1kHz-int",
    7: "flash-LS",
    8: "flash-MS",
    9: "RAM-LS",
    10: "RAM-MS",
    11: "EEPROM",
    12: "DCXO-SPI",
    13: "RTC",
    14: "chan7",
    15: "chan8",
}


def unpack_selftest(w):
    """Undo @@Fa's packing to the @@Ca bit order: 0-5 kept, 6-7 <- 14-15, 8-15 <- 6-13."""
    return (w & 0x3F) | (w >> 6 & 3) << 14 | (w >> 8) << 6


def cmd_selftest(port, a):
    # In fix mode the first @@Ca/@@Fa only stops the channels and goes idle;
    # the second runs the test (answer in about 6 s).  @@Cg 1 resumes fixing.
    port.reset_input_buffer()
    eight = None
    for _t, found, _p in frames(port, 5, (b"Ea", b"Ba")):
        eight = found == b"Ea"
        break
    cmd = b"Fa" if eight else b"Ca"
    for _attempt in range(2):
        port.write(frame(cmd))
        for _t, _found, p in frames(port, 12, (cmd,)):
            raw = p[0] << 8 | p[1]
            w = unpack_selftest(raw) if cmd == b"Fa" else raw
            verdict = "all pass" if w == 0 else "FAIL " + bits(w, SELFTEST)
            print(f"@@{cmd.decode()} word ${raw:04X}: {verdict}")
            port.write(frame(b"Cg", b"\x01"))
            print("sent @@Cg 1 (resume fixing)")
            return
    port.write(frame(b"Cg", b"\x01"))
    sys.exit("no self-test reply; sent @@Cg 1")


def cmd_ttff(port, a):
    t0 = time.time()
    port.reset_input_buffer()
    if a.settime:
        settime(port)
    last = None
    for t, _letters, p in frames(port, a.seconds, (b"Ea", b"Ba")):
        d = decode_pos(p)
        used = sum(1 for c in d["chans"] if c[3] & 0x80)
        state = (d["vis"], d["trk"], d["status"], used)
        if state != last:
            print(
                f"{t - t0:7.1f} s  {d['date']} {d['time']} vis{d['vis']} trk{d['trk']}"
                f" st{d['status']:02x}[{bits(d['status'], STATUS)}] used{used}",
                flush=True,
            )
            last = state
        if used:
            print(f"first fix after {t - t0:.1f} s")
            return
    sys.exit(f"no fix within {a.seconds:.0f} s")


def poller(port, letters):
    def run():
        while True:
            port.write(frame(letters.encode(), b"\x00"))
            time.sleep(1)

    threading.Thread(target=run, daemon=True).start()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", default=PORT)
    ap.add_argument("--poll", choices=("Ba", "Ea"))
    sp = ap.add_subparsers(dest="cmd", required=True)
    w = sp.add_parser("watch")
    w.add_argument("--seconds", type=float, default=1e9)
    sp.add_parser("settime")
    sp.add_parser("ta")
    sp.add_parser("selftest")
    sp.add_parser("fix")
    dr = sp.add_parser("drift")
    dr.add_argument("--samples", type=int, default=5)
    f = sp.add_parser("ttff")
    f.add_argument("--settime", action="store_true")
    f.add_argument("--seconds", type=float, default=3600)
    a = ap.parse_args()
    port = serial.Serial(a.port, 9600, timeout=0.2)
    if a.poll:
        poller(port, a.poll)
    {
        "fix": lambda p, a: (p.write(frame(b"Cg", b"\x01")), print("sent @@Cg 1")),
        "watch": cmd_watch,
        "settime": lambda p, a: settime(p),
        "ta": cmd_ta,
        "drift": cmd_drift,
        "selftest": cmd_selftest,
        "ttff": cmd_ttff,
    }[a.cmd](port, a)


if __name__ == "__main__":
    main()
