#!/usr/bin/env python3
"""
Imports layouts from Mah by ffalt (https://github.com/ffalt/mah, MIT licence) into Dark Mahjong.

Reads ffalt/mah's src/assets/data/boards.json (downloaded at the pinned commit, or a local copy
given as an argument) and writes app/src/main/java/.../data/repository/MahLayouts.kt, keeping
each board in Mah's own compact map format.

Which boards are kept: those whose tiles are at least MIN_TILE_DP wide when the whole board is
fitted to a Pixel 9 Pro's board area, so none needs constant zooming and panning (the app's
own level tiles are about 47 dp), and whose upper tiles rest fully on the layer below. Mah
draws flat, so some of its boards let tiles overhang thin air; Dark Mahjong lights its tiles as
solid objects, and its tests require full support. None of the kept boards duplicates a Dark
Mahjong layout:
checked by tile arrangement when this was first run (2026-10-26); the closest share an outline
but stack their tiles quite differently.

Difficulty, provisional until a proper difficulty measure exists: the share of tiles free at
the start (every board here is a full 144 tiles, so the count says nothing).

Run from the repo root:
    python3 tools/import-mah-layouts.py [boards.json]
"""
import json
import math
import os
import sys
import urllib.request

MAH_COMMIT = "43c3174c41e1a4b40867f702542dc29375d96d68"
BOARDS_URL = f"https://raw.githubusercontent.com/ffalt/mah/{MAH_COMMIT}/src/assets/data/boards.json"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/java/com/vortessence/mahjong/data/repository/MahLayouts.kt")

MIN_TILE_DP = 30.0

# Mirrors the app: TableCamera, BoardTransform and the board area on a Pixel 9 Pro (dp)
TILT_DEGREES, CAMERA_DISTANCE = 20.0, 2.2
LAYER_SHIFT_X, LAYER_SHIFT_Y, TILE_ASPECT = 0.03, 0.13, 1.333
BOARD_W, BOARD_H, PADDING = 427.0, 792.0, 16.0

# Spelling fixes to Mah's board names
RENAMES = {"Teotihucan": "Teotihuacan"}


def decode(board_map):
    """Mah's map: [z, [[y, [x | [x, count]] ...] ...]] in half-tile units; tiles 2 units wide."""
    tiles = []
    for z, rows in board_map:
        for y, items in rows:
            if not isinstance(items, list):
                items = [items]
            for item in items:
                if isinstance(item, list):
                    x, count = item
                    tiles += [(x + 2 * i, y, z) for i in range(count)]
                else:
                    tiles.append((item, y, z))
    return tiles


def fitted_tile_dp(tiles):
    """Tile width when the whole board is fitted to the board area (as computeBoardTransform)."""
    left = min(x - z * 2 * LAYER_SHIFT_X for x, y, z in tiles)
    right = max(x + 2 - z * 2 * LAYER_SHIFT_X + 2 * LAYER_SHIFT_X for x, y, z in tiles)
    top = min(y * TILE_ASPECT - z * 2 * TILE_ASPECT * LAYER_SHIFT_Y for x, y, z in tiles)
    bottom = max((y + 2) * TILE_ASPECT - z * 2 * TILE_ASPECT * LAYER_SHIFT_Y + 2 * TILE_ASPECT * LAYER_SHIFT_Y for x, y, z in tiles)
    near_edge = CAMERA_DISTANCE / (CAMERA_DISTANCE - 0.5 * math.sin(math.radians(TILT_DEGREES)))
    content_w = (BOARD_W - 2 * PADDING) / near_edge
    content_h = BOARD_H - 2 * PADDING
    unit = min(content_w / (right - left), content_h / (bottom - top))
    return 2 * unit, (bottom - top) / (right - left)


def overlaps(a, b):
    return max(a, b) < min(a + 2, b + 2) - 0.05


def is_free(tile, tiles):
    """The game's rule (BoardSolvabilityGenerator): nothing on top, and one side open."""
    x, y, z = tile
    left = right = False
    for other in tiles:
        ox, oy, oz = other
        if oz > z and overlaps(x, ox) and overlaps(y, oy):
            return False
        if oz == z and other != tile and overlaps(y, oy):
            if abs(ox + 2 - x) < 0.15 or (ox < x < ox + 2):
                left = True
            if abs(x + 2 - ox) < 0.15 or (x < ox < x + 2):
                right = True
    return not (left and right)


def fully_supported(tiles):
    """Every tile above the bottom layer rests on tiles below across its whole footprint."""
    for x, y, z in tiles:
        if z == 0:
            continue
        below = [(ox, oy) for ox, oy, oz in tiles if oz == z - 1 and overlaps(x, ox) and overlaps(y, oy)]
        for i in range(10):
            for j in range(10):
                sx, sy = x + 0.05 + i * 1.9 / 9, y + 0.05 + j * 1.9 / 9
                if not any(ox <= sx <= ox + 2 and oy <= sy <= oy + 2 for ox, oy in below):
                    return False
    return True


def difficulty(tiles):
    free = sum(is_free(t, tiles) for t in tiles) / len(tiles)
    if free >= 0.30:
        return "MEDIUM", free
    if free >= 0.20:
        return "HARD", free
    return "EXPERT", free


def main():
    if len(sys.argv) > 1:
        with open(sys.argv[1]) as f:
            boards = json.load(f)
    else:
        with urllib.request.urlopen(BOARDS_URL) as response:
            boards = json.load(response)

    kept = []
    for board in boards:
        tiles = decode(board["map"])
        tile_dp, height_over_width = fitted_tile_dp(tiles)
        if tile_dp < MIN_TILE_DP or not fully_supported(tiles):
            continue
        level, free = difficulty(tiles)
        kept.append({
            "id": "mah_" + board["id"],
            "name": RENAMES.get(board["name"], board["name"]),
            "category": board["cat"],
            "difficulty": level,
            "tall": height_over_width >= 1.0,
            "map": json.dumps(board["map"], separators=(",", ":")),
            "count": len(tiles),
            "free": free,
            "tile_dp": tile_dp,
        })
    order = {"EASY": 0, "MEDIUM": 1, "HARD": 2, "EXPERT": 3}
    kept.sort(key=lambda b: (order[b["difficulty"]], b["name"]))

    lines = [
        "// Generated by tools/import-mah-layouts.py from Mah by ffalt (MIT licence, see NOTICE.md),",
        f"// boards.json at commit {MAH_COMMIT}. Don't edit by hand: change the script and re-run it.",
        "package com.vortessence.mahjong.data.repository",
        "",
        "import com.vortessence.mahjong.data.model.Difficulty",
        "",
        "/**",
        f" * {len(kept)} of Mah's {len(boards)} boards: those that fit a phone held upright with tiles at least",
        f" * {MIN_TILE_DP:.0f} dp wide, and whose tiles all rest fully on the layer below. Each map is in Mah's",
        " * own compact format, decoded by [MahBoard].",
        " */",
        "internal object MahLayouts {",
        f"    const val SOURCE_COMMIT = \"{MAH_COMMIT}\"",
        "",
        "    val boards: List<MahBoard> = listOf(",
    ]
    for b in kept:
        lines.append(
            f"        // {b['count']} tiles, {b['free'] * 100:.0f}% free at the start, {b['tile_dp']:.0f} dp tiles when fitted"
        )
        lines.append(
            f"        MahBoard(\"{b['id']}\", \"{b['name']}\", \"{b['category']}\", Difficulty.{b['difficulty']}, "
            f"isTall = {'true' if b['tall'] else 'false'},"
        )
        lines.append(f"            \"{b['map']}\"),")
    lines += ["    )", "}", ""]
    with open(OUT, "w") as f:
        f.write("\n".join(lines))
    counts = {}
    for b in kept:
        counts[b["difficulty"]] = counts.get(b["difficulty"], 0) + 1
    print(f"Kept {len(kept)} of {len(boards)} boards: {counts}; wrote {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
