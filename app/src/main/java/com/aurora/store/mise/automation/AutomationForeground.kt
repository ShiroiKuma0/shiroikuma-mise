/*
 * 白い熊 店 (shiroikuma-mise) fork: what to answer when a foreground start is refused.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise.automation

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService

/**
 * The one place that decides how a refused foreground start is reported.
 *
 * There are **four** call sites that can be refused — the §1 receiver's `startForegroundService`,
 * the §1 service's own `startForeground`, the §2a provider's service start, and the §2a service's
 * own `startForeground` — and a broadcast or a provider `call()` is a BACKGROUND start on API 31+,
 * so every one of them can throw `ForegroundServiceStartNotAllowedException`. The allowance comes
 * from recent interaction, which is why none of this appears by hand: open the app, run a backup,
 * it works. It fails in the cold unattended batch and on a restore onto a clean phone — the case
 * the contract exists for — so the fault is inversely correlated with anyone watching.
 *
 * The decision lives here rather than at each site for the reason the gate does: four copies of a
 * two-branch rule is how they drift apart.
 */
object AutomationForeground {

    /**
     * The keyed refusal 保存中核 matches to put a 「電池最適化を除外」 button on the failed row.
     *
     * **Reserved for the case that button can actually fix**, which is the only thing that makes it
     * trustworthy. On EMUI a refused start can equally be アプリ起動管理 sitting on 自動管理, which
     * no app can change for itself — and a button that cannot repair the fault is worse than a line
     * that names it, because it turns one dead end into two (shiroikuma-handyrss, 2026-09-04).
     */
    const val NO_FOREGROUND_START = "ERROR:no-foreground-start"

    /**
     * The reply for a refused start: the key when the battery-optimisation exemption would fix it,
     * and the exception itself when it would not.
     *
     * The test is deliberately positive — the key is used **only** when we determined the app is
     * genuinely not exempt. If the check throws, or there is no `PowerManager`, we do not know that
     * the exemption is the fault and must not claim a repair we cannot promise, so the descriptive
     * line wins.
     */
    fun refusal(context: Context, exception: Throwable?): String {
        val exemptionWouldHelp = runCatching {
            context.getSystemService<PowerManager>()
                ?.isIgnoringBatteryOptimizations(context.packageName) == false
        }.getOrDefault(false)
        return if (exemptionWouldHelp) NO_FOREGROUND_START else "ERROR:${oneLine(exception)}"
    }

    /**
     * A reply carries exactly ONE line, and `LIST_CATEGORIES` answers are newline-delimited — so a
     * message with a newline in it corrupts the wire format rather than merely reading badly. The
     * catch is on `Exception`, so `mAllowStartForeground false` being single-line is luck, not a
     * guarantee.
     */
    fun oneLine(exception: Throwable?): String =
        (exception?.message ?: exception?.javaClass?.simpleName ?: "foreground service refused")
            .replace('\n', ' ')
            .replace('\r', ' ')
}
