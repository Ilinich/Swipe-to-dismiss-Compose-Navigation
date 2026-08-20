package com.begoml.composenav.swipetodismiss.swipe

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Signals whether a swipe-to-dismiss gesture is currently in progress.
 *
 * Provided by [SwipeToDismissLayout] via [CompositionLocalProvider].
 * Consumers (e.g. [freezeDuringSwipeToDismiss]) read this to freeze their draw output
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
 *    into a [GraphicsLayer] while the scene is RESUMED. Once a dismiss starts or the scene
 *    transitions to STARTED (pop), composition of [backgroundContent] stops and the cached
 *    snapshot is replayed. This avoids `movableContentOf` conflicts with `SinglePaneScene`.
 *    When [freezeBackgroundWhileIdle] is `true`, the background's CPU draw (`record`) and GPU
 *    draw (`drawLayer`) are skipped during RESUMED-idle while a snapshot already exists.
 *    Measure and placement are never skipped: taking a subtree out of the placed state costs
 *    far more to undo than the measure work it saves, and the frame that first exposes the
 *    background must not be the frame that re-places it.
 *    Background composition stays alive — its `LaunchedEffect` /
 *    `DisposableEffect` are not disposed, which prevents window-scoped side effects (focus,
 *    IME) from being torn down on every touch in the foreground. On first touch
 *    (`awaitFirstDown`) draw resumes so the layer is refreshed before parallax exposes the
 *    background.
 *
 * 2. **Foreground layer** — the current screen. The content is recorded into a [GraphicsLayer]
 *    snapshot exactly once per accepted gesture (and after invalidation events like
 *    configuration changes). While idle, [drawContent] is called directly, avoiding the
 *    double-draw cost of recording a layer that no one will replay. Once a gesture is
 *    accepted, only the cached snapshot is drawn (no live recomposition of draw), which
 *    prevents Haze and other position-aware effects from recalculating against the moving
 *    coordinate space. Recording takes precedence over replaying, so a snapshot invalidated
 *    in the same frame the gesture is accepted is refreshed rather than replayed stale for
 *    the whole swipe.
 *
 * ## Gesture handling
 *
 * - Horizontal drag is detected via [awaitHorizontalTouchSlopOrCancellation] inside an
 *   [awaitEachGesture] loop, which gives access to the down-event x-position for edge-gating.
 * - During drag, raw float offset is used (no coroutine-per-event) for performance.
 * - On drag end: if offset exceeds the configured distance threshold (per [SwipeSensitivity],
 *   default 35% of screen width), the foreground animates off-screen and [onDismiss] is called;
 *   otherwise it springs back to origin.
 * - Visual transform during swipe: translation, progressive corner rounding (up to the device
 *   physical corner radius, or 5% of screen-width fallback).
 * - Velocity-based dismiss: a fast flick (≥ the configured [SwipeSensitivity.velocityDp])
 *   dismisses regardless of distance.
 * - Background parallax: the previous screen shifts from −screenWidth/3 to 0 during swipe.
 * - Edge-gate: when [edgeWidthDp] is set, only down-events with `x <= edgePx` initiate the
 *   gesture; touches outside the zone propagate to children. [swipeFromAnywhere] disables
 *   this gate for screens like media gallery / image preview.
 *
 * ## Settle animation contract
 *
 * Both touch-drag and nested-scroll-fling end paths route through one [Job] (`animationJob`),
 * which is cancelled before launching a new settle animation. This prevents races where a
 * spring-back coroutine continues to mutate `offsetX` while the user has already started a
 * new gesture.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
internal fun SwipeToDismissLayout(
    onDismiss: () -> Unit,
    backgroundContent: @Composable () -> Unit,
    foregroundContent: @Composable () -> Unit,
    freezeBackgroundWhileIdle: Boolean,
    edgeWidthDp: Dp? = null,
    swipeFromAnywhere: Boolean = false,
    sensitivity: SwipeSensitivity = SwipeSensitivity.Default,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val screenWidth = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isPredictiveBackInProgress by rememberPredictiveBackInProgress()
    val view = LocalView.current
    val insets = view.rootWindowInsets
    val deviceCornerRadiusDp = remember(insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                ?.radius
                ?.let { with(density) { it.toDp().value } }
        } else {
            null
        }
    }

    // Maximum corner radius applied at full progress.
    // Prefer the device's physical corner radius (matches OS aesthetic). Fallback to 5% of
    // screen width which approximates the iOS standard on devices without the API.
    val maxCornerRadiusDp = remember(deviceCornerRadiusDp, screenWidth, density) {
        deviceCornerRadiusDp ?: with(density) { (screenWidth * 0.05f).toDp().value }
    }

    // Pre-create discrete RoundedCornerShape instances so the graphicsLayer block
    // does not allocate a new shape on every draw frame. Four levels look smooth at 60 Hz.
    val shape25 = remember(maxCornerRadiusDp) { RoundedCornerShape((maxCornerRadiusDp * 0.25f).dp) }
    val shape50 = remember(maxCornerRadiusDp) { RoundedCornerShape((maxCornerRadiusDp * 0.5f).dp) }
    val shape75 = remember(maxCornerRadiusDp) { RoundedCornerShape((maxCornerRadiusDp * 0.75f).dp) }
    val shapeMax = remember(maxCornerRadiusDp) { RoundedCornerShape(maxCornerRadiusDp.dp) }

    val edgePx = remember(edgeWidthDp, density) {
        edgeWidthDp?.let { with(density) { it.toPx() } }
    }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissThreshold = screenWidth * sensitivity.distanceFraction
    val velocityDismissThreshold = with(density) { sensitivity.velocityDp.dp.toPx() }
    val velocityTracker = remember { VelocityTracker() }

    // Drag state: use plain float during drag to avoid coroutine-per-event,
    // sync to Animatable only when animation is needed.
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var swipeConsumedByKeyboard by remember { mutableStateOf(false) }
    var isNestedScrollDragging by remember { mutableStateOf(false) }
    var nestedScrollConsumedByKeyboard by remember { mutableStateOf(false) }
    // Marks the foreground snapshot as in need of refresh. Set synchronously by whichever
    // handler accepts a gesture, and on configuration change.
    var foregroundSnapshotInvalid by remember { mutableStateOf(false) }
    var isDismissed by remember { mutableStateOf(false) }

    // Single owning Job for any settle animation (spring-back / dismiss-throw).
    // Cancelled before launching a new one to prevent races on rapid touch-up/down sequences.
    var animationJob by remember { mutableStateOf<Job?>(null) }

    fun launchSettleAnimation(targetOffset: Float, velocity: Float) {
        animationJob?.cancel()
        animationJob = scope.launch {
            offsetX.snapTo(targetOffset)
            if (targetOffset > dismissThreshold || velocity > velocityDismissThreshold) {
                offsetX.animateTo(screenWidth, tween(SETTLE_TWEEN_MS))
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
    }

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
                    foregroundSnapshotInvalid = true
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
                val capturedOffset = dragOffset
                dragOffset = 0f
                launchSettleAnimation(targetOffset = capturedOffset, velocity = velocity)
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(false) }
    var hasBeenResumed by remember { mutableStateOf(false) }
    val backgroundLayer = rememberGraphicsLayer()
    val foregroundLayer = rememberGraphicsLayer()

    // Background freeze state (active only when freezeBackgroundWhileIdle = true).
    // hasBackgroundSnapshot: flips true once the first record() pass completes; gate for
    // switching from Live to Snapshot mode.
    // isTouchDown: set on awaitFirstDown via a separate pointerInput on the foreground.
    // Pre-warms Live mode before the touch-slop classifies the gesture as a horizontal drag,
    // so a fresh snapshot is captured before the user's finger has moved enough for parallax
    // to expose stale frames.
    var hasBackgroundSnapshot by remember { mutableStateOf(false) }
    var isTouchDown by remember { mutableStateOf(false) }

    // Invalidate snapshot when configuration changes that affect rendering. Rotation triggers
    // full recomposition anyway; uiMode (light/dark), fontScale, locales can change without
    // recreating the activity, leaving a visually stale snapshot until the next swipe.
    val configuration = LocalConfiguration.current
    var isInitialConfiguration by remember { mutableStateOf(true) }
    LaunchedEffect(configuration.uiMode, configuration.fontScale, configuration.locales) {
        if (isInitialConfiguration) {
            isInitialConfiguration = false
            return@LaunchedEffect
        }
        if (freezeBackgroundWhileIdle) hasBackgroundSnapshot = false
        foregroundSnapshotInvalid = true
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

    // Background composition is gated only by lifecycle (push/pop transitions).
    // Composition stays alive during RESUMED-idle so that LaunchedEffect / DisposableEffect of
    // the background don't tear down on every touch in the foreground (which would fire
    // window-scoped side effects like focusManager.clearFocus, breaking IME on the foreground).
    //
    // Draw is gated separately via isFullyCoveredByForeground below.
    val shouldComposeBackground = isResumed && !isDismissed && !isBeingRemoved

    val isInteracting = isDragging || isNestedScrollDragging || isSwiping || isTouchDown

    // When true, foreground (assumed fully opaque per the freezeBackgroundWhileIdle invariant)
    // covers the entire screen and the background is invisible. Skip both CPU draw (record) and
    // GPU draw (drawLayer) — background subtree's drawContent() is not invoked.
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
                    translationX = -(screenWidth / BACKGROUND_PARALLAX_DIVISOR) * (1f - progress)
                }
        ) {
            if (shouldComposeBackground) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            // During predictive back NavDisplay reveals the real previous scene
                            // underneath; drawing this copy too would show the same screen twice,
                            // travelling with the outgoing foreground.
                            if (isPredictiveBackInProgress) return@drawWithContent
                            if (isFullyCoveredByForeground) {
                                // Foreground fully covers the screen — skip CPU display-list
                                // issuance + GPU layer playback. Composition stays alive, but
                                // drawContent() is not invoked, eliminating per-frame work for
                                // an invisible subtree.
                                return@drawWithContent
                            }
                            SwipeTrace.section("SwipeBg.record") {
                                backgroundLayer.record(
                                    size = IntSize(size.width.toInt(), size.height.toInt()),
                                ) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            drawLayer(backgroundLayer)
                            // Guarded write: only on first record() pass. Avoids scheduling
                            // a recomposition every draw when freezeBackgroundWhileIdle
                            // re-enters Live mode and hasBackgroundSnapshot is already true.
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
                        .drawBehind { if (!isPredictiveBackInProgress) drawLayer(backgroundLayer) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Lightweight observer that just tracks "is a finger down on the screen".
                    // Pre-warms the background draw pipeline before slop classifies the gesture
                    // as a horizontal drag — pointer events are not consumed.
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
                .nestedScroll(nestedScrollConnection)
                .pointerInput(edgePx, swipeFromAnywhere) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Edge gate: if a width is configured and the gesture started outside
                        // the left-edge zone, do not claim the gesture. Children (scrollables)
                        // continue to receive events normally.
                        val effectiveEdge = when {
                            swipeFromAnywhere -> Float.MAX_VALUE
                            edgePx != null -> edgePx
                            else -> Float.MAX_VALUE
                        }
                        if (down.position.x > effectiveEdge) return@awaitEachGesture
                        // The system predictive-back gesture already seeks this scene through
                        // NavDisplay; claiming the same touch stream here would transform the
                        // screen twice (NavDisplay offset + dragOffset).
                        if (isPredictiveBackInProgress) return@awaitEachGesture

                        var slopOver = 0f
                        val drag: PointerInputChange = awaitHorizontalTouchSlopOrCancellation(
                            down.id,
                        ) { change, over ->
                            if (over > 0f) {
                                change.consume()
                                slopOver = over
                            }
                        } ?: return@awaitEachGesture

                        // onDragStart equivalent
                        val isImeVisible = ViewCompat.getRootWindowInsets(view)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                        if (isImeVisible) {
                            swipeConsumedByKeyboard = true
                            runCatching { keyboardController?.hide() }
                        } else {
                            animationJob?.cancel()
                            isDragging = true
                            foregroundSnapshotInvalid = true
                            dragOffset = (offsetX.value + slopOver).coerceAtLeast(0f)
                            velocityTracker.resetTracking()
                            velocityTracker.addPosition(drag.uptimeMillis, drag.position)
                        }

                        SwipeTrace.beginGesture(down.id.value.toInt())
                        try {
                            // Drain pointer events until release.
                            val completed = horizontalDrag(drag.id) { change ->
                                if (swipeConsumedByKeyboard) {
                                    change.consume()
                                    return@horizontalDrag
                                }
                                val dx = change.positionChange().x
                                if (dx > 0f || dragOffset > 0f) {
                                    change.consume()
                                    dragOffset = (dragOffset + dx).coerceAtLeast(0f)
                                    velocityTracker.addPosition(
                                        change.uptimeMillis,
                                        Offset(dragOffset, 0f),
                                    )
                                }
                            }

                            if (swipeConsumedByKeyboard) {
                                swipeConsumedByKeyboard = false
                                return@awaitEachGesture
                            }
                            isDragging = false

                            if (!completed) {
                                // Cancelled mid-gesture — spring back to origin.
                                val capturedOffset = dragOffset
                                launchSettleAnimation(targetOffset = capturedOffset, velocity = 0f)
                                return@awaitEachGesture
                            }

                            val velocity = runCatching {
                                velocityTracker.calculateVelocity().x
                            }.getOrDefault(0f)
                            launchSettleAnimation(targetOffset = dragOffset, velocity = velocity)
                        } finally {
                            SwipeTrace.endGesture(down.id.value.toInt())
                        }
                    }
                }
                .graphicsLayer {
                    val offset = if (isDragging || isNestedScrollDragging) dragOffset else offsetX.value
                    val progress = (offset / screenWidth).coerceIn(0f, 1f)
                    translationX = offset
                    if (progress > 0.05f) {
                        // Pick a pre-created shape based on progress — zero allocations on hot path.
                        // On devices with a known physical corner radius we jump to the device
                        // value as soon as the gesture begins (matches OS aesthetic). Otherwise
                        // we step through 25/50/75/100% of the fallback radius.
                        shape = if (deviceCornerRadiusDp != null) {
                            shapeMax
                        } else {
                            when {
                                progress >= CORNER_PROGRESS_FULL -> shapeMax
                                progress >= CORNER_PROGRESS_75 -> shape75
                                progress >= CORNER_PROGRESS_50 -> shape50
                                else -> shape25
                            }
                        }
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
                        val needsRecord = foregroundSnapshotInvalid
                        when {
                            needsRecord -> {
                                SwipeTrace.section("SwipeFg.record") {
                                    foregroundLayer.record(
                                        size = IntSize(size.width.toInt(), size.height.toInt()),
                                    ) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                                drawLayer(foregroundLayer)
                                foregroundSnapshotInvalid = false
                            }
                            isSwiping -> drawLayer(foregroundLayer)
                            else -> drawContent()
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

private const val SETTLE_TWEEN_MS = 200
private const val BACKGROUND_PARALLAX_DIVISOR = 3f
private const val CORNER_PROGRESS_FULL = 0.5f
private const val CORNER_PROGRESS_75 = 0.35f
private const val CORNER_PROGRESS_50 = 0.20f
