package com.autoseer.scripts

/**
 * Discrete UI states of 《賽爾號：巔峰之戰》 the automation reasons about.
 * [templateId] is the asset used to detect the state (empty = not template-based).
 */
enum class GameState(val templateId: String) {
    UNKNOWN(""),

    /** Stage-select map: a node is challengeable and 「進入戰鬥」 is shown. */
    STAGE_ENTRY(SeerTemplates.ENTER_BATTLE),

    /** In battle, it's the player's turn (「你的回合」). */
    BATTLE_ACTION(SeerTemplates.BATTLE_ACTION),

    /** Victory screen (「勝利」). */
    RESULT_WIN(SeerTemplates.RESULT_WIN),

    /** Out of daily challenge attempts / stamina. */
    OUT_OF_STAMINA(SeerTemplates.OUT_OF_STAMINA),
}
