#!/usr/bin/env python3
"""
Builds Dark Mahjong's tile tap (also the title-screen menu click, played softer and faster):
two light wood impacts from Kenney's Impact Sounds pack (CC0, sounds/kenney/), each at two
slightly different pitches, levelled to sit as quietly as the taps they replaced.

Writes app/src/main/res/raw/tile_tap_1.ogg to tile_tap_4.ogg.

The Imperial set's tap is made the same way from one short stone knock (--stone,
sounds/stone_knock.wav), at the same two pitches, and writes stone_tap_1.ogg and stone_tap_2.ogg.

Needs Python 3 with numpy, and ffmpeg (with libvorbis). Run from the repo root:
    python3 tools/make-tap-sounds.py [--stone] [--source WAV] [--out DIR]
"""
import argparse
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
STONE_SOURCE = os.path.join(ROOT, "sounds/stone_knock.wav")
STONE_PITCHES = [0.98, 1.03]
# Loudness of the knock itself. A phone's speaker plays little below 500 Hz, which is most of the
# wood knock's body but none of the stone's crack: at the same level the wood was 8 dB quieter
# on the phone, so the wood is levelled 6 dB up and the stone 3 dB down (2026-11-14)
TARGET_RMS_DB = -20.0
STONE_TARGET_RMS_DB = -29.0


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
    global OUT
    args = argparse.ArgumentParser()
    args.add_argument("--stone", action="store_true", help="the Imperial set's stone tap")
    args.add_argument("--source", help="the stone knock (default: sounds/stone_knock.wav)")
    args.add_argument("--out", help="where to write (default: the app's res/raw)")
    a = args.parse_args()
    OUT = a.out or OUT
    prefix = "stone_tap" if a.stone else "tile_tap"
    target = STONE_TARGET_RMS_DB if a.stone else TARGET_RMS_DB
    takes = [(a.source or STONE_SOURCE, p) for p in STONE_PITCHES] if a.stone else [(os.path.join(KENNEY, n), p) for n, p in TAKES]
    for n, (src, pitch) in enumerate(takes, start=1):
        name = os.path.basename(src)
        # Start just before the knock, end once it has died away, with tiny fades
        x = decode(src)
        a = np.abs(x)
        start = max(0, np.argmax(a > a.max() * 0.02) - int(0.001 * SR))
        quiet = np.where(np.convolve(a, np.ones(220) / 220, "same") > a.max() * 0.003)[0]
        end = quiet[-1] / SR
        gain = target - knock_rms_db(x)
        # Levelled on the source, then checked on the result: a knock that is mostly tail can land
        # off target, so it's put right once (the ivory taps already land within half a dB)
        for _ in range(2):
            chain = (f",atrim=start={start / SR:.4f}:end={end:.4f},asetpts=PTS-STARTPTS,afade=t=in:d=0.0008,"
                     f"afade=t=out:st={max(0, end - start / SR - 0.02):.4f}:d=0.02,volume={gain:.2f}dB,"
                     f"asetrate={SR}*{pitch},aresample={SR}")
            out = os.path.join(OUT, f"{prefix}_{n}.ogg")
            subprocess.run(
                ["ffmpeg", "-v", "error", "-y", "-i", src, "-af", "pan=mono|c0=0.5*c0+0.5*c1" + chain,
                 "-c:a", "libvorbis", "-q:a", "6", out],
                check=True,
            )
            miss = target - knock_rms_db(decode(out))
            if abs(miss) <= 0.5:
                break
            gain += miss
        y = decode(out)
        print(f"{prefix}_{n}.ogg: {name} at {pitch}x, {len(y) / SR * 1000:.0f} ms, "
              f"knock {knock_rms_db(y):.1f} dB, peak {20 * np.log10(np.abs(y).max()):.1f} dB")


if __name__ == "__main__":
    main()
