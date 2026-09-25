package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.IPattern
import com.autoseer.libautomata.Match
import com.autoseer.libautomata.Region
import com.autoseer.libautomata.Size
import com.autoseer.libautomata.Templates

/**
 * Navigates the 因子關卡 selection UI — the "選擇+進入" module, kept independent of
 * the battle module ([BattleTurnRunner]/[SeerFactorScript]'s fight loop):
 *
 *   選擇格(grid) → 比對定位並點目標因子卡 → 詳情頁(detail)
 *
 * Locating a factor is template-free and position-free: the captured library image
 * ([FactorTarget.pattern]) is resized to the on-grid card size and slid over the
 * WHOLE grid region (multi-scale [AutomataApi.find]); the best peak's location is
 * the card, wherever it sits. This tolerates any scroll offset (cards settle at
 * arbitrary y) and any capture size — verified offline: correct card peaks ~0.90 at
 * its true centre, a card that isn't on screen peaks ≤0.49. If nothing matches, the
 * grid scrolls down and we rescan; to rewind we scroll up until the frame stops
 * changing (a fixed count can't recover from deep in a long library). Two light
 * screen-id templates (grid/detail markers) gate the state so we never tap blind.
 */
class FactorNavigator(
    private val api: AutomataApi,
    private val templates: Templates,
    private val delays: BattleDelays,
    /**
     * Hide/show the floating overlay. The overlay is captured by MediaProjection, so
     * a bubble over a card occludes it in the screenshot and it never matches;
     * scanning hides it, then restores it. Default no-op (e.g. tests).
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

    /** Drag upward (下→上) to reveal the next rows. Fling-free so it advances ~the
     *  drag distance (≈0.6 row), not ~1.7 rows of momentum that skips cards. */
    fun scrollDownOneRow() {
        api.dragSteady(SeerLayout.GRID_SCROLL_BOTTOM, SeerLayout.GRID_SCROLL_TOP, GRID_SCROLL_MS, GRID_HOLD_MS)
        api.sleep(GRID_SETTLE_MS)
    }

    /** Drag downward (上→下) to scroll the grid back toward the top (fling-free). */
    private fun scrollUpOneRow() {
        api.dragSteady(SeerLayout.GRID_SCROLL_TOP, SeerLayout.GRID_SCROLL_BOTTOM, GRID_SCROLL_MS, GRID_HOLD_MS)
        api.sleep(GRID_SETTLE_MS)
    }

    /**
     * Find [target] on the grid and tap it. Scans the current (settled) view — the
     * card is found wherever it sits — then, if absent, rewinds to the true top and
     * scrolls down rescanning. Returns true once we leave the grid onto the 詳情頁.
     */
    fun findAndTapFactor(
        target: FactorTarget,
        maxScrolls: Int = MAX_SCROLLS,
        threshold: Double = MATCH_THRESHOLD,
    ): Boolean {
        api.refreshScreen()
        if (isOnGrid()) findTargetOnScreen(target, threshold)?.let { return tapMatch(it, target) }
        scrollToTop()
        for (attempt in 0..maxScrolls) {
            api.refreshScreen()
            if (!isOnGrid()) {
                api.logger.w("因子導航：目前不在選擇格，無法比對定位")
                return false
            }
            findTargetOnScreen(target, threshold)?.let { return tapMatch(it, target) }
            if (attempt < maxScrolls) scrollDownOneRow()
        }
        api.logger.w("因子導航：捲完整份清單仍找不到「${target.name}」")
        return false
    }

    /**
     * Best match of [target] anywhere in the grid region, at/above [threshold], or
     * null. The library image is resized to the on-grid card size across [SCAN_SCALES]
     * (absorbs capture-size drift) and slid over the region; the peak's location is
     * the card. Uses the current (already-settled) screen — no refresh here so all
     * scales see the same frame.
     */
    private fun findTargetOnScreen(target: FactorTarget, threshold: Double): Match? {
        var best: Match? = null
        for (s in SCAN_SCALES) {
            val tw = (CARD_W * s).toInt()
            val th = (CARD_H * s).toInt()
            if (tw < 8 || th < 8) continue
            target.pattern.resize(Size(tw, th)).use { scaled ->
                val m = api.find(scaled, GRID_REGION, threshold = 0.0)
                val cur = best
                if (m != null && (cur == null || m.score > cur.score)) best = m
            }
        }
        val b = best
        if (b != null) {
            api.logger.i("因子「${target.name}」全區最佳 score=${(b.score * 100).toInt()}% @(${b.region.center.x},${b.region.center.y})")
        }
        return b?.takeIf { it.score >= threshold }
    }

    /** Tap the matched card's centre and confirm we left the grid onto the 詳情頁. */
    private fun tapMatch(m: Match, target: FactorTarget): Boolean {
        api.logger.i("因子「${target.name}」命中 → 點 (${m.region.center.x},${m.region.center.y})")
        api.click(m.region.center)
        return api.waitUntil(DETAIL_WAIT_MS, 300) { isOnDetail() || !isOnGrid() }
    }

    // ---- 畫面穩定 / 捲到頂 偵測（比對前後兩幀是否幾乎相同）----

    private fun gridSample(): IPattern = api.cropScreen(GRID_SAMPLE)

    /** 兩幀（同區同尺寸）幾乎相同？用於偵測「畫面停穩」「已到頂」。 */
    private fun sameFrame(a: IPattern, b: IPattern): Boolean =
        api.similarity(a, b, ONE_SCALE) >= STABLE_SIM

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
        private const val MAX_SCROLLS = 34       // 每步≈0.6排、fling-free，需較多步才到庫底
        private const val MARKER_THRESHOLD = 0.70 // screen-id markers (video-derived; loose)
        private const val MATCH_THRESHOLD = 0.68  // card peak（實幀：命中≥0.90、未在畫面≤0.49）
        // 卡片在格上的實際尺寸（正規化）＋掃描尺度：模板縮到此範圍在全區滑動
        private const val CARD_W = 176
        private const val CARD_H = 298
        private val SCAN_SCALES = listOf(0.85, 0.92, 1.0, 1.08, 1.15)
        private val GRID_REGION = Region(210, 100, 1065, 620) // 卡片格整體區域（避開左欄/頂列頁籤）
        private const val CHROME_SETTLE_MS = 250L // 隱藏懸浮窗後等合成器把它移出畫面再截圖
        // 捲動＝FGA 式不甩尾拖曳：GRID_SCROLL_MS 移動時間、GRID_HOLD_MS 抬起停頓（消 fling）。
        private const val GRID_SCROLL_MS = 500L
        private const val GRID_HOLD_MS = 350L
        private const val GRID_SETTLE_MS = 320L   // 拖曳後固定短暫停頓再掃（取代不穩的 settle）
        private const val DETAIL_WAIT_MS = 6_000L
        // 回頂偵測（掃到畫面不再變化＝到頂）
        private const val STABLE_SIM = 0.90       // 兩幀相似度≥此值＝視為同一畫面（容忍粒子動畫）
        private const val MAX_REWIND = 25         // 回頂最多往上捲幾次（到頂即止；每步較小故加大）
        private val ONE_SCALE = listOf(1.0)
        private val GRID_SAMPLE = Region(216, 100, 900, 340) // 到頂偵測取樣：頂列卡帶
    }
}
