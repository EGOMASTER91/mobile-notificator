package ro.pretradar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelStore
import android.graphics.Bitmap
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import ro.pretradar.data.createSources
import ro.pretradar.domain.ProductSearch
import ro.pretradar.domain.Store
import ro.pretradar.domain.ThemeChoice
import ro.pretradar.ui.RadarApp
import ro.pretradar.ui.RadarViewModel
import kotlinx.coroutines.flow.first
import java.io.File

/** Explicit live diagnostic; source failures are recorded, never replaced with sample products. */
@RunWith(AndroidJUnit4::class)
class LiveSearchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun requestedFanHasVerifiedExactResults() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveSearch") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val query = "arctic bionix p120 a-rgb"
        val sources = createSources(context)
        val evidence = Store.defaults.map { store ->
            val source = sources.first { it.store == store }
            val response = source.search(query)
            val exact = response.items.filter(ProductSearch(query)::matches)
            val observations = if (store in setOf(Store.ITGALAXY, Store.EMAG)) exact.take(1).map { source.offers(it) } else emptyList()
            mapOf("source" to store.name, "query" to query, "url" to store.searchUrl(query),
                "rawCount" to response.items.size, "error" to response.error, "exact" to exact, "observations" to observations)
        }
        File(context.getExternalFilesDir(null), "live-search-evidence.json").writeText(Gson().toJson(evidence))
        val galaxy = evidence.single { it["source"] == Store.ITGALAXY.name }
        assertTrue(Gson().toJson(galaxy), (galaxy["exact"] as List<*>).isNotEmpty())

        // Repeat through the actual search screen with evoMAG excluded.
        val repository = (compose.activity.application as RadarApplication).repository
        val preferences = repository.preferences
        val original = preferences.settings.first()
        val owner = ViewModelStore()
        try {
            preferences.setDemo(false)
            preferences.setTheme(ThemeChoice.DARK)
            preferences.setStores(setOf(Store.ITGALAXY, Store.FORIT))
            lateinit var vm: RadarViewModel
            compose.runOnIdle {
                vm = RadarViewModel(repository)
                owner.put("live-search", vm)
                compose.activity.setContent { RadarApp(vm, {}, { false }) }
            }
            compose.waitUntil(10_000) { vm.settings.value.stores == setOf(Store.ITGALAXY, Store.FORIT) }
            compose.runOnIdle { vm.search(query) }
            compose.waitUntil(60_000) { !vm.searching.value && vm.results.value.any { it.items.isNotEmpty() } }
            compose.onNodeWithTag("home").performScrollToNode(hasText("Ventilator ARCTIC AC BioniX P120 ARGB") and !hasSetTextAction())
            compose.onNodeWithText("Ventilator ARCTIC AC BioniX P120 ARGB").assertIsDisplayed()
            val bitmap = compose.onNodeWithTag("home").captureToImage().asAndroidBitmap()
            File(context.getExternalFilesDir(null), "live-search-ui.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } finally {
            compose.runOnIdle { compose.activity.setContent {}; owner.clear() }
            preferences.setStores(original.stores)
            preferences.setTheme(original.theme)
            preferences.setDemo(original.demo)
        }
    }
}
