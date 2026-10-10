package ro.pretradar

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ro.pretradar.data.*
import ro.pretradar.domain.*
import ro.pretradar.ui.RadarViewModel

@RunWith(AndroidJUnit4::class)
class HotRepositoryTest {
    @Test fun categoryCacheAndLocalThresholdNeverScheduleCatalogPolling() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RadarDatabase::class.java).build()
        val prefs = Preferences(context)
        prefs.setStores(Store.defaults); prefs.setDemo(false); prefs.setHotCategory(HotCategory.PHONES)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val source = object : StoreSource {
            override val store = Store.ALTEX
            override suspend fun search(query: String) = SourceResult<ProductCandidate>(store, emptyList())
            override suspend fun offers(product: ProductCandidate) = SourceResult<Offer>(store, emptyList())
            override suspend fun hot(category: HotCategory): SourceResult<HotDeal> {
                calls.incrementAndGet()
                val product = ProductCandidate("a", "Telefon test", "https://altex.ro/test/cpd/A/", store, price = 5_000)
                return SourceResult(store, listOf(HotDeal(product, 10_000, category)))
            }
        }
        val repo = RadarRepository(db, prefs, listOf(source)) { _, _ -> }
        val owner = ViewModelStore()
        val vm = withContext(Dispatchers.Main) { RadarViewModel(repo).also { owner.put("hot", it) } }
        suspend fun load(category: HotCategory, force: Boolean = false) {
            prefs.setHotCategory(category)
            withTimeout(3_000) { vm.settings.first { it.hotCategory == category && !it.demo } }
            withContext(Dispatchers.Main) { vm.loadHot(force) }
            withTimeout(3_000) { vm.hot.first { it.category == category && !it.loading && it.loadedAt > 0 } }
        }
        try {
            load(HotCategory.PHONES); assertEquals(1, calls.get())
            prefs.setHotMinimum(70)
            withTimeout(3_000) { vm.settings.first { it.hotMinimum == 70 } }
            assertTrue(HotDeals.filter(vm.hot.value.results.flatMap { it.items }, 70, Store.defaults).isEmpty())
            load(HotCategory.PHONES); assertEquals(1, calls.get())
            load(HotCategory.LAPTOPS); assertEquals(2, calls.get())
            load(HotCategory.PHONES); assertEquals(2, calls.get())
            load(HotCategory.PHONES, force = true); assertEquals(3, calls.get())
            repo.refreshAll(background = true); assertEquals(3, calls.get())
            assertTrue(repo.products.first().isEmpty())
        } finally {
            withContext(Dispatchers.Main) { owner.clear() }
            prefs.setHotMinimum(30); prefs.setHotCategory(HotCategory.PHONES)
            db.close()
        }
    }
}
