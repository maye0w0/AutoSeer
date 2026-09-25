package com.autoseer.core

import android.content.Context
import android.util.Log
import com.autoseer.scripts.FactorTarget

/**
 * Resolves the ordered set of 因子 the sweep should fight, from the 因子關卡排序 plan
 * ([FactorPlanPrefs]) reconciled against what is actually in the 精靈圖庫 folder now.
 *
 * Rules (open-box safe):
 *  - Saved plan present → keep only names still in the folder, in the saved order.
 *  - No saved plan, OR the reconciled result is empty (e.g. the folder was switched
 *    and the old names are gone) → fall back to every image, in file-name order.
 *
 * This only feeds the "選擇/進入" navigation module; the battle module is untouched.
 */
object FactorPlan {
    private const val TAG = "AutoSeer"

    fun forSweep(ctx: Context): List<FactorTarget> {
        val all = FactorLibrary.list(ctx)
        val order = FactorPlanPrefs.getOrder(ctx)
        val ordered = if (order != null) {
            val byName = all.associateBy { it.displayName }
            order.mapNotNull { byName[it] }
        } else emptyList()

        val chosen = if (ordered.isNotEmpty()) ordered else all
        if (order != null && ordered.isEmpty() && all.isNotEmpty()) {
            Log.i(TAG, "因子排序計畫與圖庫對不上（可能換過資料夾/未選任何項），退回全部依檔名")
        }
        return FactorLibrary.loadTargets(ctx, chosen)
    }
}
