/*
 * 白い熊 店 (shiroikuma-mise) fork: the 保存復元 automation entry point.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aurora.store.mise.MiseBackup

/**
 * The exported receiver 白い熊 自由作業盤 fires at, carrying the three contract actions.
 *
 * It does **no work**: it checks the switch and the token, answers `LIST_CATEGORIES` inline
 * (instant), hands `EXPORT_STATE` to a foreground service and returns at once, and signals a
 * running export for `CANCEL_EXPORT`. A manifest receiver must reach `finish()` inside Android's
 * broadcast window — running an export here is what gets an app ANR'd and killed mid-write.
 *
 * No `android:permission` on the receiver, and since contract v2 no token either unless 白い熊 asks
 * for one — this is deliberately the **unauthenticated** half of the surface. It only ever writes
 * where it was told to and reports what it did. Everything that moves data through a caller-supplied
 * descriptor lives behind [AutomationProvider], which knows who is calling.
 */
class StateExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN)

        when (action) {
            "${app.packageName}$ACTION_LIST_CATEGORIES" -> {
                val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION) ?: return
                val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE) ?: return
                val replyId = intent.getStringExtra(EXTRA_REPLY_ID) ?: return

                val result = MiseAutomationAuth.refuse(app, token) ?: ("OK:" + categoryLines())
                reply(app, replyAction, replyPackage, replyId, result)
            }

            "${app.packageName}$ACTION_EXPORT_STATE" -> {
                val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION) ?: return
                val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE) ?: return
                val replyId = intent.getStringExtra(EXTRA_REPLY_ID) ?: return

                MiseAutomationAuth.refuse(app, token)?.let { refusal ->
                    reply(app, replyAction, replyPackage, replyId, refusal)
                    return
                }

                // Validate `items` here, where an error is still cheap to report.
                val items = intent.getStringExtra(EXTRA_ITEMS).orEmpty()
                val unknown = items.split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() && MiseBackup.Cat.ofId(it) == null }
                if (unknown.isNotEmpty()) {
                    reply(
                        app, replyAction, replyPackage, replyId,
                        "ERROR:unknown category in items: ${unknown.joinToString(",")}"
                    )
                    return
                }

                // A broadcast is a BACKGROUND start on API 31+, so this throws
                // ForegroundServiceStartNotAllowedException unless the app happens to hold a
                // foreground-start allowance — and an exception escaping onReceive does not fail
                // the export, it kills the process ("Unable to start receiver …").
                //
                // The allowance comes from recent interaction, which is why this never shows up by
                // hand: open the app, run a backup, it works. The failure belongs to the cold,
                // unattended batch and to a restore onto a clean phone — the case this contract
                // exists for — so it is inversely correlated with anyone watching.
                //
                // Catching alone would only trade a crash for silence: the caller would wait out
                // its whole timeout and report "no response", which is indistinguishable from an
                // app that never implemented the contract. The ERROR: reply is the point.
                try {
                    ContextCompat.startForegroundService(
                        app,
                        Intent(app, StateExportService::class.java).apply {
                            putExtra(EXTRA_PATH, intent.getStringExtra(EXTRA_PATH))
                            putExtra(EXTRA_ITEMS, items)
                            putExtra(
                                EXTRA_PROGRESS_ACTION,
                                intent.getStringExtra(EXTRA_PROGRESS_ACTION)
                            )
                            putExtra(EXTRA_REPLY_ACTION, replyAction)
                            putExtra(EXTRA_REPLY_PACKAGE, replyPackage)
                            putExtra(EXTRA_REPLY_ID, replyId)
                        }
                    )
                } catch (exception: Exception) {
                    reply(
                        app, replyAction, replyPackage, replyId,
                        AutomationForeground.refusal(app, exception)
                    )
                }
            }

            "${app.packageName}$ACTION_CANCEL_EXPORT" -> {
                // Fire-and-forget, and a silent no-op when nothing is running.
                if (MiseAutomationAuth.refuse(app, token) != null) return
                StateExportService.requestCancel()
            }
        }
    }

    /**
     * `id<TAB>label`, one per line, with the optional third (parent) and fourth (default) fields.
     * Fonts are re-creatable by re-importing them, so they ship as `off`.
     */
    private fun categoryLines(): String = MiseBackup.Cat.entries.joinToString("\n") { cat ->
        if (cat.onByDefault) "${cat.id}\t${cat.label}" else "${cat.id}\t${cat.label}\t\toff"
    }

    companion object {
        const val ACTION_EXPORT_STATE = ".action.EXPORT_STATE"
        const val ACTION_LIST_CATEGORIES = ".action.LIST_CATEGORIES"
        const val ACTION_CANCEL_EXPORT = ".action.CANCEL_EXPORT"

        const val EXTRA_TOKEN = "token"
        const val EXTRA_PATH = "path"
        const val EXTRA_ITEMS = "items"
        const val EXTRA_PROGRESS_ACTION = "progress_action"
        const val EXTRA_REPLY_ACTION = "reply_action"
        const val EXTRA_REPLY_PACKAGE = "reply_package"
        const val EXTRA_REPLY_ID = "reply_id"

        /**
         * The reply is a **fresh broadcast** — never a Binder. EMUI will not reliably carry a
         * ResultReceiver/PendingIntent/Messenger into another app's manifest receiver, and it
         * severs the ordered-broadcast result channel between third-party apps.
         * FLAG_INCLUDE_STOPPED_PACKAGES matters: without it a backgrounded caller never hears us.
         */
        fun reply(
            context: Context,
            replyAction: String,
            replyPackage: String,
            replyId: String,
            result: String
        ) {
            context.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra("result", result)
                }
            )
        }
    }
}
