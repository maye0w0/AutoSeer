package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.IPattern
import com.autoseer.libautomata.Location
import com.autoseer.libautomata.Templates

/**
 * Runs the in-battle per-turn logic for ONE battle until 勝利 (or the plan is
 * exhausted / the screen gets stuck). Factored out of the farm loop so mission
 * sweeps ([SeerFactorScript]) and any future battle driver share the same, proven
 * casting / 換精靈 / `*N`-until-defeat handling.
 */
class BattleTurnRunner(
    private val api: AutomataApi,
    private val templates: Templates,
    private val delays: BattleDelays = BattleDelays.default(),
) {
    enum class Result { WIN, LOSE, PLAN_EXHAUSTED, STUCK }

    private var stepIndex = 0

    /**
     * Fight the current battle. [steps] empty = spam [defaultCode] every turn.
     * Ends when the result screen shows: returns WIN on 「勝利」 or LOSE on
     * 「失敗」 (both screens also carry 「點擊任意位置繼續」, so the top diamond is
     * what distinguishes them — miscropping RESULT_WIN as the continue text was
     * why losses were once counted as wins). PLAN_EXHAUSTED if the tactics run
     * out before the battle ends; STUCK if nothing recognizable happens.
     */
    fun fight(steps: List<Step>, defaultCode: Int, label: String = ""): Result {
        stepIndex = 0
        var unknown = 0
        api.logger.i("開始戰鬥$label")
        while (true) {
            api.refreshScreen()
            when {
                exists(SeerTemplates.RESULT_WIN) -> return Result.WIN
                exists(SeerTemplates.RESULT_LOSE) -> return Result.LOSE
                // Continue text present but neither diamond matched yet: settle then classify.
                exists(SeerTemplates.TAP_CONTINUE) -> return resolveOutcome()
                exists(SeerTemplates.PET_DEFEATED) -> {
                    if (!onPetDefeated(steps)) return Result.PLAN_EXHAUSTED
                    unknown = 0
                }
                exists(SeerTemplates.BATTLE_ACTION) -> {
                    if (!onYourTurn(steps, defaultCode)) return Result.PLAN_EXHAUSTED
                    unknown = 0
                }
                else -> {
                    if (++unknown >= STUCK_LIMIT) return Result.STUCK
                    api.sleep(600)
                }
            }
        }
    }

    /**
     * The result screen is up (「點擊任意位置繼續」 seen) but the win/lose diamond
     * hasn't matched yet — give the animation a moment and read it. Defaults to
     * WIN only if 失敗 is never seen, and logs when it had to guess.
     */
    private fun resolveOutcome(): Result {
        repeat(OUTCOME_POLLS) {
            if (exists(SeerTemplates.RESULT_LOSE)) return Result.LOSE
            if (exists(SeerTemplates.RESULT_WIN)) return Result.WIN
            api.sleep(300)
            api.refreshScreen()
        }
        api.logger.w("戰鬥結束但未讀到勝利/失敗字樣，暫以勝利處理")
        return Result.WIN
    }

    /** Returns false when the plan is exhausted before 勝利 (failure). */
    private fun onYourTurn(steps: List<Step>, defaultCode: Int): Boolean {
        when {
            steps.isEmpty() -> cast(defaultCode)
            stepIndex >= steps.size -> return false
            else -> {
                val step = steps[stepIndex]
                if (step.isSwitch) {
                    deployPet(step.petIndex, alreadyOpen = false)   // 主動切換 (4-1)
                    stepIndex++
                } else {
                    cast(step.code)
                    if (!step.untilDefeat) stepIndex++   // *N stays; advances on 已戰敗
                }
            }
        }
        api.waitUntil(TURN_SETTLE_MS, pollMs = 400) {
            !exists(SeerTemplates.BATTLE_ACTION) || exists(SeerTemplates.PET_DEFEATED)
        }
        return true
    }

    /**
     * A pet is 已戰敗 (犧牲技主動下場 4-2 / 被打死 4-3). Bring in the pet the script's
     * next step names, or 決策 b fallback. False = plan exhausted (→ retreat).
     * The死因 needn't be told apart — the replacement is decided purely from the
     * script by [DeployDecision].
     */
    private fun onPetDefeated(steps: List<Step>): Boolean {
        val d = DeployDecision.decide(steps, stepIndex)
        stepIndex = d.nextStepIndex
        // deployPet returns whether a pet actually took the field. Propagate it:
        // failing to deploy (scripted pet already 已戰敗 / recognition fails, or the
        // whole team is down) returns false → PLAN_EXHAUSTED → retreat, instead of
        // spinning the loop while PET_DEFEATED stays up.
        return when (val a = d.action) {
            is DeployDecision.Action.Scripted -> deployPet(a.petIndex, alreadyOpen = true)
            DeployDecision.Action.Fallback -> deployPet(null, alreadyOpen = true)
            DeployDecision.Action.PlanExhausted -> false
        }
    }

    private fun cast(code: Int) {
        api.logger.i("施放：${SeerLayout.labelFor(code)}")
        api.click(SeerLayout.pointFor(code) ?: SeerLayout.SKILL_SLOTS[0])
        api.sleep(delays.afterSkill)
    }

    /**
     * Switch a pet in AND verify it actually took the field. [target] = the
     * scripted pet (1..6); null = 決策 b「補位任一可用精靈」(tries each slot in turn).
     * [alreadyOpen] = the 換精靈 sub-screen is already up (a pet just died) vs. we
     * must open it via the 精靈 button first (主動切換 while the pet is alive).
     *
     * Verification ([verifyDeployed]) is by "did we leave the 換精靈 介面", not by
     * head-similarity. The old code tapped a fixed slot and moved on, so a failed
     * drag left PET_DEFEATED up → the loop re-entered onPetDefeated → stepIndex ran
     * onto a cast → deployAnyPet blind-tapped the 精靈1 slot, which overlaps the
     * 招牌技 圓鈕. Here, a failed switch (still in the 換精靈 介面) retries the SAME
     * target instead of falling through to that blind tap.
     */
    private fun deployPet(target: Int?, alreadyOpen: Boolean): Boolean {
        val slots: List<Int> = when (target) {
            null -> (0 until SeerLayout.PET_COUNT).toList()              // fallback: try each
            else -> listOf(target - 1).filter { it in 0 until SeerLayout.PET_COUNT }
        }
        if (slots.isEmpty()) return false
        val attemptsPerSlot = if (target != null) SWITCH_ATTEMPTS else 1
        for (slotIndex in slots) {
            repeat(attemptsPerSlot) {
                // Open the 換精靈 sub-screen only for 主動切換 (pet still alive). On 陣亡補位
                // the game already popped it up; tapping 精靈 again would mis-hit / toggle
                // it (seen on device: a re-tap opened a skill popup), so a retry there just
                // re-taps the card.
                if (!alreadyOpen) {
                    api.click(SeerLayout.PET.center); api.sleep(delays.afterSwitch); api.refreshScreen()
                }
                api.logger.i("換精靈 ${slotIndex + 1}${if (target == null) "（補位）" else ""}")
                val cardHead = SeerLayout.petCardHead(slotIndex + 1)
                    ?.let { runCatching { api.cropScreen(it) }.getOrNull() }
                val slot = SeerLayout.PET_SLOTS[slotIndex]
                api.click(slot); api.sleep(300)
                api.swipe(slot, Location(slot.x, slot.y - SeerLayout.PET_DEPLOY_UP_PX), 450)
                api.sleep(delays.afterSwitch); api.refreshScreen()
                val ok = verifyDeployed(cardHead, activeSwitch = !alreadyOpen)
                cardHead?.close()
                if (ok) return true
                api.logger.w("換精靈未通過驗證")
            }
        }
        api.logger.w("換精靈失敗（driver 放棄）")
        return false
    }

    /**
     * Did the switch complete? Uses a coordinate-free signal that differs by case,
     * so it survives the still-uncalibrated head coords:
     *  - 主動切換 (activeSwitch — pet was alive): success = the turn got consumed →
     *    「你的回合」(BATTLE_ACTION) disappears (換人後直接進 Boss 回合). The 換精靈
     *    sub-screen keeps 「你的回合」 visible, so it clears only once a pet actually
     *    takes the field; a missed tap/drag leaves the turn ours → retry. (The old
     *    "no PET_DEFEATED → always success" made a missed 主動切換 look successful, so
     *    the original pet stayed in and the sequence drifted — the 30–40% flakiness.)
     *  - 陣亡補位 (else): success = leaving the 換精靈 介面 (PET_DEFEATED gone).
     * Head-similarity is logged for diagnosis only (uncalibrated coords → a low score
     * means "coords unset", not "switch failed"; it must not drive pass/fail).
     */
    private fun verifyDeployed(cardHead: IPattern?, activeSwitch: Boolean): Boolean {
        logHeadSimilarity(cardHead)
        return if (activeSwitch) {
            api.waitUntil(SWITCH_VERIFY_MS, pollMs = 300) { !exists(SeerTemplates.BATTLE_ACTION) }
        } else {
            api.waitUntil(SWITCH_VERIFY_MS, pollMs = 300) { !exists(SeerTemplates.PET_DEFEATED) }
        }
    }

    /** Log 場上頭像 vs 目標卡頭像 similarity — for on-device coord calibration only. */
    private fun logHeadSimilarity(cardHead: IPattern?) {
        if (cardHead == null) return
        runCatching { api.cropScreen(SeerLayout.FIELD_HEAD) }.getOrNull()?.let { field ->
            val sim = api.similarity(cardHead, field)
            field.close()
            val mark = if (sim >= HEAD_MATCH_THRESHOLD) "≥門檻" else "<門檻(座標未校準?)"
            api.logger.i("換精靈頭像 similarity=${"%.2f".format(sim)} $mark（診斷）")
        }
    }

    private fun exists(id: String): Boolean = templates.has(id) && api.exists(templates.get(id))

    companion object {
        private const val STUCK_LIMIT = 80
        private const val TURN_SETTLE_MS = 12_000L
        private const val OUTCOME_POLLS = 6   // ~1.8s to read the win/lose diamond
        private const val SWITCH_ATTEMPTS = 3 // 換人「點卡→驗證」重試次數（同一目標）
        private const val SWITCH_VERIFY_MS = 5_000L // 換人後等成功訊號（你的回合/換精靈介面消失）
        // 頭像相似度門檻，目前僅供 verifyDeployed 的診斷 log 標記，**不主導換人成敗**
        // （實機 FIELD_HEAD/petCardHead 座標未校準時 similarity 偏低，若用來判失敗會誤判
        // →重試誤觸）。0.55 出自影片壓縮幀；實機校準座標後可重啟嚴格驗證。
        private const val HEAD_MATCH_THRESHOLD = 0.55
    }
}
