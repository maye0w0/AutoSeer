package com.autoseer.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.autoseer.core.RunPrefs
import com.autoseer.core.ScriptStore
import com.autoseer.core.SeerScript
import com.autoseer.ui.DelaySettingsActivity

/**
 * FGA-style floating control. Collapsed it is a small draggable dot ("AS") with a
 * one-line live status above it (what the sweep is doing right now) — small enough
 * to drag clear of any region the vision needs. Tapping the dot expands a panel:
 * live status, current progress (第 X 關｜已清 N 關), quick script switch, 起始關卡
 * stepper, a link to the global delay settings, Start/Stop, and the debug tools.
 */
class ControlOverlay(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    private val onCapture: () -> Unit = {},
    private val onProbe: () -> Unit = {},
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: FrameLayout? = null
    private lateinit var collapsed: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var capText: TextView      // status above the dot (collapsed)
    private lateinit var dotView: TextView      // the "AS" circle
    private lateinit var statusText: TextView   // status line (panel)
    private lateinit var progressText: TextView // 目前進度 (panel)
    private lateinit var scriptButton: Button   // cycles the active script
    private lateinit var stageValue: TextView   // 起始關卡 value
    private lateinit var runButton: Button

    private var running = false
    private lateinit var params: WindowManager.LayoutParams

    // ---- colors ----
    private val cPanel = Color.parseColor("#1F2226")
    private val cLine = Color.parseColor("#33383F")
    private val cInk = Color.parseColor("#F2F4F8")
    private val cSub = Color.parseColor("#AAB1BD")
    private val cField = Color.parseColor("#2A2F36")
    private val cAccent = Color.parseColor("#3F7BFF")
    private val cGood = Color.parseColor("#2EA36B")
    private val cStop = Color.parseColor("#E05555")
    private val cProgBg = Color.parseColor("#131C16")
    private val cProgInk = Color.parseColor("#BFE6CF")

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (root != null) return
        val container = FrameLayout(context)
        collapsed = buildCollapsed()
        panel = buildPanel()
        panel.visibility = View.GONE
        container.addView(collapsed)
        container.addView(panel)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(300)   // 左側中段，避開左上快速選單/回大廳判斷區
        }
        enableDrag(collapsed) { setExpanded(true) }   // 拖曳移動；未拖動＝展開
        windowManager.addView(container, params)
        root = container
    }

    // ---- collapsed ----
    private fun buildCollapsed(): LinearLayout {
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        capText = TextView(context).apply {
            text = "待命"
            setTextColor(cInk)
            textSize = 11f
            maxWidth = dp(210)
            background = rounded(Color.parseColor("#EA1F2226"), 8, cLine)
            setPadding(dp(9), dp(4), dp(9), dp(4))
        }
        dotView = TextView(context).apply {
            text = "AS"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cAccent) }
        }
        wrap.addView(capText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
        wrap.addView(dotView, LinearLayout.LayoutParams(dp(52), dp(52)))
        return wrap
    }

    // ---- expanded panel ----
    private fun buildPanel(): LinearLayout {
        val p = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cPanel, 14, cLine)
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        // header
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(context).apply {
            text = "AutoSeer"; setTextColor(cInk); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
        }
        val collapseBtn = makeBtn("▬", cField).apply { setOnClickListener { setExpanded(false) } }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(collapseBtn, LinearLayout.LayoutParams(dp(40), dp(34)))
        enableDrag(header, null)

        statusText = TextView(context).apply {
            text = "狀態：待命"; setTextColor(cSub); textSize = 12f
            background = rounded(Color.parseColor("#171A1E"), 8); setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        progressText = TextView(context).apply {
            text = "目前進度：第 1 關｜已清 0 關"; setTextColor(cProgInk); textSize = 12f
            background = rounded(cProgBg, 8); setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        scriptButton = makeBtn("腳本：--", cField).apply {
            setOnClickListener { cycleScript() }
        }
        // 起始關卡 stepper
        val stageRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val stageLabel = TextView(context).apply { text = "起始關卡"; setTextColor(cSub); textSize = 12f }
        val minus = makeBtn("－", cField).apply { setOnClickListener { bumpStage(-1) } }
        stageValue = TextView(context).apply {
            text = RunPrefs.startStage(context).toString(); setTextColor(cInk); textSize = 14f; gravity = Gravity.CENTER
            background = rounded(Color.parseColor("#171A1E"), 8)
        }
        val plus = makeBtn("＋", cField).apply { setOnClickListener { bumpStage(1) } }
        stageRow.addView(stageLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        stageRow.addView(minus, LinearLayout.LayoutParams(dp(38), dp(34)))
        stageRow.addView(stageValue, LinearLayout.LayoutParams(dp(48), dp(34)).apply { leftMargin = dp(4); rightMargin = dp(4) })
        stageRow.addView(plus, LinearLayout.LayoutParams(dp(38), dp(34)))

        val delayBtn = makeBtn("延遲設定…（全域）", cField).apply {
            textSize = 12f
            setOnClickListener {
                runCatching {
                    context.startActivity(
                        Intent(context, DelaySettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        runButton = makeBtn("開始", cGood).apply {
            textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { if (running) onStop() else onStart() }
        }
        // debug tools
        val tools = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cap = makeBtn("存畫面", cField).apply { textSize = 12f; setOnClickListener { onCapture() } }
        val probe = makeBtn("測試偵測", cField).apply { textSize = 12f; setOnClickListener { onProbe() } }
        tools.addView(cap, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(4) })
        tools.addView(probe, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(4) })

        val w = dp(250)
        fun add(v: View, topMargin: Int = dp(9)) =
            p.addView(v, LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT).apply { this.topMargin = topMargin })
        p.addView(header, LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT))
        add(statusText); add(progressText); add(scriptButton); add(stageRow); add(delayBtn); add(runButton); add(tools)
        return p
    }

    private fun makeBtn(text: String, bg: Int): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        setTextColor(cInk)
        textSize = 13f
        background = rounded(bg, 9, if (bg == cField) cLine else null)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        minWidth = 0; minHeight = 0
    }

    // ---- actions ----
    private fun setExpanded(expanded: Boolean) {
        if (root == null) return
        if (expanded) { refreshScriptButton(); stageValue.text = RunPrefs.startStage(context).toString() }
        collapsed.visibility = if (expanded) View.GONE else View.VISIBLE
        panel.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    private fun cycleScript() {
        val list = ScriptStore.list(context, SeerScript.CATEGORY_SEER_FACTOR)
        if (list.isEmpty()) { scriptButton.text = "腳本：（無，請先新增）"; return }
        val sel = ScriptStore.selectedId(context)
        val i = list.indexOfFirst { it.id == sel }.let { if (it < 0) 0 else it }
        val next = list[(i + 1) % list.size]
        ScriptStore.setSelected(context, next.id)
        refreshScriptButton()
    }

    private fun refreshScriptButton() {
        val list = ScriptStore.list(context, SeerScript.CATEGORY_SEER_FACTOR)
        val sel = ScriptStore.selectedId(context)
        val name = list.firstOrNull { it.id == sel }?.displayName
            ?: list.firstOrNull()?.displayName ?: "（無，請先新增）"
        scriptButton.text = "腳本：$name（點擊切換）"
    }

    private fun bumpStage(delta: Int) {
        val v = (RunPrefs.startStage(context) + delta).coerceAtLeast(1)
        RunPrefs.save(
            ctx = context,
            startStage = v,
            defaultSlot = RunPrefs.defaultSlot(context),
            healBeforeBattle = RunPrefs.healBeforeBattle(context),
            backToLobbyOnRetryExhausted = RunPrefs.backToLobbyOnRetryExhausted(context),
        )
        stageValue.text = v.toString()
    }

    /** Reflect the runner state. Safe to call from any thread. */
    fun setRunning(isRunning: Boolean) {
        dotView.post {
            running = isRunning
            runButton.text = if (isRunning) "停止" else "開始"
            runButton.background = rounded(if (isRunning) cStop else cGood, 9)
            dotView.text = if (isRunning) "⏸" else "AS"
            (dotView.background as? GradientDrawable)?.setColor(if (isRunning) cGood else cAccent)
            if (!isRunning) { capText.text = "待命"; statusText.text = "狀態：待命" }
        }
    }

    fun setStatus(text: String) {
        capText.post { capText.text = text }
        statusText.post { statusText.text = text }
    }

    fun setProgress(stageNo: Int, cleared: Int) {
        progressText.post { progressText.text = "目前進度：第 $stageNo 關｜已清 $cleared 關" }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag(handle: View, onTap: (() -> Unit)?) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY; moved = false
                    true   // 消費 DOWN 以確保收到後續 MOVE/UP（拖曳＋點擊展開）
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt(); val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(6)) moved = true
                    params.x = initialX + dx; params.y = initialY + dy
                    root?.let { windowManager.updateViewLayout(it, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && onTap != null) onTap()
                    false
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
