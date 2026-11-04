package com.yourway.app.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** Address and place search using the public Photon service, which is based on OpenStreetMap. */
class PhotonSearch : PlaceSearch {
    override fun search(text: String, hebrew: Boolean): List<Place> {
        val query = URLEncoder.encode(text, "UTF-8")
        val language = if (hebrew) "default" else "en"
        val url = URL("https://photon.komoot.io/api/?q=$query&limit=8&lang=$language&bbox=$ISRAEL_BOUNDS")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.setRequestProperty("User-Agent", "YourWay/0.2 (Android)")
        return try {
            parse(connection.inputStream.bufferedReader().use { it.readText() }, hebrew)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ISRAEL_BOUNDS = "34.2,29.4,35.95,33.4"

        fun parse(json: String, hebrew: Boolean = false): List<Place> {
            val features = JSONObject(json).getJSONArray("features")
            return (0 until features.length()).mapNotNull { i ->
                val feature = features.getJSONObject(i)
                val p = feature.getJSONObject("properties")
                val street = listOf(p.optString("street"), p.optString("housenumber"))
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                val title = p.optString("name").ifEmpty { street }
                if (title.isEmpty()) return@mapNotNull null
                val point = feature.getJSONObject("geometry").getJSONArray("coordinates")
                val kind = categoryOf(p.optString("osm_value"), hebrew)
                val city = p.optString("city")
                val subtitle = listOf(kind, city).filter { it.isNotEmpty() }.joinToString(" · ")
                Place(title, subtitle, point.getDouble(1), point.getDouble(0), PlaceKind.ADDRESS)
            }.distinctBy { it.name to it.subtitle }
                // In Hebrew, names written in Hebrew come first (Photon has no Hebrew language option).
                .let { list -> if (hebrew) list.sortedBy { if (HEBREW_LETTER.containsMatchIn(it.name)) 0 else 1 } else list }
        }

        private val HEBREW_LETTER = Regex("[א-ת]")

        private val CATEGORIES = mapOf(
            "mall" to ("Mall" to "קניון"), "supermarket" to ("Supermarket" to "סופרמרקט"),
            "hospital" to ("Hospital" to "בית חולים"), "clinic" to ("Clinic" to "מרפאה"),
            "university" to ("University" to "אוניברסיטה"), "college" to ("College" to "מכללה"),
            "school" to ("School" to "בית ספר"), "restaurant" to ("Restaurant" to "מסעדה"),
            "cafe" to ("Cafe" to "בית קפה"), "pharmacy" to ("Pharmacy" to "בית מרקחת"),
            "bank" to ("Bank" to "בנק"), "library" to ("Library" to "ספרייה"),
            "museum" to ("Museum" to "מוזיאון"), "park" to ("Park" to "פארק"),
            "stadium" to ("Stadium" to "אצטדיון"), "cinema" to ("Cinema" to "קולנוע"),
            "theatre" to ("Theatre" to "תיאטרון"), "hotel" to ("Hotel" to "מלון"),
            "place_of_worship" to ("Place of worship" to "בית תפילה"), "station" to ("Station" to "תחנה"),
            "bus_station" to ("Bus station" to "תחנה מרכזית"), "airport" to ("Airport" to "שדה תעופה"),
            "beach" to ("Beach" to "חוף"), "marketplace" to ("Market" to "שוק"),
            "police" to ("Police" to "משטרה"), "post_office" to ("Post office" to "דואר"),
            "attraction" to ("Attraction" to "אטרקציה"), "zoo" to ("Zoo" to "גן חיות"),
        )

        private fun categoryOf(value: String, hebrew: Boolean): String =
            CATEGORIES[value]?.let { if (hebrew) it.second else it.first } ?: ""
    }
}
