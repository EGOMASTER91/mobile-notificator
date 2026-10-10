package ro.pretradar.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Retail category listings, never a broad keyword search or recommendation feed. */
enum class HotCategory(val label: String, val group: String, val altex: String, val emag: String) {
    PHONES("Telefoane", "Telefoane și IT", "telefoane", "telefoane-mobile"),
    TABLETS("Tablete", "Telefoane și IT", "tablete", "tablete"),
    LAPTOPS("Laptopuri", "Telefoane și IT", "laptopuri", "laptopuri"),
    MONITORS("Monitoare", "Telefoane și IT", "monitoare-pc", "monitoare"),
    TV("Televizoare", "TV, audio și gaming", "televizoare", "televizoare"),
    HEADPHONES("Căști audio", "TV, audio și gaming", "casti-telefon", "casti-audio"),
    CONSOLES("Console gaming", "TV, audio și gaming", "console-jocuri", "console-hardware"),
    FRIDGES("Combine frigorifice", "Electrocasnice", "combine-frigorifice", "combine-frigorifice"),
    WASHERS("Mașini de spălat rufe", "Electrocasnice", "masini-spalat-rufe", "masini-spalat-rufe"),
    VACUUMS("Aspiratoare", "Electrocasnice", "aspiratoare", "aspiratoare"),
    COFFEE("Espressoare", "Electrocasnice", "expresoare-cafea", "espressoare"),
    HAIRDRYERS("Uscătoare de păr", "Îngrijire personală", "uscatoare-par", "uscatoare-par");

    fun url(store: Store): String = when (store) {
        Store.ALTEX -> "https://altex.ro/$altex/cpl/filtru/order/best_deal/dir/desc/"
        Store.EMAG -> "https://www.emag.ro/$emag/sort-discountdesc/c"
        else -> error("Hot este disponibil pentru Altex și eMAG.")
    }
}

data class HotDeal(val product: ProductCandidate, val referencePrice: Long, val category: HotCategory,
    val checkedAt: Long = System.currentTimeMillis()) {
    init { require(product.price != null && product.price > 0 && referencePrice > product.price) }
    // Floor rather than round up: a 29.99% offer must never pass the 30% filter.
    val discountBasisPoints: Int get() = BigDecimal.valueOf(referencePrice - product.price!!)
        .multiply(BigDecimal.valueOf(10_000)).divide(BigDecimal.valueOf(referencePrice), 0, RoundingMode.DOWN).toInt()
    val discountPercent: Int get() = discountBasisPoints / 100
}

object HotDeals {
    fun filter(items: List<HotDeal>, minimum: Int, stores: Set<Store>): List<HotDeal> = items
        .filter { it.product.source in stores && it.discountBasisPoints >= minimum.coerceIn(0, 99) * 100 }
        .distinctBy { it.product.source to it.product.url.ifBlank { it.product.id } }
        .sortedWith(compareByDescending<HotDeal> { it.discountBasisPoints }.thenBy { it.product.price })
}
