# Design — Dark Mahjong

Fork of [PcmciaKai/mahjong_solitaire](https://github.com/PcmciaKai/mahjong_solitaire) (Kotlin + Jetpack Compose).
Status: **draft v2 for Gareth's review** — nothing below is built yet. Open questions from v1 are answered and folded in; the few new decisions are marked **(proposed)**.

## Goals

1. **Looks really good** — polished, tactile, Vita-Mahjong-level care, but our own look: **dark and softly lit**, never glaring.
2. **Smooth** — a locked **120 fps** on the Pixel 9 Pro's 120 Hz screen with a full 144-tile board; never below 60.
3. **Easy on the eyes and hands** — extra-large tiles, high contrast, big text, big buttons.
4. **Tasteful and minimalist** — Material 3 components on one dark token palette, one quiet accent, generous space, no badges, counters or clutter.
5. **Single-player, offline, calm** — no scores, timers, stats, leaderboards, accounts, ads or network access.

## Rules scope

Classic mahjong solitaire, unchanged from upstream: remove pairs of identical *free* tiles (nothing on top, and a free left or right side). Any Season matches any Season; any Flower matches any Flower. Every dealt board is guaranteed solvable (upstream's `BoardSolvabilityGenerator`). Unlimited hint, undo and shuffle.

**Out:** score, timer, best times, Stats screen, Ko-fi/support dialog, any online feature, a light theme.

## Platform

| | Upstream | Dark Mahjong |
|---|---|---|
| Test device | — | **Google Pixel 9 Pro**, latest Android, 120 Hz LTPO |
| minSdk | 30 (Android 11) | **33 (Android 13)** — AGSL `RuntimeShader` for backgrounds, tile sheen and glows; `RenderEffect` blur behind dialogs. The Pixel 9 Pro far exceeds this. |
| targetSdk / compileSdk | 37 | 37 |
| App name / ID | Mahjong Solitaire / `com.vortessence.mahjong` | **Dark Mahjong** / `com.formlessform.darkmahjong` **(proposed)**, so it installs alongside upstream |
| Orientation | portrait + landscape | **portrait only** (since M2: most layouts are taller than wide), full-screen (system bars hidden; swipe from the edge to show them) |
| UI | Compose, single Activity | same; board stays a single `Canvas` |

## Look & feel

### Theme

Dark only. Near-black, slightly warm surfaces (deep charcoal, never pure `#000`, so tile edges and shadows still read). Text is soft off-white, not pure white. Accent: a muted amber/jade glow for selection and primary buttons. All UI colours come from one token set, so a later tweak is a single file.

### Android 17 conventions (since 2026-10-26)

The menus and controls follow **Material 3 Expressive**, Android 16–17's design language, on the same dark palette (no wallpaper colours):

- **Type:** **Google Sans Flex** (OFL; Android's own typeface) everywhere except the title screen, on the Material 3 Expressive type scale. The bundled file keeps its weight and optical-size axes; three families use optical sizes tuned to small, medium and large text.
- **Screens:** a large title that shrinks into the top bar on scroll, with a round tonal back button. Settings, Levels and Layouts are **segmented lists**: rounded groups with small inner corners and hairline gaps, leading icons in soft circles, switches with a tick in the thumb, sentence-case labels.
- **Shapes:** level badges use the Expressive shape set (a "sunny" burst for the level you're up to, a soft cookie with a tick once cleared). Buttons are pills whose corners square off slightly when pressed. The table choice is a connected button group.
- **In game:** the level and layout name sit at the top; **Hint · Undo · Restart** are big labelled buttons in a floating toolbar under the board (the first step of the big-button bar; M5 may add Shuffle).
- **Depth and blur:** dialogs blur what's behind them (window blur), and the board blurs softly behind the win celebration.
- **Motion:** springy expressive motion; screens change with a shared-axis depth transition, and the back gesture is **predictive**: the screen shrinks toward the swipe with rounded corners, the title screen visible behind it.
- **Loading:** Material's shape-morphing loading indicator.
- **Launcher icon:** the name seal itself, as an adaptive icon: vermilion background, the carved white characters on the foreground, plus a monochrome layer for themed icons. Drawn by `tools/make-launcher-icon.py` from the seal font, with the title seal's roughened edges but no worn flecks.

### The board: softly lit

- **Lighting:** one gentle overhead "lamp". A soft pool of light falls on the centre of the table and fades to dark at the edges (vignette). Tiles are lit from the upper-left with a low-intensity highlight, so they read as solid objects without shine or glare.
- **Tiles:** chunky, rounded, with visible thickness (top face plus a darker side edge showing depth) and soft contact shadows. Faces are a **muted warm ivory at reduced brightness**, not white; glyph colours are deepened to match.
- **States:** the selected tile lifts slightly with a soft amber rim glow; hinted pairs breathe gently; blocked tiles dim (optional). Matched pairs slide together and dissolve into a few drifting embers.
- **Tile faces:** samoheen's CC0 vector art, recoloured per tile set. For speed, each face is rasterised **once per (tile set, zoom level)** into a texture atlas, and the board draws bitmaps instead of 144 vector paths a frame. While pinching, the cached atlas is drawn scaled; it is re-rasterised sharp when the gesture ends.

### Backgrounds: generated by code

A family of **AGSL fragment shaders**, one palette per journey chapter: e.g. midnight indigo ink-wash mountains, deep jade forest mist, ember-lit lacquer, plum dusk.

- **Built from:** layered fractal noise (fBm) for ridgelines and mist, domain-warping for organic flow, and a soft depth haze so layers recede.
- **No banding:** dark gradients are where cheap backgrounds look bad (visible stepping). Every shader adds **blue-noise dithering** before output, and colour maths runs in linear space.
- **Cheap to run:** the scene renders once to an offscreen image when the chapter or screen size changes. An optional, very slow mist drift runs on its own layer at a low update rate, so the background never competes with the board for frame time or battery.
- **Readable:** backgrounds stay low-contrast and darker at the board's edge, so the board's light pool always stands out.

### Title screen

- **Photo:** full-bleed, darkened, misty Chinese mountains, cropped to a tall portrait frame with the lone pine in the middle: *Sanqing Mountain is surrounded by clouds and mists* (Huangdan2060, CC0, Wikimedia Commons). Scaled uniformly and never stretched, with subtle film grain (AGSL). A tiny courtesy credit sits bottom-right.
- **Moving cloud: parked (2026-10-26).** A slowly drifting mist layer over the photo was tried. On the Pixel its reduced-resolution layer showed a hard-edged rectangle (part of the layer not refreshing), so it was removed for now. The last version is in commit `ef2c448` (`DriftingFog.kt`) if we revisit it; a fix would render it at full resolution or into a properly invalidated offscreen bitmap.
- **Top, over the misty sky:** the **暗夜麻將** name seal (seal script, from Taiwan's CNS 11643 說文解字 font, subset; white on deep vermilion with carved-stone wear) and, close beneath it (about 10 dp of visible gap), "DARK MAHJONG" in bold **Cinzel**, 21sp, letter-spaced 0.28 em.
- **Bottom, over the dark forest:** a plain text menu in **Google Sans Flex** 20sp, left-aligned (Continue · Play · Layouts · Settings). Items brighten while pressed and give a soft click plus a light haptic.
- **Chosen 2026-10-26** over the original all-Cinzel layout (menu in Cinzel 17sp, title 19sp semibold spaced 0.32 em, 20 dp gap), which remains available as `TitleMenuStyle.SERIF`.

### The table: tilted camera, lamp light

- **Camera:** about 20° away from overhead (`TableCamera`). The board is laid out flat and projected to the screen through one homography. The tabletop shader uses the exact inverse, so tiles and table always agree, and taps are mapped back through it. Tile thickness shows mostly on the front edge, and upper layers shift up the table to match.
- **Lamp:** one warm lamp hangs over the middle of the table and the room falls away into darkness. The overall exposure (×0.9, set 2026-10-26) is shared by the table shader and the tiles' light. Tiles are modulated by the same radial falloff, full under the lamp but never too dark to read. Their glossy faces brighten towards the top, catch a fine bevel highlight, and near the lamp's mirror point show a soft reflection of it.
- **Used, glazed tiles:** every tile looks like a real tile from a well-played set, varying from tile to tile but stable for each one (`TileWear`, chosen from the tile id).
  - **Wear:** handled grime worked into the edges and corners, hairline scratches that catch the light, the odd tiny ding, a faint fingerprint haze. There are 8 generated textures × 4 orientations.
  - **Ageing and placement:** ivory from crisp to warmly yellowed, and a fraction-off-square placement.
  - **Thick clear glaze on a flat face:** drawn over the artwork as on a real tile. The base tone stays even, and the gloss shows in sharp features rather than shading: a fine crisp edge glint, the reflections below, and bevel glints.
  - **Reflections that reveal the surface:** streaky specular highlights where the lamp's reflection is broken up by ripples in the glaze, with scratches flaring brighter inside the reflection. A crisp lamp reflection with a wavering lower edge and a slight per-tile tilt, plus a sharp hotspot near the lamp.
  - **Flat, but not machine-perfect:** faces are flat (no dome shading, which read as pillowed). Imperfection comes from broad, faint ripples where the glaze pooled thicker or ran thinner, and from patchy grime rather than an even ring around the edge.
  - **Engraved artwork:** the paint sits in carved grooves, so a fine shadow falls along each stroke's upper edge and a fine light edge along its lower lip.
- **Tabletops:** all are drawn by one full-screen AGSL shader in true perspective.
  - **Green Mat:** fine felt fibres and soft mottling, matte.
  - **Walnut:** procedural narrow boards whose grain runs strictly parallel to the board edges, so every line converges with the perspective like the tile columns. Ring spacing varies across a board but not along it. Heartwood streaks, tiny pores, fine seams, and a glossy clearcoat reflecting the lamp.
  - **Thai Straw:** a woven sedge mat, redrawn procedurally from measurements of a real one (Willuconquer's photo, CC BY-SA 4.0; the photo itself bowed, so tiling it looked wavy). It lies perfectly flat, with rows square to the tiles.
    - **Weave:** tightly packed reed spindles about 11.5 strand-widths long with slightly staggered pointed tips, binding thread glimpsed between the tips.
    - **Pattern:** straw/jade stripe bands, rose separator rows, and rose motif rows with stepped blocks.
    - **Wear:** sun-faded scuffed patches, the odd frayed reed, faint stains with tide-marks, and loose fibres.

### Winning: fireworks and a warm message

Clearing a board sets off a fireworks light show **seen from directly above**. There are no rockets, since their climb is straight towards the viewer. Each shell bursts as a disc of sparks, densest at its rim, that expands, slows, shrinks and twinkles out. Bursts overlap densely, about 30 over 4 seconds, in widely varied colours: any hue, plus gold and silver. Styles are peony, ring, chrysanthemum (colour-shifting), willow (slow gold) and crackle. It respects the animations toggle. Alongside it, a **warm congratulations message** is drawn from a large hand-written pool through a no-repeat shuffle that persists across sessions; some messages are context-aware (first win of the day, tutorial, late night, morning). Then big **Next** / **Replay** / **Menu** buttons.

### Tile sets

- **Classic Dark:** muted ivory faces, deepened glyphs. The default.
- **High Contrast:** **(proposed)** dark slate faces with **bright, thick, colour-blind-safe glyphs** and a light rim on every tile. It's inverted, so contrast goes up without a bright white board.
- **Large Index:** either set above plus big corner numbers/letters, building on upstream's corner-index option.

## Accessibility & controls

- **Tile size:** Normal / Large / Extra-large. At large sizes the board is bigger than the screen, so you pan around it.
- **Pinch-to-zoom + pan** (since 2026-10-26): pinch from 1× up to 3×, centred on the fingers; once zoomed, a drag pans (stopping at the table's edges). It is a true camera zoom: the table, tiles and lamp light magnify together, the shader sharpens its fine detail to match, and the lens vignette stays fixed to the screen. A round **zoom-out** button appears at the right edge, just above the toolbar, while zoomed; one tap springs back to the whole board. Taps still select tiles at any zoom, and a pinch or a drag past the touch slop never selects one. No double-tap zoom, because it would delay every tile tap. Upstream's auto-fit as the board empties still applies underneath.
- **Turning the play area:** tried 2026-10-26 (the tabletop and tiles turned 2° together) and dropped; the board stays square to the screen.
- **Highlight free tiles** (dim blocked tiles), on by default for early levels.
- **Larger text:** 100 / 130 / 160 % on top of the system font size.
- **Big buttons:** in portrait the controls live in a **bottom bar** under the board: a floating toolbar of large labelled buttons, icon plus word (Hint · Undo · Restart since 2026-10-26; M5 may add Shuffle). Back is the round button at the top left, or the back gesture. Menus use big list rows.
- Sounds, music and haptics stay, each with its own toggle.

## Screens (all portrait)

1. **Home:** one big primary button, **"Continue — Level 37"**, then **Today's Puzzle** (ticked once done), **Quick Game** and **Settings**.
2. **Journey map:** a **horizontally scrolling** winding path of level nodes across painted (shader) scenery, in chapters of 20; completed nodes ticked, the current one glowing. Tap a node to play it (any completed or current level can be replayed).
3. **Today's Puzzle:** **today only**. A month calendar shows the days you completed with a tick; past days can't be played. No streak counter.
4. **Quick Game:** pick **Easy / Medium / Hard / Expert** and get a fresh randomised board at that difficulty. **(Proposed)** A secondary "Choose layout" option lets you pick a specific layout.
5. **Game:** the board, the bottom button bar, and a small level label. On a win: a short celebration, then a big **Next** button.
6. **Settings:** tile set, tile size, text size, highlight free tiles, rail side, background motion, sound, music, haptics, animations.
7. **Tutorial:** upstream's guided tutorial, kept as Level 0.

## Journey & difficulty (300 levels)

Each journey level is **fixed**: level *N* always deals the same board (layout + seed). The levers, ramped gradually:

- **Tile count:** ~24 → 144.
- **Layers:** 1 → 5.
- **Distinct tile faces:** fewer faces means more matching choices, so it's easier.
- **Openness:** how many free pairs the board starts with.

**Layout pool:**
- upstream's 12 portrait level layouts (tall, phone-shaped) and 12 classic layouts (wide);
- **29 ffalt/mah layouts, ported 2026-10-26** (`MahLayouts.kt`, generated by `tools/import-mah-layouts.py`; MIT). All of Mah's 84 boards are 144 tiles. Only those whose tiles are at least 30 dp wide when fitted to the Pixel's board area were kept (the levels' tiles are about 47 dp), so none needs constant zooming and panning; 33 passed that. Four of those (Interweaved, Teotihuacan, The Question Mark, Vagues) were then dropped because tiles overhang thin air, which Mah's flat drawing allows but our solid, lit tiles and the support test don't. None duplicates a Dark Mahjong layout (checked by tile arrangement; two share an outline but stack differently). Provisional difficulty from the share of tiles free at the start: 30%+ Medium, 20–29% Hard, under 20% Expert (7 / 12 / 10). They're in Layouts, labelled with Mah's groups (Animals, Plants, Symbols, Shapes, Architecture), and in Levels;
- for the early levels, smaller boards *derived* from those: e.g. the bottom layer or two, or a centred section, trimmed to an even count.

**Interim ordering (since 2026-10-26):** Levels (41: upstream's 12 phone-shaped layouts plus the 29 from Mah) and the Layouts list both run easy to hard: by difficulty band, then fewer tiles, then the board with more tiles free at the start. Levels 1–8 kept their layouts, so earlier progress still means the same boards. Every level must give tiles at least 30 dp wide on the Pixel (BoardFitTest).

**Ordering by measured difficulty:** a JVM tool plays every candidate (layout, seed, face count) thousands of times with a simple random-greedy player. It records how often the player hits a dead end and how many choices it has per move, then picks 300 boards that ramp smoothly. The result is checked into the repo as data, so levels never change between builds.

**Quick Game difficulty bands** reuse the same measurement: Easy ≈ journey levels 1–60, Medium 60–150, Hard 150–240, Expert 240–300 and beyond, each with a random seed.

## Today's Puzzle

Seed = today's date (`yyyyMMdd`), so the puzzle is the same all day, fully offline, with nothing to download. The layout rotates daily; difficulty follows the weekday (gentle Monday → harder Sunday). Playable only on its day.

## State model

- **Settings** (DataStore): the options above.
- **Progress:** `highestLevelCompleted: Int`, `dailyCompleted: Set<epochDay>`. Nothing else is recorded.
- **Saved game:** upstream's `SavedGameState` for resume, plus its mode (Journey *N* / Daily *date* / Quick *difficulty + seed*). An unfinished daily puzzle from a previous day is discarded at midnight.
- **LevelSpec:** `(level, layoutId, seed, faceCount, highlightFree)`, loaded from the checked-in level table.
- **Layouts:** upstream `Constellation`s plus ffalt/mah boards, kept in Mah's compact format in generated Kotlin (`MahLayouts.kt`) and decoded when first needed, so plain JVM tests can load them without Android assets.
- **Removed:** `GameStats`, `StatsScreen`, the timer, best times, `SupportDialog`.

## Performance plan

- No allocations in the draw loop; tile geometry and draw order are precomputed per board, and only changed tiles are invalidated.
- Tile atlas (above); shadows baked into the atlas; background rendered offscreen.
- A Baseline Profile for fast cold start.
- Measured on the Pixel 9 Pro with `adb shell dumpsys gfxinfo <pkg> framestats`. The target is no janky frames during a pinch-zoom across a full 144-tile board.

## Licences

- Code: GPLv3 with upstream's app-store exception; keep `NOTICE.md` and add Dark Mahjong's changes.
- Tiles: samoheen, CC0. Music: upstream's Suno tracks, CC0. Fonts: Google Sans Flex and Cinzel, OFL (upstream's Tomorrow is no longer bundled).
- Layouts from ffalt/mah: MIT; add its copyright notice.
- Sounds: T-STUDIO pack (not GPL), kept for personal use only. **Next milestone (M4, sound design):** replace every sound with open-source files (CC0 preferred, e.g. Kenney's packs), so the app can go on Google Play.
- Backgrounds: our own shader code, so nothing to license.

## Testing

- JVM unit tests: generator determinism (same seed → same board), solvability, level-table ordering, daily seed per date.
- Builds via `./gradlew`, memory-capped through `systemd-run --user` (peak 5.4 GB on the first build).
- **Screenshot tour** (`ScreenshotTour`, Robolectric + Roborazzi): renders the real Compose screens, AGSL shaders and render effects included, at the Pixel's portrait size and density into `app/build/screenshots`. No device or emulator is needed (this machine's BIOS has virtualisation off, so no emulator).
- The Pixel 9 Pro over **Wireless debugging** (no cable): I install with `adb install -r`, read crashes with `adb logcat`, and take screenshots with `adb exec-out screencap` so we can both see what changed.

## Milestones

| | |
|---|---|
| M0 | Toolchain + unmodified upstream running on the Pixel ✅ done 2026-10-25 (adb over Tailscale) |
| M1 | Rebrand + strip: Dark Mahjong name/ID, landscape lock, minSdk 33, dark token theme, remove stats/timer/moves/Ko-fi; photo title screen with name seal; win fireworks + varied messages ✅ 2026-10-25 (win celebration not yet seen on the phone) |
| M2 | Portrait refocus; fireworks seen from above; tilted camera; walnut / green mat / Thai straw (photo) tabletops; shiny lamp-lit tiles in the dark; drifting title fog; JVM screenshot tests ✅ 2026-10-26 |
| M3 | Android 17 look: Material 3 Expressive components and motion, Google Sans Flex, predictive back, window blur, floating game toolbar, Sans Flex title menu; the name seal as launcher icon; pinch-to-zoom with one-tap zoom-out; 29 layouts ported from ffalt/mah ✅ 2026-10-26 |
| M4 | **In progress: sound design.** Match sound replaced (2026-10-26, CC0): a tile clack made for the game, sped up 1.4×, over a soft burst of synthesised stardust (pentatonic chimes over a glitter fizz), in four takes at slightly different pitches, each match also played at a random speed within ±3 %; built by `tools/make-match-sounds.py`; the four T-STUDIO pick sounds are left. Open-source sounds only (CC0 preferred; every file's licence recorded in NOTICE.md), replacing the T-STUDIO pack, so the app can be listed on Google Play |
| M5 | Board renderer: tile atlas, tile-size setting, Shuffle in the button bar |
| M6 | Tile sets (Classic Dark, High Contrast, Large Index), text scale |
| M7 | Difficulty tool, 300-level table, journey map |
| M8 | Today's Puzzle + calendar, Quick Game |
| M9 | Shader backgrounds for the journey chapters + motion polish (match dissolve) |
| M10 | Performance pass on the Pixel (atlas tuning, Baseline Profile) |

Each finished milestone is tagged in git (`milestone-01`, `milestone-02`, `milestone-03`).

## Remaining decisions

The **(proposed)** items above: the app ID, the inverted High Contrast set and a "Choose layout" option inside Quick Game. Approved 2026-10-25. The landscape side rail was superseded by the portrait refocus on 2026-10-26.
