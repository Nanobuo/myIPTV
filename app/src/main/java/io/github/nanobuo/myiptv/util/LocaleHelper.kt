package io.github.nanobuo.myiptv.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language selection. Stored in plain SharedPreferences because it must be read
 * synchronously in [android.app.Activity.attachBaseContext].
 */
object LocaleHelper {
    const val SYSTEM = "system"
    private const val PREFS = "locale"
    private const val KEY = "language"

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).apply()
    }

    fun wrap(base: Context): Context {
        val language = getLanguage(base)
        if (language == SYSTEM) return base
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
