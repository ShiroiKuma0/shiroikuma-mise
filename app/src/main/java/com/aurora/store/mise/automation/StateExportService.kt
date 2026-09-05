/*
 * 白い熊 店 (shiroikuma-mise) fork: where the headless 保存復元 export actually runs.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.aurora.store.R
import com.aurora.store.mise.MiseBackup
import com.aurora.store.mise.MiseUiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the export off the broadcast window, reports progress with real counts, and sends exactly
 * one terminal reply.
 *
 * A manifest receiver — `goAsync()` or not — must finish inside ~10 s foreground / ~60 s
 * background or the system ANRs and kills the process mid-write, leaving a half-written archive
 * and a caller waiting for a reply that can never come. Hence this service.
 */
class StateExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // EXTRAS FIRST, then the guard, then the reply. startForeground() can be refused here just
        // as the receiver's start can, and a refusal raised ABOVE these three lines has nowhere to
        // answer — the caller then waits out its whole timeout and reports "no response", which is
        // indistinguishable from an app that never implemented the contract. Reading three extras
        // costs microseconds, so it does not threaten the 5 s startForeground deadline.
        val request = intent ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val replyAction = request.getStringExtra(StateExportReceiver.EXTRA_REPLY_ACTION)
        val replyPackage = request.getStringExtra(StateExportReceiver.EXTRA_REPLY_PACKAGE)
        val replyId = request.getStringExtra(StateExportReceiver.EXTRA_REPLY_ID)
        if (replyAction == null || replyPackage == null || replyId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.app_name)))
        } catch (exception: Exception) {
            StateExportReceiver.reply(
                applicationContext, replyAction, replyPackage, replyId,
                AutomationForeground.refusal(applicationContext, exception)
            )
            stopSelf()
            return START_NOT_STICKY
        }

        cancelled = false
        running.set(true)

        scope.launch {
            val replied = AtomicBoolean(false)
            fun reply(result: String) {
                if (!replied.compareAndSet(false, true)) return
                StateExportReceiver.reply(
                    applicationContext, replyAction, replyPackage, replyId, result
                )
            }

            // EMUI dozes the CPU with the screen off; a partial wakelock keeps a longer export
            // alive. Released in the finally, always.
            val wakeLock = getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                ?.apply { setReferenceCounted(false) }

            try {
                wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
                runExport(request, replyId, replyPackage, ::reply)
            } catch (exception: Exception) {
                reply("ERROR:${exception.message ?: exception.javaClass.simpleName}")
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                running.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runExport(
        request: Intent,
        replyId: String,
        replyPackage: String,
        reply: (String) -> Unit
    ) {
        val progressAction = request.getStringExtra(StateExportReceiver.EXTRA_PROGRESS_ACTION)
        val itemsExtra = request.getStringExtra(StateExportReceiver.EXTRA_ITEMS).orEmpty()

        // `items` absent means OUR DEFAULT SET, not everything.
        val categories = itemsExtra.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { MiseBackup.Cat.ofId(it) }
            .toSet()
            .ifEmpty { MiseBackup.Cat.defaults }

        val bytes = ByteArrayOutputStream()
        val written = MiseBackup.writeZip(
            context = applicationContext,
            categories = categories,
            out = bytes,
            onProgress = { cat, position, total ->
                sendProgress(progressAction, replyPackage, replyId, cat, position, total)
            },
            isCancelled = { cancelled }
        )

        if (cancelled) {
            reply("ERROR:cancelled")
            return
        }

        val payload = bytes.toByteArray()
        val pathOverride = request.getStringExtra(StateExportReceiver.EXTRA_PATH)
        val configuredDir = MiseUiConfig(applicationContext).exportDir

        val absolutePath = when {
            !pathOverride.isNullOrBlank() -> {
                // Declaring MANAGE_EXTERNAL_STORAGE is not holding it — check, don't discover it
                // by failing: `ERROR:no-storage-access` is what 自由作業盤 keys on to offer the
                // "grant" button on the failed row.
                if (!Environment.isExternalStorageManager()) {
                    reply("ERROR:no-storage-access")
                    return
                }
                writeToDirectory(File(pathOverride), payload)
            }

            configuredDir.isNotBlank() ->
                MiseBackup.writeToTree(applicationContext, Uri.parse(configuredDir), payload)
                    ?.let { name -> "$configuredDir/$name" }

            else -> {
                reply("ERROR:no-directory")
                return
            }
        } ?: run {
            reply("ERROR:the backup folder could not be written to")
            return
        }

        val size = payload.size.toLong()
        reply("OK:$absolutePath|$size|${MiseBackup.humanSize(size)}|${written.size} categories")
    }

    /**
     * Atomic write to a plain directory: `.part` first, renamed only once the archive is whole,
     * and deleted on any failure — a cancelled or killed export leaves the folder exactly as it
     * found it.
     */
    private fun writeToDirectory(dir: File, payload: ByteArray): String? {
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = File(dir, MiseBackup.exportFileName())
        val part = File(dir, "${target.name}.part")
        return runCatching {
            part.writeBytes(payload)
            if (cancelled || !part.renameTo(target)) {
                part.delete()
                null
            } else {
                target.absolutePath
            }
        }.getOrElse {
            part.delete()
            null
        }
    }

    /** Real numbers, never a percentage — and `item` is what moves the panel's highlight. */
    private fun sendProgress(
        progressAction: String?,
        replyPackage: String,
        replyId: String,
        cat: MiseBackup.Cat,
        position: Int,
        total: Int
    ) {
        if (progressAction.isNullOrBlank()) return
        sendBroadcast(
            Intent(progressAction).apply {
                setPackage(replyPackage)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(StateExportReceiver.EXTRA_REPLY_ID, replyId)
                putExtra("app", getString(R.string.app_name))
                putExtra("item", cat.id)
                putExtra("text", "区分 $position/$total — ${cat.label}")
                putExtra("current", position.toLong())
                putExtra("total", total.toLong())
                putExtra("unit", "区分")
            }
        )
    }

    private fun buildNotification(appName: String): Notification {
        val manager = getSystemService<NotificationManager>()
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Automation export",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_outlined)
            .setContentTitle(appName)
            .setContentText("Exporting settings…")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running.set(false)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "mise_automation_export"
        private const val NOTIFICATION_ID = 91_001
        private const val WAKELOCK_TAG = "shiroikuma-mise:automation-export"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L

        /**
         * Process-local, never persisted: a persisted "export in progress" flag wedges the app
         * for good after a single crash.
         */
        private val running = AtomicBoolean(false)

        @Volatile
        private var cancelled = false

        /** Signals the running export to unwind at its next category boundary. */
        fun requestCancel() {
            if (running.get()) cancelled = true
        }
    }
}
