#!/usr/bin/env python3
"""
Builds the fireplace's crackle loop: app/src/main/res/raw/fireplace.ogg (Opus, 80 kbps, mono).

Source: "Crackeling Fireplace" by Christopher C. Courter (Freesound 447818, CC0), in
sounds/freesound/.

Mixed to mono, the last seconds crossfaded into the start (equal-power, as the ends are unrelated
noise) so FireplaceSound's gapless loop has no seam. Soft ambience: loudest pops compressed,
harshest highs rolled off, level under the music.

Needs Python 3 with numpy, and ffmpeg (with libopus). Run from the repo root:
    python3 tools/make-fire-sound.py [source]
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "sounds/freesound/447818__courter__crackeling-fireplace.mp3")
OUT = os.path.join(ROOT, "app/src/main/res/raw/fireplace.ogg")
SR = 48000  # Opus's own rate
LENGTH = 200.0  # seconds of loop: about half the recording, to keep the app small
CROSSFADE = 4.0  # seconds
LEVEL_DB = -24.0  # RMS level of the loop, in dBFS
PEAK_DB = -1.5  # no pop louder than this


def main():
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "mono.wav")
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", SOURCE, "-ac", "1", "-ar", str(SR),
             "-af", "acompressor=threshold=-28dB:ratio=4:attack=2:release=120:knee=4,lowpass=f=9000", wav],
            check=True,
        )
        with wave.open(wav) as w:
            x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768

        # The first LENGTH seconds, the stretch after them crossfaded into the start
        x = x[: int((LENGTH + CROSSFADE) * SR)]
        n = int(CROSSFADE * SR)
        t = np.linspace(0, np.pi / 2, n)
        loop = x[: len(x) - n].copy()
        loop[:n] = x[:n] * np.sin(t) + x[len(x) - n:] * np.cos(t)

        # Soft background, no pop above the peak ceiling
        rms = np.sqrt(np.mean(loop ** 2))
        gain = 10 ** (LEVEL_DB / 20) / rms
        gain = min(gain, 10 ** (PEAK_DB / 20) / np.abs(loop).max())
        loop *= gain

        out_wav = os.path.join(tmp, "loop.wav")
        with wave.open(out_wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((np.clip(loop, -1, 1) * 32767).astype(np.int16).tobytes())
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", out_wav, "-c:a", "libopus", "-b:a", "80k",
             "-application", "audio", OUT],
            check=True,
        )
    print(f"fireplace.ogg: {len(loop) / SR:.1f} s loop, gain {20 * np.log10(gain):+.1f} dB, "
          f"{os.path.getsize(OUT) / 1024:.0f} KB")


if __name__ == "__main__":
    main()
