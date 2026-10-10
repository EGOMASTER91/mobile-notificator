package ro.pretradar.domain

import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun parsesRomanianPricesWithoutLosingCents() {
        assertEquals(129999L, parsePrice("1.299,99 lei"))
        assertEquals(129900L, parsePrice("1 299,00 RON"))
        assertEquals(129900L, parsePrice("1.299 lei"))
        assertEquals(129999L, parsePrice("1299.99"))
        assertEquals(100000000L, parsePrice("1.000.000"))
        assertNull(parsePrice("indisponibil")); assertNull(parsePrice("0 lei"))
    }
    @Test fun variantsAreNotMergedBySharedBrand() {
        val a = ProductCandidate("a", "Samsung Galaxy S24 128 GB Negru", "", Store.ALTEX, model = "SM-S921")
        assertFalse(ProductMatching.sameVariant(a, a.copy(id = "b", title = "Samsung Galaxy S24 256 GB Negru", source = Store.EMAG)))
        assertFalse(ProductMatching.sameVariant(a, a.copy(title = "Samsung Galaxy S24 128 GB Alb")))
        assertTrue(ProductMatching.sameVariant(a, a.copy(source = Store.EMAG)))
        assertFalse(ProductMatching.sameVariant(a.copy(ean = "123"), a.copy(ean = "456")))
    }
    @Test fun alertsUseObservedPricesAndNewPromotionEpisodes() {
        val original = Offer("p", "Altex", "Altex", "", 129900, true)
        val lower = original.copy(price = 109900)
        assertNull(AlertEngine.detect(null, lower, WatchRule()))
        assertNotNull(AlertEngine.detect(original, lower, WatchRule()))
        assertNull(AlertEngine.detect(lower, lower, WatchRule()))
        assertNull(AlertEngine.detect(lower, original, WatchRule()))
        assertNull(AlertEngine.detect(original, lower.copy(inStock = false), WatchRule()))
        assertNull(AlertEngine.detect(original, lower, WatchRule(threshold = 100000)))
        val coupon = lower.copy(promotion = Promotion("Cupon", "SAVE10", "Aplică în coș"))
        assertNotNull(AlertEngine.detect(lower, coupon, WatchRule()))
        assertNull(AlertEngine.detect(coupon, coupon, WatchRule()))
        assertNotNull(AlertEngine.detect(coupon, coupon.copy(promotion = Promotion("Cupon", "SAVE20")), WatchRule()))
    }
    @Test fun minimaAreCalculatedForEachMerchantAndOnlyAvailableOffers() {
        val offers = listOf(Offer("p", "eMAG", "Seller A", "", 110000, true), Offer("p", "eMAG.ro", "Seller B", "", 99000, true),
            Offer("p", "Altex", "Altex", "", 98000, false), Offer("p", "Altex", "Altex", "", 119000, true))
        val minimum = AlertEngine.minima(offers)
        assertEquals(2, minimum.size); assertEquals("Seller B", minimum.first().seller)
        assertEquals(119000L, minimum.last().price)
    }
}
