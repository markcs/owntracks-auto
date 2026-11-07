package com.owntracks.auto.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row from `GET /trackinfos?date&tz`.
 *
 * Mirrors `owntrack-rs` `TrackInfo`: device_id, user_id, device, tid,
 * ts_start, ts_end (timestamps are "YYYY-MM-DD HH:MM:SS+00" strings).
 */
@Serializable
data class TrackInfoDto(
    @SerialName("device_id") val deviceId: Int,
    @SerialName("user_id") val userId: String = "",
    val device: String = "",
    val tid: String = "",
    @SerialName("ts_start") val tsStart: String = "",
    @SerialName("ts_end") val tsEnd: String = "",
)

/**
 * The single most recent fix from `GET /latest-position`, or `null`.
 *
 * Note the coordinate convention: `x` is longitude, `y` is latitude.
 */
@Serializable
data class LatestPositionDto(
    @SerialName("device_id") val deviceId: Int,
    val x: Double, // longitude
    val y: Double, // latitude
    val ts: String = "",
    val tid: String = "",
    val address: String? = null,
)

// --- Minimal GeoJSON model for `GET /positions?date&tz` --------------------

/** GeoJSON FeatureCollection as returned by `/positions` and `/track`. */
@Serializable
data class FeatureCollectionDto(
    val type: String = "FeatureCollection",
    val features: List<FeatureDto> = emptyList(),
)

@Serializable
data class FeatureDto(
    val type: String = "Feature",
    val geometry: GeometryDto? = null,
    val properties: PositionPropsDto? = null,
)

/**
 * GeoJSON geometry. `coordinates` is kept as raw JSON-decoded doubles; for a
 * Point it is `[lon, lat]`, for a LineString it is `[[lon,lat], ...]`.
 * We only decode Point geometries into [coordinates]; LineString handling for
 * the track overlay is done separately by the map layer which consumes the
 * raw GeoJSON string directly.
 */
@Serializable
data class GeometryDto(
    val type: String = "",
    val coordinates: List<Double> = emptyList(),
)

/** Properties attached to each `/positions` point feature. */
@Serializable
data class PositionPropsDto(
    @SerialName("device_id") val deviceId: Int = -1,
    val time: String = "",
    val tid: String = "",
    val speed: Int? = null,
    val elevation: Int? = null,
    val accuracy: Int? = null,
    @SerialName("v_accuracy") val vAccuracy: Int? = null,
    val cog: Int? = null,
)

// --- `GET /trackpoints?device_id&ts_start&tz` -------------------------------

/** One day's track for a device as GeoJSON Points (extra `bbox`/`stats` ignored). */
@Serializable
data class TrackPointsDto(
    val features: List<TrackPointFeatureDto> = emptyList(),
)

@Serializable
data class TrackPointFeatureDto(
    val geometry: GeometryDto? = null,
    val properties: TrackPointPropsDto? = null,
)

/** Properties of a track point. `address` is set when server geocoding is on. */
@Serializable
data class TrackPointPropsDto(
    /** UTC fix time, "YYYY-MM-DD HH:MM:SS+00". */
    val time: String = "",
    val address: String? = null,
    val accuracy: Int? = null,
    val speed: Int? = null,
)
