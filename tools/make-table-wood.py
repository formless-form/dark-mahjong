#!/usr/bin/env python3
"""Not used yet (parked 2026-10-29: the knots read too large at table scale). Builds a wood tabletop (res/drawable-nodpi/table_wood.webp) from its source photo.

Source: "Wood grain structure, annual rings" (Needpix 294637, CC0):
https://www.needpix.com/photo/download/294637/
It is turned a quarter round so the figure runs up the table, and each colour channel is scaled
so the wood's average matches the darker tone of the procedural walnut it replaced (its albedo
before the lamp lights it). Usage: tools/make-table-wood.py [r g b]
"""
import sys
from pathlib import Path
from PIL import Image, ImageStat

root = Path(__file__).resolve().parent.parent
src = root / "tools/sources/table-wood-needpix-294637.jpg"
out = root / "app/src/main/res/drawable-nodpi/table_wood.webp"
# The procedural walnut's average colour (0..1, as the table shader saw it)
target = [float(v) for v in sys.argv[1:4]] if len(sys.argv) >= 4 else [0.27, 0.165, 0.10]

img = Image.open(src).convert("RGB").transpose(Image.Transpose.ROTATE_270)
mean = [m / 255.0 for m in ImageStat.Stat(img).mean]
gains = [t / m for t, m in zip(target, mean)]
bands = [band.point(lambda v, g=g: min(255, round(v * g))) for band, g in zip(img.split(), gains)]
Image.merge("RGB", bands).save(out, "WEBP", quality=92, method=6)
print(f"{out.relative_to(root)}: {img.size[0]}x{img.size[1]}, source mean {[round(m, 3) for m in mean]}, gains {[round(g, 3) for g in gains]}")
