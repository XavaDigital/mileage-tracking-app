package com.xavadigital.mileagetracker.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.xavadigital.mileagetracker.data.AppGraph
import com.xavadigital.mileagetracker.data.Trip
import com.xavadigital.mileagetracker.export.CsvExporter
import com.xavadigital.mileagetracker.sync.SyncScheduler
import com.xavadigital.mileagetracker.tracking.RecordingState
import com.xavadigital.mileagetracker.tracking.TripNotifications
import com.xavadigital.mileagetracker.tracking.TripRecordingService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val tripDateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a")

private fun formatTripDate(millis: Long): String =
    tripDateFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Geocoded address when available, raw coordinates otherwise — never blank. */
private fun locationLabel(address: String?, lat: Double?, lng: Double?): String? =
    address ?: if (lat != null && lng != null) {
        String.format(Locale.US, "%.4f, %.4f", lat, lng)
    } else {
        null
    }

private fun openInMaps(context: Context, lat: Double, lng: Double, label: String) {
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun missingStartPermissions(context: Context): List<String> {
    val missing = mutableListOf<String>()
    if (!hasLocationPermission(context)) {
        missing += Manifest.permission.ACCESS_FINE_LOCATION
        missing += Manifest.permission.ACCESS_COARSE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        missing += Manifest.permission.POST_NOTIFICATIONS
    }
    return missing
}

/**
 * The chronologically previous trip, if this trip looks like a continuation of it —
 * a petrol stop, school pickup, etc.: started within 15 min and 300 m of where the
 * previous one ended.
 */
private fun findMergeCandidate(trip: Trip, trips: List<Trip>): Trip? {
    val previous = trips
        .filter { it.id != trip.id && it.startTime < trip.startTime }
        .maxByOrNull { it.startTime } ?: return null
    val gapMillis = trip.startTime - previous.endTime
    if (gapMillis !in 0..15 * 60_000L) return null
    val startLat = trip.startLat ?: return null
    val startLng = trip.startLng ?: return null
    val endLat = previous.endLat ?: return null
    val endLng = previous.endLng ?: return null
    val distance = FloatArray(1)
    android.location.Location.distanceBetween(endLat, endLng, startLat, startLng, distance)
    return if (distance[0] <= 300f) previous else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    classifyRequest: MutableStateFlow<Long?>,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val trips by AppGraph.tripDao.observeAll().collectAsState(initial = emptyList())
    val businesses by AppGraph.tripDao.observeBusinesses().collectAsState(initial = emptyList())
    val purposes by AppGraph.tripDao.observePurposes().collectAsState(initial = emptyList())
    var filter by rememberSaveable { mutableStateOf("ALL") }
    val recording by TripRecordingService.state.collectAsState()
    val driverName by AppGraph.settings.driverName.collectAsState(initial = null)

    var classifying by remember { mutableStateOf<Trip?>(null) }

    // A "Work…" tap on the trip-finished notification lands here with a trip id.
    val requestedTripId by classifyRequest.collectAsState()
    LaunchedEffect(requestedTripId, trips) {
        val id = requestedTripId ?: return@LaunchedEffect
        trips.find { it.id == id }?.let {
            classifying = it
            classifyRequest.value = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Start as long as location is in place — notifications are nice-to-have.
        if (hasLocationPermission(context)) {
            TripRecordingService.start(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mileage Log") },
                actions = {
                    IconButton(onClick = { scope.launch { CsvExporter.shareCsv(context) } }) {
                        Icon(Icons.Default.Share, contentDescription = "Export CSV")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == "ALL",
                    onClick = { filter = "ALL" },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filter == "UNCLASSIFIED",
                    onClick = { filter = "UNCLASSIFIED" },
                    label = { Text("To classify") }
                )
                FilterChip(
                    selected = filter == "WORK",
                    onClick = { filter = "WORK" },
                    label = { Text("Work") }
                )
                FilterChip(
                    selected = filter == "PERSONAL",
                    onClick = { filter = "PERSONAL" },
                    label = { Text("Personal") }
                )
                businesses.forEach { name ->
                    FilterChip(
                        selected = filter == "BIZ:$name",
                        onClick = { filter = "BIZ:$name" },
                        label = { Text(name) }
                    )
                }
            }

            val filteredTrips = when {
                filter == "UNCLASSIFIED" -> trips.filter { it.type == Trip.TYPE_UNCLASSIFIED }
                filter == "WORK" -> trips.filter { it.type == Trip.TYPE_WORK }
                filter == "PERSONAL" -> trips.filter { it.type == Trip.TYPE_PERSONAL }
                filter.startsWith("BIZ:") ->
                    trips.filter { it.business == filter.removePrefix("BIZ:") }
                else -> trips
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val unclassified = trips.count { it.type == Trip.TYPE_UNCLASSIFIED }
                if (unclassified > 0) {
                    item {
                        Text(
                            "$unclassified trip${if (unclassified == 1) "" else "s"} to classify — tap a trip below",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                items(filteredTrips, key = { it.id }) { trip ->
                    TripRow(trip = trip, onClick = { classifying = trip })
                }

                if (trips.isEmpty() && recording == null) {
                    item {
                        Text(
                            "No trips yet. Tap Start Trip when you set off.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                }
            }

            // Pinned at the bottom, within one-handed thumb reach.
            RecordingCard(
                recording = recording,
                onStart = {
                    val missing = missingStartPermissions(context)
                    if (missing.isEmpty()) {
                        TripRecordingService.start(context)
                    } else {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                },
                onStop = { TripRecordingService.stop(context) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }
    }

    classifying?.let { trip ->
        val mergeCandidate = remember(trip, trips) { findMergeCandidate(trip, trips) }
        ClassifyDialog(
            trip = trip,
            businesses = businesses,
            purposes = purposes,
            onDismiss = { classifying = null },
            onSave = { updated ->
                scope.launch {
                    AppGraph.tripDao.update(updated.copy(syncedAt = null))
                    TripNotifications.cancelClassifyPrompt(context, trip.id)
                    SyncScheduler.syncNow(context)
                }
                classifying = null
            },
            onDelete = {
                scope.launch {
                    AppGraph.tripDao.delete(trip)
                    TripNotifications.cancelClassifyPrompt(context, trip.id)
                }
                classifying = null
            },
            onMerge = mergeCandidate?.let { previous ->
                {
                    scope.launch {
                        AppGraph.tripDao.update(
                            previous.copy(
                                endTime = trip.endTime,
                                distanceMeters = previous.distanceMeters + trip.distanceMeters,
                                endLat = trip.endLat,
                                endLng = trip.endLng,
                                endAddress = trip.endAddress,
                                polyline = listOfNotNull(previous.polyline, trip.polyline)
                                    .joinToString(";").ifBlank { null },
                                syncedAt = null,
                            )
                        )
                        AppGraph.tripDao.delete(trip)
                        TripNotifications.cancelClassifyPrompt(context, trip.id)
                        TripNotifications.cancelClassifyPrompt(context, previous.id)
                        SyncScheduler.syncNow(context)
                    }
                    classifying = null
                }
            }
        )
    }

    if (driverName == "") {
        DriverNameDialog(onSave = { name ->
            scope.launch { AppGraph.settings.setDriverName(name) }
        })
    }
}

@Composable
private fun RecordingCard(
    recording: RecordingState?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (recording == null) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Start Trip", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(recording.startTime) {
                    while (true) {
                        now = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                val elapsed = (now - recording.startTime) / 1000
                Text(
                    String.format(Locale.US, "%.1f km", recording.distanceMeters / 1000.0),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    String.format(
                        Locale.US, "%d:%02d:%02d",
                        elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("End Trip", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun TripRow(trip: Trip, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatTripDate(trip.startTime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    String.format(Locale.US, "%.1f km", trip.distanceKm),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            val route = listOfNotNull(
                locationLabel(trip.startAddress, trip.startLat, trip.startLng),
                locationLabel(trip.endAddress, trip.endLat, trip.endLng),
            )
            if (route.isNotEmpty()) {
                Text(
                    route.joinToString(" → "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            val (label, color) = when (trip.type) {
                Trip.TYPE_WORK ->
                    (trip.business?.takeIf { it.isNotBlank() }?.let { "Work · $it" } ?: "Work") to
                        MaterialTheme.colorScheme.primary
                Trip.TYPE_PERSONAL -> "Personal" to MaterialTheme.colorScheme.onSurfaceVariant
                else -> "Tap to classify" to MaterialTheme.colorScheme.tertiary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
                if (!trip.purpose.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        trip.purpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassifyDialog(
    trip: Trip,
    businesses: List<String>,
    purposes: List<String>,
    onDismiss: () -> Unit,
    onSave: (Trip) -> Unit,
    onDelete: () -> Unit,
    onMerge: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var isWork by remember {
        mutableStateOf(if (trip.type == Trip.TYPE_UNCLASSIFIED) true else trip.type == Trip.TYPE_WORK)
    }
    var business by remember { mutableStateOf(trip.business.orEmpty()) }
    var purpose by remember { mutableStateOf(trip.purpose.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(String.format(Locale.US, "%.1f km · %s", trip.distanceKm, formatTripDate(trip.startTime)))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val route = listOfNotNull(
                    locationLabel(trip.startAddress, trip.startLat, trip.startLng),
                    locationLabel(trip.endAddress, trip.endLat, trip.endLng),
                )
                if (route.isNotEmpty()) {
                    Text(
                        route.joinToString(" → "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trip.startLat != null && trip.startLng != null) {
                        TextButton(onClick = {
                            openInMaps(context, trip.startLat, trip.startLng, "Trip start")
                        }) { Text("Start on map") }
                    }
                    if (trip.endLat != null && trip.endLng != null) {
                        TextButton(onClick = {
                            openInMaps(context, trip.endLat, trip.endLng, "Trip end")
                        }) { Text("End on map") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isWork,
                        onClick = { isWork = true },
                        label = { Text("Work") }
                    )
                    FilterChip(
                        selected = !isWork,
                        onClick = { isWork = false },
                        label = { Text("Personal") }
                    )
                }
                if (isWork) {
                    if (businesses.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            businesses.forEach { name ->
                                SuggestionChip(
                                    onClick = { business = name },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = business,
                        onValueChange = { business = it },
                        label = { Text("Business / category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (purposes.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        purposes.take(8).forEach { text ->
                            SuggestionChip(
                                onClick = { purpose = text },
                                label = { Text(text) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Description — e.g. “visit rental property”") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (onMerge != null) {
                    TextButton(onClick = onMerge) {
                        Text("Merge into previous trip (short stop)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    trip.copy(
                        type = if (isWork) Trip.TYPE_WORK else Trip.TYPE_PERSONAL,
                        business = if (isWork) business.trim().ifBlank { null } else null,
                        purpose = purpose.trim().ifBlank { null },
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun DriverNameDialog(onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Who drives this phone?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Trips recorded on this phone will be logged under this name.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        }
    )
}
