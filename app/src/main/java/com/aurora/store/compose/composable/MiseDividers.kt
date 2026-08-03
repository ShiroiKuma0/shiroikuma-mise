/*
 * 白い熊 店 (shiroikuma-mise) fork: dividers that obey the UI page's knobs.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aurora.store.mise.LocalMiseUi

/**
 * Drop-in replacements for Material 3's dividers, taking their thickness and colour from the
 * 白い熊 店 UI page.
 *
 * The colour already followed the theme (`outlineVariant` is mapped to the divider knob), but the
 * **thickness** did not: upstream's calls take Material's fixed 1 dp, so the page's divider slider
 * moved our own rules and nothing else. Here a width of **0 draws nothing at all**, which is what
 * the slider reaching 0 is supposed to mean.
 *
 * As with the buttons, a file opts in by importing these instead of the `androidx.compose.material3`
 * ones; call sites are untouched.
 */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = LocalMiseUi.current.dividerWidth.dp,
    color: Color = Color(LocalMiseUi.current.dividerColor)
) {
    if (thickness <= 0.dp) return
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = LocalMiseUi.current.dividerWidth.dp,
    color: Color = Color(LocalMiseUi.current.dividerColor)
) {
    if (thickness <= 0.dp) return
    androidx.compose.material3.VerticalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}
