package com.yourway.app.data

import android.content.Context
import com.yourway.app.routing.Schedule
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import org.json.JSONObject

/** The schedule and stop list the app uses: the downloaded pair when there is a good one, otherwise the pair shipped inside the app. */
class ScheduleData(val schedule: Schedule, val stops: StopDirectory, val downloadedVersion: String?)

/**
 * Keeps the schedule fresh. A nightly GitHub job publishes a manifest naming the newest schedule and stop files with their
 * sizes and sha256. This downloads both into a staging folder, checks them, loads them once to be sure they are usable
 * and match each other, and only then makes them the current pair. The new pair is used from the next launch; anything
 * that goes wrong leaves the pair already in use untouched.
 */
class ScheduleUpdater(
    private val root: File,
    private val state: Memory,
    private val appVersion: Int,
    private val manifestUrl: String = MANIFEST_URL,
    private val open: (String) -> InputStream = ::openUrl,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /** Where the updater remembers things (SharedPreferences in the app). */
    interface Memory {
        fun get(key: String): String?
        fun put(key: String, value: String?)
    }

    class Outcome(val updated: Boolean, val message: String)

    /** Loads the downloaded pair if one is current for this app version, otherwise null. Never throws. */
    fun loadDownloaded(): ScheduleData? {
        val version = state.get(KEY_VERSION) ?: return null
        if (state.get(KEY_APP) != appVersion.toString()) {
            forget()
            return null
        }
        return runCatching { read(File(root, version), version) }.getOrElse {
            forget()
            null
        }
    }

    /** Checks for a newer schedule (at most every [MIN_INTERVAL_MILLIS] unless [force]) and installs it. Never throws. */
    fun updateIfNeeded(force: Boolean = false): Outcome = try {
        val last = state.get(KEY_CHECKED)?.toLongOrNull() ?: 0L
        if (!force && nowMillis() - last < MIN_INTERVAL_MILLIS) {
            Outcome(false, "checked recently")
        } else {
            val result = update()
            state.put(KEY_CHECKED, nowMillis().toString())
            result
        }
    } catch (e: Exception) {
        Outcome(false, "failed: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun update(): Outcome {
        val manifest = JSONObject(open(manifestUrl).use { String(it.readNBytes(MAX_MANIFEST_BYTES)) })
        val version = manifest.getString("version")
        require(version.matches(Regex("[0-9A-Za-z._-]{1,40}"))) { "bad version" }
        if (version == state.get(KEY_VERSION) && state.get(KEY_APP) == appVersion.toString()) return Outcome(false, "up to date")

        val staging = File(root, "staging").also { it.deleteRecursively(); it.mkdirs() }
        try {
            val files = manifest.getJSONObject("files")
            for (name in FILES) {
                val entry = files.getJSONObject(name)
                download(entry.getString("url"), File(staging, name), entry.getLong("size"), entry.getString("sha256"))
            }
            // Loading it is the real test: a file that parses and matches its partner is one the planner can use.
            val data = read(staging, version)
            check(data.schedule.lastDate >= dateNumber(today())) { "downloaded schedule has already expired" }
            val final = File(root, version)
            final.deleteRecursively()
            check(staging.renameTo(final)) { "could not move the new schedule into place" }
            val previous = state.get(KEY_VERSION)
            state.put(KEY_APP, appVersion.toString())
            state.put(KEY_VERSION, version)
            if (previous != null && previous != version) File(root, previous).deleteRecursively()
            return Outcome(true, "installed $version")
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun read(dir: File, version: String): ScheduleData {
        val schedule = File(dir, "schedule.dat").inputStream().use { Schedule.read(it) }
        val stops = File(dir, "stops.dat").inputStream().use { StopDirectory.read(it) }
        check(schedule.stopCount == stops.count) { "schedule and stop list do not match" }
        return ScheduleData(schedule, stops, version)
    }

    private fun download(url: String, to: File, size: Long, sha256: String) {
        require(size in 1..MAX_FILE_BYTES) { "unreasonable file size" }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        open(url).use { input ->
            to.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    check(total <= size) { "${to.name} is larger than announced" }
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
            }
        }
        check(total == size) { "${to.name} is incomplete" }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(sha256, ignoreCase = true)) { "${to.name} failed its checksum" }
    }

    private fun forget() {
        state.get(KEY_VERSION)?.let { File(root, it).deleteRecursively() }
        state.put(KEY_VERSION, null)
    }

    companion object {
        const val MANIFEST_URL = "https://github.com/yorairh/your-way/releases/download/schedule-latest/manifest.json"
        const val KEY_VERSION = "version"
        const val KEY_APP = "app_version"
        const val KEY_CHECKED = "checked_at"
        const val MIN_INTERVAL_MILLIS = 12 * 60 * 60 * 1000L
        private const val MAX_MANIFEST_BYTES = 64 * 1024
        private const val MAX_FILE_BYTES = 60L * 1024 * 1024
        private val FILES = listOf("schedule.dat", "stops.dat")

        private fun dateNumber(d: LocalDate) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth

        private fun openUrl(url: String): InputStream {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "YourWay (Android)")
            check(connection.responseCode == 200) { "server answered ${connection.responseCode}" }
            return connection.inputStream
        }

        fun create(context: Context, appVersion: Int): ScheduleUpdater {
            val prefs = context.getSharedPreferences("schedule_update", Context.MODE_PRIVATE)
            val memory = object : Memory {
                override fun get(key: String) = prefs.getString(key, null)
                override fun put(key: String, value: String?) {
                    prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.commit()
                }
            }
            return ScheduleUpdater(File(context.filesDir, "schedule"), memory, appVersion)
        }
    }
}
