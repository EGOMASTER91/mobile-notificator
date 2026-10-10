package ro.pretradar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ro.pretradar.data.*
import ro.pretradar.domain.*

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var database: RadarDatabase
    private val candidate = ProductCandidate("test-product", "Sony WH-1000XM5", "https://altex.ro/casti/cpd/TEST/", Store.ALTEX)
    private var price = 129900L
    private var blocked = false
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RadarDatabase::class.java).build() }
    @After fun cleanup() { database.close() }
    @Test fun aSlowOrFailingStoreDoesNotHoldBackExactResultsFromOtherStores() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        val firstResult = CompletableDeferred<SourceResult<ProductCandidate>>()
        val sources = listOf(Store.ITGALAXY, Store.EVOMAG).map { store -> object : StoreSource {
            override val store = store
            override suspend fun search(query: String): SourceResult<ProductCandidate> {
                if (store == Store.EVOMAG) {
                    waiting.await()
                    return SourceResult(store, emptyList(), "Căutare indisponibilă")
                }
                return SourceResult(store, listOf(
                    candidate.copy(source = store, title = "Ventilator ARCTIC AC BioniX P120 ARGB"),
                    candidate.copy(id = "wrong", source = store, title = "Ventilator ARCTIC BioniX P120 Alb/Gri")))
            }
            override suspend fun offers(product: ProductCandidate) = SourceResult<Offer>(store, emptyList())
        } }
        val preferences = Preferences(ApplicationProvider.getApplicationContext())
        val original = preferences.settings.first().stores
        try {
            preferences.setStores(setOf(Store.ITGALAXY, Store.EVOMAG))
            val repository = RadarRepository(database, preferences, sources) { _, _ -> }
            val pending = async { repository.search("arctic bionix p120 a-rgb") {
                if (it.source == Store.ITGALAXY) firstResult.complete(it)
            } }
            val partial = withTimeout(5_000) { firstResult.await() }
            assertEquals(1, partial.items.size)
            assertFalse(pending.isCompleted)
            waiting.complete(Unit)
            assertEquals("Căutare indisponibilă", pending.await().single { it.source == Store.EVOMAG }.error)
            preferences.setStores(setOf(Store.ITGALAXY))
            assertEquals(listOf(Store.ITGALAXY), repository.search("arctic bionix p120 a-rgb").map { it.source })
        } finally { waiting.complete(Unit); preferences.setStores(original) }
    }
    @Test fun searchFiltersEverySourceAndPreservesSourceErrors() = runBlocking {
        val sources = Store.entries.map { store -> object : StoreSource {
            override val store = store
            override suspend fun search(query: String) = SourceResult(store, listOf(
                candidate.copy(id = "${store.name}-exact", source = store, title = "Telefon Samsung Galaxy S24 128GB Negru"),
                candidate.copy(id = "${store.name}-similar", source = store, title = "Telefon Samsung Galaxy S23 128GB"),
                candidate.copy(id = "${store.name}-edition", source = store, title = "Samsung Galaxy S24 Ultra 256GB"),
                candidate.copy(id = "${store.name}-accessory", source = store, title = "Husa Samsung Galaxy S24")
            ), if (store == Store.COMPARI) "Acces limitat" else null)
            override suspend fun offers(product: ProductCandidate) = SourceResult<Offer>(store, emptyList())
        } }
        val repository = RadarRepository(database, Preferences(ApplicationProvider.getApplicationContext()), sources) { _, _ -> }
        repository.preferences.setStores(Store.entries.toSet())
        val results = repository.search("Samsung S24")
        assertEquals(Store.entries.size, results.size)
        results.forEach { result -> assertEquals(listOf("${result.source.name}-exact"), result.items.map { it.id }) }
        assertEquals("Acces limitat", results.single { it.source == Store.COMPARI }.error)
        assertTrue(repository.search("Samsung S25").all { it.items.isEmpty() })
        repository.preferences.setStores(Store.defaults)
    }
    @Test fun failedChecksPreservePriceAndSuccessfulDropsNotifyOnce() = runBlocking {
        val notifications = mutableListOf<AlertEvent>()
        val source = object : StoreSource {
            override val store = Store.ALTEX
            override suspend fun search(query: String) = SourceResult(store, listOf(candidate))
            override suspend fun offers(product: ProductCandidate): SourceResult<Offer> = if (blocked) SourceResult(store, emptyList(), "Acces blocat")
                else SourceResult(store, listOf(Offer(candidate.id, "Altex", "Altex", candidate.url, price, true)))
        }
        val repository = RadarRepository(database, Preferences(ApplicationProvider.getApplicationContext()), listOf(source)) { _, e -> notifications += e }
        repository.preferences.setStores(Store.entries.toSet())
        repository.track(candidate, listOf(candidate), emptyList())
        repository.refreshAll()
        assertTrue(notifications.isEmpty())
        price = 109900; repository.refreshAll(); repository.refreshAll()
        assertEquals(1, notifications.size)
        val before = database.dao().snapshots(candidate.id).single().offerJson
        blocked = true; repository.refreshAll()
        assertEquals(before, database.dao().snapshots(candidate.id).single().offerJson)
        assertTrue(database.dao().product(candidate.id)!!.error.contains("Acces blocat"))
        assertEquals(1, notifications.size)
        assertEquals(2, repository.history(candidate.id).first().size)
        repository.remove(candidate.id)
        assertNull(database.dao().product(candidate.id))
        assertTrue(database.dao().snapshots(candidate.id).isEmpty())
    }
}
