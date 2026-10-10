package ro.pretradar.data

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import ro.pretradar.domain.*

/** Only paired visible prices in the category's product cards; no PRP, coupons or JSON lowPrice. */
object HotPageParser {
    fun parse(doc: Document, store: Store, category: HotCategory): List<HotDeal> {
        val cards = when (store) {
            Store.ALTEX -> doc.select(".Products .Product")
            Store.EMAG -> doc.select(".card-item[data-product-id]")
            else -> return emptyList()
        }
        return cards.mapNotNull { card ->
            val title = card.selectFirst(if (store == Store.ALTEX) ".Product-name" else "a.card-v2-title") ?: return@mapNotNull null
            val anchor = if (title.tagName() == "a") title else title.closest("a") ?: return@mapNotNull null
            val url = anchor.absUrl("href").substringBefore('?').substringBefore('#')
            if (!isSourceUrl(url, store) || !(if (store == Store.ALTEX) "/cpd/" else "/pd/").let { it in url }) return@mapNotNull null
            val stock = normalize(card.text())
            if ("stoc epuizat" in stock || "indisponibil" in stock || "nu este in stoc" in stock) return@mapNotNull null
            if ("in stoc" !in stock) return@mapNotNull null
            val current = card.selectFirst(if (store == Store.ALTEX) ".text-red-brand" else ".product-new-price")?.let(::price) ?: return@mapNotNull null
            val oldElements = card.select(if (store == Store.ALTEX) ".has-line-through" else
                ".product-old-price, .rrp-lp30d-content")
            val old = oldElements.mapNotNull { el ->
                val label = normalize(el.text() + " " + el.parent()?.text().orEmpty())
                if ("prp" in label || "rrp" in label || "recomandat" in label || "cupon" in label || "%" in el.text()) null
                else price(el)
            }.distinct().singleOrNull() ?: return@mapNotNull null
            if (old <= current) return@mapNotNull null
            val image = RemoteUrls.image(card.selectFirst("img")?.let { it.absUrl("src").ifBlank { it.absUrl("data-src") } }.orEmpty())
            HotDeal(ProductCandidate(PublicPageParser.stableId(url), title.text().trim(), url, store,
                imageUrl = image, price = current), old, category)
        }.distinctBy { it.product.url }.sortedByDescending { it.discountBasisPoints }
    }

    private fun price(el: Element): Long? {
        // Do not concatenate two prices or a percentage into one number.
        if (Regex("(?i)\\b(EUR|USD|GBP)\\b|€|\\$").containsMatchIn(el.text())) return null
        if (el.select(".Price-int").size > 1) return null
        val sup = el.selectFirst("sup")
        return if (sup != null) {
            val decimals = sup.text().filter(Char::isDigit)
            if (decimals.length != 2) return null
            val integer = el.clone().also { it.select("sup").remove() }.text()
            if (!Regex("^[\\d.\\s\\u00a0\\u202f]+(?:[Ll][Ee][Ii]|RON)?$").matches(integer.trim())) return null
            parsePrice(integer + "," + decimals)
        } else {
            val values = Regex("\\d[\\d.\\u00a0\\u202f]*(?:,\\d{2})?").findAll(el.text()).toList()
            if (values.size != 1) null else parsePrice(values.single().value)
        }
    }
}
