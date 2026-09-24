package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.Size
import com.autoseer.libautomata.Templates

/**
 * Navigates the 因子關卡 selection UI — the "選擇+進入" module, kept independent of
 * the battle module ([BattleTurnRunner]/[SeerFactorScript]'s fight loop):
 *
 *   選擇格(grid) → 比對定位並點目標因子卡 → 詳情頁(detail)
 *
 * and can back out to the grid from any layer. Locating a factor is template-free:
 * each fully-visible top-row card is cropped and compared (multi-scale
 * [AutomataApi.similarity]) to the target's captured reference image
 * ([FactorTarget.pattern]); if none matches, the grid scrolls one row and the scan
 * repeats. Two light screen-id templates (grid/detail markers) gate the state so we
 * never tap blind — matched at a lenient [MARKER_THRESHOLD] because they are cut
 * from a compressed video frame (recapture on-device to tighten).
 *
 * All coordinates come from [SeerLayout] (normalized 1280x720). There is no
 * long-press primitive, so scrolling uses a slow swipe to approximate 長按拖曳;
 * tune [GRID_SCROLL_MS] / thresholds on device if needed.
 */
class FactorNavigator(
    private val api: AutomataApi,
    private val templates: Templates,
    private val delays: BattleDelays,
) {
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
     * Find [target] on the grid and tap it, scrolling as needed. Returns true once
     * we leave the grid onto the factor's 詳情頁. Scans only the fully-visible top
     * row (the second row's name plate sits below the fold, matching less reliably).
     */
    fun findAndTapFactor(
        target: FactorTarget,
        maxScrolls: Int = MAX_SCROLLS,
        threshold: Double = MATCH_THRESHOLD,
    ): Boolean {
        // Deterministic start: rewind to the top of the list first.
        repeat(TOP_REWIND) { scrollUpOneRow() }
        for (attempt in 0..maxScrolls) {
            api.refreshScreen()
            if (!isOnGrid()) {
                api.logger.w("因子導航：目前不在選擇格，無法比對定位")
                return false
            }
            val col = bestColumn(target, threshold)
            if (col >= 0) {
                api.logger.i("因子「${target.name}」命中第 ${col + 1} 欄 → 點選")
                api.click(SeerLayout.factorCardSlot(col))
                return api.waitUntil(DETAIL_WAIT_MS, 300) { isOnDetail() || !isOnGrid() }
            }
            if (attempt < maxScrolls) scrollDownOneRow()
        }
        return false
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
     * library card can be at a different absolute size/aspect than the on-grid
     * card cell (擷卡時的框大小不一），which pushes the true match outside a plain
     * multi-scale sweep and yields a garbage score. So first force the template to
     * the cell's card geometry (kills that drift), then fine-sweep for alignment.
     */
    private fun scoreCard(tmpl: com.autoseer.libautomata.IPattern, crop: com.autoseer.libautomata.IPattern, logDims: Boolean, name: String): Double {
        val tw = (crop.width * CANON_FRAC).toInt().coerceAtLeast(8)
        val th = (crop.height * CANON_FRAC).toInt().coerceAtLeast(8)
        if (logDims) {
            api.logger.i("因子比對「$name」模板 ${tmpl.width}x${tmpl.height} → 卡格幾何 ${tw}x${th}（卡格 ${crop.width}x${crop.height}）")
        }
        return tmpl.resize(Size(tw, th)).use { canon -> api.similarity(canon, crop, FINE_SCALES) }
    }

    companion object {
        private const val MAX_BACKS = 4
        private const val MAX_SCROLLS = 12       // rows to scan before giving up
        private const val TOP_REWIND = 6         // drags down to reach the list top first
        private const val MARKER_THRESHOLD = 0.70 // screen-id markers (video-derived; loose)
        private const val MATCH_THRESHOLD = 0.60  // card similarity (tune on device)
        private const val CANON_FRAC = 0.86       // 模板先縮到卡格幾何的比例（卡約佔卡格 0.88w×0.93h）
        private val FINE_SCALES = listOf(0.80, 0.88, 0.94, 1.0, 1.06, 1.12) // 卡格內細對位
        private const val GRID_SCROLL_MS = 700L   // slow drag ≈ 長按拖曳（無長按原語）
        private const val GRID_SETTLE_MS = 500L
        private const val DETAIL_WAIT_MS = 6_000L
    }
}
