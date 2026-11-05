package com.yourway.app

import com.yourway.app.data.ScheduleUpdater
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleUpdaterTest {
    private val schedule = File("src/main/assets/schedule.dat").readBytes()
    private val stops = File("src/main/assets/stops.dat").readBytes()
    private lateinit var root: File
    private val memory = object : ScheduleUpdater.Memory {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }
    private var version = "20261105-0100"
    private var scheduleServed = schedule
    private var corruptChecksum = false

    @Before
    fun setUp() {
        root = Files.createTempDirectory("schedule-test").toFile()
        memory.map.clear()
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun serve(url: String): InputStream = when (url) {
        "manifest" -> {
            val wrong = if (corruptChecksum) "0".repeat(64) else sha(schedule)
            ByteArrayInputStream(
                """{"format":1,"version":"$version","files":{
                    "schedule.dat":{"url":"schedule","sha256":"$wrong","size":${schedule.size}},
                    "stops.dat":{"url":"stops","sha256":"${sha(stops)}","size":${stops.size}}}}""".toByteArray(),
            )
        }
        "schedule" -> ByteArrayInputStream(scheduleServed)
        "stops" -> ByteArrayInputStream(stops)
        else -> throw IllegalStateException("unexpected $url")
    }

    private fun updater(appVersion: Int = 9, today: LocalDate = LocalDate.of(2026, 11, 5)) =
        ScheduleUpdater(root, memory, appVersion, "manifest", ::serve, { today }, { 1_000_000_000_000L })

    @Test
    fun aGoodDownloadIsInstalledAndLoadedNextTime() {
        val outcome = updater().updateIfNeeded()
        assertTrue(outcome.message, outcome.updated)
        val data = updater().loadDownloaded()
        assertNotNull(data)
        assertEquals(version, data!!.downloadedVersion)
        assertEquals(data.stops.count, data.schedule.stopCount)
        assertFalse(File(root, "staging").exists())
    }

    @Test
    fun aFileThatFailsItsChecksumIsRejectedAndNothingChanges() {
        corruptChecksum = true
        val outcome = updater().updateIfNeeded()
        assertFalse(outcome.updated)
        assertTrue(outcome.message, outcome.message.contains("checksum"))
        assertNull(updater().loadDownloaded())
        assertFalse(File(root, "staging").exists())
    }

    @Test
    fun aTruncatedDownloadIsRejected() {
        scheduleServed = schedule.copyOf(schedule.size / 2)
        val outcome = updater().updateIfNeeded()
        assertFalse(outcome.message, outcome.updated)
        assertNull(updater().loadDownloaded())
    }

    @Test
    fun theSameVersionIsNotDownloadedAgain() {
        assertTrue(updater().updateIfNeeded().updated)
        val again = updater().updateIfNeeded(force = true)
        assertFalse(again.updated)
        assertEquals("up to date", again.message)
    }

    @Test
    fun aNewerVersionReplacesTheOldOne() {
        updater().updateIfNeeded()
        version = "20261106-0100"
        assertTrue(updater().updateIfNeeded(force = true).updated)
        assertEquals("20261106-0100", updater().loadDownloaded()!!.downloadedVersion)
        assertFalse(File(root, "20261105-0100").exists())
    }

    @Test
    fun aDownloadedScheduleIsDroppedWhenTheAppItselfIsUpdated() {
        updater(appVersion = 9).updateIfNeeded()
        assertNull(updater(appVersion = 10).loadDownloaded())
    }

    @Test
    fun anAlreadyExpiredScheduleIsNotInstalled() {
        val outcome = updater(today = LocalDate.of(2030, 1, 1)).updateIfNeeded()
        assertFalse(outcome.updated)
        assertTrue(outcome.message, outcome.message.contains("expired"))
    }

    @Test
    fun aServerFailureIsReportedNotThrown() {
        val broken = ScheduleUpdater(root, memory, 9, "manifest", { throw java.io.IOException("offline") })
        val outcome = broken.updateIfNeeded()
        assertFalse(outcome.updated)
        assertTrue(outcome.message, outcome.message.startsWith("failed"))
    }

    @Test
    fun checksAreNotRepeatedWithinTwelveHours() {
        updater().updateIfNeeded()
        version = "20261106-0100"
        assertEquals("checked recently", updater().updateIfNeeded().message)
    }
}
