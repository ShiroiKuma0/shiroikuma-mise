/*
 * 白い熊 店 (shiroikuma-mise) fork: the app's own language.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.mise

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import com.aurora.store.R
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

/**
 * The language of this app alone, chosen on the UI page.
 *
 * The chosen BCP-47 tag is kept in the UI page's own preferences file (empty = the system's
 * language), so it travels in the export. It is applied by wrapping the base context of the
 * application and of every activity ([wrap]) — the phone this fork is built for has no per-app
 * language of its own, and where the system has one (Android 13) EMUI does not keep it across a
 * sideloaded update. From Android 13 the system is told as well, so its own per-app language
 * screen shows the same choice.
 *
 * `Locale.getDefault()` is deliberately left alone: the sign-in and the Play requests take their
 * language from it, and those follow the system or the spoof settings, not the look of this app.
 */
object MiseLanguage {

    private const val KEY = "app_language"

    /**
     * What the picker offers: the short list in `res/xml/locales_config.xml`, which the system's
     * own per-app language screen reads too, so the two cannot drift apart. The file's first two
     * entries, Japanese and English, keep their place; the rest are sorted by name.
     */
    private fun offered(context: Context): List<String> {
        val tags = mutableListOf<String>()
        val parser = context.resources.getXml(R.xml.locales_config)
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue(ANDROID_NS, "name")?.let(tags::add)
                }
            }
        } finally {
            parser.close()
        }
        return tags
    }

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** A row of the picker: the tag, the language in its own words, and its country if any. */
    class Entry(val tag: String, val name: String, val country: String)

    fun tag(context: Context): String =
        context.getSharedPreferences(MiseUiConfig.PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty()

    /** The rows after "System default". A language chosen earlier and no longer listed stays. */
    fun entries(context: Context): List<Entry> {
        val tags = offered(context)
        val rest = tags.drop(2).map(::entry).sortedBy { it.name.lowercase(Locale.ROOT) }
        val listed = tags.take(2).map(::entry) + rest
        val current = tag(context)
        return if (current.isEmpty() || listed.any { it.tag == current }) {
            listed
        } else {
            listed + entry(current)
        }
    }

    /** The name of the language in force, in its own words; null for the system's language. */
    fun currentName(context: Context): String? = tag(context).takeIf { it.isNotEmpty() }?.let { entry(it).name }

    private fun entry(tag: String): Entry {
        val locale = Locale.forLanguageTag(tag)
        val name = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
        return Entry(tag, name, locale.getDisplayCountry(locale))
    }

    /** [base] in the chosen language, or [base] itself when the system's language is wanted. */
    fun wrap(base: Context): Context {
        val tag = tag(base)
        if (tag.isEmpty()) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }

    /** Stores [tag] (empty = the system's language) and redraws [activity] in it. */
    fun set(activity: Activity, tag: String) {
        activity.getSharedPreferences(MiseUiConfig.PREFS, Context.MODE_PRIVATE)
            .edit(commit = true) { putString(KEY, tag) }
        tellSystem(activity, tag)
        activity.recreate()
    }

    /** At every start: the stored choice again, for a system that forgot it. */
    fun reassert(context: Context) {
        val tag = tag(context)
        if (tag.isNotEmpty()) tellSystem(context, tag)
    }

    private fun tellSystem(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        val wanted = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        if (manager.applicationLocales != wanted) manager.applicationLocales = wanted
    }
}
