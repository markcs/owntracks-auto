package com.owntracks.auto.data

import retrofit2.http.GET
import retrofit2.http.Query

/** Retrofit interface for the owntrack-rs REST API. */
interface ApiService {

    /** Devices/users that reported on [date] (local calendar day, `YYYY-MM-DD`). */
    @GET("trackinfos")
    suspend fun trackInfos(
        @Query("date") date: String,
        @Query("tz") tzMinutes: Int,
    ): List<TrackInfoDto>

    /** Per-device latest positions for [date] as a GeoJSON FeatureCollection. */
    @GET("positions")
    suspend fun positions(
        @Query("date") date: String,
        @Query("tz") tzMinutes: Int,
    ): FeatureCollectionDto

    /** The single most recent fix across all devices, or null. */
    @GET("latest-position")
    suspend fun latestPosition(): LatestPositionDto?

    /**
     * All points of a device's track for the local day containing [tsStart]
     * (the `ts_start` from `/trackinfos`).
     */
    @GET("trackpoints")
    suspend fun trackPoints(
        @Query("device_id") deviceId: Int,
        @Query("ts_start") tsStart: String,
        @Query("tz") tzMinutes: Int,
    ): TrackPointsDto
}
