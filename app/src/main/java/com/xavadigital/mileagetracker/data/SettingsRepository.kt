package com.xavadigital.mileagetracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val driverKey = stringPreferencesKey("driver_name")

    /** Null until the store has loaded, then the saved name ("" if never set). */
    val driverName: Flow<String> = context.dataStore.data.map { it[driverKey] ?: "" }

    suspend fun setDriverName(name: String) {
        context.dataStore.edit { it[driverKey] = name.trim() }
    }
}
