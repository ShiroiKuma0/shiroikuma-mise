/*
 * 白い熊 店 (shiroikuma-mise) fork: house-styled popup menus.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composable

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.aurora.store.mise.ColorSlot
import com.aurora.store.mise.LocalMiseUi

/**
 * What a menu row does — which decides the colour of its icon. A menu is read by colour before
 * it is read by word, so the icon carries the meaning while frame and label stay in the text
 * colour. Each role is a settable colour on the UI page.
 */
enum class MenuRole(val slot: ColorSlot) {
    /** Views something, opens a page, settings. */
    VIEW(ColorSlot.MENU_VIEW),

    /** Connects, downloads, synchronises, refreshes. */
    CONNECT(ColorSlot.MENU_CONNECT),

    /** Is about an account or a person. */
    CONTACT(ColorSlot.MENU_CONTACT),

    /** Copies, shares, exports, imports. */
    SHARE(ColorSlot.MENU_SHARE),

    /** Blocks, holds back, cancels. */
    BLOCK(ColorSlot.MENU_BLOCK),

    /** Deletes, removes. */
    DELETE(ColorSlot.WARN)
}

/**
 * Drop-in replacement for Material 3's `DropdownMenu`, with the house border and surface.
 *
 * A file opts in by importing this and [DropdownMenuItem] instead of the
 * `androidx.compose.material3` ones, exactly as with [MiseButtons.kt].
 *
 * The rule that a menu never scrolls while the screen has room needs nothing here: Compose's
 * menu positioner already tries below the anchor, above it, and then against the window edge
 * before it lets the content scroll, so a long menu opened low on the screen uses the whole
 * height.
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = Color(LocalMiseUi.current.surface),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    content: @Composable ColumnScope.() -> Unit
) = androidx.compose.material3.DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    offset = offset,
    scrollState = scrollState,
    properties = properties,
    shape = shape,
    containerColor = containerColor,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
    border = houseBorder(),
    content = content
)

/**
 * A menu row in the house look. Takes Material 3's `DropdownMenuItem` call shape — `text`,
 * `onClick`, `enabled`, `leadingIcon`, `trailingIcon` — so switching a file over is a change of
 * import, plus what Material has no word for:
 *
 * @param icon The row's icon. Every action row has one; [leadingIcon] wins when both are given.
 * @param role What the row does, which colours the icon.
 * @param opens The row opens a screen, dialog, picker or the browser: a › at its right end.
 * @param destructive The row deletes: frame, icon and label take the warning colour.
 * @param checked Non-null makes this a **toggle row**: plain, no pill, 36 dp tall, with the
 *   outlined switch at the right. [onClick] flips it.
 * @param iconColumn `false` for a list of pure choices (sort orders, thresholds), where no row
 *   has an icon and none keeps the column.
 *
 * An action row is an outlined pill spanning the menu's width: 1.25 dp frame, normal weight,
 * the icon 14 dp inside it, 8 dp to each side of the menu and 3 dp above and below. A disabled
 * row is the same pill at 38 %. The whole pill is the touch target and carries the ripple.
 */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    role: MenuRole = MenuRole.VIEW,
    opens: Boolean = false,
    destructive: Boolean = false,
    checked: Boolean? = null,
    iconColumn: Boolean = true,
    interactionSource: MutableInteractionSource? = null
) {
    val ui = LocalMiseUi.current
    val labelColor = Color(if (destructive) ui.warnColor else ui.textColor)
    val frameColor = Color(if (destructive) ui.warnColor else ui.borderColor)
    val iconColor = Color(ui.colorOf(if (destructive) ColorSlot.WARN else role.slot))
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val isToggle = checked != null

    val rowModifier = if (isToggle) {
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toggleable(
                value = checked == true,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactions,
                indication = ripple(color = labelColor),
                onValueChange = { onClick() }
            )
            .height(36.dp)
            // 8 dp menu inset + the 14 dp a pill keeps inside, so labels share one left edge
            // with the pills above; the switch ends on the pills' right edge.
            .padding(start = 22.dp, end = 8.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .border(1.25.dp, frameColor, CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactions,
                indication = ripple(color = labelColor),
                onClick = onClick
            )
            .heightIn(min = 42.dp)
            .padding(horizontal = 14.dp)
    }

    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        if (iconColumn) {
            Box(modifier = Modifier.requiredSize(24.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    when {
                        leadingIcon != null -> leadingIcon()
                        icon != null -> Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        CompositionLocalProvider(
            LocalContentColor provides labelColor,
            LocalTextStyle provides MaterialTheme.typography.bodyLarge
        ) {
            Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) { text() }
            trailingIcon?.invoke()
            when {
                isToggle -> MiseSwitch(checked = checked == true, compact = true)
                opens -> Text(text = "›")
            }
        }
    }
}

/**
 * The line between two groups of a menu: 1 dp in an opaque darker accent — 70 % of the way from
 * the background to the accent — inset so its ends meet the pill edges. The stock hairline is
 * all but invisible on black.
 */
@Composable
fun MenuDivider(modifier: Modifier = Modifier) {
    val ui = LocalMiseUi.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .height(1.dp)
            .background(lerp(Color(ui.background), Color(ui.accentColor), 0.7f))
    )
}

/**
 * The house switch: an outlined pill with a solid dot — right and in the accent when on, left
 * and in the opaque dim "switch off" colour when off. Never filled, never a white ring. It only
 * draws; the row that holds it is the touch target.
 *
 * @param compact The smaller 44 × 24 dp form that sits in a menu row.
 */
@Composable
fun MiseSwitch(checked: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ui = LocalMiseUi.current
    val color = Color(if (checked) ui.accentColor else ui.colorOf(ColorSlot.SWITCH_OFF))
    val height = if (compact) 24.dp else 28.dp
    Box(
        modifier = modifier
            .requiredSize(width = if (compact) 44.dp else 52.dp, height = height)
            .border(2.dp, color, CircleShape)
            .padding(5.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.requiredSize(height - 10.dp).background(color, CircleShape))
    }
}

private const val DISABLED_ALPHA = 0.38f
