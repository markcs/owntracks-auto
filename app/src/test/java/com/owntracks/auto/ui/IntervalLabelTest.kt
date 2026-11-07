package com.owntracks.auto.ui

import com.owntracks.auto.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalLabelTest {

    @Test
    fun labelsEveryOfferedInterval() {
        val labels = SettingsStore.REFRESH_INTERVALS_SECONDS.map(::intervalLabel)
        assertEquals(
            listOf("30 seconds", "1 minute", "2 minutes", "5 minutes", "10 minutes", "15 minutes"),
            labels,
        )
    }

    @Test
    fun defaultIntervalIsOneOfTheOptions() {
        assertTrue(SettingsStore.DEFAULT_REFRESH_SECONDS in SettingsStore.REFRESH_INTERVALS_SECONDS)
    }
}
