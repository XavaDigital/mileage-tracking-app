package com.xavadigital.mileagetracker.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xavadigital.mileagetracker.data.AppGraph

/** Fires periodically; only notifies when unclassified trips are actually waiting. */
class UnclassifiedReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val count = AppGraph.tripDao.getUnclassified().size
        if (count > 0) {
            TripNotifications.postUnclassifiedReminder(applicationContext, count)
        }
        return Result.success()
    }
}
