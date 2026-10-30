# Design — Dark Mahjong

Fork of [PcmciaKai/mahjong_solitaire](https://github.com/PcmciaKai/mahjong_solitaire) (Kotlin + Jetpack Compose).
Status: **draft v2 for Gareth's review** — nothing below is built yet. Open questions from v1 are answered and folded in; the few new decisions are marked **(proposed)**.

## Goals

1. **Looks really good** — polished, tactile, Vita-Mahjong-level care, but our own look: **dark and softly lit**, never glaring.
2. **Smooth**: a steady **60 fps** with a full 144-tile board (the app asks for 60 Hz at most: plenty for a calm game, and easier on the battery; since 2026-10-29). The firelight redraws 20 times a second, chosen by eye against 30.
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

- **Photo:** full-bleed, darkened, misty Chinese mountains, cropped to a tall portrait frame with the lone pine in the middle: *Sanqing Mountain is surrounded by clouds and mists* (Huangdan2060, CC0, Wikimedia Commons). Scaled uniformly and never stretched, with subtle film grain (AGSL, `TitleBackdrop`; its hash was replaced on 2026-10-29, as the old one showed faint banding). A living version (creeping mist, slow parallax, cloud shadows) was tried on 2026-10-29 and dropped as not worth it. No credit on screen (CC0 needs none; it is credited in NOTICE.md, since 2026-10-29).
- **Moving cloud: parked (2026-10-26).** A slowly drifting mist layer over the photo was tried. On the Pixel its reduced-resolution layer showed a hard-edged rectangle (part of the layer not refreshing), so it was removed for now. The last version is in commit `ef2c448` (`DriftingFog.kt`) if we revisit it; a fix would render it at full resolution or into a properly invalidated offscreen bitmap.
- **Top, over the misty sky:** the **暗夜麻將** name seal (seal script, from Taiwan's CNS 11643 說文解字 font, subset; white on deep vermilion with carved-stone wear) and, close beneath it (about 10 dp of visible gap), "DARK MAHJONG" in bold **Cinzel**, 22sp, letter-spaced 0.12 em (21sp at 0.28 em until 2026-10-29).
- **Bottom, over the dark forest:** a plain text menu in **Google Sans Flex** 20sp, left-aligned (Continue · Play · Layouts · Settings). Items brighten while pressed and give a soft click plus a light haptic.
- **Chosen 2026-10-26** over the original all-Cinzel layout (menu in Cinzel 17sp, title 19sp semibold spaced 0.32 em, 20 dp gap), which remains available as `TitleMenuStyle.SERIF`.

### The table: tilted camera, lamp light

- **Camera:** about 20° away from overhead (`TableCamera`). The board is laid out flat and projected to the screen through one homography. The tabletop shader uses the exact inverse, so tiles and table always agree, and taps are mapped back through it. Tile thickness shows mostly on the front edge, and upper layers shift up the table to match.
- **Lamp:** one warm lamp (about 2700 K) hangs over the table, a little right of the board and up the table from it, so it lights the board at a slant and its shadows lean down and to the left (since 2026-10-29); the room falls away into darkness. The overall exposure (×0.9, set 2026-10-26) is shared by the table shader and the tiles' light. Tiles are modulated by the same radial falloff, full under the lamp but never too dark to read. Their glossy faces brighten towards the top, catch a fine bevel highlight, and near the lamp's mirror point show a soft reflection of it.
- **Lamp brightness:** a Settings slider, from about a third of the original lamp to a quarter brighter. The default (the middle) is 5% dimmer than the original (set 2026-10-27).
- **Fireplace (optional, off by default):** a hearth out of view to the left of the table (`FlameLight`). It is broad and redder (about 1700 K) and added to the lamp, which is turned down a little beside it, so every tile face stays readable. Its brightness wanders by up to a third, in random surges, licks of flame and the odd flare. It burns yellower as it brightens, and its light shifts a little as the flames move. A Fire intensity slider runs it from embers to a blaze. While it burns, a soft crackling loop plays (`FireplaceSound`, gapless; louder as the fire burns brighter): *Crackeling Fireplace* by Christopher C. Courter (Freesound 447818, CC0), a stone fireplace burning wet loblolly pine for a steady popping, 6:43 at 44.1 kHz, 256 kbps MP3 stereo, chosen 2026-10-27.
- **Shadows cast from each light:** a point-light model. The lamp's shadows lean slightly away from it. The fire's are long and soft-edged, falling across the lower tiles, stretching and fading with the flames. Each shadow is as dark as its light's share of the light reaching that tile. The lamp's hold still, changing only with the sliders.
- **Used, glazed tiles:** every tile looks like a real tile from a well-played set, varying from tile to tile but stable for each one (`TileWear`, chosen from the tile id).
  - **Wear:** handled grime worked into the edges and corners, hairline scratches that catch the light, the odd tiny ding, a faint fingerprint haze. There are 8 generated textures × 4 orientations.
  - **Ageing and placement:** the ivory evenly and warmly aged across the whole set, and every wear texture dulling a face by the same amount, so no tile reads lighter or darker than its neighbours (tiles of differing tone looked like a difference in height or light; since 2026-10-29); a fraction-off-square placement.
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

Clearing a board sets off a fireworks light show **seen from directly above**. There are no rockets, since their climb is straight towards the viewer. Each shell bursts as a disc of sparks, densest at its rim, that expands, slows, shrinks and twinkles out. Bursts overlap densely, about 30 over 4 seconds, in widely varied colours: any hue, plus gold and silver. Styles are peony, ring, chrysanthemum (colour-shifting), willow (slow gold) and crackle. It respects the animations toggle. **It sounds like the real thing:** 5.5 seconds of a small-town show recorded by greysound (Freesound 769030, CC0), mono Opus (`tools/make-win-sound.py`), with the music easing down beneath it. Alongside it, a **warm congratulations message** is drawn from a large hand-written pool through a no-repeat shuffle that persists across sessions; some messages are context-aware (first win of the day, tutorial, late night, morning). Then big **Next** / **Replay** / **Menu** buttons.

Every match ends in a small version of the same thing: as the raised pair's edges touch, a single shell (no willows, half the sparks, about half as long) bursts where they meet, timed with the stardust in the match sound. It respects the animations toggle.

### Tile sets

- **Classic Dark:** muted ivory faces, deepened glyphs. The default.
- **High Contrast:** **(proposed)** dark slate faces with **bright, thick, colour-blind-safe glyphs** and a light rim on every tile. It's inverted, so contrast goes up without a bright white board.
- **Large Index:** either set above plus big corner numbers/letters. (Upstream's small corner-index option was removed on 2026-10-29.)

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

## Performance

Done 2026-10-29, in two phases, measured on the Pixel 9 Pro (Quad, fireplace lit) with `adb shell dumpsys gfxinfo <pkg> framestats`. The render thread went from 81 ms a frame to 5 ms, the GPU from 26 to 15 ms, and matches run at 60 fps. Side-by-side renders match the unoptimised look.

- **Only what the player can see is drawn** (`TileCulling`): a tile wholly covered by tiles drawn after it is left out, with its shadows, allowing for rounded corners and the slight off-square placement. Worked out only when tiles change.
- **The tile atlas** (`TileRenderer.TileAtlas`): each tile is stamped from about five shared images (body, artwork with its engraving, each of the 32 looks' wear and fixed glaze, and each look's glare, stamped as strongly as the tile catches the light), made off the main thread at the size tiles are shown. Highlighted tiles too, since phase 3.
- **The tabletop's colour is drawn once** into a cached layer, remade only when the zoom, pan or surface changes; only its light is worked out each frame. Table pixels under the tiles skip it.
- **The fire's long shadows are traced once per pixel**, on the table or on a tile, not both; long shadows are always on (the setting is gone: its painted fallback was twice as slow).
- **Fireworks:** match bursts on their own layer (a burst never redraws the board), from a pool of three reused shells of 40 sparks; every spark is one stamp of a glow image.
- **Contact shadows** are one pre-blurred stamp.
- The fireplace is marked "More GPU-intensive" in Settings; budget phones can keep the lamp alone.
- **Phase 3 (2026-10-30, from a rendering audit):**
  - The fire's shadows are traced only where they can fall. The march from each pixel towards
    the flames takes only its steps over the board's bounds, and stops once a pixel is fully
    shadowed. That gives the same result, but most of the table needs no steps at all, and a
    cleared board none.
  - Highlighted tiles (selected, hinted, blocked, the tutorial's rings) are stamped from the
    atlas too, which holds a tinted body for each highlight. The gold glow is a pre-blurred stamp.
    Only the matched pair in flight, shown larger than the atlas's images, is still drawn shape
    by shape.
  - During a pinch, pan or spring back, the cached table colour is scaled to follow, and drawn
    afresh once the view settles. Once zoomed in, it reaches 20% past the screen.
  - The menu slate is baked once per app run and shared by Settings, Levels and Layouts.
  - Just after a match, while the new shadow map is made, the tiles' light still skips their
    layer's empty parts. The map's occlusion is worked out only around the board.
  - Smaller items: the title photo is decoded once, tiles' nudges no longer allocate, and the
    fading pair shares one layer.
  - **Measured** on the Pixel (Quad, fire lit), old and new builds alternated twice:
    - At rest, the slowest frames are gone: frame time 90th percentile 37–39 → 30–31 ms, GPU
      90th percentile 28–29 → 20 ms.
    - At 20 fps the GPU slows its clock to fill the time, so GPU time hides savings. GPU time ×
      clock shows slate at rest takes about 35% fewer GPU cycles a frame (5.4–6.0 M → 3.6–3.7 M)
      at a lower clock (240–250 → 181–189 MHz). Walnut's median is unchanged within the noise.
    - With a hint showing, the median frame is 26 → 21 ms and the 99th percentile 44 → 31 ms.
    - Matches are unchanged (GPU about 10 ms at full clock).
- **Test builds** (`tools/deploy-to-phone.sh`, `-PperfHooks=true`) can be told over adb to open a fixed deal, switch drawing stages off, set the fire's rate or play matches by themselves, for measuring; Play builds never can.
- Still to do: a Baseline Profile for fast cold start; perhaps a cheaper fire-shadow trace for weaker GPUs.

## Licences

- Code: GPLv3 with upstream's app-store exception; keep `NOTICE.md` and add Dark Mahjong's changes. Since 2026-10-29 an additional GPLv3 §7(e) term withholds the names "Dark Mahjong" / 暗夜麻將, the name seal, the launcher icon and "Grevillea Labs" from modified versions, and the README asks forks to stay ad-free and not be sold (a request: GPLv3 allows commercial use).
- Tiles: samoheen, CC0. Music: upstream's Suno tracks, CC0. Fonts: Google Sans Flex and Cinzel, OFL (upstream's Tomorrow is no longer bundled).
- Layouts from ffalt/mah: MIT; add its copyright notice.
- Sounds: all open source since 2026-10-26 (M4): the match sound made for the game and Kenney's wood taps, both CC0, a real fireworks show for the win (a dizi phrase until 2026-10-28), the fireplace recording, and the synthesised blocked sound in code. The T-STUDIO pack is gone.
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
| M4 | Sound design: every sound open source, so the app can go on Google Play. Match: a tile clack recorded for the game, sped up 1.4×, over synthesised stardust (pentatonic chimes over a glitter fizz), four takes plus random ±3 % speed (`tools/make-match-sounds.py`). Taps: Kenney light wood impacts, two takes at two pitches (`tools/make-tap-sounds.py`). Win: a dizi phrase by CarlosCarty (Freesound) with the music dipping under it (`tools/make-win-sound.py`). All CC0; T-STUDIO sounds removed ✅ 2026-10-26 |
| M5 | Light and atmosphere: Google Play closed test (1.0.0 live, 1.1.0 in review); a firework burst where each matched pair meets; lamp brightness slider; optional fireplace (flickering, shifting firelight with an intensity slider and a soft crackling loop on its own volume, heard on the game screen only); shadows cast from each light, the fire's traced per pixel through a full-resolution height map of the board, long, soft, following each tile's curved edges and jittering with the flames; contact shadows and darkened side walls between tiles, ambient occlusion on the table; the table running on under the toolbar; music capped quieter ✅ 2026-10-27 |
| M6 | Board renderer: tile atlas ✅ 2026-10-29 (with the performance work above); still to come: tile-size setting, Shuffle in the button bar |
| M7 | Tile sets (Classic Dark, High Contrast, Large Index), text scale |
| M8 | Difficulty tool, 300-level table, journey map |
| M9 | Today's Puzzle + calendar, Quick Game |
| M10 | Shader backgrounds for the journey chapters + motion polish (match dissolve) |
| M11 | Performance pass on the Pixel (atlas tuning, Baseline Profile) |

Each finished milestone is tagged in git (`milestone-01` to `milestone-05`). **Next:** M6, and 12 testers through the 14-day closed test before applying for production on Google Play.

## Remaining decisions

The **(proposed)** items above: the app ID, the inverted High Contrast set and a "Choose layout" option inside Quick Game. Approved 2026-10-25. The landscape side rail was superseded by the portrait refocus on 2026-10-26.
