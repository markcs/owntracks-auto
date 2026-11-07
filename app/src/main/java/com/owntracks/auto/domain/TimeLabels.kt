package com.owntracks.auto.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Formatting of the server's UTC timestamps ("YYYY-MM-DD HH:MM:SS+00") for display. */
object TimeLabels {

    /** Parse a server UTC stamp, or null if it is not in the expected form. */
    fun parseUtc(utcStamp: String): Date? {
        val base = utcStamp.substringBefore('+').trim()
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return runCatching { parser.parse(base) }.getOrNull()
    }

    /** Local "HH:mm" for a server UTC stamp; the input unchanged if unparseable. */
    fun localTime(utcStamp: String): String {
        val date = parseUtc(utcStamp) ?: return utcStamp
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    /**
     * A short comment saying how current an endpoint is:
     * "Last seen at 17:45" for a current fix, or
     * "Last recorded at 17:45 on Thu 5 Nov" for the end of an earlier day's track.
     * Empty when the endpoint has no usable timestamp.
     */
    fun endpointComment(endpoint: Endpoint): String {
        val date = parseUtc(endpoint.timestamp) ?: return ""
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        if (!endpoint.fromHistory) return "Last seen at $time"
        val day = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(date)
        return "Last recorded at $time on $day"
    }
}
