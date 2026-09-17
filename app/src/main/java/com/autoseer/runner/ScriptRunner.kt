package com.autoseer.runner

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.IGestureService
import com.autoseer.libautomata.IImageMatcher
import com.autoseer.libautomata.IScreenshotProvider
import com.autoseer.libautomata.Logger
import com.autoseer.libautomata.Script
import com.autoseer.libautomata.ScriptAbortException

/**
 * Runs one [Script] at a time on a background thread. Wires an [AutomataApi]
 * from the platform pieces and exposes a stop flag the script polls via the API.
 */
class ScriptRunner(
    private val screenshotProvider: IScreenshotProvider,
    private val matcher: IImageMatcher,
    private val gestures: IGestureService,
    private val logger: Logger,
    private val onStateChange: (running: Boolean) -> Unit = {},
) {
    @Volatile
    var isRunning: Boolean = false
        private set

    private var thread: Thread? = null

    /** Build the API and start [scriptFactory]'s script. No-op if already running. */
    fun start(scriptFactory: (AutomataApi) -> Script) {
        if (isRunning) return
        isRunning = true
        onStateChange(true)
        val api = AutomataApi(
            screenshotProvider = screenshotProvider,
            matcher = matcher,
            gestures = gestures,
            logger = logger,
            isRunning = { isRunning },
        )
        val script = scriptFactory(api)
        thread = Thread({
            try {
                logger.i("啟動腳本：${script.name}")
                script.run()
                logger.i("腳本結束：${script.name}")
            } catch (e: ScriptAbortException) {
                logger.i("腳本已停止：${script.name}")
            } catch (e: InterruptedException) {
                logger.i("腳本已停止：${script.name}")
            } catch (e: Throwable) {
                logger.e("腳本錯誤：${e.message}", e)
            } finally {
                isRunning = false
                onStateChange(false)
            }
        }, "AutoSeerScript").also { it.start() }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        thread?.interrupt()
        thread = null
        onStateChange(false)
    }
}
