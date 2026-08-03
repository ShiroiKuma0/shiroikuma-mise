/*
 * 白い熊 店 (shiroikuma-mise) fork: the UI page's dialogs — colour picker, font picker,
 * and the Export / Import panel.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.mise

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.aurora.store.compose.composable.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import com.aurora.store.compose.composable.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.store.mise.LocalMiseUi
import com.aurora.store.mise.MiseBackup
import com.aurora.store.mise.MiseFonts
import com.aurora.store.mise.MiseUiState
import com.jakewharton.processphoenix.ProcessPhoenix
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ colour picker

/**
 * The house colour picker: a row of one-click swatches pre-filled with the colours already in
 * use, then four RGBA sliders over a live preview of exactly what they mix.
 */
@Composable
fun MiseColorPickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onPicked: (Int) -> Unit
) {
    val ui = LocalMiseUi.current
    var alpha by remember { mutableIntStateOf(AndroidColor.alpha(initial)) }
    var red by remember { mutableIntStateOf(AndroidColor.red(initial)) }
    var green by remember { mutableIntStateOf(AndroidColor.green(initial)) }
    var blue by remember { mutableIntStateOf(AndroidColor.blue(initial)) }

    val current = AndroidColor.argb(alpha, red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(ui.surface),
        title = {
            Text(text = title, color = Color(ui.accentColor), fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                // One-click swatches: what was picked before, newest first.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ui.recentColors) { swatch ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(ui.cornerRadius.dp))
                                .background(Color(swatch))
                                .border(
                                    width = if (swatch == current) 3.dp else 1.dp,
                                    color = Color(ui.borderColor),
                                    shape = RoundedCornerShape(ui.cornerRadius.dp)
                                )
                                .clickable {
                                    alpha = AndroidColor.alpha(swatch)
                                    red = AndroidColor.red(swatch)
                                    green = AndroidColor.green(swatch)
                                    blue = AndroidColor.blue(swatch)
                                }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Live preview of the mix, over the app background so alpha is honest.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(ui.cornerRadius.dp))
                        .background(Color(ui.background))
                        .border(
                            1.dp,
                            Color(ui.borderColor),
                            RoundedCornerShape(ui.cornerRadius.dp)
                        )
                ) {
                    Box(Modifier.fillMaxWidth().height(56.dp).background(Color(current)))
                }
                Text(
                    text = "#%08X".format(current),
                    color = Color(ui.textColor),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    textAlign = TextAlign.Center
                )

                ChannelSlider(ui, "A", alpha) { alpha = it }
                ChannelSlider(ui, "R", red) { red = it }
                ChannelSlider(ui, "G", green) { green = it }
                ChannelSlider(ui, "B", blue) { blue = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(current) }) {
                Text("OK", color = Color(ui.accentColor))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(ui.textDimColor))
            }
        }
    )
}

@Composable
private fun ChannelSlider(ui: MiseUiState, name: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            color = Color(ui.accentColor),
            modifier = Modifier.width(20.dp),
            fontSize = ui.fontSize.sp
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(ui.accentColor),
                activeTrackColor = Color(ui.accentColor),
                inactiveTrackColor = Color(ui.textDimColor).copy(alpha = 0.4f)
            )
        )
        Text(
            text = value.toString(),
            color = Color(ui.textColor),
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
            fontSize = ui.labelSize.sp
        )
    }
}

// -------------------------------------------------------------------- font picker

/** Every candidate font drawn in its own glyphs — you pick a font by looking at it. */
@Composable
fun MiseFontPickerDialog(onDismiss: () -> Unit) {
    val ui = LocalMiseUi.current
    val context = LocalContext.current
    val options = remember(ui.fontRevision) { MiseFonts.available(context) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val id = MiseFonts.import(context, uri)
            ui.onFontsChanged()
            if (id != null) ui.updateFontFamily(id)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(ui.surface),
        title = { Text("Font", color = Color(ui.accentColor), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(options) { option ->
                    val selected = option.id == ui.fontFamilyId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ui.updateFontFamily(option.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = option.displayName,
                                color = Color(if (selected) ui.accentColor else ui.textColor),
                                fontFamily = MiseFonts.family(context, option.id),
                                fontSize = (ui.fontSize + 2).sp
                            )
                            Text(
                                text = "白い熊 店 — AaBbCc 0123",
                                color = Color(ui.textDimColor),
                                fontFamily = MiseFonts.family(context, option.id),
                                fontSize = ui.labelSize.sp
                            )
                        }
                        if (option.id.isNotBlank() && !option.id.startsWith("@")) {
                            TextButton(onClick = {
                                MiseFonts.delete(context, option.id)
                                if (selected) ui.updateFontFamily(MiseFonts.SYSTEM)
                                ui.onFontsChanged()
                            }) {
                                Text("Remove", color = Color(ui.warnColor), fontSize = ui.labelSize.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { importLauncher.launch(arrayOf("font/*", "application/octet-stream")) }) {
                Text("Import font…", color = Color(ui.accentColor))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color(ui.textDimColor)) }
        }
    )
}

// ------------------------------------------------------------- export / import

/**
 * The Export / Import panel, in the Kōjiki flow and the kxkb box look: one bordered rounded box,
 * the backup folder as its own tappable box (red until it is set), the newest backup found there,
 * a category checklist, and an ArcaneChat-style button bar — Cancel alone on the left, Import and
 * Export grouped right, all fully round pills.
 */
@Composable
fun MiseExportImportPanel(onDismiss: () -> Unit, onFinishedAndClose: () -> Unit) {
    val ui = LocalMiseUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val checks = remember {
        SnapshotStateMap<MiseBackup.Cat, Boolean>().apply {
            MiseBackup.Cat.entries.forEach { put(it, it.onByDefault) }
        }
    }
    var lastBackup by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var info by remember { mutableStateOf<InfoState?>(null) }

    // The page queries the directory for the newest export as soon as it opens.
    LaunchedEffect(ui.exportDir) {
        lastBackup = ui.exportDir.takeIf { it.isNotBlank() }
            ?.let { MiseBackup.newestBackup(context, it.toUri()) }
    }

    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            ui.updateExportDir(uri.toString())
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selected = checks.filterValues { it }.keys
        // Room reads/writes are suspending, and the zip is real I/O — never on the main thread.
        scope.launch {
            info = runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                }
                val restored = MiseBackup.restore(context, bytes, selected)
                ui.reload()
                InfoState(
                    "Import finished",
                    "$restored categories restored.",
                    success = true,
                    restart = true
                )
            }.getOrElse {
                InfoState("Import failed", it.message ?: "Unknown error", success = false)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(ui.surface),
        title = {
            Text(
                text = "Export / Import",
                color = Color(ui.accentColor),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Carry every setting to another install: tick what to take, then Export " +
                        "to the backup folder or Import from a backup file.",
                    color = Color(ui.textDimColor),
                    fontSize = ui.labelSize.sp
                )
                Spacer(Modifier.height(10.dp))

                // Backup folder — red until it is set, yellow once it is.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ui.cornerRadius.dp))
                        .border(
                            2.dp,
                            Color(if (ui.exportDir.isBlank()) ui.warnColor else ui.borderColor),
                            RoundedCornerShape(ui.cornerRadius.dp)
                        )
                        .clickable { dirLauncher.launch(null) }
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Backup folder",
                            color = Color(ui.textDimColor),
                            fontSize = ui.labelSize.sp
                        )
                        Text(
                            text = if (ui.exportDir.isBlank()) {
                                "No backup directory set — tap to choose one"
                            } else {
                                Uri.decode(ui.exportDir.substringAfterLast('/'))
                            },
                            color = Color(
                                if (ui.exportDir.isBlank()) ui.warnColor else ui.accentColor
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = ui.fontSize.sp
                        )
                    }
                }
                Text(
                    text = lastBackup?.let {
                        "Last backup: ${it.first} (${MiseBackup.formatTimestamp(it.second)})"
                    } ?: "No backup found in this folder yet",
                    color = Color(ui.textDimColor),
                    fontSize = ui.labelSize.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                Divider(ui)
                MiseBackup.Cat.entries.forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checks[cat] = !(checks[cat] ?: false) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checks[cat] ?: false,
                            onCheckedChange = { checks[cat] = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(ui.accentColor),
                                uncheckedColor = Color(ui.borderColor),
                                checkmarkColor = Color(ui.background)
                            )
                        )
                        Text(
                            text = cat.label,
                            color = Color(ui.textColor),
                            fontSize = ui.fontSize.sp
                        )
                    }
                }
                Divider(ui)
            }
        },
        confirmButton = {
            // ArcaneChat bar: Cancel alone left, Import + Export grouped right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillButton(ui, "Cancel", onClick = onDismiss)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(ui, "Import") {
                        if (checks.none { it.value }) {
                            info = InfoState("Import failed", "No categories selected.", success = false)
                        } else {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        }
                    }
                    PillButton(ui, "Export") {
                        val selected = checks.filterValues { it }.keys
                        when {
                            selected.isEmpty() ->
                                info = InfoState(
                                    "Export failed", "No categories selected.", success = false
                                )

                            ui.exportDir.isBlank() ->
                                info = InfoState(
                                    "Export failed", "No backup directory set.", success = false
                                )

                            else -> scope.launch {
                                info = runCatching {
                                    val bytes = withContext(Dispatchers.IO) {
                                        ByteArrayOutputStream().also { out ->
                                            MiseBackup.writeZip(context, selected, out)
                                        }.toByteArray()
                                    }
                                    val name = withContext(Dispatchers.IO) {
                                        MiseBackup.writeToTree(context, ui.exportDir.toUri(), bytes)
                                    } ?: error("The backup folder could not be written to.")
                                    lastBackup = MiseBackup.newestBackup(context, ui.exportDir.toUri())
                                    InfoState(
                                        "Export finished",
                                        "$name\n${bytes.size / 1024} kB · ${selected.size} categories",
                                        success = true
                                    )
                                }.getOrElse {
                                    InfoState(
                                        "Export failed",
                                        it.message ?: "Unknown error",
                                        success = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    info?.let { state ->
        MiseInfoDialog(
            state = state,
            onAcknowledge = {
                info = null
                // A success closes the whole chain — this dialog, the panel under it, and the UI
                // page itself. A failure leaves the panel open so it can be corrected.
                if (state.success) onFinishedAndClose()
            },
            onRestart = { ProcessPhoenix.triggerRebirth(context) }
        )
    }
}

private data class InfoState(
    val title: String,
    val message: String,
    val success: Boolean,
    val restart: Boolean = false
)

/** Result dialog: yellow border, black ground — and it is what closes the chain. */
@Composable
private fun MiseInfoDialog(
    state: InfoState,
    onAcknowledge: () -> Unit,
    onRestart: () -> Unit
) {
    val ui = LocalMiseUi.current
    AlertDialog(
        onDismissRequest = onAcknowledge,
        containerColor = Color(ui.surface),
        modifier = Modifier.border(
            width = 2.dp,
            color = Color(if (state.success) ui.borderColor else ui.warnColor),
            shape = RoundedCornerShape(ui.cornerRadius.dp)
        ),
        title = {
            Text(
                text = state.title,
                color = Color(if (state.success) ui.accentColor else ui.warnColor),
                fontWeight = FontWeight.Bold
            )
        },
        text = { Text(text = state.message, color = Color(ui.textColor)) },
        confirmButton = {
            if (state.restart) {
                PillButton(ui, "Restart now", onClick = onRestart)
            } else {
                PillButton(ui, "OK", onClick = onAcknowledge)
            }
        },
        dismissButton = {
            if (state.restart) PillButton(ui, "Later", onClick = onAcknowledge)
        }
    )
}

@Composable
private fun PillButton(ui: MiseUiState, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Color(ui.background))
            .border(1.5.dp, Color(ui.borderColor), RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = Color(ui.accentColor), fontSize = ui.fontSize.sp)
    }
}

@Composable
private fun Divider(ui: MiseUiState) {
    if (ui.dividerWidth <= 0) return
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(ui.dividerWidth.dp)
            .background(Color(ui.dividerColor).copy(alpha = 0.4f))
    )
}

private fun String.toUri(): Uri = Uri.parse(this)
