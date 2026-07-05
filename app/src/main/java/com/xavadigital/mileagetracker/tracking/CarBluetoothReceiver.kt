package com.xavadigital.mileagetracker.tracking

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.xavadigital.mileagetracker.data.AppGraph
import com.xavadigital.mileagetracker.data.Trip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Auto start/stop trips when the phone connects to / disconnects from the car's
 * Bluetooth. ACL_CONNECTED/DISCONNECTED are on the implicit-broadcast exemption
 * list, so this manifest-registered receiver fires even when the app is dead.
 */
class CarBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) {
            return
        }
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val address = device?.address ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = AppGraph.settings
                if (!settings.autoTrackEnabled.first()) return@launch
                val carAddress = settings.carAddress.first() ?: return@launch
                if (!address.equals(carAddress, ignoreCase = true)) return@launch

                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> startTracking(context)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        // Only poke the service if a trip is actually recording;
                        // startService from the background throws otherwise.
                        if (TripRecordingService.state.value != null) {
                            TripRecordingService.stop(context)
                        }
                        TripNotifications.cancelStartFallback(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun startTracking(context: Context) {
        if (TripRecordingService.state.value != null) return
        try {
            TripRecordingService.start(context, Trip.SOURCE_BLUETOOTH)
        } catch (_: Exception) {
            // Android 12+ blocks FGS start from the background without a
            // companion-device association — fall back to a one-tap notification.
            TripNotifications.postStartFallback(context)
        }
    }
}
