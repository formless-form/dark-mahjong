# Code review: Dark Mahjong fork vs upstream (13 Nov 2026)

**Scope:** every change on `dark-mahjong` since forking from `PcmciaKai/mahjong_solitaire`
(`upstream/master...HEAD`: 153 commits, 231 files, about 17.8k lines added).

**How:** the engine (`engine/`), view model, save and settings code (`data/`), audio, the
first-game performance check (`perf/`) and `MainActivity` were read closely. The large rendering
files (`TileRenderer`, `MahjongBoard`, `TableSurfaceRenderer`, `BoardShadowMap`, `TileCulling`)
got a targeted pass for threading, lifecycle and memory problems, not a line-by-line read.
Images, sounds, the store screenshots and the Python tools were not reviewed.

**Result:** 9 findings. The 4 most serious are fixed (below). The other 5 are written up and left
as they are.

| # | Finding | Kind | Status |
|---|---------|------|--------|
| 1 | A new deal's opening-safety search runs on the main thread | Performance | **Fixed** |
| 2 | A hint can be paid for and never shown | Bug | **Fixed** |
| 3 | Continue does nothing after Today's Puzzle rolls over at 4 am | Bug | **Fixed** |
| 4 | Fireplace leaks a player and can go silent; its loop reloads on the main thread | Bug / performance | **Fixed** |
| 5 | A delayed save emission can briefly bring back a just-cleared save | Bug (race) | Open |
| 6 | Hint cycling in `findHint` can't be reached | Dead code | Open |
| 7 | The solver rebuilds the blocking graph for every opening it checks | Performance | Open |
| 8 | Doc comments stacked above the wrong functions | Maintainability | Open |
| 9 | Fireplace re-creates `MediaPlayer` on the main thread each loop | Performance | Fixed with #4 |

#9 was reported as its own finding. It touches the same code as #4, so it was fixed together with it.

---

## Fixed

### 1. A new deal's opening-safety search runs on the main thread

**Where:** `engine/BoardSolvabilityGenerator.kt` (`deal`, `openingIsSafe`), called from
`MahjongViewModel` (`initGame`, `startTodaysPuzzle`).

**Problem:** `deal()` rejects deals whose opening move can lose the board. For a layout with 5
or fewer free pairs at the start (Quad, for example), each opening is checked with a
`HintSolver` search of up to 20,000 positions, for up to 12 redeals. A losing opening is exactly
the case where the search runs its full budget before giving up, and about a quarter of Quad
deals have one (per the 1.4.15 commit). All of this ran on the UI thread whenever a level, Quick
Game, layout, restart, next level or Today's Puzzle was dealt. The hint code already runs the
same solver on `Dispatchers.Default` for this reason.

**Effect:** starting a game on a narrow layout could freeze the UI for a noticeable moment. The
worst case is about 60 full searches, which risks an ANR on a slow phone.

**Fix:** new deals are now worked out off the main thread, in `MahjongViewModel`:

- `dealThen(make, start)` runs the deal on `dealContext` (`Dispatchers.Default`), then applies it
  on the main thread. Nothing in the view model changes until the deal is ready (level index,
  game origin, game state, screen), so the screen the player is on stays responsive. A cancelled
  deal leaves everything exactly as it was.
- Only one deal can be pending. A newer deal, `navigateTo`, `continueGame`,
  `startTodaysPuzzle` or `replayDeal` cancels it, so pressing Back while a deal is being made
  doesn't throw the player into the game afterwards.
- `initGame` was split: the state changes are now in `beginGame(state)`, and every caller
  (`beginLevel`, `startQuickGame`, `startConstellation`, `restartGame`, `startPerfScene`, and
  the new-deal branch of `startTodaysPuzzle`) goes through `dealThen`.
- `dealContext` is an internal hook so tests can deal inline. `BackNavigationTest` sets it to
  `EmptyCoroutineContext` so the game is on the table when the test checks it.

Most deals take milliseconds, so in practice the screen still changes immediately. Only on a
slow narrow deal does the menu now stay up a moment, responsive, instead of freezing.

### 2. A hint can be paid for and never shown

**Where:** `MahjongViewModel.requestHint`.

**Problem:** after the background search, the code checked the board, then called
`prefsRepo.spendHint()` (a suspending DataStore write), then checked the board *again* before
showing the hint. A match made while the write was suspended failed the second check, so the
hint was paid for and discarded.

**Fix:** the hint is now shown first and paid for afterwards. Nothing suspends between the
board check and the hint appearing, so a hint is never spent on a board that changed. The
payment runs under `NonCancellable`, so a new deal arriving mid-write doesn't skip it (the hint
was seen). If the write finds no hints left (two requests racing for the last one), the hint is
taken back off the board.

### 3. Continue does nothing after Today's Puzzle rolls over at 4 am

**Where:** `MahjongViewModel` (`continueInfo`, `todaysPuzzleStarted`), `MahjongApp`
(title-screen puzzle line).

**Problem:** these called `TodaysPuzzle.today()` only when one of their source flows emitted,
so nothing recomputed them at the 4 am rollover. `continueGame()` does check the day live.
With today's puzzle left unfinished on the title screen overnight, Continue still said "Today's
Puzzle", but tapping it found the puzzle gone and silently did nothing. The title line's weekday
and difficulty, and its solved tick, were stale the same way.

**Fix:**

- New `TodaysPuzzle.nextDayStarts(day)`: 4 am the morning after.
- The view model keeps a `today` state flow. A coroutine waits until the next rollover and then
  moves it on, and `onResume()` refreshes it as well, because a coroutine `delay` doesn't advance
  while the phone sleeps.
- `continueInfo` and `todaysPuzzleStarted` now combine `today`, and `puzzleGone` takes it as a
  parameter.
- `MahjongApp` uses `viewModel.today` for the title line, the solved tick, and
  `TodaysPuzzleScreen(today = …)`.

### 4 (and 9). Fireplace leaks a player and can go silent; its loop reloads on the main thread

**Where:** `audio/FireplaceSound.kt`.

**Problems:**

- If the first `MediaPlayer` was created and the second wasn't, the first leaked, and `playing`
  stayed `true` with no players. Every later `play()` returned early, so the fire stayed silent
  for the rest of the session.
- The loader built both players *while holding the lock*, so main-thread `play()`, `pause()` and
  `resume()` calls could wait on decoding.
- At the end of each round (the loop is 200 s), the completion listener ran on the main thread
  (the players are made on a thread with no looper) and called `MediaPlayer.create` there
  synchronously. That is the work the loader thread exists to keep off the main thread.

**Fix:**

- Players are created outside the lock and then installed under it.
- If creation fails, whatever was made is released and `playing` is reset, so the next
  `play()` tries again.
- A `generation` counter, bumped by `stop()`, makes the loader release players made for a play
  that has since stopped.
- At the end of a round, the finished player is released on the main thread (cheap), and its
  replacement is created on the loader thread (`rearm`), then set to follow the player now
  playing. If the replacement can't be made, the current round plays out and the loop ends with
  it, and `playing` is reset so the next `play()` restarts it.
- The volume is applied when players are installed, so a change made while they were being
  created isn't lost.

---

## Open (not changed)

### 5. A delayed save emission can briefly bring back a just-cleared save

**Where:** `MahjongViewModel` init, the `savedGameFlow` collector.

`saveCurrentGame()` and `clearSavedGame()` set `_savedGame` directly, and the collector of
`prefsRepo.savedGameFlow` also writes it, after parsing on `Dispatchers.Default`. If the last two
pairs are matched quickly, the emission for the second-to-last save can land *after* the win's
`clearSavedGame()`. For a moment `_savedGame` holds a board with one pair left, so the title can
offer Continue for a board just won, and `startTodaysPuzzle()` could restore it. It corrects
itself when the clear's own emission arrives.

*Suggested fix:* treat the in-memory value as the source of truth (for example, ignore emissions
older than the last local write, using a write counter), or stop setting `_savedGame` locally and
rely on the flow.

### 6. Hint cycling in `findHint` can't be reached

**Where:** `MahjongGameEngine.findHint` (around line 200), `MahjongViewModel.requestHint`.

`findHint` moves to the next free pair of the plan when a hint is already showing, but
`requestHint` returns early whenever `hintPair != null`, so `findHint` always sees `null`. Only
`HintTest` exercises the cycling.

*Suggested fix:* either let a second Hint tap through (if showing another pair is wanted), or
delete the rotation and its test.

### 7. The solver rebuilds the blocking graph for every opening it checks

**Where:** `HintSolver.Board` init, called per opening by `openingIsSafe`.

Each `solve()` builds a `Board` with an O(n²) scan that allocates a `listOf()` for every tile
pair. `openingIsSafe` calls it once per opening on the same positions, up to 5 times per try and
60 per deal. Now that deals run off the main thread (#1) this no longer causes jank, but it is
still wasted work and garbage.

*Suggested fix:* build the above/left/right relations once per position list and pass them to
`solve`, or cache them by the positions list.

### 8. Doc comments stacked above the wrong functions

**Where:** `PreferencesRepository.kt` (around line 234), `AudioManager.kt` (around line 179),
`LayoutDifficulty.kt` (around line 111).

Two `/** */` blocks sit over a single declaration. Kotlin attaches only the last one, so
`migrateLevelOrder` and `setFireRoom` end up with no docs, and their text appears to describe
the function below.

*Suggested fix:* move each block back above the function it describes.

---

## Also in this change

- The title screen's **Play** menu item is now **Journey** (`MainMenuScreen.kt`). It still opens
  Levels. `docs/DESIGN.md`'s description of the menu was updated to match.

## Verification

Built with Android Studio's bundled JDK:

```
gradlew :app:compilePlayDebugKotlin :app:testFossDebugUnitTest \
  --tests MahjongEngineTest --tests HintTest --tests TodaysPuzzleTest \
  --tests TutorialTest --tests BackNavigationTest
```

- The Play build (`compilePlayDebugKotlin`) and the foss build with its tests compile.
- 41 tests, 0 failures: `MahjongEngineTest` 20, `TutorialTest` 8, `HintTest` 5,
  `TodaysPuzzleTest` 5, `BackNavigationTest` 3. `BackNavigationTest` covers starting a level, a
  layout and Today's Puzzle through the new off-thread deal path, then pressing Back.
- **Not verified:** nothing was run on a phone. The fireplace's loop handover, the 4 am rollover
  and how a deal feels on a slow phone were reasoned about, not observed. The Roborazzi screenshot
  tests weren't run, because they re-record the tracked images.
