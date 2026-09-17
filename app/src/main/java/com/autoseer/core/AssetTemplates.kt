package com.autoseer.core

import android.content.Context
import android.graphics.BitmapFactory
import com.autoseer.libautomata.IPattern
import com.autoseer.libautomata.Templates
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Loads template images from `assets/images/<id>.png` as grayscale
 * [OpenCvPattern]s, caching each. Template ids match [com.autoseer.scripts.TemplateIds]
 * and [com.autoseer.scripts.GameState.templateId].
 */
class AssetTemplates(context: Context) : Templates {
    private val assets = context.applicationContext.assets
    private val cache = HashMap<String, OpenCvPattern>()
    private val available: Set<String> by lazy { listAssetImages() }

    override fun has(id: String): Boolean = id in available

    override fun get(id: String): IPattern {
        cache[id]?.let { return it }
        val path = "images/$id.png"
        assets.open(path).use { stream ->
            val bmp = BitmapFactory.decodeStream(stream)
                ?: error("無法解碼樣板圖片: $path")
            val rgba = Mat()
            Utils.bitmapToMat(bmp, rgba)
            val gray = Mat()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            rgba.release()
            bmp.recycle()
            val pattern = OpenCvPattern(gray)
            cache[id] = pattern
            return pattern
        }
    }

    private fun listAssetImages(): Set<String> {
        val result = HashSet<String>()
        fun walk(dir: String) {
            val entries = runCatching { assets.list(dir) }.getOrNull() ?: return
            for (name in entries) {
                val full = if (dir.isEmpty()) name else "$dir/$name"
                val children = runCatching { assets.list(full) }.getOrNull()
                if (children.isNullOrEmpty()) {
                    if (full.startsWith("images/") && full.endsWith(".png")) {
                        result += full.removePrefix("images/").removeSuffix(".png")
                    }
                } else {
                    walk(full)
                }
            }
        }
        walk("images")
        return result
    }
}
