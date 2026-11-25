/*
 * 白い熊 店 (shiroikuma-mise) fork: the app-language picker.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.mise

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.aurora.store.R
import com.aurora.store.compose.composable.AlertDialog
import com.aurora.store.compose.composable.TextButton
import com.aurora.store.mise.ColorSlot
import com.aurora.store.mise.LocalMiseUi
import com.aurora.store.mise.MiseLanguage

/**
 * The language list in the settled look: plain rows, only the language in force in a pill. Each
 * row has its selection ring, the language in its own words with the country beneath in the dim
 * colour, and the tag in dim monospace in a right-hand column of its own — also for a
 * right-to-left language. "System default" comes first; choosing a row applies it at once.
 */
@Composable
fun MiseLanguageDialog(onDismiss: () -> Unit) {
    val ui = LocalMiseUi.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val current = remember { MiseLanguage.tag(context) }
    val rows = remember {
        listOf(MiseLanguage.Entry("", "", "")) + MiseLanguage.entries(context)
    }
    val hairline = lerp(Color(ui.background), Color(ui.accentColor), 0.7f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(ui.surface),
        title = {
            Text(
                stringResource(R.string.mise_ui_section_language),
                color = Color(ui.accentColor),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            // The tag column stays at the right whatever the language of the app.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                    itemsIndexed(rows, key = { _, row -> row.tag }) { index, row ->
                        val selected = row.tag == current
                        val nextSelected = rows.getOrNull(index + 1)?.tag == current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (selected) 3.dp else 0.dp)
                                .clip(CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.border(3.dp, Color(ui.borderColor), CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    onDismiss()
                                    if (!selected) activity?.let { MiseLanguage.set(it, row.tag) }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val ring = Color(
                                if (selected) ui.accentColor else ui.colorOf(ColorSlot.SWITCH_OFF)
                            )
                            Box(
                                modifier = Modifier.size(18.dp).border(2.dp, ring, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Box(Modifier.size(8.dp).background(ring, CircleShape))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = row.name.ifEmpty {
                                        stringResource(R.string.mise_ui_language_system)
                                    },
                                    color = Color(ui.textColor),
                                    fontSize = ui.fontSize.sp,
                                    fontWeight = if (selected) FontWeight.Bold else null
                                )
                                if (row.country.isNotEmpty()) {
                                    Text(
                                        text = row.country,
                                        color = Color(ui.textDimColor),
                                        fontSize = ui.labelSize.sp
                                    )
                                }
                            }
                            if (row.tag.isNotEmpty()) {
                                Text(
                                    text = row.tag,
                                    color = Color(ui.textDimColor),
                                    fontSize = ui.labelSize.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        if (index < rows.lastIndex && !selected && !nextSelected) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 42.dp, end = 12.dp)
                                    .height(1.dp)
                                    .background(hairline)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
