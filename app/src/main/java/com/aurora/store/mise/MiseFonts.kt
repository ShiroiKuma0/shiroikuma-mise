/*
 * 白い熊 店 (shiroikuma-mise) fork: font families for the custom UI, including imported ones.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * The font catalogue behind the UI page's font pickers: the built-in families plus any
 * `.ttf`/`.otf` 白い熊 imports, which are copied into the app's own storage so a picked font
 * survives the content-uri going stale.
 *
 * Every entry is rendered **in its own glyphs** in the picker, which is the point — you choose a
 * font by looking at it, not by reading its file name in the system font.
 */
object MiseFonts {

    /** @param id the value stored in [MiseUiConfig.fontFamily]; "" = the system default. */
    data class FontOption(val id: String, val displayName: String)

    const val SYSTEM = ""
    const val SANS = "@sans"
    const val SERIF = "@serif"
    const val MONOSPACE = "@monospace"
    const val CURSIVE = "@cursive"

    private val BUILT_IN = listOf(
        FontOption(SYSTEM, "System default"),
        FontOption(SANS, "Sans serif"),
        FontOption(SERIF, "Serif"),
        FontOption(MONOSPACE, "Monospace"),
        FontOption(CURSIVE, "Cursive")
    )

    private val cache = mutableMapOf<String, FontFamily>()

    fun fontsDir(context: Context): File =
        File(context.applicationContext.filesDir, "mise-fonts").apply { mkdirs() }

    /** Built-in families first, then imported files sorted by name. */
    fun available(context: Context): List<FontOption> =
        BUILT_IN + imported(context).map { FontOption(it.name, it.nameWithoutExtension) }

    fun imported(context: Context): List<File> =
        fontsDir(context).listFiles { f -> f.isFile && f.extension.lowercase() in FONT_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun displayName(context: Context, id: String): String =
        available(context).firstOrNull { it.id == id }?.displayName
            ?: BUILT_IN.first().displayName

    /**
     * Resolves a stored id to a Compose [FontFamily]. An imported file that has since been
     * deleted falls back to the system default rather than throwing at composition time.
     */
    fun family(context: Context, id: String): FontFamily = when (id) {
        SYSTEM -> FontFamily.Default
        SANS -> FontFamily.SansSerif
        SERIF -> FontFamily.Serif
        MONOSPACE -> FontFamily.Monospace
        CURSIVE -> FontFamily.Cursive
        else -> cache.getOrPut(id) {
            val file = File(fontsDir(context), id)
            if (file.isFile) {
                FontFamily(Font(file, FontWeight.Normal, FontStyle.Normal))
            } else {
                FontFamily.Default
            }
        }
    }

    /**
     * Copies the picked font into our own storage.
     * @return the stored file name (the id to persist), or null if it was not a font file.
     */
    fun import(context: Context, uri: Uri): String? {
        val name = queryName(context, uri) ?: return null
        if (name.substringAfterLast('.', "").lowercase() !in FONT_EXTENSIONS) return null

        val target = File(fontsDir(context), name.replace(File.separatorChar, '_'))
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            cache.remove(target.name)
            target.name
        }.getOrNull()
    }

    fun delete(context: Context, id: String) {
        File(fontsDir(context), id).takeIf { it.isFile }?.delete()
        cache.remove(id)
    }

    private fun queryName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private val FONT_EXTENSIONS = setOf("ttf", "otf")
}
