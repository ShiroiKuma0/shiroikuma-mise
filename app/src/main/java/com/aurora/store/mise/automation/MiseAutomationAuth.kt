/*
 * 白い熊 店 (shiroikuma-mise) fork: the token gate for the 保存復元 automation contract.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise.automation

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Master switch + shared secret for the headless export 白い熊 自由作業盤 triggers.
 *
 * The switch is **off by default**: nothing is reachable from outside until 白い熊 turns it on.
 * The token is 24 SecureRandom bytes, hex-encoded, generated lazily on first read so the settings
 * row always has something to show and to copy.
 *
 * These live in their **own** preferences file, which is deliberately not part of any backup
 * category — a token must never travel inside an export ZIP.
 */
object MiseAutomationAuth {

    private const val PREFS = "mise_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }

    /** The token, minted on first read so the row is never empty. */
    fun token(context: Context): String {
        val stored = prefs(context).getString(KEY_TOKEN, null)
        if (!stored.isNullOrBlank()) return stored
        return regenerate(context)
    }

    fun regenerate(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit { putString(KEY_TOKEN, token) }
        return token
    }

    /** Constant-time compare — a token check must not leak its answer through timing. */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            candidate.toByteArray(),
            token(context).toByteArray()
        )
    }

    /** `80922d8c…4c49a87c` — what the settings row shows. */
    fun abbreviated(token: String): String =
        if (token.length <= 20) token else "${token.take(8)}…${token.takeLast(8)}"
}
