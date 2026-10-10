package ro.pretradar.data

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.pretradar.domain.*

class RadarRepository(
    private val database: RadarDatabase,
    val preferences: Preferences,
    private val sources: List<StoreSource>,
    private val onAlert: (Long, AlertEvent) -> Unit
) {
    private val dao = database.dao()
    private val gson = Gson()
    private val refreshLock = Mutex()
    val products = dao.observeProducts()
    val alerts = dao.observeAlerts()
    val snapshots = dao.observeSnapshots().map { list -> list.mapNotNull { decodeOffer(it.offerJson) } }
    val refreshing = MutableStateFlow(false)

    suspend fun hot(category: HotCategory): List<SourceResult<HotDeal>> {
        val active = preferences.settings.first().stores
        return coroutineScope { sources.filter { it.store in active && it.store in setOf(Store.ALTEX, Store.EMAG) }
            .map { source -> async { source.hot(category) } }.awaitAll() }
    }

    suspend fun search(query: String,
        onSourceResult: (SourceResult<ProductCandidate>) -> Unit = {}): List<SourceResult<ProductCandidate>> {
        val settings = preferences.settings.first()
        if (query.trim().length < 2) return emptyList()
        val filter = ProductSearch(query)
        return coroutineScope { sources.filter { it.store in settings.stores }.map { source -> async {
            source.search(query).let { result -> result.copy(items = result.items.filter(filter::matches)) }
                .also(onSourceResult)
        } }.awaitAll() }
    }
    suspend fun fetch(links: List<ProductCandidate>, productId: String,
        observations: MutableMap<Pair<Store, String>, SourceResult<Offer>>? = null): List<SourceResult<Offer>> {
        val active = preferences.settings.first().stores
        return coroutineScope { links.filter { it.source in active }.distinctBy { it.source to it.url }.map { candidate -> async {
            val key = candidate.source to candidate.url
            val result = observations?.get(key) ?: sources.first { it.store == candidate.source }.offers(candidate)
                .also { observations?.set(key, it) }
            result.let {
                result.copy(items = result.items.map { it.copy(productId = productId) })
            }
        } }.awaitAll() }
    }
    fun links(product: TrackedProduct): List<ProductCandidate> = runCatching {
        gson.fromJson<List<ProductCandidate>>(product.linksJson, object : TypeToken<List<ProductCandidate>>() {}.type)
    }.getOrDefault(emptyList())
    fun offers(snapshots: List<Offer>, id: String): List<Offer> = snapshots.filter { it.productId == id }
    fun history(id: String): Flow<List<PricePoint>> = dao.history(id).map { entries -> entries.map { PricePoint(it.time, it.price, it.store) } }
    suspend fun findProduct(id: String): TrackedProduct? = dao.product(id)

    suspend fun track(candidate: ProductCandidate, links: List<ProductCandidate>, offers: List<Offer>, threshold: Long? = null) = refreshLock.withLock {
        require(!candidate.id.startsWith("demo-")) { "Exemplele nu pot fi urmărite." }
        database.withTransaction {
            val existing = dao.product(candidate.id)
            dao.saveProduct(TrackedProduct(candidate.id, candidate.title, candidate.imageUrl, gson.toJson(links),
                threshold = threshold, lastChecked = existing?.lastChecked ?: offers.maxOfOrNull { it.checkedAt } ?: 0))
            if (existing == null) {
                val minima = AlertEngine.minima(offers)
                dao.saveSnapshots(minima.map { Snapshot(candidate.id, it.merchantKey, links.firstOrNull()?.source?.name.orEmpty(), gson.toJson(it)) })
                dao.addHistory(minima.map { HistoryEntry(productId = candidate.id, store = it.store, price = it.price, time = it.checkedAt) })
            }
        }
    }
    suspend fun remove(id: String) = refreshLock.withLock { database.withTransaction {
        dao.deleteProduct(id); dao.deleteSnapshots(id); dao.deleteHistory(id); dao.deleteAlerts(id)
    } }
    suspend fun setThreshold(id: String, threshold: Long?) {
        dao.product(id)?.let { dao.saveProduct(it.copy(threshold = threshold)) }
    }
    suspend fun markRead(id: Long) = dao.markRead(id)
    suspend fun markAllRead() = dao.markAllRead()

    suspend fun refreshAll(background: Boolean = false) = refreshLock.withLock {
        refreshing.value = true
        try {
            val activeStores = preferences.settings.first().stores
            val products = dao.activeProducts().filter { product -> links(product).any { it.source in activeStores } }.sortedBy { it.id }
            val cursor = if (background) preferences.backgroundCursor() else null
            val cursorIndex = products.indexOfFirst { it.id == cursor }
            val ordered = if (cursorIndex >= 0) products.drop(cursorIndex + 1) + products.take(cursorIndex + 1) else products
            val observations = java.util.concurrent.ConcurrentHashMap<Pair<Store, String>, SourceResult<Offer>>()
            for (product in ordered) {
                currentCoroutineContext().ensureActive()
                if (background && product.lastChecked > 0 && System.currentTimeMillis() - product.lastChecked in 0 until 900_000L) continue
                if (background) preferences.setBackgroundCursor(product.id)
                refreshProduct(product, observations)
            }
            dao.pruneHistory(System.currentTimeMillis() - 90L * 86400000L)
        } finally { refreshing.value = false }
    }
    private suspend fun refreshProduct(product: TrackedProduct, observations: MutableMap<Pair<Store, String>, SourceResult<Offer>>) {
        val results = fetch(links(product), product.id, observations)
        if (results.isEmpty()) return
        val offers = results.flatMap { it.items }.groupBy { it.merchantKey }.values.map { group ->
            group.filter { it.inStock }.minByOrNull { it.price } ?: group.minBy { it.price }
        }
        val errors = results.mapNotNull { r -> r.error?.let { "${r.source.label}: $it" } }.joinToString("\n")
        val notifications = mutableListOf<Pair<Long, AlertEvent>>()
        database.withTransaction {
            val previous = dao.snapshots(product.id).mapNotNull { decodeOffer(it.offerJson) }.associateBy { it.merchantKey }
            val history = mutableListOf<HistoryEntry>()
            for (offer in offers) {
                val old = previous[offer.merchantKey]
                val event = AlertEngine.detect(old, offer, WatchRule(threshold = product.threshold))?.copy(title = product.title)
                if (event != null) {
                    val id = dao.addAlert(SavedAlert(productId = product.id, title = product.title, store = offer.store,
                        oldPrice = event.oldPrice, newPrice = event.newPrice, promotionJson = event.promotion?.let { gson.toJson(it) }, createdAt = event.createdAt))
                    notifications += id to event
                }
                if (old == null || old.price != offer.price || offer.checkedAt - old.checkedAt >= 86400000L) {
                    history += HistoryEntry(productId = product.id, store = offer.store, price = offer.price, time = offer.checkedAt)
                }
            }
            dao.saveSnapshots(offers.map { Snapshot(product.id, it.merchantKey, "", gson.toJson(it)) })
            dao.addHistory(history)
            // Failed fetches preserve last successful prices and timestamps.
            dao.saveProduct(product.copy(error = errors,
                lastChecked = if (results.any { it.items.isNotEmpty() }) System.currentTimeMillis() else product.lastChecked))
        }
        notifications.forEach { (id, event) -> onAlert(id, event) }
    }
    private fun decodeOffer(json: String): Offer? = runCatching { gson.fromJson(json, Offer::class.java) }.getOrNull()
}
