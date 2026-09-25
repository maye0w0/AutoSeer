package com.autoseer.core

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.autoseer.scripts.FactorTarget
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Loads the player's captured 因子卡 images from the 精靈圖庫 SAF folder
 * ([PetImagePrefs]) into [FactorTarget]s the sweep matches against the selection
 * grid.
 *
 * Name = file name without extension; the reference pattern is decoded to
 * grayscale (matching how screenshots are matched). Listing order = file-name
 * order. The set/order actually swept is decided by [FactorPlan] (the 因子關卡排序
 * UI); with no saved plan every image is used, in file-name order.
 */
object FactorLibrary {
    private const val TAG = "AutoSeer"

    /** All images in the 精靈圖庫 folder (name + URI), file-name sorted. UI-facing. */
    fun list(ctx: Context): List<SafImage> {
        val tree = PetImagePrefs.treeUri(ctx) ?: return emptyList()
        return SafStore.listImages(ctx, tree)
    }

    /** Decode the given [images] (in order) to [FactorTarget]s; undecodable ones are skipped. */
    fun loadTargets(ctx: Context, images: List<SafImage>): List<FactorTarget> {
        val out = ArrayList<FactorTarget>(images.size)
        for (img in images) {
            val pattern = runCatching { decodeGray(ctx, img.uri) }.getOrNull()
            if (pattern == null) { Log.w(TAG, "圖庫圖片解碼失敗，略過：${img.displayName}"); continue }
            out += FactorTarget(img.displayName.substringBeforeLast('.'), pattern)
        }
        Log.i(TAG, "精靈圖庫載入 ${out.size} 張因子卡")
        return out
    }

    /** Back-compat: every image in the folder, in file-name order. */
    fun load(ctx: Context): List<FactorTarget> = loadTargets(ctx, list(ctx))

    private fun decodeGray(ctx: Context, uri: Uri): OpenCvPattern {
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("openInputStream/decode 回傳 null：$uri")
        val rgba = Mat()
        Utils.bitmapToMat(bmp, rgba)
        bmp.recycle()
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        return OpenCvPattern(gray)
    }
}
