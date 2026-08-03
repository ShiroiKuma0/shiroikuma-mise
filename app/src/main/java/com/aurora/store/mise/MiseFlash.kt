/*
 * 白い熊 店 (shiroikuma-mise) fork: the black-yellow info flash that replaces every system toast.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/**
 * A toast in the house scheme — black ground, yellow text, yellow rounded border — built from the
 * same knobs the 白い熊 店 UI page edits, so a colour or corner change moves the flashes too.
 *
 * Nothing user-facing should call [Toast.makeText] directly: a grey system pill on a black-yellow
 * app is exactly the thing the house rule forbids. This is the raikidoban `Flash` pattern ported
 * to Kotlin.
 *
 * **One platform limit worth knowing:** since Android 11 a custom toast view is only honoured
 * while the app is in the **foreground**; from the background the system substitutes its own plain
 * text toast and ignores the view. Every flash here is raised in response to something 白い熊 just
 * did on screen, so in practice they all render themed — but a toast posted from a worker or a
 * receiver will look like the platform's, and that is the OS's call, not a bug here.
 */
object MiseFlash {

    fun show(context: Context, text: CharSequence, duration: Int = Toast.LENGTH_SHORT) =
        make(context, text, duration).show()

    fun show(context: Context, resId: Int, duration: Int = Toast.LENGTH_SHORT) =
        show(context, context.getString(resId), duration)

    /** Like [show] but centred on the screen rather than along the bottom edge. */
    fun showCentered(context: Context, text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
        make(context, text, duration).apply { setGravity(Gravity.CENTER, 0, 0) }.show()
    }

    /** Builds the themed toast without showing it, for callers that need the object itself. */
    @Suppress("DEPRECATION") // setView: the only way to theme a toast; see the KDoc.
    fun make(context: Context, text: CharSequence, duration: Int = Toast.LENGTH_SHORT): Toast {
        val config = MiseUiConfig(context)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val view = TextView(context).apply {
            this.text = text
            setTextColor(config.textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (config.fontSize - 1).coerceAtLeast(10).toFloat())
            typeface = MiseFonts.typeface(context, config.fontFamily) ?: Typeface.DEFAULT
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(config.background)
                cornerRadius = config.cornerRadius * density
                // A border width of 0 really means none — same as everywhere else in the app.
                if (config.borderWidth > 0) {
                    setStroke(dp(config.borderWidth), config.borderColor)
                }
            }
        }

        return Toast(context.applicationContext).apply {
            this.duration = duration
            @Suppress("DEPRECATION")
            this.view = view
        }
    }
}
