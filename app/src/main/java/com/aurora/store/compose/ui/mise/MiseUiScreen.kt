/*
 * 白い熊 店 (shiroikuma-mise) fork: the 白い熊 店 UI page.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.mise

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.aurora.store.compose.composable.MiseSwitch
import kotlinx.coroutines.launch
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aurora.store.R
import com.aurora.store.compose.composable.TopAppBar
import com.aurora.store.mise.ColorSlot
import com.aurora.store.mise.LocalMiseUi
import com.aurora.store.mise.MiseFonts
import com.aurora.store.mise.MiseLanguage
import com.aurora.store.mise.automation.MiseAutomationAuth
import com.aurora.store.mise.MiseUiState
import com.aurora.store.mise.MiseFlash
import com.aurora.store.util.Preferences
import com.aurora.store.util.Preferences.PREFERENCE_UPDATES_FROZEN
import com.aurora.store.util.save
import com.aurora.store.viewmodel.all.UpdatesViewModel

/**
 * The 白い熊 店 UI page — every knob that shapes the app's look, in the kxkb page grammar:
 *
 *  * a top-level group opens with a full-width hairline, then a big bold heading carrying a
 *    **word-width** underline;
 *  * a sub-group repeats that one indent level in, smaller;
 *  * rows sit two levels in (three under a sub-group), with tight vertical padding — the only
 *    generous spacing in the page is between top-level groups, so grouping reads instantly;
 *  * every control previews itself, and since the page is themed by the very values it edits,
 *    the whole app is the preview.
 */
/** One top-level section of the page: what its heading and its chip show, and its rows. */
private class PageSection(
    val key: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val slot: ColorSlot,
    val content: @Composable () -> Unit
)

/** The stored order, with sections it does not know appended in their default place. */
private fun ordered(sections: List<PageSection>, stored: List<String>): List<PageSection> {
    val byKey = sections.associateBy { it.key }
    val known = stored.mapNotNull { byKey[it] }
    val unknown = sections.filter { it !in known }
    // "App language" goes first also in an order stored before it existed: nothing dragged it
    // anywhere yet. Once it is in the stored order it stays where it was put.
    val (language, rest) = unknown.partition { it.key == "language" }
    return language + known + rest
}

@Composable
fun MiseUiScreen() {
    val ui = LocalMiseUi.current
    val activity = LocalActivity.current as? ComponentActivity
    var showExportImport by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val headerTops = remember { HashMap<String, Int>() }

    // The registry: title, icon and colour sit together, so a heading and its chip cannot drift
    // apart. The order here is the default order — no two neighbours share a colour.
    val sections = listOf(
        PageSection("language", R.string.mise_ui_section_language, R.drawable.ic_mise_language, ColorSlot.SECTION_WORDS) {
            LanguageSection(ui)
        },
        PageSection("export", R.string.mise_ui_section_export_import, R.drawable.ic_share, ColorSlot.SECTION_REACH) {
            ExportSection(ui) { showExportImport = true }
        },
        PageSection("colours", R.string.mise_ui_section_colours, R.drawable.ic_ui, ColorSlot.SECTION_BASE) {
            ColoursSection(ui)
        },
        PageSection("typography", R.string.mise_ui_section_typography, R.drawable.ic_mise_text, ColorSlot.SECTION_WORDS) {
            TypographySection(ui)
        },
        PageSection("shape", R.string.mise_ui_section_shape, R.drawable.ic_mise_shape, ColorSlot.SECTION_FRAME) {
            ShapeSection(ui)
        },
        PageSection("icons", R.string.mise_ui_section_icons, R.drawable.ic_apps, ColorSlot.SECTION_BASE) {
            IconsSection(ui)
        },
        PageSection("density", R.string.mise_ui_section_density, R.drawable.ic_tune, ColorSlot.SECTION_FRAME) {
            DensitySection(ui)
        },
        PageSection("updates", R.string.mise_ui_section_updates, R.drawable.ic_updates, ColorSlot.SECTION_BASE) {
            FrozenUpdatesRow(ui)
        },
        PageSection("reset", R.string.mise_ui_section_reset, R.drawable.ic_refresh, ColorSlot.SECTION_WARNING) {
            ResetSection(ui)
        }
    )
    val inOrder = ordered(sections, ui.sectionOrder)

    Scaffold(
        topBar = { TopAppBar(title = "白い熊 店 UI") }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            ChipIndex(
                ui = ui,
                sections = inOrder,
                onJump = { key ->
                    ui.setSectionOpen(key, true)
                    scope.launch { scrollState.animateScrollTo(headerTops[key] ?: 0) }
                },
                onReorder = { ui.updateSectionOrder(it) }
            )
            inOrder.forEach { section ->
                key(section.key) {
                    val open = section.key in ui.openSections
                    SectionHeader(
                        ui = ui,
                        section = section,
                        open = open,
                        modifier = Modifier.onGloballyPositioned {
                            headerTops[section.key] = it.positionInParent().y.toInt()
                        },
                        onClick = { ui.setSectionOpen(section.key, !open) }
                    )
                    if (open) section.content()
                }
            }
            Spacer(Modifier.height(ui.groupSpacing.dp * 2))
        }
    }

    if (showExportImport) {
        MiseExportImportPanel(
            onDismiss = { showExportImport = false },
            onFinishedAndClose = {
                showExportImport = false
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        )
    }
}

/**
 * The index: one small outlined chip per section, each as wide as its icon and label, wrapping.
 * A tap unfolds the section and scrolls to it. A long-press lifts the chip into a drag; the chips
 * reorder live under the finger and the sections follow on drop.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipIndex(
    ui: MiseUiState,
    sections: List<PageSection>,
    onJump: (String) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    val incoming = sections.map { it.key }
    var order by remember { mutableStateOf(incoming) }
    var dragging by remember { mutableStateOf<String?>(null) }
    // All three in root coordinates: the finger, where in the chip it took hold, and the place
    // the lifted chip holds in the grid — the chip is drawn at the finger, away from that place.
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var slot by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { HashMap<String, Rect>() }
    val byKey = sections.associateBy { it.key }
    LaunchedEffect(incoming) { if (dragging == null) order = incoming }

    val density = LocalDensity.current
    val iconSize = with(density) { (ui.labelSize * 1.25f).sp.toDp() }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        order.forEach { k ->
            val section = byKey[k]
            if (section != null) {
                key(k) {
                    val lifted = dragging == k
                    // The outer box is the chip's place in the grid and takes the touches; the
                    // chip inside it is what floats.
                    Box(
                        modifier = Modifier
                            .zIndex(if (lifted) 1f else 0f)
                            .onGloballyPositioned {
                                val rect = it.boundsInRoot()
                                bounds[k] = rect
                                if (dragging == k) slot = rect.topLeft
                            }
                            .pointerInput(k) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val held = awaitLongPressOrCancellation(down.id)
                                    if (held == null) {
                                        // Let go before the long-press: a tap, unless the
                                        // finger wandered off (the page scrolled instead).
                                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                        if (up != null && !up.pressed &&
                                            (up.position - down.position).getDistance() <
                                            viewConfiguration.touchSlop
                                        ) {
                                            onJump(k)
                                        }
                                    } else {
                                        val place = bounds[k]?.topLeft ?: Offset.Zero
                                        slot = place
                                        grab = held.position
                                        pointer = place + held.position
                                        dragging = k
                                        drag(held.id) { change ->
                                            pointer += change.positionChange()
                                            change.consume()
                                            order = reordered(order, k, pointer, bounds)
                                        }
                                        dragging = null
                                        onReorder(order)
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    if (lifted) {
                                        translationX = pointer.x - grab.x - slot.x
                                        translationY = pointer.y - grab.y - slot.y
                                        scaleX = 1.12f
                                        scaleY = 1.12f
                                    }
                                }
                                .clip(CircleShape)
                                .background(Color(ui.background))
                                .border(
                                    if (lifted) 3.dp else 1.25.dp,
                                    Color(ui.borderColor),
                                    CircleShape
                                )
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(section.icon),
                                contentDescription = null,
                                tint = Color(ui.colorOf(section.slot)),
                                modifier = Modifier.size(iconSize)
                            )
                            Spacer(Modifier.width(iconSize / 3))
                            Text(
                                text = stringResource(section.title),
                                color = Color(ui.textColor),
                                fontSize = ui.labelSize.sp,
                                fontWeight = if (lifted) FontWeight.Bold else null,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Where the dragged chip belongs once the finger is at [at]. It takes the place of the chip under
 * the finger only when the finger has passed that chip's middle or left the dragged chip's own
 * line — without that, two chips of unequal width swap back and forth under a resting finger.
 */
private fun reordered(
    order: List<String>,
    key: String,
    at: Offset,
    bounds: Map<String, Rect>
): List<String> {
    val own = bounds[key] ?: return order
    val target = order.firstOrNull { it != key && bounds[it]?.contains(at) == true } ?: return order
    val from = order.indexOf(key)
    val to = order.indexOf(target)
    val there = bounds.getValue(target)
    val passed = if (to > from) {
        at.y > own.bottom || at.x >= there.center.x
    } else {
        at.y < own.top || at.x <= there.center.x
    }
    if (!passed) return order
    return order.toMutableList().apply {
        removeAt(from)
        add(to, key)
    }
}

// ---------------------------------------------------------------------- sections

@Composable
private fun LanguageSection(ui: MiseUiState) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    val current = MiseLanguage.currentName(context)
        ?: stringResource(R.string.mise_ui_language_system)
    NavigationRow(
        ui = ui,
        title = stringResource(R.string.mise_ui_language_row, current),
        summary = stringResource(R.string.mise_ui_language_summary),
        main = true,
        opens = true,
        icon = R.drawable.ic_mise_language,
        slot = ColorSlot.MENU_VIEW,
        onClick = { showPicker = true }
    )
    if (showPicker) MiseLanguageDialog(onDismiss = { showPicker = false })
}

@Composable
private fun ExportSection(ui: MiseUiState, onOpenPanel: () -> Unit) {
    NavigationRow(
        ui = ui,
        title = stringResource(R.string.mise_ui_export_import_settings),
        summary = exportDirSummary(ui),
        summaryIsWarning = ui.exportDir.isBlank(),
        main = true,
        icon = R.drawable.ic_share,
        slot = ColorSlot.MENU_SHARE,
        opens = true,
        onClick = onOpenPanel
    )
    // The 保存復元 automation rows belong here, under the export rows — a backup feature
    // lives where backup lives, and every sister app looks the same.
    AutomationRows(ui)
}

@Composable
private fun ColoursSection(ui: MiseUiState) {
    ColorRow(ui, stringResource(R.string.mise_ui_colour_background), ColorSlot.BACKGROUND)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_surface), ColorSlot.SURFACE)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_text), ColorSlot.TEXT)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_text_secondary), ColorSlot.TEXT_DIM)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_accent), ColorSlot.ACCENT)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_border), ColorSlot.BORDER)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_divider), ColorSlot.DIVIDER)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_icons), ColorSlot.ICON)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_warnings), ColorSlot.WARN)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_switch_off), ColorSlot.SWITCH_OFF)
    SubHeader(ui, stringResource(R.string.mise_ui_sub_held_updates))
    ColorRow(ui, stringResource(R.string.mise_ui_colour_held_new), ColorSlot.HELD_NEW, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_held_skipped), ColorSlot.HELD_SKIPPED, level2 = true)
    SubHeader(ui, stringResource(R.string.mise_ui_sub_menu_icons))
    ColorRow(ui, stringResource(R.string.mise_ui_colour_menu_view), ColorSlot.MENU_VIEW, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_menu_connect), ColorSlot.MENU_CONNECT, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_menu_account), ColorSlot.MENU_CONTACT, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_menu_share), ColorSlot.MENU_SHARE, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_menu_block), ColorSlot.MENU_BLOCK, level2 = true)
    SubHeader(ui, stringResource(R.string.mise_ui_sub_section_icons))
    ColorRow(ui, stringResource(R.string.mise_ui_colour_section_base), ColorSlot.SECTION_BASE, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_section_controls), ColorSlot.SECTION_CONTROLS, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_section_words), ColorSlot.SECTION_WORDS, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_section_frame), ColorSlot.SECTION_FRAME, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_section_reach), ColorSlot.SECTION_REACH, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_section_warning), ColorSlot.SECTION_WARNING, level2 = true)
    ColorRow(ui, stringResource(R.string.mise_ui_colour_section_flash), ColorSlot.SECTION_FLASH, level2 = true)
}

@Composable
private fun TypographySection(ui: MiseUiState) {
    FontRow(ui)
    SubHeader(ui, stringResource(R.string.mise_ui_sub_body))
    SliderRow(ui, stringResource(R.string.mise_ui_size), ui.fontSize, 8..32, stringResource(R.string.mise_unit_sp), level2 = true) {
        ui.updateFontSize(it)
    }
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_weight),
        ui.fontWeight,
        100..900,
        step = 100,
        level2 = true
    ) {
        ui.updateFontWeight(it)
    }
    ToggleRow(ui, stringResource(R.string.mise_ui_italic), ui.fontItalic, level2 = true) {
        ui.updateFontItalic(it)
    }
    SubHeader(ui, stringResource(R.string.mise_ui_sub_headings))
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_size),
        ui.headingSize,
        12..40,
        stringResource(R.string.mise_unit_sp),
        level2 = true
    ) {
        ui.updateHeadingSize(it)
    }
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_weight),
        ui.headingWeight,
        100..900,
        step = 100,
        level2 = true
    ) {
        ui.updateHeadingWeight(it)
    }
    SubHeader(ui, stringResource(R.string.mise_ui_sub_secondary_lines))
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_size),
        ui.labelSize,
        8..24,
        stringResource(R.string.mise_unit_sp),
        level2 = true
    ) { ui.updateLabelSize(it) }
    SubHeader(ui, stringResource(R.string.mise_ui_sub_spacing))
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_letter_spacing),
        ui.letterSpacing,
        0..20,
        stringResource(R.string.mise_unit_em_hundredths),
        level2 = true
    ) {
        ui.updateLetterSpacing(it)
    }
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_line_height),
        ui.lineHeightPct,
        100..200,
        stringResource(R.string.mise_unit_percent),
        level2 = true
    ) {
        ui.updateLineHeightPct(it)
    }
    TypePreview(ui)
}

@Composable
private fun ShapeSection(ui: MiseUiState) {
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_corner_roundness),
        ui.cornerRadius,
        0..40,
        stringResource(R.string.mise_unit_dp)
    ) {
        ui.updateCornerRadius(it)
    }
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_border_thickness),
        ui.borderWidth,
        0..8,
        stringResource(R.string.mise_unit_dp)
    ) { ui.updateBorderWidth(it) }
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_divider_thickness),
        ui.dividerWidth,
        0..8,
        stringResource(R.string.mise_unit_dp)
    ) {
        ui.updateDividerWidth(it)
    }
    ShapePreview(ui)
}

@Composable
private fun IconsSection(ui: MiseUiState) {
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_size),
        ui.iconSize,
        12..64,
        stringResource(R.string.mise_unit_dp)
    ) { ui.updateIconSize(it) }
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_roundness),
        ui.iconRoundness,
        0..100,
        stringResource(R.string.mise_unit_percent)
    ) { ui.updateIconRoundness(it) }
    IconPreview(ui)
}

@Composable
private fun DensitySection(ui: MiseUiState) {
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_row_padding),
        ui.rowPadding,
        0..24,
        stringResource(R.string.mise_unit_dp)
    ) { ui.updateRowPadding(it) }
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_group_spacing),
        ui.groupSpacing,
        0..40,
        stringResource(R.string.mise_unit_dp)
    ) {
        ui.updateGroupSpacing(it)
    }
    SliderRow(
        ui,
        stringResource(R.string.mise_ui_indent_step),
        ui.indentStep,
        0..48,
        stringResource(R.string.mise_unit_dp)
    ) { ui.updateIndentStep(it) }
}

@Composable
private fun ResetSection(ui: MiseUiState) {
    NavigationRow(
        ui = ui,
        title = stringResource(R.string.mise_ui_reset_defaults),
        summary = stringResource(R.string.mise_ui_reset_defaults_summary),
        icon = R.drawable.ic_refresh,
        slot = ColorSlot.WARN,
        onClick = { ui.resetToDefaults() }
    )
    NavigationRow(
        ui = ui,
        title = stringResource(R.string.mise_ui_reset_section_order),
        summary = stringResource(R.string.mise_ui_reset_section_order_summary),
        icon = R.drawable.ic_list_check,
        slot = ColorSlot.WARN,
        onClick = { ui.updateSectionOrder(emptyList()) }
    )
}

/**
 * The three contract rows, in the order every sister app shows them.
 *
 * 1. The master switch — **default ON** since contract v2. It stays a switch rather than being
 *    removed because it is the only way to close this app off, and a feature that can be turned on
 *    but never off is one 白い熊 cannot retreat from.
 * 2. 「Use authorization token?」 — **default OFF**. A pasted secret cannot survive a wipe, and the
 *    case this exists for is 応用管理 restoring this app *and its data* onto a clean phone.
 * 3. The token itself, **shown only when row 2 is on**: a 48-character secret sitting under an off
 *    switch invites 白い熊 to paste it somewhere it will do nothing.
 */
@Composable
private fun AutomationRows(ui: MiseUiState) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(MiseAutomationAuth.enabled(context)) }
    var requireToken by remember { mutableStateOf(MiseAutomationAuth.requireToken(context)) }

    ToggleRow(
        ui,
        stringResource(R.string.mise_ui_automation_export),
        enabled,
        icon = R.drawable.ic_cloud_upload,
        slot = ColorSlot.MENU_SHARE
    ) {
        MiseAutomationAuth.setEnabled(context, it)
        enabled = it
    }
    RowNote(ui, stringResource(R.string.mise_ui_automation_export_note))

    ToggleRow(
        ui,
        stringResource(R.string.mise_ui_automation_use_token),
        requireToken,
        icon = R.drawable.ic_lock,
        slot = ColorSlot.MENU_BLOCK
    ) {
        MiseAutomationAuth.setRequireToken(context, it)
        requireToken = it
    }
    RowNote(
        ui,
        if (requireToken) {
            stringResource(R.string.mise_ui_automation_use_token_on_note)
        } else {
            stringResource(R.string.mise_ui_automation_use_token_off_note)
        }
    )

    // Hidden while the token is not being asked for — see the KDoc's third point. The read below
    // is also what MINTS the token, so an app 白い熊 never asks a token of never generates one.
    if (!requireToken) return

    var token by remember { mutableStateOf(MiseAutomationAuth.token(context)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("token", token))
                MiseFlash.show(
                    context,
                    R.string.mise_ui_automation_token_copied,
                    Toast.LENGTH_SHORT
                )
            }
            .padding(
                start = rowIndent(ui, false),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(ui, R.drawable.ic_file_copy, ColorSlot.MENU_SHARE)
        Column(Modifier.weight(1f)) {
            RowTitle(
                ui,
                stringResource(R.string.mise_ui_automation_token),
                MiseAutomationAuth.abbreviated(token)
            )
        }
        Text(
            text = stringResource(R.string.mise_ui_automation_token_regenerate),
            color = Color(ui.warnColor),
            fontSize = ui.labelSize.sp,
            modifier = Modifier
                .clip(CircleShape)
                .border(1.25.dp, Color(ui.warnColor), CircleShape)
                .clickable {
                    token = MiseAutomationAuth.regenerate(context)
                    MiseFlash.show(
                        context,
                        R.string.mise_ui_automation_token_regenerated,
                        Toast.LENGTH_LONG
                    )
                }
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

/**
 * The frozen-apps update switch, mirrored from Settings → Updates → Advanced. Both rows read and
 * write the same preference, so whichever page you open shows the current state; flipping either
 * kicks off a fresh check, since the answer it changes is "what is in the Updates tab".
 */
@Composable
private fun FrozenUpdatesRow(ui: MiseUiState) {
    val context = LocalContext.current
    val viewModel: UpdatesViewModel = hiltViewModel()
    var enabled by remember {
        mutableStateOf(Preferences.getBoolean(context, PREFERENCE_UPDATES_FROZEN, true))
    }

    ToggleRow(
        ui,
        stringResource(R.string.mise_ui_updates_frozen),
        enabled,
        icon = R.drawable.ic_updates,
        slot = ColorSlot.MENU_CONNECT
    ) {
        context.save(PREFERENCE_UPDATES_FROZEN, it)
        enabled = it
        viewModel.fetchUpdates()
    }
    Text(
        text = stringResource(R.string.mise_ui_updates_frozen_note),
        color = Color(ui.textDimColor),
        fontSize = ui.labelSize.sp,
        modifier = Modifier.padding(
            start = rowIndent(ui, false) + rowIconGutter(ui),
            end = 16.dp,
            bottom = ui.rowPadding.dp
        )
    )
}

@Composable
private fun exportDirSummary(ui: MiseUiState): String =
    if (ui.exportDir.isBlank()) {
        stringResource(R.string.mise_ui_export_dir_unset)
    } else {
        stringResource(R.string.mise_ui_export_dir_set)
    }

// ---------------------------------------------------------------------- headings

/**
 * Top-level group heading: the full-width hairline above, the section's icon in its colour, a big
 * bold title with a word-width underline, and › when folded or ⌄ when open. A tap folds or
 * unfolds the rows; the line and the heading stay either way.
 */
@Composable
private fun SectionHeader(
    ui: MiseUiState,
    section: PageSection,
    open: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val state = stringResource(
        if (open) R.string.mise_ui_section_open else R.string.mise_ui_section_folded
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(ui.groupSpacing.dp))
        if (ui.dividerWidth > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ui.dividerWidth.dp)
                    .background(Color(ui.dividerColor))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics { stateDescription = state }
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RowIcon(ui, section.icon, section.slot, textSp = ui.headingSize)
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                Text(
                    text = stringResource(section.title),
                    color = Color(ui.accentColor),
                    fontSize = ui.headingSize.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(2.5.dp)
                        .background(Color(ui.accentColor))
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (open) "⌄" else "›",
                color = Color(ui.accentColor),
                fontSize = ui.headingSize.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** One level under a section: same shape, smaller, no full-width rule. */
@Composable
private fun SubHeader(ui: MiseUiState, title: String) {
    Column(
        modifier = Modifier
            .padding(start = (BASE_INDENT + ui.indentStep).dp, top = 6.dp, bottom = 2.dp)
            .width(IntrinsicSize.Max)
    ) {
        Text(
            text = title,
            color = Color(ui.accentColor),
            fontSize = (ui.headingSize - 3).coerceAtLeast(10).sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .height(1.5.dp)
                .background(Color(ui.accentColor))
        )
    }
}

// -------------------------------------------------------------------------- rows

private const val BASE_INDENT = 36

/**
 * A row's icon: 1.25 × the label's text size and never under 18 dp, so it follows the settable
 * font. It stands in a box as tall as the label's first line, which keeps it beside the label on
 * a row that also has a description; the gap after it is half the icon.
 */
@Composable
private fun RowIcon(
    ui: MiseUiState,
    @DrawableRes icon: Int,
    slot: ColorSlot,
    textSp: Int = ui.fontSize
) {
    val density = LocalDensity.current
    val size = with(density) { maxOf((textSp * 1.25f).sp.toDp(), 18.dp) }
    val line = with(density) { (textSp * ui.lineHeightPct / 100f).sp.toDp() }
    Box(
        modifier = Modifier
            .padding(end = size / 2)
            .size(width = size, height = maxOf(size, line)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color(ui.colorOf(slot)),
            modifier = Modifier.size(size)
        )
    }
}

/** The width [RowIcon] takes with its gap — the indent of a note under an icon row's label. */
@Composable
private fun rowIconGutter(ui: MiseUiState) =
    with(LocalDensity.current) { maxOf((ui.fontSize * 1.25f).sp.toDp(), 18.dp) } * 1.5f

/** The dim explanatory line that sits under a toggle row. */
@Composable
private fun RowNote(ui: MiseUiState, text: String) {
    Text(
        text = text,
        color = Color(ui.textDimColor),
        fontSize = ui.labelSize.sp,
        modifier = Modifier.padding(
            start = rowIndent(ui, false) + rowIconGutter(ui),
            end = 16.dp,
            bottom = ui.rowPadding.dp
        )
    )
}

@Composable
private fun rowIndent(ui: MiseUiState, level2: Boolean) =
    (BASE_INDENT + ui.indentStep * (if (level2) 3 else 2)).dp

@Composable
private fun RowTitle(ui: MiseUiState, title: String, summary: String?, warn: Boolean = false) {
    Text(text = title, color = Color(ui.textColor), fontSize = ui.fontSize.sp)
    if (summary != null) {
        Text(
            text = summary,
            color = if (warn) Color(ui.warnColor) else Color(ui.textDimColor),
            fontSize = ui.labelSize.sp
        )
    }
}

/**
 * A row that does something when tapped — opens a panel, runs an action. It is an outlined pill
 * as wide as its label, never stretched across the line and never filled, with its explanation
 * beneath in the dim colour, aligned with the label. The pill alone is the touch target.
 *
 * @param main The main action of its section: 3 dp frame and a bold label. Any other: 1.25 dp.
 * @param opens The row opens another screen, picker or panel: a › at the pill's right end.
 */
@Composable
private fun NavigationRow(
    ui: MiseUiState,
    title: String,
    summary: String? = null,
    summaryIsWarning: Boolean = false,
    level2: Boolean = false,
    main: Boolean = false,
    opens: Boolean = false,
    @DrawableRes icon: Int = R.drawable.ic_menu_settings,
    slot: ColorSlot = ColorSlot.MENU_VIEW,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = rowIndent(ui, level2),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp
            )
    ) {
        val weight = if (main) FontWeight.Bold else null
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .border(if (main) 3.dp else 1.25.dp, Color(ui.borderColor), CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = PILL_INSET.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RowIcon(ui, icon, slot)
            Text(
                text = title,
                color = Color(ui.textColor),
                fontSize = ui.fontSize.sp,
                fontWeight = weight
            )
            if (opens) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "›",
                    color = Color(ui.textColor),
                    fontSize = ui.fontSize.sp,
                    fontWeight = weight
                )
            }
        }
        if (summary != null) {
            Text(
                text = summary,
                color = Color(if (summaryIsWarning) ui.warnColor else ui.textDimColor),
                fontSize = ui.labelSize.sp,
                modifier = Modifier.padding(start = PILL_INSET.dp + rowIconGutter(ui), top = 3.dp)
            )
        }
    }
}

/** What a pill keeps between its frame and its label — and so the indent of its explanation. */
private const val PILL_INSET = 16

/** A switch row: plain, the whole row the touch target, the outlined house switch at the right. */
@Composable
private fun ToggleRow(
    ui: MiseUiState,
    title: String,
    checked: Boolean,
    level2: Boolean = false,
    @DrawableRes icon: Int = R.drawable.ic_check,
    slot: ColorSlot = ColorSlot.MENU_VIEW,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(
                start = rowIndent(ui, level2),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(ui, icon, slot)
        Column(Modifier.weight(1f).padding(end = 8.dp)) { RowTitle(ui, title, null) }
        MiseSwitch(checked = checked)
    }
}

/** A slider row: title, the live value, and a track that reaches 0 wherever 0 means "none". */
@Composable
private fun SliderRow(
    ui: MiseUiState,
    title: String,
    value: Int,
    range: IntRange,
    unit: String = "",
    step: Int = 1,
    level2: Boolean = false,
    @DrawableRes icon: Int = R.drawable.ic_tune,
    slot: ColorSlot = ColorSlot.MENU_VIEW,
    onChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = rowIndent(ui, level2),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(ui, icon, slot)
            Text(
                text = title,
                color = Color(ui.textColor),
                fontSize = ui.fontSize.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$value$unit",
                color = Color(ui.accentColor),
                fontSize = ui.labelSize.sp
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).toInt() * step) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (step > 1) ((range.last - range.first) / step) - 1 else 0,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color(ui.accentColor),
                activeTrackColor = Color(ui.accentColor),
                inactiveTrackColor = Color(ui.textDimColor).copy(alpha = 0.4f)
            )
        )
    }
}

/** A colour row: the swatch is the preview, tapping it opens the RGBA picker. */
@Composable
private fun ColorRow(ui: MiseUiState, title: String, slot: ColorSlot, level2: Boolean = false) {
    var showPicker by remember { mutableStateOf(false) }
    val color = ui.colorOf(slot)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .padding(
                start = rowIndent(ui, level2),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        RowIcon(ui, R.drawable.ic_mise_swatch, ColorSlot.MENU_VIEW)
        Column(Modifier.weight(1f)) {
            RowTitle(ui, title, "#%08X".format(color))
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(ui.cornerRadius.dp))
                .background(Color(color))
                .border(
                    width = (if (ui.borderWidth > 0) ui.borderWidth else 1).dp,
                    color = Color(ui.borderColor),
                    shape = RoundedCornerShape(ui.cornerRadius.dp)
                )
        )
    }

    if (showPicker) {
        MiseColorPickerDialog(
            title = title,
            initial = color,
            onDismiss = { showPicker = false },
            onPicked = {
                ui.updateColor(slot, it)
                showPicker = false
            }
        )
    }
}

/** The font row: shows the current family rendered in its own glyphs. */
@Composable
private fun FontRow(ui: MiseUiState) {
    var showPicker by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .padding(
                start = rowIndent(ui, false),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        RowIcon(ui, R.drawable.ic_mise_text, ColorSlot.MENU_VIEW)
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.mise_ui_font),
                color = Color(ui.textColor),
                fontSize = ui.fontSize.sp
            )
            Text(
                text = MiseFonts.displayName(context, ui.fontFamilyId),
                color = Color(ui.textDimColor),
                fontSize = ui.labelSize.sp,
                fontFamily = MiseFonts.family(context, ui.fontFamilyId)
            )
        }
    }

    if (showPicker) {
        MiseFontPickerDialog(onDismiss = { showPicker = false })
    }
}

// ---------------------------------------------------------------------- previews

@Composable
private fun PreviewFrame(ui: MiseUiState, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = rowIndent(ui, false), end = 16.dp, top = 4.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(ui.cornerRadius.dp))
            .background(Color(ui.surface))
            .then(
                if (ui.borderWidth > 0) {
                    Modifier.border(
                        ui.borderWidth.dp,
                        Color(ui.borderColor),
                        RoundedCornerShape(ui.cornerRadius.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(10.dp)
    ) { content() }
}

@Composable
private fun TypePreview(ui: MiseUiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val family = MiseFonts.family(context, ui.fontFamilyId)
    PreviewFrame(ui) {
        Column {
            Text(
                text = stringResource(R.string.mise_ui_preview_heading),
                color = Color(ui.accentColor),
                fontSize = ui.headingSize.sp,
                fontWeight = FontWeight(ui.headingWeight.coerceIn(100, 900)),
                fontFamily = family
            )
            Text(
                text = stringResource(R.string.mise_ui_preview_body),
                color = Color(ui.textColor),
                fontSize = ui.fontSize.sp,
                fontWeight = FontWeight(ui.fontWeight.coerceIn(100, 900)),
                fontStyle = if (ui.fontItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = family
            )
            Text(
                text = stringResource(R.string.mise_ui_preview_secondary),
                color = Color(ui.textDimColor),
                fontSize = ui.labelSize.sp,
                fontFamily = family
            )
        }
    }
}

@Composable
private fun ShapePreview(ui: MiseUiState) {
    PreviewFrame(ui) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(56.dp, 32.dp)
                    .clip(RoundedCornerShape(ui.cornerRadius.dp))
                    .background(Color(ui.background))
                    .border(
                        (if (ui.borderWidth > 0) ui.borderWidth else 0).dp,
                        Color(ui.borderColor),
                        RoundedCornerShape(ui.cornerRadius.dp)
                    )
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.mise_ui_preview_shape,
                        ui.cornerRadius,
                        ui.borderWidth
                    ),
                    color = Color(ui.textColor),
                    fontSize = ui.labelSize.sp
                )
                Spacer(Modifier.height(4.dp))
                if (ui.dividerWidth > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(ui.dividerWidth.dp)
                            .background(Color(ui.dividerColor))
                    )
                }
            }
        }
    }
}

@Composable
private fun IconPreview(ui: MiseUiState) {
    val shape = RoundedCornerShape(percent = (ui.iconRoundness / 2).coerceIn(0, 50))
    PreviewFrame(ui) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(ui.iconSize.dp)
                    .clip(shape)
                    .background(Color(ui.accentColor).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu_settings),
                    contentDescription = null,
                    tint = Color(ui.iconColor),
                    modifier = Modifier.size((ui.iconSize * 0.75f).dp)
                )
            }
            Text(
                text = stringResource(
                    R.string.mise_ui_preview_icon,
                    ui.iconSize,
                    ui.iconRoundness
                ),
                color = Color(ui.textColor),
                fontSize = ui.labelSize.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}
