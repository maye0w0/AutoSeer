package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.Templates

/**
 * Looks at the current (already-refreshed) screen and decides which [GameState]
 * we're in, trying each state's template in priority order. States whose
 * template asset isn't captured yet are skipped (treated as not-present).
 */
class GameStateDetector(
    private val api: AutomataApi,
    private val templates: Templates,
) {
    private val priority = listOf(
        GameState.OUT_OF_STAMINA,
        GameState.RESULT_WIN,
        GameState.BATTLE_ACTION,
        GameState.STAGE_ENTRY,
    )

    fun detect(): GameState {
        for (state in priority) {
            val id = state.templateId
            if (id.isEmpty() || !templates.has(id)) continue
            if (api.exists(templates.get(id))) {
                api.logger.d("Detected state: $state")
                return state
            }
        }
        return GameState.UNKNOWN
    }
}
