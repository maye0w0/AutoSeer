package com.autoseer.core

import android.content.Context

/** Persists the user's automation settings (skill plan + options) in SharedPreferences. */
object SeerPrefs {
    private const val FILE = "autoseer"
    private const val KEY_PLAN = "plan_text"
    private const val KEY_MAX = "max_battles"
    private const val KEY_HEAL = "heal"
    private const val KEY_ADVANCE = "advance"
    private const val KEY_DEFAULT_SLOT = "default_slot"
    private const val KEY_START_STAGE = "start_stage"
    private const val KEY_MAX_RETRIES = "max_retries"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun planText(ctx: Context): String = prefs(ctx).getString(KEY_PLAN, "") ?: ""
    fun maxBattles(ctx: Context): Int = prefs(ctx).getInt(KEY_MAX, 30)
    fun healBeforeBattle(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HEAL, true)
    fun advanceMap(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ADVANCE, true)
    fun defaultSlot(ctx: Context): Int = prefs(ctx).getInt(KEY_DEFAULT_SLOT, 2)
    fun startStage(ctx: Context): Int = prefs(ctx).getInt(KEY_START_STAGE, 1)
    fun maxRetries(ctx: Context): Int = prefs(ctx).getInt(KEY_MAX_RETRIES, 5)

    fun save(
        ctx: Context,
        planText: String,
        maxBattles: Int,
        healBeforeBattle: Boolean,
        advanceMap: Boolean,
        defaultSlot: Int,
        startStage: Int,
        maxRetries: Int,
    ) {
        prefs(ctx).edit()
            .putString(KEY_PLAN, planText)
            .putInt(KEY_MAX, maxBattles)
            .putBoolean(KEY_HEAL, healBeforeBattle)
            .putBoolean(KEY_ADVANCE, advanceMap)
            .putInt(KEY_DEFAULT_SLOT, defaultSlot)
            .putInt(KEY_START_STAGE, startStage)
            .putInt(KEY_MAX_RETRIES, maxRetries)
            .apply()
    }
}
