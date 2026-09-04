/*
 * 白い熊 店 (shiroikuma-mise) fork: the gate for the 保存復元 automation contract.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise.automation

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The external-automation gate: a master switch, and a token that is now OPTIONAL.
 *
 * ## What changed in contract v2 (白い熊, 2026-09-04)
 *
 * v1 shipped this app closed: [enabled] defaulted to false and a caller also had to present a
 * 48-character secret pasted out of these settings. That is the wrong shape for where the family is
 * going — **a pasted secret cannot survive a wipe**, and the case the whole contract now exists to
 * serve is 白い熊 応用管理 restoring apps *and their data* onto a clean phone, where nothing has been
 * configured and nobody has pasted anything. A gate that only works once the phone is already set up
 * is no gate for setting the phone up.
 *
 * So [enabled] defaults to **true**, and [requireToken] is a new, separate switch defaulting to
 * **false**. The token still exists, still regenerates, still never leaves the phone — it is simply
 * opt-in now.
 *
 * ## Idempotent about the token — required, not a nicety
 *
 * **A token handed to an app that does not require one is IGNORED, never an error.** Tokens live in
 * task arguments and workspace variables that outlive the setting they were pasted for; a caller
 * still sending one — because it was configured last year, or because another app on the batch does
 * want one — must be served. Refusing it would turn "白い熊 turned a switch off" into "half the batch
 * mysteriously fails", which is precisely the friction the switch exists to remove.
 *
 * ## Device-local by design
 *
 * These live in their **own** preferences file, which is deliberately not part of any backup
 * category — a token must never travel inside an export ZIP or reach another phone.
 */
object MiseAutomationAuth {

    private const val PREFS = "mise_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether this app answers automation at all. **Default true** since contract v2.
     *
     * Kept as a switch rather than removed: it is the only way to close this app off entirely, and a
     * feature that can be turned on but never off is one 白い熊 cannot retreat from.
     */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }

    /** Whether a caller must also present [token]. **Default false** — the token is opt-in now. */
    fun requireToken(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    fun setRequireToken(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_REQUIRE_TOKEN, value) }

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

    /**
     * The whole gate, in the one place every entry point asks.
     *
     * Returns null to proceed, or the exact `ERROR:` string to answer with. Written as one function
     * so no receiver, provider or service can implement the two checks in a subtly different order —
     * which is how "disabled" and "bad token" drift apart across forty-two apps. The two stay
     * distinct errors because they debug differently.
     */
    fun refuse(context: Context, candidate: String?): String? = when {
        !enabled(context) -> "ERROR:automation disabled"
        requireToken(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
        else -> null
    }

    /** `80922d8c…4c49a87c` — what the settings row shows. */
    fun abbreviated(token: String): String =
        if (token.length <= 20) token else "${token.take(8)}…${token.takeLast(8)}"
}
