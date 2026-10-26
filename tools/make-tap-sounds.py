#!/usr/bin/env python3
"""
Builds Dark Mahjong's tile tap (also the title-screen menu click, played softer and faster):
two light wood impacts from Kenney's Impact Sounds pack (CC0, sounds/kenney/), each at two
slightly different pitches, levelled to sit as quietly as the taps they replaced.

Writes app/src/main/res/raw/tile_tap_1.ogg to tile_tap_4.ogg.

Needs Python 3 with numpy, and ffmpeg (with libvorbis). Run from the repo root:
    python3 tools/make-tap-sounds.py
"""
import os
import subprocess

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KENNEY = os.path.join(ROOT, "sounds/kenney")
OUT = os.path.join(ROOT, "app/src/main/res/raw")
SR = 44100

TAKES = [  # (source, pitch)
    ("impactWood_light_000.ogg", 0.98),
    ("impactWood_light_000.ogg", 1.03),
    ("impactWood_light_002.ogg", 0.98),
    ("impactWood_light_002.ogg", 1.03),
]
TARGET_RMS_DB = -26.0  # loudness of the knock itself (the old taps ranged -33 to -21.5)


def decode(path):
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-f", "s16le", "-ar", str(SR), "-"],
        capture_output=True, check=True,
    ).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(float) / 32768


def knock_rms_db(x):
    a = np.abs(x)
    i = np.where(a > a.max() * 0.05)[0]
    return 20 * np.log10(np.sqrt((x[i[0]:i[-1] + 1] ** 2).mean()))


def main():
    for n, (name, pitch) in enumerate(TAKES, start=1):
        src = os.path.join(KENNEY, name)
        # Start just before the knock, end once it has died away, with tiny fades
        x = decode(src)
        a = np.abs(x)
        start = max(0, np.argmax(a > a.max() * 0.02) - int(0.001 * SR))
        quiet = np.where(np.convolve(a, np.ones(220) / 220, "same") > a.max() * 0.003)[0]
        end = quiet[-1] / SR
        gain = TARGET_RMS_DB - knock_rms_db(x)
        chain = (f",atrim=start={start / SR:.4f}:end={end:.4f},asetpts=PTS-STARTPTS,afade=t=in:d=0.0008,"
                 f"afade=t=out:st={max(0, end - start / SR - 0.02):.4f}:d=0.02,volume={gain:.2f}dB,"
                 f"asetrate={SR}*{pitch},aresample={SR}")
        out = os.path.join(OUT, f"tile_tap_{n}.ogg")
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", src, "-af", "pan=mono|c0=0.5*c0+0.5*c1" + chain,
             "-c:a", "libvorbis", "-q:a", "6", out],
            check=True,
        )
        y = decode(out)
        print(f"tile_tap_{n}.ogg: {name} at {pitch}x, {len(y) / SR * 1000:.0f} ms, "
              f"knock {knock_rms_db(y):.1f} dB, peak {20 * np.log10(np.abs(y).max()):.1f} dB")


if __name__ == "__main__":
    main()
