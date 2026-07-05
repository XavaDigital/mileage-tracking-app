package com.xavadigital.mileagetracker.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Trip::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
}
