package com.yourway.app

import androidx.test.core.app.ApplicationProvider
import com.yourway.app.data.StopDirectory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StopFileTest {
    private val app: YourWayApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun realFileLoadsAndSearchesInBothLanguages() {
        val stops = app.assets.open("stops.dat").use { StopDirectory.read(it) }
        assertEquals("Herzl/Bilu Junction", stops.search("38832", hebrew = false).single().name)
        assertEquals("הרצל/צומת בילו", stops.search("38832", hebrew = true).single().name)
        assertEquals("Kiryat Ekron", stops.search("38832", hebrew = false).single().subtitle)
    }
}
