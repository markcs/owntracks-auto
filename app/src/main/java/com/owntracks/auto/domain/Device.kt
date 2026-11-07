package com.owntracks.auto.domain

/**
 * A device/user as presented in the UI, combining identity from `/trackinfos`
 * with its resolved last-known endpoint from `/positions`.
 */
data class Device(
    val deviceId: Int,
    val userId: String,
    val device: String,
    val tid: String,
    /** Last known endpoint, or null if no position was available for the day. */
    val lastEndpoint: Endpoint?,
    /** `ts_start` of the device's track for the day; identifies it to `/trackpoints`. */
    val trackStart: String = "",
) {
    /** Title: the user, falling back to the device name, then the numeric id. */
    val label: String
        get() = userId.ifBlank { device.ifBlank { "Device $deviceId" } }

    /** Secondary line: the device name only (blank if it is already the title). */
    val subtitle: String
        get() = device.takeIf { it.isNotBlank() && it != label }.orEmpty()
}

/** A geographic point the user can navigate to. */
data class Endpoint(
    val latitude: Double,
    val longitude: Double,
    /** Fix timestamp string, "YYYY-MM-DD HH:MM:SS+00" (UTC), may be blank. */
    val timestamp: String = "",
    /** Reverse-geocoded address label, resolved on the client. Null if unknown. */
    val address: String? = null,
    /**
     * True when this is the last point of the selected day's track rather than
     * the device's current fix (i.e. when viewing an earlier day).
     */
    val fromHistory: Boolean = false,
)

/** One recorded position on a device's track. */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    /** UTC fix time, "YYYY-MM-DD HH:MM:SS+00". */
    val time: String,
    /** Address from the server's geocoder, or resolved on the client when tapped. */
    val address: String? = null,
)
