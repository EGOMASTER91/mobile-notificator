package ro.pretradar.domain

/** Strict title matching, independent of the recommendations returned by a store. */
class ProductSearch(query: String) {
    private val normalizedQuery = searchable(query)
    private val terms = terms(normalizedQuery)
    private val patterns = terms.map(::termPattern)
    private val requestedModifiers = modifiers.filter { termPattern(it).containsMatchIn(normalizedQuery) }.toSet()
    private val requestsAccessory = accessoryPattern.containsMatchIn(normalizedQuery)
    private val modelIndices = terms.indices.filter { index ->
        terms[index].any(Char::isDigit) && !measurement.matches(terms[index])
    }

    fun matches(product: ProductCandidate): Boolean {
        if (terms.isEmpty()) return false
        if (eanPattern.matches(normalizedQuery) && product.ean == normalizedQuery) return true
        val title = searchable(product.title)
        // Match every requested term as a whole word/model, never as a substring.
        val matches = patterns.map { it.find(title) ?: return false }
        if (modelIndices.isNotEmpty() && !bundlePattern.containsMatchIn(normalizedQuery) &&
            bundlePattern.containsMatchIn(title)) return false
        val identityEnd = (modelIndices.ifEmpty { matches.indices.toList() }).maxOf { matches[it].range.last + 1 }
        val prefix = title.substring(0, identityEnd)
        val namesMainProduct = productKindPattern.containsMatchIn(normalizedQuery) ||
            modelIndices.any { deviceModelPattern.matches(terms[it]) }
        if (!requestsAccessory && accessoryPattern.containsMatchIn(prefix) &&
            (namesMainProduct || compatibilityPattern.containsMatchIn(prefix))) return false

        for (index in modelIndices) {
            // S24 Ultra, S24 FE and iPhone 15 Pro Max are distinct models.
            val suffix = title.substring(matches[index].range.last + 1).trimStart()
            val adjacentModifiers = suffix.split(' ').takeWhile { it in modifiers }
            if (adjacentModifiers.any { it !in requestedModifiers }) return false
        }
        return true
    }

    companion object {
        // Retailers spell addressable RGB as ARGB, A-RGB or A RGB. Plain RGB is distinct.
        private val addressableRgb = Regex("\\ba[\\s-]+rgb\\b", RegexOption.IGNORE_CASE)
        private val bundlePattern = Regex("\\b(?:pachet|pack|kit|set)\\b")
        fun retailerQuery(value: String): String = addressableRgb.replace(value.trim(), "ARGB")
        private val modifiers = setOf("ultra", "pro", "max", "plus", "mini", "lite", "fe", "se", "neo", "ti", "super")
        private val units = setOf("gb", "tb", "mb", "cm", "mm", "inch", "ml", "w", "mah")
        private val numberedNames = setOf("iphone", "ipad", "pixel")
        private val measurement = Regex("[0-9]+(?:${units.joinToString("|")})")
        private val eanPattern = Regex("(?:[0-9]{8}|[0-9]{12,14})")
        private val modifierPattern = Regex(modifiers.joinToString("|"))
        private val attachedModifiers = Regex("([0-9])((?:${modifiers.joinToString("|")})+)\\b")
        private val deviceModelPattern = Regex("(?:s[0-9]+|iphone[0-9]+|ipad[0-9]+|pixel[0-9]+|(?:wh|wf)[0-9]+[a-z0-9]*)")
        private val productKindPattern = Regex("\\b(?:telefon|smartphone|casti|televizor|laptop|tableta|consola|monitor|airfryer|aparat|aspirator)\\b")
        private val compatibilityPattern = Regex("\\b(?:pentru|compatibil|compatibila|compatibile|compatibili|for)\\b")
        private val accessoryPattern = Regex(
            "\\b(?:husa|huse|carcasa|carcase|folie|folii|protector|protectoare|curea|curele|" +
                "incarcator|incarcatoare|cablu|cabluri|adaptor|adaptoare|suport|suporturi|" +
                "stand|dock|docking|baterie|acumulator|earpads|pernite|cushions|accesorii|" +
                "case|cover|charger|cable|adapter|holder|strap|telecomanda)\\b"
        )

        private fun searchable(value: String): String = attachedModifiers.replace(normalize(retailerQuery(value).replace("+", " plus "))) { match ->
            match.groupValues[1] + " " + modifierPattern.findAll(match.groupValues[2]).joinToString(" ") { it.value }
        }

        private fun terms(value: String): List<String> {
            val words = value.split(' ').filter(String::isNotBlank)
            val result = mutableListOf<String>()
            var index = 0
            while (index < words.size) {
                var word = words[index++]
                val next = words.getOrNull(index)
                // WH-1000XM5 / WH1000XM5, S 24 / S24, iPhone 15 / iPhone15.
                if (word.all(Char::isLetter) && next != null && next.first().isDigit() &&
                    (word.length <= 3 || word in numberedNames)) {
                    word += next
                    index++
                }
                val continuation = words.getOrNull(index)
                if (Regex("[a-z]{1,3}[0-9]+").matches(word) && continuation != null &&
                    Regex("[a-z]{1,3}[0-9]+").matches(continuation)) {
                    word += continuation
                    index++
                }
                val unit = words.getOrNull(index)
                if (word.all(Char::isDigit) && unit in units) {
                    word += unit
                    index++
                }
                result += word
            }
            return result
        }

        private fun termPattern(term: String): Regex {
            val parts = Regex("[a-z]+|[0-9]+").findAll(term).map { Regex.escape(it.value) }.toList()
            // Sony lists B/L/P/S as colour SKUs of WH-1000XM5. No generic prefix matching.
            val colourSku = if (term == "wh1000xm5") "(?:[blps])?" else ""
            return Regex("\\b${parts.joinToString("\\s*")}$colourSku\\b")
        }
    }
}
