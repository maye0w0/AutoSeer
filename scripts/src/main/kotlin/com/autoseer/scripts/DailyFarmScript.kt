package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.Script
import com.autoseer.libautomata.Templates

/**
 * Thin wrapper that runs [BattleScript] with a given [plan]. All battle/map
 * logic lives in [BattleScript]; this just names the run.
 */
class DailyFarmScript(
    api: AutomataApi,
    private val templates: Templates,
    private val plan: BattlePlan = BattlePlan.default(),
) : Script(api) {

    override val name = "每日刷取 (DailyFarmScript)"

    override fun run() {
        api.logger.i("DailyFarmScript 開始，目標 ${plan.maxBattles} 關")
        val battle = BattleScript(api, templates, plan)
        battle.run()
        api.logger.i("DailyFarmScript 結束，實際清 ${battle.battlesDone} 關。")
    }
}
