package com.owntracks.auto.domain

import android.content.Context
import com.owntracks.auto.data.ApiClientFactory
import com.owntracks.auto.data.FeatureCollectionDto
import com.owntracks.auto.settings.SettingsStore
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Repository that fetches devices and their last-known endpoints from the
 * owntrack-rs API. Reads the current base URL from [settings] on each call so
 * configuration changes take effect without restarting.
 */
class DeviceRepository(
    context: Context,
    private val settings: SettingsStore,
) {

    private val addresses = AddressResolver(context)

    /** Resolve the API service against the currently-configured base URL. */
    private suspend fun api() =
        ApiClientFactory.create(settings.baseUrlFlow.first(), settings.basicAuthFlow.first())

    /**
     * Load the devices seen on [date] (defaults to today), each enriched with
     * its last known endpoint from `/positions` and a client-side reverse-
     * geocoded address.
     *
     * The "last endpoint" for a device is the most recent `/positions` feature
     * for that `device_id` on the given day. `/positions` already returns one
     * row per device (the device's current fix), so we simply match by id.
     */
    suspend fun loadDevices(date: Date = Date()): List<Device> {
        val service = api()
        val isoDate = isoDate(date)
        val tz = utcOffsetMinutes(date)

        val infos = service.trackInfos(isoDate, tz)
        val positions = runCatching { service.positions(isoDate, tz) }
            .getOrElse { FeatureCollectionDto() }

        val endpointByDevice: Map<Int, Endpoint> = positions.features.mapNotNull { f ->
            val geom = f.geometry ?: return@mapNotNull null
            val props = f.properties ?: return@mapNotNull null
            if (geom.type != "Point" || geom.coordinates.size < 2) return@mapNotNull null
            val lon = geom.coordinates[0]
            val lat = geom.coordinates[1]
            props.deviceId to Endpoint(latitude = lat, longitude = lon, timestamp = props.time)
        }.toMap()

        return infos.map { info ->
            val device = Device(
                deviceId = info.deviceId,
                userId = info.userId,
                device = info.device,
                tid = info.tid,
                lastEndpoint = null,
                trackStart = info.tsStart,
            )
            // `/positions` only holds each device's *current* fix, so for an
            // earlier day it is usually missing. Every device listed by
            // `/trackinfos` did report that day, so use its last point instead.
            val endpoint = endpointByDevice[info.deviceId]
                ?: runCatching { loadTrack(device, date).lastOrNull() }.getOrNull()?.let { last ->
                    Endpoint(
                        latitude = last.latitude,
                        longitude = last.longitude,
                        timestamp = last.time,
                        address = last.address,
                        fromHistory = true,
                    )
                }
            device.copy(
                lastEndpoint = endpoint?.let { ep ->
                    ep.copy(address = ep.address ?: addresses.resolve(ep.latitude, ep.longitude))
                },
            )
        }
    }

    /** The single most recent fix across all devices, as an [Endpoint]. */
    suspend fun latestEndpoint(): Endpoint? {
        val pos = api().latestPosition() ?: return null
        // Prefer the server's reverse-geocoded address; fall back to the client.
        val address = pos.address ?: addresses.resolve(pos.y, pos.x)
        return Endpoint(latitude = pos.y, longitude = pos.x, timestamp = pos.ts, address = address)
    }

    /** The raw `/positions` GeoJSON FeatureCollection for a day (map source). */
    suspend fun positions(date: Date = Date()): FeatureCollectionDto =
        api().positions(isoDate(date), utcOffsetMinutes(date))

    /**
     * The [device]'s recorded positions for the day of [date], oldest first.
     * Empty if the device has no track that day.
     */
    suspend fun loadTrack(device: Device, date: Date): List<TrackPoint> {
        if (device.trackStart.isBlank()) return emptyList()
        val dto = api().trackPoints(device.deviceId, device.trackStart, utcOffsetMinutes(date))
        return dto.features.mapNotNull { f ->
            val coords = f.geometry?.coordinates ?: return@mapNotNull null
            if (f.geometry.type != "Point" || coords.size < 2) return@mapNotNull null
            TrackPoint(
                latitude = coords[1],
                longitude = coords[0],
                time = f.properties?.time.orEmpty(),
                address = f.properties?.address?.takeIf { it.isNotBlank() },
            )
        }
    }

    /** Client-side reverse geocode, for points the server has not geocoded. */
    suspend fun resolveAddress(lat: Double, lon: Double): String? = addresses.resolve(lat, lon)

    companion object {
        /** Format a date as the local calendar `YYYY-MM-DD` the API expects. */
        fun isoDate(date: Date): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

        /** The device's current UTC offset in minutes (e.g. 660 for AEDT). */
        fun utcOffsetMinutes(date: Date): Int =
            TimeZone.getDefault().getOffset(date.time) / 60000
    }
}
