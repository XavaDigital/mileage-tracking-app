package com.xavadigital.mileagetracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val driverKey = stringPreferencesKey("driver_name")
    private val carAddressKey = stringPreferencesKey("car_bt_address")
    private val carNameKey = stringPreferencesKey("car_bt_name")
    private val autoTrackKey = booleanPreferencesKey("auto_track_enabled")
    private val spreadsheetIdKey = stringPreferencesKey("spreadsheet_id")
    private val sheetsConnectedKey = booleanPreferencesKey("sheets_connected")
    private val lastSyncTimeKey = longPreferencesKey("last_sync_time")
    private val lastSyncErrorKey = stringPreferencesKey("last_sync_error")

    val driverName: Flow<String> = context.dataStore.data.map { it[driverKey] ?: "" }

    /** MAC address of the car's Bluetooth device; null until a car is chosen. */
    val carAddress: Flow<String?> = context.dataStore.data.map { it[carAddressKey] }
    val carName: Flow<String?> = context.dataStore.data.map { it[carNameKey] }
    val autoTrackEnabled: Flow<Boolean> = context.dataStore.data.map { it[autoTrackKey] ?: false }

    suspend fun setDriverName(name: String) {
        context.dataStore.edit { it[driverKey] = name.trim() }
    }

    suspend fun setCarDevice(address: String, name: String) {
        context.dataStore.edit {
            it[carAddressKey] = address
            it[carNameKey] = name
        }
    }

    suspend fun setAutoTrackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[autoTrackKey] = enabled }
    }

    val spreadsheetId: Flow<String?> = context.dataStore.data.map { it[spreadsheetIdKey] }
    val sheetsConnected: Flow<Boolean> =
        context.dataStore.data.map { it[sheetsConnectedKey] ?: false }
    val lastSyncTime: Flow<Long?> = context.dataStore.data.map { it[lastSyncTimeKey] }
    val lastSyncError: Flow<String?> = context.dataStore.data.map { it[lastSyncErrorKey] }

    suspend fun setSpreadsheetId(id: String) {
        context.dataStore.edit { it[spreadsheetIdKey] = id }
    }

    suspend fun setSheetsConnected(connected: Boolean) {
        context.dataStore.edit { it[sheetsConnectedKey] = connected }
    }

    suspend fun setLastSync(time: Long?, error: String?) {
        context.dataStore.edit {
            if (time != null) it[lastSyncTimeKey] = time
            if (error != null) it[lastSyncErrorKey] = error else it.remove(lastSyncErrorKey)
        }
    }
}
