package com.begoml.composenav.swipetodismiss.swipe

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.launch

/**
 * Signals whether a swipe-to-dismiss gesture is currently in progress.
 *
 * Provided by [SwipeToDismissLayout] via [CompositionLocalProvider].
 * Consumers (e.g. [com.begoml.composenav.swipetodismiss.swipe.freezeDuringSwipeToDismiss]) read this to freeze their draw output
 * while the foreground is being translated/scaled, avoiding visual artifacts from
 * position-dependent effects like Haze blur.
 */
val LocalSwipeToDismissActive = staticCompositionLocalOf { false }

/**
 * iOS-style swipe-to-dismiss layout that allows the user to drag the foreground screen
 * to the right to navigate back.
 *
 * ## Layer architecture
 *
 * The layout consists of two layers stacked in a [Box]:
 *
 * 1. **Background layer** — the previous screen in the back stack. Continuously recorded
 *    into a [GraphicsLayer][androidx.compose.ui.graphics.layer.GraphicsLayer] while the scene
 *    is RESUMED. Once a dismiss starts or the scene transitions to STARTED (pop), composition
 *    of [backgroundContent] stops and the cached snapshot is replayed. This avoids
 *    `movableContentOf` conflicts with [SinglePaneScene][androidx.navigation3.scene.SinglePaneScene].
 *    When [freezeBackgroundWhileIdle] is `true`, the background's CPU draw (`record`), GPU
 *    draw (`drawLayer`) and measure pass are skipped during RESUMED-idle while a snapshot
 *    already exists. Background composition stays alive — its `LaunchedEffect` /
 *    `DisposableEffect` are not disposed, which prevents window-scoped side effects (focus,
 *    IME) from being torn down on every touch in the foreground. On first touch
 *    (`awaitFirstDown`) draw / measure resume so the layer is refreshed before parallax
 *    exposes the background.
 *
 * 2. **Foreground layer** — the current screen. During idle state the content is recorded into
 *    a [GraphicsLayer] snapshot. Once swiping begins (`progress > 0`), only the cached snapshot
 *    is drawn (no live recomposition of draw), which prevents Haze and other position-aware
 *    effects from recalculating against the moving coordinate space.
 *
 * ## Gesture handling
 *
 * - Horizontal drag is detected via [detectHorizontalDragGestures].
 * - During drag, raw float offset is used (no coroutine-per-event) for performance.
 * - On drag end: if offset exceeds 35% of screen width, the foreground animates off-screen
 *   and [onDismiss] is called; otherwise it springs back to origin.
 * - Visual transform during swipe: translation, scale (1.0 → 0.95), alpha (1.0 → 0.8),
 *   and progressive corner rounding (0 → 48dp).
 * - Velocity-based dismiss: a fast flick (≥ 1500 dp/s) dismisses regardless of distance.
 * - Background parallax: the previous screen shifts from −screenWidth/3 to 0 during swipe.
 *
 * ## CompositionLocal
 *
 * Provides [LocalSwipeToDismissActive] = `true` to [foregroundContent] during an active swipe,
 * enabling child composables to freeze position-sensitive effects via [com.begoml.composenav.swipetodismiss.swipe.freezeDuringSwipeToDismiss].
 *
 * @param onDismiss Called when the swipe gesture completes a dismiss (navigates back).
 * @param backgroundContent Content of the previous screen (rendered behind the foreground).
 * @param foregroundContent Content of the current screen (the one being swiped away).
 * @param freezeBackgroundWhileIdle When `true`, the background's CPU draw (`record` of the
 * display-list), GPU draw (`drawLayer`) and measure pass are skipped while the foreground is
 * RESUMED, idle (no touch / swipe), and a snapshot has already been captured. Background
 * composition stays alive (`LaunchedEffect` / `DisposableEffect` are not disposed). On first
 * touch (`awaitFirstDown`) draw and measure resume so the layer is refreshed before the
 * user's finger moves enough to expose the background. When `false`, background composes
 * and draws live every frame (legacy behaviour).
 *
 * Invariants when this flag is `true`:
 * - [foregroundContent] must be fully opaque and cover the entire screen. Semi-transparent
 *   foregrounds will show a black/empty background while idle because draw is skipped.
 * - The background's measure pass is also skipped while fully covered, so size-aware
 *   callbacks in [backgroundContent] (`onSizeChanged`, `onGloballyPositioned`, `onPlaced`)
 *   must guard against zero / empty sizes to remain idempotent across measure-skip cycles.
 * @param modifier Optional modifier for the root container.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun SwipeToDismissLayout(
    onDismiss: () -> Unit,
    backgroundContent: @Composable () -> Unit,
    foregroundContent: @Composable () -> Unit,
    freezeBackgroundWhileIdle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val screenWidth = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissThreshold = screenWidth * 0.35f
    val velocityDismissThreshold = with(density) { 1500.dp.toPx() }
    val velocityTracker = remember { VelocityTracker() }

    // Drag state: use plain float during drag to avoid coroutine-per-event,
    // sync to Animatable only when animation is needed.
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var swipeConsumedByKeyboard by remember { mutableStateOf(false) }
    var isNestedScrollDragging by remember { mutableStateOf(false) }
    var nestedScrollConsumedByKeyboard by remember { mutableStateOf(false) }
    var isDismissed by remember { mutableStateOf(false) }

    // Recomposition only on false↔true transition (swipe start/end), not every frame.
    // Per-frame offset/progress reads are deferred to graphicsLayer (draw phase only).
    val isSwiping by remember {
        derivedStateOf {
            val offset = if (isDragging || isNestedScrollDragging) dragOffset else offsetX.value
            offset > 0f
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isNestedScrollDragging || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                val newOffset = (dragOffset + available.x).coerceAtLeast(0f)
                val consumed = newOffset - dragOffset
                dragOffset = newOffset
                if (dragOffset == 0f) isNestedScrollDragging = false
                return Offset(consumed, 0f)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.x <= 0f) {
                    return Offset.Zero
                }
                if (isDragging || nestedScrollConsumedByKeyboard) return Offset.Zero

                if (!isNestedScrollDragging) {
                    val isImeVisible = ViewCompat.getRootWindowInsets(view)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    if (isImeVisible) {
                        nestedScrollConsumedByKeyboard = true
                        runCatching { keyboardController?.hide() }
                        return Offset(available.x, 0f)
                    }
                    isNestedScrollDragging = true
                    dragOffset = 0f
                }
                dragOffset = (dragOffset + available.x).coerceAtLeast(0f)
                return Offset(available.x, 0f)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (nestedScrollConsumedByKeyboard) {
                    nestedScrollConsumedByKeyboard = false
                    return Velocity.Zero
                }
                if (!isNestedScrollDragging) return Velocity.Zero
                isNestedScrollDragging = false
                val velocity = available.x
                scope.launch {
                    offsetX.snapTo(dragOffset)
                    dragOffset = 0f
                    if (offsetX.value > dismissThreshold || velocity > velocityDismissThreshold) {
                        offsetX.animateTo(screenWidth, tween(200))
                        isDismissed = true
                        onDismiss()
                    } else {
                        offsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                            ),
                        )
                    }
                }
                return Velocity(velocity, 0f)
            }
        }
    }

    // NavDisplay provides AnimatedContentScope per scene slot.
    // transition.targetState == PostExit means this scene is being animated out (pop).
    // Unlike lifecycle effects (which update AFTER composition), this is a snapshot state
    // that is accurate DURING composition — preventing duplicate entry composition on back press.
    val isBeingRemoved = LocalNavAnimatedContentScope.current
        .transition.targetState == EnterExitState.PostExit

    // Scene lifecycle provided by NavDisplay:
    // STARTED during push/pop transitions, RESUMED when settled.
    // - STARTED on push: don't compose backgroundContent (movableContentOf is in SinglePaneScene).
    // - RESUMED: compose backgroundContent and record into GraphicsLayer.
    // - isDismissed / STARTED on pop: show cached GraphicsLayer snapshot, stop composing
    //   backgroundContent so movableContentOf can move to SinglePaneScene without conflict.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(false) }
    var hasBeenResumed by remember { mutableStateOf(false) }
    val backgroundLayer = rememberGraphicsLayer()
    val foregroundLayer = rememberGraphicsLayer()
    var hasForegroundSnapshot by remember { mutableStateOf(false) }

    // Background freeze state (active only when freezeBackgroundWhileIdle = true).
    // hasBackgroundSnapshot: flips true once the first record() pass completes; gate for
    //   switching from live draw to skip-draw, and prerequisite for skip-measure.
    // isTouchDown: set on awaitFirstDown via a separate pointerInput on the foreground.
    //   Pre-warms live draw before detectHorizontalDragGestures slop, so a fresh snapshot is
    //   captured before the user's finger has moved enough for parallax to expose stale frames.
    var hasBackgroundSnapshot by remember { mutableStateOf(false) }
    var isTouchDown by remember { mutableStateOf(false) }

    // Invalidate snapshot when configuration changes that affect rendering. Rotation triggers
    // full recomposition anyway; uiMode (light/dark), fontScale, locales can change without
    // recreating the activity, leaving a visually stale snapshot until the next swipe.
    val configuration = LocalConfiguration.current
    if (freezeBackgroundWhileIdle) {
        LaunchedEffect(configuration.uiMode, configuration.fontScale, configuration.locales) {
            hasBackgroundSnapshot = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isResumed = true
                    hasBeenResumed = true
                }
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        val currentlyResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        isResumed = currentlyResumed
        if (currentlyResumed) hasBeenResumed = true
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isInteracting = isDragging || isNestedScrollDragging || isSwiping || isTouchDown

    // When true, foreground (assumed fully opaque per the freezeBackgroundWhileIdle invariant)
    // covers the entire screen and the background is invisible. Skip CPU draw (record), GPU
    // draw (drawLayer) and measure for the background subtree.
    // hasBackgroundSnapshot guards the very first frame: at least one record must have run so
    // that future swipe-parallax has a layer to replay if composition is dropped by lifecycle.
    val isFullyCoveredByForeground = freezeBackgroundWhileIdle && isResumed &&
        !isDismissed && !isBeingRemoved && !isInteracting && hasBackgroundSnapshot

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val offset = if (isDragging || isNestedScrollDragging) dragOffset else offsetX.value
                    val progress = (offset / screenWidth).coerceIn(0f, 1f)
                    translationX = -(screenWidth / 3f) * (1f - progress)
                }
        ) {
            if (isResumed && !isDismissed && !isBeingRemoved) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layout { measurable, constraints ->
                            if (isFullyCoveredByForeground) {
                                // Skip measure for the background subtree — children are
                                // invisible under the foreground, so we don't pay for measure
                                // invalidations from background state ticks.
                                // Returning full constraints as the layout size keeps parent
                                // placement stable; no children are placed, so onPlaced /
                                // onSizeChanged / onGloballyPositioned do not fire.
                                // Background composables MUST guard size-dependent state
                                // against zero size to remain idempotent across measure-skip
                                // cycles.
                                layout(constraints.maxWidth, constraints.maxHeight) {}
                            } else {
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    placeable.place(0, 0)
                                }
                            }
                        }
                        .drawWithContent {
                            if (isFullyCoveredByForeground) {
                                // Foreground fully covers the screen — skip CPU display-list
                                // issuance + GPU layer playback. Composition stays alive, but
                                // drawContent() is not invoked, eliminating per-frame work
                                // for an invisible subtree.
                                return@drawWithContent
                            }
                            backgroundLayer.record(
                                size = IntSize(size.width.toInt(), size.height.toInt()),
                            ) {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(backgroundLayer)
                            // Guarded write: only on first record() pass. Avoids scheduling
                            // a recomposition every draw when freezeBackgroundWhileIdle
                            // re-enters live draw and hasBackgroundSnapshot is already true.
                            if (freezeBackgroundWhileIdle && !hasBackgroundSnapshot) {
                                hasBackgroundSnapshot = true
                            }
                        }
                ) {
                    backgroundContent()
                }
            } else if (hasBeenResumed) {
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind { drawLayer(backgroundLayer) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // Pre-warm live draw on first touch (before detectHorizontalDragGestures
                    // classifies the gesture as a horizontal drag via slop). Gives Compose
                    // ~50–100 ms head start to refresh the snapshot before parallax translation
                    // begins to expose the background. Pointer events are not consumed, so
                    // detectHorizontalDragGestures still sees them.
                    if (freezeBackgroundWhileIdle) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                isTouchDown = true
                                try {
                                    do {
                                        val event = awaitPointerEvent()
                                    } while (event.changes.any { it.pressed })
                                } finally {
                                    isTouchDown = false
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .nestedScroll(nestedScrollConnection)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            val isImeVisible = ViewCompat.getRootWindowInsets(view)
                                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                            if (isImeVisible) {
                                swipeConsumedByKeyboard = true
                                runCatching { keyboardController?.hide() }
                            } else {
                                isDragging = true
                                dragOffset = offsetX.value
                                velocityTracker.resetTracking()
                            }
                        },
                        onDragEnd = {
                            if (swipeConsumedByKeyboard) {
                                swipeConsumedByKeyboard = false
                                return@detectHorizontalDragGestures
                            }
                            isDragging = false
                            val velocity = runCatching {
                                velocityTracker.calculateVelocity().x
                            }.getOrDefault(0f)
                            scope.launch {
                                offsetX.snapTo(dragOffset)
                                if (dragOffset > dismissThreshold || velocity > velocityDismissThreshold) {
                                    offsetX.animateTo(screenWidth, tween(200))
                                    isDismissed = true
                                    onDismiss()
                                } else {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                        ),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            if (swipeConsumedByKeyboard) {
                                swipeConsumedByKeyboard = false
                                return@detectHorizontalDragGestures
                            }
                            isDragging = false
                            scope.launch {
                                offsetX.snapTo(dragOffset)
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (swipeConsumedByKeyboard) {
                                change.consume()
                                return@detectHorizontalDragGestures
                            }
                            if (dragAmount > 0 || dragOffset > 0f) {
                                change.consume()
                                dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                velocityTracker.addPosition(
                                    change.uptimeMillis,
                                    Offset(dragOffset, 0f),
                                )
                            }
                        },
                    )
                }
                .graphicsLayer {
                    val offset = if (isDragging || isNestedScrollDragging) dragOffset else offsetX.value
                    val progress = (offset / screenWidth).coerceIn(0f, 1f)
                    translationX = offset
                    val clampedProgress = (progress / 0.5f).coerceAtMost(1f)
                    val scale = lerp(1f, 0.95f, clampedProgress)
                    scaleX = scale
                    scaleY = scale
                    alpha = lerp(1f, 0.8f, progress)
                    val cornerRadius = lerp(0f, 48f, clampedProgress)
                    if (cornerRadius > 0f) {
                        shape = RoundedCornerShape(cornerRadius.dp)
                        clip = true
                    } else {
                        clip = false
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (!isSwiping) {
                            foregroundLayer.record(
                                size = IntSize(size.width.toInt(), size.height.toInt()),
                            ) {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(foregroundLayer)
                            hasForegroundSnapshot = true
                        } else if (hasForegroundSnapshot) {
                            drawLayer(foregroundLayer)
                        }
                    }
            ) {
                CompositionLocalProvider(LocalSwipeToDismissActive provides isSwiping) {
                    foregroundContent()
                }
            }
        }
    }
}
