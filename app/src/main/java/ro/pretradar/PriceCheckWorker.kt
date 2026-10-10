package ro.pretradar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import ro.pretradar.data.RadarRepository

class PriceCheckWorker @JvmOverloads constructor(
    context: Context,
    params: WorkerParameters,
    private val repository: RadarRepository = (context.applicationContext as RadarApplication).repository,
    private val budgetMillis: Long = 120_000L
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        withTimeout(budgetMillis) { repository.refreshAll(background = true) }
        Result.success()
    } catch (_: TimeoutCancellationException) {
        // Continue with the saved cursor next period, without immediate battery-consuming retries.
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}
