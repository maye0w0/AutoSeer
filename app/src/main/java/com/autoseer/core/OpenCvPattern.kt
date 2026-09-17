package com.autoseer.core

import com.autoseer.libautomata.IPattern
import com.autoseer.libautomata.Region
import com.autoseer.libautomata.Size
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * [IPattern] backed by an OpenCV [Mat]. Screenshots and templates are stored as
 * single-channel (grayscale) mats in the normalized 720p space.
 */
class OpenCvPattern(val mat: Mat) : IPattern {
    override val width: Int get() = mat.cols()
    override val height: Int get() = mat.rows()

    override fun crop(region: Region): IPattern {
        val safe = clampRegion(region)
        val sub = Mat(mat, Rect(safe.x, safe.y, safe.width, safe.height))
        // Rect Mat shares data with parent; clone so callers can close independently.
        return OpenCvPattern(sub.clone())
    }

    override fun resize(size: Size): IPattern {
        val dst = Mat()
        Imgproc.resize(mat, dst, org.opencv.core.Size(size.width.toDouble(), size.height.toDouble()))
        return OpenCvPattern(dst)
    }

    override fun save(path: String) {
        Imgcodecs.imwrite(path, mat)
    }

    override fun close() {
        mat.release()
    }

    private fun clampRegion(r: Region): Region {
        val x = r.x.coerceIn(0, maxOf(0, width - 1))
        val y = r.y.coerceIn(0, maxOf(0, height - 1))
        val w = r.width.coerceIn(1, width - x)
        val h = r.height.coerceIn(1, height - y)
        return Region(x, y, w, h)
    }
}
