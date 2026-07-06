package com.xavadigital.mileagetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): Trip?

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips ORDER BY startTime ASC")
    suspend fun getAllChronological(): List<Trip>

    @Query(
        "SELECT * FROM trips WHERE type != 'UNCLASSIFIED' AND syncedAt IS NULL " +
            "ORDER BY startTime ASC"
    )
    suspend fun getPendingSync(): List<Trip>

    @Query("SELECT * FROM trips WHERE type = 'UNCLASSIFIED'")
    suspend fun getUnclassified(): List<Trip>

    @Query(
        "SELECT DISTINCT business FROM trips " +
            "WHERE business IS NOT NULL AND business != '' ORDER BY business"
    )
    fun observeBusinesses(): Flow<List<String>>

    @Query(
        "SELECT DISTINCT purpose FROM trips " +
            "WHERE purpose IS NOT NULL AND purpose != '' ORDER BY purpose"
    )
    fun observePurposes(): Flow<List<String>>
}
