package com.xavadigital.mileagetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Double,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val type: String = TYPE_UNCLASSIFIED,
    val business: String? = null,
    val purpose: String? = null,
    val driver: String = "",
    val source: String = SOURCE_MANUAL,
    val polyline: String? = null,
    /** Stable id used to find/update this trip's row in the shared sheet. */
    val entryId: String? = null,
    /** When this trip's current state last landed in the sheet; null = needs sync. */
    val syncedAt: Long? = null,
) {
    val distanceKm: Double get() = distanceMeters / 1000.0

    companion object {
        const val TYPE_UNCLASSIFIED = "UNCLASSIFIED"
        const val TYPE_WORK = "WORK"
        const val TYPE_PERSONAL = "PERSONAL"

        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_BLUETOOTH = "BLUETOOTH"
        const val SOURCE_NFC = "NFC"
    }
}
