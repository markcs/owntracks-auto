package com.owntracks.auto.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class OwnTracksSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = DeviceListScreen(carContext)
}
