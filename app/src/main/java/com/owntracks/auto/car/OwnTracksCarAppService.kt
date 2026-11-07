package com.owntracks.auto.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point for the Android Auto surface. Declared in the manifest with the
 * `androidx.car.app.category.POI` category.
 */
class OwnTracksCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        // Allow all hosts for sideloaded/debug use. For a store release, use
        // HostValidator.Builder(context) with the known allowlist instead.
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = OwnTracksSession()
}
