package ro.pretradar.domain

import org.junit.Assert.*
import org.junit.Test

class HotDealsTest {
    private fun deal(id: String, price: Long, old: Long, store: Store = Store.ALTEX) = HotDeal(
        ProductCandidate(id, id, "https://${store.host}/$id", store, price = price), old, HotCategory.PHONES)
    @Test fun greatestPercentageFirstRatherThanLargestSavingInLei() {
        val items = listOf(deal("expensive", 500_000, 600_000), deal("small", 5_000, 10_000))
        assertEquals(listOf("small", "expensive"), HotDeals.filter(items, 0, Store.defaults).map { it.product.id })
    }
    @Test fun thresholdNeverRoundsADealUp() {
        val items = listOf(deal("below", 7_001, 10_000), deal("exact", 7_000, 10_000))
        assertEquals(29, items.first().discountPercent)
        assertEquals(listOf("exact"), HotDeals.filter(items, 30, Store.defaults).map { it.product.id })
    }
    @Test fun sourceFilterAndDuplicateRemoval() {
        val a = deal("a", 50, 100); val b = deal("b", 20, 100, Store.EMAG)
        assertEquals(listOf(a), HotDeals.filter(listOf(a, a, b), 30, setOf(Store.ALTEX)))
    }
    @Test fun invalidOrMissingPricesAreRejected() {
        for ((price, old) in listOf(0L to 100L, 100L to 100L, 110L to 100L))
            assertThrows(IllegalArgumentException::class.java) { deal("invalid", price, old) }
        assertThrows(IllegalArgumentException::class.java) {
            HotDeal(ProductCandidate("x", "x", "", Store.ALTEX), 100, HotCategory.PHONES)
        }
    }
    @Test fun priceArithmeticCannotOverflow() {
        assertEquals(99, deal("large", 1, Long.MAX_VALUE).discountPercent)
    }
}
