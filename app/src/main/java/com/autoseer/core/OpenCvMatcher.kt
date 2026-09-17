package com.autoseer.core

import com.autoseer.libautomata.IImageMatcher
import com.autoseer.libautomata.IPattern
import com.autoseer.libautomata.Match
import com.autoseer.libautomata.Region
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc

/**
 * Template matching via OpenCV `matchTemplate` (TM_CCOEFF_NORMED). Repeatedly
 * takes the best remaining peak above [threshold], blanking a window around
 * each hit so overlapping detections aren't double-counted.
 */
class OpenCvMatcher(
    private val maxMatches: Int = 8,
) : IImageMatcher {

    override fun match(image: IPattern, template: IPattern, threshold: Double): List<Match> {
        val img = (image as OpenCvPattern).mat
        val tmpl = (template as OpenCvPattern).mat
        if (tmpl.cols() > img.cols() || tmpl.rows() > img.rows()) return emptyList()

        val result = Mat()
        Imgproc.matchTemplate(img, tmpl, result, Imgproc.TM_CCOEFF_NORMED)

        val matches = ArrayList<Match>()
        try {
            repeat(maxMatches) {
                val mm = Core.minMaxLoc(result)
                val score = mm.maxVal
                if (score < threshold) return@repeat
                val loc: Point = mm.maxLoc
                matches += Match(
                    region = Region(loc.x.toInt(), loc.y.toInt(), tmpl.cols(), tmpl.rows()),
                    score = score,
                )
                blankAround(result, loc, tmpl.cols(), tmpl.rows())
            }
        } finally {
            result.release()
        }
        return matches.sortedByDescending { it.score }
    }

    /** Zero out a region around a found peak so the next minMaxLoc finds a different one. */
    private fun blankAround(result: Mat, loc: Point, w: Int, h: Int) {
        val x0 = (loc.x - w / 2).toInt().coerceIn(0, result.cols() - 1)
        val y0 = (loc.y - h / 2).toInt().coerceIn(0, result.rows() - 1)
        val x1 = (loc.x + w / 2).toInt().coerceIn(0, result.cols() - 1)
        val y1 = (loc.y + h / 2).toInt().coerceIn(0, result.rows() - 1)
        for (yy in y0..y1) {
            for (xx in x0..x1) {
                result.put(yy, xx, 0.0)
            }
        }
    }
}
