/*
 * 白い熊 店 (shiroikuma-mise) fork: the custom-UI config store.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import org.json.JSONObject

/**
 * SharedPreferences-backed configuration for the 白い熊 店 UI.
 *
 * Every knob the UI page exposes lives here, seeded to the house black-yellow scheme: black
 * background, pure yellow (`#FFFF00` — never a softer yellow) text and borders. Sizes are stored
 * as plain integers (sp for text, dp for everything else) so a slider can drive them straight,
 * and every border/divider knob is allowed to reach **0** = "draw nothing".
 *
 * The store is deliberately flat and string-keyed: [toJson]/[fromJson] then serialise the whole
 * thing for the Export/Import panel without a schema to maintain in two places.
 */
class MiseUiConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun int(key: String, def: Int) = prefs.getInt(key, def)
    private fun putInt(key: String, v: Int) = prefs.edit { putInt(key, v) }
    private fun str(key: String, def: String = "") = prefs.getString(key, def) ?: def
    private fun putStr(key: String, v: String) = prefs.edit { putString(key, v) }
    private fun bool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun putBool(key: String, v: Boolean) = prefs.edit { putBoolean(key, v) }

    // ---------------------------------------------------------------- colours

    var background: Int
        get() = int(KEY_BG, BLACK); set(v) = putInt(KEY_BG, v)

    /** Cards, sheets, dialogs — anything drawn on top of [background]. */
    var surface: Int
        get() = int(KEY_SURFACE, BLACK); set(v) = putInt(KEY_SURFACE, v)

    var textColor: Int
        get() = int(KEY_TEXT, YELLOW); set(v) = putInt(KEY_TEXT, v)

    /** Secondary/summary text. Defaults to a dimmed yellow so rows stay readable. */
    var textDimColor: Int
        get() = int(KEY_TEXT_DIM, YELLOW_DIM); set(v) = putInt(KEY_TEXT_DIM, v)

    var accentColor: Int
        get() = int(KEY_ACCENT, YELLOW); set(v) = putInt(KEY_ACCENT, v)

    var borderColor: Int
        get() = int(KEY_BORDER, YELLOW); set(v) = putInt(KEY_BORDER, v)

    var dividerColor: Int
        get() = int(KEY_DIVIDER, YELLOW); set(v) = putInt(KEY_DIVIDER, v)

    var iconColor: Int
        get() = int(KEY_ICON, YELLOW); set(v) = putInt(KEY_ICON, v)

    /** Warnings — the "no backup directory set" line, failures. */
    var warnColor: Int
        get() = int(KEY_WARN, RED); set(v) = putInt(KEY_WARN, v)

    // ------------------------------------------------------------- typography

    /** "" = system default; `@sans` / `@serif` / `@monospace` = built-in; else an imported file. */
    var fontFamily: String
        get() = str(KEY_FONT_FAMILY); set(v) = putStr(KEY_FONT_FAMILY, v)

    /** 100..900 in Compose terms; 400 = regular. */
    var fontWeight: Int
        get() = int(KEY_FONT_WEIGHT, 400); set(v) = putInt(KEY_FONT_WEIGHT, v)

    var fontItalic: Boolean
        get() = bool(KEY_FONT_ITALIC, false); set(v) = putBool(KEY_FONT_ITALIC, v)

    /** Body text, sp. */
    var fontSize: Int
        get() = int(KEY_FONT_SIZE, 16); set(v) = putInt(KEY_FONT_SIZE, v)

    /** Section headings, sp. */
    var headingSize: Int
        get() = int(KEY_HEADING_SIZE, 20); set(v) = putInt(KEY_HEADING_SIZE, v)

    var headingWeight: Int
        get() = int(KEY_HEADING_WEIGHT, 700); set(v) = putInt(KEY_HEADING_WEIGHT, v)

    /** Summaries / secondary lines, sp. */
    var labelSize: Int
        get() = int(KEY_LABEL_SIZE, 13); set(v) = putInt(KEY_LABEL_SIZE, v)

    /** Extra letter spacing, hundredths of an em (0 = none). */
    var letterSpacing: Int
        get() = int(KEY_LETTER_SPACING, 0); set(v) = putInt(KEY_LETTER_SPACING, v)

    /** Line height as a percentage of the font size (100 = single). */
    var lineHeightPct: Int
        get() = int(KEY_LINE_HEIGHT, 130); set(v) = putInt(KEY_LINE_HEIGHT, v)

    // --------------------------------------------------------- shape & border

    /** dp; 0 = square corners. */
    var cornerRadius: Int
        get() = int(KEY_CORNER, 12); set(v) = putInt(KEY_CORNER, v)

    /** dp; 0 = no border drawn at all. */
    var borderWidth: Int
        get() = int(KEY_BORDER_W, 1); set(v) = putInt(KEY_BORDER_W, v)

    /** dp; 0 = no divider drawn. */
    var dividerWidth: Int
        get() = int(KEY_DIVIDER_W, 1); set(v) = putInt(KEY_DIVIDER_W, v)

    // ----------------------------------------------------------------- icons

    /** dp. */
    var iconSize: Int
        get() = int(KEY_ICON_SIZE, 24); set(v) = putInt(KEY_ICON_SIZE, v)

    /** 0..100 % of half the icon: 0 = square, 100 = circle. */
    var iconRoundness: Int
        get() = int(KEY_ICON_ROUND, 0); set(v) = putInt(KEY_ICON_ROUND, v)

    // --------------------------------------------------------------- density

    /** dp of vertical padding inside a list row. 白い熊's rule: tight by default. */
    var rowPadding: Int
        get() = int(KEY_ROW_PAD, 4); set(v) = putInt(KEY_ROW_PAD, v)

    /** dp of space between top-level groups — the only place padding is welcome. */
    var groupSpacing: Int
        get() = int(KEY_GROUP_SPACING, 10); set(v) = putInt(KEY_GROUP_SPACING, v)

    /** dp of indent added per nesting level on the UI page. */
    var indentStep: Int
        get() = int(KEY_INDENT, 18); set(v) = putInt(KEY_INDENT, v)

    // ------------------------------------------------------ export directory

    /** SAF tree uri of the backup folder, "" when never set. */
    var exportDir: String
        get() = str(KEY_EXPORT_DIR); set(v) = putStr(KEY_EXPORT_DIR, v)

    // ------------------------------------------------------- recent colours

    /**
     * Most-recently-picked colours, newest first — the one-click swatch row above the RGBA
     * sliders. Seeded with the house palette so the row is useful on a fresh install.
     */
    fun recentColors(): List<Int> {
        val raw = str(KEY_RECENT)
        if (raw.isBlank()) return DEFAULT_SWATCHES
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { DEFAULT_SWATCHES }
    }

    fun rememberColor(color: Int) {
        val next = (listOf(color) + recentColors().filter { it != color }).take(MAX_RECENT)
        putStr(KEY_RECENT, next.joinToString(","))
    }

    // --------------------------------------------------------- serialisation

    /** Every knob as JSON — what the Export/Import panel writes into the backup zip. */
    fun toJson(): JSONObject = JSONObject().apply {
        prefs.all.forEach { (key, value) ->
            when (value) {
                is Int -> put(key, value)
                is Boolean -> put(key, value)
                is String -> put(key, value)
                else -> Unit
            }
        }
    }

    /** Restores a [toJson] snapshot. Unknown keys are ignored, so old backups still import. */
    fun fromJson(json: JSONObject) {
        prefs.edit {
            json.keys().forEach { key ->
                when (val value = json.get(key)) {
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    else -> Unit
                }
            }
        }
    }

    /** Back to the house black-yellow defaults. */
    fun resetToDefaults() {
        prefs.edit { clear() }
    }

    companion object {
        const val PREFS = "mise_ui"

        /** Pure yellow — 白い熊's yellow is never a softer shade. */
        const val YELLOW = 0xFFFFFF00.toInt()
        const val YELLOW_DIM = 0xFFB3B300.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val RED = 0xFFFF3B30.toInt()

        private const val MAX_RECENT = 8
        private val DEFAULT_SWATCHES = listOf(
            YELLOW, BLACK, YELLOW_DIM, RED,
            0xFFFFFFFF.toInt(), 0xFF808080.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt()
        )

        private const val KEY_BG = "color_background"
        private const val KEY_SURFACE = "color_surface"
        private const val KEY_TEXT = "color_text"
        private const val KEY_TEXT_DIM = "color_text_dim"
        private const val KEY_ACCENT = "color_accent"
        private const val KEY_BORDER = "color_border"
        private const val KEY_DIVIDER = "color_divider"
        private const val KEY_ICON = "color_icon"
        private const val KEY_WARN = "color_warn"

        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_FONT_WEIGHT = "font_weight"
        private const val KEY_FONT_ITALIC = "font_italic"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_HEADING_SIZE = "heading_size"
        private const val KEY_HEADING_WEIGHT = "heading_weight"
        private const val KEY_LABEL_SIZE = "label_size"
        private const val KEY_LETTER_SPACING = "letter_spacing"
        private const val KEY_LINE_HEIGHT = "line_height_pct"

        private const val KEY_CORNER = "shape_corner"
        private const val KEY_BORDER_W = "shape_border_width"
        private const val KEY_DIVIDER_W = "shape_divider_width"

        private const val KEY_ICON_SIZE = "icon_size"
        private const val KEY_ICON_ROUND = "icon_roundness"

        private const val KEY_ROW_PAD = "density_row_padding"
        private const val KEY_GROUP_SPACING = "density_group_spacing"
        private const val KEY_INDENT = "density_indent"

        private const val KEY_EXPORT_DIR = "export_dir"
        private const val KEY_RECENT = "recent_colors"
    }
}

/** Convenience: an ARGB int as a Compose [Color]. */
fun Int.toComposeColor(): Color = Color(this)
