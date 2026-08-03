/*
 * 白い熊 店 (shiroikuma-mise) fork: the Export / Import engine behind the UI page's top section.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Category-based backup of everything settable in the app, in the shape the sister apps use: one
 * `.zip` per export, each ticked category a JSON entry inside it, absent categories skipped on
 * import.
 *
 * Filenames follow the family convention shared by every 白い熊 fork —
 * `shiroikuma-mise_<yyyy-MM-dd_HH-mm-ss>.zip`, no version and no `_backup` suffix — because all of
 * them back up into one directory, and the newest-backup scan filters on that prefix.
 */
object MiseBackup {

    const val FILE_PREFIX = "shiroikuma-mise_"
    private const val MIME_ZIP = "application/zip"

    /** A tickable unit of the backup. [entry] is the file name inside the zip. */
    enum class Cat(val entry: String, val label: String, val onByDefault: Boolean = true) {
        UI("ui.json", "白い熊 店 UI (colours, fonts, sizes)"),
        SETTINGS("settings.json", "App settings"),
        FONTS("fonts/", "Imported fonts");

        companion object {
            fun ofEntry(name: String): Cat? = entries.firstOrNull {
                if (it == FONTS) name.startsWith(FONTS.entry) else name == it.entry
            }
        }
    }

    fun exportFileName(): String =
        FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ------------------------------------------------------------------ export

    /** Builds the zip in memory — backups are a few kB of JSON plus any imported font files. */
    fun buildZip(context: Context, categories: Set<Cat>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            if (Cat.UI in categories) {
                zip.putNextEntry(ZipEntry(Cat.UI.entry))
                zip.write(MiseUiConfig(context).toJson().toString(2).toByteArray())
                zip.closeEntry()
            }
            if (Cat.SETTINGS in categories) {
                zip.putNextEntry(ZipEntry(Cat.SETTINGS.entry))
                zip.write(appSettingsJson(context).toString(2).toByteArray())
                zip.closeEntry()
            }
            if (Cat.FONTS in categories) {
                MiseFonts.imported(context).forEach { font ->
                    zip.putNextEntry(ZipEntry(Cat.FONTS.entry + font.name))
                    zip.write(font.readBytes())
                    zip.closeEntry()
                }
            }
        }
        return buffer.toByteArray()
    }

    /**
     * Writes [bytes] into the SAF tree [treeUri] as a new document.
     * @return the display name written, or null when the directory is gone or read-only.
     */
    fun writeToTree(context: Context, treeUri: Uri, bytes: ByteArray): String? = runCatching {
        val name = exportFileName()
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val target = DocumentsContract.createDocument(context.contentResolver, parent, MIME_ZIP, name)
            ?: return null
        context.contentResolver.openOutputStream(target)?.use { it.write(bytes) } ?: return null
        name
    }.getOrNull()

    // ------------------------------------------------------------------ import

    fun categoriesIn(bytes: ByteArray): Set<Cat> {
        val found = mutableSetOf<Cat>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                Cat.ofEntry(entry.name)?.let { found += it }
                entry = zip.nextEntry
            }
        }
        return found
    }

    /**
     * Applies the ticked [categories] from a backup.
     * @return how many categories were actually restored.
     */
    fun restore(context: Context, bytes: ByteArray, categories: Set<Cat>): Int {
        var restored = 0
        val seen = mutableSetOf<Cat>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val cat = Cat.ofEntry(entry.name)
                if (cat != null && cat in categories) {
                    val content = zip.readBytes()
                    when (cat) {
                        Cat.UI -> MiseUiConfig(context).fromJson(JSONObject(String(content)))
                        Cat.SETTINGS -> restoreAppSettings(context, JSONObject(String(content)))
                        Cat.FONTS -> {
                            val name = entry.name.removePrefix(Cat.FONTS.entry)
                            if (name.isNotBlank()) {
                                java.io.File(MiseFonts.fontsDir(context), name).writeBytes(content)
                            }
                        }
                    }
                    if (seen.add(cat)) restored++
                }
                entry = zip.nextEntry
            }
        }
        return restored
    }

    // ------------------------------------------------------- app settings I/O

    private fun appSettingsJson(context: Context): JSONObject {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        return JSONObject().apply {
            prefs.all.forEach { (key, value) ->
                when (value) {
                    is Int, is Boolean, is String, is Long, is Float -> put(key, value)
                    else -> Unit
                }
            }
        }
    }

    private fun restoreAppSettings(context: Context, json: JSONObject) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit {
            json.keys().forEach { key ->
                when (val value = json.get(key)) {
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putFloat(key, value.toFloat())
                    else -> Unit
                }
            }
        }
    }

    // ------------------------------------------------------- newest backup scan

    /** Newest `shiroikuma-mise_*.zip` in the tree, as `name to lastModified`. */
    fun newestBackup(context: Context, treeUri: Uri): Pair<String, Long>? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        var best: Pair<String, Long>? = null
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                if (!name.startsWith(FILE_PREFIX) || !name.endsWith(".zip")) continue
                val modified = cursor.getLong(1)
                if (best == null || modified > best!!.second) best = name to modified
            }
        }
        best
    }.getOrNull()

    fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(millis))
}
