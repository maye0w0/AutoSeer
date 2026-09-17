package com.autoseer.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
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
        ImageReader.newInstance(deviceWidth, deviceHeight, PixelFormat.RGBA_8888, 2)

    private var virtualDisplay: VirtualDisplay? = null
    private var reusableBitmap: Bitmap? = null

    fun start() {
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection 已停止")
            }
        }, handler)
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

    override fun takeScreenshot(): IPattern {
        val bitmap = acquireBitmap()
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
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

    /** Acquire the latest frame as an ARGB bitmap, retrying briefly if none yet. */
    private fun acquireBitmap(): Bitmap {
        var attempts = 0
        while (true) {
            val image = imageReader.acquireLatestImage()
            if (image != null) {
                try {
                    val plane = image.planes[0]
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * deviceWidth
                    val bufferedWidth = deviceWidth + rowPadding / pixelStride
                    val bmp = reusableBitmap?.takeIf {
                        it.width == bufferedWidth && it.height == deviceHeight
                    } ?: Bitmap.createBitmap(bufferedWidth, deviceHeight, Bitmap.Config.ARGB_8888)
                        .also { reusableBitmap = it }
                    bmp.copyPixelsFromBuffer(plane.buffer)
                    // Crop away the row padding on the right, if any.
                    return if (bufferedWidth != deviceWidth) {
                        Bitmap.createBitmap(bmp, 0, 0, deviceWidth, deviceHeight)
                    } else {
                        bmp
                    }
                } finally {
                    image.close()
                }
            }
            if (++attempts > 40) error("擷取螢幕逾時：沒有可用的畫面影格")
            Thread.sleep(25)
        }
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader.close()
        reusableBitmap?.recycle()
        reusableBitmap = null
        runCatching { projection.stop() }
        handlerThread.quitSafely()
    }

    companion object {
        private const val TAG = "AutoSeer"
        const val NORMALIZED_HEIGHT = 720
    }
}
