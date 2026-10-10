package ro.pretradar.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import ro.pretradar.domain.Store
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.*
import java.io.IOException

class SourceAccessTest {
    @Test fun respectsNumericAndDateRetryAfterWithoutImmediateRetries() {
        val now = Instant.parse("2026-10-06T09:00:00Z").toEpochMilli()
        assertEquals(now + 3_600_000, blockedUntil(429, null, now))
        assertEquals(now + 120_000, blockedUntil(429, "120", now))
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now + 7_200_000).atZone(ZoneOffset.UTC))
        assertEquals(now + 7_200_000, blockedUntil(429, date, now))
        assertTrue(blockedUntil(403, "invalid", now) >= now + 3_600_000)
        assertEquals(now + 60_000, blockedUntil(429, "0", now))
    }
    @Test fun publicSearchRoutesKeepQueriesEncodedAndCompariIsOptional() {
        assertTrue(Store.FORIT.searchUrl("Sony WH-1000XM5").contains("q=Sony+WH-1000XM5"))
        assertTrue(Store.ITGALAXY.searchUrl("Samsung & Sony").contains("%26"))
        assertTrue(Store.EVOMAG.searchUrl("Sony WH-1000XM5").contains("sn.q="))
        assertFalse(Store.COMPARI in Store.defaults)
        assertTrue(Store.defaults.containsAll(listOf(Store.FORIT, Store.ITGALAXY, Store.EVOMAG)))
    }
    @Test fun aRateLimitedSourceDoesNotContactTheServerAgainDuringCooldown() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(429)
                .message("Too Many Requests").header("Retry-After", "3600")
                .body(ResponseBody.create(null, "")).build()
        }.build()
        val pages = PublicStoreHttp(client)
        repeat(2) {
            try { pages.fetch(Store.ALTEX.searchUrl("Sony"), Store.ALTEX); fail("Expected blocked access") }
            catch (_: IOException) { }
        }
        assertEquals(1, requests)
    }
    @Test fun anExternalRedirectIsRejectedBeforeTheDestinationIsContacted() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302)
                .message("Found").header("Location", "https://unknown.example/product")
                .body(ResponseBody.create(null, "")).build()
        }.build()
        try { PublicStoreHttp(client).fetch(Store.ALTEX.searchUrl("Sony"), Store.ALTEX); fail("Expected rejected redirect") }
        catch (_: IOException) { }
        assertEquals(1, requests)
    }
    @Test fun aRenderedPageLimitAlsoPausesSubsequentHttpChecks() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(ResponseBody.create(null, "<html></html>")).build()
        }.build()
        val pages = PublicStoreHttp(client)
        pages.pause(Store.ALTEX, 429, "3600")
        try { pages.fetch(Store.ALTEX.searchUrl("Sony"), Store.ALTEX); fail("Expected cooldown") }
        catch (_: IOException) { }
        assertEquals(0, requests)
    }
    @Test fun currentPricesRequireServerValidationInsteadOfUnvalidatedCacheHits() = runBlocking {
        var directive = ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            directive = chain.request().header("Cache-Control").orEmpty()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(ResponseBody.create(null, "<html><title>Product</title></html>")).build()
        }.build()
        PublicStoreHttp(client).fetch("https://altex.ro/test/cpd/TEST/", Store.ALTEX)
        assertEquals("max-age=0", directive)
    }
    @Test fun cancelledChecksCancelTheNetworkCall() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = AtomicBoolean(false)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            started.complete(Unit)
            val deadline = System.nanoTime() + 3_000_000_000L
            while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(5)
            cancelled.set(chain.call().isCanceled())
            throw IOException("Cancelled")
        }.build()
        val job = launch(Dispatchers.Default) { PublicStoreHttp(client).fetch("https://altex.ro/test/cpd/TEST/", Store.ALTEX) }
        withTimeout(2_000) { started.await() }
        job.cancelAndJoin()
        assertTrue(cancelled.get())
    }
}
