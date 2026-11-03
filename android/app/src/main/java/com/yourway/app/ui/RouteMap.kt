package com.yourway.app.ui

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yourway.app.R
import com.yourway.app.data.Itinerary
import com.yourway.app.data.LatLon
import com.yourway.app.data.TransitLeg
import com.yourway.app.data.TransitMode
import com.yourway.app.data.WalkLeg
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** Colours are plain strings because the map styles read them from each line's properties. */
private fun TransitMode.color() = when (this) {
    TransitMode.BUS -> "#1565C0"
    TransitMode.TRAIN -> "#2E7D32"
    TransitMode.LIGHT_RAIL -> "#C62828"
}

/**
 * The route on a map. Anything that goes wrong, from missing native code to no internet, shows a message instead,
 * because the steps below the map are still enough to follow the trip.
 */
@Composable
fun RouteMap(trip: Itinerary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember(trip) { mutableStateOf(false) }
    val mapView = remember(trip) { runCatching { MapLibre.getInstance(context); MapView(context) }.getOrNull() }

    if (mapView == null || failed) {
        Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.map_unavailable), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            runCatching {
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        }
        runCatching { mapView.onCreate(null) }.onFailure { failed = true }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
        }
    }

    LaunchedEffect(mapView) {
        runCatching {
            mapView.addOnDidFailLoadingMapListener { failed = true }
            mapView.getMapAsync { map -> runCatching { show(map, trip) }.onFailure { failed = true } }
        }.onFailure { failed = true }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun show(map: MapLibreMap, trip: Itinerary) {
    map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
        style.addSource(GeoJsonSource("route", FeatureCollection.fromFeatures(lines(trip))))
        style.addSource(GeoJsonSource("stops", FeatureCollection.fromFeatures(markers(trip))))
        style.addLayer(
            LineLayer("walk-line", "route")
                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("walk")))
                .withProperties(
                    PropertyFactory.lineColor(Color.DKGRAY), PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)),
                ),
        )
        style.addLayer(
            LineLayer("ride-line", "route")
                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("ride")))
                .withProperties(
                    PropertyFactory.lineColor(Expression.get("color")), PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
                ),
        )
        style.addLayer(
            CircleLayer("stop-dots", "stops").withProperties(
                PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(Expression.get("color")),
                PropertyFactory.circleStrokeColor(Color.WHITE), PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
        // A trip that is one point, such as a walk of a few metres, can't be fitted, so zoom in on it instead.
        runCatching { fit(map, trip) }.onFailure {
            val at = markers(trip).first().geometry() as Point
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(at.latitude(), at.longitude()), 15.0))
        }
    }
}

private fun line(points: List<LatLon>, kind: String, color: String): Feature =
    Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })).also {
        it.addStringProperty("kind", kind)
        it.addStringProperty("color", color)
    }

private fun lines(trip: Itinerary): List<Feature> = trip.legs.map { leg ->
    when (leg) {
        is WalkLeg -> line(listOf(leg.from, leg.to), "walk", "#616161")
        is TransitLeg -> line(leg.path, "ride", leg.mode.color())
    }
}

/** A dot where the trip starts (green), ends (red) and where you board each ride in between (white). */
private fun markers(trip: Itinerary): List<Feature> {
    fun dot(at: LatLon, color: String) = Feature.fromGeometry(Point.fromLngLat(at.lon, at.lat)).also { it.addStringProperty("color", color) }
    val first = trip.legs.first()
    val last = trip.legs.last()
    val start = if (first is WalkLeg) first.from else (first as TransitLeg).path.first()
    val end = if (last is WalkLeg) last.to else (last as TransitLeg).path.last()
    return listOf(dot(start, "#2E7D32"), dot(end, "#C62828")) +
        trip.rides.drop(1).map { dot(it.path.first(), "#FFFFFF") }
}

private fun fit(map: MapLibreMap, trip: Itinerary) {
    val points = trip.legs.flatMap { leg ->
        when (leg) {
            is WalkLeg -> listOf(leg.from, leg.to)
            is TransitLeg -> leg.path
        }
    }.map { LatLng(it.lat, it.lon) }
    val bounds = LatLngBounds.Builder().includes(points).build()
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80, 80, 80, 80))
}
