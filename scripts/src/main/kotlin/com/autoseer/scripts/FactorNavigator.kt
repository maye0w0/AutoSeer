package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.IPattern
import com.autoseer.libautomata.Region
import com.autoseer.libautomata.Size
import com.autoseer.libautomata.Templates

/**
 * Navigates the 因子關卡 selection UI — the "選擇+進入" module, kept independent of
 * the battle module ([BattleTurnRunner]/[SeerFactorScript]'s fight loop):
 *
 *   選擇格(grid) → 比對定位並點目標因子卡 → 詳情頁(detail)
 *
 * and can back out to the grid from any layer. Locating a factor is template-free:
 * each top-row card cell is cropped and compared (multi-scale [AutomataApi.similarity])
 * to the target's captured reference image ([FactorTarget.pattern]); if none matches,
 * the grid scrolls one row and the scan repeats. Two light screen-id templates
 * (grid/detail markers) gate the state so we never tap blind.
 *
 * Robustness: scanning waits for the grid to STOP moving first (avoid capturing a
 * mid-scroll frame), and "rewind to top" scrolls up until the frame stops changing
 * (a fixed count can't recover once scrolled deep into a long library). There is no
 * long-press primitive, so scrolling uses a slow swipe to approximate 長按拖曳.
 */
class FactorNavigator(
    private val api: AutomataApi,
    private val templates: Templates,
    private val delays: BattleDelays,
    /**
     * Hide/show the floating overlay. The overlay window is captured by
     * MediaProjection, so if the bubble sits over a card it occludes it in the
     * matching screenshot and that card never matches. Scanning hides it, then
     * restores it. Default no-op (e.g. tests).
     */
    private val setChromeVisible: (Boolean) -> Unit = {},
) {
    /** Hide the overlay so it can't occlude cards in the scan screenshots. */
    fun hideChrome() { setChromeVisible(false); api.sleep(CHROME_SETTLE_MS) }

    /** Restore the overlay after scanning. */
    fun showChrome() { setChromeVisible(true) }

    private fun onScreen(id: String): Boolean =
        templates.has(id) && api.exists(templates.get(id), threshold = MARKER_THRESHOLD)

    fun isOnGrid(): Boolean = onScreen(SeerTemplates.FACTOR_GRID)
    fun isOnDetail(): Boolean = onScreen(SeerTemplates.FACTOR_DETAIL)

    /** Tap the top-left 返回 arrow (shared across the three factor screens). */
    private fun tapExit() {
        api.click(SeerLayout.FACTOR_EXIT)
        api.sleep(delays.afterResultTap)
    }

    /**
     * Press 返回 until the selection grid shows (or [maxBacks] tries). Assumes any
     * 達到每天操作上限 popup was already dismissed by the caller.
     */
    fun backToGrid(maxBacks: Int = MAX_BACKS): Boolean {
        repeat(maxBacks) {
            api.refreshScreen()
            if (isOnGrid()) return true
            tapExit()
        }
        api.refreshScreen()
        return isOnGrid()
    }

    /** Long drag upward (下→上) to reveal the next row of factors. */
    fun scrollDownOneRow() {
        api.swipe(SeerLayout.GRID_SCROLL_BOTTOM, SeerLayout.GRID_SCROLL_TOP, GRID_SCROLL_MS)
        api.sleep(GRID_SETTLE_MS)
    }

    /** Drag downward (上→下) to scroll the grid back toward the top. */
    private fun scrollUpOneRow() {
        api.swipe(SeerLayout.GRID_SCROLL_TOP, SeerLayout.GRID_SCROLL_BOTTOM, GRID_SCROLL_MS)
        api.sleep(GRID_SETTLE_MS)
    }

    /**
     * Find [target] on the grid and tap it. First scans the current (settled) view
     * — the target is often already visible, so it enters with no scrolling. If not,
     * rewinds to the true top and scans downward row by row. Returns true once we
     * leave the grid onto the factor's 詳情頁.
     */
    fun findAndTapFactor(
        target: FactorTarget,
        maxScrolls: Int = MAX_SCROLLS,
        threshold: Double = MATCH_THRESHOLD,
    ): Boolean {
        settleGrid()
        if (isOnGrid()) {
            val col = bestColumn(target, threshold)
            if (col >= 0) return tapColumn(col, target)
        }
        scrollToTop()
        for (attempt in 0..maxScrolls) {
            settleGrid()
            if (!isOnGrid()) {
                api.logger.w("因子導航：目前不在選擇格，無法比對定位")
                return false
            }
            val col = bestColumn(target, threshold)
            if (col >= 0) return tapColumn(col, target)
            if (attempt < maxScrolls) scrollDownOneRow()
        }
        api.logger.w("因子導航：捲完整份清單仍找不到「${target.name}」")
        return false
    }

    /** Tap the matched column and confirm we left the grid onto the 詳情頁. */
    private fun tapColumn(col: Int, target: FactorTarget): Boolean {
        api.logger.i("因子「${target.name}」命中第 ${col + 1} 欄 → 點選")
        api.click(SeerLayout.factorCardSlot(col))
        return api.waitUntil(DETAIL_WAIT_MS, 300) { isOnDetail() || !isOnGrid() }
    }

    /** Best-matching top-row column (0-based) for [target] at/above [threshold], or -1. */
    private fun bestColumn(target: FactorTarget, threshold: Double): Int {
        var bestCol = -1
        var bestScore = threshold
        for (col in 0 until SeerLayout.FACTOR_CARD_COUNT) {
            val region = SeerLayout.factorCardMatchRegion(col) ?: continue
            api.cropScreen(region).use { crop ->
                val s = scoreCard(target.pattern, crop, logDims = col == 0, name = target.name)
                api.logger.i("  因子比對 第${col + 1}欄 score=${(s * 100).toInt()}%")
                if (s > bestScore) { bestScore = s; bestCol = col }
            }
        }
        return bestCol
    }

    /**
     * Score how well the library [tmpl] matches the card in [crop]. The captured
     * library card can be at a different absolute size/aspect than the on-grid card
     * cell (擷卡時的框大小不一), which pushes the true match outside a plain multi-scale
     * sweep and yields a garbage score. So first force the template to the cell's card
     * geometry (kills that drift), then fine-sweep for alignment.
     */
    private fun scoreCard(tmpl: IPattern, crop: IPattern, logDims: Boolean, name: String): Double {
        val tw = (crop.width * CANON_FRAC).toInt().coerceAtLeast(8)
        val th = (crop.height * CANON_FRAC).toInt().coerceAtLeast(8)
        if (logDims) {
            api.logger.i("因子比對「$name」模板 ${tmpl.width}x${tmpl.height} → 卡格幾何 ${tw}x${th}（卡格 ${crop.width}x${crop.height}）")
        }
        return tmpl.resize(Size(tw, th)).use { canon -> api.similarity(canon, crop, FINE_SCALES) }
    }

    // ---- 畫面穩定 / 捲到頂 偵測（比對前後兩幀是否幾乎相同）----

    private fun gridSample(): IPattern = api.cropScreen(GRID_SAMPLE)

    /** 兩幀（同區同尺寸）幾乎相同？用於偵測「畫面停穩」「已到頂」。 */
    private fun sameFrame(a: IPattern, b: IPattern): Boolean =
        api.similarity(a, b, ONE_SCALE) >= STABLE_SIM

    /** 等畫面停穩（連續兩幀幾乎相同）再回；最多 [SETTLE_TRIES] 次，避免枯等。 */
    private fun settleGrid() {
        api.refreshScreen()
        var prev = gridSample()
        try {
            repeat(SETTLE_TRIES) {
                api.sleep(SETTLE_MS)
                api.refreshScreen()
                val cur = gridSample()
                val same = sameFrame(prev, cur)
                prev.close(); prev = cur
                if (same) return
            }
        } finally {
            prev.close()
        }
    }

    /** 往上捲到「畫面不再變化」＝已到列表頂端；最多 [MAX_REWIND] 次。 */
    private fun scrollToTop() {
        api.refreshScreen()
        var prev = gridSample()
        try {
            repeat(MAX_REWIND) {
                scrollUpOneRow()
                api.refreshScreen()
                val cur = gridSample()
                val same = sameFrame(prev, cur)
                prev.close(); prev = cur
                if (same) return
            }
        } finally {
            prev.close()
        }
    }

    companion object {
        private const val MAX_BACKS = 4
        private const val MAX_SCROLLS = 14       // rows to scan before giving up
        private const val MARKER_THRESHOLD = 0.70 // screen-id markers (video-derived; loose)
        private const val MATCH_THRESHOLD = 0.55  // card similarity（自比非對角≤0.39，留餘裕）
        private const val CANON_FRAC = 0.86       // 模板先縮到卡格幾何的比例（卡約佔卡格 0.88w×0.93h）
        private val FINE_SCALES = listOf(0.80, 0.88, 0.94, 1.0, 1.06, 1.12) // 卡格內細對位
        private const val CHROME_SETTLE_MS = 250L // 隱藏懸浮窗後等合成器把它移出畫面再截圖
        private const val GRID_SCROLL_MS = 700L   // slow drag ≈ 長按拖曳（無長按原語）
        private const val GRID_SETTLE_MS = 500L
        private const val DETAIL_WAIT_MS = 6_000L
        // 畫面穩定/到頂偵測
        private const val STABLE_SIM = 0.90       // 兩幀相似度≥此值＝視為同一畫面（容忍粒子動畫）
        private const val MAX_REWIND = 15         // 回頂最多往上捲幾次（到頂即止）
        private const val SETTLE_TRIES = 5        // 等停穩最多幾輪
        private const val SETTLE_MS = 200L
        private val ONE_SCALE = listOf(1.0)
        private val GRID_SAMPLE = Region(216, 100, 900, 340) // 變化偵測取樣：頂列卡帶
    }
}
