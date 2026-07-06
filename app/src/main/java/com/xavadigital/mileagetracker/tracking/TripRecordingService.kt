package com.xavadigital.mileagetracker.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.xavadigital.mileagetracker.MainActivity
import com.xavadigital.mileagetracker.data.AppGraph
import com.xavadigital.mileagetracker.data.Trip
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RecordingState(
    val startTime: Long,
    val distanceMeters: Double,
)

class TripRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var fusedClient: FusedLocationProviderClient

    private val points = mutableListOf<Location>()
    private var distanceMeters = 0.0
    private var startTime = 0L
    private var recording = false
    private var finishing = false
    private var source = Trip.SOURCE_MANUAL

    // Stationary watchdog: prompt when the trip stops moving but keeps recording.
    private var lastMovedTime = 0L
    private var lastMovedDistance = 0.0
    private var stationaryPrompted = false
    private var monitorJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onLocation(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(
                intent.getStringExtra(EXTRA_SOURCE) ?: Trip.SOURCE_MANUAL
            )
            ACTION_STOP -> stopRecording()
            ACTION_STILL_DRIVING -> {
                lastMovedTime = System.currentTimeMillis()
                lastMovedDistance = distanceMeters
                stationaryPrompted = false
                TripNotifications.cancelStationaryPrompt(this)
            }
        }
        return START_STICKY
    }

    private fun startRecording(tripSource: String) {
        if (recording || finishing) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Waiting for GPS…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        startTime = System.currentTimeMillis()
        distanceMeters = 0.0
        points.clear()
        recording = true
        source = tripSource
        _state.value = RecordingState(startTime, 0.0)
        TripNotifications.cancelStartFallback(this)
        playTone(start = true)

        lastMovedTime = startTime
        lastMovedDistance = 0.0
        stationaryPrompted = false
        monitorJob = scope.launch {
            while (true) {
                delay(60_000)
                checkStationary()
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun onLocation(location: Location) {
        if (!recording) return
        // Ignore low-quality fixes; they cause phantom kilometers.
        if (location.hasAccuracy() && location.accuracy > 50f) return

        val last = points.lastOrNull()
        if (last != null) {
            val meters = location.distanceTo(last).toDouble()
            val seconds = (location.time - last.time) / 1000.0
            // Ignore GPS jumps implying > ~200 km/h.
            if (seconds > 0 && meters / seconds > 55) return
            distanceMeters += meters
        }
        points.add(location)
        _state.value = RecordingState(startTime, distanceMeters)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(String.format(Locale.US, "%.1f km recorded", distanceMeters / 1000.0))
        )
    }

    private fun stopRecording() {
        if (finishing) return
        if (!recording) {
            stopSelf()
            return
        }
        recording = false
        finishing = true
        monitorJob?.cancel()
        TripNotifications.cancelStationaryPrompt(this)
        fusedClient.removeLocationUpdates(locationCallback)
        _state.value = null
        playTone(start = false)

        scope.launch {
            saveTrip()
            stopSelf()
        }
    }

    private suspend fun saveTrip() {
        val endTime = System.currentTimeMillis()
        // Fewer than 2 fixes or under 50 m means we never really went anywhere.
        if (points.size < 2 || distanceMeters < 50.0) {
            val reason = if (points.size < 2) {
                "GPS never got a fix — this usually means the phone was indoors. " +
                    "Trips need at least 50 m of recorded movement to be saved."
            } else {
                String.format(
                    Locale.US,
                    "Only %.0f m of movement was recorded — trips under 50 m are discarded.",
                    distanceMeters
                )
            }
            // Toast for when the app is open in hand; notification for auto trips.
            android.widget.Toast.makeText(this, "Trip not saved: $reason", android.widget.Toast.LENGTH_LONG).show()
            TripNotifications.postTripDiscarded(this, reason)
            return
        }

        val first = points.first()
        val last = points.last()
        val driver = try {
            AppGraph.settings.driverName.first()
        } catch (_: Exception) {
            ""
        }
        val startAddress = Geocoding.addressFor(this, first.latitude, first.longitude)
        val endAddress = Geocoding.addressFor(this, last.latitude, last.longitude)

        val trip = Trip(
            startTime = startTime,
            endTime = endTime,
            distanceMeters = distanceMeters,
            startLat = first.latitude,
            startLng = first.longitude,
            endLat = last.latitude,
            endLng = last.longitude,
            startAddress = startAddress,
            endAddress = endAddress,
            driver = driver,
            source = source,
            polyline = points.joinToString(";") {
                String.format(Locale.US, "%.5f,%.5f", it.latitude, it.longitude)
            },
            entryId = java.util.UUID.randomUUID().toString(),
        )
        val id = AppGraph.tripDao.insert(trip)
        TripNotifications.postClassifyPrompt(this, trip.copy(id = id))
    }

    private fun checkStationary() {
        if (!recording) return
        if (distanceMeters - lastMovedDistance >= 100.0) {
            lastMovedDistance = distanceMeters
            lastMovedTime = System.currentTimeMillis()
            if (stationaryPrompted) {
                stationaryPrompted = false
                TripNotifications.cancelStationaryPrompt(this)
            }
            return
        }
        if (!stationaryPrompted &&
            System.currentTimeMillis() - lastMovedTime >= STATIONARY_PROMPT_MS
        ) {
            stationaryPrompted = true
            TripNotifications.postStationaryPrompt(this)
        }
    }

    /** Short beep on start, double beep on end — audible confirmation of recording. */
    private fun playTone(start: Boolean) {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            generator.startTone(
                if (start) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP2,
                250
            )
            scope.launch {
                delay(500)
                generator.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Trip recording",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TripRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Recording trip")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "End trip", stop)
            .build()
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        _state.value = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.xavadigital.mileagetracker.action.START_TRIP"
        const val ACTION_STOP = "com.xavadigital.mileagetracker.action.STOP_TRIP"
        const val ACTION_STILL_DRIVING = "com.xavadigital.mileagetracker.action.STILL_DRIVING"
        const val EXTRA_SOURCE = "source"
        private const val CHANNEL_ID = "trip_recording"
        private const val NOTIFICATION_ID = 1
        private const val STATIONARY_PROMPT_MS = 10 * 60_000L

        private val _state = MutableStateFlow<RecordingState?>(null)

        /** Non-null while a trip is being recorded; observed by the UI. */
        val state: StateFlow<RecordingState?> = _state.asStateFlow()

        fun start(context: Context, source: String = Trip.SOURCE_MANUAL) {
            val intent = Intent(context, TripRecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SOURCE, source)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TripRecordingService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
