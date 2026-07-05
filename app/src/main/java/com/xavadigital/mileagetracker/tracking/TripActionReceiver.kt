package com.xavadigital.mileagetracker.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xavadigital.mileagetracker.data.AppGraph
import com.xavadigital.mileagetracker.data.Trip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the one-tap actions on the "Trip finished" notification. */
class TripActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        val action = intent.action
        if (tripId <= 0 || action == null) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppGraph.tripDao
                val trip = dao.getById(tripId)
                if (trip != null) {
                    when (action) {
                        ACTION_MARK_PERSONAL -> dao.update(
                            trip.copy(type = Trip.TYPE_PERSONAL, business = null, purpose = null)
                        )
                        ACTION_DISCARD -> dao.delete(trip)
                    }
                }
                TripNotifications.cancelClassifyPrompt(context, tripId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_PERSONAL = "com.xavadigital.mileagetracker.action.MARK_PERSONAL"
        const val ACTION_DISCARD = "com.xavadigital.mileagetracker.action.DISCARD_TRIP"
        const val EXTRA_TRIP_ID = "trip_id"
    }
}
