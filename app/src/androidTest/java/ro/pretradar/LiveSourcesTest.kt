package ro.pretradar

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ro.pretradar.data.createSources
import ro.pretradar.domain.Store

/** Opt-in network smoke tests. Normal test runs use deterministic fixtures only. */
@RunWith(AndroidJUnit4::class)
class LiveSourcesTest {
    @Test fun publicSearchAndTwoProductObservations() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Live sources are opt-in", args.getString("liveSources") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val requested = args.getString("source")?.let(Store::valueOf)
        val stores = requested?.let(::listOf) ?: listOf(Store.ALTEX, Store.FORIT, Store.ITGALAXY, Store.EVOMAG)
        val sources = createSources(context)
        val evidence = mutableListOf<Map<String, Any?>>()
        try {
            for (store in stores) {
              try {
                val source = sources.first { it.store == store }
                val results = source.search("Sony WH-1000XM5")
                assertNull("${store.label}: ${results.error}", results.error)
                assertTrue("${store.label}: no public results", results.items.isNotEmpty())
                val candidate = results.items.firstOrNull { "negru" in it.title.lowercase() }
                    ?: results.items.first()
                val first = source.offers(candidate)
                assertNull("${store.label}: ${first.error}", first.error)
                assertTrue("${store.label}: no verified prices", first.items.any { it.price > 0 })
                val second = source.offers(candidate)
                assertNull("${store.label} repeat: ${second.error}", second.error)
                assertTrue(second.items.any { it.price > 0 })
                evidence += mapOf("source" to store.label, "results" to results.items.size,
                    "candidate" to candidate, "first" to first.items, "second" to second.items)
                Log.i("PretRadarLive", Gson().toJson(evidence.last()))
              } catch (failure: AssertionError) {
                evidence += mapOf("source" to store.label, "error" to failure.message)
                Log.e("PretRadarLive", Gson().toJson(evidence.last()))
              }
            }
            assertTrue(evidence.joinToString { it.toString() }, evidence.none { "error" in it })
        } finally {
            java.io.File(context.getExternalFilesDir(null), "live-source-evidence.json").writeText(Gson().toJson(evidence))
        }
    }
}
