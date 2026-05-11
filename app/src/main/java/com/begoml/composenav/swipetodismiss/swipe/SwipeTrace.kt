package com.begoml.composenav.swipetodismiss.swipe

import android.os.Trace

/**
 * Lightweight trace markers around hot paths of [SwipeToDismissLayout].
 *
 * Uses [android.os.Trace] which is a no-op when systrace is not actively recording.
 * Cost on a typical frame is negligible (~tens of nanoseconds per begin/end pair).
 * Sections appear in Perfetto / Android Studio CPU Profiler as named slices.
 */
internal object SwipeTrace {

    private const val GESTURE_TAG = "SwipeGesture"

    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    fun beginGesture(id: Int) {
        Trace.beginAsyncSection(GESTURE_TAG, id)
    }

    fun endGesture(id: Int) {
        Trace.endAsyncSection(GESTURE_TAG, id)
    }
}
