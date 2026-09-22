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
}
