package com.autoseer.scripts

/**
 * Pure sweep bookkeeping for [SeerFactorScript]: which stage to fight next, and
 * what to do after a win / loss. Kept free of any screen/Android dependency so
 * the decision logic that actually had bugs (win→advance, lose→retry-same-stage,
 * retry-limit→stop) is unit-testable without a device.
 */
class SweepProgress(
    private val plan: BattlePlan,
    private val backToLobbyOnExhaust: Boolean = false,
) {
    /** What the caller should do after a loss/retreat. */
    enum class LoseAction { RETRY, STOP_TO_LOBBY, STOP_HERE }

    /** Stages cleared so far this run. */
    var battlesDone = 0
        private set

    /** Consecutive failures on the current stage (reset on a win). */
    var retriesThisStage = 0
        private set

    /** 0-based plan index of the stage to fight now (wraps within a loop, FR-1). */
    fun currentStageIndex(): Int = plan.stageIndexFor(battlesDone)

    /** Won the current stage: advance to the next, reset per-stage retries. */
    fun onWin() {
        battlesDone++
        retriesThisStage = 0
    }

    /**
     * Lost or retreated: count a retry and decide. RETRY = re-fight the same
     * stage (battlesDone unchanged); STOP_* = give up after exceeding the
     * per-stage retry limit ([BattlePlan.maxRetriesPerStage]; <=0 = unlimited),
     * to lobby or in place depending on [backToLobbyOnExhaust].
     */
    fun onLose(): LoseAction {
        retriesThisStage++
        val limit = plan.maxRetriesPerStage
        if (limit > 0 && retriesThisStage > limit) {
            return if (backToLobbyOnExhaust) LoseAction.STOP_TO_LOBBY else LoseAction.STOP_HERE
        }
        return LoseAction.RETRY
    }
}
