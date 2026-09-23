package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
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
                    switchPet(step.petIndex, alreadyOpen = false)
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

    /** A pet is 已戰敗. Advance a *N step or do a pending Switch. False = plan exhausted. */
    private fun onPetDefeated(steps: List<Step>): Boolean {
        steps.getOrNull(stepIndex)?.let { if (it.untilDefeat) stepIndex++ }
        val next = steps.getOrNull(stepIndex)
        when {
            next == null && steps.isNotEmpty() -> return false
            next != null && next.isSwitch -> { switchPet(next.petIndex, alreadyOpen = true); stepIndex++ }
            else -> deployAnyPet()
        }
        return true
    }

    private fun cast(code: Int) {
        api.logger.i("施放：${SeerLayout.labelFor(code)}")
        api.click(SeerLayout.pointFor(code) ?: SeerLayout.SKILL_SLOTS[0])
        api.sleep(delays.afterSkill)
    }

    private fun switchPet(n: Int, alreadyOpen: Boolean) {
        val slot = SeerLayout.PET_SLOTS.getOrNull(n - 1) ?: return
        api.logger.i("換精靈 $n")
        if (!alreadyOpen) { api.click(SeerLayout.PET.center); api.sleep(700); api.refreshScreen() }
        api.click(slot)
        api.sleep(300)
        api.swipe(slot, Location(slot.x, slot.y - SeerLayout.PET_DEPLOY_UP_PX), 450)
        api.sleep(delays.afterSwitch)
    }

    private fun deployAnyPet() {
        var tries = 0
        while (exists(SeerTemplates.PET_DEFEATED) && tries < SeerLayout.PET_COUNT) {
            switchPet(tries + 1, alreadyOpen = true)
            api.refreshScreen()
            tries++
        }
    }

    private fun exists(id: String): Boolean = templates.has(id) && api.exists(templates.get(id))

    companion object {
        private const val STUCK_LIMIT = 80
        private const val TURN_SETTLE_MS = 12_000L
        private const val OUTCOME_POLLS = 6   // ~1.8s to read the win/lose diamond
    }
}
