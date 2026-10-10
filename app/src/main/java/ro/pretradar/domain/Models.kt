package ro.pretradar.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

enum class Store(val label: String, val host: String) {
    ALTEX("Altex", "altex.ro"), EMAG("eMAG", "emag.ro"), COMPARI("Compari.ro", "compari.ro"),
    FORIT("ForIT", "forit.ro"), ITGALAXY("ITGalaxy", "itgalaxy.ro"), EVOMAG("evoMAG", "evomag.ro");

    val directPartner: Boolean get() = this in setOf(FORIT, ITGALAXY, EVOMAG)
    fun searchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(ProductSearch.retailerQuery(query), "UTF-8")
        return when (this) {
            ALTEX -> "https://altex.ro/cauta/?q=$encoded"
            EMAG -> "https://www.emag.ro/search/$encoded"
            COMPARI -> "https://www.compari.ro/CategorySearch.php?st=$encoded"
            FORIT -> "https://www.forit.ro/cauta/?q=$encoded"
            ITGALAXY -> "https://www.itgalaxy.ro/cauta/?search=$encoded"
            EVOMAG -> "https://www.evomag.ro/?sn.q=$encoded"
        }
    }

    companion object { val defaults: Set<Store> = entries.filter { it != COMPARI }.toSet() }
}
enum class ThemeChoice { SYSTEM, LIGHT, DARK }
data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val intervalHours: Int = 6,
    val stores: Set<Store> = Store.defaults,
    val demo: Boolean = false,
    val wifiOnly: Boolean = false,
    val hotMinimum: Int = 30,
    val hotCategory: HotCategory = HotCategory.PHONES
)
data class ProductCandidate(
    val id: String,
    val title: String,
    val url: String,
    val source: Store,
    val imageUrl: String = "",
    val ean: String = "",
    val model: String = "",
    val price: Long? = null
)
data class Promotion(val title: String, val code: String = "", val conditions: String = "") {
    val key: String get() = normalize("$title|$code|$conditions")
}
data class Offer(
    val productId: String,
    val store: String,
    val seller: String,
    val url: String,
    val price: Long,
    val inStock: Boolean,
    val shipping: Long? = null,
    val promotion: Promotion? = null,
    val checkedAt: Long = System.currentTimeMillis(),
    val stockDescription: String? = null
) {
    val stockLabel: String get() = stockDescription ?: if (inStock) "În stoc" else "Stoc neconfirmat / indisponibil"
    val merchantKey: String get() = when (val name = normalize(store)) {
        "altex", "altex ro", "www altex ro" -> "altex"
        "emag", "emag ro", "www emag ro" -> "emag"
        "forit", "forit ro", "www forit ro" -> "forit"
        "itgalaxy", "itgalaxy ro", "www itgalaxy ro" -> "itgalaxy"
        "evomag", "evomag ro", "www evomag ro" -> "evomag"
        else -> name
    }
}
data class SourceResult<T>(val source: Store, val items: List<T>, val error: String? = null)
data class WatchRule(val stores: Set<Store> = Store.entries.toSet(), val threshold: Long? = null)
data class PricePoint(val time: Long, val price: Long, val store: String)
data class AlertEvent(
    val productId: String,
    val title: String,
    val store: String,
    val oldPrice: Long?,
    val newPrice: Long,
    val promotion: Promotion? = null,
    val createdAt: Long = System.currentTimeMillis()
)

fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

fun money(cents: Long): String {
    val format = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("ro-RO"))
    format.minimumFractionDigits = if (cents % 100 == 0L) 0 else 2
    format.maximumFractionDigits = 2
    return "${format.format(BigDecimal.valueOf(cents, 2))} lei"
}

/** Parse Romanian display prices; never interpret thousands as decimal separators. */
fun parsePrice(text: String): Long? {
    val cleaned = text.replace(Regex("(?i)(lei|ron|\\s|\u00a0|\u202f)"), "")
        .replace(Regex("[^0-9.,]"), "")
    if (cleaned.isBlank()) return null
    val canonical = when {
        ',' in cleaned -> cleaned.replace(".", "").replace(',', '.')
        cleaned.count { it == '.' } > 1 -> cleaned.replace(".", "")
        '.' in cleaned && cleaned.substringAfterLast('.').length == 3 -> cleaned.replace(".", "")
        else -> cleaned
    }
    return runCatching { BigDecimal(canonical).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact() }
        .getOrNull()?.takeIf { it > 0 }
}

object ProductMatching {
    /** Conservative: no merge based on a broad search query or shared brand. */
    fun sameVariant(a: ProductCandidate, b: ProductCandidate): Boolean {
        if (a.ean.isNotBlank() && b.ean.isNotBlank()) return a.ean == b.ean
        if (a.model.isNotBlank() && b.model.isNotBlank()) {
            return normalize(a.model) == normalize(b.model) && variantTokens(a.title) == variantTokens(b.title)
        }
        return normalize(a.title) == normalize(b.title)
    }
    private fun variantTokens(title: String): Set<String> {
        val normalized = normalize(title)
        return Regex("\\b\\d+\\s*(gb|tb|inch|cm|mm)\\b|\\b(negru|alb|argintiu|albastru|verde|black|white|silver|blue|green)\\b")
            .findAll(normalized).map { it.value.replace(" ", "") }.toSet()
    }
}

object AlertEngine {
    /** Compare store minima only across successful, available observations. */
    fun detect(previous: Offer?, current: Offer, rule: WatchRule): AlertEvent? {
        if (!current.inStock || previous == null || !previous.inStock) return null
        if (rule.threshold != null && current.price > rule.threshold) return null
        val cheaper = current.price < previous.price
        val newPromotion = current.promotion != null && current.promotion.key != previous.promotion?.key
        if (!cheaper && !newPromotion) return null
        return AlertEvent(current.productId, "", current.store, previous.price, current.price,
            if (newPromotion) current.promotion else null, current.checkedAt)
    }
    fun minima(offers: List<Offer>): List<Offer> = offers.filter { it.inStock && it.price > 0 }
        .groupBy { it.merchantKey }.values.map { group -> group.minBy { it.price } }.sortedBy { it.price }
}
