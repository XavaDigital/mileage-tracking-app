package com.xavadigital.mileagetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.xavadigital.mileagetracker.ui.HomeScreen
import com.xavadigital.mileagetracker.ui.theme.MileageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MileageTheme {
                HomeScreen()
            }
        }
    }
}
