package io.github.nanobuo.myiptv.util

import android.content.Context
import android.os.Build
import io.github.nanobuo.myiptv.BuildConfig
import java.io.File
import java.util.Date

/**
 * Saves the details of a crash so they can be shown (and shared) at the next start.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(appContext.filesDir, FILE).writeText(
                    "myIPTV ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                        "${Build.MANUFACTURER} ${Build.MODEL}\n${Date()} · thread ${thread.name}\n\n" +
                        error.stackTraceToString().take(12_000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Returns the last crash report, if any, and removes it. */
    fun consume(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull().also { file.delete() }
    }
}
