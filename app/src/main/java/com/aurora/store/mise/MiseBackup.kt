/*
 * 白い熊 店 (shiroikuma-mise) fork: the Export / Import engine — the category ZIP shared by the
 * UI page's panel and the headless 保存復元 automation path.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import com.aurora.Constants
import com.aurora.store.data.room.AuroraDatabase
import com.aurora.store.data.room.favourite.Favourite
import com.aurora.store.data.room.update.IgnoredUpdate
import com.aurora.store.util.Preferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Category-based backup in the family shape: one `.zip` per export holding a `manifest.json` plus
 * one `<id>.json` per category, absent categories skipped on import, imports merging per key.
 *
 * Filenames follow the mandatory family convention — `shiroikuma-mise_<yyyy-MM-dd_HH-mm-ss>.zip`,
 * no version and no suffix — because every sister app backs up into one directory.
 *
 * The core is [writeZip]: `(categories, OutputStream, onProgress, isCancelled)`. The UI panel and
 * the automation service are both thin callers of it — the export logic exists once.
 */
object MiseBackup {

    const val FORMAT = "shiroikuma-mise-backup"
    const val VERSION = 1
    const val FILE_PREFIX = "shiroikuma-mise_"
    const val MIME_ZIP = "application/zip"

    /** A tickable unit of the backup. [id] is both the zip entry base and the automation id. */
    enum class Cat(val id: String, val label: String, val onByDefault: Boolean = true) {
        UI("ui", "白い熊 店 UI (colours, fonts, sizes)"),
        SETTINGS("settings", "App settings (installer, network, updates, blacklist, spoof)"),
        FAVOURITES("favourites", "Favourites"),
        IGNORED_UPDATES("ignored_updates", "Ignored updates"),
        FONTS("fonts", "Imported fonts", onByDefault = false);

        /** Entry name inside the zip. Fonts are a directory of the original files. */
        val entry: String get() = if (this == FONTS) "fonts/" else "$id.json"

        companion object {
            fun ofId(id: String): Cat? = entries.firstOrNull { it.id == id }

            fun ofEntry(name: String): Cat? = entries.firstOrNull {
                if (it == FONTS) name.startsWith("fonts/") else name == it.entry
            }

            val defaults: Set<Cat> get() = entries.filter { it.onByDefault }.toSet()
        }
    }

    /** Progress callback: the category being written, plus a display count of what is finished. */
    fun interface Progress {
        fun report(cat: Cat, position: Int, total: Int)
    }

    fun exportFileName(): String =
        FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ------------------------------------------------------------------ the core

    /**
     * Writes the ticked [categories] into [out]. Nothing here touches a UI, a SAF uri or a
     * broadcast — callers own the destination.
     *
     * @param isCancelled checked between categories, so a cancel unwinds at a boundary rather
     *        than tearing a write in half.
     * @return the categories actually written.
     */
    suspend fun writeZip(
        context: Context,
        categories: Set<Cat>,
        out: OutputStream,
        onProgress: Progress? = null,
        isCancelled: () -> Boolean = { false }
    ): Set<Cat> {
        val ordered = Cat.entries.filter { it in categories }
        val written = linkedSetOf<Cat>()

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson(context, ordered).toString(2).toByteArray())
            zip.closeEntry()

            ordered.forEachIndexed { index, cat ->
                if (isCancelled()) return written
                // The contract's rule: `current` is the POSITION of the one being written.
                onProgress?.report(cat, index + 1, ordered.size)

                when (cat) {
                    Cat.UI -> zip.jsonEntry(cat, MiseUiConfig(context).toJson())
                    Cat.SETTINGS -> zip.jsonEntry(cat, appSettingsJson(context))
                    Cat.FAVOURITES -> zip.jsonEntry(cat, favouritesJson(context))
                    Cat.IGNORED_UPDATES -> zip.jsonEntry(cat, ignoredUpdatesJson(context))
                    Cat.FONTS -> MiseFonts.imported(context).forEach { font ->
                        if (isCancelled()) return written
                        zip.putNextEntry(ZipEntry("fonts/${font.name}"))
                        zip.write(font.readBytes())
                        zip.closeEntry()
                    }
                }
                written += cat
            }
        }
        return written
    }

    private fun ZipOutputStream.jsonEntry(cat: Cat, json: JSONObject) {
        putNextEntry(ZipEntry(cat.entry))
        write(json.toString(2).toByteArray())
        closeEntry()
    }

    private fun manifestJson(context: Context, cats: List<Cat>) = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("app", context.packageName)
        put(
            "appVersion",
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: ""
        )
        put("createdTs", System.currentTimeMillis())
        put("categories", JSONArray(cats.map { it.id }))
    }

    // ------------------------------------------------------------- SAF destination

    /**
     * Writes [bytes] into the SAF tree [treeUri] **atomically**: a `.part` document first, renamed
     * to the final name only once the archive is complete, and deleted if anything goes wrong. A
     * killed export must never leave something that looks like a backup.
     */
    fun writeToTree(context: Context, treeUri: Uri, bytes: ByteArray): String? {
        val finalName = exportFileName()
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        var partUri: Uri? = null
        return runCatching {
            partUri = DocumentsContract.createDocument(
                context.contentResolver, parent, MIME_ZIP, "$finalName.part"
            ) ?: error("The backup folder could not be written to.")
            context.contentResolver.openOutputStream(partUri!!)?.use { it.write(bytes) }
                ?: error("The backup folder could not be written to.")
            DocumentsContract.renameDocument(context.contentResolver, partUri!!, finalName)
            finalName
        }.getOrElse { failure ->
            partUri?.let {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) }
            }
            throw failure
        }
    }

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
     * Applies the ticked [categories]. Absent ones are skipped, present ones merge per key.
     * @return how many categories were restored.
     */
    suspend fun restore(context: Context, bytes: ByteArray, categories: Set<Cat>): Int {
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
                        Cat.FAVOURITES -> restoreFavourites(context, JSONObject(String(content)))
                        Cat.IGNORED_UPDATES ->
                            restoreIgnoredUpdates(context, JSONObject(String(content)))

                        Cat.FONTS -> {
                            val name = entry.name.removePrefix("fonts/")
                            if (name.isNotBlank()) {
                                File(MiseFonts.fontsDir(context), name).writeBytes(content)
                            }
                        }
                    }
                    seen += cat
                }
                entry = zip.nextEntry
            }
        }
        return seen.size
    }

    // ------------------------------------------------------------- app settings

    /**
     * The signed-in account's keys, which never travel in a backup.
     *
     * Two reasons, and either alone would be enough. They are live Google credentials — the same
     * reason the `accounts` Room table is excluded and the automation token lives in its own prefs
     * file. And carrying them *without* that table is what manufactures a broken session: the
     * restored `ACCOUNT_SIGNED_IN` makes the app believe it is logged in while the account table
     * holds nothing usable, so `AuthProvider` falls back to its BOGUS placeholder and every Play
     * call dies in `HeaderProvider.getDefaultHeaders`.
     *
     * Filtered on import as well as export, so an archive written before this fix cannot re-wedge
     * a working install.
     */
    private val ACCOUNT_KEYS = setOf(
        Constants.ACCOUNT_SIGNED_IN,
        Constants.ACCOUNT_TYPE,
        Constants.ACCOUNT_EMAIL_PLAIN,
        Constants.ACCOUNT_AAS_PLAIN,
        Constants.ACCOUNT_AUTH_PLAIN,
        Preferences.PREFERENCE_AUTH_DATA,
        Preferences.PREFERENCE_AUTH_VIA_MICROG
    )

    /**
     * Aurora keeps the installer/network/update preferences — and the blacklist and the spoof
     * configuration — in the default SharedPreferences, so this one category carries them all.
     * The automation token lives in its own prefs file and is deliberately NOT here: a token must
     * never travel in a backup. Neither does the logged-in account — see [ACCOUNT_KEYS].
     */
    private fun appSettingsJson(context: Context): JSONObject {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        return JSONObject().apply {
            prefs.all.forEach { (key, value) ->
                if (key in ACCOUNT_KEYS) return@forEach
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
                if (key in ACCOUNT_KEYS) return@forEach
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

    // -------------------------------------------------------------- Room-backed

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DatabaseEntryPoint {
        fun database(): AuroraDatabase
    }

    private fun database(context: Context): AuroraDatabase =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DatabaseEntryPoint::class.java
        ).database()

    private suspend fun favouritesJson(context: Context): JSONObject {
        val rows = database(context).favouriteDao().favourites().first()
        return JSONObject().apply {
            put(
                "favourites",
                JSONArray(
                    rows.map { favourite ->
                        JSONObject().apply {
                            put("packageName", favourite.packageName)
                            put("displayName", favourite.displayName)
                            put("iconURL", favourite.iconURL)
                            put("added", favourite.added)
                            put("mode", favourite.mode.name)
                        }
                    }
                )
            )
        }
    }

    private suspend fun restoreFavourites(context: Context, json: JSONObject) {
        val array = json.optJSONArray("favourites") ?: return
        val rows = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val packageName = item.optString("packageName").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            Favourite(
                packageName = packageName,
                displayName = item.optString("displayName"),
                iconURL = item.optString("iconURL"),
                added = item.optLong("added", System.currentTimeMillis()),
                // An unknown/renamed mode must not sink the whole import.
                mode = runCatching { Favourite.Mode.valueOf(item.optString("mode")) }
                    .getOrDefault(Favourite.Mode.IMPORT)
            )
        }
        // insertAll REPLACEs per primary key, which is the merge-per-key the family expects.
        if (rows.isNotEmpty()) database(context).favouriteDao().insertAll(rows)
    }

    private suspend fun ignoredUpdatesJson(context: Context): JSONObject {
        val rows = database(context).ignoredUpdateDao().ignoredUpdates().first()
        return JSONObject().apply {
            put(
                "ignored_updates",
                JSONArray(
                    rows.map { ignored ->
                        JSONObject().apply {
                            put("packageName", ignored.packageName)
                            ignored.ignoredVersionCode?.let { put("ignoredVersionCode", it) }
                        }
                    }
                )
            )
        }
    }

    private suspend fun restoreIgnoredUpdates(context: Context, json: JSONObject) {
        val array = json.optJSONArray("ignored_updates") ?: return
        val dao = database(context).ignoredUpdateDao()
        (0 until array.length()).forEach { index ->
            val item = array.optJSONObject(index) ?: return@forEach
            val packageName = item.optString("packageName").takeIf { it.isNotBlank() }
                ?: return@forEach
            dao.upsert(
                IgnoredUpdate(
                    packageName = packageName,
                    ignoredVersionCode = if (item.has("ignoredVersionCode")) {
                        item.optLong("ignoredVersionCode")
                    } else {
                        null
                    }
                )
            )
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
                // Half-written archives carry .part and are never "the latest backup".
                if (!name.startsWith(FILE_PREFIX) || !name.endsWith(".zip")) continue
                val modified = cursor.getLong(1)
                if (best == null || modified > best!!.second) best = name to modified
            }
        }
        best
    }.getOrNull()

    fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(millis))

    /** Human size for the automation reply — `4.6 MB`, `1.20 GB`. */
    fun humanSize(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format(Locale.ROOT, "%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(Locale.ROOT, "%.1f kB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
