package com.autoseer.libautomata

/**
 * Base class for an automation script. A script sees the game through [api]
 * and drives it via the same. Implementations put their loop in [run].
 */
abstract class Script(protected val api: AutomataApi) {
    /** Human-readable name for logs / UI. */
    abstract val name: String

    /** Entry point. Should loop until done or [ScriptAbortException] is thrown. */
    abstract fun run()
}
