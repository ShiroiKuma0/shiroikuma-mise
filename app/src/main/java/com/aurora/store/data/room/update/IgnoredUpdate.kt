/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.room.update

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Marks an update as ignored by the user.
 *
 * @property packageName Package whose updates are being ignored.
 * @property ignoredVersionCode When `null`, all future updates for this package are hidden.
 *   When set, only that specific version is hidden — a newer version arriving will show up
 *   in the Updates list again.
 * @property testedVersionCode Fork: the newest version of a held app that was installed (or
 *   marked by hand) as tried. While the app sits on an older version than this, that version
 *   was rejected — the Updates screen shows it as skipped instead of offering it again.
 * @property testedVersionName Version name that goes with [testedVersionCode], for display.
 * @property testedAt When the mark was set, in epoch milliseconds.
 */
@Entity(tableName = "ignored_update")
data class IgnoredUpdate(
    @PrimaryKey
    val packageName: String,
    val ignoredVersionCode: Long? = null,
    val testedVersionCode: Long? = null,
    val testedVersionName: String? = null,
    val testedAt: Long? = null
) {
    /** Fork: an "ignore all" rule — the app is held at its installed version. */
    val isHold: Boolean get() = ignoredVersionCode == null

    /** Fork: [update] is the very version already tried and rejected. */
    fun hasSkipped(update: Update): Boolean =
        isHold && testedVersionCode != null && testedVersionCode == update.versionCode

    fun hides(update: Update): Boolean =
        ignoredVersionCode == null || ignoredVersionCode == update.versionCode
}

/**
 * Whether [rules], keyed by package name, mute this update. Every path that surfaces an
 * update — list, notification or auto-install — must go through this, or an update the
 * user ignored comes back on one of the paths that skipped the check.
 */
fun Update.isIgnoredBy(rules: Map<String, IgnoredUpdate>): Boolean =
    rules[packageName]?.hides(this) == true
