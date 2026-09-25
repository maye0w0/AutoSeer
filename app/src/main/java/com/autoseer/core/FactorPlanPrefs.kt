package com.autoseer.core

import android.content.Context
import org.json.JSONArray

/**
 * Persists the 因子關卡排序 plan: an ordered list of selected image display-names
 * (with extension — the stable per-folder key SAF gives each file). The order IS
 * the sweep/fight order; a name not in the list is a stage the user chose not to
 * fight. Stored as a JSON array so display names with commas/spaces survive.
 *
 * "No key present" (never saved) is distinct from "saved empty list": [getOrder]
 * returns null for the former so [FactorPlan] can fall back to the open-box
 * behavior (fight everything by file name).
 */
object FactorPlanPrefs {
    private const val PREFS = "factor_plan_prefs"
    private const val KEY_ORDER = "order_json"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ordered selected display-names, or null if the user has never saved a plan. */
    fun getOrder(ctx: Context): List<String>? {
        val s = prefs(ctx).getString(KEY_ORDER, null) ?: return null
        return runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrNull()
    }

    fun setOrder(ctx: Context, names: List<String>) {
        val arr = JSONArray().apply { names.forEach { put(it) } }
        prefs(ctx).edit().putString(KEY_ORDER, arr.toString()).apply()
    }

    fun hasPlan(ctx: Context): Boolean = prefs(ctx).contains(KEY_ORDER)
}
