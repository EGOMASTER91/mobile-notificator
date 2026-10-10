package ro.pretradar.domain

import org.junit.Assert.*
import org.junit.Test

class ProductSearchTest {
    private fun product(title: String) = ProductCandidate("p", title, "", Store.ALTEX)
    private fun matches(query: String, title: String) = ProductSearch(query).matches(product(title))

    @Test fun addressableRgbSpellingsMatchTheSameFanWithoutAcceptingDifferentModels() {
        listOf("A-RGB", "ARGB", "A RGB").forEach { spelling ->
            val query = "arctic bionix p120 $spelling"
            assertTrue(matches(query, "Ventilator ARCTIC AC BioniX P120 ARGB (ACFAN00146A)"))
            assertTrue(matches(query, "Ventilator ARCTIC BioniX P120 A-RGB, 120mm"))
            assertFalse(matches(query, "Ventilator ARCTIC BioniX P120 RGB"))
            assertFalse(matches(query, "Ventilator ARCTIC BioniX P120 Alb/Gri"))
            assertFalse(matches(query, "Ventilator ARCTIC BioniX P140 ARGB"))
            assertFalse(matches(query, "Ventilator ARCTIC BioniX P12 ARGB"))
            assertFalse(matches(query, "Ventilator ARCTIC BioniX P120 A-RGB (pachet 3 ventilatoare + Controller), 120mm"))
        }
        assertFalse(matches("arctic bionix p120 rgb", "Ventilator ARCTIC BioniX P120 ARGB"))
        assertTrue(Store.ITGALAXY.searchUrl("arctic bionix p120 a-rgb").endsWith("arctic+bionix+p120+ARGB"))
        assertTrue(Store.EMAG.searchUrl("S24+").endsWith("S24%2B"))
        assertTrue(matches("arctic bionix p120 argb pachet 3", "Ventilator ARCTIC BioniX P120 A-RGB (pachet 3 ventilatoare + Controller), 120mm"))
    }

    @Test fun everyRequestedWordIsRequiredButRetailDescriptionsAreAllowed() {
        assertTrue(matches("Samsung Galaxy S24", "Telefon mobil Samsung Galaxy S24, 128GB, 5G, Negru"))
        assertTrue(matches("galaxy samsung s24", "Telefon Samsung Galaxy S24 256GB Albastru"))
        assertFalse(matches("Samsung Galaxy S24", "Telefon Samsung Galaxy S23 128GB"))
        assertFalse(matches("Sony WH-1000XM5", "Casti wireless Sony WH-1000XM4 Negru"))
        assertFalse(matches("Sony WH-1000XM5", "Casti wireless Bose QuietComfort"))
    }

    @Test fun modelsAreWholeCodesInsteadOfPartialMatches() {
        assertFalse(matches("Samsung S24", "Samsung Galaxy S240"))
        assertFalse(matches("Sony WH-1000XM5", "Sony WH-1000XM50"))
        assertFalse(matches("S24", "Samsung S24FE"))
        assertFalse(matches("Pro", "Monitor Professional"))
    }

    @Test fun caseDiacriticsAndModelSeparatorsDoNotHideTheRequestedProduct() {
        assertTrue(matches("căști SONY wh1000xm5", "Casti Sony WH-1000XM5, Bluetooth, Negru"))
        assertTrue(matches("Sony WH-1000XM5", "Casti SONY WH1000XM5, Argintiu"))
        assertTrue(matches("Sony WH-1000XM5", "Casti Sony WH 1000 XM 5"))
        assertTrue(matches("Sony WH 1000 XM5", "Casti Sony WH-1000XM5"))
        assertTrue(matches("iPhone15", "Telefon Apple iPhone 15 128GB"))
        assertTrue(matches("iPhone 15", "Telefon Apple iPhone15 128GB"))
    }

    @Test fun unrequestedModelEditionsAreRemoved() {
        listOf("Ultra", "Plus", "FE", "Lite").forEach { edition ->
            assertFalse(edition, matches("Samsung S24", "Samsung Galaxy S24 $edition 256GB"))
        }
        assertFalse(matches("Samsung S24", "Samsung Galaxy S24+ 256GB"))
        assertFalse(matches("iPhone 15", "Apple iPhone 15 Pro Max 256GB"))
        assertFalse(matches("iPhone 15 Pro", "Apple iPhone 15 Pro Max 256GB"))
        assertTrue(matches("Samsung S24 Ultra", "Samsung Galaxy S24 Ultra 256GB"))
        assertTrue(matches("Samsung S24 FE", "Samsung Galaxy S24FE 256GB"))
        assertTrue(matches("iPhone15ProMax", "Apple iPhone 15 Pro Max 256GB"))
        assertTrue(matches("S24+", "Samsung Galaxy S24 Plus 256GB"))
        assertTrue(matches("iPhone 15 Pro Max", "Apple iPhone 15 Pro Max 256GB"))
    }

    @Test fun accessoriesThatMentionTheModelAreNotTheProduct() {
        listOf("Husa protectie pentru", "Folie sticla pentru", "Incarcator compatibil cu", "Suport pentru").forEach { prefix ->
            assertFalse(prefix, matches("Samsung S24", "$prefix Samsung Galaxy S24"))
        }
        assertFalse(matches("Samsung S24", "Samsung Husa pentru Galaxy S24"))
        assertFalse(matches("Sony WH-1000XM5", "Pernite compatibile Sony WH-1000XM5"))
        assertFalse(matches("S24", "Case for Samsung S24"))
        assertTrue(matches("Sony WH-1000XM5", "Casti Sony WH-1000XM5 cu cablu USB inclus"))
        assertTrue(matches("Sony WH-1000XM5 Negru", "Casti Sony WH-1000XM5, cablu USB inclus, Negru"))
        assertTrue(matches("husa Samsung S24", "Husa protectie Samsung Galaxy S24"))
        assertTrue(matches("cablu Sony WH1000XM5", "Cablu compatibil cu Sony WH-1000XM5"))
    }

    @Test fun accessoriesCanThemselvesBeTheRequestedProduct() {
        assertTrue(matches("Anker Nano 65W", "Incarcator Anker Nano 65W Negru"))
        assertTrue(matches("Corsair 4000D", "Carcasa PC Corsair 4000D Airflow"))
        assertTrue(matches("Spigen Liquid Air", "Husa Spigen Liquid Air Negru"))
        assertFalse(matches("Corsair 4000D", "Suport pentru Corsair 4000D"))
    }

    @Test fun requestedStorageAndColourAreRequiredWithoutRestrictingUnspecifiedVariants() {
        assertTrue(matches("S24 128 GB Negru", "Samsung Galaxy S24 128GB Negru"))
        assertFalse(matches("S24 128GB Negru", "Samsung Galaxy S24 256GB Negru"))
        assertFalse(matches("S24 128GB Negru", "Samsung Galaxy S24 128GB Albastru"))
        assertTrue(matches("S24", "Samsung Galaxy S24 128GB Negru"))
        assertTrue(matches("S24", "Samsung Galaxy S24 256GB Albastru"))
    }

    @Test fun broadCategoryQueriesKeepOnlyThatCategoryAndBrand() {
        assertTrue(matches("televizor Samsung", "Televizor Samsung QLED 55Q80"))
        assertTrue(matches("televizor Samsung", "Televizor Samsung OLED 65S90"))
        assertFalse(matches("televizor Samsung", "Telefon Samsung Galaxy S24"))
        assertFalse(matches("televizor Samsung", "Televizor LG OLED"))
    }

    @Test fun eanSearchUsesTheActualProductIdentity() {
        val candidate = product("Casti Sony WH-1000XM5").copy(ean = "4548736132580")
        assertTrue(ProductSearch("4548736132580").matches(candidate))
        assertFalse(ProductSearch("4548736132597").matches(candidate))
        assertFalse(ProductSearch("   --- ").matches(candidate))
    }

    @Test fun officialSonyColourCodesAreAliasesOfTheBaseModelOnly() {
        listOf("B", "L", "P", "S").forEach { colour ->
            assertTrue(colour, matches("Sony WH-1000XM5", "Casti Sony WH-1000XM5$colour"))
        }
        assertFalse(matches("Sony WH-1000XM5", "Casti Sony WH-1000XM5X"))
        assertFalse(matches("Sony WH-1000XM5", "Casti Sony WH-1000XM6B"))
        assertTrue(matches("WH1000XM5B", "Casti Sony WH-1000XM5B Negru"))
        assertFalse(matches("WH1000XM5B", "Casti Sony WH-1000XM5S Argintiu"))
    }

    @Test fun recordedStoreTitlesKeepTheRequestedModelWithoutAcceptingDifferentBundles() {
        assertTrue(matches("Sony WH-1000XM5", "Casti SONY WH-1000XM5, Bluetooth, Over-ear, Microfon, Noise Cancelling, negru"))
        assertTrue(matches("Sony WH-1000XM5", "Casti Over-Head Sony WH-1000XM5B, negru"))
        assertTrue(matches("Sony WH-1000XM5", "Casti Over the Ear Sony WH-1000XM5B, Wireless, Bluetooth, Noise cancelling, Autonomie baterie 30 ore, Microfon, Negru"))
        assertFalse(matches("Sony WH-1000XM5", "Casti Sony WH-1000XM5 + Softcase USB-C ANC Negru"))
        assertTrue(matches("Sony WH-1000XM5 + Softcase", "Casti Sony WH-1000XM5 + Softcase USB-C ANC Negru"))
    }
}
