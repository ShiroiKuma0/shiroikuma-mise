/*
 * SPDX-FileCopyrightText: 2021 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.SharedLibraryInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.aurora.Constants.FLAVOUR_PRELOAD
import com.aurora.Constants.FLAVOUR_VANILLA
import com.aurora.Constants.PACKAGE_NAME_APP_GALLERY
import com.aurora.Constants.PACKAGE_NAME_GMS
import com.aurora.Constants.PACKAGE_NAME_PLAY_STORE
import com.aurora.extensions.isHuawei
import com.aurora.extensions.isOAndAbove
import com.aurora.extensions.isPAndAbove
import com.aurora.extensions.isRAndAbove
import com.aurora.extensions.isTAndAbove
import com.aurora.extensions.isVAndAbove
import com.aurora.extensions.isValidApp
import com.aurora.store.BuildConfig
import com.aurora.store.R
import com.aurora.store.data.model.BuildType
import java.util.Locale

object PackageUtil {

    private const val TAG = "PackageUtil"

    private const val VERSION_CODE_MICRO_G: Long = 240913402
    private const val VERSION_CODE_MICRO_G_HUAWEI: Long = 240913007
    private const val VERSION_CODE_MICROG_COMPANION_MIN: Long = 84022620
    private const val MICROG_INSTALL_ACTIVITY = "org.microg.vending.installer.AppInstallActivity"

    fun getAllValidPackages(context: Context): List<PackageInfo> =
        getInstalledPackages(context, PackageManager.GET_META_DATA)
            .filter { it.isValidApp(context.packageManager) }
            .sortedBy {
                it.applicationInfo!!.loadLabel(context.packageManager).toString()
                    .lowercase(Locale.getDefault())
            }

    fun hasSupportedAppGallery(context: Context): Boolean {
        return try {
            val result = context.packageManager.checkPermission(
                android.Manifest.permission.INSTALL_PACKAGES,
                PACKAGE_NAME_APP_GALLERY
            )

            if (result != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "AppGallery does not have INSTALL_PACKAGES permission")
                return false
            }

            val packageInfo = context.packageManager.getPackageInfo(
                PACKAGE_NAME_APP_GALLERY,
                PackageManager.GET_META_DATA
            )

            @Suppress("DEPRECATION")
            val versionCode = if (Build.VERSION.SDK_INT >= 28) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }

            Log.i(TAG, "AppGallery - ${packageInfo.versionName} ($versionCode)")

            versionCode >= 15010000L
        } catch (_: Exception) {
            false
        }
    }

    fun hasSupportedMicroGVariant(context: Context): Boolean {
        val isMicroG = CertUtil.isMicroGGms(context)

        // Do not proceed if MicroG variant is not installed
        if (!isMicroG) return false

        return if (isHuawei) {
            isInstalled(context, PACKAGE_NAME_GMS, VERSION_CODE_MICRO_G_HUAWEI)
        } else {
            isInstalled(context, PACKAGE_NAME_GMS, VERSION_CODE_MICRO_G)
        }
    }

    fun hasActivity(context: Context, packageName: String, activityName: String): Boolean {
        val intent = Intent()
        intent.setClassName(packageName, activityName)

        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo != null
    }

    fun hasMicroGCompanion(context: Context): Boolean = isInstalled(
        context,
        PACKAGE_NAME_PLAY_STORE,
        VERSION_CODE_MICROG_COMPANION_MIN
    ) &&
        hasActivity(
            context,
            PACKAGE_NAME_PLAY_STORE,
            MICROG_INSTALL_ACTIVITY
        )

    fun isInstalled(context: Context, packageName: String, versionCode: Long? = null): Boolean =
        try {
            val packageInfo = getPackageInfo(context, packageName, PackageManager.GET_META_DATA)
            if (versionCode != null) {
                PackageInfoCompat.getLongVersionCode(packageInfo) >= versionCode
            } else {
                true
            }
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun isArchived(context: Context, packageName: String): Boolean = try {
        isVAndAbove && context.packageManager.getArchivedPackage(packageName) != null
    } catch (_: Exception) {
        false
    }

    fun isSharedLibrary(context: Context, packageName: String): Boolean = if (isOAndAbove) {
        getAllSharedLibraries(context).any { it.name == packageName }
    } else {
        false
    }

    fun isSharedLibraryInstalled(
        context: Context,
        packageName: String,
        versionCode: Long
    ): Boolean = if (isOAndAbove) {
        val sharedLibraries = getAllSharedLibraries(context)
        if (isPAndAbove) {
            sharedLibraries.any {
                it.name == packageName && it.longVersion == versionCode
            }
        } else {
            sharedLibraries.any {
                @Suppress("DEPRECATION")
                it.name == packageName && it.version == versionCode.toInt()
            }
        }
    } else {
        false
    }

    fun isUpdatable(context: Context, packageName: String, versionCode: Long): Boolean {
        return try {
            val packageInfo = getPackageInfo(context, packageName)
            return versionCode > PackageInfoCompat.getLongVersionCode(packageInfo)
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * `true` when [packageName] is frozen: disabled by the user — what `pm disable-user` and the
     * freezer apps do — suspended, or hidden. Such an app is still installed and still updatable,
     * it just can't be launched, so the Updates tab lists it in italics rather than hiding it.
     */
    fun isFrozen(context: Context, packageName: String): Boolean = try {
        val applicationInfo = getPackageInfo(context, packageName).applicationInfo!!
        !applicationInfo.enabled ||
            applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0 ||
            isHidden(context, packageName)
    } catch (_: Exception) {
        false
    }

    /**
     * Fork: `true` when [packageName] is installed but frozen by *hiding* it
     * (`setApplicationHiddenSettingAsUser`, what device-owner freezers do). A plain lookup can't
     * see a hidden app at all; only the [MATCH_HIDDEN] lookup in [getPackageInfo] finds it.
     */
    fun isHidden(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        false
    } catch (_: PackageManager.NameNotFoundException) {
        isInstalled(context, packageName)
    }

    /**
     * Build-level eligibility for Aurora Store's self-update: vanilla / preload flavors
     * only, never debug, and never an F-Droid-signed build. Huawei is excluded by the
     * flavor check. The user-facing toggle gates this further at runtime.
     */
    fun isSelfUpdateSupported(context: Context): Boolean {
        val flavorEligible = BuildConfig.FLAVOR == FLAVOUR_VANILLA ||
            BuildConfig.FLAVOR == FLAVOUR_PRELOAD
        return flavorEligible &&
            BuildType.CURRENT != BuildType.DEBUG &&
            !CertUtil.isFDroidApp(context, BuildConfig.APPLICATION_ID)
    }

    /**
     * Confirm if MicroG bundle is installed
     * Considering the following:
     * 1. GmsCore is installed and it is a microG huawei variant
     * 2. Play Store is installed - (microG Companion)
     */
    fun isMicroGBundleInstalled(context: Context): Boolean =
        hasSupportedMicroGVariant(context) && isInstalled(context, PACKAGE_NAME_PLAY_STORE)

    fun getInstalledVersionName(context: Context, packageName: String): String = try {
        getPackageInfo(context, packageName).versionName ?: ""
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }

    fun getInstalledVersionCode(context: Context, packageName: String): Long = try {
        PackageInfoCompat.getLongVersionCode(getPackageInfo(context, packageName))
    } catch (_: PackageManager.NameNotFoundException) {
        0
    }

    fun isTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun getLaunchIntent(context: Context, packageName: String?): Intent? {
        val intent = if (isTv(context)) {
            context.packageManager.getLeanbackLaunchIntentForPackage(packageName!!)
        } else {
            context.packageManager.getLaunchIntentForPackage(packageName!!)
        } ?: return null

        return intent.apply {
            addCategory(
                if (isTv(context)) {
                    Intent.CATEGORY_LEANBACK_LAUNCHER
                } else {
                    Intent.CATEGORY_LAUNCHER
                }
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun getStorageManagerIntent(context: Context): Intent {
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:${BuildConfig.APPLICATION_ID}".toUri()
        )

        // Check if the intent can be resolved
        val packageManager = context.packageManager
        val isIntentAvailable = packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        ).isNotEmpty()

        return if (isIntentAvailable) {
            intent
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    fun getInstallUnknownAppsIntent(): Intent = if (isOAndAbove) {
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${BuildConfig.APPLICATION_ID}".toUri()
        )
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (isOAndAbove) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            val secureResult = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.INSTALL_NON_MARKET_APPS,
                0
            )

            return secureResult == 1
        }
    }

    /**
     * Returns the installer package name that installed [packageName], or null if unknown / not
     * installed. Uses [PackageManager.getInstallSourceInfo] on API 30+; falls back to the
     * pre-30 deprecated API below that.
     */
    @Suppress("DEPRECATION")
    fun getInstallerPackageName(context: Context, packageName: String): String? = try {
        if (isRAndAbove) {
            context.packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            context.packageManager.getInstallerPackageName(packageName)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** A package's own display label, or null when it is not installed. */
    fun getPackageLabel(context: Context, packageName: String): String? = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
            .loadLabel(context.packageManager).toString()
    }.getOrNull()

    /**
     * Fork: a hidden app — frozen by `setApplicationHiddenSettingAsUser` — is still installed and
     * still updatable, but the PackageManager reports it only to a query that also asks for
     * uninstalled packages. That query also returns apps uninstalled with their data kept
     * (`pm uninstall -k`, or `--user 0` on a system app); those lack [ApplicationInfo.FLAG_INSTALLED],
     * which is how [isInstalledForUser] tells them apart.
     */
    private const val MATCH_HIDDEN = PackageManager.MATCH_UNINSTALLED_PACKAGES

    private fun PackageInfo.isInstalledForUser(): Boolean =
        (applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_INSTALLED != 0

    /** Every installed package, hidden ones included — see [MATCH_HIDDEN]. */
    fun getInstalledPackages(context: Context, flags: Int = 0): List<PackageInfo> =
        if (isTAndAbove) {
            context.packageManager.getInstalledPackages(
                PackageInfoFlags.of((flags or MATCH_HIDDEN).toLong())
            )
        } else {
            context.packageManager.getInstalledPackages(flags or MATCH_HIDDEN)
        }.filter { it.isInstalledForUser() }

    @Throws(Exception::class)
    fun getPackageInfo(context: Context, packageName: String, flags: Int = 0): PackageInfo {
        val packageInfo = if (isTAndAbove) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageInfoFlags.of((flags or MATCH_HIDDEN).toLong())
            )
        } else {
            context.packageManager.getPackageInfo(packageName, flags or MATCH_HIDDEN)
        }
        // Uninstalled with its data kept is not installed, whatever the lookup returned.
        if (!packageInfo.isInstalledForUser()) throw PackageManager.NameNotFoundException(packageName)
        return packageInfo
    }

    fun getIconForPackage(context: Context, packageName: String): Bitmap? = try {
        val packageInfo = getPackageInfo(context, packageName)
        val icon = packageInfo.applicationInfo!!.loadIcon(context.packageManager)
        if (icon.intrinsicWidth > 0 && icon.intrinsicHeight > 0) {
            icon.toBitmap(96, 96)
        } else {
            context.packageManager.defaultActivityIcon.toBitmap(96, 96)
        }
    } catch (exception: Exception) {
        Log.e(TAG, "Failed to get icon for package!", exception)
        null
    }

    fun getIconDrawableForPackage(context: Context, packageName: String): Drawable? {
        val placeholder = AppCompatResources.getDrawable(context, R.drawable.bg_placeholder)

        return try {
            val packageInfo = getPackageInfo(context, packageName)
            val applicationInfo = packageInfo.applicationInfo ?: return placeholder

            applicationInfo.loadIcon(context.packageManager)
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to get icon for package!", exception)
            placeholder
        }
    }

    private fun getAllSharedLibraries(context: Context, flags: Int = 0): List<SharedLibraryInfo> =
        if (isTAndAbove) {
            context.packageManager.getSharedLibraries(PackageInfoFlags.of(flags.toLong()))
        } else if (isOAndAbove) {
            context.packageManager.getSharedLibraries(flags)
        } else {
            emptyList()
        }

    fun getFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addDataScheme("package")
        @Suppress("DEPRECATION")
        filter.addAction(Intent.ACTION_PACKAGE_INSTALL)
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        return filter
    }
}
