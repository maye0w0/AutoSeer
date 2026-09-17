package com.autoseer.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A small draggable floating control bar shown over the game: a status line and
 * a Start/Stop button. Uses TYPE_APPLICATION_OVERLAY (requires the "display
 * over other apps" permission).
 */
class ControlOverlay(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private var root: View? = null
    private var running = false

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (root != null) return

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC202124"))
            setPadding(24, 16, 24, 16)
        }
        statusText = TextView(context).apply {
            text = "AutoSeer 待命"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        toggleButton = Button(context).apply {
            text = "開始"
            setOnClickListener { toggle() }
        }
        container.addView(statusText)
        container.addView(toggleButton)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }

        enableDrag(container, params)
        windowManager.addView(container, params)
        root = container
    }

    private fun toggle() {
        if (running) onStop() else onStart()
    }

    /** Reflect the runner state in the overlay. Safe to call from any thread. */
    fun setRunning(isRunning: Boolean) {
        toggleButton.post {
            running = isRunning
            toggleButton.text = if (isRunning) "停止" else "開始"
            statusText.text = if (isRunning) "AutoSeer 執行中" else "AutoSeer 待命"
        }
    }

    fun setStatus(text: String) {
        statusText.post { statusText.text = text }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    fun hide() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
    }
}
