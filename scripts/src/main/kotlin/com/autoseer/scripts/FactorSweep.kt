package com.autoseer.scripts

import com.autoseer.libautomata.Logger

/**
 * The 銜接 hook [SeerFactorScript] consults when a factor's daily count is spent.
 * Deliberately tiny: the stable battle loop only ever asks "enter the first
 * factor?" and "go to the next factor?" — it never learns how navigation works.
 * A null [FactorSweep] means single-factor mode (legacy: stop / back to lobby).
 */
interface FactorSweep {
    /** Navigate into the first target factor's 詳情頁. false = couldn't start. */
    fun enterFirst(): Boolean

    /** Move to the next target's 詳情頁. false = list finished or navigation failed. */
    fun advanceToNext(): Boolean
}

/**
 * Drives a [FactorNavigator] over a [FactorSweepProgress] list. This is the
 * selection/entry module the user asked to keep separate from the battle module:
 * it only takes control between factors (before the first, and at each 達到每天操作
 * 上限), lands on the next factor's 詳情頁, then hands control back to the battle
 * loop — it does not touch battle/recover/switch logic at all.
 */
class FactorSweepRunner(
    private val nav: FactorNavigator,
    private val progress: FactorSweepProgress,
    private val logger: Logger,
) : FactorSweep {

    override fun enterFirst(): Boolean {
        val t = progress.current() ?: run { logger.w("因子清單為空，無法開始銜接"); return false }
        logger.i("銜接：共 ${progress.size} 個指定因子，先前往「${t.name}」")
        return enter(t)
    }

    override fun advanceToNext(): Boolean {
        val t = progress.advance() ?: run { logger.i("銜接：指定因子已全部打完"); return false }
        logger.i("銜接：前往下一個因子「${t.name}」（${progress.index + 1}/${progress.size}）")
        return enter(t)
    }

    private fun enter(t: FactorTarget): Boolean {
        if (!nav.backToGrid()) { logger.w("銜接：回不到因子選擇格"); return false }
        val ok = nav.findAndTapFactor(t)
        if (!ok) logger.w("銜接：在選擇格找不到因子「${t.name}」（比對不到或已捲到底）")
        return ok
    }
}
