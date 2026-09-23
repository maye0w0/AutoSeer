package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.Location
import com.autoseer.libautomata.Script
import com.autoseer.libautomata.Templates

/**
 * 「精靈因子」關卡自動掃蕩，依標註 PPT（20260921 新版，36 頁）的完整流程組裝可復用
 * 視覺模塊（[SeerModules]）與戰鬥核心（[BattleTurnRunner]）：
 *
 *   反覆：開啟/繼續挑戰 → 精靈恢復 →（每日首次提示?→確認）→ 等恢復完成字樣 →
 *        進入戰鬥 → 等你的回合 → 依該關技能排序戰鬥 → 戰鬥結束：
 *          勝利 → 點繼續 → 下一關
 *          失敗 → 點繼續 → 繼續挑戰 → 重打同一關（達每關重試上限才停）
 *   直到「達到每天操作上限」出現 → 確認 → 回大廳 → 結束。
 *
 * 停止條件改為「持續到達到上限」（不再用清關場數/循環輪數），另設高安全上限防呆。
 * 關卡技能沿用使用者腳本的 [BattlePlan]，第幾關由 [BattlePlan.stageIndexFor] 環繞
 * 決定（FR-1）；失敗重打時 battlesDone 不變、關 index 不變。
 */
class SeerFactorScript(
    api: AutomataApi,
    private val templates: Templates,
    private val plan: BattlePlan = BattlePlan.default(),
    private val delays: BattleDelays = BattleDelays.default(),
    /** 失敗達每關重試上限後：true=回大廳再停；false(預設)=原地停。 */
    private val backToLobbyOnExhaust: Boolean = false,
) : Script(api) {

    override val name = "精靈因子掃蕩 (SeerFactorScript)"

    private val m = SeerModules(api, templates)
    private val runner = BattleTurnRunner(api, templates, delays)
    private val progress = SweepProgress(plan, backToLobbyOnExhaust)

    /** Stages cleared so far (for logs / UI). */
    val battlesDone: Int get() = progress.battlesDone

    private var stop = false

    override fun run() {
        api.logger.i("精靈因子掃蕩開始：每輪關數=${plan.stagesPerLoop}, 起始關=${plan.startStage}")
        var unknown = 0
        var iterations = 0
        while (!stop) {
            if (++iterations > SAFETY_CAP) { api.logger.w("達安全上限（$SAFETY_CAP），停止防呆。"); break }
            api.refreshScreen()

            // 次數用盡：整個掃蕩結束 → 回大廳
            if (m.exists(SeerTemplates.DAILY_LIMIT)) { onDailyLimit(); break }

            // 已在戰鬥中（你的回合）？直接打這一關。
            if (m.exists(SeerTemplates.BATTLE_ACTION)) { fightThisStage(); unknown = 0; continue }

            // 結算殘留（點擊繼續字樣仍在）→ 點空白略過，避免卡住。
            if (m.exists(SeerTemplates.TAP_CONTINUE) ||
                m.exists(SeerTemplates.RESULT_WIN) || m.exists(SeerTemplates.RESULT_LOSE)
            ) {
                api.click(SeerLayout.VICTORY_CONTINUE); api.sleep(delays.afterResultTap); unknown = 0; continue
            }

            // 前置：點「開啟/繼續挑戰」固定位置（兩者同位置，使用者已確認）直到「精靈恢復」
            // 出現＝關卡資訊卡已展開。改「固定點＋驗證」取代繼續挑戰樣板辨識（實機不穩、
            // 導致失敗後資訊卡沒展開、重打卡住 A1-2）。
            if (!m.exists(SeerTemplates.PET_RECOVER)) {
                val opened = m.tapUntilAppears(SeerTemplates.PET_RECOVER, ENTRY_TRIES) {
                    api.click(SeerLayout.CONTINUE_CHALLENGE)   // =(1168,622)，開啟/繼續挑戰同位置
                    api.sleep(delays.afterResultTap)
                }
                if (!opened) {
                    api.logger.w("點開啟/繼續挑戰後未見『精靈恢復』（關卡資訊卡未展開）")
                    if (++unknown >= STUCK_LIMIT) { api.logger.w("連續無法展開關卡資訊卡，停止。"); break }
                    continue
                }
                api.logger.i("關卡資訊卡已展開（偵測到精靈恢復）")
            }

            // 精靈恢復模塊
            if (m.waitAndTap(SeerTemplates.PET_RECOVER, WAIT_UI_MS)) {
                api.sleep(delays.afterHeal)
                api.refreshScreen()
                // 每日首次恢復提示（若出現）→ 確認
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

            // 進入戰鬥後可能直接跳「達到每天操作上限」
            api.refreshScreen()
            if (m.exists(SeerTemplates.DAILY_LIMIT)) { onDailyLimit(); break }

            // 等你的回合（Boss 先制可能久等）
            if (!m.waitAppear(SeerTemplates.BATTLE_ACTION, WAIT_TURN_MS)) {
                api.refreshScreen()
                if (m.exists(SeerTemplates.DAILY_LIMIT)) { onDailyLimit(); break }
                if (++unknown >= STUCK_LIMIT) { api.logger.w("等不到你的回合，停止。"); break }
                continue
            }
            fightThisStage(); unknown = 0
        }
        api.logger.i("精靈因子掃蕩結束，共清 $battlesDone 關。")
    }

    /** 打當前這一關並依勝/敗分流。 */
    private fun fightThisStage() {
        val stageIdx = progress.currentStageIndex()
        val stage = plan.forStage(stageIdx)
        val label = "（第 ${stageIdx + 1} 關｜已清 ${progress.battlesDone}｜重試 ${progress.retriesThisStage}）"
        when (runner.fight(stage.steps, plan.defaultCode, label)) {
            BattleTurnRunner.Result.WIN -> onWin()
            BattleTurnRunner.Result.LOSE -> onLose("失敗")
            BattleTurnRunner.Result.PLAN_EXHAUSTED -> {
                api.logger.w("此關技能排序跑完仍未勝利 → 撤退")
                m.retreat(delays)          // 撤退後會走到「失敗」結算
                onLose("撤退")
            }
            BattleTurnRunner.Result.STUCK -> api.logger.w("戰鬥中畫面卡住，回主迴圈重試。")
        }
    }

    /** 勝利：累加 → 點繼續 → 下一關（記帳交給 [SweepProgress]）。 */
    private fun onWin() {
        progress.onWin()
        api.logger.i("勝利！已清 ${progress.battlesDone} 關 → 點擊繼續，進入下一關")
        dismissResultScreen()
    }

    /** 失敗/撤退：點繼續 → 依 [SweepProgress] 決定重打同關或停止（原地/回大廳）。 */
    private fun onLose(reason: String) {
        dismissResultScreen()
        val stageNo = progress.currentStageIndex() + 1
        when (progress.onLose()) {
            SweepProgress.LoseAction.RETRY ->
                api.logger.i("$reason → 準備重打第 $stageNo 關（第 ${progress.retriesThisStage} 次重試）")
            SweepProgress.LoseAction.STOP_TO_LOBBY -> {
                api.logger.w("第 $stageNo 關連續 $reason 超過重試上限 → 回大廳並停止。")
                backToLobby(); stop = true
            }
            SweepProgress.LoseAction.STOP_HERE -> {
                api.logger.w("第 $stageNo 關連續 $reason 超過重試上限 → 原地停止（不回大廳）。")
                stop = true
            }
        }
    }

    /**
     * 戰鬥結束畫面：先等「點擊任意位置繼續」字樣出現（在那之前點擊無效），再點空白
     * 區「直到該字樣消失」為止——確保真的離開結算畫面 (抱怨2)。
     */
    private fun dismissResultScreen() {
        if (!m.waitAppear(SeerTemplates.TAP_CONTINUE, WAIT_UI_MS)) {
            api.logger.w("未見『點擊任意位置繼續』字樣，仍嘗試點擊繼續")
        }
        m.tapPointUntilGone(
            SeerTemplates.TAP_CONTINUE, SeerLayout.VICTORY_CONTINUE, DISMISS_TRIES, delays.afterResultTap,
        )
    }

    /** 達到每天操作上限 → 確認 → 回大廳。 */
    private fun onDailyLimit() {
        api.logger.i("偵測到『達到每天操作上限』→ 確認並回大廳")
        api.click(DAILY_LIMIT_CONFIRM)
        api.sleep(delays.afterResultTap)
        backToLobby()
    }

    /** 回大廳模塊：快速功能選單 → 小房子 → 等航行指南出現。 */
    private fun backToLobby() {
        api.logger.i("回到大廳…")
        if (m.waitAndTap(SeerTemplates.QUICK_MENU, WAIT_UI_MS)) api.sleep(delays.afterResultTap)
        if (m.waitAndTap(SeerTemplates.HOME_BTN, WAIT_UI_MS)) api.sleep(delays.afterResultTap)
        if (m.waitAppear(SeerTemplates.NAV_GUIDE, WAIT_UI_MS)) api.logger.i("已回到大廳。")
        else api.logger.w("未確認回到大廳（航行指南未出現）。")
    }

    companion object {
        // 「確認」按鈕固定座標（正規化 1280x720，量自 PPT）。
        private val FIRST_TIP_CONFIRM = Location(723, 486)   // 每日首次恢復提示 → 確認
        private val DAILY_LIMIT_CONFIRM = Location(640, 486) // 達到每天操作上限 → 確認

        private const val WAIT_UI_MS = 12_000L
        private const val WAIT_TURN_MS = 90_000L  // 等你的回合（先制/長開場）
        private const val STUCK_LIMIT = 40
        private const val SAFETY_CAP = 200        // 防呆：正常會由「達到上限」先結束
        private const val DISMISS_TRIES = 5       // 結算畫面點繼續的重試次數
        private const val ENTRY_TRIES = 5         // 點開啟/繼續挑戰、等資訊卡展開的重試次數
    }
}
