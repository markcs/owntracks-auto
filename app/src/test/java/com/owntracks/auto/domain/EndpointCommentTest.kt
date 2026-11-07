package com.owntracks.auto.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class EndpointCommentTest {

    private lateinit var savedZone: TimeZone
    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        savedLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Melbourne")) // UTC+11 in November
        Locale.setDefault(Locale.UK)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    private fun endpoint(ts: String, fromHistory: Boolean) =
        Endpoint(latitude = 0.0, longitude = 0.0, timestamp = ts, fromHistory = fromHistory)

    @Test
    fun currentFixSaysLastSeen() {
        assertEquals("Last seen at 17:45", TimeLabels.endpointComment(endpoint("2026-11-06 06:45:12+00", false)))
    }

    @Test
    fun earlierDaySaysLastRecordedWithLocalDay() {
        // 2026-11-05 12:30 UTC is 23:30 on Thu 5 Nov in Melbourne.
        assertEquals(
            "Last recorded at 23:30 on Thu 5 Nov",
            TimeLabels.endpointComment(endpoint("2026-11-05 12:30:00+00", true)),
        )
    }

    @Test
    fun missingTimestampGivesNoComment() {
        assertEquals("", TimeLabels.endpointComment(endpoint("", true)))
    }
}
