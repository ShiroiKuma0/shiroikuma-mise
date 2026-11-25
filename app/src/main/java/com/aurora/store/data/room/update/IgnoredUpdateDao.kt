/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.room.update

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoredUpdateDao {

    @Query("SELECT * FROM ignored_update")
    fun ignoredUpdates(): Flow<List<IgnoredUpdate>>

    @Query("SELECT * FROM ignored_update WHERE packageName = :packageName")
    suspend fun get(packageName: String): IgnoredUpdate?

    /** Fork: set or clear (all three `null`) the tested mark without touching the rule. */
    @Query(
        "UPDATE ignored_update SET testedVersionCode = :versionCode, " +
            "testedVersionName = :versionName, testedAt = :testedAt " +
            "WHERE packageName = :packageName"
    )
    suspend fun setTested(
        packageName: String,
        versionCode: Long?,
        versionName: String?,
        testedAt: Long?
    )

    @Upsert
    suspend fun upsert(ignoredUpdate: IgnoredUpdate)

    @Query("DELETE FROM ignored_update WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
