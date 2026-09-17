package com.autoseer.libautomata

/** Minimal logging abstraction so `libautomata` doesn't depend on android.util.Log. */
interface Logger {
    fun d(message: String)
    fun i(message: String)
    fun w(message: String, error: Throwable? = null)
    fun e(message: String, error: Throwable? = null)

    /** No-op logger for tests. */
    object NoOp : Logger {
        override fun d(message: String) {}
        override fun i(message: String) {}
        override fun w(message: String, error: Throwable?) {}
        override fun e(message: String, error: Throwable?) {}
    }
}
