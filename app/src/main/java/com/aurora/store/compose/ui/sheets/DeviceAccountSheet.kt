/*
 * SPDX-FileCopyrightText: 2026 白い熊
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.aurora.store.compose.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.aurora.store.compose.composable.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.aurora.store.R
import com.aurora.store.compose.composable.HorizontalDivider

/**
 * Picks one of the Google accounts already present on-device (via microG).
 *
 * This exists so the sign-in flow stays inside our own theme: the framework's
 * [android.accounts.AccountManager.newChooseAccountIntent] dialog runs in the system process
 * and cannot be styled from here. Adding a *new* account still has to hand off to microG's own
 * activity — that is [onAddAccount], and it is the only path that leaves our look behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAccountSheet(
    emails: List<String>,
    onSelect: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensionResource(R.dimen.spacing_small))
        ) {
            Text(
                text = stringResource(R.string.account_choose),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    horizontal = dimensionResource(R.dimen.spacing_large),
                    vertical = dimensionResource(R.dimen.spacing_small)
                )
            )

            HorizontalDivider(
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.spacing_xsmall))
            )

            emails.forEach { email ->
                AccountRow(
                    iconRes = R.drawable.ic_account,
                    label = email,
                    onClick = { onSelect(email) }
                )
            }

            if (emails.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(
                        vertical = dimensionResource(R.dimen.spacing_xsmall)
                    )
                )
            }

            AccountRow(
                iconRes = R.drawable.ic_google,
                label = stringResource(R.string.account_add),
                onClick = onAddAccount
            )

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun AccountRow(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimensionResource(R.dimen.spacing_large),
                vertical = dimensionResource(R.dimen.spacing_medium)
            ),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_medium)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
