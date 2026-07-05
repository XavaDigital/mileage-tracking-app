package com.xavadigital.mileagetracker.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.xavadigital.mileagetracker.data.AppGraph
import com.xavadigital.mileagetracker.data.Trip
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CsvExporter {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun shareCsv(context: Context) {
        val file = withContext(Dispatchers.IO) { writeCsv(context) }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Mileage log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export mileage log"))
    }

    private suspend fun writeCsv(context: Context): File {
        val trips = AppGraph.tripDao.getAllChronological()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "mileage-log.csv")
        val zone = ZoneId.systemDefault()

        file.bufferedWriter().use { writer ->
            writer.appendLine(
                "Date,Start Time,End Time,Start Location,End Location," +
                    "Distance (km),Driver,Type,Business,Purpose,Source"
            )
            trips.forEach { trip ->
                val start = Instant.ofEpochMilli(trip.startTime).atZone(zone)
                val end = Instant.ofEpochMilli(trip.endTime).atZone(zone)
                val row = listOf(
                    dateFormat.format(start),
                    timeFormat.format(start),
                    timeFormat.format(end),
                    trip.startAddress.orEmpty(),
                    trip.endAddress.orEmpty(),
                    String.format(Locale.US, "%.2f", trip.distanceKm),
                    trip.driver,
                    trip.type.lowercase(Locale.US),
                    trip.business.orEmpty(),
                    trip.purpose.orEmpty(),
                    trip.source.lowercase(Locale.US),
                )
                writer.appendLine(row.joinToString(",") { escape(it) })
            }
        }
        return file
    }

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
