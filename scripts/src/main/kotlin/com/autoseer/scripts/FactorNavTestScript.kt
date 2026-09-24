package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.Script
import com.autoseer.libautomata.Templates

/**
 * 因子導航測試（不打戰鬥）：把「選擇→進入→退出→下一個」的銜接流程單獨跑起來，
 * 用來驗證因子定位（含捲動尋找）與返回，完全不觸碰、也不呼叫戰鬥模組。
 *
 * 逐一定位並進入圖庫每個因子的詳情頁，停留片刻供肉眼確認，再退回選擇格換下一個；
 * 跑完整份清單為一輪，循環 [MAX_LOOPS] 輪或使用者按停止為止。若目標不在當前畫面，
 * [FactorNavigator.findAndTapFactor] 會自動回頂端往下捲動尋找。
 */
class FactorNavTestScript(
    api: AutomataApi,
    private val templates: Templates,
    private val delays: BattleDelays,
    private val targets: List<FactorTarget>,
    private val setChromeVisible: (Boolean) -> Unit = {},
) : Script(api) {

    override val name = "因子導航測試 (FactorNavTestScript)"

    override fun run() {
        if (targets.isEmpty()) {
            api.logger.w("圖庫沒有因子卡，無法測試導航。請先用『擷取卡』存幾張。")
            return
        }
        val nav = FactorNavigator(api, templates, delays, setChromeVisible)
        api.logger.i("因子導航測試開始：清單 ${targets.size} 個（${targets.joinToString("、") { it.name }}）")

        var loop = 0
        while (loop < MAX_LOOPS) {
            loop++
            api.logger.i("=== 第 $loop/$MAX_LOOPS 輪 ===")
            for ((i, t) in targets.withIndex()) {
                api.logger.i("[$loop.${i + 1}] 前往因子「${t.name}」…")
                // 掃描期間隱藏懸浮窗（防遮擋）；backToGrid 兼作「退出當前因子」。
                nav.hideChrome()
                val entered = try {
                    nav.backToGrid() && nav.findAndTapFactor(t)
                } finally {
                    nav.showChrome()
                }
                if (entered) {
                    api.logger.i("✔ 已進入「${t.name}」詳情頁，停留 ${DWELL_MS}ms 供確認，再退出換下一個")
                    api.sleep(DWELL_MS)
                } else {
                    api.logger.w("✗ 未能進入「${t.name}」（比對不到或已捲到底）")
                    api.sleep(SHORT_MS)
                }
            }
        }
        api.logger.i("因子導航測試結束（達 $MAX_LOOPS 輪）。")
    }

    companion object {
        private const val MAX_LOOPS = 5      // 循環幾輪整份清單（可按停止提前結束）
        private const val DWELL_MS = 1500L   // 進入詳情頁後停留、供肉眼確認的時間
        private const val SHORT_MS = 600L
    }
}
