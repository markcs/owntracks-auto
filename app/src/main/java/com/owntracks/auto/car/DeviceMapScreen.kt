package com.owntracks.auto.car

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
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

            // Non-browsable rows in a PlaceListMapTemplate must carry a distance
            // span (Car App Library rule). Use the distance from the car's last
            // known position when available; otherwise make the row browsable,
            // which is exempt from the rule.
            val address = endpoint.address ?: device.subtitle.ifBlank { "Last known position" }
            val distance = distanceFromHere(endpoint.latitude, endpoint.longitude)

            // Rows allow two lines of secondary text: address, then recency.
            val rowBuilder = Row.Builder()
                .setTitle(device.label)
                .setMetadata(Metadata.Builder().setPlace(place).build())
            if (distance != null) {
                rowBuilder.addText(
                    SpannableString(address).apply {
                        setSpan(DistanceSpan.create(distance), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                    },
                )
            } else {
                // A browsable row must have a click handler; tapping it navigates,
                // the same as the Navigate button.
                rowBuilder.addText(address)
                    .setBrowsable(true)
                    .setOnClickListener { navigateTo(endpoint.latitude, endpoint.longitude) }
            }
            TimeLabels.endpointComment(endpoint).takeIf { it.isNotBlank() }?.let { rowBuilder.addText(it) }
            listBuilder.addItem(rowBuilder.build())
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

    /**
     * Straight-line distance from the car's last known GPS fix to [lat]/[lon],
     * or null when no fix is available or location permission is not granted.
     */
    @SuppressLint("MissingPermission")
    private fun distanceFromHere(lat: Double, lon: Double): Distance? {
        val lm = carContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        // Prefer the GPS fix; with only approximate location granted, fall back to network.
        val here = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        } ?: return null
        val results = FloatArray(1)
        Location.distanceBetween(here.latitude, here.longitude, lat, lon, results)
        return Distance.create(results[0].toDouble(), Distance.UNIT_METERS)
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
