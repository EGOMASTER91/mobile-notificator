package ro.pretradar

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ro.pretradar.domain.*
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SchedulingTest {
    @Test fun monitoringIsUniqueBatteryConstrainedAndStopsAfterRemovingTheLastProduct() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<RadarApplication>()
        val repository = app.repository
        val manager = WorkManager.getInstance(app)
        val candidate = ProductCandidate("scheduler-test", "Scheduler test", "https://altex.ro/test/cpd/TEST/", Store.ALTEX)
        suspend fun active(): List<WorkInfo> = manager.getWorkInfosForUniqueWork(PriceCheckScheduler.WORK_NAME)
            .get(5, TimeUnit.SECONDS).filter { !it.state.isFinished }
        suspend fun waitFor(predicate: (List<WorkInfo>) -> Boolean): List<WorkInfo> = withTimeout(15_000) {
            var infos = active()
            while (!predicate(infos)) { delay(100); infos = active() }
            infos
        }
        repository.preferences.setInterval(6)
        repository.preferences.setWifiOnly(false)
        repository.preferences.setStores(Store.defaults)
        try {
            repository.track(candidate, listOf(candidate), emptyList())
            val info = waitFor { it.size == 1 }.single()
            assertTrue(info.constraints.requiresBatteryNotLow())
            assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
            assertEquals(TimeUnit.HOURS.toMillis(6), info.periodicityInfo!!.repeatIntervalMillis)
            assertEquals(TimeUnit.MINUTES.toMillis(90), info.periodicityInfo!!.flexIntervalMillis)
            repository.preferences.setWifiOnly(true)
            val changed = waitFor { it.size == 1 && it.single().constraints.requiredNetworkType == NetworkType.UNMETERED }.single()
            assertEquals(info.id, changed.id)
            repository.preferences.setInterval(24)
            waitFor { it.size == 1 && it.single().periodicityInfo?.repeatIntervalMillis == TimeUnit.HOURS.toMillis(24) }
            repository.remove(candidate.id)
            assertTrue(waitFor { it.isEmpty() }.isEmpty())
        } finally {
            repository.remove(candidate.id)
            repository.preferences.setWifiOnly(false)
            repository.preferences.setInterval(6)
        }
    }

    @Test fun batteryDefaultUpgradePreservesAnExplicitInterval() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<RadarApplication>()
        app.repository.preferences.setInterval(3)
        app.repository.preferences.optimizeDefaults()
        assertEquals(3, app.repository.preferences.settings.first().intervalHours)
        app.repository.preferences.setInterval(6)
    }
}
