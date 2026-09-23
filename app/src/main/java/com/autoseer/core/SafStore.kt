package com.autoseer.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

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
}
