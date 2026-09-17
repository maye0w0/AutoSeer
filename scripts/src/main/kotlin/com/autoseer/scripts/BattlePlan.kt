package com.autoseer.scripts

/**
 * One step in a stage's plan.
 *
 * @param code  1..5 = skill, 6..9 = 戰鬥/道具/精靈/撤退, 11..16 = 換精靈1..6
 * @param untilDefeat  true = 「*N」：repeat this cast every turn until the active
 *   pet is defeated (已戰敗) or the battle is won. Only meaningful for casts.
 */
data class Step(val code: Int, val untilDefeat: Boolean = false) {
    val isSwitch: Boolean get() = SeerLayout.isPetCode(code)
    /** Pet index 1..6 when [isSwitch]. */
    val petIndex: Int get() = code - SeerLayout.PET_CODE_BASE
}

/** Ordered steps for one stage (關卡). Empty = use the plan's default cast. */
data class StagePlan(val steps: List<Step> = emptyList())

/**
 * The whole automation plan: per-stage step sequences plus global options.
 * [stages] is indexed by stage number as we progress (0 = first stage this run).
 */
data class BattlePlan(
    /** Stop after this many stages cleared. <=0 means "until stamina out". */
    val maxBattles: Int = 30,
    /** Press 「精靈恢復」 before every battle. */
    val healBeforeBattle: Boolean = true,
    /** Select the next 「等待挑戰」 node before entering (沿地圖推進). */
    val advanceMap: Boolean = true,
    val stages: List<StagePlan> = emptyList(),
    /** Skill used for stages with no explicit plan (spammed until win). */
    val defaultCode: Int = 2,
    /** How many times to retreat-and-retry a stage before giving up. */
    val maxRetriesPerStage: Int = 5,
    /** 1-based stage to start from (resume mid-run); plan index = startStage-1 + cleared. */
    val startStage: Int = 1,
) {
    fun forStage(index: Int): StagePlan = stages.getOrNull(index) ?: StagePlan()

    companion object {
        fun default() = BattlePlan()
    }
}
