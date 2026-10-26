#!/usr/bin/env python3
"""
Renders Dark Mahjong's launcher icon from the title screen's name seal: 暗夜麻將 in seal script,
cut white out of deep vermilion, with the same roughened carved edges as NameSeal.kt's AGSL
shader but none of its worn-out flecks (a clean field reads better at icon size).

Writes Android adaptive-icon layers (background, foreground, monochrome for themed icons) to
app/src/main/res/mipmap-*/ and a 512 px store icon to fastlane/metadata/android/en-US/images/.

Needs Python 3 with numpy and Pillow. Run from the repo root:
    python3 tools/make-launcher-icon.py [--preview out.png]
"""
import argparse
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app/src/main/res/font/seal_script_name.ttf")
RES = os.path.join(ROOT, "app/src/main/res")
STORE_ICON = os.path.join(ROOT, "fastlane/metadata/android/en-US/images/icon.png")

# Same colours as NameSeal.kt and the app's ink background
SEAL_INK = (0x8E, 0x2A, 0x23)
SEAL_PAPER = (0xF2, 0xE7, 0xD8)

CANVAS_DP = 108  # adaptive icon canvas; launchers show the middle 72 dp through their mask
SEAL_DP = 58     # the seal's side: its glyphs stay inside the 66 dp safe circle
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
SUPERSAMPLE = 3

# Quadrant centres as fractions of the seal, read right column first: 暗夜 then 麻將 (NameSeal.kt)
CELLS = [("暗", 0.71, 0.31), ("夜", 0.71, 0.70), ("麻", 0.29, 0.31), ("將", 0.29, 0.70)]


# --- The carved edges: a numpy port of SEAL_WEAR_AGSL in NameSeal.kt, less its flecks ---
def fract(x):
    return x - np.floor(x)


def hash21(px, py):
    px = fract(px * 123.34)
    py = fract(py * 456.21)
    d = px * (px + 45.32) + py * (py + 45.32)
    px, py = px + d, py + d
    return fract(px * py)


def vnoise(x, y):
    ix, iy = np.floor(x), np.floor(y)
    fx, fy = x - ix, y - iy
    ux, uy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = hash21(ix, iy), hash21(ix + 1, iy)
    c, d = hash21(ix, iy + 1), hash21(ix + 1, iy + 1)
    return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy


def carved_edges(size_px, px_per_dp):
    """Where each pixel samples from: a small noise-driven displacement that roughens the edges."""
    ys, xs = np.mgrid[0:size_px, 0:size_px].astype(np.float64)
    qx, qy = xs / px_per_dp, ys / px_per_dp
    dx = (vnoise(qx * 0.9, qy * 0.9) - 0.5) * 1.1 * px_per_dp
    dy = (vnoise(qx * 0.9 + 17, qy * 0.9 + 17) - 0.5) * 1.1 * px_per_dp
    return xs + dx, ys + dy


def sample(img, sx, sy):
    """Bilinear lookup of a float image at displaced coordinates."""
    h, w = img.shape
    x0 = np.clip(np.floor(sx).astype(int), 0, w - 2)
    y0 = np.clip(np.floor(sy).astype(int), 0, h - 2)
    fx, fy = np.clip(sx - x0, 0, 1), np.clip(sy - y0, 0, 1)
    top = img[y0, x0] * (1 - fx) + img[y0, x0 + 1] * fx
    bottom = img[y0 + 1, x0] * (1 - fx) + img[y0 + 1, x0 + 1] * fx
    return top * (1 - fy) + bottom * fy


# --- Drawing ----------------------------------------------------------------------------------
def glyph_mask(size_px, px_per_dp):
    """The four characters, white on black, positioned as NameSeal draws them."""
    seal = SEAL_DP * px_per_dp
    origin = (size_px - seal) / 2
    font = ImageFont.truetype(FONT, round(seal * 0.40))
    ascent, descent = font.getmetrics()
    img = Image.new("L", (size_px, size_px), 0)
    draw = ImageDraw.Draw(img)
    for ch, fx, fy in CELLS:
        advance = font.getlength(ch)
        x = origin + fx * seal - advance / 2
        y = origin + fy * seal - (ascent + descent) / 2
        draw.text((x, y), ch, font=font, fill=255, anchor="la")
    return np.asarray(img, dtype=np.float64) / 255


def render_layers(final_px):
    px_per_dp = final_px / CANVAS_DP * SUPERSAMPLE
    size = final_px * SUPERSAMPLE
    sx, sy = carved_edges(size, px_per_dp)
    alpha = sample(glyph_mask(size, px_per_dp), sx, sy)

    # Background: the vermilion field, faintly mottled like ink on stone
    ys, xs = np.mgrid[0:size, 0:size] / px_per_dp
    mottle = 1 + 0.05 * (vnoise(xs * 0.35 + 3, ys * 0.35 + 9) - 0.5) + 0.03 * (vnoise(xs * 1.7, ys * 1.7) - 0.5)
    background = np.array(SEAL_INK, dtype=np.float64)[None, None, :] * mottle[..., None]

    # Foreground: the carved characters
    paper = np.broadcast_to(np.array(SEAL_PAPER, dtype=np.float64), (size, size, 3))

    def to_image(rgb, a=None):
        arr = np.clip(rgb, 0, 255).astype(np.uint8)
        if a is None:
            img = Image.fromarray(arr, "RGB")
        else:
            img = Image.fromarray(np.dstack([arr, (np.clip(a, 0, 1) * 255).astype(np.uint8)]), "RGBA")
        return img.resize((final_px, final_px), Image.LANCZOS)

    return {
        "background": to_image(background),
        "foreground": to_image(paper, alpha),
        "monochrome": to_image(np.full((size, size, 3), 255.0), alpha),
    }


def masked(layers, shape):
    """What a launcher shows: background + foreground through a mask over the middle 72 dp."""
    full = layers["background"].convert("RGBA")
    full.alpha_composite(layers["foreground"])
    s = full.size[0]
    inset = s * (CANVAS_DP - 72) / CANVAS_DP / 2
    mask = Image.new("L", full.size, 0)
    draw = ImageDraw.Draw(mask)
    box = (inset, inset, s - inset, s - inset)
    if shape == "circle":
        draw.ellipse(box, fill=255)
    else:
        draw.rounded_rectangle(box, radius=(s - 2 * inset) * 0.3, fill=255)
    out = Image.new("RGBA", full.size, (0, 0, 0, 0))
    out.paste(full, (0, 0), mask)
    return out.crop(tuple(round(v) for v in box))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="write a contact sheet of masked icons here instead of resources")
    args = ap.parse_args()

    if args.preview:
        layers = render_layers(432)
        tiles = [masked(layers, "circle"), masked(layers, "squircle")]
        mono = Image.new("RGBA", (432, 432), (0x2A, 0x3A, 0x34, 255))
        tint = Image.new("RGBA", (432, 432), (0xCD, 0xE6, 0xDA, 255))
        mono.paste(tint, (0, 0), layers["monochrome"])
        tiles.append(masked({"background": mono, "foreground": Image.new("RGBA", (432, 432))}, "circle"))
        sheet = Image.new("RGB", (sum(t.size[0] for t in tiles) + 40 * (len(tiles) + 1), tiles[0].size[1] + 80), (0x1B, 0x22, 0x20))
        x = 40
        for t in tiles:
            sheet.paste(t, (x, 40), t)
            x += t.size[0] + 40
        # Also at roughly home-screen size, to judge legibility
        small = [t.resize((150, 150), Image.LANCZOS) for t in tiles]
        row = Image.new("RGB", (sheet.size[0], 190), (0x1B, 0x22, 0x20))
        for i, t in enumerate(small):
            row.paste(t, (40 + i * 190, 20), t)
        out = Image.new("RGB", (sheet.size[0], sheet.size[1] + row.size[1]))
        out.paste(sheet, (0, 0))
        out.paste(row, (0, sheet.size[1]))
        out.save(args.preview)
        return

    for name, scale in DENSITIES.items():
        final = round(CANVAS_DP * scale)
        layers = render_layers(final)
        folder = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        for layer, img in layers.items():
            img.save(os.path.join(folder, f"ic_launcher_{layer}.webp"), lossless=True, quality=100)
    # The store icon: a full square (the store applies its own rounding), scaled from the middle 72 dp
    layers = render_layers(768)
    full = layers["background"].convert("RGBA")
    full.alpha_composite(layers["foreground"])
    inset = 768 * (CANVAS_DP - 72) / CANVAS_DP / 2
    full.crop((round(inset), round(inset), round(768 - inset), round(768 - inset))).resize((512, 512), Image.LANCZOS).convert("RGB").save(STORE_ICON)


if __name__ == "__main__":
    main()
