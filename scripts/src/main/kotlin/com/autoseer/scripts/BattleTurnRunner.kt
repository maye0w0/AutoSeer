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
     * Verification is the point. The old code tapped a fixed slot and moved on, so
     * a mis-tap / failed drag left PET_DEFEATED up → the loop re-entered
     * onPetDefeated → stepIndex ran onto a cast → deployAnyPet blind-tapped the
     * 精靈1 slot, which overlaps the 招牌技 圓鈕. Here we remember the target card's
     * head, drag it out, then require the on-field head to match; on failure we
     * retry the SAME target rather than fall through to a blind tap.
     */
    private fun deployPet(target: Int?, alreadyOpen: Boolean): Boolean {
        val slots: List<Int> = when (target) {
            null -> (0 until SeerLayout.PET_COUNT).toList()              // fallback: try each
            else -> listOf(target - 1).filter { it in 0 until SeerLayout.PET_COUNT }
        }
        if (slots.isEmpty()) return false
        val attemptsPerSlot = if (target != null) SWITCH_ATTEMPTS else 1
        for (slotIndex in slots) {
            repeat(attemptsPerSlot) { attempt ->
                // Ensure the 換精靈 sub-screen is open: 主動切換 always opens it; after a
                // failed try we re-open before retrying.
                if (!alreadyOpen || attempt > 0) {
                    api.click(SeerLayout.PET.center); api.sleep(delays.afterSwitch); api.refreshScreen()
                }
                api.logger.i("換精靈 ${slotIndex + 1}${if (target == null) "（補位）" else ""}")
                val cardHead = SeerLayout.petCardHead(slotIndex + 1)
                    ?.let { runCatching { api.cropScreen(it) }.getOrNull() }
                val slot = SeerLayout.PET_SLOTS[slotIndex]
                api.click(slot); api.sleep(300)
                api.swipe(slot, Location(slot.x, slot.y - SeerLayout.PET_DEPLOY_UP_PX), 450)
                api.sleep(delays.afterSwitch); api.refreshScreen()
                val ok = verifyDeployed(cardHead)
                cardHead?.close()
                if (ok) return true
                api.logger.w("換精靈未通過驗證")
            }
        }
        api.logger.w("換精靈失敗（driver 放棄）")
        return false
    }

    /**
     * Did a pet actually take the field? Minimum evidence: we've left the 換精靈
     * 介面 (PET_DEFEATED gone). If the target card's head was captured, also require
     * the on-field head to match it (so a mis-tap onto the wrong / greyed card is
     * caught). No head crop (region unset) → trust the PET_DEFEATED check alone.
     */
    private fun verifyDeployed(cardHead: IPattern?): Boolean {
        if (exists(SeerTemplates.PET_DEFEATED)) return false
        if (cardHead == null) return true
        val field = runCatching { api.cropScreen(SeerLayout.FIELD_HEAD) }.getOrNull() ?: return true
        val sim = api.similarity(cardHead, field)
        field.close()
        api.logger.i("換精靈驗證 similarity=${"%.2f".format(sim)}")
        return sim >= HEAD_MATCH_THRESHOLD
    }

    private fun exists(id: String): Boolean = templates.has(id) && api.exists(templates.get(id))

    companion object {
        private const val STUCK_LIMIT = 80
        private const val TURN_SETTLE_MS = 12_000L
        private const val OUTCOME_POLLS = 6   // ~1.8s to read the win/lose diamond
        private const val SWITCH_ATTEMPTS = 3 // 換人「點卡→驗證」重試次數（同一目標）
        // 場上頭像 vs 換精靈卡頭像 的相似度門檻。0.55 出自影片壓縮幀實測（正確 0.78 /
        // 已戰敗卡 0.51），偏寬鬆以免誤判失敗；**須以實機截圖校準**。
        private const val HEAD_MATCH_THRESHOLD = 0.55
    }
}
