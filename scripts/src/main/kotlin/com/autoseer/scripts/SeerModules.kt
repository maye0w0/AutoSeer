package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.Location
import com.autoseer.libautomata.Templates

/**
 * Reusable vision building-blocks for mission scripts (精靈因子、每日任務…).
 * Everything works full-screen: `matchTemplate` locates the target wherever it
 * is and taps the found position, so buttons need no fixed coordinates. These
 * are the "通用判斷" modules the user asked to factor out for reuse across stages.
 */
class SeerModules(
    private val api: AutomataApi,
    private val templates: Templates,
) {
    /** Template present on screen right now (screen must be refreshed by caller). */
    fun exists(id: String): Boolean = templates.has(id) && api.exists(templates.get(id))

    /** Best match for [id] on the current screen, or null. */
    private fun find(id: String) = if (templates.has(id)) api.find(templates.get(id)) else null

    /** Tap [id] where it is found right now. Returns false if not present. */
    fun tapIfPresent(id: String): Boolean {
        val m = find(id) ?: return false
        api.click(m)
        return true
    }

    /** Refresh + poll until [id] appears (or timeout). */
    fun waitAppear(id: String, timeoutMs: Long, pollMs: Long = 400): Boolean =
        templates.has(id) && api.waitUntil(timeoutMs, pollMs) { api.exists(templates.get(id)) }

    /** Wait until any of [ids] appears; returns the one that hit, or null on timeout. */
    fun waitAppearAny(ids: List<String>, timeoutMs: Long, pollMs: Long = 400): String? {
        val known = ids.filter { templates.has(it) }
        if (known.isEmpty()) return null
        var hit: String? = null
        api.waitUntil(timeoutMs, pollMs) {
            hit = known.firstOrNull { api.exists(templates.get(it)) }
            hit != null
        }
        return hit
    }

    /** Wait for [id] to appear then tap it where found. Returns true if tapped. */
    fun waitAndTap(id: String, timeoutMs: Long): Boolean {
        if (!waitAppear(id, timeoutMs)) return false
        api.refreshScreen()
        return tapIfPresent(id)
    }

    /** If [id] is showing now, tap the given fixed [point] (e.g. a 確認 button). */
    fun tapFixedIfPresent(id: String, point: Location): Boolean {
        if (!exists(id)) return false
        api.click(point)
        return true
    }

    /**
     * 「撤退」通用判斷 (PPT s14–18): 撤退 → 等「你確定要撤退嗎」→ 確認 → 等「恭喜你，
     * 成功撤退」→ 確認. Template-gated (fixed 確認 coords from [SeerLayout]) instead
     * of the old blind paced-tap loop, so it stops the moment each dialog is seen.
     * If 撤退 is greyed out because the on-field pet is 已戰敗, deploys one first.
     * Returns true once the retreat dialogs are cleared.
     */
    fun retreat(delays: BattleDelays): Boolean {
        api.logger.i("嘗試撤退…")
        repeat(RETREAT_ATTEMPTS) {
            api.refreshScreen()
            if (exists(SeerTemplates.PET_DEFEATED)) deployAnyPet(delays)
            api.click(SeerLayout.RETREAT.center)                 // 撤退 (code 9)
            api.sleep(delays.afterRetreatTap)
            if (waitAppear(SeerTemplates.RETREAT_TIP, RETREAT_WAIT_MS)) {
                api.click(SeerLayout.RETREAT_CONFIRM)            // 你確定要撤退嗎 → 確認
                api.sleep(delays.afterRetreatTap)
                if (waitAppear(SeerTemplates.RETREAT_SUCCESS, RETREAT_WAIT_MS)) {
                    api.click(SeerLayout.RETREAT_SUCCESS_CONFIRM) // 恭喜你，成功撤退 → 確認
                    api.sleep(delays.afterRetreatTap)
                }
                return true
            }
        }
        api.logger.w("撤退未成功（撤退視窗未出現）")
        return false
    }

    /** Deploy an alive pet to un-grey 撤退 / unstick, when the field pet is 已戰敗. */
    private fun deployAnyPet(delays: BattleDelays) {
        var tries = 0
        while (exists(SeerTemplates.PET_DEFEATED) && tries < SeerLayout.PET_COUNT) {
            val slot = SeerLayout.PET_SLOTS.getOrNull(tries) ?: break
            api.click(SeerLayout.PET.center); api.sleep(500); api.refreshScreen()
            api.click(slot); api.sleep(300)
            api.swipe(slot, Location(slot.x, slot.y - SeerLayout.PET_DEPLOY_UP_PX), 450)
            api.sleep(delays.afterSwitch); api.refreshScreen()
            tries++
        }
    }

    companion object {
        private const val RETREAT_ATTEMPTS = 3
        private const val RETREAT_WAIT_MS = 6_000L
    }
}
