package com.yourway.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/** Saves the details of a crash so the next launch can offer them to send, since testers have no cable to a PC. */
object CrashReporter {
    private const val FILE_NAME = "crash-report.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(appContext, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun save(context: Context, threadName: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        file(context).writeText(
            buildString {
                appendLine("Your Way ${BuildConfig.VERSION_NAME}")
                appendLine("Time: ${Instant.now()}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Thread: $threadName")
                append(trace)
            },
        )
    }

    fun pending(context: Context): String? = runCatching {
        file(context).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
