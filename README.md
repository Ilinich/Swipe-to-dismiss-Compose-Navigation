# Swipe-to-Dismiss for Jetpack Navigation 3

iOS-style swipe-to-dismiss navigation for Jetpack Compose Navigation 3.

## What it demonstrates

- Horizontal swipe gesture to navigate back (drag from left edge or flick right)
- Nested scroll integration — scroll a `LazyColumn` to the top, keep pulling right, and the dismiss gesture kicks in automatically
- Visual feedback during swipe: translation, scale (1.0 → 0.95), alpha fade, progressive corner rounding
- Background parallax effect on the previous screen
- `GraphicsLayer` caching to avoid `movableContentOf` conflicts and freeze position-aware effects (e.g. Haze blur) during the gesture
- Keyboard-aware: first swipe hides the IME, second one starts the dismiss
- Velocity-based dismiss — a fast flick (≥ 1500 dp/s) dismisses regardless of distance

## One-line API

```kotlin
swipeToDismissHorizontalEntry<DetailKey> { key -> DetailScreen(key) }
```

## Architecture

```
swipe/
├── SwipeToDismissSceneStrategy.kt   — SceneStrategy that intercepts marked entries
├── SwipeToDismissScene.kt           — Scene rendering two entries (background + foreground)
├── SwipeToDismissLayout.kt          — Core composable: gesture, animation, GraphicsLayer caching
├── SwipeToDismissEntry.kt           — DSL extension for one-line entry registration
└── FreezeDuringSwipeToDismiss.kt    — Modifier to freeze position-aware effects during swipe
```

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
