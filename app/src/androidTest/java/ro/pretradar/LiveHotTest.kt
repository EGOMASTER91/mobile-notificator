package ro.pretradar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ro.pretradar.data.createSources
import ro.pretradar.domain.*

@RunWith(AndroidJUnit4::class)
class LiveHotTest {
    @Test fun publicCategoryDiscounts() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Live Hot is opt-in", args.getString("liveHot") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sources = createSources(context)
        val category = args.getString("category")?.let(HotCategory::valueOf) ?: HotCategory.HEADPHONES
        val results = listOf(Store.ALTEX, Store.EMAG).map { store -> sources.first { it.store == store }.hot(category) }
        java.io.File(context.getExternalFilesDir(null), "live-hot-evidence.json").writeText(Gson().toJson(results))
        for (result in results) for (deal in result.items) {
            assertTrue(deal.referencePrice > deal.product.price!!)
            assertTrue(deal.discountBasisPoints > 0)
            assertEquals(category, deal.category)
        }
        // A blocked merchant remains explicitly recorded as an error, never a fabricated feed.
        assertTrue(results.toString(), results.first { it.source == Store.ALTEX }.items.isNotEmpty())
    }
}
