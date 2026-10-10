package ro.pretradar

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ro.pretradar.data.*
import ro.pretradar.domain.*

@RunWith(AndroidJUnit4::class)
class BackgroundChecksTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: RadarDatabase
    private lateinit var repository: RadarRepository
    private var requests = 0
    private var waiting = false
    private val candidate = ProductCandidate("a-background", "Produs background Sony WH-1000XM5", "https://altex.ro/test/cpd/TEST/", Store.ALTEX)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, RadarDatabase::class.java).build()
        val notifier = RadarNotifications(context)
        val source = object : StoreSource {
            override val store = Store.ALTEX
            override suspend fun search(query: String) = SourceResult(store, listOf(candidate))
            override suspend fun offers(product: ProductCandidate): SourceResult<Offer> {
                requests++
                if (waiting && product.id == candidate.id) awaitCancellation()
                return SourceResult(store, listOf(Offer(product.id, "Altex", "Altex", product.url, 90_000, true)))
            }
        }
        repository = RadarRepository(database, Preferences(context), listOf(source), notifier::post)
        repository.preferences.setStores(Store.defaults)
        repository.preferences.setBackgroundCursor("")
        notifications.cancelAll()
    }

    @After fun cleanup() { database.close(); notifications.cancelAll() }

    private fun worker(budget: Long = 120_000) = TestListenableWorkerBuilder<PriceCheckWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                PriceCheckWorker(appContext, workerParameters, repository, budget)
        }).build()

    @Test fun workerPostsAnAndroidNotificationWithoutOpeningAnActivity() = runBlocking {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val old = Offer(candidate.id, "Altex", "Altex", candidate.url, 100_000, true, checkedAt = System.currentTimeMillis() - 7_200_000)
        repository.track(candidate, listOf(candidate), listOf(old))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        withTimeout(5_000) { while (notifications.activeNotifications.isEmpty()) delay(50) }
        val notification = notifications.activeNotifications.single().notification
        assertTrue(notification.extras.getString(Notification.EXTRA_TITLE).orEmpty().contains(candidate.title))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("900"))
        assertEquals(1, repository.alerts.first().size)
        assertEquals(1, requests)
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(1, requests) // A recent manual/background observation does not download the page again.
        assertEquals(1, repository.alerts.first().size)
    }

    @Test fun unchangedEmptyAndInactiveListsDoNotSendNotificationsOrWasteRequests() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(0, requests)
        repository.track(candidate, listOf(candidate), listOf(Offer(candidate.id, "Altex", "Altex", candidate.url, 90_000, true,
            checkedAt = System.currentTimeMillis() - 7_200_000)))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(1, requests)
        assertTrue(repository.alerts.first().isEmpty())
        assertTrue(notifications.activeNotifications.isEmpty())
        repository.preferences.setStores(setOf(Store.EMAG))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(1, requests)
        repository.preferences.setStores(Store.defaults)
    }

    @Test fun aBoundedRunResumesAfterTheTimedOutProductNextPeriod() = runBlocking {
        waiting = true
        val next = candidate.copy(id = "b-background", url = "https://altex.ro/next/cpd/TEST2/")
        repository.track(candidate, listOf(candidate), emptyList())
        repository.track(next, listOf(next), emptyList())
        assertEquals(ListenableWorker.Result.success(), worker(1_500).doWork())
        assertEquals(candidate.id, repository.preferences.backgroundCursor())
        assertFalse(repository.refreshing.value)
        assertEquals(ListenableWorker.Result.success(), worker(1_500).doWork())
        assertNotNull(database.dao().product(next.id))
        assertTrue(database.dao().snapshots(next.id).isNotEmpty())
        assertTrue(repository.alerts.first().isEmpty()) // Initial observation is not a price-drop alert.
    }

    @Test fun duplicateTrackedLinksShareOneObservationPerBatch() = runBlocking {
        val next = candidate.copy(id = "b-background")
        repository.track(candidate, listOf(candidate, candidate), emptyList())
        repository.track(next, listOf(next), emptyList())
        repository.refreshAll()
        assertEquals(1, requests)
        assertEquals(candidate.id, repository.snapshots.first().single { it.productId == candidate.id }.productId)
        assertEquals(next.id, repository.snapshots.first().single { it.productId == next.id }.productId)
    }
}
