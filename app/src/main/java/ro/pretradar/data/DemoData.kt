package ro.pretradar.data

import ro.pretradar.domain.*

/** Preview-only examples. Never inserted in the real watch database or scheduled for checks. */
object DemoData {
    fun hot(category: HotCategory): List<HotDeal> {
        val candidate = when (category) {
            HotCategory.PHONES -> candidates[1]
            HotCategory.HEADPHONES -> candidates[0]
            HotCategory.VACUUMS, HotCategory.COFFEE -> candidates[2].copy(source = Store.ALTEX,
                title = if (category == HotCategory.VACUUMS) "Aspirator · exemplu" else "Espressor · exemplu")
            else -> ProductCandidate("demo-hot-${category.name}", "${category.label} · exemplu", "", Store.ALTEX, price = 49900)
        }
        val current = candidate.price!!
        return listOf(HotDeal(candidate, current * 2, category),
            HotDeal(candidate.copy(id = candidate.id + "-second", title = candidate.title + " · oferta 2", source = Store.EMAG), current * 5 / 4, category))
    }
    val candidates = listOf(
        ProductCandidate("demo-sony", "Sony WH-1000XM5", "", Store.ALTEX, imageUrl = "demo:headphones", model = "WH-1000XM5", price = 109900),
        ProductCandidate("demo-samsung", "Samsung Galaxy S24", "", Store.EMAG, imageUrl = "demo:phone", model = "S24", price = 249900),
        ProductCandidate("demo-philips", "Philips Airfryer", "", Store.COMPARI, imageUrl = "demo:airfryer", model = "Airfryer", price = 54900)
    )
    fun offers(id: String): List<Offer> = when (id) {
        "demo-sony" -> listOf(Offer(id, "Altex", "Altex", "", 109900, true), Offer(id, "eMAG", "eMAG", "", 119900, true), Offer(id, "Magazin partener", "Magazin partener", "", 114900, true))
        "demo-samsung" -> listOf(Offer(id, "eMAG", "eMAG", "", 249900, true), Offer(id, "Altex", "Altex", "", 259900, true))
        else -> listOf(Offer(id, "Magazin partener", "Magazin partener", "", 54900, true, promotion = Promotion("Cupon disponibil", "EXEMPLU10", "Cod demonstrativ, fără valoare comercială.")))
    }
    fun history(id: String): List<PricePoint> {
        val prices = when (id) {
            "demo-sony" -> listOf(129900L, 126900L, 124900L, 119900L, 121900L, 114900L, 109900L)
            "demo-samsung" -> listOf(249900L, 251900L, 249900L, 250900L, 248900L, 249900L, 249900L)
            else -> listOf(55900L, 57900L, 54900L, 53900L, 54900L, 56900L, 54900L)
        }
        return prices.mapIndexed { i, p -> PricePoint(System.currentTimeMillis() - (6 - i) * 86400000L, p, offers(id).first().store) }
    }
}
