package com.autoseer.libautomata

/** Thrown to unwind a running script when the user stops it. */
class ScriptAbortException(message: String = "Script aborted") : Exception(message)

/**
 * The high-level automation surface scripts use. It ties together the screen
 * source, the image matcher and the gesture injector, and works entirely in the
 * normalized 720p coordinate space. Gesture coordinates are transformed to
 * device pixels here, so scripts never deal with real resolutions.
 */
class AutomataApi(
    private val screenshotProvider: IScreenshotProvider,
    private val matcher: IImageMatcher,
    private val gestures: IGestureService,
    val logger: Logger = Logger.NoOp,
    /** Returns false once the user asks the script to stop. */
    private val isRunning: () -> Boolean = { true },
) {
    companion object {
        const val DEFAULT_THRESHOLD = 0.8
    }

    private var currentScreen: IPattern? = null

    val normalizedSize: Size get() = screenshotProvider.normalizedSize()

    /** Full-screen region in normalized coordinates. */
    val fullRegion: Region get() = Region.of(normalizedSize)

    /** Grab a fresh screenshot, replacing the cached one. */
    fun refreshScreen(): IPattern {
        checkRunning()
        currentScreen?.close()
        val shot = screenshotProvider.takeScreenshot()
        currentScreen = shot
        return shot
    }

    private fun screen(): IPattern = currentScreen ?: refreshScreen()

    /** All places [template] is found within [region] (default: whole screen). */
    fun findAll(
        template: IPattern,
        region: Region? = null,
        threshold: Double = DEFAULT_THRESHOLD,
    ): List<Match> {
        checkRunning()
        val screen = screen()
        val searchRegion = region ?: Region.of(screen.size)
        return if (region == null) {
            matcher.match(screen, template, threshold)
        } else {
            screen.crop(searchRegion).use { cropped ->
                matcher.match(cropped, template, threshold).map { m ->
                    m.copy(region = m.region.copy(x = m.region.x + searchRegion.x, y = m.region.y + searchRegion.y))
                }
            }
        }
    }

    fun find(
        template: IPattern,
        region: Region? = null,
        threshold: Double = DEFAULT_THRESHOLD,
    ): Match? = findAll(template, region, threshold).maxByOrNull { it.score }

    fun exists(
        template: IPattern,
        region: Region? = null,
        threshold: Double = DEFAULT_THRESHOLD,
    ): Boolean = find(template, region, threshold) != null

    fun click(location: Location, durationMs: Long = 50) {
        checkRunning()
        val device = location.transform(normalizedSize, screenshotProvider.deviceSize())
        gestures.click(device, durationMs)
    }

    fun click(region: Region) = click(region.center)
    fun click(match: Match) = click(match.region.center)

    fun swipe(from: Location, to: Location, durationMs: Long = 300) {
        checkRunning()
        val device = screenshotProvider.deviceSize()
        gestures.swipe(from.transform(normalizedSize, device), to.transform(normalizedSize, device), durationMs)
    }

    /** Sleep [ms], remaining responsive to stop requests. */
    fun sleep(ms: Long) {
        val step = 50L
        var remaining = ms
        while (remaining > 0) {
            checkRunning()
            val slice = minOf(step, remaining)
            Thread.sleep(slice)
            remaining -= slice
        }
    }

    /**
     * Poll [condition] every [pollMs] until it is true or [timeoutMs] elapses.
     * Refreshes the screen before each poll. Returns true if it became true.
     */
    fun waitUntil(timeoutMs: Long, pollMs: Long = 500, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            refreshScreen()
            if (condition()) return true
            sleep(pollMs)
        }
        return false
    }

    private fun checkRunning() {
        if (!isRunning()) throw ScriptAbortException()
    }
}
