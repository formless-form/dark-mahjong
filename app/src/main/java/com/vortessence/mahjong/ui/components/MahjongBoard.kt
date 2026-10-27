package com.vortessence.mahjong.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Painter's Algorithm order for tiles:
 * 1. Layer Z ascending (bottom layer first, apex layer last)
 * 2. X + Y ascending: a tile's depth edge extends right and down, so any tile overlapping it from
 *    the east or south must be drawn later. Tiles are 2x2 grid units, so such a neighbor always has
 *    a strictly larger X + Y, even on half-offset rows/columns where plain X-then-Y ordering fails.
 * 3. X ascending as a tie-breaker for diagonal neighbors that only touch at a corner
 */
private val TileDrawOrder: Comparator<BoardTile> =
    compareBy<BoardTile> { it.z }
        .thenBy { it.x + it.y }
        .thenBy { it.x }

private class SmashAnimationItem(
    val id: String,
    val tile1: BoardTile,
    val tile2: BoardTile,
    val animatable: Animatable<Float, AnimationVector1D>,
)

private data class SmashFrame(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val scale: Float,
    val alpha: Float,
    val impactFraction: Float
)

@Composable
fun MahjongBoard(
    gameState: GameState,
    modifier: Modifier = Modifier,
    settings: GameSettings = GameSettings(),
    highlightedTileIds: Set<Int> = emptySet(),
    onTileClick: (Int) -> Unit,
    onTileMatch: () -> Unit = {},
) {
    val density = LocalDensity.current
    val paddingPx = with(density) { 16.dp.toPx() }

    // Hint pulse animation
    val hintPulse = remember { Animatable(0f) }
    LaunchedEffect(gameState.hintPair) {
        if (gameState.hintPair != null) {
            hintPulse.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            hintPulse.snapTo(0f)
        }
    }

    // Blocked shake/flash state
    var blockedFlashId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(gameState.blockedTileId) {
        val blockedId = gameState.blockedTileId
        if (blockedId != null) {
            blockedFlashId = blockedId
            try {
                delay(350.milliseconds)
            } finally {
                blockedFlashId = null
            }
        } else {
            blockedFlashId = null
        }
    }

    var boardSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // A zoomed-in scene must stay within the board's area
            .clipToBounds()
            .onSizeChanged { boardSize = it }
    ) {
        val canvasW = boardSize.width.toFloat()
        val canvasH = boardSize.height.toFloat()

        if ((canvasW > 0f) && (canvasH > 0f) && gameState.tiles.isNotEmpty()) {
            key(gameState.constellation.id) {
                BoardContent(
                    gameState = gameState,
                    settings = settings,
                    highlightedTileIds = highlightedTileIds,
                    onTileClick = onTileClick,
                    onTileMatch = onTileMatch,
                    canvasW = canvasW,
                    canvasH = canvasH,
                    paddingPx = paddingPx,
                    density = density,
                    blockedFlashId = blockedFlashId
                )
            }
        }
    }
}

@Composable
private fun BoardContent(
    gameState: GameState,
    settings: GameSettings,
    highlightedTileIds: Set<Int>,
    onTileClick: (Int) -> Unit,
    onTileMatch: () -> Unit = {},
    canvasW: Float,
    canvasH: Float,
    paddingPx: Float,
    density: Density,
    blockedFlashId: Int?
) {
    val context = LocalContext.current

    // Base transform: unzoomed full layout bounds
    val baseTransform = remember(gameState.tiles.size, canvasW, canvasH, paddingPx) {
        computeBoardTransform(
            tiles = gameState.tiles,
            availableWidth = canvasW,
            availableHeight = canvasH,
            paddingPx = paddingPx
        )
    }

    // Target transform: shrinks/zooms in on remaining active tiles when autoZoomEnabled is true
    val targetTransform = remember(
        gameState.tiles,
        canvasW,
        canvasH,
        paddingPx,
        settings.autoZoomEnabled,
        baseTransform
    ) {
        if (settings.autoZoomEnabled) {
            val activeTiles = gameState.tiles.filter { !it.isRemoved }
            if (activeTiles.isNotEmpty()) {
                computeBoardTransform(
                    tiles = activeTiles,
                    availableWidth = canvasW,
                    availableHeight = canvasH,
                    paddingPx = paddingPx,
                    maxZoomFactor = 1.8f,
                    baseUnitX = baseTransform.unitX
                )
            } else {
                baseTransform
            }
        } else {
            baseTransform
        }
    }

    val zoomAnimSpec = if (settings.animationsEnabled) {
        tween<Float>(durationMillis = 500, easing = FastOutSlowInEasing)
    } else {
        snap()
    }

    val animatedUnitX by animateFloatAsState(
        targetValue = targetTransform.unitX,
        animationSpec = zoomAnimSpec,
        label = "boardUnitX"
    )
    val animatedStartX by animateFloatAsState(
        targetValue = targetTransform.startX,
        animationSpec = zoomAnimSpec,
        label = "boardStartX"
    )
    val animatedStartY by animateFloatAsState(
        targetValue = targetTransform.startY,
        animationSpec = zoomAnimSpec,
        label = "boardStartY"
    )

    val currentTileW = animatedUnitX * 2f
    val currentTileH = currentTileW * BoardTransform.ASPECT_RATIO
    val currentShiftX = currentTileW * BoardTransform.LAYER_SHIFT_FACTOR_X
    val currentShiftY = currentTileH * BoardTransform.LAYER_SHIFT_FACTOR_Y

    val currentTransform = BoardTransform(
        unitX = animatedUnitX,
        tileWidth = currentTileW,
        tileHeight = currentTileH,
        startX = animatedStartX,
        startY = animatedStartY,
        layerShiftX = currentShiftX,
        layerShiftY = currentShiftY,
        depthX = currentShiftX,
        depthY = currentShiftY
    )

    // Matching tiles smash animations, each ending in a small firework burst where they meet
    val matchBursts = remember { MatchBursts() }
    var activeSmashes by remember { mutableStateOf<List<SmashAnimationItem>>(emptyList()) }
    var prevMoveCount by remember { mutableIntStateOf(gameState.moveHistory.size) }

    LaunchedEffect(gameState.moveHistory.size, settings.animationsEnabled) {
        val currentCount = gameState.moveHistory.size
        if (currentCount > prevMoveCount && settings.animationsEnabled) {
            val latestMove = gameState.moveHistory.last()
            val t1 = gameState.tiles.firstOrNull { it.id == latestMove.tileId1 }
            val t2 = gameState.tiles.firstOrNull { it.id == latestMove.tileId2 }
            if (t1 != null && t2 != null) {
                val animId = "${t1.id}_${t2.id}_${System.currentTimeMillis()}"
                val smashItem = SmashAnimationItem(
                    id = animId,
                    tile1 = t1,
                    tile2 = t2,
                    animatable = Animatable(0f)
                )
                activeSmashes += smashItem
                launch {
                    delay(280.milliseconds)
                    onTileMatch()
                }
                launch {
                    // Burst the moment the raised pair's edges touch, where they meet
                    val transform = currentTransform
                    delay((SMASH_MILLIS * smashContactFraction(t1, t2, transform)).toLong().milliseconds)
                    val lift = with(density) { 16.dp.toPx() }
                    matchBursts.burst(
                        x = (transform.tilePixelX(t1.x, t1.z) + transform.tilePixelX(t2.x, t2.z) + transform.tileWidth) / 2f,
                        y = (transform.tilePixelY(t1.y, t1.z) + transform.tilePixelY(t2.y, t2.z) + transform.tileHeight) / 2f - lift,
                        reach = transform.tileWidth * 1.6f,
                        canvasHeight = canvasH,
                    )
                }
                launch {
                    smashItem.animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = SMASH_MILLIS, easing = LinearEasing)
                    )
                    activeSmashes = activeSmashes.filter { it.id != animId }
                }
            }
        } else if (currentCount < prevMoveCount) {
            // Undo occurred: cancel in-flight animations so restored tiles display normally
            activeSmashes = emptyList()
        }
        prevMoveCount = currentCount
    }

    // Read the latest values inside the gesture detector so it is not restarted on every
    // zoom animation frame (which would drop in-flight taps)
    val latestTiles by rememberUpdatedState(gameState.tiles)
    val latestTransform by rememberUpdatedState(currentTransform)
    val latestOnTileClick by rememberUpdatedState(onTileClick)

    // Flat table layout -> screen through the tilted camera, and back again for taps
    val planeMatrix = remember(canvasW, canvasH) { TableCamera.planeMatrix(canvasW, canvasH) }
    val screenToPlane = remember(planeMatrix) { android.graphics.Matrix().also { planeMatrix.invert(it) } }
    // Firelight flickers, so it is redrawn every frame; the lamp alone never changes
    val firelit = settings.fireplaceLit
    val flameSeconds by produceState(0f, firelit) {
        if (firelit) while (true) withFrameNanos { value = it / 1e9f }
    }
    // The board's ambient occlusion and the fire's long shadows, worked out only when the tiles or
    // the board's layout change. The first is made at once; later ones off the main thread, after
    // the board has settled (so a zoom animation doesn't start one every frame), with the old map
    // shown meanwhile.
    val tracedShadows = firelit && settings.fireShadows
    val shadowMap by produceState<BoardShadowMap?>(null, gameState.tiles, currentTransform, canvasW, canvasH) {
        if (value == null) {
            value = BoardShadowMap.build(gameState.tiles, currentTransform, canvasW, canvasH)
        } else {
            delay(80.milliseconds)
            value = withContext(Dispatchers.Default) {
                BoardShadowMap.build(gameState.tiles, currentTransform, canvasW, canvasH)
            }
        }
    }
    // Pinch-to-zoom: a camera zoom over the whole scene (table, tiles and lamp light alike), with
    // screen = scene * zoom + pan. Panning stops at the scene's edges, so the table always fills
    // the view.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val zoomedIn by remember { derivedStateOf { zoom > 1.01f } }
    val scope = rememberCoroutineScope()
    var zoomOutJob by remember { mutableStateOf<Job?>(null) }
    val clampPan = { p: Offset, z: Float ->
        Offset(p.x.coerceIn(canvasW * (1f - z), 0f), p.y.coerceIn(canvasH * (1f - z), 0f))
    }
    val zoomOut = {
        val fromZoom = zoom
        val fromPan = pan
        zoomOutJob?.cancel()
        zoomOutJob = scope.launch {
            animate(
                0f, 1f,
                animationSpec = if (settings.animationsEnabled) {
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                } else {
                    snap()
                },
            ) { f, _ ->
                zoom = fromZoom + (1f - fromZoom) * f
                pan = fromPan * (1f - f)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Back through the zoom and the camera to the flat layout
                    val p = floatArrayOf((offset.x - pan.x) / zoom, (offset.y - pan.y) / zoom)
                    screenToPlane.mapPoints(p)
                    val tappedId = findTappedTileId(p[0], p[1], latestTiles, latestTransform)
                    if (tappedId != null) {
                        latestOnTileClick(tappedId)
                    }
                }
            }
            // Inside the tap detector, so a pinch or a drag past the touch slop cancels the tap
            .pointerInput(canvasW, canvasH) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    zoomOutJob?.cancel()
                    val newZoom = (zoom * zoomChange).coerceIn(1f, TableCamera.MAX_ZOOM)
                    // Keep the spot under the fingers where it is, then follow the fingers
                    val scenePoint = (centroid - pan) / zoom
                    pan = clampPan(centroid - scenePoint * newZoom + panChange, newZoom)
                    zoom = newZoom
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawContext.canvas.save()
            drawContext.canvas.translate(pan.x, pan.y)
            drawContext.canvas.scale(zoom, zoom)

            val flame = FlameLight.at(flameSeconds, settings.lampBrightness, settings.fireplaceLit, settings.fireIntensity)
            TableSurfaceRenderer.drawTableSurface(
                scope = this,
                surface = settings.tableSurface,
                width = canvasW,
                height = canvasH,
                context = context,
                zoom = zoom,
                pan = pan,
                flame = flame,
                shadowMap = shadowMap,
                fireShadows = tracedShadows,
            )

            val sortedTiles = gameState.tiles
                .filter { !it.isRemoved }
                .sortedWith(TileDrawOrder)

            val nativeCanvas = drawContext.canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.concat(planeMatrix)
            // One layer for everything on the table, so the lamp light can be applied to it whole
            drawContext.canvas.saveLayer(
                Rect(-canvasW * 0.25f, -canvasH * 0.25f, canvasW * 1.25f, canvasH * 1.25f),
                Paint(),
            )
            var layerShaded = 0
            for (tile in sortedTiles) {
                // On reaching each raised layer, first lay all its contact shadows on the layer
                // below; its own tiles then cover any that reach a neighbour of the same height
                if (tile.z > layerShaded) {
                    layerShaded = tile.z
                    for (above in sortedTiles) {
                        if (above.z != tile.z) continue
                        TileRenderer.drawContactShadow(
                            this,
                            currentTransform.tilePixelX(above.x, above.z),
                            currentTransform.tilePixelY(above.y, above.z),
                            currentTransform.tileWidth,
                            currentTransform.tileHeight,
                        )
                    }
                }
                val px = currentTransform.tilePixelX(tile.x, tile.z)
                val py = currentTransform.tilePixelY(tile.y, tile.z)
                val isSelected = tile.id == gameState.selectedTileId
                val isHinted = gameState.hintPair?.let { it.first == tile.id || it.second == tile.id } ?: false
                val isBlockedFlash = (blockedFlashId != null) && (tile.id == blockedFlashId)

                TileRenderer.drawTile(
                    scope = this,
                    boardTile = tile,
                    x = px,
                    y = py,
                    width = currentTransform.tileWidth,
                    height = currentTransform.tileHeight,
                    isSelected = isSelected,
                    isHinted = isHinted,
                    isBlockedHighlight = isBlockedFlash,
                    isTutorialHighlight = tile.id in highlightedTileIds,
                    highlightFreeTiles = settings.highlightFreeTiles,
                    showCornerIndex = settings.showCornerIndex,
                    glare = tileGlare(px + currentTransform.tileWidth / 2f, py + currentTransform.tileHeight / 2f, canvasW, canvasH, flame),
                    shadows = tileShadows(
                        px + currentTransform.tileWidth / 2f, py + currentTransform.tileHeight / 2f, tile.z,
                        currentTransform.tileWidth, canvasW, canvasH, flame, paintFire = !tracedShadows,
                    ),
                )
            }

            // Draw matching pairs being raised, smashed into each other, and disappearing
            for (smash in activeSmashes) {
                drawSmashAnimation(
                    smash = smash,
                    transform = currentTransform,
                    density = density,
                    showCornerIndex = settings.showCornerIndex
                )
            }

            // Lamp and firelight: multiplies everything drawn on the table (alpha untouched), full and
            // warm under the lamp, falling off towards the dark edges
            drawRect(
                brush = TableSurfaceRenderer.tileLight(canvasW, canvasH, flame, shadowMap, tracedShadows),
                topLeft = Offset(-canvasW * 0.25f, -canvasH * 0.25f),
                size = Size(canvasW * 1.5f, canvasH * 1.5f),
                blendMode = BlendMode.Modulate,
            )
            drawContext.canvas.restore()
            // Firework light is its own, so it goes on after the lamp's falloff
            if (matchBursts.frame != 0L) matchBursts.draw(this)
            nativeCanvas.restore()
            drawContext.canvas.restore()
        }

        // Once zoomed in, one tap at the edge of the screen goes back to the whole board
        AnimatedVisibility(
            visible = zoomedIn,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            FilledTonalIconButton(
                onClick = zoomOut,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out to the whole board", modifier = Modifier.size(28.dp))
            }
        }
    }
}

/**
 * Shadows cast away from each light, as a point light casts them: a tile's top, standing zh
 * above whatever it rests on, throws its shadow (centre - light) * zh / (lightHeight - zh). A
 * raised tile's shadow mostly lands on the tiles beneath it, one tile's height down, so every
 * tile casts as one tile tall. The lamp hangs high, so its shadows only lean a little outwards;
 * firelight is low, so its shadows reach far, and they stretch, shrink and fade as the flames move.
 *
 * Each shadow is as dark as its light's share of all the light reaching the tile: a shadow keeps
 * out one light and the other still fills it. Near the hearth the fire's shadows are deep and the
 * lamp's soften; across the table, the other way round. The fire's shadows follow every flicker;
 * the lamp's hold still, weighed against the fire as it burns on average, so they change only
 * with the two sliders. With long fire shadows on ([BoardShadowMap]), the fire's are traced by the
 * shaders instead, and [paintFire] is false.
 */
private fun tileShadows(cx: Float, cy: Float, z: Int, tileW: Float, w: Float, h: Float, flame: FlameLight, paintFire: Boolean): TileShadows {
    // A tile stands about an eighth of its width tall; the light's height counts from the
    // surface the tile rests on
    val top = tileW * 0.12f
    val base = z * tileW * 0.12f
    fun cast(lx: Float, ly: Float, lightHeight: Float, cap: Float): Offset {
        val k = top / (lightHeight - base - top).coerceAtLeast(top)
        val s = Offset((cx - lx) * k, (cy - ly) * k)
        val len = s.getDistance()
        return if (len > cap) s * (cap / len) else s
    }
    val lamp = cast(w / 2f, h / 2f - 0.06f * h, 1.4f * h, tileW * 0.25f)
    if (flame.power <= 0f) return TileShadows(lamp, Offset.Zero, 0f)
    val fire = cast(w / 2f + flame.u * h, h / 2f - flame.v * h, flame.height * h, tileW * 0.9f)
    // The light reaching this tile from each, as the shaders light it (the fire by its brightness)
    val u = (cx - w / 2f) / h
    val v = (h / 2f - cy) / h
    val lampHere = flame.lamp * TableSurfaceRenderer.lampLight(u, v)
    fun fireShare(fu: Float, fv: Float, power: Float): Float {
        val du = u - fu
        val dv = v - fv
        val fireHere = power / (1f + (du * du + dv * dv) / flame.reach) * 0.6f // the fire's brightness
        return fireHere / (fireHere + lampHere)
    }
    val flickering = fireShare(flame.u, flame.v, flame.power)
    val steady = fireShare(flame.steadyU, flame.steadyV, flame.steadyPower)
    // The lamp's resting shadow is partly the tile's own contact shadow, which no light fills
    // With traced shadows on, the shaders take the fire's light away themselves
    val fireAlpha = if (paintFire) 0.6f * flickering else 0f
    return TileShadows(lamp, fire, fireAlpha = fireAlpha, lampDarkness = 1f - 0.6f * steady)
}

/** How strongly a tile's glossy face reflects the lamp and the fire, by its centre in flat table pixels. */
private fun tileGlare(cx: Float, cy: Float, w: Float, h: Float, flame: FlameLight): Float {
    val u = (cx - w / 2f) / h
    val v = (h / 2f - cy) / h
    return TableSurfaceRenderer.flameGlare(u, v, flame)
}

private const val SMASH_MILLIS = 420

/**
 * The point in the smash (0..1 of [drawSmashAnimation]'s progress) when the two raised tiles,
 * rushing together, first touch edge to edge. Never before the rush is half done, so even a pair
 * that starts side by side visibly collides.
 */
private fun smashContactFraction(t1: BoardTile, t2: BoardTile, transform: BoardTransform): Float {
    val dx = abs(transform.tilePixelX(t1.x, t1.z) - transform.tilePixelX(t2.x, t2.z))
    val dy = abs(transform.tilePixelY(t1.y, t1.z) - transform.tilePixelY(t2.y, t2.z))
    // The rectangles touch once both gaps are within a (raised, 1.18x) tile's width and height
    val apart = minOf(
        if (dx > 0f) 1.18f * transform.tileWidth / dx else 1f,
        if (dy > 0f) 1.18f * transform.tileHeight / dy else 1f,
    ).coerceIn(0f, 1f)
    // The rush closes the gap by rushRaw squared
    val rushRaw = sqrt(1f - apart).coerceAtLeast(0.5f)
    return 0.25f + 0.45f * rushRaw
}

private fun DrawScope.drawSmashAnimation(
    smash: SmashAnimationItem,
    transform: BoardTransform,
    density: Density,
    showCornerIndex: Boolean
) {
    val p = smash.animatable.value
    val t1 = smash.tile1
    val t2 = smash.tile2

    val start1X = transform.tilePixelX(t1.x, t1.z)
    val start1Y = transform.tilePixelY(t1.y, t1.z)
    val start2X = transform.tilePixelX(t2.x, t2.z)
    val start2Y = transform.tilePixelY(t2.y, t2.z)

    val midX = (start1X + start2X) / 2f
    val midY = (start1Y + start2Y) / 2f

    val maxLift = with(density) { 16.dp.toPx() }

    // Phases:
    // Phase 1 (0f..0.25f): Raise off board
    // Phase 2 (0.25f..0.70f): Smash into midpoint
    // Phase 3 (0.70f..1.0f): Impact & Disappear
    val liftFraction = (p / 0.25f).coerceIn(0f, 1f)
    val liftY = -maxLift * liftFraction

    val frame = when {
        p < 0.25f -> {
            val s = 1f + 0.18f * liftFraction
            SmashFrame(
                x1 = start1X,
                y1 = start1Y + liftY,
                x2 = start2X,
                y2 = start2Y + liftY,
                scale = s,
                alpha = 1f,
                impactFraction = 0f
            )
        }
        p < 0.70f -> {
            val rushRaw = (p - 0.25f) / 0.45f
            val rushEased = rushRaw * rushRaw
            val s = 1.18f
            SmashFrame(
                x1 = start1X + (midX - start1X) * rushEased,
                y1 = start1Y + (midY - start1Y) * rushEased + liftY,
                x2 = start2X + (midX - start2X) * rushEased,
                y2 = start2Y + (midY - start2Y) * rushEased + liftY,
                scale = s,
                alpha = 1f,
                impactFraction = 0f
            )
        }
        else -> {
            val impact = (p - 0.70f) / 0.30f
            val s = 1.18f * (1f - impact * 0.7f)
            val a = (1f - impact).coerceIn(0f, 1f)
            SmashFrame(
                x1 = midX,
                y1 = midY + liftY,
                x2 = midX,
                y2 = midY + liftY,
                scale = s,
                alpha = a,
                impactFraction = impact
            )
        }
    }

    // 1. Raised shadow cast on table plane during lift and rush
    if (frame.alpha > 0.05f && p < 0.70f) {
        val shadowAlpha = 0.35f * liftFraction
        val shadowCorner = CornerRadius(transform.tileWidth * 0.085f)
        val shadowW = transform.tileWidth * frame.scale
        val shadowH = transform.tileHeight * frame.scale
        val shadowOffset = with(density) { (6.dp + 10.dp * liftFraction).toPx() }

        drawRoundRect(
            color = Color.Black.copy(alpha = shadowAlpha),
            topLeft = Offset(frame.x1 + shadowOffset, frame.y1 - liftY + shadowOffset * 0.7f),
            size = Size(shadowW, shadowH),
            cornerRadius = shadowCorner
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = shadowAlpha),
            topLeft = Offset(frame.x2 + shadowOffset, frame.y2 - liftY + shadowOffset * 0.7f),
            size = Size(shadowW, shadowH),
            cornerRadius = shadowCorner
        )
    }

    // 2. Draw colliding raised tiles
    if (frame.alpha > 0.01f) {
        val sorted = listOf(t1 to (frame.x1 to frame.y1), t2 to (frame.x2 to frame.y2))
            .sortedWith(
                compareBy<Pair<BoardTile, Pair<Float, Float>>> { it.first.z }
                    .thenBy { it.second.first }  // X
                    .thenBy { it.second.second } // Y
            )

        for ((tile, pos) in sorted) {
            drawSingleAnimatingTile(
                tile = tile,
                posX = pos.first,
                posY = pos.second,
                scale = frame.scale,
                alpha = frame.alpha,
                transform = transform,
                showCornerIndex = showCornerIndex
            )
        }
    }
}

private fun DrawScope.drawSingleAnimatingTile(
    tile: BoardTile,
    posX: Float,
    posY: Float,
    scale: Float,
    alpha: Float,
    transform: BoardTransform,
    showCornerIndex: Boolean
) {
    val pivot = Offset(posX + transform.tileWidth / 2f, posY + transform.tileHeight / 2f)
    if (alpha < 0.99f) {
        val bounds = Rect(0f, 0f, size.width, size.height)
        val paint = Paint().apply { this.alpha = alpha }
        drawContext.canvas.saveLayer(bounds, paint)
        try {
            withTransform({
                scale(scale, scale, pivot = pivot)
            }) {
                TileRenderer.drawTile(
                    scope = this,
                    boardTile = tile.copy(isRemoved = false, isFree = true),
                    x = posX,
                    y = posY,
                    width = transform.tileWidth,
                    height = transform.tileHeight,
                    isSelected = true,
                    isHinted = false,
                    isBlockedHighlight = false,
                    highlightFreeTiles = false,
                    showCornerIndex = showCornerIndex
                )
            }
        } finally {
            drawContext.canvas.restore()
        }
    } else {
        withTransform({
            scale(scale, scale, pivot = pivot)
        }) {
            TileRenderer.drawTile(
                scope = this,
                boardTile = tile.copy(isRemoved = false, isFree = true),
                x = posX,
                y = posY,
                width = transform.tileWidth,
                height = transform.tileHeight,
                isSelected = true,
                isHinted = false,
                isBlockedHighlight = false,
                highlightFreeTiles = false,
                showCornerIndex = showCornerIndex
            )
        }
    }
}

/**
 * Geometric transformation calculating exact tile positions and sizes
 * to guarantee that the constellation fits in the viewport.
 */
data class BoardTransform(
    val unitX: Float,
    val tileWidth: Float,
    val tileHeight: Float,
    val startX: Float,
    val startY: Float,
    val layerShiftX: Float,
    val layerShiftY: Float,
    val depthX: Float,
    val depthY: Float
) {
    fun tilePixelX(gridX: Float, layerZ: Int): Float {
        return startX + gridX * unitX - layerZ * layerShiftX
    }

    fun tilePixelY(gridY: Float, layerZ: Int): Float {
        return startY + gridY * (unitX * ASPECT_RATIO) - layerZ * layerShiftY
    }

    companion object {
        const val ASPECT_RATIO = 1.333f // Mahjong tile ratio (width : height = 3 : 4)
        // The camera looks slightly up the table, so each tile's thickness shows mostly on its
        // front (lower) edge and only a sliver on its right; stacked tiles shift the same way
        const val LAYER_SHIFT_FACTOR_X = 0.03f
        const val LAYER_SHIFT_FACTOR_Y = 0.13f
    }
}

internal fun computeBoardTransform(
    tiles: List<BoardTile>,
    availableWidth: Float,
    availableHeight: Float,
    paddingPx: Float,
    maxZoomFactor: Float = 1f,
    baseUnitX: Float = 0f,
    widthSqueeze: Float = TableCamera.nearEdgeScale(),
): BoardTransform {
    val aspectRatio = BoardTransform.ASPECT_RATIO

    // Calculate normalized bounds of the given tiles
    var minNormX = Float.MAX_VALUE
    var maxNormX = -Float.MAX_VALUE
    var minNormY = Float.MAX_VALUE
    var maxNormY = -Float.MAX_VALUE

    for (t in tiles) {
        val normLeft = t.x - t.z * (2f * BoardTransform.LAYER_SHIFT_FACTOR_X)
        val normRight = t.x + 2f - t.z * (2f * BoardTransform.LAYER_SHIFT_FACTOR_X) + (2f * BoardTransform.LAYER_SHIFT_FACTOR_X)
        val normTop = (t.y * aspectRatio) - t.z * (2f * aspectRatio * BoardTransform.LAYER_SHIFT_FACTOR_Y)
        val normBottom = ((t.y + 2f) * aspectRatio) - t.z * (2f * aspectRatio * BoardTransform.LAYER_SHIFT_FACTOR_Y) + (2f * aspectRatio * BoardTransform.LAYER_SHIFT_FACTOR_Y)

        if (normLeft < minNormX) minNormX = normLeft
        if (normRight > maxNormX) maxNormX = normRight
        if (normTop < minNormY) minNormY = normTop
        if (normBottom > maxNormY) maxNormY = normBottom
    }

    if (minNormX == Float.MAX_VALUE) {
        minNormX = 0f
        maxNormX = 10f
        minNormY = 0f
        maxNormY = 10f
    }

    val totalNormW = max(1f, maxNormX - minNormX)
    val totalNormH = max(1f, maxNormY - minNormY)

    val contentW = max(10f, (availableWidth - paddingPx * 2f) / widthSqueeze)
    val contentH = max(10f, availableHeight - paddingPx * 2f)
    val paddingX = (availableWidth - contentW) * 0.5f

    var unitX = min(contentW / totalNormW, contentH / totalNormH)
    if (baseUnitX > 0f && maxZoomFactor > 1f) {
        unitX = min(unitX, baseUnitX * maxZoomFactor)
    }

    val tileW = unitX * 2f
    val tileH = tileW * aspectRatio

    val layerShiftX = tileW * BoardTransform.LAYER_SHIFT_FACTOR_X
    val layerShiftY = tileH * BoardTransform.LAYER_SHIFT_FACTOR_Y

    val boardPixelW = totalNormW * unitX
    val boardPixelH = totalNormH * unitX

    val startX = paddingX + (contentW - boardPixelW) * 0.5f - minNormX * unitX
    val startY = paddingPx + (contentH - boardPixelH) * 0.5f - minNormY * unitX

    return BoardTransform(
        unitX = unitX,
        tileWidth = tileW,
        tileHeight = tileH,
        startX = startX,
        startY = startY,
        layerShiftX = layerShiftX,
        layerShiftY = layerShiftY,
        depthX = layerShiftX,
        depthY = layerShiftY
    )
}

/**
 * Maps screen touch coordinate to top-most active tile.
 */
private fun findTappedTileId(
    touchX: Float,
    touchY: Float,
    tiles: List<BoardTile>,
    transform: BoardTransform
): Int? {
    // Test in reverse draw order (top-most visible tile first)
    val sorted = tiles
        .filter { !it.isRemoved }
        .sortedWith(TileDrawOrder.reversed())

    for (tile in sorted) {
        val px = transform.tilePixelX(tile.x, tile.z)
        val py = transform.tilePixelY(tile.y, tile.z)
        val right = px + transform.tileWidth + transform.depthX
        val bottom = py + transform.tileHeight + transform.depthY

        if (touchX in px..right && touchY in py..bottom) {
            return tile.id
        }
    }
    return null
}
