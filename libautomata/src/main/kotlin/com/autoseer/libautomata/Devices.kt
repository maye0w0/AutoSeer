package com.autoseer.libautomata

/** Supplies screenshots already normalized into the 720p working space. */
interface IScreenshotProvider {
    /** Grab the current screen, normalized (grayscale, scaled to [normalizedSize]). */
    fun takeScreenshot(): IPattern

    /** Size of the normalized working space (height == 720 by convention). */
    fun normalizedSize(): Size

    /** Actual device/emulator screen size in pixels. */
    fun deviceSize(): Size
}

/** Injects touch input. Coordinates passed here are in *device* pixels. */
interface IGestureService {
    fun click(location: Location, durationMs: Long = 50)
    fun swipe(from: Location, to: Location, durationMs: Long = 300)
}

/** Matches a [template] within an [image]; both share a coordinate space. */
interface IImageMatcher {
    /**
     * @return every location where [template] appears in [image] with a score
     *   >= [threshold] (0..1), best first.
     */
    fun match(image: IPattern, template: IPattern, threshold: Double): List<Match>
}
