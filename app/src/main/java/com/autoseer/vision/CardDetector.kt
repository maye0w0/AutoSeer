package com.autoseer.vision

import android.graphics.Bitmap
import android.graphics.RectF
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Finds candidate 精靈圖像卡 rectangles on a captured 精靈因子/圖鑑 screen.
 *
 * The grid is a regular row of portrait cards (blue frame + art on top, a name
 * plate below). We Canny-edge the frame, close small gaps, find contours, and
 * keep bounding boxes whose size/aspect look like a card *portrait*, then extend
 * each downward to swallow the name plate so the saved crop is the whole card
 * (art + name), matching the samples the user provided:
 *   full card 181×306 (aspect ~0.59) = portrait 175×248 (top) + name 175×49.
 *
 * These constants are tuned from those real screenshots but kept adjustable —
 * on-device the review page still lets the user add/move/resize boxes, so a miss
 * is recoverable. Coordinates are in the input bitmap's pixel space.
 */
object CardDetector {

    // Portrait (art-only) filters, as fractions of the frame — from the samples.
    private const val MIN_H_FRAC = 0.24f  // portrait height ≥ 24% of screen height
    private const val MAX_H_FRAC = 0.48f
    private const val MIN_ASPECT = 0.52f  // portrait w/h (175/248 ≈ 0.71)
    private const val MAX_ASPECT = 0.95f
    // Extend the portrait box down to include the name plate (≈ 49/248).
    private const val NAME_EXTEND = 0.20f
    private const val IOU_DEDUP = 0.55f   // drop near-duplicate boxes
    private const val MAX_CARDS = 20

    fun detect(bmp: Bitmap): List<RectF> {
        val src = Mat()
        Utils.bitmapToMat(bmp, src)
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        src.release()

        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        gray.release()
        // Close gaps in the card frame so each card is one contour.
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.dilate(edges, edges, kernel)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            edges, contours, Mat(),
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE,
        )
        edges.release()

        val h = bmp.height.toFloat()
        val w = bmp.width.toFloat()
        val cards = ArrayList<RectF>()
        for (c in contours) {
            val r = Imgproc.boundingRect(c)
            c.release()
            val rh = r.height.toFloat()
            val rw = r.width.toFloat()
            if (rh < MIN_H_FRAC * h || rh > MAX_H_FRAC * h) continue
            val aspect = rw / rh
            if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) continue
            // Extend down for the name plate, clamped to the image.
            val top = r.y.toFloat()
            val bottom = (r.y + rh * (1f + NAME_EXTEND)).coerceAtMost(h)
            cards += RectF(r.x.toFloat(), top, (r.x + rw), bottom)
        }
        return dedupe(cards).sortedWith(compareBy({ it.top / (0.1f * h) }, { it.left })).take(MAX_CARDS)
    }

    /** Drop boxes that overlap an already-kept larger box past [IOU_DEDUP]. */
    private fun dedupe(boxes: List<RectF>): List<RectF> {
        val sorted = boxes.sortedByDescending { it.width() * it.height() }
        val kept = ArrayList<RectF>()
        for (b in sorted) {
            if (kept.none { iou(it, b) > IOU_DEDUP }) kept += b
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val ix = maxOf(0f, minOf(a.right, b.right) - maxOf(a.left, b.left))
        val iy = maxOf(0f, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
        val inter = ix * iy
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }
}
