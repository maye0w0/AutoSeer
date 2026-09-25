package com.autoseer.core

import android.content.Context

/**
 * Global execution options (shared by all scripts) per the finalized UI:
 * 起始關卡、預設技能格、戰前恢復. Delays live in [DelayPrefs].
 *
 * 清關場數上限與循環輪數已移除——精靈因子改由畫面「達到每天操作上限」字樣自然結束。
 */
object RunPrefs {
    private const val FILE = "autoseer_run"
    private const val KEY_START_STAGE = "start_stage"
    private const val KEY_DEFAULT_SLOT = "default_slot"
    private const val KEY_HEAL = "heal_before_battle"
    private const val KEY_LOBBY_ON_EXHAUST = "lobby_on_retry_exhausted"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 1-based stage to start from (中途續跑用；第 1 關填 1). */
    fun startStage(ctx: Context): Int = prefs(ctx).getInt(KEY_START_STAGE, 1)
    fun defaultSlot(ctx: Context): Int = prefs(ctx).getInt(KEY_DEFAULT_SLOT, 2)
    fun healBeforeBattle(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HEAL, true)

    /** 失敗達每關重試上限後是否回大廳。預設 false＝待在原本的地方（原地停）。 */
    fun backToLobbyOnRetryExhausted(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_LOBBY_ON_EXHAUST, false)

    fun save(
        ctx: Context,
        startStage: Int,
        defaultSlot: Int,
        healBeforeBattle: Boolean,
        backToLobbyOnRetryExhausted: Boolean,
    ) {
        prefs(ctx).edit()
            .putInt(KEY_START_STAGE, startStage.coerceAtLeast(1))
            .putInt(KEY_DEFAULT_SLOT, defaultSlot.coerceIn(1, 5))
            .putBoolean(KEY_HEAL, healBeforeBattle)
            .putBoolean(KEY_LOBBY_ON_EXHAUST, backToLobbyOnRetryExhausted)
            .apply()
    }
}
