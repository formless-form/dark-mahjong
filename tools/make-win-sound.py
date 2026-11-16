#!/usr/bin/env python3
"""
Builds the win sound, app/src/main/res/raw/win_fireworks.ogg (Opus, 80 kbps, mono), under the
fireworks when a board is cleared.

Source: "Fireworks - Annapolis Royal, NS" by greysound (Freesound 769030, CC0). The repo keeps the
first 10 s of 47.5 as FLAC in sounds/freesound/; the sound is the first 5.5 s, mono, levelled, faded
over the last second.

Needs ffmpeg (with libopus). Run from the repo root:
    python3 tools/make-win-sound.py
"""
import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "sounds/freesound/769030__greysound__fireworks-annapolis-royal-ns-excerpt.flac")
OUT = os.path.join(ROOT, "app/src/main/res/raw/win_fireworks.ogg")
LENGTH = 5.5  # seconds: the show runs about 4.5 s, then its last sparks die away
FADE = 1.0


def main():
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-t", str(LENGTH), "-i", SOURCE, "-ac", "1",
         "-af", f"loudnorm=I=-16:TP=-1:LRA=20,afade=t=in:d=0.01,afade=t=out:st={LENGTH - FADE}:d={FADE}",
         "-ar", "48000", "-c:a", "libopus", "-b:a", "80k", "-application", "audio", OUT],
        check=True,
    )
    print(f"win_fireworks.ogg: {os.path.getsize(OUT) / 1024:.0f} KB")


if __name__ == "__main__":
    main()
