package com.autoseer.scripts

import com.autoseer.libautomata.AutomataApi
import com.autoseer.libautomata.Location
import com.autoseer.libautomata.Script

/**
 * Phase-1 verification script: proves the capture -> (recognition) -> input
 * pipeline works end-to-end WITHOUT needing any template assets.
 *
 * Each iteration it grabs a screenshot, logs the normalized size (confirming
 * capture works), and taps the center of the screen (confirming gesture
 * injection works). Runs [iterations] times then stops.
 */
class TestPipelineScript(
    api: AutomataApi,
    private val iterations: Int = 5,
) : Script(api) {

    override val name = "管線測試 (TestPipelineScript)"

    override fun run() {
        api.logger.i("TestPipelineScript 開始：驗證截圖與點擊管線。")
        repeat(iterations) { i ->
            val screen = api.refreshScreen()
            api.logger.i("第 ${i + 1} 次：截圖成功，normalized=${screen.width}x${screen.height}")
            val center = Location(screen.width / 2, screen.height / 2)
            api.logger.i("點擊畫面中央 (normalized) $center")
            api.click(center)
            api.sleep(1000)
        }
        api.logger.i("TestPipelineScript 完成。")
    }
}
