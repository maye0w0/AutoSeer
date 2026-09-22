package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.Location
import com.autoseer.libautomata.Script
import com.autoseer.libautomata.Templates

/**
 * 「精靈因子」關卡自動掃蕩，依標註 PPT 的流程組裝可復用視覺模塊（[SeerModules]）
 * 與戰鬥核心（[BattleTurnRunner]）：
 *
 *   反覆：精靈恢復 →（每日首次提示?→確認）→ 等恢復完成字樣 → 進入戰鬥 →
 *        等你的回合 → 依該關技能排序戰鬥 → 勝利→點擊繼續（下一關）
 *   直到「達到每天操作上限」出現 → 確認 → 回大廳 → 結束。
 *
 * 全程全螢幕辨識、點在找到處；固定座標只用於兩個「確認」按鈕。關卡技能沿用
 * 使用者腳本的 [BattlePlan]，第幾關由 [BattlePlan.stageIndexFor] 環繞決定（FR-1）。
 */
class SeerFactorScript(
    api: AutomataApi,
    private val templates: Templates,
    private val plan: BattlePlan = BattlePlan.default(),
    private val delays: BattleDelays = BattleDelays.default(),
) : Script(api) {

    override val name = "精靈因子掃蕩 (SeerFactorScript)"

    var battlesDone = 0
        private set

    private val m = SeerModules(api, templates)
    private val runner = BattleTurnRunner(api, templates, delays)

    override fun run() {
        api.logger.i("精靈因子掃蕩開始：每輪關數=${plan.stagesPerLoop}, 循環=${plan.loops}")
        var unknown = 0
        while (true) {
            if (reachedLimit()) { api.logger.i("已達安全上限（清 $battlesDone 關），停止。"); break }
            api.refreshScreen()

            // 次數用盡：整個掃蕩結束 → 回大廳
            if (m.exists(SeerTemplates.DAILY_LIMIT)) {
                api.logger.i("偵測到『達到每天操作上限』→ 確認並回大廳")
                api.click(DAILY_LIMIT_CONFIRM)
                api.sleep(delays.afterResultTap)
                backToLobby()
                break
            }

            // 已在戰鬥中（你的回合）？直接打這一關。
            if (m.exists(SeerTemplates.BATTLE_ACTION)) {
                fightThisStage(); unknown = 0; continue
            }
            // 勝利結算殘留 → 點繼續。
            if (m.exists(SeerTemplates.RESULT_WIN)) {
                m.tapIfPresent(SeerTemplates.RESULT_WIN); api.sleep(delays.afterResultTap); unknown = 0; continue
            }

            // 前置：開啟挑戰（若在關卡地圖）
            m.tapIfPresent(SeerTemplates.OPEN_CHALLENGE)

            // 精靈恢復模塊
            if (m.waitAndTap(SeerTemplates.PET_RECOVER, WAIT_UI_MS)) {
                api.sleep(delays.afterHeal)
                // 每日首次恢復提示（若出現）→ 確認
                api.refreshScreen()
                if (m.tapFixedIfPresent(SeerTemplates.FIRST_RECOVER_TIP, FIRST_TIP_CONFIRM)) {
                    api.sleep(delays.afterResultTap)
                }
                // 等恢復完成字樣（全部恢復 or 已滿）；沒出現就重跑一輪（回頭再恢復）
                val ok = m.waitAppearAny(
                    listOf(SeerTemplates.RECOVER_FULL, SeerTemplates.RECOVER_CANNOT), WAIT_UI_MS,
                )
                if (ok == null) { api.logger.w("未偵測到恢復完成字樣，重試恢復"); continue }
            }

            // 進入戰鬥
            if (!m.waitAndTap(SeerTemplates.ENTER_BATTLE, WAIT_UI_MS)) {
                if (++unknown >= STUCK_LIMIT) { api.logger.w("連續無法進入戰鬥，停止。"); break }
                api.sleep(700); continue
            }
            api.sleep(delays.afterEnter)

            // 等你的回合（Boss 先制可能久等）
            if (!m.waitAppear(SeerTemplates.BATTLE_ACTION, WAIT_TURN_MS)) {
                // 也可能直接跳出達到上限
                api.refreshScreen()
                if (m.exists(SeerTemplates.DAILY_LIMIT)) continue
                if (++unknown >= STUCK_LIMIT) { api.logger.w("等不到你的回合，停止。"); break }
                continue
            }
            fightThisStage(); unknown = 0
        }
        api.logger.i("精靈因子掃蕩結束，共清 $battlesDone 關。")
    }

    /** 打當前這一關，勝利後點繼續並累加。 */
    private fun fightThisStage() {
        val stage = plan.forStage(plan.stageIndexFor(battlesDone))
        val label = "（第 ${plan.stageIndexFor(battlesDone) + 1} 關｜已清 $battlesDone）"
        when (runner.fight(stage.steps, plan.defaultCode, label)) {
            BattleTurnRunner.Result.WIN -> {
                battlesDone++
                api.logger.i("勝利！已清 $battlesDone 關 → 點擊繼續")
                m.waitAndTap(SeerTemplates.RESULT_WIN, WAIT_UI_MS)
                api.sleep(delays.afterResultTap)
            }
            BattleTurnRunner.Result.PLAN_EXHAUSTED ->
                api.logger.w("此關技能排序跑完仍未勝利（暫不自動撤退，之後補失敗處理）。")
            BattleTurnRunner.Result.STUCK ->
                api.logger.w("戰鬥中畫面卡住。")
        }
    }

    /** 回大廳模塊：快速功能選單 → 小房子 → 等航行指南出現。 */
    private fun backToLobby() {
        api.logger.i("回到大廳…")
        if (m.waitAndTap(SeerTemplates.QUICK_MENU, WAIT_UI_MS)) api.sleep(delays.afterResultTap)
        if (m.waitAndTap(SeerTemplates.HOME_BTN, WAIT_UI_MS)) api.sleep(delays.afterResultTap)
        if (m.waitAppear(SeerTemplates.NAV_GUIDE, WAIT_UI_MS)) api.logger.i("已回到大廳。")
        else api.logger.w("未確認回到大廳（航行指南未出現）。")
    }

    private fun reachedLimit(): Boolean {
        if (plan.maxBattles > 0 && battlesDone >= plan.maxBattles) return true
        val target = plan.clearsTarget()
        return target > 0 && battlesDone >= target
    }

    companion object {
        // 「確認」按鈕固定座標（正規化 1280x720，量自 PPT）。
        private val FIRST_TIP_CONFIRM = Location(723, 486)   // 每日首次恢復提示 → 確認
        private val DAILY_LIMIT_CONFIRM = Location(640, 486) // 達到每天操作上限 → 確認

        private const val WAIT_UI_MS = 12_000L
        private const val WAIT_TURN_MS = 90_000L  // 等你的回合（先制/長開場）
        private const val STUCK_LIMIT = 40
    }
}
