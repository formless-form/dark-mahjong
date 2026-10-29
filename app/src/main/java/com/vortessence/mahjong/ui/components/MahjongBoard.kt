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
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
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
    // How much of the bottom of the board's area lies under something drawn over it (the game
    // toolbar): the table carries on underneath, but the tiles are fitted above it
    bottomOverlap: Dp = 0.dp,
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
        // The table is laid out, lit and seen in perspective in the area above the overlap, and
        // simply continues below it
        val canvasH = (boardSize.height - with(density) { bottomOverlap.toPx() }).coerceAtLeast(1f)

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
                    blockedFlashId = blockedFlashId,
                    bottomOverlap = bottomOverlap,
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
    blockedFlashId: Int?,
    bottomOverlap: Dp,
) {
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()

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

    // The smash, the match sound and the burst are launched in the board's own scope, not the
    // effect's: the next match restarts the effect, and must not cut short the one before
    val animationScope = rememberCoroutineScope()
    LaunchedEffect(matchBursts) { matchBursts.run() }
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
                animationScope.launch {
                    delay(280.milliseconds)
                    onTileMatch()
                }
                animationScope.launch {
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
                animationScope.launch {
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
    // Firelight flickers, so the board is redrawn for it, 20 times a second (chosen by eye against
    // 30: the flicker looks the same); the lamp alone never changes
    val firelit = settings.fireplaceLit
    val flameSeconds by produceState(0f, firelit) {
        if (firelit) {
            var last = Long.MIN_VALUE / 2
            val interval = PerfSwitches.fireFps.takeIf { it > 0 }?.let { 1_000_000_000L / it - 2_000_000L } ?: FIRE_FRAME_NANOS
            while (true) withFrameNanos { now ->
                if (now - last >= interval) {
                    last = now
                    value = now / 1e9f
                }
            }
        }
    }
    // The board's ambient occlusion and the fire's long shadows, worked out only when tiles are
    // removed (or the screen changes size): the first at once, later ones off the main thread,
    // with the old map shown meanwhile. When the board scales or moves, the map is placed under
    // it as it is now, so the shading keeps up every frame without being rebuilt.
    val tracedShadows = firelit
    val shadowMap by produceState<BoardShadowMap?>(null, gameState.tiles, canvasW, canvasH) {
        val layout = currentTransform
        value = if (value == null) {
            BoardShadowMap.build(gameState.tiles, layout, canvasW, canvasH)
        } else {
            withContext(Dispatchers.Default) { BoardShadowMap.build(gameState.tiles, layout, canvasW, canvasH) }
        }
    }
    val placedShadows = shadowMap?.placed(currentTransform, canvasH)
    // The map is of the tiles as drawn (not still being remade after a match or an undo), so
    // the shaders may skip what lies under the tiles
    val shadowsFresh = (shadowMap?.tiles === gameState.tiles) && !PerfSwitches.isOff("cullunder")
    val tableAlbedo = remember(graphicsLayer) { TableSurfaceRenderer.AlbedoCache(graphicsLayer) }
    // Only what the player can see is drawn: tiles wholly covered by others are left out
    val visibleTiles = remember(gameState.tiles) {
        if (PerfSwitches.isOff("cull")) gameState.tiles.filter { !it.isRemoved }.sortedWith(TileDrawOrder)
        else TileCulling.visibleInDrawOrder(gameState.tiles)
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
            // The table's colour never flickers: drawn once, in screen pixels, and kept until the
            // zoom, pan, size or surface changes
            if (!PerfSwitches.isOff("table")) TableSurfaceRenderer.drawTableAlbedo(
                this, tableAlbedo, settings.tableSurface, canvasW, canvasH, context, zoom, pan,
            )
            drawContext.canvas.save()
            drawContext.canvas.translate(pan.x, pan.y)
            drawContext.canvas.scale(zoom, zoom)

            val flame = FlameLight.at(flameSeconds, settings.lampBrightness, settings.fireplaceLit, settings.fireIntensity)
            if (!PerfSwitches.isOff("table")) TableSurfaceRenderer.drawTableLight(
                scope = this,
                surface = settings.tableSurface,
                width = canvasW,
                height = canvasH,
                zoom = zoom,
                pan = pan,
                flame = flame,
                shadowMap = placedShadows,
                fireShadows = tracedShadows,
                cullUnder = shadowsFresh,
            )
            TileRenderer.TileAtlas.screenScale = zoom * TableCamera.nearEdgeScale()

            val sortedTiles = visibleTiles

            val nativeCanvas = drawContext.canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.concat(planeMatrix)
            // One layer for everything on the table, so the lamp light can be applied to it whole
            drawContext.canvas.saveLayer(
                Rect(-canvasW * 0.25f, -canvasH * 0.25f, canvasW * 1.25f, canvasH * 1.25f),
                Paint(),
            )
            var layerShaded = 0
            if (!PerfSwitches.isOff("tiles")) for (tile in sortedTiles) {
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
                    glare = tileGlare(px + currentTransform.tileWidth / 2f, py + currentTransform.tileHeight / 2f, canvasW, canvasH, flame),
                    shadows = tileShadows(
                        px + currentTransform.tileWidth / 2f, py + currentTransform.tileHeight / 2f, tile.z,
                        currentTransform.tileWidth, canvasW, canvasH, flame,
                    ),
                )
            }

            // Draw matching pairs being raised, smashed into each other, and disappearing
            for (smash in activeSmashes) {
                drawSmashAnimation(
                    smash = smash,
                    transform = currentTransform,
                    density = density,
                )
            }

            // Lamp and firelight: multiplies everything drawn on the table (alpha untouched), full and
            // warm under the lamp, falling off towards the dark edges
            if (!PerfSwitches.isOff("tilelight")) drawRect(
                brush = TableSurfaceRenderer.tileLight(canvasW, canvasH, flame, placedShadows, tracedShadows, fresh = shadowsFresh),
                topLeft = Offset(-canvasW * 0.25f, -canvasH * 0.25f),
                size = Size(canvasW * 1.5f, canvasH * 1.5f),
                blendMode = BlendMode.Modulate,
            )
            drawContext.canvas.restore()
            nativeCanvas.restore()
            drawContext.canvas.restore()
        }

        // Match bursts on a layer of their own, so a burst redraws only itself, never the board.
        // Firework light is its own, so it goes on after the lamp's falloff
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (matchBursts.frame == 0L || matchBursts.idle) return@Canvas
            val nativeCanvas = drawContext.canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.translate(pan.x, pan.y)
            nativeCanvas.scale(zoom, zoom)
            nativeCanvas.concat(planeMatrix)
            matchBursts.draw(this, canvasH)
            nativeCanvas.restore()
        }

        // Once zoomed in, one tap at the edge of the screen goes back to the whole board
        AnimatedVisibility(
            visible = zoomedIn,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + bottomOverlap),
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
 * The lamp's shadow, cast away from it as a point light casts it: a tile's top, standing zh
 * above whatever it rests on, throws its shadow (centre - light) * zh / (lightHeight - zh). A
 * raised tile's shadow mostly lands on the tiles beneath it, one tile's height down, so every
 * tile casts as one tile tall. The lamp hangs high and to one side, so its shadows lean away from
 * it, a little more for tiles further from it.
 *
 * It is as dark as the lamp's share of all the light reaching the tile: beside the hearth the
 * fire fills it in. It holds still, weighed against the fire as it burns on average, so it
 * changes only with the two sliders. The fire's own long shadows are traced by the shaders
 * ([BoardShadowMap]).
 */
private fun tileShadows(cx: Float, cy: Float, z: Int, tileW: Float, w: Float, h: Float, flame: FlameLight): TileShadows {
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
    val lamp = cast(w / 2f + LAMP_U * h, h / 2f - LAMP_V * h, LAMP_HEIGHT * h, tileW * 0.35f)
    if (flame.power <= 0f) return TileShadows(lamp)
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
    val steady = fireShare(flame.steadyU, flame.steadyV, flame.steadyPower)
    // The lamp's resting shadow is partly the tile's own contact shadow, which no light fills
    return TileShadows(lamp, lampDarkness = 1f - 0.6f * steady)
}

/** How strongly a tile's glossy face reflects the lamp and the fire, by its centre in flat table pixels. */
private fun tileGlare(cx: Float, cy: Float, w: Float, h: Float, flame: FlameLight): Float {
    val u = (cx - w / 2f) / h
    val v = (h / 2f - cy) / h
    return TableSurfaceRenderer.flameGlare(u, v, flame)
}

private const val SMASH_MILLIS = 420

// The firelight's frame interval: 20 a second, less a little so a 60 or 120 Hz screen's frames
// land on it (every 3rd or 6th)
private const val FIRE_FRAME_NANOS = 1_000_000_000L / 20 - 2_000_000L

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
    density: Density
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
                transform = transform
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
    transform: BoardTransform
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
                    highlightFreeTiles = false
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
                highlightFreeTiles = false
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
