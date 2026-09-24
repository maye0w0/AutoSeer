package com.autoseer.core

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
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
 * grayscale (matching how screenshots are matched). Order = name-sorted. Phase 1
 * has no pick UI, so *every* image in the folder becomes a target in file-name
 * order — the user controls the set (and order) purely by what they capture/name.
 */
object FactorLibrary {
    private const val TAG = "AutoSeer"

    fun load(ctx: Context): List<FactorTarget> {
        val treeStr = PetImagePrefs.treeUri(ctx) ?: return emptyList()
        val tree = runCatching { Uri.parse(treeStr) }.getOrNull() ?: return emptyList()
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree),
            )
        }.getOrNull() ?: return emptyList()

        val out = ArrayList<FactorTarget>()
        runCatching {
            ctx.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { c ->
                val idIdx = 0; val nameIdx = 1; val mimeIdx = 2
                while (c.moveToNext()) {
                    val docId = c.getString(idIdx) ?: continue
                    val name = c.getString(nameIdx) ?: continue
                    val mime = c.getString(mimeIdx) ?: ""
                    if (!mime.startsWith("image/")) continue
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                    val pattern = runCatching { decodeGray(ctx, fileUri) }.getOrNull()
                    if (pattern == null) { Log.w(TAG, "圖庫圖片解碼失敗，略過：$name"); continue }
                    out += FactorTarget(name.substringBeforeLast('.'), pattern)
                }
            }
        }.onFailure { Log.e(TAG, "讀取精靈圖庫失敗", it) }

        Log.i(TAG, "精靈圖庫載入 ${out.size} 張因子卡")
        return out.sortedBy { it.name }
    }

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
