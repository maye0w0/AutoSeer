package com.autoseer.ui

import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import com.autoseer.core.FactorLibrary
import com.autoseer.core.FactorPlanPrefs
import com.autoseer.core.PetImagePrefs
import com.autoseer.core.SafImage
import com.autoseer.core.SafStore

/**
 * 因子關卡排序 — two layers with native drag-and-drop:
 *
 *  - 頂區「運行排序」: the ordered subset that actually runs (left→right = fight order).
 *  - 底區「因子庫」: every captured card (file-name order, browse-only per spec).
 *
 * Long-press a card to drag (a shrunken shadow follows the finger):
 *  - 因子庫 → 運行排序 : add, inserted at the drop position (an accent bar previews it).
 *  - within 運行排序    : move / re-insert at a new position.
 *  - 運行排序 → 因子庫  : remove from the run order.
 *  - any card → the red 刪除 box on the right : permanently delete the file from the
 *    SAF folder (confirmed first — irreversible).
 *
 * The insert index is decided by the drop point vs each card's horizontal centre
 * (right of a card's centre ⇒ after it). Columns are adaptive, capped at 9.
 *
 * This screen only edits the plan ([FactorPlanPrefs]) and, on explicit confirm, the
 * library folder. The sweep/navigation/battle modules are untouched.
 */
class FactorListActivity : AppCompatActivity() {

    private val cBg = Color.parseColor("#15181C")
    private val cPanel = Color.parseColor("#1F2226")
    private val cField = Color.parseColor("#2A2F36")
    private val cInk = Color.parseColor("#F2F4F8")
    private val cSub = Color.parseColor("#AAB1BD")
    private val cAccent = Color.parseColor("#3F7BFF")
    private val cGood = Color.parseColor("#2EA36B")
    private val cCell = Color.parseColor("#0B0F14")
    private val cDanger = Color.parseColor("#C0392B")

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Ordered selected display-names (the fight order). */
    private val order = ArrayList<String>()
    /** Every image in the folder (file-name order). Mutable: delete removes entries. */
    private val allImages = ArrayList<SafImage>()
    private val thumbs = HashMap<String, Bitmap>()
    private var cols = 5

    private lateinit var runFrame: FrameLayout
    private lateinit var runGrid: LinearLayout
    private lateinit var libGrid: LinearLayout
    private lateinit var insertBar: View
    private lateinit var deleteZone: View
    private lateinit var runCountText: TextView

    private val runCells = ArrayList<View>()
    private var draggingView: View? = null

    private class DragState(val fromRun: Boolean, val name: String)

    /** Drag shadow that renders the card shrunk, centred under the finger. */
    private class ShrinkShadow(target: View, private val scale: Float = 0.75f) : View.DragShadowBuilder(target) {
        override fun onProvideShadowMetrics(size: Point, touch: Point) {
            val w = (view.width * scale).toInt().coerceAtLeast(1)
            val h = (view.height * scale).toInt().coerceAtLeast(1)
            size.set(w, h); touch.set(w / 2, h / 2)
        }
        override fun onDrawShadow(canvas: Canvas) {
            canvas.save(); canvas.scale(scale, scale); view.draw(canvas); canvas.restore()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "因子關卡排序"

        val tree = PetImagePrefs.treeUri(this)
        if (!tree.isNullOrBlank()) allImages.addAll(FactorLibrary.list(this))
        // Pre-select from the saved plan, reconciled to files that still exist (order kept).
        val existing = allImages.map { it.displayName }.toHashSet()
        FactorPlanPrefs.getOrder(this)?.forEach { if (it in existing && it !in order) order.add(it) }

        setContentView(buildUi(tree))
        if (allImages.isNotEmpty()) loadThumbsAsync()
    }

    private fun buildUi(tree: String?): View {
        val outer = FrameLayout(this).apply { setBackgroundColor(cBg) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        outer.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        content.addView(titleTv("因子關卡排序"))
        content.addView(hintTv(
            "上＝運行排序（實際會打·由左至右）；下＝因子庫（所有擷取卡）。長按卡片拖曳：" +
                "庫→運行排序＝加入並插到落點；運行排序內拖＝改順序；運行排序→庫＝移除；" +
                "拖到右側紅框＝從資料夾永久刪除該卡（會先確認）。",
        ))

        if (tree.isNullOrBlank() || allImages.isEmpty()) {
            content.addView(TextView(this).apply {
                text = if (tree.isNullOrBlank())
                    "尚未設定精靈圖庫資料夾。\n請回主頁點「選擇精靈圖庫資料夾」，再用懸浮列『擷取卡』存因子卡。"
                else
                    "圖庫沒有因子卡。\n請用懸浮列『擷取卡』先擷取幾張再回來排序。"
                setTextColor(cSub); textSize = 14f; setPadding(dp(4), dp(20), dp(4), dp(20))
            })
            return outer
        }

        // 頂區：運行排序
        content.addView(sectionTv("運行排序（實際會打）"))
        runCountText = TextView(this).apply { setTextColor(cSub); textSize = 12f; setPadding(0, dp(2), 0, dp(4)) }
        content.addView(runCountText)
        runGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        insertBar = View(this).apply { setBackgroundColor(cAccent); visibility = View.GONE }
        runFrame = FrameLayout(this).apply {
            background = rounded(cPanel, 10); minimumHeight = dp(120)
            addView(runGrid, FrameLayout.LayoutParams(MATCH, WRAP))
            addView(insertBar, FrameLayout.LayoutParams(dp(3), dp(104)))
        }
        val runScroll = ScrollView(this).apply { isFillViewport = true; addView(runFrame) }
        content.addView(runScroll, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(4) })

        // 底區：因子庫
        content.addView(sectionTv("因子庫（所有擷取卡·依檔名）"))
        libGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val libScroll = ScrollView(this).apply { isFillViewport = true; addView(libGrid) }
        content.addView(libScroll, LinearLayout.LayoutParams(MATCH, 0, 1.4f).apply { topMargin = dp(4) })

        // 底部動作列
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        bar.addView(btn("清除運行排序", cField) { order.clear(); persist(); rebuild() }, lpW(1f))
        bar.addView(
            btn("儲存並返回", cGood) { persist(); toast("已儲存排序：${order.size} 個因子"); finish() }
                .apply { setTypeface(typeface, Typeface.BOLD) },
            lpW(1.4f, dp(8)),
        )
        content.addView(bar)

        // 右側紅色刪除區（拖曳時才顯現）
        deleteZone = buildDeleteZone()
        outer.addView(
            deleteZone,
            FrameLayout.LayoutParams(dp(76), dp(128), Gravity.END or Gravity.CENTER_VERTICAL)
                .apply { rightMargin = dp(6) },
        )

        outer.setOnDragListener(outerDragListener)
        runFrame.setOnDragListener(runDragListener)
        libScroll.setOnDragListener(libDragListener)
        deleteZone.setOnDragListener(deleteDragListener)

        runFrame.doOnLayout {
            val wdp = it.width / resources.displayMetrics.density
            cols = (wdp / 92f).toInt().coerceIn(3, 9)
            rebuild()
        }
        return outer
    }

    private fun buildDeleteZone(): View {
        val z = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = rounded(cDanger, 12); alpha = 0f
        }
        z.addView(TextView(this).apply { text = "🗑"; textSize = 24f; gravity = Gravity.CENTER })
        z.addView(TextView(this).apply {
            text = "刪除\n因子庫檔案"; setTextColor(Color.WHITE); textSize = 11f; gravity = Gravity.CENTER
        })
        return z
    }

    // ---- drag listeners ----

    private val outerDragListener = View.OnDragListener { _, e ->
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> true
            DragEvent.ACTION_DRAG_ENDED -> {
                draggingView?.alpha = 1f; draggingView = null; insertBar.visibility = View.GONE; true
            }
            else -> false
        }
    }

    private val runDragListener = View.OnDragListener { _, e ->
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> true
            DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> {
                setInsertBar(computeInsertIndex(e.x, e.y)); true
            }
            DragEvent.ACTION_DRAG_EXITED -> { insertBar.visibility = View.GONE; true }
            DragEvent.ACTION_DROP -> { insertBar.visibility = View.GONE; handleDropOnRun(e); true }
            DragEvent.ACTION_DRAG_ENDED -> { insertBar.visibility = View.GONE; true }
            else -> false
        }
    }

    private val libDragListener = View.OnDragListener { _, e ->
        when (e.action) {
            DragEvent.ACTION_DROP -> { handleDropOnLib(e); true }
            else -> true
        }
    }

    private val deleteDragListener = View.OnDragListener { v, e ->
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> { v.alpha = 1f; true }
            DragEvent.ACTION_DRAG_ENTERED -> { v.scaleX = 1.12f; v.scaleY = 1.12f; true }
            DragEvent.ACTION_DRAG_EXITED -> { v.scaleX = 1f; v.scaleY = 1f; true }
            DragEvent.ACTION_DROP -> { v.scaleX = 1f; v.scaleY = 1f; handleDropOnDelete(e); true }
            DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 0f; v.scaleX = 1f; v.scaleY = 1f; true }
            else -> true
        }
    }

    private fun handleDropOnRun(e: DragEvent) {
        val st = e.localState as? DragState ?: return
        val idx = computeInsertIndex(e.x, e.y)
        val old = order.indexOf(st.name)
        if (old >= 0) order.removeAt(old)
        var at = idx
        if (old in 0 until idx) at -= 1          // removing an earlier item shifts the target left
        at = at.coerceIn(0, order.size)
        order.add(at, st.name)
        persist(); rebuild()
    }

    private fun handleDropOnLib(e: DragEvent) {
        val st = e.localState as? DragState ?: return
        if (st.fromRun) { order.remove(st.name); persist(); rebuild() }
    }

    private fun handleDropOnDelete(e: DragEvent) {
        val st = e.localState as? DragState ?: return
        confirmDelete(st.name)
    }

    private fun confirmDelete(name: String) {
        val img = allImages.firstOrNull { it.displayName == name } ?: return
        AlertDialog.Builder(this)
            .setTitle("刪除因子庫檔案")
            .setMessage("將從資料夾永久刪除「$name」，無法復原。確定刪除？")
            .setNegativeButton("取消", null)
            .setPositiveButton("刪除") { _, _ ->
                if (SafStore.deleteImage(this, img.uri)) {
                    allImages.removeAll { it.displayName == name }
                    order.remove(name)
                    thumbs.remove(name)?.recycle()
                    persist(); rebuild()
                    toast("已刪除「$name」")
                } else {
                    toast("刪除失敗（可能無寫入權限）")
                }
            }
            .show()
    }

    // ---- geometry / insert index ----

    private fun posIn(container: View, child: View): Pair<Int, Int> {
        val c = IntArray(2); val k = IntArray(2)
        container.getLocationInWindow(c); child.getLocationInWindow(k)
        return (k[0] - c[0]) to (k[1] - c[1])
    }

    /** Reading-order insert index for a drop at ([x],[y]) in [runFrame] coords. */
    private fun computeInsertIndex(x: Float, y: Float): Int {
        var idx = 0
        for (cell in runCells) {
            val (cx0, cy0) = posIn(runFrame, cell)
            val centreX = cx0 + cell.width / 2
            val top = cy0; val bottom = cy0 + cell.height
            val after = y > bottom || (y >= top && x > centreX)
            if (after) idx++
        }
        return idx
    }

    private fun setInsertBar(index: Int) {
        val lp = insertBar.layoutParams as FrameLayout.LayoutParams
        if (runCells.isEmpty()) {
            lp.width = dp(3); lp.height = dp(104); insertBar.layoutParams = lp
            insertBar.translationX = dp(6).toFloat(); insertBar.translationY = dp(6).toFloat()
            insertBar.visibility = View.VISIBLE; return
        }
        val i = index.coerceIn(0, runCells.size)
        val atEnd = i >= runCells.size
        val cell = if (atEnd) runCells[runCells.size - 1] else runCells[i]
        val (x, y) = posIn(runFrame, cell)
        val barX = if (atEnd) x + cell.width - dp(1) else x - dp(1)
        lp.width = dp(3); lp.height = cell.height; insertBar.layoutParams = lp
        insertBar.translationX = barX.toFloat(); insertBar.translationY = y.toFloat()
        insertBar.visibility = View.VISIBLE
    }

    // ---- grid build ----

    private fun rebuild() { populateRun(); populateLib() }

    private fun populateRun() {
        runCells.clear()
        val cells = order.mapIndexedNotNull { i, name ->
            val img = allImages.firstOrNull { it.displayName == name } ?: return@mapIndexedNotNull null
            makeCell(img, badge = i + 1, dim = false, fromRun = true).also { runCells.add(it) }
        }
        fillGrid(runGrid, cells)
        runCountText.text = if (order.isEmpty())
            "（空）從下方因子庫長按卡片拖上來加入"
        else
            "已排 ${order.size} 個（由左至右）：" + order.joinToString("、") { it.substringBeforeLast('.') }
    }

    private fun populateLib() {
        val cells = allImages.map { img ->
            val q = order.indexOf(img.displayName)
            makeCell(img, badge = if (q >= 0) q + 1 else null, dim = q >= 0, fromRun = false)
        }
        fillGrid(libGrid, cells)
    }

    private fun fillGrid(grid: LinearLayout, cells: List<View>) {
        grid.removeAllViews()
        if (cells.isEmpty()) return
        var row: LinearLayout? = null
        cells.forEachIndexed { i, cell ->
            if (i % cols == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
            }
            row!!.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = if (i % cols == 0) 0 else dp(6) })
        }
        val rem = cells.size % cols
        if (rem != 0) repeat(cols - rem) {
            row!!.addView(View(this), LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(6) })
        }
    }

    private fun makeCell(image: SafImage, badge: Int?, dim: Boolean, fromRun: Boolean): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val frame = FrameLayout(this).apply { background = rounded(if (badge != null) cAccent else cCell, 10) }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        thumbs[image.displayName]?.let { img.setImageBitmap(it) }
        frame.addView(img, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(104)))
        if (dim) frame.addView(
            View(this).apply { setBackgroundColor(Color.parseColor("#88000000")) },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(104)),
        )
        if (badge != null) frame.addView(
            TextView(this).apply {
                text = badge.toString(); setTextColor(Color.WHITE); textSize = 12f
                setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; background = oval(cAccent)
            },
            FrameLayout.LayoutParams(dp(24), dp(24)).apply { leftMargin = dp(5); topMargin = dp(5) },
        )
        val label = TextView(this).apply {
            text = image.displayName.substringBeforeLast('.'); setTextColor(cSub); textSize = 10f
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0)
        }
        col.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP))
        col.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP))
        col.setOnLongClickListener {
            draggingView = col
            val data = ClipData.newPlainText("factor", image.displayName)
            val ok = col.startDragAndDrop(data, ShrinkShadow(col), DragState(fromRun, image.displayName), 0)
            if (ok) col.alpha = 0.3f else draggingView = null
            ok
        }
        return col
    }

    private fun loadThumbsAsync() {
        val imgs = allImages.toList()
        Thread {
            val local = HashMap<String, Bitmap>()
            for (im in imgs) {
                if (thumbs.containsKey(im.displayName)) continue
                val b = SafStore.readBitmap(this, im.uri) ?: continue
                local[im.displayName] = b
            }
            runOnUiThread { thumbs.putAll(local); rebuild() }
        }.start()
    }

    private fun persist() = FactorPlanPrefs.setOrder(this, order)

    // ---- small view helpers ----

    private fun titleTv(t: String) = TextView(this).apply {
        text = t; setTextColor(cInk); textSize = 18f; setTypeface(typeface, Typeface.BOLD)
    }

    private fun hintTv(t: String) = TextView(this).apply {
        text = t; setTextColor(cSub); textSize = 12f; setPadding(0, dp(4), 0, dp(6))
    }

    private fun sectionTv(t: String) = TextView(this).apply {
        text = t; setTextColor(cInk); textSize = 14f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(6), 0, dp(2))
    }

    private fun rounded(fill: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radius).toFloat()
    }

    private fun oval(fill: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }

    private fun btn(text: String, bg: Int, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text; isAllCaps = false; setTextColor(cInk); textSize = 14f
        background = rounded(bg, 9); setPadding(dp(8), dp(10), dp(8), dp(10)); minWidth = 0; minHeight = 0
        setOnClickListener { onClick() }
    }

    private fun lpW(weight: Float, leftMargin: Int = 0) =
        LinearLayout.LayoutParams(0, WRAP, weight).apply { this.leftMargin = leftMargin }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        thumbs.values.forEach { it.recycle() }
        thumbs.clear()
    }
}
