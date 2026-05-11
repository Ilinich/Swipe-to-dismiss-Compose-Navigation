package com.begoml.composenav.swipetodismiss.swipe

/**
 * Opt-in flag for skipping the background entry's draw work while a swipe-to-dismiss
 * foreground is on top, idle (no swipe in progress) and fully covers the screen.
 *
 * Default for [swipeToDismissHorizontalEntry] is **enabled** (`true`). The vast majority of
 * Compose-only screens have fully opaque backgrounds and benefit from skipping the per-frame
 * draw of the invisible background.
 *
 * When enabled on a navigation entry, [SwipeToDismissLayout] will:
 * 1. Compose the previous (background) entry as usual and capture a
 *    [GraphicsLayer][androidx.compose.ui.graphics.layer.GraphicsLayer] snapshot during the
 *    first RESUMED draw.
 * 2. Skip both CPU draw (display-list `record`) and GPU draw (`drawLayer`) for the background
 *    while the foreground is RESUMED, idle, and a snapshot already exists. Background
 *    composition stays alive — `LaunchedEffect` / `DisposableEffect` are not torn down, so
 *    window-scoped side effects in the background (focus, IME, snackbar host) remain stable
 *    across foreground touches.
 * 3. Resume draw on the first touch (`awaitFirstDown`) so the layer is refreshed before the
 *    parallax translation begins to expose the background.
 *
 * ## Set `freezeBackgroundWhileIdle = false` when:
 *
 *  - **Background contains an external surface**: `SurfaceView`, `TextureView`,
 *    `GLSurfaceView`, `WebView`, camera preview, video player, OpenGL renderer.
 *    Their content lives on a separate hardware plane (SurfaceFlinger) and is **not**
 *    captured by the Compose snapshot. During swipe-parallax the cached snapshot will draw
 *    a transparent hole while the live surface continues to render at the original position
 *    — visually broken.
 *  - **Foreground is not fully opaque** (semi-transparent root background, scrim overlays
 *    used as full-screen). Skipping background draw will reveal black/empty pixels through
 *    the foreground while idle.
 *  - **Background must show fresh data immediately on return** (live counter, video preview,
 *    chronometer). The cached snapshot can lag behind by up to ~50–100 ms before the awaitFirstDown
 *    refresh runs.
 */
internal const val FREEZE_BACKGROUND_WHILE_IDLE = "freezeBackgroundWhileIdle"

/**
 * Helper for opting a [swipeToDismissHorizontalEntry]-style entry into background freeze.
 * Mirrors the [SwipeToDismissSceneStrategy.enabled] pattern.
 */
object FreezeBackgroundWhileIdle {

    /**
     * Returns entry metadata that opts the annotated navigation entry into background-freeze
     * while it is on top and idle.
     */
    fun enabled(): Map<String, Any> = mapOf(FREEZE_BACKGROUND_WHILE_IDLE to true)

    /**
     * Reads the opt-in flag from entry metadata. Returns `false` (safe default) when the key
     * is absent or holds a non-boolean value.
     */
    fun isEnabledIn(metadata: Map<String, Any?>): Boolean =
        metadata[FREEZE_BACKGROUND_WHILE_IDLE] as? Boolean == true
}
