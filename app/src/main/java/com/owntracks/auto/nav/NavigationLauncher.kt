package com.owntracks.auto.nav

import android.content.Intent
import android.net.Uri
import com.owntracks.auto.settings.NavApp
import java.util.Locale

/**
 * Builds navigation intents to hand a destination off to a turn-by-turn
 * navigation app (Google Maps, Waze, or the system chooser). The same URIs
 * work on the phone and, via `CarContext`, on Android Auto.
 */
object NavigationLauncher {

    /**
     * A `google.navigation:` intent, which Google Maps consumes as
     * turn-by-turn and which Android Auto routes to the active nav app.
     */
    fun googleNavigationIntent(lat: Double, lon: Double): Intent {
        val uri = Uri.parse("google.navigation:q=${coord(lat)},${coord(lon)}")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
    }

    /** A Waze deep link that starts navigation to the point. */
    fun wazeIntent(lat: Double, lon: Double): Intent {
        val uri = Uri.parse("https://waze.com/ul?ll=${coord(lat)},${coord(lon)}&navigate=yes")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.waze")
        }
    }

    /**
     * A generic `geo:` intent with a query label. No package set, so the
     * system shows the chooser / default maps app. Also the correct form to
     * hand to `CarContext.startCarApp` with `ACTION_NAVIGATE` on Auto.
     */
    fun geoIntent(lat: Double, lon: Double, label: String? = null): Intent {
        val point = "${coord(lat)},${coord(lon)}"
        val q = if (label.isNullOrBlank()) point else "$point(${Uri.encode(label)})"
        val uri = Uri.parse("geo:$point?q=$q")
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /** Pick the right intent for the user's preferred nav app. */
    fun intentFor(app: NavApp, lat: Double, lon: Double, label: String? = null): Intent =
        when (app) {
            NavApp.GOOGLE_MAPS -> googleNavigationIntent(lat, lon)
            NavApp.WAZE -> wazeIntent(lat, lon)
            NavApp.ASK -> geoIntent(lat, lon, label)
        }

    /** The `geo:` URI used for the Android Auto navigation hand-off. */
    fun carNavigateUri(lat: Double, lon: Double): Uri =
        Uri.parse("geo:${coord(lat)},${coord(lon)}?q=${coord(lat)},${coord(lon)}")

    private fun coord(v: Double): String = String.format(Locale.US, "%.6f", v)
}
