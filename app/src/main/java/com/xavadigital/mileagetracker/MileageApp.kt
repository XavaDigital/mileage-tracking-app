package com.xavadigital.mileagetracker

import android.app.Application
import com.xavadigital.mileagetracker.data.AppGraph

class MileageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
