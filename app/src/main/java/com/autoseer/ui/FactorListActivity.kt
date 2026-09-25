package com.autoseer.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autoseer.core.FactorLibrary
import com.autoseer.core.FactorPlanPrefs
import com.autoseer.core.PetImagePrefs
import com.autoseer.core.SafImage
import com.autoseer.core.SafStore

/**
 * 因子關卡排序 — pick which captured 因子卡 to fight and in what order. Tapping a
 * card adds it to the fight order (shrinks + dims it and shows a numbered badge);
 * tapping again removes it and renumbers the rest. Cards left un-tapped are not
 * fought. Saving persists the ordered selection to [FactorPlanPrefs], which
 * [com.autoseer.core.FactorPlan] feeds to the sweep. This screen only edits the
 * plan; it never touches the battle module.
 *
 * Built in code (plain Views, dark palette) to match [CardCaptureActivity].
 */
class FactorListActivity : AppCompatActivity() {

    private val cBg = Color.parseColor("#15181C")
    private val cField = Color.parseColor("#2A2F36")
    private val cInk = Color.parseColor("#F2F4F8")
    private val cSub = Color.parseColor("#AAB1BD")
    private val cAccent = Color.parseColor("#3F7BFF")
    private val cGood = Color.parseColor("#2EA36B")
    private val cCell = Color.parseColor("#0B0F14")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Ordered selected display-names (the fight order). */
    private val selected = ArrayList<String>()
    private val cells = ArrayList<Cell>()
    private val thumbTargets = ArrayList<Pair<Uri, ImageView>>()
    private lateinit var countText: TextView

    private class Cell(
        val name: String,
        val frame: FrameLayout,
        val img: ImageView,
        val dim: View,
        val badge: TextView,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "因子關卡排序"
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(TextView(this).apply {
            text = "因子關卡排序"; setTextColor(cInk); textSize = 18f; setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "點卡片＝加入戰鬥順序（縮小＋變暗＋顯示第幾個）；再點一次＝取消並重新編號。未選取的關卡不會打。順序即掃蕩順序。"
            setTextColor(cSub); textSize = 12f; setPadding(0, dp(4), 0, dp(8))
        })

        val tree = PetImagePrefs.treeUri(this)
        val images: List<SafImage> = if (tree.isNullOrBlank()) emptyList() else FactorLibrary.list(this)

        if (images.isEmpty()) {
            root.addView(TextView(this).apply {
                text = if (tree.isNullOrBlank())
                    "尚未設定精靈圖庫資料夾。\n請回主頁點「選擇精靈圖庫資料夾」，再用懸浮列『擷取卡』存因子卡。"
                else
                    "圖庫沒有因子卡。\n請用懸浮列『擷取卡』先擷取幾張因子卡再回來排序。"
                setTextColor(cSub); textSize = 14f; setPadding(dp(4), dp(20), dp(4), dp(20))
            })
            return root
        }

        // Pre-select from the saved plan, reconciled to files that still exist (order kept).
        val existing = images.map { it.displayName }.toHashSet()
        FactorPlanPrefs.getOrder(this)?.forEach { if (it in existing && it !in selected) selected.add(it) }

        countText = TextView(this).apply { setTextColor(cSub); textSize = 13f; setPadding(0, dp(6), 0, dp(6)) }
        root.addView(countText)

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(grid) }
        buildGrid(grid, images)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        bar.addView(
            btn("清除選擇", cField) { selected.clear(); refreshAll() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        bar.addView(
            btn("儲存排序", cGood) { save() }.apply { setTypeface(typeface, Typeface.BOLD) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f).apply { leftMargin = dp(8) },
        )
        root.addView(bar)

        refreshAll()
        loadThumbsAsync()
        return root
    }

    private fun buildGrid(grid: LinearLayout, images: List<SafImage>) {
        val cols = 3
        var row: LinearLayout? = null
        images.forEachIndexed { i, img ->
            if (i % cols == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(
                    row,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = dp(8) },
                )
            }
            row!!.addView(
                buildCell(img),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { leftMargin = if (i % cols == 0) 0 else dp(8) },
            )
        }
        // Pad the last row with empty cells so columns keep equal width.
        val rem = images.size % cols
        if (rem != 0) repeat(cols - rem) {
            row!!.addView(
                View(this),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) },
            )
        }
    }

    private fun buildCell(image: SafImage): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val frame = FrameLayout(this).apply { background = rounded(cCell, 10) }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val dim = View(this).apply { setBackgroundColor(Color.parseColor("#88000000")); visibility = View.GONE }
        val badge = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 12f; setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER; background = oval(cAccent); visibility = View.GONE
        }
        frame.addView(img, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(140)))
        frame.addView(dim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(140)))
        frame.addView(badge, FrameLayout.LayoutParams(dp(26), dp(26)).apply { leftMargin = dp(6); topMargin = dp(6) })

        val label = TextView(this).apply {
            text = image.displayName.substringBeforeLast('.'); setTextColor(cSub); textSize = 11f
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0)
        }
        col.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.setOnClickListener { toggle(image.displayName) }

        cells += Cell(image.displayName, frame, img, dim, badge)
        thumbTargets += image.uri to img
        return col
    }

    private fun toggle(name: String) {
        if (!selected.remove(name)) selected.add(name)
        refreshAll()
    }

    /** Repaint every cell from the current [selected] order (badges renumber live). */
    private fun refreshAll() {
        for (cell in cells) {
            val idx = selected.indexOf(cell.name)
            if (idx >= 0) {
                cell.img.scaleX = 0.84f; cell.img.scaleY = 0.84f
                cell.dim.visibility = View.VISIBLE
                cell.badge.visibility = View.VISIBLE
                cell.badge.text = (idx + 1).toString()
                cell.frame.background = rounded(cAccent, 10)
            } else {
                cell.img.scaleX = 1f; cell.img.scaleY = 1f
                cell.dim.visibility = View.GONE
                cell.badge.visibility = View.GONE
                cell.frame.background = rounded(cCell, 10)
            }
        }
        countText.text = if (selected.isEmpty())
            "尚未選取（未選＝維持預設：全部依檔名順序）"
        else
            "已排序 ${selected.size} 個因子：" + selected.joinToString("、") { it.substringBeforeLast('.') }
    }

    private fun save() {
        FactorPlanPrefs.setOrder(this, selected)
        Toast.makeText(
            this,
            if (selected.isEmpty()) "已儲存：未選任何因子（將維持預設：全部依檔名）"
            else "已儲存排序：${selected.size} 個因子",
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }

    private fun loadThumbsAsync() {
        val targets = thumbTargets.toList()
        Thread {
            for ((uri, iv) in targets) {
                val bmp = SafStore.readBitmap(this, uri) ?: continue
                runOnUiThread { iv.setImageBitmap(bmp) }
            }
        }.start()
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
}
