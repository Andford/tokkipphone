// SPDX-License-Identifier: GPL-3.0-or-later
package app.nectarlink.android.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File

/**
 * Files from a PC land in the app's cache first (the core writes them
 * there); this puts them where the user finds them: Download/Tokkip Phone.
 */
object ReceivedFiles {
    /** The folder in Download where received files go. */
    const val FOLDER = "Tokkip Phone"
    /** Where they went before the name changed (moved once by [moveOldFolder]). */
    private const val OLD_FOLDER = "Nectarlink"

    /**
     * Once: the files Tokkip Phone saved in Download/Nectarlink move to
     * Download/Tokkip Phone (Android lets an app move what it created).
     * Files other apps put there stay.
     */
    fun moveOldFolder(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val prefs = context.getSharedPreferences("archivos", Context.MODE_PRIVATE)
        if (prefs.getBoolean("carpetaMovida", false)) return
        val resolver = context.contentResolver
        val oldRoot = "${Environment.DIRECTORY_DOWNLOADS}/$OLD_FOLDER/"
        val moved = runCatching {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$oldRoot%"),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val path = c.getString(1) ?: continue
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    val newPath = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/" + path.removePrefix(oldRoot)
                    runCatching {
                        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, newPath) }, null, null)
                    }
                }
            }
        }.isSuccess
        if (moved) prefs.edit().putBoolean("carpetaMovida", true).apply()
    }

    /** A received file where the user can open it. */
    data class Published(val uri: Uri, val name: String, val mime: String)

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    /**
     * Moves a received file, or a folder with everything in it, into
     * Downloads. Returns what's there now; a file that failed stays where
     * it is and is left out.
     */
    fun publish(context: Context, item: File): List<Published> =
        if (item.isDirectory) {
            item.walkTopDown().filter { it.isFile }.mapNotNull { file ->
                val fileParent = file.parentFile ?: return@mapNotNull null
                val itemParent = item.parentFile ?: return@mapNotNull null
                val folder = fileParent.relativeTo(itemParent).invariantSeparatorsPath
                publishFile(context, file, folder)
            }.toList().also {
                item.walkBottomUp().filter { it.isDirectory }.forEach { dir ->
                    if (dir.list()?.isEmpty() == true) dir.delete()
                }
            }
        } else {
            listOfNotNull(publishFile(context, item, folder = null))
        }

    /**
     * Saves a downloaded or temporary [file] into `Download/Tokkip Phone` under [fileName]
     * and returns its content [Uri], or `null` if saving failed.
     */
    fun saveToDownloads(
        context: Context,
        file: File,
        fileName: String = file.name,
        folder: String? = null,
    ): Uri? = runCatching {
        val cleanName = fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { file.name }
        val mime = mimeOf(cleanName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            toMediaStore(context, file, cleanName, mime, folder)
        } else {
            toAppDownloads(context, file, cleanName, folder)
        }
    }.getOrNull()

    /** `folder`: where it goes in Download/Tokkip Phone (`Trip/Day 1`). */
    private fun publishFile(context: Context, file: File, folder: String?): Published? = runCatching {
        val mime = mimeOf(file.name)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            toMediaStore(context, file, file.name, mime, folder)
        } else {
            toAppDownloads(context, file, file.name, folder)
        }
        file.delete()
        Published(uri, file.name, mime)
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun toMediaStore(context: Context, file: File, displayName: String, mime: String, folder: String?): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, listOfNotNull(Environment.DIRECTORY_DOWNLOADS, FOLDER, folder).joinToString("/"))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Android 8–9: the app's own Downloads folder, shared through a FileProvider. */
    private fun toAppDownloads(context: Context, file: File, displayName: String, folder: String?): Uri {
        val base = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        val dir = (if (folder == null) base else File(base, folder)).apply { mkdirs() }
        val baseFile = File(displayName)
        var target = File(dir, baseFile.name)
        var n = 2
        while (target.exists()) {
            target = File(
                dir,
                "${baseFile.nameWithoutExtension} ($n)${baseFile.extension.let { if (it.isEmpty()) "" else ".$it" }}",
            )
            n++
        }
        file.copyTo(target)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }
}
