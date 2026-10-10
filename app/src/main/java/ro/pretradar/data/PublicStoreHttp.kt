package ro.pretradar.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.CacheControl
import java.util.concurrent.TimeUnit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ro.pretradar.domain.Store
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

/** Respect the server's delay; a blocked request never triggers a browser retry. */
fun blockedUntil(code: Int, retryAfter: String?, now: Long): Long {
    val supplied = retryAfter?.toLongOrNull()?.takeIf { it >= 0 }?.let {
        now + it.coerceAtMost(31_536_000) * 1000
    } ?: runCatching {
        ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()
    val default = if (code == 429) 3_600_000L else 21_600_000L
    return max(now + 60_000, supplied ?: now + default)
}

class PublicStoreHttp(private val http: OkHttpClient, context: Context? = null) {
    private val saved = context?.getSharedPreferences("source_access", Context.MODE_PRIVATE)
    private val locks = Store.entries.associateWith { Mutex() }
    private val lastRequest = java.util.concurrent.ConcurrentHashMap<Store, Long>()
    private val blocked = java.util.concurrent.ConcurrentHashMap<Store, Long>()

    fun pause(store: Store, code: Int, retryAfter: String?) {
        val until = blockedUntil(code, retryAfter, System.currentTimeMillis())
        blocked[store] = until
        saved?.edit()?.putLong(store.name, until)?.apply()
    }

    suspend fun fetch(url: String, store: Store): Document = locks.getValue(store).withLock {
        require(isSourceUrl(url, store)) { "Adresa nu aparține sursei selectate." }
        val now = System.currentTimeMillis()
        val resumeAt = max(blocked[store] ?: 0, saved?.getLong(store.name, 0) ?: 0)
        if (resumeAt > now) throw IOException("Sursa este în pauză după limitarea accesului. Reîncearcă în ${((resumeAt - now + 59_999) / 60_000)} min.")
        delay((1_500 - (now - (lastRequest[store] ?: 0))).coerceAtLeast(0))
        try {
            withContext(Dispatchers.IO) {
                var nextUrl = url
                for (redirect in 0..3) {
                    val request = Request.Builder().url(nextUrl)
                        .header("User-Agent", "PretRadar/0.2 (Android; personal price monitor)")
                        .header("Accept-Language", "ro-RO,ro;q=0.9")
                        .cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build()).build()
                    val call = http.newCall(request)
                    var loadedDocument: Document? = null
                    // Cancelling a stopped/timed-out worker closes its active network call.
                    coroutineScope {
                        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                            try { awaitCancellation() } finally { call.cancel() }
                        }
                        try { call.execute().use { response ->
                        if (response.code in setOf(301, 302, 303, 307, 308)) {
                            val next = response.header("Location")?.let { response.request.url.resolve(it) }
                            if (next == null || !isSourceUrl(next.toString(), store) || redirect == 3)
                                throw IOException("Redirecționarea paginii nu poate fi verificată.")
                            nextUrl = next.toString()
                            delay(1_500)
                        } else {
                            if (response.code == 403 || response.code == 429) {
                                pause(store, response.code, response.header("Retry-After"))
                                throw IOException("Magazinul limitează accesul automat (${response.code}). Verificările acestei surse intră în pauză.")
                            }
                            if (!response.isSuccessful) throw IOException(when (response.code) {
                                404, 410 -> "Pagina nu mai este disponibilă (${response.code})."
                                else -> "Magazinul nu a răspuns corect (${response.code})."
                            })
                            val body = response.body ?: throw IOException("Pagina nu conține date.")
                            if (body.contentLength() > 5_000_000) throw IOException("Pagina depășește limita de procesare.")
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            body.byteStream().use { input ->
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > 5_000_000) throw IOException("Pagina depășește limita de procesare.")
                                    output.write(buffer, 0, count)
                                }
                            }
                            val document = Jsoup.parse(output.toString("UTF-8"), response.request.url.toString())
                            if (listOf("captcha", "access denied", "just a moment").any { it in document.title().lowercase() }) {
                                pause(store, 403, null)
                                throw IOException("Magazinul solicită verificarea accesului în browser.")
                            }
                            loadedDocument = document
                        }
                        } } catch (error: IOException) {
                            currentCoroutineContext().ensureActive()
                            throw error
                        } finally { cancellation.cancel() }
                    }
                    loadedDocument?.let { return@withContext it }
                }
                throw IOException("Pagina nu poate fi încărcată.")
            }
        } finally { lastRequest[store] = System.currentTimeMillis() }
    }
}
