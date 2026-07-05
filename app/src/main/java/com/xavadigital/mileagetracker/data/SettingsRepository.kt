package com.xavadigital.mileagetracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
