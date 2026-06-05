package com.caminerin.backingtrack

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists uncaught exceptions to a file so a non-developer user can see/share
 * the real cause on next launch instead of just "the app closed".
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun file(app: Application): File = File(app.filesDir, FILE)

    fun readAndClear(app: Application): String? {
        val f = file(app)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull()
        runCatching { f.delete() }
        return text?.takeIf { it.isNotBlank() }
    }

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file(app).writeText("[$ts] hilo=${thread.name}\n$sw")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}

class BackingTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
