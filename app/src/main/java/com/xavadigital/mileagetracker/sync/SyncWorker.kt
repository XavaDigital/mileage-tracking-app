package com.xavadigital.mileagetracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xavadigital.mileagetracker.data.AppGraph
import com.xavadigital.mileagetracker.data.Trip
import com.xavadigital.mileagetracker.tracking.TripNotifications
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Pushes classified, unsynced trips to the shared Google Sheet (append, or
 * in-place row update when the trip was edited after an earlier sync), then
 * checks the sheet for the other driver's overlapping trips to suggest
 * "were you a passenger?" on local unclassified trips.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = AppGraph.settings
        val spreadsheetId = settings.spreadsheetId.first()
            ?: return@withContext Result.success() // sync not configured — nothing to do

        val token = GoogleAuth.getAccessToken(applicationContext)
        if (token == null) {
            settings.setLastSync(time = null, error = "Google authorization needed — open Settings")
            return@withContext Result.failure()
        }

        try {
            val api = SheetsApi(token)
            api.ensureHeader(spreadsheetId)
            val entries = api.readEntries(spreadsheetId)

            val dao = AppGraph.tripDao
            for (trip in dao.getPendingSync()) {
                val withId = if (trip.entryId != null) {
                    trip
                } else {
                    trip.copy(entryId = UUID.randomUUID().toString())
                }
                val row = withId.toSheetRow()
                val existingRow = entries.idToRow[withId.entryId]
                if (existingRow != null) {
                    api.updateRow(spreadsheetId, existingRow, row)
                } else {
                    api.appendRow(spreadsheetId, row)
                }
                dao.update(withId.copy(syncedAt = System.currentTimeMillis()))
            }

            suggestPassengerForOverlaps(entries)

            settings.setLastSync(time = System.currentTimeMillis(), error = null)
            Result.success()
        } catch (e: Exception) {
            settings.setLastSync(time = null, error = e.message ?: e.javaClass.simpleName)
            Result.retry()
        }
    }

    /**
     * If the other driver's sheet rows overlap a local unclassified trip in time
     * with a similar distance, both phones recorded the same drive — nudge this
     * one towards the Passenger action.
     */
    private suspend fun suggestPassengerForOverlaps(entries: SheetsApi.Entries) {
        val myDriver = AppGraph.settings.driverName.first()
        if (myDriver.isBlank()) return
        val otherRows = entries.rows.filter {
            val driver = it.getOrNull(SheetsApi.COL_DRIVER).orEmpty()
            driver.isNotBlank() && !driver.equals(myDriver, ignoreCase = true)
        }
        if (otherRows.isEmpty()) return

        for (trip in AppGraph.tripDao.getUnclassified()) {
            val overlap = otherRows.firstOrNull { row ->
                val window = row.toTimeWindow() ?: return@firstOrNull false
                val km = row.getOrNull(SheetsApi.COL_DISTANCE)?.toDoubleOrNull()
                    ?: return@firstOrNull false
                val overlaps = window.first <= trip.endTime && window.second >= trip.startTime
                val similarKm =
                    kotlin.math.abs(km - trip.distanceKm) <= maxOf(1.0, trip.distanceKm * 0.25)
                overlaps && similarKm
            }
            if (overlap != null) {
                TripNotifications.postPassengerSuggestion(
                    applicationContext,
                    trip,
                    otherDriver = overlap.getOrNull(SheetsApi.COL_DRIVER).orEmpty()
                )
            }
        }
    }
}

private val sheetDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val sheetTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun Trip.toSheetRow(): List<String> {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startTime).atZone(zone)
    val end = Instant.ofEpochMilli(endTime).atZone(zone)
    return listOf(
        sheetDateFormat.format(start),
        sheetTimeFormat.format(start),
        sheetTimeFormat.format(end),
        startAddress.orEmpty(),
        endAddress.orEmpty(),
        String.format(Locale.US, "%.2f", distanceKm),
        driver,
        type.lowercase(Locale.US),
        business.orEmpty(),
        purpose.orEmpty(),
        source.lowercase(Locale.US),
        entryId.orEmpty(),
    )
}

/** Parses a sheet row's date + start/end times back into epoch millis. */
private fun List<String>.toTimeWindow(): Pair<Long, Long>? = try {
    val zone = ZoneId.systemDefault()
    val date = LocalDate.parse(getOrNull(SheetsApi.COL_DATE).orEmpty(), sheetDateFormat)
    val start = LocalTime.parse(getOrNull(SheetsApi.COL_START_TIME).orEmpty(), sheetTimeFormat)
    val end = LocalTime.parse(getOrNull(SheetsApi.COL_END_TIME).orEmpty(), sheetTimeFormat)
    val startMillis = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
    var endMillis = date.atTime(end).atZone(zone).toInstant().toEpochMilli()
    if (endMillis < startMillis) endMillis += 24 * 60 * 60_000L // trip past midnight
    startMillis to endMillis
} catch (_: Exception) {
    null
}
