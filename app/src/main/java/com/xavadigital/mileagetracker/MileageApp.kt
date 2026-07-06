package com.xavadigital.mileagetracker

import android.app.Application
import com.xavadigital.mileagetracker.data.AppGraph
import com.xavadigital.mileagetracker.sync.SyncScheduler

class MileageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.scheduleUnclassifiedReminder(this)
    }
}
