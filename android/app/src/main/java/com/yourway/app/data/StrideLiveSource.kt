package com.yourway.app.data

import com.yourway.app.routing.LiveSource
import com.yourway.app.routing.VehicleFix
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import org.json.JSONArray

/** Live bus positions from Open Bus Stride, a community service built on the Ministry of Transport's real-time feed. */
class StrideLiveSource : LiveSource {
    override fun latestFix(lineRef: Int, scheduledStart: Instant): VehicleFix? {
        val time = URLEncoder.encode(scheduledStart.toString().replace("Z", "+00:00"), "UTF-8")
        val url = URL(
            "https://open-bus-stride-api.hasadna.org.il/siri_vehicle_locations/list?limit=1" +
                "&siri_routes__line_ref=$lineRef&siri_rides__scheduled_start_time_from=$time" +
                "&siri_rides__scheduled_start_time_to=$time&order_by=recorded_at_time%20desc",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("User-Agent", "YourWay (Android)")
        return try {
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun parse(json: String): VehicleFix? {
            val list = JSONArray(json)
            if (list.length() == 0) return null
            val row = list.getJSONObject(0)
            val at = row.getString("recorded_at_time").replace("+00:00", "Z")
            return VehicleFix(row.getDouble("lat"), row.getDouble("lon"), Instant.parse(at))
        }
    }
}
