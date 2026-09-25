package com.autoseer.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.autoseer.libautomata.IGestureService
import com.autoseer.libautomata.Location

/**
 * Injects taps and swipes via the Accessibility gesture API (no root needed).
 * Exposes itself as a singleton so the running script can reach it. Coordinates
 * received here are in device pixels.
 */
class GestureAccessibilityService : AccessibilityService(), IGestureService {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "無障礙手勢服務已連線")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* unused */ }
    override fun onInterrupt() { /* unused */ }

    override fun click(location: Location, durationMs: Long) {
        val path = Path().apply { moveTo(location.x.toFloat(), location.y.toFloat()) }
        dispatch(path, 0, durationMs.coerceAtLeast(1))
    }

    override fun swipe(from: Location, to: Location, durationMs: Long) {
        val path = Path().apply {
            moveTo(from.x.toFloat(), from.y.toFloat())
            lineTo(to.x.toFloat(), to.y.toFloat())
        }
        dispatch(path, 0, durationMs.coerceAtLeast(1))
    }

    /**
     * Fling-free drag: move from→to, then hold stationary for [holdMs] so the
     * platform releases with ~zero velocity (no momentum fling). Implemented by
     * chaining a stationary continuation stroke onto the move stroke.
     */
    override fun dragSteady(from: Location, to: Location, durationMs: Long, holdMs: Long) {
        val moveDur = durationMs.coerceAtLeast(1)
        val hold = holdMs.coerceAtLeast(1)
        val move = Path().apply {
            moveTo(from.x.toFloat(), from.y.toFloat())
            lineTo(to.x.toFloat(), to.y.toFloat())
        }
        val holdPath = Path().apply {
            moveTo(to.x.toFloat(), to.y.toFloat())
            lineTo(to.x.toFloat(), to.y.toFloat() + 1f) // 1px so the stroke isn't degenerate
        }
        val s1 = GestureDescription.StrokeDescription(move, 0, moveDur, true)
        val s2 = s1.continueStroke(holdPath, moveDur, hold, false)
        val gesture = GestureDescription.Builder().addStroke(s1).addStroke(s2).build()
        mainHandler.post { dispatchGesture(gesture, null, null) }
    }

    private fun dispatch(path: Path, startMs: Long, durationMs: Long) {
        val stroke = GestureDescription.StrokeDescription(path, startMs, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        mainHandler.post { dispatchGesture(gesture, null, null) }
    }

    companion object {
        private const val TAG = "AutoSeer"

        @Volatile
        var instance: GestureAccessibilityService? = null
            private set

        val isConnected: Boolean get() = instance != null
    }
}
