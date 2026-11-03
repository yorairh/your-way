package com.yourway.app.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The app's language: the phone's, or one chosen in Settings. Kept in plain preferences so it is readable before any screen exists. */
object LanguageSetting {
    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val HEBREW = "he"

    private const val FILE = "language"
    private const val KEY = "choice"

    fun get(context: Context): String =
        runCatching { prefs(context).getString(KEY, SYSTEM) }.getOrNull() ?: SYSTEM

    fun set(context: Context, choice: String) {
        // commit, not apply: the screen restarts straight away and must read the new value.
        prefs(context).edit().putString(KEY, choice).commit()
    }

    /** The context an activity should use, with the chosen language applied. */
    fun wrap(base: Context): Context {
        val locale = when (get(base)) {
            ENGLISH -> Locale.ENGLISH
            HEBREW -> Locale("iw")
            else -> return base
        }
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun isHebrew(context: Context): Boolean = when (get(context)) {
        HEBREW -> true
        ENGLISH -> false
        else -> context.resources.configuration.locales[0].language in setOf("iw", "he")
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
