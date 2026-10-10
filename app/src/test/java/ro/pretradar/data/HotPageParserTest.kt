package ro.pretradar.data

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import ro.pretradar.domain.*

class HotPageParserTest {
    private fun altex(old: String = "<div class='has-line-through'><span class='Price-int'>1.299</span><sup>,90</sup></div>", stock: String = "in stoc") =
        """<div class='Product'><a href='/telefon-test/cpd/TEST/'><span class='Product-name'>Telefon test 128GB</span></a>
        <div>$stock</div>$old<div class='text-red-brand'><span class='Price-int'>999</span><sup>,90</sup> lei</div>
        <span>-15% extra in app</span></div>"""
    private fun parseAltex(html: String) = HotPageParser.parse(Jsoup.parse(html, "https://altex.ro/telefoane/cpl/"), Store.ALTEX, HotCategory.PHONES)
    @Test fun altexPairedVisiblePricesIgnoreConditionalCoupon() {
        val deal = parseAltex("<ul class='Products'>${altex()}</ul>").single()
        assertEquals(99_990L, deal.product.price); assertEquals(129_990L, deal.referencePrice)
        assertEquals(23, deal.discountPercent)
        assertEquals("https://altex.ro/telefon-test/cpd/TEST/", deal.product.url)
    }
    @Test fun recommendationCardsOutsideCategoryListingAreExcluded() {
        assertTrue(parseAltex(altex()).isEmpty())
    }
    @Test fun recommendedRetailPriceAndCouponOnlyAreExcluded() {
        for (old in listOf("<div>PRP: 1.299,90 lei</div>", "<div>-70% cupon</div>",
            "<div class='has-line-through'>PRP: 1.299,90 lei</div>"))
            assertTrue(parseAltex("<ul class='Products'>${altex(old)}</ul>").isEmpty())
    }
    @Test fun unavailableAndUnknownStockAreExcluded() {
        for (stock in listOf("Stoc epuizat", "Indisponibil", "Precomandă", ""))
            assertTrue(parseAltex("<ul class='Products'>${altex(stock = stock)}</ul>").isEmpty())
    }
    @Test fun emagSupDecimalsAndHydratedReferenceAreParsed() {
        val html = """<div class='card-item' data-product-id='123'><a class='card-v2-title' href='/telefon/pd/TEST/'>Telefon test</a>
            În stoc <span class='rrp-lp30d-content'><s>1.999<sup><small>,</small>99</sup> Lei</s></span>
            <p class='product-new-price'>999<sup><small>,</small>99</sup> Lei</p></div>"""
        val deal = HotPageParser.parse(Jsoup.parse(html, "https://www.emag.ro/telefoane-mobile/c"), Store.EMAG, HotCategory.PHONES).single()
        assertEquals(99_999L, deal.product.price); assertEquals(199_999L, deal.referencePrice)
        assertEquals(50, deal.discountPercent)
    }
    @Test fun noReferenceNeverInfersDiscountFromBadgeOrAggregateOffer() {
        val html = """<div class='card-item' data-product-id='123'><a class='card-v2-title' href='/telefon/pd/TEST/'>Telefon test</a>
            În stoc <span>-90%</span><span class='rrp-lp30d-content'></span><p class='product-new-price'>999,99 Lei</p></div>
            <script type='application/ld+json'>{"@type":"Product","offers":{"@type":"AggregateOffer","lowPrice":"999","highPrice":"1999"}}</script>"""
        assertTrue(HotPageParser.parse(Jsoup.parse(html, "https://www.emag.ro/"), Store.EMAG, HotCategory.PHONES).isEmpty())
    }
    @Test fun wrongSourceLinkIsExcluded() {
        assertTrue(parseAltex("<ul class='Products'>${altex().replace("/telefon-test/cpd/TEST/", "https://wrong.example/cpd/TEST/")}</ul>").isEmpty())
    }
    @Test fun mixedCurrencyOrTwoReferencePricesAreExcluded() {
        assertTrue(parseAltex("<ul class='Products'>${altex().replace("lei", "EUR")}</ul>").isEmpty())
        val old = "<div class='has-line-through'>1299,90 lei sau 1199,90 lei</div>"
        assertTrue(parseAltex("<ul class='Products'>${altex(old)}</ul>").isEmpty())
    }
    @Test fun recommendedPriceLabelOutsideThePriceSpanIsExcluded() {
        val html = """<div class='card-item' data-product-id='123'><a class='card-v2-title' href='/telefon/pd/TEST/'>Telefon test</a>
            În stoc <p>PRP: <span class='rrp-lp30d-content'><s>1.999,99 Lei</s></span></p>
            <p class='product-new-price'>999,99 Lei</p></div>"""
        assertTrue(HotPageParser.parse(Jsoup.parse(html, "https://www.emag.ro/"), Store.EMAG, HotCategory.PHONES).isEmpty())
    }
}
