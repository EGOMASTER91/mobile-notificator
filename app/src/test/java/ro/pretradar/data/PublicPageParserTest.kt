package ro.pretradar.data

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import ro.pretradar.domain.*

/** Synthetic contract fixtures; these do not certify current live retailer markup. */
class PublicPageParserTest {
    private val product = ProductCandidate("p", "Căști Sony", "https://altex.ro/casti/cpd/ABC/", Store.ALTEX)
    @Test fun maliciousImagesAndOfferLinksFromStoreHtmlAreNotTrusted() {
        val doc = Jsoup.parse("""<script type="application/ld+json">{"@type":"Product","name":"Casti Sony",
            "url":"${product.url}","image":"file:///data/data/ro.pretradar/private.png","offers":{"@type":"Offer",
            "url":"https://phishing.example/buy","price":"99","priceCurrency":"RON","availability":"https://schema.org/InStock"}}</script>""", product.url)
        assertEquals("", PublicPageParser.search(doc, Store.ALTEX).single().imageUrl)
        assertEquals(product.url, PublicPageParser.offers(doc, product).single().url)
    }
    @Test fun aRequestedProductAfterTheFirstFortyCardsIsStillAvailableForStrictFiltering() {
        val cards = (1..60).joinToString("") { index ->
            val title = if (index == 51) "Ventilator ARCTIC AC BioniX P120 ARGB" else "Ventilator ARCTIC P140 $index"
            """<div class="p-card"><div class="p-title"><a href="/ventilator-bp$index">$title</a></div></div>"""
        }
        val document = Jsoup.parse("<div class=\"product-grid\">$cards</div>", Store.FORIT.searchUrl("arctic"))
        val candidates = PublicPageParser.search(document, Store.FORIT)
        assertEquals(60, candidates.size)
        assertEquals("Ventilator ARCTIC AC BioniX P120 ARGB",
            candidates.filter(ProductSearch("arctic bionix p120 a-rgb")::matches).single().title)
    }
    @Test fun readsStructuredOfferButRejectsWrongCurrencyAndUnknownStock() {
        fun document(currency: String, stock: String) = Jsoup.parse("""
            <script type="application/ld+json">{"@context":"https://schema.org","@type":"Product","name":"Căști Sony",
            "url":"${product.url}","mpn":"WH-1000XM5","gtin13":"4548736132580","offers":{"@type":"Offer",
            "price":"1099.99","priceCurrency":"$currency","availability":"https://schema.org/$stock"}}</script>
        """, product.url)
        val result = PublicPageParser.offers(document("RON", "InStock"), product).single()
        assertEquals(109999L, result.price); assertTrue(result.inStock)
        assertTrue(PublicPageParser.offers(document("EUR", "InStock"), product).isEmpty())
        assertFalse(PublicPageParser.offers(document("RON", ""), product).single().inStock)
        assertEquals("4548736132580", PublicPageParser.search(document("RON", "InStock"), Store.ALTEX).single().ean)
    }
    @Test fun rejectsAggregateComparatorPriceWithoutMerchant() {
        val candidate = product.copy(source = Store.COMPARI, url = "https://www.compari.ro/casti-p123/")
        val doc = Jsoup.parse("""<script type="application/ld+json">{"@type":"Product","name":"Căști Sony",
            "offers":{"@type":"AggregateOffer","lowPrice":"1099","priceCurrency":"RON"}}</script>""", candidate.url)
        assertTrue(PublicPageParser.offers(doc, candidate).isEmpty())
    }
    @Test fun comparatorKeepsMerchantsSeparateAndReadsExplicitCoupon() {
        val candidate = product.copy(source = Store.COMPARI, url = "https://www.compari.ro/casti-p123/")
        val doc = Jsoup.parse("""<div class="offer-container"><span class="shopname">Magazin A</span>
            <span class="price">1.099,90 RON</span><span>În stoc</span><a class="offer-link" href="https://shop.example/product">Oferta</a>
            <span class="coupon" data-coupon-code="SAVE10">Cod SAVE10, aplică în coș</span></div>
            <div class="offer-container"><span class="shopname">Magazin B</span><span class="price">1.199 lei</span><span>În stoc</span></div>""", candidate.url)
        val offers = PublicPageParser.offers(doc, candidate)
        assertEquals(2, offers.size); assertEquals("SAVE10", offers.first().promotion?.code)
        assertEquals(109990L, offers.first().price)
    }
    @Test fun rejectsExternalOrInsecureProductUrls() {
        assertFalse(isSourceUrl("http://altex.ro/cpd/ABC/", Store.ALTEX))
        assertFalse(isSourceUrl("https://altex.ro.evil.example/cpd/ABC/", Store.ALTEX))
        assertFalse(isSourceUrl("https://user:password@altex.ro/cpd/ABC/", Store.ALTEX))
        assertTrue(isSourceUrl("https://www.emag.ro/produs/pd/ABC/", Store.EMAG))
    }
    @Test fun listingSuperscriptPreservesDecimals() {
        val doc = Jsoup.parse("""<div class="card-item"><a href="https://www.emag.ro/sony/pd/ABC/" title="Sony WH-1000XM5">Sony WH-1000XM5</a>
            <span class="product-new-price">1.099<sup>99</sup> Lei</span></div>""", "https://www.emag.ro/search/sony")
        assertEquals(109999L, PublicPageParser.search(doc, Store.EMAG).single().price)
    }
    @Test fun currentEmagCardSelectsTitleInsteadOfCampaignAndParsesDecimalComma() {
        val doc = Jsoup.parse("""<div class="card-v2"><a href="https://www.emag.ro/sony/pd/ABC/">Super Pret</a>
            <a class="card-v2-title" href="https://www.emag.ro/sony/pd/ABC/?ref=search">Căști Sony WH-1000XM5</a>
            <p class="product-new-price">1&#46;249<sup><small>&#44;</small>99</sup> Lei</p></div>""", "https://www.emag.ro/search/sony")
        val product = PublicPageParser.search(doc, Store.EMAG).single()
        assertEquals("Căști Sony WH-1000XM5", product.title)
        assertEquals(124999L, product.price)
        assertFalse(product.url.contains("?"))
    }
    @Test fun directPartnerSearchDoesNotReturnHomeRecommendations() {
        val home = Jsoup.parse("""<div class="nice_product_item"><div class="npi_name"><a href="/sony-headphones-123.html">Căști Sony recomandate</a></div></div>""", "https://www.evomag.ro/")
        assertTrue(PublicPageParser.search(home, Store.EVOMAG).isEmpty())
        val results = Jsoup.parse("""<div class="searchnode"><div class="nice_product_item">
            <div class="npi_name"><a href="/sony-headphones-123.html">Căști Sony WH-1000XM5</a></div>
            <span class="real_price">1.799<sup>99</sup> lei</span></div></div>""", Store.EVOMAG.searchUrl("Sony"))
        assertEquals(179999L, PublicPageParser.search(results, Store.EVOMAG).single().price)
    }
    @Test fun readsPartnerCardsAndExactDecimalPrices() {
        val forit = Jsoup.parse("""<div class="product-grid"><div class="p-card"><div class="p-title">
            <a href="/casti-sony-wh-1000xm5b-negru-bp414828">Căști Sony WH-1000XM5B, negru</a></div>
            <div class="p-price">1.762,04<span> lei</span></div></div></div>""", Store.FORIT.searchUrl("Sony"))
        assertEquals(176204L, PublicPageParser.search(forit, Store.FORIT).single().price)
        val galaxy = Jsoup.parse("""<div id="products-list"><article class="product-box"><h2 class="name">
            <a title="WH-1000XM5 Blue" href="/casti/sony/wh-1000xm5-blue-672672/">Căști Sony WH-1000XM5 Blue</a></h2>
            <span id="price-672672">1.193,99 lei</span></article></div>""", Store.ITGALAXY.searchUrl("Sony"))
        val candidate = PublicPageParser.search(galaxy, Store.ITGALAXY).single()
        assertEquals(119399L, candidate.price); assertTrue(candidate.title.startsWith("Căști Sony"))
    }
    @Test fun altexBenefitDoesNotReplacePriceOrMixInResealedOffers() {
        val doc = Jsoup.parse("""<script type="application/ld+json">{"@type":"Product","url":"${product.url}",
            "offers":{"@type":"Offer","price":"1249.99","priceCurrency":"RON","availability":"https://schema.org/InStock"}}</script>
            <div><div><div><h2>Extra beneficii</h2></div></div><ul><li><span class="text-12px">-15% extra in app</span></li></ul></div>
            <section><h2>Produse Resigilate</h2><span class="price">999,99 lei</span></section>""", product.url)
        val offer = PublicPageParser.offers(doc, product).single()
        assertEquals(124999L, offer.price)
        assertEquals("-15% extra in app", offer.promotion?.title)
        assertEquals("", offer.promotion?.code)
    }
    @Test fun supplierStockRemainsDistinctAndNegativeStockOverridesStructuredAvailability() {
        fun doc(stock: String) = Jsoup.parse("""<script type="application/ld+json">{"@type":"Product","offers":{
            "@type":"Offer","price":"1762.04","priceCurrency":"RON","availability":"https://schema.org/InStock"}}</script>
            <div class="stock-row">$stock</div>""", "https://www.forit.ro/sony-bp123")
        val candidate = product.copy(source = Store.FORIT, url = "https://www.forit.ro/sony-bp123")
        val supplier = PublicPageParser.offers(doc("Stoc furnizor"), candidate).single()
        assertEquals("Stoc furnizor", supplier.stockLabel); assertTrue(supplier.inStock)
        assertFalse(PublicPageParser.offers(doc("Indisponibil"), candidate).single().inStock)
    }
    @Test fun evomagRequiresAgreementBetweenMicrodataAndVisibleCurrentPrice() {
        val candidate = product.copy(source = Store.EVOMAG, url = "https://www.evomag.ro/sony-123.html")
        fun doc(visible: String, currency: String = "RON") = Jsoup.parse("""<div itemscope itemtype="http://schema.org/Product">
            <span itemprop="url">${candidate.url}</span><div itemprop="offers">
            <meta itemprop="priceCurrency" content="$currency"><span itemprop="price" content="1799.99"></span></div></div>
            <div class="product_right_inside"><div class="pret_rons">$visible</div><span id="stockWidgetView">In stoc depozit</span></div>""", candidate.url)
        assertEquals(179999L, PublicPageParser.offers(doc("1.799<sup>99</sup> lei"), candidate).single().price)
        assertTrue(PublicPageParser.offers(doc("1.699<sup>99</sup> lei"), candidate).isEmpty())
        assertTrue(PublicPageParser.offers(doc("1.799<sup>99</sup> lei", "EUR"), candidate).isEmpty())
    }
}
