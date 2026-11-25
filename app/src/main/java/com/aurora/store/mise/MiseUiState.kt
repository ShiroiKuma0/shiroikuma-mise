/*
 * 白い熊 店 (shiroikuma-mise) fork: live Compose state for the custom UI + the derived theme.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Observable mirror of [MiseUiConfig]. Every setter writes through to SharedPreferences *and*
 * updates the Compose state, so a slider drag repaints the whole app live — which is the point of
 * "always everything with preview": the preview is the app itself.
 */
class MiseUiState(context: Context) {

    private val config = MiseUiConfig(context)
    private val appContext = context.applicationContext

    var background by mutableIntStateOf(config.background)
        private set
    var surface by mutableIntStateOf(config.surface)
        private set
    var textColor by mutableIntStateOf(config.textColor)
        private set
    var textDimColor by mutableIntStateOf(config.textDimColor)
        private set
    var accentColor by mutableIntStateOf(config.accentColor)
        private set
    var borderColor by mutableIntStateOf(config.borderColor)
        private set
    var dividerColor by mutableIntStateOf(config.dividerColor)
        private set
    var iconColor by mutableIntStateOf(config.iconColor)
        private set
    var warnColor by mutableIntStateOf(config.warnColor)
        private set

    /** Roles that carry their own key and default — see [ColorSlot]. */
    private val keyedColors = mutableStateMapOf<ColorSlot, Int>().apply { putAll(readKeyed()) }

    private fun readKeyed(): Map<ColorSlot, Int> = ColorSlot.entries
        .filter { it.key != null }
        .associateWith { config.color(it.key!!, it.default) }

    var fontFamilyId by mutableStateOf(config.fontFamily)
        private set
    var fontWeight by mutableIntStateOf(config.fontWeight)
        private set
    var fontItalic by mutableStateOf(config.fontItalic)
        private set
    var fontSize by mutableIntStateOf(config.fontSize)
        private set
    var headingSize by mutableIntStateOf(config.headingSize)
        private set
    var headingWeight by mutableIntStateOf(config.headingWeight)
        private set
    var labelSize by mutableIntStateOf(config.labelSize)
        private set
    var letterSpacing by mutableIntStateOf(config.letterSpacing)
        private set
    var lineHeightPct by mutableIntStateOf(config.lineHeightPct)
        private set

    var cornerRadius by mutableIntStateOf(config.cornerRadius)
        private set
    var borderWidth by mutableIntStateOf(config.borderWidth)
        private set
    var dividerWidth by mutableIntStateOf(config.dividerWidth)
        private set

    var iconSize by mutableIntStateOf(config.iconSize)
        private set
    var iconRoundness by mutableIntStateOf(config.iconRoundness)
        private set

    var rowPadding by mutableIntStateOf(config.rowPadding)
        private set
    var groupSpacing by mutableIntStateOf(config.groupSpacing)
        private set
    var indentStep by mutableIntStateOf(config.indentStep)
        private set

    var exportDir by mutableStateOf(config.exportDir)
        private set

    var recentColors by mutableStateOf(config.recentColors())
        private set

    /** The UI page's dragged section order (keys); empty = the default order. */
    var sectionOrder by mutableStateOf(config.sectionOrder)
        private set

    /** The UI page's unfolded sections (keys). */
    var openSections by mutableStateOf(config.openSections)
        private set

    /** Bumped whenever a font is imported or deleted, so pickers recompose. */
    var fontRevision by mutableIntStateOf(0)
        private set

    // ------------------------------------------------------------------ writes

    fun updateColor(slot: ColorSlot, value: Int) {
        when (slot) {
            ColorSlot.BACKGROUND -> { config.background = value; background = value }
            ColorSlot.SURFACE -> { config.surface = value; surface = value }
            ColorSlot.TEXT -> { config.textColor = value; textColor = value }
            ColorSlot.TEXT_DIM -> { config.textDimColor = value; textDimColor = value }
            ColorSlot.ACCENT -> { config.accentColor = value; accentColor = value }
            ColorSlot.BORDER -> { config.borderColor = value; borderColor = value }
            ColorSlot.DIVIDER -> { config.dividerColor = value; dividerColor = value }
            ColorSlot.ICON -> { config.iconColor = value; iconColor = value }
            ColorSlot.WARN -> { config.warnColor = value; warnColor = value }
            else -> { config.putColor(slot.key!!, value); keyedColors[slot] = value }
        }
        config.rememberColor(value)
        recentColors = config.recentColors()
    }

    fun colorOf(slot: ColorSlot): Int = when (slot) {
        ColorSlot.BACKGROUND -> background
        ColorSlot.SURFACE -> surface
        ColorSlot.TEXT -> textColor
        ColorSlot.TEXT_DIM -> textDimColor
        ColorSlot.ACCENT -> accentColor
        ColorSlot.BORDER -> borderColor
        ColorSlot.DIVIDER -> dividerColor
        ColorSlot.ICON -> iconColor
        ColorSlot.WARN -> warnColor
        else -> keyedColors[slot] ?: slot.default
    }

    fun updateFontFamily(id: String) { config.fontFamily = id; fontFamilyId = id }
    fun updateFontWeight(v: Int) { config.fontWeight = v; fontWeight = v }
    fun updateFontItalic(v: Boolean) { config.fontItalic = v; fontItalic = v }
    fun updateFontSize(v: Int) { config.fontSize = v; fontSize = v }
    fun updateHeadingSize(v: Int) { config.headingSize = v; headingSize = v }
    fun updateHeadingWeight(v: Int) { config.headingWeight = v; headingWeight = v }
    fun updateLabelSize(v: Int) { config.labelSize = v; labelSize = v }
    fun updateLetterSpacing(v: Int) { config.letterSpacing = v; letterSpacing = v }
    fun updateLineHeightPct(v: Int) { config.lineHeightPct = v; lineHeightPct = v }

    fun updateCornerRadius(v: Int) { config.cornerRadius = v; cornerRadius = v }
    fun updateBorderWidth(v: Int) { config.borderWidth = v; borderWidth = v }
    fun updateDividerWidth(v: Int) { config.dividerWidth = v; dividerWidth = v }

    fun updateIconSize(v: Int) { config.iconSize = v; iconSize = v }
    fun updateIconRoundness(v: Int) { config.iconRoundness = v; iconRoundness = v }

    fun updateRowPadding(v: Int) { config.rowPadding = v; rowPadding = v }
    fun updateGroupSpacing(v: Int) { config.groupSpacing = v; groupSpacing = v }
    fun updateIndentStep(v: Int) { config.indentStep = v; indentStep = v }

    fun updateExportDir(uri: String) { config.exportDir = uri; exportDir = uri }

    fun updateSectionOrder(order: List<String>) { config.sectionOrder = order; sectionOrder = order }

    fun setSectionOpen(key: String, open: Boolean) {
        val next = if (open) openSections + key else openSections - key
        config.openSections = next
        openSections = next
    }

    fun onFontsChanged() { fontRevision++ }

    fun resetToDefaults() {
        config.resetToDefaults()
        reload()
    }

    /** Re-reads everything from storage — used after an import. */
    fun reload() {
        background = config.background
        surface = config.surface
        textColor = config.textColor
        textDimColor = config.textDimColor
        accentColor = config.accentColor
        borderColor = config.borderColor
        dividerColor = config.dividerColor
        iconColor = config.iconColor
        warnColor = config.warnColor
        keyedColors.putAll(readKeyed())
        fontFamilyId = config.fontFamily
        fontWeight = config.fontWeight
        fontItalic = config.fontItalic
        fontSize = config.fontSize
        headingSize = config.headingSize
        headingWeight = config.headingWeight
        labelSize = config.labelSize
        letterSpacing = config.letterSpacing
        lineHeightPct = config.lineHeightPct
        cornerRadius = config.cornerRadius
        borderWidth = config.borderWidth
        dividerWidth = config.dividerWidth
        iconSize = config.iconSize
        iconRoundness = config.iconRoundness
        rowPadding = config.rowPadding
        groupSpacing = config.groupSpacing
        indentStep = config.indentStep
        exportDir = config.exportDir
        recentColors = config.recentColors()
        sectionOrder = config.sectionOrder
        openSections = config.openSections
        fontRevision++
    }

    // ------------------------------------------------------------- derivations

    val fontFamily: FontFamily
        @Composable get() = MiseFonts.family(appContext, fontFamilyId)

    fun colorScheme(): ColorScheme {
        val bg = Color(background)
        val sf = Color(surface)
        val text = Color(textColor)
        val accent = Color(accentColor)
        val border = Color(borderColor)
        return darkColorScheme(
            primary = accent,
            onPrimary = bg,
            primaryContainer = sf,
            onPrimaryContainer = text,
            secondary = accent,
            onSecondary = bg,
            secondaryContainer = sf,
            onSecondaryContainer = text,
            tertiary = accent,
            onTertiary = bg,
            tertiaryContainer = sf,
            onTertiaryContainer = text,
            background = bg,
            onBackground = text,
            surface = sf,
            onSurface = text,
            surfaceVariant = sf,
            onSurfaceVariant = Color(textDimColor),
            surfaceContainer = sf,
            surfaceContainerHigh = sf,
            surfaceContainerHighest = sf,
            surfaceContainerLow = sf,
            surfaceContainerLowest = sf,
            surfaceBright = sf,
            surfaceDim = sf,
            outline = border,
            outlineVariant = Color(dividerColor),
            error = Color(warnColor),
            onError = bg
        )
    }

    fun shapes(): Shapes {
        val corner = RoundedCornerShape(cornerRadius.dp)
        return Shapes(
            extraSmall = RoundedCornerShape((cornerRadius / 2).dp),
            small = corner,
            medium = corner,
            large = corner,
            extraLarge = corner
        )
    }

    @Composable
    fun typography(base: Typography): Typography {
        val family = fontFamily
        val weight = FontWeight(fontWeight.coerceIn(100, 900))
        val headingWeightValue = FontWeight(headingWeight.coerceIn(100, 900))
        val style = if (fontItalic) FontStyle.Italic else FontStyle.Normal
        val spacing = (letterSpacing / 100f).em

        fun TextStyle.tuned(sizeSp: Int, w: FontWeight): TextStyle = copy(
            fontFamily = family,
            fontWeight = w,
            fontStyle = style,
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * lineHeightPct / 100f).sp,
            letterSpacing = spacing
        )

        val body = fontSize
        val label = labelSize
        val heading = headingSize
        return base.copy(
            displayLarge = base.displayLarge.tuned(heading + 12, headingWeightValue),
            displayMedium = base.displayMedium.tuned(heading + 8, headingWeightValue),
            displaySmall = base.displaySmall.tuned(heading + 6, headingWeightValue),
            headlineLarge = base.headlineLarge.tuned(heading + 6, headingWeightValue),
            headlineMedium = base.headlineMedium.tuned(heading + 4, headingWeightValue),
            headlineSmall = base.headlineSmall.tuned(heading + 2, headingWeightValue),
            titleLarge = base.titleLarge.tuned(heading, headingWeightValue),
            titleMedium = base.titleMedium.tuned(body + 2, headingWeightValue),
            titleSmall = base.titleSmall.tuned(body, headingWeightValue),
            bodyLarge = base.bodyLarge.tuned(body, weight),
            bodyMedium = base.bodyMedium.tuned(body - 1, weight),
            bodySmall = base.bodySmall.tuned(label, weight),
            labelLarge = base.labelLarge.tuned(label + 1, weight),
            labelMedium = base.labelMedium.tuned(label, weight),
            labelSmall = base.labelSmall.tuned(label - 1, weight)
        )
    }

    /** The colour slots the UI page exposes, in the order they appear there. */
    enum class ColorSlot(val key: String? = null, val default: Int = 0) {
        BACKGROUND, SURFACE, TEXT, TEXT_DIM, ACCENT, BORDER, DIVIDER, ICON, WARN,

        /** A held app's offered version that has not been tried yet. */
        HELD_NEW("color_held_new", 0xFF00A550.toInt()),

        /** A held app's offered version that was tried and rejected. */
        HELD_SKIPPED("color_held_skipped", 0xFFFF8000.toInt()),

        /** Menu icon: the row views something or opens a page. */
        MENU_VIEW("color_menu_view", MiseUiConfig.YELLOW),

        /** Menu icon: the row connects, downloads, synchronises or refreshes. */
        MENU_CONNECT("color_menu_connect", 0xFF00A550.toInt()),

        /** Menu icon: the row is about an account or a person. */
        MENU_CONTACT("color_menu_contact", 0xFF00B0FF.toInt()),

        /** Menu icon: the row copies, shares, exports or imports. */
        MENU_SHARE("color_menu_share", 0xFFFF8000.toInt()),

        /** Menu icon: the row blocks, holds back or cancels. */
        MENU_BLOCK("color_menu_block", 0xFFFF3D7F.toInt()),

        /** A switch that is off — opaque, so it reads on any ground. */
        SWITCH_OFF("color_switch_off", 0xFF8C8C00.toInt()),

        // The seven section-icon colours of the UI page, each with one meaning. They are not the
        // menu roles, even where a default coincides.

        /** Section icon: the base — what the app is, and its settings. */
        SECTION_BASE("color_section_base", MiseUiConfig.YELLOW),

        /** Section icon: controls — what you operate. */
        SECTION_CONTROLS("color_section_controls", 0xFF00A550.toInt()),

        /** Section icon: words, written and read. */
        SECTION_WORDS("color_section_words", 0xFF00B0FF.toInt()),

        /** Section icon: the frame and its boxes. */
        SECTION_FRAME("color_section_frame", 0xFF0066FF.toInt()),

        /** Section icon: the app reaches out — asks, offers, hands over. */
        SECTION_REACH("color_section_reach", 0xFFFF8000.toInt()),

        /** Section icon: warning — resets, wipes. */
        SECTION_WARNING("color_section_warning", 0xFFFF0000.toInt()),

        /** Section icon: a brief flash. Rare. */
        SECTION_FLASH("color_section_flash", 0xFFFF3D7F.toInt())
    }
}

typealias ColorSlot = MiseUiState.ColorSlot

/**
 * The one [MiseUiState] for the whole app. Provided by the theme, read by the UI page and by any
 * composable that wants a knob (border width, indent, row padding …).
 */
val LocalMiseUi = compositionLocalOf<MiseUiState> {
    error("LocalMiseUi accessed outside AuroraTheme")
}

@Composable
fun rememberMiseUiState(context: Context): MiseUiState = remember { MiseUiState(context) }
