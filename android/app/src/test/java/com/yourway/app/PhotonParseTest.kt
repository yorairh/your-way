package com.yourway.app

import com.yourway.app.data.PhotonSearch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotonParseTest {
    private val json = """{"features":[
      {"geometry":{"coordinates":[34.9,32.8]},"properties":{"name":"Grand Mall","osm_value":"mall","city":"Haifa"}},
      {"geometry":{"coordinates":[34.8,32.0]},"properties":{"name":"קניון עזריאלי","osm_value":"mall","city":"תל אביב"}}]}"""

    @Test
    fun placesShowTheirKindAndHebrewComesFirstInHebrew() {
        val english = PhotonSearch.parse(json, hebrew = false)
        assertEquals("Mall · Haifa", english[0].subtitle)
        val hebrew = PhotonSearch.parse(json, hebrew = true)
        assertEquals("קניון עזריאלי", hebrew[0].name)
        assertEquals("קניון · תל אביב", hebrew[0].subtitle)
    }
}
