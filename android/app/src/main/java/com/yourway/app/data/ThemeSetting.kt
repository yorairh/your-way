package com.yourway.app.data

import android.content.Context

enum class AppTheme { BLUE, GREEN, ORANGE, PURPLE }

/** The colour the app uses, kept in plain preferences like the language so it is known before any screen exists. */
object ThemeSetting {
    private const val FILE = "theme"
    private const val KEY = "choice"

    fun get(context: Context): AppTheme = runCatching {
        AppTheme.valueOf(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: "")
    }.getOrDefault(AppTheme.BLUE)

    fun set(context: Context, theme: AppTheme) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, theme.name).apply()
    }
}
