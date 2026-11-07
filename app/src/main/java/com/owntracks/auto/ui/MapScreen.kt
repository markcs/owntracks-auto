package com.owntracks.auto.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owntracks.auto.domain.Device
import com.owntracks.auto.domain.DeviceRepository
import com.owntracks.auto.domain.Endpoint
import com.owntracks.auto.domain.TimeLabels
import com.owntracks.auto.nav.NavigationLauncher
import com.owntracks.auto.settings.AutoRefresh
import com.owntracks.auto.settings.NavApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenSettings: () -> Unit,
    vm: MainViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val navApp by vm.navApp.collectAsState()
    val autoRefresh by vm.autoRefresh.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Periodic refresh, only while this screen is visible. Restarts when the setting changes.
    LaunchedEffect(autoRefresh, lifecycleOwner) {
        if (!autoRefresh.enabled) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val intervalMs = autoRefresh.intervalSeconds * 1000L
            var lastAttempt = 0L
            while (true) {
                // Wait out the rest of the interval since the last update or attempt, so
                // returning to the app after a while refreshes straight away, while a
                // failing or skipped refresh still waits a full interval before retrying.
                val lastSuccess = vm.state.value.lastUpdated?.time ?: 0L
                val since = System.currentTimeMillis() - maxOf(lastSuccess, lastAttempt)
                delay((intervalMs - since).coerceIn(0L, intervalMs))
                lastAttempt = System.currentTimeMillis()
                vm.autoRefresh()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OwnTracks Auto") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val availableHeight = maxHeight
            Column(Modifier.fillMaxSize()) {
                // The map takes whatever height the panel leaves free.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    MapLibreMapView(
                        devices = state.devices,
                        selectedDeviceId = state.selectedDeviceId,
                        droppedPin = state.droppedPin,
                        track = state.track,
                        selectedPointIndex = state.selectedPointIndex,
                        onMarkerClick = { vm.selectDevice(it) },
                        onLongPress = { lat, lon -> vm.dropPin(lat, lon) },
                        onTrackPointClick = { vm.selectPoint(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                ResizablePanel(
                    availableHeight = availableHeight,
                    header = {
                        DateBar(
                            dateLabel = DeviceRepository.isoDate(state.date),
                            onPrev = { vm.shiftDate(-1) },
                            onNext = { vm.shiftDate(1) },
                            onToday = { vm.setToday() },
                        )
                        // Status and navigate bars stay visible even when the list is collapsed.
                        RefreshStatus(state, autoRefresh)
                        PanelStatus(state, vm, navApp, context)
                    },
                    body = { bodyModifier ->
                        DeviceList(
                            devices = state.devices,
                            loading = state.loading,
                            error = state.error,
                            selectedId = state.selectedDeviceId,
                            onSelect = { vm.selectDevice(it.deviceId) },
                            onNavigate = { device ->
                                val ep = device.lastEndpoint
                                if (ep == null) {
                                    Toast.makeText(context, "No known position for ${device.label}", Toast.LENGTH_SHORT).show()
                                } else {
                                    launchNav(context, navApp, ep.latitude, ep.longitude, device.label)
                                }
                            },
                            modifier = bodyModifier,
                        )
                    },
                )
            }
        }
    }
}

/**
 * One small line describing auto-refresh, shown only when it is on:
 * "Auto-refresh every 1 minute · updated 18:14", "… paused for earlier days",
 * or "Couldn't refresh · showing data from 18:10".
 */
@Composable
private fun RefreshStatus(state: MainUiState, autoRefresh: AutoRefresh) {
    if (!autoRefresh.enabled) return
    val isToday = DeviceRepository.isoDate(state.date) == DeviceRepository.isoDate(java.util.Date())
    val updated = state.lastUpdated?.let {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(it)
    }
    val text = when {
        !isToday -> "Auto-refresh paused for earlier days"
        state.lastAutoRefreshFailed && updated != null -> "Couldn't refresh · showing data from $updated"
        updated != null -> "Auto-refresh every ${intervalLabel(autoRefresh.intervalSeconds)} · updated $updated"
        else -> "Auto-refresh every ${intervalLabel(autoRefresh.intervalSeconds)}"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (state.lastAutoRefreshFailed && isToday) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

/** The tapped-point, track-status and dropped-pin bars shown under the date bar. */
@Composable
private fun PanelStatus(state: MainUiState, vm: MainViewModel, navApp: NavApp, context: Context) {
    val selectedDevice = vm.deviceById(state.selectedDeviceId)
    val point = state.selectedPoint
    when {
        point != null -> {
            val time = MainViewModel.localTime(point.time)
            val who = selectedDevice?.label.orEmpty()
            PointBar(
                title = listOf(who, time).filter { it.isNotBlank() }.joinToString(" at "),
                address = point.address,
                onNavigate = {
                    launchNav(context, navApp, point.latitude, point.longitude, "$who $time".trim())
                },
                onClear = { vm.clearPoint() },
            )
        }
        selectedDevice != null && state.trackLoading ->
            Hint("Loading ${selectedDevice.label}'s track…")
        selectedDevice != null && state.track.isNotEmpty() ->
            Hint("Tap a point on the track to navigate to that position.")
        selectedDevice != null ->
            Hint("No track for ${selectedDevice.label} on this day.")
    }

    state.droppedPin?.let { pin ->
        NavigateBar(
            label = "Navigate to dropped pin",
            onNavigate = { launchNav(context, navApp, pin.latitude, pin.longitude, "Dropped pin") },
            onClear = { vm.clearPin() },
        )
    }
}

@Composable
private fun DateBar(dateLabel: String, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
        }
        Text(dateLabel, fontWeight = FontWeight.Medium)
        Row {
            TextButton(onClick = onToday) { Text("Today") }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
            }
        }
    }
}

@Composable
private fun PointBar(title: String, address: String?, onNavigate: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(address ?: "Looking up address…")
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onNavigate, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Navigation, contentDescription = null)
                Text("  Navigate here")
            }
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun NavigateBar(label: String, onNavigate: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onNavigate, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Navigation, contentDescription = null)
            Text("  $label")
        }
        OutlinedButton(onClick = onClear) { Text("Clear") }
    }
}

@Composable
private fun DeviceList(
    devices: List<Device>,
    loading: Boolean,
    error: String?,
    selectedId: Int?,
    onSelect: (Device) -> Unit,
    onNavigate: (Device) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 8.dp)) {
        Text(
            "Devices",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        when {
            error != null -> Text("Error: $error")
            loading && devices.isEmpty() -> Text("Loading…")
            devices.isEmpty() -> Text("No devices reported for this day.")
            else -> LazyColumn {
                items(devices, key = { it.deviceId }) { device ->
                    DeviceRow(
                        device = device,
                        selected = device.deviceId == selectedId,
                        onSelect = { onSelect(device) },
                        onNavigate = { onNavigate(device) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: Device, selected: Boolean, onSelect: () -> Unit, onNavigate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (device.subtitle.isNotBlank()) Text(device.subtitle)
            val endpoint = device.lastEndpoint
            if (endpoint == null) {
                // Only reachable if the server returned no points at all for a listed device.
                Text("Position unavailable", style = MaterialTheme.typography.bodySmall)
            } else {
                endpoint.address?.takeIf { it.isNotBlank() }?.let { Text(it) }
                TimeLabels.endpointComment(endpoint).takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Button(onClick = onNavigate, enabled = device.lastEndpoint != null) {
            Icon(Icons.Filled.Navigation, contentDescription = null)
            Text("  Go")
        }
    }
}

/** Resolve the nav intent and launch it, with a graceful fallback chooser. */
private fun launchNav(context: Context, app: NavApp, lat: Double, lon: Double, label: String) {
    val intent = NavigationLauncher.intentFor(app, lat, lon, label)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Preferred app missing: fall back to a generic geo chooser.
        val fallback = NavigationLauncher.geoIntent(lat, lon, label)
        try {
            context.startActivity(Intent.createChooser(fallback, "Navigate with"))
        } catch (e2: Exception) {
            Toast.makeText(context, "No navigation app available", Toast.LENGTH_SHORT).show()
        }
    }
}
