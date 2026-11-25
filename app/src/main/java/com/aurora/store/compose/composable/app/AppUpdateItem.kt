/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composable.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aurora.store.compose.composable.DropdownMenu
import com.aurora.store.compose.composable.DropdownMenuItem
import com.aurora.store.compose.composable.MenuRole
import com.aurora.store.compose.composable.houseMainButtonBorder
import com.aurora.store.mise.ColorSlot
import com.aurora.store.mise.LocalMiseUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.aurora.store.R
import com.aurora.store.compose.preview.ThemePreviewProvider
import com.aurora.store.data.model.DownloadStatus
import com.aurora.store.data.room.download.Download
import com.aurora.store.data.room.update.Update
import com.aurora.store.util.CommonUtil
import com.aurora.store.compose.composable.OutlinedButton

/**
 * Fork: where a held app's offered update stands against the last version that was tried.
 * Only apps held at their installed version ("ignore all updates") have one.
 */
sealed interface HeldState {
    /**
     * The offered version has not been tried. [lastSkippedName] / [lastSkippedAt] name the
     * version rejected before it, when there is one.
     */
    data class Untested(
        val lastSkippedName: String? = null,
        val lastSkippedAt: Long? = null
    ) : HeldState

    /** The offered version is the very one already tried and rejected, at [testedAt]. */
    data class Skipped(val testedAt: Long? = null) : HeldState
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppUpdateItem(
    modifier: Modifier = Modifier,
    update: Update,
    download: Download? = null,
    isChecking: Boolean = false,
    isFrozen: Boolean = false,
    onClick: () -> Unit = {},
    onUpdate: () -> Unit = {},
    onCancel: () -> Unit = {},
    onUnignore: (() -> Unit)? = null,
    heldState: HeldState? = null,
    onSkip: () -> Unit = {},
    onTestAgain: () -> Unit = {},
    onDetails: () -> Unit = {}
) {
    // Only the INSTALLING status shows as "Installing"; a downloaded-but-not-installed
    // (COMPLETED) app falls back to the "Update" action. isChecking is the pre-download
    // tracker check, shown with the same indeterminate progress + Cancel as an active download.
    val inProgress = isChecking ||
        (download != null && !download.isFinished && !download.isAwaitingInstall)
    val installing = download?.status == DownloadStatus.INSTALLING
    val progress = if (download?.status == DownloadStatus.DOWNLOADING) {
        download.progress.toFloat()
    } else {
        0f
    }
    // A frozen app's update is offered like any other; the italics are what tell them apart.
    val fontStyle = if (isFrozen) FontStyle.Italic else null
    // Fork: a version already tried and rejected steps back — dim, its number struck through.
    val skipped = heldState is HeldState.Skipped
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var menuOpen by remember { mutableStateOf(false) }
    val iconSize = dimensionResource(R.dimen.icon_size_medium)
    val gap = dimensionResource(R.dimen.spacing_medium)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // An ignored row has no update sheet to open; a tap or a long-press shows
                // what can be done with it instead.
                if (onUnignore != null) {
                    Modifier.combinedClickable(
                        onClick = { menuOpen = true },
                        onLongClick = { menuOpen = true }
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(
                horizontal = gap,
                vertical = dimensionResource(R.dimen.spacing_small)
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .requiredSize(iconSize)
                    .alpha(if (skipped) 0.5f else 1f)
            ) {
                AnimatedAppIcon(
                    modifier = Modifier.requiredSize(iconSize),
                    iconUrl = update.iconURL,
                    inProgress = inProgress,
                    progress = progress
                )
            }
            Spacer(Modifier.width(gap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = update.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = fontStyle,
                    color = if (skipped) dim else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = update.developerName,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = fontStyle,
                    color = dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                textDecoration = if (skipped) {
                                    TextDecoration.LineThrough
                                } else {
                                    null
                                }
                            )
                        ) { append(update.versionName) }
                        append("  •  ${CommonUtil.addSiPrefix(update.size)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = fontStyle,
                    color = dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(dimensionResource(R.dimen.spacing_small)))
            Box {
                when {
                    installing -> {
                        OutlinedButton(onClick = {}, enabled = false) {
                            Text(stringResource(R.string.action_installing))
                        }
                    }

                    inProgress -> {
                        OutlinedButton(onClick = onCancel) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }

                    // Fork: a held app's untested version — try it, or mark it as rejected.
                    // "Test" is the main action of the row: the thick frame and bold label.
                    heldState is HeldState.Untested -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(
                                dimensionResource(R.dimen.spacing_small)
                            )
                        ) {
                            OutlinedButton(onClick = onUpdate, border = houseMainButtonBorder()) {
                                Text(
                                    text = stringResource(R.string.action_test),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(onClick = onSkip) {
                                Text(stringResource(R.string.action_skip_version))
                            }
                        }
                    }

                    onUnignore != null -> {
                        OutlinedButton(onClick = onUnignore) {
                            Text(stringResource(R.string.action_unignore))
                        }
                    }

                    // Outlined, not filled: black ground, yellow label, yellow border — the same
                    // shape as the row's other actions, so the column reads as one set of buttons.
                    else -> {
                        OutlinedButton(onClick = onUpdate) {
                            Text(stringResource(R.string.action_update))
                        }
                    }
                }

                if (onUnignore != null) {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.updates_app_details)) },
                            onClick = {
                                menuOpen = false
                                onDetails()
                            },
                            icon = R.drawable.ic_about,
                            opens = true
                        )
                        if (skipped) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_test_again)) },
                                onClick = {
                                    menuOpen = false
                                    onTestAgain()
                                },
                                icon = R.drawable.ic_refresh,
                                role = MenuRole.CONNECT
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_unignore)) },
                            onClick = {
                                menuOpen = false
                                onUnignore()
                            },
                            icon = R.drawable.ic_visibility
                        )
                    }
                }
            }
        }

        if (heldState != null) {
            HeldStatus(
                state = heldState,
                modifier = Modifier.padding(start = iconSize + gap, top = 2.dp)
            )
        }
    }
}

/**
 * Fork: the two lines under a held app's row that say whether its offered version still needs
 * trying. They sit below the row rather than in its text column, so they keep the full width
 * when the pills at the right leave the column narrow.
 */
@Composable
private fun HeldStatus(state: HeldState, modifier: Modifier = Modifier) {
    val ui = LocalMiseUi.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val bold = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                is HeldState.Untested -> Box(
                    Modifier
                        .requiredSize(12.dp)
                        .background(Color(ui.colorOf(ColorSlot.HELD_NEW)), CircleShape)
                )

                is HeldState.Skipped -> Icon(
                    painter = painterResource(R.drawable.ic_cancel),
                    contentDescription = null,
                    tint = Color(ui.colorOf(ColorSlot.HELD_SKIPPED)),
                    modifier = Modifier.requiredSize(16.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = when (state) {
                    is HeldState.Untested -> stringResource(R.string.held_untested)
                    is HeldState.Skipped -> state.testedAt?.let {
                        stringResource(R.string.held_skipped_on, formatDay(it))
                    } ?: stringResource(R.string.held_skipped)
                },
                style = bold,
                color = if (state is HeldState.Skipped) dim else Color.Unspecified
            )
        }
        when (state) {
            is HeldState.Untested -> if (state.lastSkippedName != null) {
                Text(
                    text = state.lastSkippedAt?.let {
                        stringResource(
                            R.string.held_last_skipped_on,
                            state.lastSkippedName,
                            formatDay(it)
                        )
                    } ?: stringResource(R.string.held_last_skipped, state.lastSkippedName),
                    style = MaterialTheme.typography.bodySmall,
                    color = dim
                )
            }

            is HeldState.Skipped -> Text(
                text = stringResource(R.string.held_skipped_desc),
                style = MaterialTheme.typography.bodySmall,
                color = dim
            )
        }
    }
}

private fun formatDay(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(epochMillis))

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun AppUpdateItemPreview() {
    val update = Update(
        packageName = "com.example.app",
        versionCode = 100,
        versionName = "1.0.0",
        displayName = "Example App",
        iconURL = "",
        changelog = "• Bug fixes<br>• Performance improvements",
        id = 1,
        developerName = "Developer",
        size = 5_000_000,
        updatedOn = "May 2025",
        hasValidCert = true,
        offerType = 1,
        fileList = emptyList(),
        sharedLibs = emptyList()
    )
    AppUpdateItem(update = update)
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun AppUpdateItemDownloadingPreview() {
    val update = Update(
        packageName = "com.example.app",
        versionCode = 100,
        versionName = "1.0.0",
        displayName = "Example App",
        iconURL = "",
        changelog = "",
        id = 1,
        developerName = "Developer",
        size = 5_000_000,
        updatedOn = "May 2025",
        hasValidCert = true,
        offerType = 1,
        fileList = emptyList(),
        sharedLibs = emptyList()
    )
    val download = Download.fromUpdate(update).copy(
        status = DownloadStatus.DOWNLOADING,
        progress = 45
    )
    AppUpdateItem(update = update, download = download)
}
