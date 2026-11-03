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
        val url = URL("https://photon.komoot.io/api/?q=$query&limit=5&lang=$language&bbox=$ISRAEL_BOUNDS")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.setRequestProperty("User-Agent", "YourWay/0.2 (Android)")
        return try {
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ISRAEL_BOUNDS = "34.2,29.4,35.95,33.4"

        fun parse(json: String): List<Place> {
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
                Place(title, p.optString("city"), point.getDouble(1), point.getDouble(0), PlaceKind.ADDRESS)
            }.distinctBy { it.name to it.subtitle }
        }
    }
}
