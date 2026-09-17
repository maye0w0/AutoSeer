package com.autoseer.libautomata

/**
 * Supplies named template images (loaded from bundled assets by the platform
 * layer). Scripts reference templates by id so they stay platform-agnostic.
 */
interface Templates {
    /** Load the template registered under [id] (e.g. "battle/attack_button"). */
    fun get(id: String): IPattern

    /** Whether a template [id] is available. */
    fun has(id: String): Boolean
}
