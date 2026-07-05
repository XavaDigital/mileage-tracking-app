package com.xavadigital.mileagetracker.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xavadigital.mileagetracker.MainActivity
import com.xavadigital.mileagetracker.data.Trip
import java.util.Locale

object TripNotifications {
    private const val REVIEW_CHANNEL_ID = "trip_review"
    private const val START_FALLBACK_NOTIFICATION_ID = 2

    private fun ensureReviewChannel(context: Context) {
        val channel = NotificationChannel(
            REVIEW_CHANNEL_ID,
            "Trip classification",
            NotificationManager.IMPORTANCE_HIGH
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun notificationIdFor(tripId: Long): Int = (1000 + tripId).toInt()

    /** "Trip finished" prompt with one-tap Work…/Personal/Passenger actions. */
    fun postClassifyPrompt(context: Context, trip: Trip) {
        ensureReviewChannel(context)

        val km = String.format(Locale.US, "%.1f km", trip.distanceKm)
        val route = listOfNotNull(trip.startAddress, trip.endAddress).joinToString(" → ")

        val workIntent = PendingIntent.getActivity(
            context,
            trip.id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CLASSIFY_TRIP_ID, trip.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val personalIntent = actionIntent(context, TripActionReceiver.ACTION_MARK_PERSONAL, trip.id)
        val passengerIntent = actionIntent(context, TripActionReceiver.ACTION_DISCARD, trip.id)

        val notification = NotificationCompat.Builder(context, REVIEW_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Trip finished — $km")
            .setContentText(route.ifBlank { "Tap to classify" })
            .setContentIntent(workIntent)
            .setAutoCancel(true)
            .addAction(0, "Work…", workIntent)
            .addAction(0, "Personal", personalIntent)
            .addAction(0, "Passenger", passengerIntent)
            .build()

        notifySafe(context, notificationIdFor(trip.id), notification)
    }

    fun cancelClassifyPrompt(context: Context, tripId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(tripId))
    }

    /**
     * Fallback when Android refuses to auto-start the recording service from the
     * background (no companion-device association yet): one tap starts the trip.
     */
    fun postStartFallback(context: Context) {
        ensureReviewChannel(context)
        val startIntent = PendingIntent.getForegroundService(
            context,
            3,
            Intent(context, TripRecordingService::class.java)
                .setAction(TripRecordingService.ACTION_START)
                .putExtra(TripRecordingService.EXTRA_SOURCE, Trip.SOURCE_BLUETOOTH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, REVIEW_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Driving?")
            .setContentText("Tap to record this trip")
            .setContentIntent(startIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .build()
        notifySafe(context, START_FALLBACK_NOTIFICATION_ID, notification)
    }

    fun cancelStartFallback(context: Context) {
        NotificationManagerCompat.from(context).cancel(START_FALLBACK_NOTIFICATION_ID)
    }

    private fun actionIntent(context: Context, action: String, tripId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            tripId.toInt(),
            Intent(context, TripActionReceiver::class.java)
                .setAction(action)
                .putExtra(TripActionReceiver.EXTRA_TRIP_ID, tripId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun notifySafe(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
