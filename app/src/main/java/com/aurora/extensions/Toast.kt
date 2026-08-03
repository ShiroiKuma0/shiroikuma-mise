/*
 * SPDX-FileCopyrightText: 2021 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.extensions

import android.content.Context
import androidx.fragment.app.Fragment
import com.aurora.store.mise.MiseFlash

// 白い熊 店 fork: every toast in the app funnels through here, and here it is the black-yellow
// flash rather than the platform's grey pill. Call sites are unchanged.

fun Context.toast(resId: Int) = runOnUiThread { MiseFlash.show(this, resId) }

fun Context.toast(text: CharSequence) = runOnUiThread { MiseFlash.show(this, text) }

fun Fragment.toast(resId: Int) = requireContext().toast(resId)

fun Fragment.toast(text: CharSequence) = requireContext().toast(text)
