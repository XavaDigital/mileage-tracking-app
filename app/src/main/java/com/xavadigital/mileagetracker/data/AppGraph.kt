package com.xavadigital.mileagetracker.data

import android.content.Context
import androidx.room.Room

/** Tiny service locator — this app is too small for a DI framework. */
object AppGraph {
    lateinit var database: AppDatabase
        private set
    lateinit var settings: SettingsRepository
        private set

    val tripDao: TripDao get() = database.tripDao()

    fun init(context: Context) {
        database = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "mileage.db"
        ).build()
        settings = SettingsRepository(context.applicationContext)
    }
}
