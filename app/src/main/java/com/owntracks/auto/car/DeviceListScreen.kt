package com.owntracks.auto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.owntracks.auto.domain.Device
import com.owntracks.auto.domain.DeviceRepository
import com.owntracks.auto.domain.TimeLabels
import com.owntracks.auto.settings.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The car's top screen: a scrollable list of devices. Selecting one opens a
 * [DeviceMapScreen] with a map marker and a Navigate action.
 */
class DeviceListScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val settings = SettingsStore(carContext)
    private val repo = DeviceRepository(carContext, settings)

    private var devices: List<Device> = emptyList()
    private var loading = true
    private var error: String? = null
    private var autoRefreshJob: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        load()
        // Follow the phone's auto-refresh setting while this screen is on the car display.
        autoRefreshJob = lifecycleScope.launch {
            settings.autoRefreshFlow.collectLatest { auto ->
                if (!auto.enabled) return@collectLatest
                while (true) {
                    delay(auto.intervalSeconds * 1000L)
                    load(silent = true)
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /** A [silent] load keeps the current list on screen and on failure. */
    private fun load(silent: Boolean = false) {
        if (!silent) {
            loading = true
            error = null
            invalidate()
        }
        lifecycleScope.launch {
            runCatching { repo.loadDevices() }
                .onSuccess {
                    devices = it
                    error = null
                }
                .onFailure { if (!silent || devices.isEmpty()) error = it.message ?: "Failed to load" }
            loading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (devices.isEmpty()) {
            listBuilder.setNoItemsMessage(
                when {
                    loading -> "Loading devices…"
                    error != null -> "Error: $error"
                    else -> "No devices reported today."
                },
            )
        } else {
            // The host caps list length; take the first N to stay within limits.
            devices.take(MAX_ROWS).forEach { device ->
                val rowBuilder = Row.Builder()
                    .setTitle(device.label)
                    .setOnClickListener { openDevice(device) }
                // The Car App Library caps secondary text at two lines; use one.
                val endpoint = device.lastEndpoint
                val address = endpoint?.address
                val secondary = when {
                    endpoint == null -> "Position unavailable"
                    !address.isNullOrBlank() -> address
                    else -> TimeLabels.endpointComment(endpoint).ifBlank { "Tap to view & navigate" }
                }
                rowBuilder.addText(secondary)
                listBuilder.addItem(rowBuilder.build())
            }
        }

        return ListTemplate.Builder()
            .setTitle("Devices")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .setActionStrip(
                androidx.car.app.model.ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Refresh")
                            .setOnClickListener { load() }
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun openDevice(device: Device) {
        screenManager.push(DeviceMapScreen(carContext, device))
    }

    private companion object {
        const val MAX_ROWS = 6
    }
}
