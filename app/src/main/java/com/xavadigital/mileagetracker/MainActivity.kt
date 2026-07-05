package com.xavadigital.mileagetracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.xavadigital.mileagetracker.ui.HomeScreen
import com.xavadigital.mileagetracker.ui.SettingsScreen
import com.xavadigital.mileagetracker.ui.theme.MileageTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Trip id from a "Work…" notification tap; consumed by HomeScreen. */
    private val classifyRequest = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MileageTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    SettingsScreen(onBack = { showSettings = false })
                } else {
                    HomeScreen(
                        classifyRequest = classifyRequest,
                        onOpenSettings = { showSettings = true }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val tripId = intent?.getLongExtra(EXTRA_CLASSIFY_TRIP_ID, -1L) ?: -1L
        if (tripId > 0) classifyRequest.value = tripId
    }

    companion object {
        const val EXTRA_CLASSIFY_TRIP_ID = "classify_trip_id"
    }
}
