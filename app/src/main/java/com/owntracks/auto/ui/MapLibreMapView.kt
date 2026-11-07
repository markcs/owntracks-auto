package com.owntracks.auto.ui

import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.owntracks.auto.domain.Device
import com.owntracks.auto.domain.TrackPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Default street map style (OpenFreeMap Liberty, no API key). Replace with your own. */
private const val DEFAULT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val SRC_TRACK_LINE = "track-line"
private const val SRC_TRACK_POINTS = "track-points"
private const val SRC_TRACK_SELECTED = "track-selected"
private const val LAYER_TRACK_LINE = "track-line-layer"
private const val LAYER_TRACK_POINTS = "track-points-layer"
private const val LAYER_TRACK_SELECTED = "track-selected-layer"
private const val PROP_INDEX = "idx"

private const val TRACK_COLOR = "#1565C0"
private const val SELECTED_COLOR = "#E65100"

/** Holds the map once its style has loaded, plus camera bookkeeping. */
private class MapHolder {
    var map: MapLibreMap? = null
    var style: Style? = null
    /** Identifies what the camera was last framed on, so it only moves when that changes. */
    var cameraKey: Any? = null
}

/**
 * A MapLibre map showing device markers, the selected device's track (a line
 * plus tappable points), the tapped track point, and an optional dropped pin.
 *
 * @param onMarkerClick invoked with the device id when a device marker is tapped
 * @param onLongPress invoked with (lat, lon) when the user long-presses the map
 * @param onTrackPointClick invoked with the index into [track] of a tapped point
 */
@Composable
fun MapLibreMapView(
    devices: List<Device>,
    selectedDeviceId: Int?,
    droppedPin: DroppedPin?,
    track: List<TrackPoint>,
    selectedPointIndex: Int?,
    onMarkerClick: (Int) -> Unit,
    onLongPress: (Double, Double) -> Unit,
    onTrackPointClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Initialise MapLibre once per process before any MapView is created.
    remember { MapLibre.getInstance(context); true }

    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val holder = remember { MapHolder() }
    var styleReady by remember { mutableStateOf(false) }

    // Listeners are registered once; these keep them pointing at the latest callbacks.
    val markerClick by rememberUpdatedState(onMarkerClick)
    val longPress by rememberUpdatedState(onLongPress)
    val pointClick by rememberUpdatedState(onTrackPointClick)

    // Tap tolerance around a track point, in pixels.
    val hitPx = with(LocalDensity.current) { 16f * density }

    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(DEFAULT_STYLE_URL)) { style ->
                addTrackLayers(style)
                holder.map = map
                holder.style = style
                styleReady = true
            }
            map.setOnMarkerClickListener { marker ->
                val id = marker.snippet?.toIntOrNull() ?: return@setOnMarkerClickListener false
                markerClick(id)
                true
            }
            map.addOnMapClickListener { latLng ->
                val p = map.projection.toScreenLocation(latLng)
                val hit = map.queryRenderedFeatures(
                    RectF(p.x - hitPx, p.y - hitPx, p.x + hitPx, p.y + hitPx),
                    LAYER_TRACK_POINTS,
                )
                // Several points can overlap (e.g. while stationary); take the latest fix.
                val index = hit.mapNotNull { it.getNumberProperty(PROP_INDEX)?.toInt() }.maxOrNull()
                if (index != null) {
                    pointClick(index)
                    true
                } else {
                    false
                }
            }
            map.addOnMapLongClickListener { point ->
                longPress(point.latitude, point.longitude)
                true
            }
        }
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(modifier = modifier, factory = { mapView })

    if (styleReady) {
        LaunchedEffect(devices, droppedPin, track, selectedPointIndex, selectedDeviceId) {
            val map = holder.map ?: return@LaunchedEffect
            val style = holder.style ?: return@LaunchedEffect
            renderMarkers(map, devices, droppedPin)
            renderTrack(style, track, selectedPointIndex)
            moveCamera(map, holder, devices, selectedDeviceId, droppedPin, track)
        }
    }
}

/** Create the (initially empty) track sources and layers. Line under points under selection. */
private fun addTrackLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_TRACK_LINE))
    style.addSource(GeoJsonSource(SRC_TRACK_POINTS))
    style.addSource(GeoJsonSource(SRC_TRACK_SELECTED))
    style.addLayer(
        LineLayer(LAYER_TRACK_LINE, SRC_TRACK_LINE).withProperties(
            PropertyFactory.lineColor(TRACK_COLOR),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer(LAYER_TRACK_POINTS, SRC_TRACK_POINTS).withProperties(
            PropertyFactory.circleRadius(4f),
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleStrokeColor(TRACK_COLOR),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
    style.addLayer(
        CircleLayer(LAYER_TRACK_SELECTED, SRC_TRACK_SELECTED).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(SELECTED_COLOR),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
}

private fun renderTrack(style: Style, track: List<TrackPoint>, selectedPointIndex: Int?) {
    val points = track.map { Point.fromLngLat(it.longitude, it.latitude) }

    val lineFeatures =
        if (points.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(points))) else emptyList()
    style.getSourceAs<GeoJsonSource>(SRC_TRACK_LINE)
        ?.setGeoJson(FeatureCollection.fromFeatures(lineFeatures))

    val pointFeatures = points.mapIndexed { i, p ->
        Feature.fromGeometry(p).apply { addNumberProperty(PROP_INDEX, i) }
    }
    style.getSourceAs<GeoJsonSource>(SRC_TRACK_POINTS)
        ?.setGeoJson(FeatureCollection.fromFeatures(pointFeatures))

    val selected = selectedPointIndex?.let { points.getOrNull(it) }
    style.getSourceAs<GeoJsonSource>(SRC_TRACK_SELECTED)
        ?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(selected?.let { Feature.fromGeometry(it) })))
}

private fun renderMarkers(map: MapLibreMap, devices: List<Device>, droppedPin: DroppedPin?) {
    map.clear() // removes marker annotations only; style layers are untouched
    devices.forEach { device ->
        val ep = device.lastEndpoint ?: return@forEach
        map.addMarker(
            MarkerOptions()
                .position(LatLng(ep.latitude, ep.longitude))
                .title(device.label)
                .snippet(device.deviceId.toString()),
        )
    }
    droppedPin?.let {
        map.addMarker(MarkerOptions().position(LatLng(it.latitude, it.longitude)).title("Dropped pin"))
    }
}

/**
 * Frame the selected device's track, else the selected device, else everything.
 * Only moves when the framing target changes, so tapping a point or dropping a
 * pin does not yank the camera away from where the user is looking.
 */
private fun moveCamera(
    map: MapLibreMap,
    holder: MapHolder,
    devices: List<Device>,
    selectedDeviceId: Int?,
    droppedPin: DroppedPin?,
    track: List<TrackPoint>,
) {
    val selected = devices.find { it.deviceId == selectedDeviceId }
    // Deliberately excludes the point count and device positions, so a refresh that
    // adds points or moves markers does not move the camera.
    val key: Any? = when {
        selected != null && track.isNotEmpty() -> Triple("track", selectedDeviceId, track.first().time)
        selected?.lastEndpoint != null -> "device" to selectedDeviceId
        devices.isNotEmpty() -> "all" to devices.map { it.deviceId }.toSet()
        else -> null
    }
    if (key == null || key == holder.cameraKey) return
    holder.cameraKey = key

    val targets: List<LatLng> = when {
        selected != null && track.isNotEmpty() -> track.map { LatLng(it.latitude, it.longitude) }
        selected?.lastEndpoint != null -> listOf(selected.lastEndpoint.let { LatLng(it.latitude, it.longitude) })
        else -> devices.mapNotNull { d -> d.lastEndpoint?.let { LatLng(it.latitude, it.longitude) } } +
            listOfNotNull(droppedPin?.let { LatLng(it.latitude, it.longitude) })
    }
    fitCamera(map, targets)
}

private fun fitCamera(map: MapLibreMap, targets: List<LatLng>) {
    if (targets.isEmpty()) return
    val minLat = targets.minOf { it.latitude }
    val maxLat = targets.maxOf { it.latitude }
    val minLon = targets.minOf { it.longitude }
    val maxLon = targets.maxOf { it.longitude }
    // A single point, or a track that never moved, has no area to fit.
    if (maxLat - minLat < 1e-4 && maxLon - minLon < 1e-4) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(minLat, minLon), 15.0))
        return
    }
    val bounds = LatLngBounds.Builder().includes(targets).build()
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
}
