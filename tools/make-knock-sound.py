#!/usr/bin/env python3
"""
Builds Dark Mahjong's blocked-tile knock: a dull, muted wooden knock (40 ms), synthesised the same
way the game used to make it at every blocked tap (a 240 Hz tone with its octave, decaying fast).

Writes app/src/main/res/raw/tile_blocked.ogg.

Needs Python 3 and ffmpeg (with libvorbis). Run from the repo root:
    python3 tools/make-knock-sound.py
"""
import math
import os
import struct
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/res/raw/tile_blocked.ogg")
SR = 44100


def main():
    n = int(SR * 0.040)
    samples = []
    for i in range(n):
        t = i / SR
        wave = (0.8 * math.sin(2 * math.pi * 240 * t) + 0.2 * math.sin(2 * math.pi * 480 * t)) * math.exp(-t * 90.0)
        samples.append(max(-32767, min(32767, int(wave * 20000))))
    pcm = struct.pack(f"<{n}h", *samples)
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-",
         "-c:a", "libvorbis", "-q:a", "6", OUT],
        input=pcm, check=True,
    )
    print(f"tile_blocked.ogg: {n / SR * 1000:.0f} ms, {os.path.getsize(OUT)} bytes")


if __name__ == "__main__":
    main()
