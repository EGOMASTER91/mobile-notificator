package ro.pretradar

import androidx.work.*
import java.util.concurrent.TimeUnit

data class MonitoringPlan(val intervalHours: Int, val wifiOnly: Boolean, val hasProducts: Boolean)

class PriceCheckScheduler(private val workManager: WorkManager) {
    fun update(plan: MonitoringPlan) {
        if (!plan.hasProducts) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request(plan))
    }

    companion object {
        const val WORK_NAME = "price-checks"
        fun request(plan: MonitoringPlan): PeriodicWorkRequest {
            val minutes = plan.intervalHours.coerceIn(1, 24) * 60L
            return PeriodicWorkRequestBuilder<PriceCheckWorker>(minutes, TimeUnit.MINUTES,
                (minutes / 4).coerceAtLeast(15), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(if (plan.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .addTag(WORK_NAME).build()
        }
    }
}
