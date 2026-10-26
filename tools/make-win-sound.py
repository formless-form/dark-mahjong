#!/usr/bin/env python3
"""
Builds Dark Mahjong's win sound: one phrase of Chinese bamboo flute (dizi) from "DIZI FLUTE 02"
by CarlosCarty (Freesound #339357, CC0).

The phrase rises quickly to a long held E, steps down to a held C, and the player lets that C
fade away on the breath before the next phrase. The cut starts just before the first note and
keeps that natural ending, rounding off only its last moments.

Writes app/src/main/res/raw/win_dizi.ogg. Needs Python 3 with numpy, and ffmpeg (with
libvorbis). Run from the repo root:
    python3 tools/make-win-sound.py
"""
import os
import subprocess

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "sounds/freesound/339357__carloscarty__dizi-flute-02.mp3")
OUT = os.path.join(ROOT, "app/src/main/res/raw/win_dizi.ogg")
SR = 44100

START = 1.47      # just before the first note (seconds into the source)
FADE_FROM = 9.2   # the last note is already dying away on the player's breath here
END = 10.3        # before the next phrase (10.6 s)
PEAK_DB = -3.0


def main():
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-ss", f"{START:.3f}", "-t", f"{END - START:.3f}", "-i", SOURCE,
         "-af", "highpass=f=60", "-ac", "2", "-ar", str(SR), "-f", "f32le", "-"],
        capture_output=True, check=True,
    ).stdout
    x = np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(float)
    x -= x.mean(0)  # remove any DC offset

    fade_in = int(0.012 * SR)
    x[:fade_in] *= np.linspace(0, 1, fade_in)[:, None]
    fade_from = int((FADE_FROM - START) * SR)
    n = len(x) - fade_from
    x[fade_from:] *= (0.5 + 0.5 * np.cos(np.linspace(0, np.pi, n)))[:, None]

    x *= 10 ** (PEAK_DB / 20) / np.abs(x).max()
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "2", "-i", "-",
         "-c:a", "libvorbis", "-q:a", "6", OUT],
        input=pcm, check=True,
    )
    print(f"win_dizi.ogg: {len(x) / SR:.2f} s, peak {PEAK_DB} dB")


if __name__ == "__main__":
    main()
