package com.owntracks.auto.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.PlaceListMapTemplate
import com.owntracks.auto.domain.Device
import com.owntracks.auto.domain.TimeLabels
import com.owntracks.auto.nav.NavigationLauncher

/**
 * Shows one device as a marker on the host-drawn map, with a Navigate action
 * that hands the destination off to the car's navigation app.
 */
class DeviceMapScreen(carContext: CarContext, private val device: Device) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val endpoint = device.lastEndpoint

        val listBuilder = ItemList.Builder()
        if (endpoint == null) {
            listBuilder.setNoItemsMessage("No known position for ${device.label}.")
        } else {
            val place = Place.Builder(CarLocation.create(endpoint.latitude, endpoint.longitude))
                .setMarker(
                    PlaceMarker.Builder()
                        .setColor(CarColor.BLUE)
                        .build(),
                )
                .build()

            // Rows allow two lines of secondary text: address, then recency.
            val rowBuilder = Row.Builder()
                .setTitle(device.label)
                .addText(endpoint.address ?: device.subtitle.ifBlank { "Last known position" })
                .setMetadata(Metadata.Builder().setPlace(place).build())
            TimeLabels.endpointComment(endpoint).takeIf { it.isNotBlank() }?.let { rowBuilder.addText(it) }
            val row = rowBuilder.build()
            listBuilder.addItem(row)
        }

        val builder = PlaceListMapTemplate.Builder()
            .setTitle(device.label)
            .setHeaderAction(Action.BACK)
            .setItemList(listBuilder.build())

        if (endpoint != null) {
            builder.setActionStrip(
                androidx.car.app.model.ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Navigate")
                            .setOnClickListener { navigateTo(endpoint.latitude, endpoint.longitude) }
                            .build(),
                    )
                    .build(),
            )
        }

        return builder.build()
    }

    /** Hand the destination to the car's active navigation app. */
    private fun navigateTo(lat: Double, lon: Double) {
        val uri = NavigationLauncher.carNavigateUri(lat, lon)
        try {
            carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
        } catch (e: Exception) {
            CarToast.makeText(carContext, "No navigation app available", CarToast.LENGTH_LONG).show()
        }
    }
}
