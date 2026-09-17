package com.autoseer.core

import android.util.Log
import com.autoseer.libautomata.Logger

/**
 * Bridges [Logger] to Android's Logcat, and forwards each line to an optional
 * [onLine] sink so the overlay can show recent status.
 */
class AndroidLogger(
    private val tag: String = "AutoSeer",
    private val onLine: ((String) -> Unit)? = null,
) : Logger {
    override fun d(message: String) { Log.d(tag, message); onLine?.invoke(message) }
    override fun i(message: String) { Log.i(tag, message); onLine?.invoke(message) }
    override fun w(message: String, error: Throwable?) { Log.w(tag, message, error); onLine?.invoke(message) }
    override fun e(message: String, error: Throwable?) { Log.e(tag, message, error); onLine?.invoke(message) }
}
