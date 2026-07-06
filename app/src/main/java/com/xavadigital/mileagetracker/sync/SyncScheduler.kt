package com.xavadigital.mileagetracker.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.xavadigital.mileagetracker.tracking.UnclassifiedReminderWorker
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Safety-net sync every 6 hours; classify actions also trigger [syncNow]. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sheet-sync-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Twice-daily unclassified-trips reminder (silent when there's no backlog). */
    fun scheduleUnclassifiedReminder(context: Context) {
        val request = PeriodicWorkRequestBuilder<UnclassifiedReminderWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "unclassified-reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "sheet-sync-now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
