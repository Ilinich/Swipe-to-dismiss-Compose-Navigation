# Swipe-to-Dismiss for Jetpack Navigation 3

iOS-style swipe-to-dismiss navigation for Jetpack Compose Navigation 3.

<p align="center">
  <img src="swipe.gif" width="300" alt="Swipe-to-dismiss demo" />
</p>

## What it demonstrates

- Horizontal swipe gesture to navigate back (drag from left edge or flick right)
- Nested scroll integration — scroll a `LazyColumn` to the top, keep pulling right, and the dismiss gesture kicks in automatically. A horizontal child (`LazyRow`, `HorizontalPager`) keeps its own gesture until it actually reaches its edge
- Visual feedback during swipe: translation, scale (1.0 → 0.95), alpha fade, progressive corner rounding
- Background parallax effect on the previous screen
- `GraphicsLayer` caching to avoid `movableContentOf` conflicts and freeze position-aware effects (e.g. Haze blur) during the gesture
- Keyboard-aware: first swipe hides the IME, second one starts the dismiss
- Velocity-based dismiss — a fast flick (≥ 1500 dp/s) dismisses regardless of distance
- **Background freeze (opt-in)**: when a foreground is fully opaque and idle, the background's CPU draw and GPU draw are skipped while its composition stays alive. Eliminates per-frame work for the hidden screen without tearing down its `LaunchedEffect` / `DisposableEffect` (which would otherwise misbehave on every foreground touch — e.g. clearing focus and dismissing the IME). Measure and layout are deliberately **not** skipped — see below.

## One-line API

```kotlin
swipeToDismissHorizontalEntry<DetailKey> { key -> DetailScreen(key) }

// Opt-in background freeze (skip draw for the hidden screen):
swipeToDismissHorizontalEntry<DetailKey>(freezeBackgroundWhileIdle = true) { key ->
    DetailScreen(key)
}
```

## Architecture

```
swipe/
├── SwipeToDismissSceneStrategy.kt   — SceneStrategy that intercepts marked entries
├── SwipeToDismissScene.kt           — Scene rendering two entries (background + foreground)
├── SwipeToDismissLayout.kt          — Core composable: gesture, animation, GraphicsLayer caching
├── SwipeToDismissEntry.kt           — DSL extension for one-line entry registration
├── FreezeDuringSwipeToDismiss.kt    — Modifier to freeze position-aware effects during swipe
├── NestedScrollSwipeArbiter.kt      — Decides whether a nested-scroll gesture is a dismiss
└── FreezeBackgroundWhileIdle.kt     — Metadata helper for the opt-in background freeze flag
```

## Background freeze (`freezeBackgroundWhileIdle`)

When the foreground entry is fully opaque and covers the screen, the background entry is doing useful composition work but its **draw is invisible**. Setting `freezeBackgroundWhileIdle = true` on a `swipeToDismissHorizontalEntry` skips:

| Phase | What is skipped while RESUMED + idle + fully covered |
|---|---|
| CPU draw (`record`) | display-list issuance for the background subtree |
| GPU draw (`drawLayer`) | layer playback in the backbuffer |

**Measure and layout are deliberately not skipped.** An earlier version of this
project also returned a size without measuring the children. It is a tempting
trade and it is a bad one: taking a subtree out of the placed state costs far
more to restore than the measure work it avoids. Measured on a production app,
skipping measure saved ~0.16 ms per idle frame and cost ~8.1 ms of re-placement
on the very next touch — so every finger-down on a swipe screen dropped a frame.
Freezing draw alone is what actually pays, and once the subtree is never
unplaced, nothing invalidates its layout in the first place.

What is **not** dropped: composition. `LaunchedEffect`, `DisposableEffect` and state holders of the background remain alive across foreground touches. This avoids a class of regressions where re-creating the background subtree on every `awaitFirstDown` re-fires window-scoped side effects (e.g. `focusManager.clearFocus()`) and breaks foreground UX (input focus, IME, snackbar host).

Invariants:
- Foreground must be opaque and full-screen — semi-transparent foregrounds will see a black background while idle.
- Only content whose visible output is produced outside the recorded layer, or which advances from its own draw callback, is affected by the draw freeze. A plain Compose animation in the background is not: composition and the frame clock keep running, `record` resumes on `awaitFirstDown`, and the frame that exposes the background already shows current content.

The demo in this repo uses `freezeBackgroundWhileIdle = true` on `DetailKey`, with a `LaunchedEffect { focusManager.clearFocus() }` in `HomeScreen` and a `TextField` in `DetailScreen`. With the always-compose implementation the keyboard stays open between taps; a legacy drop-from-composition freeze would dismiss it on every tap.

## How it works

1. Mark a navigation entry with `SwipeToDismissSceneStrategy.enabled()` metadata (or use the `swipeToDismissHorizontalEntry` DSL)
2. `SwipeToDismissSceneStrategy` intercepts the entry and creates a `SwipeToDismissScene` with two layers: the previous screen (background) and the current screen (foreground)
3. `SwipeToDismissLayout` handles the drag gesture, animates the foreground off-screen on dismiss, and calls `onBack` to pop the back stack

## Setup

```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLast() },
    sceneStrategy = SwipeToDismissSceneStrategy() then SinglePaneSceneStrategy(),
    entryProvider = entryProvider {
        entry<HomeKey> { HomeScreen() }
        swipeToDismissHorizontalEntry<DetailKey> { key -> DetailScreen(key) }
    },
)
```

## Dependencies

- `androidx.navigation3:navigation3-runtime:1.0.0`
- `androidx.navigation3:navigation3-ui:1.0.0`
- Jetpack Compose (UI, Animation, Foundation, Material 3)
