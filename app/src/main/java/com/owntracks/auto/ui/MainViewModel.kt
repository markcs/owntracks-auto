package com.owntracks.auto.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owntracks.auto.domain.Device
import com.owntracks.auto.domain.DeviceRepository
import com.owntracks.auto.domain.TimeLabels
import com.owntracks.auto.domain.TrackPoint
import com.owntracks.auto.settings.AutoRefresh
import com.owntracks.auto.settings.NavApp
import com.owntracks.auto.settings.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/** A point the user dropped on the map by long-pressing. */
data class DroppedPin(val latitude: Double, val longitude: Double)

data class MainUiState(
    val devices: List<Device> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val selectedDeviceId: Int? = null,
    val droppedPin: DroppedPin? = null,
    val date: Date = Date(),
    /** Track of the selected device for [date], oldest first. */
    val track: List<TrackPoint> = emptyList(),
    val trackLoading: Boolean = false,
    /** Index into [track] of the position the user tapped, if any. */
    val selectedPointIndex: Int? = null,
    /** When device data last loaded successfully. */
    val lastUpdated: Date? = null,
    /** True if the most recent automatic refresh failed (data shown is from [lastUpdated]). */
    val lastAutoRefreshFailed: Boolean = false,
) {
    val selectedPoint: TrackPoint?
        get() = selectedPointIndex?.let { track.getOrNull(it) }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val repo = DeviceRepository(app, settings)

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    val navApp: StateFlow<NavApp> = settings.navAppFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, NavApp.ASK)

    private var trackJob: Job? = null
    private var refreshJob: Job? = null

    /** Auto-refresh settings, for the screen to start and stop its timer. */
    val autoRefresh: StateFlow<AutoRefresh> = settings.autoRefreshFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AutoRefresh(enabled = false, intervalSeconds = SettingsStore.DEFAULT_REFRESH_SECONDS),
        )

    init {
        refresh()
    }

    /** Manual refresh: shows loading/error states and reloads the selected track from scratch. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.loadDevices(_state.value.date) }
                .onSuccess { devices ->
                    _state.update {
                        it.copy(devices = devices, loading = false, lastUpdated = Date(), lastAutoRefreshFailed = false)
                    }
                    // Reload the selected device's track for the (possibly new) day.
                    loadTrack(_state.value.selectedDeviceId)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "Failed to load devices")
                    }
                }
        }
    }

    /**
     * Background refresh for the auto-refresh timer. Only today's data changes,
     * so other days are skipped. Keeps the selected device, its track and any
     * tapped point; on failure, keeps the data already shown.
     */
    fun autoRefresh() {
        if (refreshJob?.isActive == true || trackJob?.isActive == true) return
        val date = _state.value.date
        if (DeviceRepository.isoDate(date) != DeviceRepository.isoDate(Date())) return
        refreshJob = viewModelScope.launch {
            val devices = runCatching { repo.loadDevices(date) }.getOrElse {
                _state.update { it.copy(lastAutoRefreshFailed = true) }
                return@launch
            }
            if (_state.value.date != date) return@launch // user changed day meanwhile
            _state.update { it.copy(devices = devices, lastUpdated = Date(), lastAutoRefreshFailed = false) }

            val device = deviceById(_state.value.selectedDeviceId) ?: return@launch
            val fresh = runCatching { repo.loadTrack(device, date) }.getOrNull() ?: return@launch
            _state.update { s ->
                if (s.date != date || s.selectedDeviceId != device.deviceId) return@update s
                // Points are returned in recording order, so indexes are stable as the track
                // grows. Keep addresses already looked up on the phone for unchanged points.
                val merged = fresh.mapIndexed { i, p ->
                    val old = s.track.getOrNull(i)
                    if (p.address == null && old != null && old.time == p.time) p.copy(address = old.address) else p
                }
                val keepIndex = s.selectedPointIndex?.takeIf { i ->
                    merged.getOrNull(i)?.time == s.track.getOrNull(i)?.time
                }
                s.copy(track = merged, selectedPointIndex = keepIndex)
            }
        }
    }

    /** Select a device (or clear the selection with null) and show its track. */
    fun selectDevice(deviceId: Int?) {
        _state.update { it.copy(selectedDeviceId = deviceId) }
        loadTrack(deviceId)
    }

    private fun loadTrack(deviceId: Int?) {
        trackJob?.cancel()
        _state.update { it.copy(track = emptyList(), selectedPointIndex = null, trackLoading = false) }
        val device = deviceById(deviceId) ?: return
        _state.update { it.copy(trackLoading = true) }
        trackJob = viewModelScope.launch {
            val track = runCatching { repo.loadTrack(device, _state.value.date) }.getOrDefault(emptyList())
            _state.update { it.copy(track = track, trackLoading = false) }
        }
    }

    /** The user tapped track point [index]; resolve its address if the server had none. */
    fun selectPoint(index: Int) {
        val point = _state.value.track.getOrNull(index) ?: return
        _state.update { it.copy(selectedPointIndex = index) }
        if (point.address != null) return
        viewModelScope.launch {
            val address = repo.resolveAddress(point.latitude, point.longitude) ?: return@launch
            _state.update { s ->
                if (s.track.getOrNull(index) !== point) return@update s // track changed meanwhile
                val updated = s.track.toMutableList().also { it[index] = point.copy(address = address) }
                s.copy(track = updated)
            }
        }
    }

    fun clearPoint() {
        _state.update { it.copy(selectedPointIndex = null) }
    }

    fun dropPin(lat: Double, lon: Double) {
        _state.update { it.copy(droppedPin = DroppedPin(lat, lon)) }
    }

    fun clearPin() {
        _state.update { it.copy(droppedPin = null) }
    }

    fun shiftDate(days: Int) {
        val cal = Calendar.getInstance().apply {
            time = _state.value.date
            add(Calendar.DAY_OF_MONTH, days)
        }
        _state.update { it.copy(date = cal.time) }
        refresh()
    }

    fun setToday() {
        _state.update { it.copy(date = Date()) }
        refresh()
    }

    fun deviceById(id: Int?): Device? = _state.value.devices.find { it.deviceId == id }

    companion object {
        /** Format a server UTC stamp ("YYYY-MM-DD HH:MM:SS+00") as local "HH:mm". */
        fun localTime(utcStamp: String): String = TimeLabels.localTime(utcStamp)
    }
}
