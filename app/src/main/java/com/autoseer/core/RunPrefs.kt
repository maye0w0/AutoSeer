package com.autoseer.core

import android.content.Context

/**
 * Global execution options (shared by all scripts), moved out of each script per
 * the finalized UI: 循環輪數、預設技能格、戰前恢復. Delays live in [DelayPrefs].
 */
object RunPrefs {
    private const val FILE = "autoseer_run"
    private const val KEY_LOOPS = "loops"
    private const val KEY_DEFAULT_SLOT = "default_slot"
    private const val KEY_HEAL = "heal_before_battle"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loops(ctx: Context): Int = prefs(ctx).getInt(KEY_LOOPS, 3)          // 精靈因子=3
    fun defaultSlot(ctx: Context): Int = prefs(ctx).getInt(KEY_DEFAULT_SLOT, 2)
    fun healBeforeBattle(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HEAL, true)

    fun save(ctx: Context, loops: Int, defaultSlot: Int, healBeforeBattle: Boolean) {
        prefs(ctx).edit()
            .putInt(KEY_LOOPS, loops.coerceAtLeast(1))
            .putInt(KEY_DEFAULT_SLOT, defaultSlot.coerceIn(1, 5))
            .putBoolean(KEY_HEAL, healBeforeBattle)
            .apply()
    }
}
