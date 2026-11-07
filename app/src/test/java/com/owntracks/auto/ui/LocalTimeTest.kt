package com.owntracks.auto.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class LocalTimeTest {

    private lateinit var savedZone: TimeZone

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
    }

    @Test
    fun convertsServerUtcStampToLocalTime() {
        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Melbourne")) // UTC+11 in November
        assertEquals("17:45", MainViewModel.localTime("2026-11-06 06:45:12+00"))
    }

    @Test
    fun crossesMidnightIntoTheNextLocalDay() {
        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Melbourne"))
        assertEquals("00:08", MainViewModel.localTime("2026-11-05 13:08:00+00"))
    }

    @Test
    fun returnsInputUnchangedWhenUnparseable() {
        assertEquals("not a time", MainViewModel.localTime("not a time"))
    }
}
