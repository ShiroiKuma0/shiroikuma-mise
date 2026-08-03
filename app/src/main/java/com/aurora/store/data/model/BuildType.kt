package com.aurora.store.data.model

import com.aurora.store.BuildConfig

/**
 * Class representing build types for Aurora Store
 */
// shiroikuma fork: these are OUR package names, not upstream's. PACKAGE_NAMES feeds
// InstallerInfo.installerPackageNames, which is what decides whether an installed app counts
// as "installed by this store" (CertUtil / the update source filter). Left on com.aurora.store
// it would never match anything we installed.
enum class BuildType(val packageName: String) {
    RELEASE("shiroikuma.mise"),
    NIGHTLY("shiroikuma.mise.nightly"),
    DEBUG("shiroikuma.mise.debug");

    companion object {

        /**
         * Returns current build type
         */
        @Suppress("KotlinConstantConditions")
        val CURRENT: BuildType
            get() = when (BuildConfig.BUILD_TYPE) {
                "release" -> RELEASE
                "nightly" -> NIGHTLY
                else -> DEBUG
            }

        /**
         * Returns package names for all possible build types
         */
        val PACKAGE_NAMES: List<String>
            get() = BuildType.entries.map { it.packageName }
    }
}
