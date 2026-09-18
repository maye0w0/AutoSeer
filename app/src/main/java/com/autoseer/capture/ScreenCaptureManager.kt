package com.autoseer.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.autoseer.core.OpenCvPattern
import com.autoseer.libautomata.IPattern
import com.autoseer.libautomata.IScreenshotProvider
import com.autoseer.libautomata.Size
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Captures the screen via MediaProjection and hands back normalized grayscale
 * screenshots (scaled so height == [NORMALIZED_HEIGHT]) as [OpenCvPattern]s.
 *
 * Frames are drained continuously on a background thread via an
 * [ImageReader.OnImageAvailableListener] into a single [latestFrame]: the script
 * loop only polls a few times per second, far slower than the VirtualDisplay
 * produces frames, so a pull-only model let the ImageReader's buffers fill and
 * the producer stall — after which reads returned a STALE frame (你的回合 already
 * on screen but never in the analyzed image). Continuous draining keeps the
 * producer flowing and always serves the newest frame.
 */
class ScreenCaptureManager(
    private val projection: MediaProjection,
    private val deviceWidth: Int,
    private val deviceHeight: Int,
    private val densityDpi: Int,
) : IScreenshotProvider {

    private val normalizedHeight = NORMALIZED_HEIGHT
    private val normalizedWidth =
        (deviceWidth.toDouble() * normalizedHeight / deviceHeight).toInt()

    private val handlerThread = HandlerThread("AutoSeerCapture").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val imageReader: ImageReader =
        ImageReader.newInstance(deviceWidth, deviceHeight, PixelFormat.RGBA_8888, MAX_IMAGES)

    private var virtualDisplay: VirtualDisplay? = null

    // Written by the capture thread's listener, read (copied) by the script thread.
    private val frameLock = Any()
    private var published: Bitmap? = null   // device-size, always the newest frame
    private var pendingBuffer: Bitmap? = null // scratch for copyPixelsFromBuffer (row-padded)

    fun start() {
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection 已停止")
            }
        }, handler)
        imageReader.setOnImageAvailableListener({ reader -> onFrame(reader) }, handler)
        virtualDisplay = projection.createVirtualDisplay(
            "AutoSeerCapture",
            deviceWidth,
            deviceHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            handler,
        )
    }

    override fun normalizedSize(): Size = Size(normalizedWidth, normalizedHeight)
    override fun deviceSize(): Size = Size(deviceWidth, deviceHeight)

    /** Drain to the newest available frame and publish it, freeing buffers so the producer keeps flowing. */
    private fun onFrame(reader: ImageReader) {
        val image = try { reader.acquireLatestImage() } catch (e: Exception) { null } ?: return
        try {
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * deviceWidth
            val bufferedWidth = deviceWidth + rowPadding / pixelStride

            val buffer = pendingBuffer?.takeIf { it.width == bufferedWidth && it.height == deviceHeight }
                ?: Bitmap.createBitmap(bufferedWidth, deviceHeight, Bitmap.Config.ARGB_8888)
                    .also { pendingBuffer = it }
            buffer.copyPixelsFromBuffer(plane.buffer)

            synchronized(frameLock) {
                val dst = published?.takeIf { it.width == deviceWidth && it.height == deviceHeight }
                    ?: Bitmap.createBitmap(deviceWidth, deviceHeight, Bitmap.Config.ARGB_8888)
                        .also { published = it }
                // Copy the (un-padded) device-width region into the published frame in place.
                Canvas(dst).drawBitmap(
                    buffer,
                    Rect(0, 0, deviceWidth, deviceHeight),
                    Rect(0, 0, deviceWidth, deviceHeight),
                    null,
                )
            }
        } finally {
            image.close()
        }
    }

    /** A stable snapshot of the newest frame (device size). Caller owns it. */
    private fun awaitFrame(timeoutMs: Long = FRAME_TIMEOUT_MS): Bitmap {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            synchronized(frameLock) {
                published?.let { return it.copy(Bitmap.Config.ARGB_8888, false) }
            }
            if (System.currentTimeMillis() > deadline) error("擷取螢幕逾時：沒有可用的畫面影格")
            Thread.sleep(20)
        }
    }

    override fun takeScreenshot(): IPattern {
        val bitmap = awaitFrame()
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        bitmap.recycle()
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        val resized = Mat()
        Imgproc.resize(
            gray,
            resized,
            org.opencv.core.Size(normalizedWidth.toDouble(), normalizedHeight.toDouble()),
        )
        gray.release()
        return OpenCvPattern(resized)
    }

    /**
     * A viewable color snapshot at the normalized size (same scaling the matcher
     * sees), for template capture / debugging. Caller owns the returned bitmap.
     */
    fun captureColorBitmap(): Bitmap {
        val src = awaitFrame()
        val scaled = Bitmap.createScaledBitmap(src, normalizedWidth, normalizedHeight, true)
        if (scaled !== src) src.recycle()
        return scaled
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader.setOnImageAvailableListener(null, null)
        imageReader.close()
        synchronized(frameLock) {
            published?.recycle()
            published = null
        }
        pendingBuffer?.recycle()
        pendingBuffer = null
        runCatching { projection.stop() }
        handlerThread.quitSafely()
    }

    companion object {
        private const val TAG = "AutoSeer"
        const val NORMALIZED_HEIGHT = 720
        // A little headroom so the producer never blocks between our polls.
        private const val MAX_IMAGES = 3
        private const val FRAME_TIMEOUT_MS = 2000L
    }
}
