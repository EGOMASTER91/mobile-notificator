package ro.pretradar.data

import com.google.gson.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import ro.pretradar.domain.*
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

interface StoreSource {
    val store: Store
    suspend fun search(query: String): SourceResult<ProductCandidate>
    suspend fun offers(product: ProductCandidate): SourceResult<Offer>
    suspend fun hot(category: HotCategory): SourceResult<HotDeal> = SourceResult(store, emptyList(), "Catalog Hot indisponibil.")
}

class HtmlStoreSource(override val store: Store, private val pages: PublicStoreHttp, private val renderer: PublicPageRenderer? = null) : StoreSource {
    override suspend fun hot(category: HotCategory): SourceResult<HotDeal> = safely {
        val url = category.url(store)
        var doc = fetch(url)
        if (store == Store.EMAG && doc.selectFirst(".rrp-lp30d-content") != null &&
            doc.select(".rrp-lp30d-content").all { it.text().isBlank() } && renderer != null) {
            doc = renderer.hot(url, store)
        }
        if (doc.select(if (store == Store.ALTEX) ".Products .Product" else ".card-item[data-product-id]").isEmpty())
            throw IOException("Magazinul nu a furnizat o listă publică de produse verificabile.")
        HotPageParser.parse(doc, store, category)
    }
    override suspend fun search(query: String): SourceResult<ProductCandidate> = safely {
        val url = store.searchUrl(query)
        val document = fetch(url)
        val parsed = PublicPageParser.search(document, store)
        if (parsed.isEmpty() && renderer != null && (store == Store.EVOMAG ||
            (store == Store.ALTEX && document.selectFirst("script#__NEXT_DATA__") != null))) {
            PublicPageParser.search(renderer.search(url, store), store)
        } else parsed
    }

    override suspend fun offers(product: ProductCandidate): SourceResult<Offer> = safely {
        require(isSourceUrl(product.url, store)) { "Adresa produsului nu aparține sursei selectate." }
        PublicPageParser.offers(fetch(product.url), product).also {
            if (it.isEmpty()) throw IOException("Prețurile nu au putut fi verificate pe această pagină.")
        }
    }

    private suspend fun fetch(url: String): Document = pages.fetch(url, store)
    private suspend fun <T> safely(block: suspend () -> List<T>): SourceResult<T> = try {
        SourceResult(store, block())
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) { SourceResult(store, emptyList(), e.message ?: "Sursa nu poate fi verificată acum.") }
}

fun isSourceUrl(url: String, store: Store): Boolean = RemoteUrls.httpsUri(url)?.let { uri ->
    val host = uri.host.lowercase()
    host == store.host || host.endsWith(".${store.host}")
} ?: false

object PublicPageParser {
    fun search(document: Document, store: Store): List<ProductCandidate> {
        val structured = products(document).mapNotNull { node -> candidate(node, document, store) }
        val retailTitles = when (store) {
            Store.EMAG -> document.select("a.card-v2-title, a.product-title")
            Store.FORIT -> document.select(".product-grid .p-title a")
            Store.ITGALAXY -> document.select("#products-list h2.name a")
            Store.EVOMAG -> document.select(".searchnode .npi_name a")
            else -> emptyList()
        }
        // These sources must provide actual result cards, never homepage recommendations.
        if (store.directPartner && retailTitles.isEmpty()) return structured
        val anchors = (retailTitles.ifEmpty { document.select("a[href]") }).filter { a ->
            val path = a.attr("href")
            when (store) {
                Store.ALTEX -> "/cpd/" in path
                Store.EMAG -> "/pd/" in path
                Store.COMPARI -> Regex("-p\\d+/?(?:\\?.*)?$").containsMatchIn(path)
                Store.FORIT -> Regex("-bp\\d+$").containsMatchIn(path)
                Store.ITGALAXY -> Regex("-\\d+/$").containsMatchIn(path)
                Store.EVOMAG -> Regex("-\\d+\\.html$").containsMatchIn(path)
            }
        }.mapNotNull { a ->
            val url = a.absUrl("href").substringBefore('#').substringBefore('?')
            if (!isSourceUrl(url, store)) return@mapNotNull null
            val name = (if (store.directPartner) a.text() else a.attr("title")).ifBlank { a.selectFirst("h2,h3,[itemprop=name]")?.text().orEmpty() }
                .ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }.ifBlank { a.text() }.trim()
            if (name.length !in 12..300 || normalize(name) in setOf("vezi oferta", "vezi ofertele", "compara preturi", "super pret", "top favorite")) return@mapNotNull null
            val card = a.parents().firstOrNull { it.hasClass("card-v2") || it.hasClass("card-item") || it.hasClass("product-box") || it.hasClass("product") || it.hasClass("p-card") || it.hasClass("nice_product_item") || it.hasAttr("data-product-id") }
            ProductCandidate(stableId(url), name, url, store,
                imageUrl = (a.selectFirst("img") ?: card?.selectFirst("img"))?.let { image(it) }.orEmpty(),
                price = card?.let { priceElement(it) })
        }
        // Keep the complete ordinary result page before strict filtering in the repository.
        return (structured + anchors).distinctBy { it.url }.take(120)
    }

    fun offers(document: Document, product: ProductCandidate): List<Offer> {
        if (product.source == Store.EVOMAG) return evoMagOffer(document, product)
        val items = products(document).filter { node ->
            val url = node.string("url")
            url.isBlank() || resolve(document, url).substringBefore('?').trimEnd('/') == product.url.substringBefore('?').trimEnd('/')
        }.flatMap { node ->
            val offers = node.get("offers") ?: return@flatMap emptyList()
            objects(offers).flatMap { offer ->
                // AggregateOffer.lowPrice is not a verified individual merchant offer.
                if (offer.string("@type") == "AggregateOffer") {
                    offer.get("offers")?.let { nested -> objects(nested).mapNotNull { offer(it, product, document) } } ?: emptyList()
                } else listOfNotNull(offer(offer, product, document))
            }
        }
        if (product.source == Store.COMPARI) {
            val rows = document.select(".offer-container, .offer-row, .offer-box, [itemprop=offers]")
                .mapNotNull { row ->
                    val merchant = row.selectFirst(".shopname, .shop-name, .shop-link, [itemprop=seller]")?.text()?.trim().orEmpty()
                    val price = priceElement(row) ?: return@mapNotNull null
                    if (merchant.isBlank()) return@mapNotNull null
                    val link = row.selectFirst("a.offer-link, a.shop-link, a[href*=redirect], a[href*=click]")?.absUrl("href").orEmpty()
                    val stock = normalize(row.text())
                    if (!stock.contains("in stoc") && !stock.contains("disponibil")) return@mapNotNull null
                    Offer(product.id, merchant, merchant, link.takeIf { RemoteUrls.httpsUri(it) != null } ?: product.url, price, true,
                        promotion = promotion(row))
                }
            return (items + rows).distinctBy { "${it.merchantKey}|${it.seller}|${it.price}" }
        }
        if (items.isNotEmpty()) return items.distinctBy { "${it.seller}|${it.price}" }.map { offer ->
            val stock = when (product.source) {
                Store.FORIT -> document.selectFirst(".stock-row")?.text()
                Store.ITGALAXY -> document.selectFirst(".product-availability")?.text()
                Store.EVOMAG -> document.selectFirst(".product_body .stoc_produs, .produs_body .stoc_produs, .produs_stoc .stoc_produs")?.text()
                else -> null
            }?.trim()?.takeIf { it.isNotBlank() }
            val unavailable = stock?.let(::normalize)?.let { it.startsWith("nu ") || "indisponibil" in it || "stoc epuizat" in it } ?: false
            offer.copy(stockDescription = stock, inStock = offer.inStock && !unavailable,
                promotion = productPromotion(document, product.source))
        }
        val price = document.selectFirst("meta[property=product:price:amount], meta[itemprop=price]")
            ?.attr("content")?.let(::parsePrice)
        val currency = document.selectFirst("meta[property=product:price:currency], [itemprop=priceCurrency]")
            ?.let(::microValue)
        val availability = document.selectFirst("link[itemprop=availability], meta[itemprop=availability]")
            ?.let { it.attr("href").ifBlank { it.attr("content") } }.orEmpty()
        if (currency != "RON" || price == null || !availability.endsWith("InStock")) return emptyList()
        return listOf(Offer(product.id, product.source.label,
            if (product.source == Store.EMAG) "Vânzător nespecificat" else product.source.label,
            product.url, price, true))
    }

    private fun offer(node: JsonObject, product: ProductCandidate, doc: Document): Offer? {
        if (node.string("priceCurrency").uppercase() != "RON") return null
        val price = parsePrice(node.string("price")) ?: return null
        val sellerNode = node.get("seller")
        val seller = if (sellerNode?.isJsonObject == true) sellerNode.asJsonObject.string("name") else ""
        if (product.source == Store.COMPARI && seller.isBlank()) return null
        val availability = node.string("availability")
        // Unknown availability is displayed, but never treated as an available alert candidate.
        val inStock = availability.substringAfterLast('/') in setOf("InStock", "LimitedAvailability")
        return Offer(product.id, if (product.source == Store.COMPARI) seller else product.source.label,
            seller.ifBlank { if (product.source == Store.EMAG) "Vânzător nespecificat" else product.source.label },
            resolve(doc, node.string("url")).takeIf { isSourceUrl(it, product.source) } ?: product.url, price, inStock)
    }
    private fun evoMagOffer(doc: Document, product: ProductCandidate): List<Offer> {
        val item = doc.select("[itemscope][itemtype$=/Product]").firstOrNull {
            val url = it.selectFirst("[itemprop=url]")?.let(::microValue).orEmpty()
            resolve(doc, url).trimEnd('/') == product.url.trimEnd('/')
        } ?: return emptyList()
        val offer = item.selectFirst("[itemprop=offers]") ?: return emptyList()
        if (offer.selectFirst("[itemprop=priceCurrency]")?.let(::microValue) != "RON") return emptyList()
        val price = offer.selectFirst("[itemprop=price]")?.let(::microValue)?.let(::parsePrice) ?: return emptyList()
        // evoMAG's microdata contains an old validity date. Only accept it when
        // corroborated by the current visible price and stock widget for this page.
        val visiblePrices = doc.select(".product_right_inside .pret_rons").mapNotNull(::priceElementValue).distinct()
        if (visiblePrices != listOf(price)) return emptyList()
        val stock = doc.selectFirst(".product_right_inside #stockWidgetView")?.text()?.trim() ?: return emptyList()
        val label = normalize(stock)
        val available = label.startsWith("in stoc") && !label.contains("nu ")
        return listOf(Offer(product.id, Store.EVOMAG.label, Store.EVOMAG.label, product.url, price, available,
            stockDescription = stock))
    }
    private fun microValue(element: Element): String = element.attr("content").ifBlank { element.attr("href") }.ifBlank { element.text() }
    private fun candidate(node: JsonObject, doc: Document, store: Store): ProductCandidate? {
        val title = node.string("name")
        val url = resolve(doc, node.string("url"))
        if (title.isBlank() || !isSourceUrl(url, store)) return null
        val img = node.get("image")?.let { when {
            it.isJsonPrimitive -> it.asString
            it.isJsonArray -> it.asJsonArray.firstOrNull()?.takeIf { v -> v.isJsonPrimitive }?.asString
            it.isJsonObject -> it.asJsonObject.string("url")
            else -> null
        } }.orEmpty()
        val priceNode = node.get("offers")?.let(::objects)?.firstOrNull()
        return ProductCandidate(stableId(url), title, url, store, RemoteUrls.image(resolve(doc, img)),
            node.string("gtin13").ifBlank { node.string("gtin") },
            node.string("mpn"), priceNode?.takeIf { it.string("priceCurrency").uppercase() == "RON" }
                ?.let { parsePrice(it.string("price").ifBlank { it.string("lowPrice") }) })
    }
    private fun products(document: Document): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        fun visit(value: JsonElement) {
            when {
                value.isJsonArray -> value.asJsonArray.forEach(::visit)
                value.isJsonObject -> {
                    val obj = value.asJsonObject
                    val type = obj.get("@type")
                    if (type?.toString()?.contains("Product") == true) result += obj
                    obj.entrySet().forEach { visit(it.value) }
                }
            }
        }
        document.select("script[type=application/ld+json]").forEach { script ->
            runCatching { visit(JsonParser.parseString(script.data())) }
        }
        return result
    }
    private fun promotion(row: Element): Promotion? {
        val text = row.selectFirst(".coupon, .coupon-code, [data-coupon-code]") ?: return null
        val code = text.attr("data-coupon-code").ifBlank { text.selectFirst(".code")?.text().orEmpty() }
        if (code.isBlank()) return null
        return Promotion("Cupon disponibil", code, text.text())
    }
    private fun productPromotion(document: Document, store: Store): Promotion? {
        if (store == Store.ALTEX) {
            val heading = document.select("h2").firstOrNull { normalize(it.text()) == "extra beneficii" }
            val container = heading?.parent()?.parent()?.parent()
            val labels = container?.select("li span.text-12px")?.map { it.text().trim() }
                ?.filter { it.length in 5..180 }?.distinct().orEmpty()
            if (labels.isNotEmpty()) return Promotion(labels.joinToString(" · "), conditions =
                "Beneficiu afișat de Altex. Verifică eligibilitatea și condițiile la magazin; prețul de mai sus nu include reducerea condiționată.")
        }
        if (store == Store.ITGALAXY) {
            val paragraph = document.select("p").firstOrNull {
                normalize(it.text()).startsWith("promotie valabila in perioada") && it.selectFirst("a[href*=/promotii/campanie/]") != null
            }
            if (paragraph != null) return Promotion("Promoție ITGalaxy", conditions = paragraph.text())
        }
        return null
    }
    private fun priceElement(row: Element): Long? {
        val element = row.selectFirst(".product-new-price, .p-price, .real_price, span[id^=price-], .price, .offer-price, [itemprop=price]") ?: return null
        return priceElementValue(element)
    }
    private fun priceElementValue(element: Element): Long? {
        if (element.hasAttr("content")) return parsePrice(element.attr("content"))
        val sup = element.selectFirst("sup")?.text()?.filter { it.isDigit() }
        if (sup != null) return parsePrice(element.clone().also { it.select("sup").remove() }.text() + "," + sup)
        return parsePrice(element.text())
    }
    private fun image(element: Element): String = RemoteUrls.image(element.absUrl("src").ifBlank { element.absUrl("data-src") })
    private fun objects(node: JsonElement): List<JsonObject> = when {
        node.isJsonArray -> node.asJsonArray.filter { it.isJsonObject }.map { it.asJsonObject }
        node.isJsonObject -> listOf(node.asJsonObject)
        else -> emptyList()
    }
    private fun JsonObject.string(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun resolve(doc: Document, url: String): String = if (url.isBlank()) "" else runCatching { URI(doc.baseUri()).resolve(url).toString() }.getOrDefault("")
    fun stableId(url: String): String = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}

fun createSources(context: android.content.Context): List<StoreSource> {
    val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .cache(okhttp3.Cache(java.io.File(context.cacheDir, "store-pages"), 20L * 1024 * 1024))
        .followRedirects(false).followSslRedirects(false).build()
    val pages = PublicStoreHttp(http, context)
    val renderer = PublicPageRenderer(context, pages::pause)
    return Store.entries.map { HtmlStoreSource(it, pages, renderer) }
}
