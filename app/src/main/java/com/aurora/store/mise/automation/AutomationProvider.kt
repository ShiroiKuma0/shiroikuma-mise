/*
 * 白い熊 店 (shiroikuma-mise) fork: the automation data door.
 * Copied from shiroikuma-jiyusagyoban's core/automation/AutomationProvider.kt; only the backup
 * engine it drives (MiseBackup) and the gate it asks (MiseAutomationAuth) are this app's own.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.content.pm.PackageInfoCompat
import com.aurora.store.mise.MiseBackup
import org.json.JSONArray
import org.json.JSONObject

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of the redesign.
 *
 * **A broadcast cannot tell you who sent it.** The old contract's answer to that was a shared
 * secret, which cannot survive the wipe that this feature exists to recover from. A provider gets
 * the caller's identity from the framework for free — see [AutomationCallers] for what is actually
 * checked and why a package-name prefix would have been worse than the token it replaced.
 *
 * **A list needs a synchronous answer.** 応用管理 draws a row per installed app before any export
 * exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. `call()` validates, starts a foreground service and returns — tens of megabytes over
 * minutes inside a binder call would block the caller, report no progress, refuse cancellation and
 * die silently if this process were killed. The bytes go through a file descriptor the caller
 * opened, and the terminal answer comes back on the broadcast the family already proved on EMUI.
 *
 * ## Why a descriptor and not a path
 *
 * Because a backup is not a stable directory while it is being assembled. 応用管理 writes into a
 * temporary path and renames on commit; it encrypts and checksums **per file it knows about**. A
 * file this app dropped into that directory itself would be renamed out from under it, would sit in
 * plaintext inside an encrypted backup, and would be unverified rather than verified-and-failing
 * (応用管理, 2026-09-04). A descriptor is also a capability that **expires when it is closed** —
 * precisely the property a URI grant failed to give us on the 地図 contract, where the revoke
 * needed a five-minute floor because 地図 might not read for three minutes.
 *
 * It also means this app no longer needs `MANAGE_EXTERNAL_STORAGE` to be backed up. That permission
 * was only ever required because the old contract handed apps an absolute path — and it stays in
 * this app's manifest only for the §1 receiver's `path` override, which predates the door.
 *
 * ## `import` lives ONLY here
 *
 * It never gets a broadcast action. An import overwrites this app's data, and the §1 receiver is
 * `exported="true"` with no permission — an import there would let any app on the phone wipe any
 * sister app's settings.
 */
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary
     * the broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` with our stack trace in it, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context?.applicationContext ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then the app's own switches — a token is ignored unless this app asks for one.
        MiseAutomationAuth.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return when (method) {
            METHOD_DESCRIBE -> ok(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }

            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw
     * a row before an export exists, and at restore must judge compatibility **before** streaming
     * tens of megabytes into an app that would reject them — which it cannot do if the header is
     * buried inside an encrypted archive (応用管理, 2026-09-04).
     *
     * Built with [JSONObject] rather than the reference's hand-rolled string because this app's
     * category labels carry punctuation ("App settings (installer, network, updates, blacklist,
     * spoof)") that has to be escaped rather than interpolated.
     *
     * `requires_launch_first` is false and means it: [MiseBackup.restore] only merges preferences
     * and upserts Room rows, and it filters the signed-in account's keys on the way in, so a
     * never-launched install cannot be left holding a placeholder session.
     */
    private fun describe(ctx: Context): String {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val cats = MiseBackup.Cat.defaults
        return "OK:" + JSONObject().apply {
            put("app_id", ctx.packageName)
            put("version_code", PackageInfoCompat.getLongVersionCode(pkg))
            put("version_name", pkg.versionName.orEmpty())
            put("format", FORMAT)
            put("min_format_readable", MIN_FORMAT_READABLE)
            put("requires_launch_first", false)
            // 応用管理 renders these verbatim, so this is where 白い熊 finds out what a backup of
            // this app does and does not cover — the exclusion is stated here, not only in a report.
            put("contains", JSONArray(cats.map { it.label } + ACCOUNT_EXCLUSION))
        }
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to
     * the binder transaction and is closed when `call()` returns; a service reading it afterwards
     * would find it shut. That is a bug you only see under load, so it is not left to the service
     * to remember.
     */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        @Suppress("DEPRECATION")
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        // If the service cannot be started the descriptor is ours and would leak, and the caller
        // would sit waiting on a job that never runs. Answer now, close now.
        return runCatching {
            AutomationDataService.start(ctx, jobId, dup, importing, extras)
            ok("OK:$jobId")
        }.getOrElse { failure ->
            AutomationJobs.finish(jobId)
            AutomationDataService.abandon(jobId)
            runCatching { dup.close() }
            fail("ERROR:${failure.message ?: failure.javaClass.simpleName}")
        }
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }
    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever `call()`ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than "wrong
    // door".
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? =
        throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /**
         * Said out loud in [describe] because a backup row that looks like it covers the login is
         * worse than one that admits it does not.
         *
         * This app holds **live Google credentials** — the Play session it runs on. They are out of
         * every export, twice over: the `accounts` Room table is not a backup category at all, and
         * [MiseBackup] filters the signed-in account's preference keys on export *and* on import.
         * That is not only a secrets decision — restoring the signed-in flag without the account
         * table is what manufactures a broken session, which this fork has already had to fix once.
         * A restored install therefore comes back fully configured but signed out, and 白い熊 signs
         * in again.
         */
        private const val ACCOUNT_EXCLUSION =
            "Excludes the Google account: session and auth tokens are never exported — a restored " +
                "install comes back signed out"

        /** This app's archive format. Bumped when an older build could no longer read what we write. */
        const val FORMAT = MiseBackup.VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a
         * caller refuse the second case at discovery time, before anything is streamed.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
