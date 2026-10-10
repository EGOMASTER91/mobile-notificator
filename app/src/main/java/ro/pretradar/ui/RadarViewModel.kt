package ro.pretradar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ro.pretradar.data.*
import ro.pretradar.domain.*

data class DetailState(
    val product: ProductCandidate,
    val links: List<ProductCandidate>,
    val offers: List<Offer> = emptyList(),
    val issues: List<String> = emptyList(),
    val loading: Boolean = false,
    val threshold: Long? = null
)
data class HotState(val category: HotCategory? = null, val results: List<SourceResult<HotDeal>> = emptyList(),
    val loading: Boolean = false, val loadedAt: Long = 0)
class RadarViewModel(val repository: RadarRepository) : ViewModel() {
    val settings = repository.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val products = repository.products.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val snapshots = repository.snapshots.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val alerts = repository.alerts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val refreshing = repository.refreshing
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<SourceResult<ProductCandidate>>>(emptyList())
    val searching = MutableStateFlow(false)
    val searched = MutableStateFlow(false)
    val detail = MutableStateFlow<DetailState?>(null)
    val history = MutableStateFlow<List<PricePoint>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private var detailJob: Job? = null
    private var historyJob: Job? = null
    val hot = MutableStateFlow(HotState())
    private var hotJob: Job? = null
    private data class HotKey(val category: HotCategory, val stores: Set<Store>, val demo: Boolean)
    private val hotCache = mutableMapOf<HotKey, Pair<Long, HotState>>()

    fun loadHot(force: Boolean = false) {
        val settings = settings.value
        val key = HotKey(settings.hotCategory, settings.stores.intersect(setOf(Store.ALTEX, Store.EMAG)), settings.demo)
        hotJob?.cancel()
        val cached = hotCache[key]
        if (!force && cached != null && android.os.SystemClock.elapsedRealtime() - cached.first in 0 until 900_000) {
            hot.value = cached.second; return
        }
        hotJob = viewModelScope.launch {
            hot.value = HotState(key.category, loading = true)
            try {
                val results = if (key.demo) key.stores.map { source -> SourceResult(source,
                    DemoData.hot(key.category).filter { it.product.source == source }) } else repository.hot(key.category)
                val state = HotState(key.category, results, loadedAt = System.currentTimeMillis())
                hot.value = state
                hotCache[key] = android.os.SystemClock.elapsedRealtime() to state
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { hot.value = HotState(key.category,
                key.stores.map { SourceResult(it, emptyList(), "Ofertele nu au putut fi încărcate. Reîncearcă.") }) }
        }
    }
    fun cancelHot() { hotJob?.cancel(); hot.value = hot.value.copy(loading = false) }
    fun setHotMinimum(value: Int) { viewModelScope.launch { repository.preferences.setHotMinimum(value) } }
    fun setHotCategory(value: HotCategory) { viewModelScope.launch { repository.preferences.setHotCategory(value) } }

    fun search(value: String = query.value) {
        if (value.trim().length < 2) { message.value = "Introdu cel puțin două caractere."; return }
        query.value = value
        val generation = ++searchGeneration
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching.value = true; searched.value = true; results.value = emptyList()
            try {
                if (settings.value.demo) {
                    val filter = ProductSearch(value)
                    results.value = Store.entries.map { store -> SourceResult(store,
                        DemoData.candidates.filter { it.source == store && filter.matches(it) }) }
                } else repository.search(value) { result ->
                    if (generation == searchGeneration) results.update { current ->
                        (current.filterNot { it.source == result.source } + result).sortedBy { it.source.ordinal }
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { message.value = "Căutarea nu a putut fi finalizată. Încearcă din nou." }
            finally { if (generation == searchGeneration) searching.value = false }
        }
    }
    fun open(product: ProductCandidate) {
        detailJob?.cancel(); historyJob?.cancel()
        history.value = emptyList()
        if (product.id.startsWith("demo-")) {
            detail.value = DetailState(product, listOf(product), DemoData.offers(product.id))
            history.value = DemoData.history(product.id)
            return
        }
        detailJob = viewModelScope.launch {
            val tracked = repository.findProduct(product.id)
            val links = tracked?.let(repository::links) ?: (listOf(product) + results.value.flatMap { it.items }
                .filter { ProductMatching.sameVariant(product, it) }).distinctBy { it.url }
            val cached = repository.offers(snapshots.value, product.id)
            detail.value = DetailState(product, links, cached, loading = true, threshold = tracked?.threshold)
            historyJob = viewModelScope.launch { repository.history(product.id).collect { history.value = it } }
            val fetched = repository.fetch(links, product.id)
            detail.value = DetailState(product, links,
                fetched.flatMap { it.items }.ifEmpty { cached },
                fetched.mapNotNull { result -> result.error?.let { "${result.source.label}: $it" } }, threshold = tracked?.threshold)
        }
    }
    fun openTracked(product: TrackedProduct) {
        val link = repository.links(product).firstOrNull() ?: return
        open(link.copy(id = product.id, title = product.title, imageUrl = product.imageUrl))
    }
    fun openId(id: String) { viewModelScope.launch { repository.findProduct(id)?.let(::openTracked) } }
    fun closeDetail() { detailJob?.cancel(); historyJob?.cancel(); detail.value = null }
    fun track() {
        val current = detail.value ?: return
        if (current.product.id.startsWith("demo-")) { message.value = "Aceste produse sunt exemple. Dezactivează modul demonstrativ pentru urmărire."; return }
        viewModelScope.launch {
            runCatching { repository.track(current.product, current.links, current.offers, current.threshold) }
                .onSuccess { message.value = "Produsul este urmărit. Vei primi alerte la ieftinire." }
                .onFailure { message.value = "Produsul nu a putut fi salvat." }
        }
    }
    fun associate(candidate: ProductCandidate) {
        val current = detail.value ?: return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val links = (current.links + candidate).distinctBy { it.url }
            detail.value = current.copy(loading = true, links = links)
            val fetched = repository.fetch(links, current.product.id)
            val updated = current.copy(links = links, loading = false,
                offers = fetched.flatMap { it.items },
                issues = fetched.mapNotNull { it.error })
            detail.value = updated
            if (products.value.any { it.id == current.product.id })
                repository.track(updated.product, links, updated.offers, updated.threshold)
        }
    }
    fun threshold(value: Long?) {
        val current = detail.value ?: return
        detail.value = current.copy(threshold = value)
        viewModelScope.launch { repository.setThreshold(current.product.id, value) }
    }
    fun remove(id: String) { viewModelScope.launch { repository.remove(id); closeDetail(); message.value = "Urmărirea a fost oprită." } }
    fun refresh() { viewModelScope.launch {
        if (settings.value.demo) { message.value = "Previzualizare: prețurile sunt demonstrative."; return@launch }
        runCatching { repository.refreshAll() }.onSuccess {
            message.value = if (products.value.isEmpty()) "Adaugă un produs pentru a începe urmărirea." else "Verificarea s-a încheiat. Vezi starea fiecărui produs."
        }.onFailure { message.value = "Verificarea nu a putut fi finalizată." }
    } }
    fun setTheme(theme: ThemeChoice) { viewModelScope.launch { repository.preferences.setTheme(theme) } }
    fun setInterval(hours: Int) { viewModelScope.launch { repository.preferences.setInterval(hours) } }
    fun setWifiOnly(value: Boolean) { viewModelScope.launch { repository.preferences.setWifiOnly(value) } }
    fun toggleStore(store: Store) { viewModelScope.launch {
        val stores = settings.value.stores.toMutableSet()
        if (store in stores) stores.remove(store) else stores.add(store)
        if (stores.isEmpty()) { message.value = "Păstrează cel puțin o sursă activă."; return@launch }
        repository.preferences.setStores(stores)
    } }
    fun setDemo(value: Boolean) { viewModelScope.launch {
        ++searchGeneration; searchJob?.cancel(); searching.value = false
        cancelHot(); hotCache.clear(); hot.value = HotState()
        closeDetail(); query.value = ""; results.value = emptyList(); searched.value = false
        repository.preferences.setDemo(value)
    } }
    fun readAlert(id: Long, productId: String) { viewModelScope.launch { repository.markRead(id); openId(productId) } }
    fun markAllRead() { viewModelScope.launch { repository.markAllRead() } }
}
