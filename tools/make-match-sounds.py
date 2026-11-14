#!/usr/bin/env python3
"""
Builds Dark Mahjong's match sound: two tiles clacking together, with a soft burst of stardust
(a scatter of tiny pentatonic chimes over a fine glitter fizz) underneath.

Writes app/src/main/res/raw/tile_match_1.ogg to tile_match_4.ogg. Each take is at a slightly
different pitch (the clack and its chimes together) with its own scatter of sparkles, so
repeated matches never sound the same. The clack is the Imperial set's stone clack
(sounds/stone_clack.wav, one hit already cut and levelled) sped up 2x into a bright tile tick: the
old wooden clack (sounds/tile_clack-original.wav) sped up 1.4x got lost in the stardust on a
phone's speaker (2026-11-14). Everything is CC0.

The Imperial set's match (--stone) is the same clack at its own speed, not sped up (sped up, stone
loses its weight), at the same pitches and over the same stardust. It writes stone_match_1.ogg to
stone_match_4.ogg.

Needs Python 3 with numpy, and ffmpeg (with libvorbis). Run from the repo root:
    python3 tools/make-match-sounds.py [--stone] [--source WAV] [--speed X] [--out DIR]
"""
import argparse
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "sounds/stone_clack.wav")
OUT = os.path.join(ROOT, "app/src/main/res/raw")
SR = 44100

# The clack: the stone clack (already mono, cut and levelled), sped up 2x (an octave up)
CLACK_CHAIN = "pan=mono|c0=c0,afade=t=in:d=0.001"
SPEED = 2.0
PITCHES = [0.955, 0.985, 1.015, 1.045]

# Stardust: G major pentatonic (the game's music is pentatonic), two octaves from about 3 kHz
PENTATONIC = [392.0, 440.0, 493.88, 587.33, 659.25]
NOTES = [f * m for m in (8, 16) for f in PENTATONIC]
STARDUST_LEVEL = 0.42   # stardust peak, relative to the clack's peak
CLACK_LEVEL = 0.9       # the clack itself, turned down 10% after the stardust is set from it (2026-11-14)
STONE_CLACK_LEVEL = 0.81  # Imperial's, down another 10%
LENGTH = 0.75           # seconds, with a short fade at the end


STONE_SOURCE = os.path.join(ROOT, "sounds/stone_clack.wav")
STONE_CHAIN = "pan=mono|c0=c0,afade=t=in:d=0.001"
STONE_SPEED = 1.0


def clack(pitch):
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "clack.wav")
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", SOURCE, "-af",
             f"{CLACK_CHAIN},asetrate={SR}*{SPEED}*{pitch},aresample={SR}", "-ac", "1", path],
            check=True,
        )
        with wave.open(path) as w:
            return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768


def stereo(mono, pan):
    return np.stack([mono * np.cos(pan * np.pi / 2), mono * np.sin(pan * np.pi / 2)], 1)


def add(out, segment, start):
    end = min(len(out), start + len(segment))
    if start < len(out):
        out[start:end] += segment[:end - start]


def ping(freq, duration, amp):
    """A tiny bell: a sine with a brief, inharmonic shimmer at the strike."""
    t = np.arange(int(duration * SR)) / SR
    env = np.exp(-t / (duration / 5)) * (1 - np.exp(-t / 0.002))
    shimmer = 0.25 * np.sin(2 * np.pi * min(freq * 2.76, 20000) * t) * np.exp(-t / 0.03)
    return amp * env * (np.sin(2 * np.pi * freq * t) + shimmer)


def low_pass(x, a):
    y = np.empty_like(x)
    s = 0.0
    for i, v in enumerate(x):
        s += a * (v - s)
        y[i] = s
    return y


def chimes(rng, n, pitch):
    out = np.zeros((n, 2))
    for k in range(7):
        start = int((0.012 + k * 0.045 + rng.uniform(0, 0.0225)) * SR)
        bell = ping(rng.choice(NOTES) * pitch, rng.uniform(0.15, 0.3), 0.45 * 0.8 ** k)
        add(out, stereo(bell, rng.uniform(0.1, 0.9)), start)
    return out


def fizz(rng, n):
    """Fine glitter: high-band noise that swells in and fades, flecked with crackling grains."""
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    high = noise - low_pass(noise, 0.75)
    env = (1 - np.exp(-t / 0.01)) * np.exp(-t / 0.2)
    grains = (rng.random(n) < 0.004).astype(float)
    grains = np.convolve(grains, np.exp(-np.arange(150) / 20))[:n]
    f = high * env * (0.25 + 1.2 * grains) * 0.22
    return np.stack([f, np.roll(f, 37)], 1)


def room(x, rng):
    """A little room echo: a short burst of decaying noise as the impulse response."""
    n = int(0.45 * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.5 * SR / 6))
    ir /= np.abs(ir).sum() / 8
    return np.convolve(x, ir)[:len(x)]


def take(index, pitch):
    rng = np.random.default_rng(1000 + index)
    n = int(LENGTH * SR)
    dust = chimes(rng, n, pitch) + fizz(rng, n)
    dust = dust + 0.6 * np.stack([room(dust[:, 0], rng), room(dust[:, 1], rng)], 1)
    hit = clack(pitch)
    dust *= STARDUST_LEVEL * np.abs(hit).max() / np.abs(dust).max()
    mix = dust
    hit = hit * CLACK_LEVEL
    mix[:len(hit)] += np.stack([hit, hit], 1)
    fade = int(0.08 * SR)
    mix[-fade:] *= np.linspace(1, 0, fade)[:, None]
    peak = np.abs(mix).max()
    if peak > 0.89:  # keep 1 dB of headroom
        mix *= 0.89 / peak
    return mix


def main():
    global SOURCE, CLACK_CHAIN, SPEED, OUT, CLACK_LEVEL
    args = argparse.ArgumentParser()
    args.add_argument("--stone", action="store_true", help="the Imperial set's stone match")
    args.add_argument("--source", help="the clack (default: the set's own)")
    args.add_argument("--speed", type=float, help="speed-up of the clack (default: the set's own)")
    args.add_argument("--out", help="where to write (default: the app's res/raw)")
    a = args.parse_args()
    name = "tile_match"
    if a.stone:
        SOURCE, CLACK_CHAIN, SPEED, name = STONE_SOURCE, STONE_CHAIN, STONE_SPEED, "stone_match"
        CLACK_LEVEL = STONE_CLACK_LEVEL
    SOURCE = a.source or SOURCE
    SPEED = a.speed or SPEED
    OUT = a.out or OUT
    for i, pitch in enumerate(PITCHES, start=1):
        mix = take(i, pitch)
        with tempfile.TemporaryDirectory() as tmp:
            wav = os.path.join(tmp, "take.wav")
            with wave.open(wav, "wb") as w:
                w.setnchannels(2)
                w.setsampwidth(2)
                w.setframerate(SR)
                w.writeframes((np.clip(mix, -1, 1) * 32767).astype(np.int16).tobytes())
            out = os.path.join(OUT, f"{name}_{i}.ogg")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "6", out], check=True)
        print(f"{name}_{i}.ogg: peak {20 * np.log10(np.abs(mix).max()):.1f} dB")


if __name__ == "__main__":
    main()
