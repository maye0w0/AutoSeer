package com.autoseer.scripts

import com.autoseer.libautomata.IPattern

/**
 * One factor stage the sweep should play, identified by a reference card image
 * ([pattern] — a 因子卡 crop the player captured into their 精靈圖庫). [name] is
 * display-only (the library file name). The pattern is compared against the cards
 * on the 因子關卡 selection grid to locate and tap the right factor.
 */
data class FactorTarget(val name: String, val pattern: IPattern)
