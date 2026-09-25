package com.autoseer.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/** One image file inside the SAF folder: its display name (with extension) and URI. */
data class SafImage(val displayName: String, val uri: Uri)

/**
 * Minimal Storage Access Framework helpers: turn a persisted tree URI into a
 * writable folder and drop PNG files into it via [DocumentsContract]. Avoids the
 * androidx.documentfile dependency — plain ContentResolver is enough here.
 */
object SafStore {
    private const val TAG = "AutoSeer"

    /** Human-ish label for the picked folder (last path segment of the tree id). */
    fun folderLabel(treeUri: String): String {
        return try {
            val uri = Uri.parse(treeUri)
            val id = DocumentsContract.getTreeDocumentId(uri)   // e.g. "primary:Pictures/AutoSeer"
            id.substringAfterLast(':').ifBlank { id }
        } catch (e: Exception) {
            treeUri
        }
    }

    /**
     * Write [bmp] as a PNG named [displayName].png into the SAF [treeUri] folder.
     * The framework auto-dedupes clashing names (adds "(1)"). Returns true on success.
     */
    fun writePng(ctx: Context, treeUri: String, displayName: String, bmp: Bitmap): Boolean {
        return try {
            val tree = Uri.parse(treeUri)
            val dirDocId = DocumentsContract.getTreeDocumentId(tree)
            val dirUri = DocumentsContract.buildDocumentUriUsingTree(tree, dirDocId)
            val name = if (displayName.endsWith(".png", true)) displayName else "$displayName.png"
            val fileUri = DocumentsContract.createDocument(
                ctx.contentResolver, dirUri, "image/png", name,
            ) ?: run { Log.e(TAG, "SAF createDocument 回傳 null：$name"); return false }
            ctx.contentResolver.openOutputStream(fileUri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: run { Log.e(TAG, "SAF openOutputStream 失敗：$name"); return false }
            true
        } catch (e: Exception) {
            Log.e(TAG, "SAF 寫檔失敗：$displayName", e)
            false
        }
    }

    /**
     * List image files (name + URI) in the SAF [treeUri] folder, sorted by display
     * name — the same filename order the sweep uses as its default priority. Only
     * entries whose MIME type begins with "image/" are returned; failures yield [].
     */
    fun listImages(ctx: Context, treeUri: String): List<SafImage> {
        val tree = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return emptyList()
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree),
            )
        }.getOrNull() ?: return emptyList()

        val out = ArrayList<SafImage>()
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
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    if (!mime.startsWith("image/")) continue
                    out += SafImage(name, DocumentsContract.buildDocumentUriUsingTree(tree, docId))
                }
            }
        }.onFailure { Log.e(TAG, "讀取 SAF 影像清單失敗", it) }
        return out.sortedBy { it.displayName }
    }

    /**
     * Decode a SAF image to a downscaled ARGB [Bitmap] for UI thumbnails (target
     * width ~[reqW] px via inSampleSize), or null on failure. Downscaling keeps a
     * folder of full-size captures from bloating memory in the list UI.
     */
    fun readBitmap(ctx: Context, uri: Uri, reqW: Int = 256): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val w = bounds.outWidth
        if (w > 0) { while (w / (sample * 2) >= reqW) sample *= 2 }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        Log.e(TAG, "SAF 讀圖失敗：$uri", e); null
    }
}
