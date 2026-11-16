package com.vortessence.mahjong.engine

import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.data.repository.ConstellationRepository

enum class TutorialAction {
    CONTINUE,
    TAP_BLOCKED,
    MATCH_PAIR,
    PINCH_ZOOM,
    // Zoom-out button, or pinching back out
    ZOOM_OUT,
}

data class TutorialStep(
    val title: String,
    val message: String,
    val action: TutorialAction = TutorialAction.CONTINUE,
    val targetTileIds: Set<Int> = emptySet(),
    val highlightFreeTiles: Boolean = false,
    val showSettingsToggles: Boolean = false,
    val highlightZoomOut: Boolean = false,
    val continueLabel: String = "Next",
)

/** Tile ids are position order in ConstellationRepository.createTutorial(). */
object TutorialScript {

    private const val TOP_LEFT_BAMBOO = 0
    private const val SPRING = 1
    private const val AUTUMN = 2
    private const val TOP_RIGHT_BAMBOO = 3
    private const val PLUM = 12
    private const val CHRYSANTHEMUM = 13

    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = "Welcome to Mahjong Solitaire",
            message = "Your goal is to clear the board by removing all tiles in matching pairs. " +
                "This short tutorial shows you how it works.",
            continueLabel = "Start",
        ),
        TutorialStep(
            title = "Selectable Tiles",
            message = "You can only pick a tile that is free: no other tile may lie on top of it, " +
                "and its left or right side must be open. The blue tiles are free right now.",
            highlightFreeTiles = true,
        ),
        TutorialStep(
            title = "Blocked Tiles",
            message = "The blue tile has neighbours on both its left and its right side, " +
                "so it is blocked. Tap it and see what happens.",
            action = TutorialAction.TAP_BLOCKED,
            targetTileIds = setOf(SPRING),
        ),
        TutorialStep(
            title = "Match a Pair",
            message = "Blocked tiles can't be selected. Now tap the two blue tiles. The first one turns " +
                "gold to show it is selected. They are identical, so together they form a pair and are removed.",
            action = TutorialAction.MATCH_PAIR,
            targetTileIds = setOf(TOP_LEFT_BAMBOO, TOP_RIGHT_BAMBOO),
        ),
        TutorialStep(
            title = "Seasons",
            message = "Removing the pair opened up its neighbours. These are Season tiles " +
                "(Spring, Summer, Autumn and Winter). Any two Seasons match each other, " +
                "even though they look different. Match them!",
            action = TutorialAction.MATCH_PAIR,
            targetTileIds = setOf(SPRING, AUTUMN),
        ),
        TutorialStep(
            title = "Flowers",
            message = "These two Flower tiles lie on top of other tiles, which stay blocked until the " +
                "Flowers are gone. Any two Flowers (Plum, Orchid, Chrysanthemum and Bamboo) " +
                "match each other, just like the Seasons.",
            action = TutorialAction.MATCH_PAIR,
            targetTileIds = setOf(PLUM, CHRYSANTHEMUM),
        ),
        TutorialStep(
            title = "Zoom In",
            message = "Want a closer look? Pinch the board with two fingers to zoom in, " +
                "then drag to look around.",
            action = TutorialAction.PINCH_ZOOM,
        ),
        TutorialStep(
            title = "Zoom Out",
            message = "To see the whole board again, tap the magnifying glass with the minus sign. " +
                "It appears whenever you're zoomed in.",
            action = TutorialAction.ZOOM_OUT,
            highlightZoomOut = true,
        ),
        TutorialStep(
            title = "Helpful Settings",
            message = "Want a little help? You can turn these on right here, " +
                "or change them any time in the Settings menu.",
            showSettingsToggles = true,
        ),
        TutorialStep(
            title = "Your Turn",
            message = "Clear the remaining tiles on your own. Stuck? The lightbulb shows a hint " +
                "and the arrow undoes your last move.",
            continueLabel = "Let's go",
        ),
    )

    private val guidedPairs: Set<Int> = steps
        .filter { it.action == TutorialAction.MATCH_PAIR }
        .flatMap { it.targetTileIds }
        .toSet()

    fun isTutorial(state: GameState): Boolean =
        state.constellation.id == ConstellationRepository.TUTORIAL_ID

    /** The saved step if the tiles gone agree with it, else worked out from the tiles; null for free play. */
    fun resumeStep(state: GameState, savedStep: Int?): Int? {
        if (!isTutorial(state)) return null
        if (savedStep == SavedGameState.FREE_PLAY) return null
        if (savedStep != null && savedStep in steps.indices) {
            val removed = state.tiles.filter { it.isRemoved }.map { it.id }.toSet()
            val matchedBefore = steps.take(savedStep)
                .filter { it.action == TutorialAction.MATCH_PAIR }
                .flatMap { it.targetTileIds }
                .toSet()
            if (removed == matchedBefore) return savedStep
        }
        return resumeStepFor(state)?.let { skipMatched(it, state) }
    }

    /**
     * Skips pair steps whose tiles are gone (matched in free play, then undone back into the guided
     * part); null at the end.
     */
    fun nextStep(index: Int, state: GameState): Int? = skipMatched(index + 1, state)

    private fun skipMatched(from: Int, state: GameState): Int? {
        val removed = state.tiles.filter { it.isRemoved }.map { it.id }.toSet()
        var index = from
        while (index < steps.size &&
            steps[index].action == TutorialAction.MATCH_PAIR &&
            removed.containsAll(steps[index].targetTileIds)
        ) index++
        return index.takeIf { it < steps.size }
    }

    /** Null if the guided part is over. */
    fun resumeStepFor(state: GameState): Int? {
        if (!isTutorial(state)) return null
        val removed = state.tiles.filter { it.isRemoved }.map { it.id }.toSet()
        if (removed.isEmpty()) return 0
        // Only free play removes other tiles
        if (!guidedPairs.containsAll(removed)) return null

        val nextMatch = steps.indexOfFirst {
            it.action == TutorialAction.MATCH_PAIR && !removed.containsAll(it.targetTileIds)
        }
        return if (nextMatch != -1) {
            nextMatch
        } else {
            steps.indexOfLast { it.action == TutorialAction.MATCH_PAIR } + 1
        }
    }

    fun isTapAllowed(step: TutorialStep, tileId: Int): Boolean =
        step.action != TutorialAction.CONTINUE && tileId in step.targetTileIds

    fun isStepComplete(step: TutorialStep, result: TileTapResult, newState: GameState): Boolean =
        when (step.action) {
            TutorialAction.CONTINUE -> false
            TutorialAction.TAP_BLOCKED -> result is TileTapResult.Blocked && result.tileId in step.targetTileIds
            TutorialAction.MATCH_PAIR -> newState.tiles
                .filter { it.id in step.targetTileIds }
                .all { it.isRemoved }
            TutorialAction.PINCH_ZOOM, TutorialAction.ZOOM_OUT -> false
        }

    fun isZoomStepComplete(step: TutorialStep, zoomedIn: Boolean): Boolean =
        when (step.action) {
            TutorialAction.PINCH_ZOOM -> zoomedIn
            TutorialAction.ZOOM_OUT -> !zoomedIn
            else -> false
        }
}
