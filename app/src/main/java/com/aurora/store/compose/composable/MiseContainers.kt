/*
 * 白い熊 店 (shiroikuma-mise) fork: dialogs and sheets that carry the house border.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.aurora.store.mise.LocalMiseUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drop-in replacements for Material 3's dialog and bottom-sheet composables, identical in
 * signature but with **the house border on by default**.
 *
 * Sheets and dialogs are the one place where upstream's surface sits on our black ground with
 * nothing marking where it begins — the container colour is near-black too, so a sheet appears
 * to bleed into the page behind it. A yellow outline gives it an edge.
 *
 * A file opts in by importing these instead of the `androidx.compose.material3` ones, exactly as
 * with [MiseButtons.kt] and [MiseDividers.kt]; call sites are untouched. The stroke follows the
 * UI page's knobs, so a border width of 0 really means "no border".
 */
@Composable
fun houseBorder(): BorderStroke? {
    val ui = LocalMiseUi.current
    if (ui.borderWidth <= 0) return null
    return BorderStroke(ui.borderWidth.dp, Color(ui.borderColor))
}

/**
 * A bordered bottom sheet.
 *
 * The border cannot ride on `modifier`: Material forwards that modifier to the sheet *above* the
 * anchored-drag modifier, which reports the sheet's size but places it at the drag offset — so a
 * `Modifier.border` there paints at the top of the screen rather than around the sheet. Instead we
 * make Material's own surface transparent and paint a bordered [Surface] inside it, which is laid
 * out with the sheet and therefore always lines up.
 *
 * Taking over the surface means taking over the drag handle too (Material renders it as a sibling
 * of the content, inside the surface we are replacing), so the handle's accessibility actions are
 * re-attached here — that is what the [semantics] block below is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = null,
        // Material pads for the system bars *outside* its surface, which here would leave a
        // transparent strip below the border. We take the same insets inside instead, so the
        // background still runs to the screen edge and the outline stays on the sheet.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = properties
    ) {
        val insets = contentWindowInsets()
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            border = houseBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(insets)
            ) {
                if (dragHandle != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .sheetHandleSemantics(sheetState, scope, onDismissRequest)
                    ) {
                        dragHandle()
                    }
                }
                content()
            }
        }
    }
}

/** The dismiss / expand / collapse actions Material attaches to a sheet's drag handle. */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.sheetHandleSemantics(
    sheetState: SheetState,
    scope: CoroutineScope,
    onDismissRequest: () -> Unit
) = semantics(mergeDescendants = true) {
    dismiss {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismissRequest()
        }
        true
    }
    if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
        if (sheetState.hasExpandedState) {
            expand {
                scope.launch { sheetState.expand() }
                true
            }
        }
    } else if (sheetState.hasPartiallyExpandedState) {
        collapse {
            scope.launch { sheetState.partialExpand() }
            true
        }
    }
}

/**
 * A bordered alert dialog. Here `modifier` *is* the right place for the stroke: Material forwards
 * it straight to the dialog's surface, and a dialog is centred by its window rather than by an
 * offsetting modifier, so the border lands on the container's own shape.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) = androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = confirmButton,
    modifier = houseBorder()?.let { modifier.border(it, shape) } ?: modifier,
    dismissButton = dismissButton,
    icon = icon,
    title = title,
    text = text,
    shape = shape,
    containerColor = containerColor,
    iconContentColor = iconContentColor,
    titleContentColor = titleContentColor,
    textContentColor = textContentColor,
    tonalElevation = tonalElevation,
    properties = properties
)
