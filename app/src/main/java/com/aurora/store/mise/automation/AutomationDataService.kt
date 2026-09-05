/*
 * 白い熊 店 (shiroikuma-mise) fork: where an automation data export or import actually runs.
 * Modelled on shiroikuma-jiyusagyoban's core/automation/AutomationDataService.kt; the engine it
 * drives is this app's own MiseBackup.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.aurora.store.R
import androidx.preference.PreferenceManager
import com.aurora.store.mise.MiseBackup
import com.aurora.store.mise.MiseUiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a data export or import actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be done
 * anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress, and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored (応用管理, 2026-09-04).
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to
 * the binder transaction and is closed the moment `call()` returns. This service owns the copy and
 * closes it in a `finally` — leaking one would hold the caller's file open indefinitely, and the
 * caller cannot checksum or encrypt a file that is still open.
 */
class AutomationDataService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) == true

        // UNCONDITIONALLY FIRST, before any early return below it. Once startForegroundService()
        // has been called the platform requires startForeground() whatever this method then
        // decides, and kills the whole process with ForegroundServiceDidNotStartInTimeException
        // otherwise. The early returns below are exactly the reachable paths — a null intent on
        // redelivery, or a job id whose descriptor is no longer in the handover — so returning
        // before this call would let a retry take the app down instead of being ignored.
        val foreground = runCatching {
            startForeground(NOTIFICATION_ID, notification(importing))
        }

        val jobId = intent?.getStringExtra(EXTRA_JOB) ?: return stop(startId)
        val fd = HANDOVER.remove(jobId) ?: return stop(startId)
        val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
        val progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            // Exactly one terminal answer per job, whatever path got here — a synchronous failure
            // and an asynchronous success must never both fire. The same guard the broadcast
            // contract has carried since the first sister app.
            if (!replied.compareAndSet(false, true)) return
            AutomationJobs.finish(jobId)
            if (replyAction.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
            sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    // Without this a caller that has been backgrounded never hears the answer, and
                    // on a clean phone the caller may not have been launched at all.
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                    putExtra(AutomationProvider.KEY_RESULT, result)
                }
            )
        }

        // A provider call() is a BACKGROUND start, and API 31+ can refuse one outright unless the
        // app is exempt from battery optimisation. Nothing can run, and the handover is drained, so
        // this descriptor is ours to close — leaking it would hold the caller's file open forever
        // under a job that never answers.
        if (foreground.isFailure) {
            runCatching { fd.close() }
            reply(AutomationForeground.refusal(applicationContext, foreground.exceptionOrNull()))
            return stop(startId)
        }

        scope.launch {
            try {
                fd.use { open ->
                    if (importing) {
                        runImport(open, ::reply)
                    } else {
                        runExport(
                            jobId = jobId,
                            fd = open,
                            items = intent.getStringExtra(AutomationProvider.KEY_ITEMS),
                            progressAction = progressAction,
                            replyPackage = replyPackage,
                            reply = ::reply
                        )
                    }
                }
            } catch (throwable: Throwable) {
                reply("ERROR:${throwable.message ?: throwable.javaClass.simpleName}")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Straight into the caller's descriptor — [MiseBackup.writeZip] is the ONE export
     * implementation, and this is a third thin caller of it beside the UI panel and the §1 service.
     */
    private suspend fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progressAction: String?,
        replyPackage: String?,
        reply: (String) -> Unit
    ) {
        val categories = resolve(items) ?: run {
            reply("ERROR:unknown category in items: $items")
            return
        }
        var written = 0L
        val done = ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
            // Counted as it goes rather than stat'ed afterwards: the caller owns the file and we
            // may not be able to see it at all — it can be an anonymous pipe or a descriptor into a
            // directory this app cannot list.
            val counting = object : OutputStream() {
                override fun write(b: Int) {
                    out.write(b); written++
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    out.write(b, off, len); written += len
                }
            }
            MiseBackup.writeZip(
                context = applicationContext,
                categories = categories,
                out = counting,
                onProgress = { cat, position, total ->
                    sendProgress(
                        progressAction, replyPackage, jobId, cat, position, total, written
                    )
                },
                isCancelled = { AutomationJobs.isCancelled(jobId) }
            )
        }

        if (AutomationJobs.isCancelled(jobId)) reply("ERROR:cancelled")
        else reply("OK:$written|${MiseBackup.humanSize(written)}|${done.size} categories")
    }

    /**
     * Read the whole archive before touching anything.
     *
     * [MiseBackup.restore] wants the bytes, and that is the right shape here for a reason beyond
     * convenience: a partial read that failed halfway would otherwise import half an archive, and
     * a half-restored app is worse than one that refused.
     */
    private suspend fun runImport(fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        // Spooled through the cache rather than read straight into an array: `readBytes()` on a
        // stream of unknown length grows its buffer by doubling, so peak memory is about twice the
        // archive — on an import 応用管理 streams from a phone-sized backup that is what gets the
        // process killed. A file is read once, at exactly its known size.
        //
        // Known residual: MiseBackup.categoriesIn/restore still take a ByteArray, so the archive is
        // held once in memory after spooling. Streaming those would change the engine the UI panel
        // shares, which is outside this contract's delta — hence the explicit cap below, which
        // refuses an absurd archive with a readable error instead of an OutOfMemoryError.
        val spool = File(cacheDir, "automation-import-${System.nanoTime()}.zip")
        try {
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                spool.outputStream().use { output -> input.copyTo(output) }
            }
            runImportFrom(spool, reply)
        } finally {
            spool.delete()
        }
    }

    private suspend fun runImportFrom(spool: File, reply: (String) -> Unit) {
        val length = spool.length()
        if (length == 0L) {
            reply("ERROR:empty archive")
            return
        }
        if (length > MAX_IMPORT_BYTES) {
            reply("ERROR:archive too large: $length bytes")
            return
        }
        val bytes = spool.readBytes()
        // Every category the archive actually carries, not every category we know about: asking
        // for one the archive lacks is how a restore ends up reporting success over nothing.
        val present = MiseBackup.categoriesIn(bytes)
        if (present.isEmpty()) {
            reply("ERROR:archive carries no categories")
            return
        }
        val restored = MiseBackup.restore(applicationContext, bytes, present)
        // The caller force-stops us straight after this. That is deliberate and belongs on its
        // side: a running process writes its cached SharedPreferences back out at orderly shutdown
        // and silently undoes the import that just happened (応用管理 paid for this one already).
        //
        // But the force-stop is a SIGKILL, and BOTH restore paths write with androidx's `edit {}`,
        // whose default is apply() — an ASYNCHRONOUS disk write. Reply first and the kill lands on
        // writes still in flight, so the restore reports success over data that never reached disk.
        // Flush synchronously here, and flush BOTH stores: one is not enough, because the restore
        // spans two preference files. (Nothing in this app debounces its writes, so there is no
        // debounce to wait out; the Room categories are already durable at transaction commit and
        // the fonts are plain file writes.)
        flushPreferences()
        reply("OK:$restored restored")
    }

    /**
     * Force every preference file the restore touched out to disk, synchronously.
     *
     * `commit()` on an editor writes the store's whole current map on the calling thread, and an
     * earlier `apply()` has already published its changes into that map — so an empty commit per
     * file is enough to make the pending writes durable, and it is the only thing that survives the
     * SIGKILL that follows our reply.
     */
    private fun flushPreferences() {
        listOf(
            PreferenceManager.getDefaultSharedPreferences(applicationContext),
            applicationContext.getSharedPreferences(MiseUiConfig.PREFS, Context.MODE_PRIVATE)
        ).forEach { prefs ->
            @Suppress("ApplySharedPref")
            runCatching { prefs.edit().commit() }
        }
    }

    /**
     * `items` absent means OUR DEFAULT SET, not everything — and an id we do not know is an error
     * rather than something to skip quietly, so a caller's typo cannot silently shrink a backup.
     */
    private fun resolve(items: String?): Set<MiseBackup.Cat>? {
        if (items.isNullOrBlank()) return MiseBackup.Cat.defaults
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { MiseBackup.Cat.ofId(it) }
        return if (found.size == wanted.size) found.toSet() else null
    }

    /** Real numbers, never a percentage — and `item` is what moves the panel's highlight. */
    private fun sendProgress(
        progressAction: String?,
        replyPackage: String?,
        jobId: String,
        cat: MiseBackup.Cat,
        position: Int,
        total: Int,
        bytes: Long
    ) {
        if (progressAction.isNullOrBlank() || replyPackage.isNullOrBlank()) return
        sendBroadcast(
            Intent(progressAction).apply {
                setPackage(replyPackage)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                // The door correlates on job_id; reply_id is sent too so a caller written against
                // the §1 broadcast vocabulary reads the same broadcast without a second code path.
                putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                putExtra(StateExportReceiver.EXTRA_REPLY_ID, jobId)
                putExtra("app", getString(R.string.app_name))
                putExtra("item", cat.id)
                putExtra("text", "区分 $position/$total — ${cat.label}")
                putExtra("current", position.toLong())
                putExtra("total", total.toLong())
                putExtra("unit", "区分")
                putExtra("bytes", bytes)
            }
        )
    }

    private fun notification(importing: Boolean): Notification {
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Automation data", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_outlined)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (importing) "Restoring data…" else "Backing data up…")
            .setOngoing(true)
            .build()
    }

    private fun stop(startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "mise_automation_data"
        private const val NOTIFICATION_ID = 91_002
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        /**
         * The largest archive this app will take back in. Nothing it exports comes close — settings
         * JSON plus imported font files — so anything past this is a caller mistake, and refusing it
         * with a sentence beats dying with an OutOfMemoryError that 白い熊 would see as a silent
         * failed row.
         */
        private const val MAX_IMPORT_BYTES = 256L * 1024 * 1024

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A `ParcelFileDescriptor` in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — the service, which
         * closes it in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?
        ) {
            HANDOVER[jobId] = fd
            ContextCompat.startForegroundService(
                context,
                Intent(context, AutomationDataService::class.java).apply {
                    putExtra(EXTRA_JOB, jobId)
                    putExtra(EXTRA_IMPORTING, importing)
                    putExtra(
                        AutomationProvider.KEY_ITEMS,
                        extras?.getString(AutomationProvider.KEY_ITEMS)
                    )
                    putExtra(
                        AutomationProvider.KEY_REPLY_ACTION,
                        extras?.getString(AutomationProvider.KEY_REPLY_ACTION)
                    )
                    putExtra(
                        AutomationProvider.KEY_REPLY_PACKAGE,
                        extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE)
                    )
                    putExtra(
                        AutomationProvider.KEY_PROGRESS_ACTION,
                        extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION)
                    )
                }
            )
        }

        /**
         * Drop a descriptor that never reached the service — the map is the only thing holding it,
         * so a failed `startForegroundService` would otherwise leave it open until the process dies
         * and leave the caller's file unclosable.
         */
        fun abandon(jobId: String) {
            HANDOVER.remove(jobId)
        }
    }
}
