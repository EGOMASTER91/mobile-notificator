package ro.pretradar.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.*
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ro.pretradar.domain.Store
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Render public JavaScript search pages on demand. Never used for
 * blocked responses, CAPTCHA challenges or periodic background checks. */
class PublicPageRenderer(private val context: Context, private val onLimited: (Store, Int, String?) -> Unit = { _, _, _ -> }) {
    private val renderLock = Mutex()
    suspend fun search(url: String, store: Store): Document = render(url, store, false)
    suspend fun hot(url: String, store: Store): Document = render(url, store, true)
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun render(url: String, store: Store, hot: Boolean): Document = renderLock.withLock {
        require(isSourceUrl(url, store))
        val selector = when (store) {
            Store.ALTEX -> "a[href*=\"/cpd/\"]"
            Store.EVOMAG -> ".searchnode .npi_name a"
            Store.EMAG -> if (hot) ".card-item .rrp-lp30d-content" else throw java.io.IOException("Randare indisponibilă.")
            else -> throw java.io.IOException("Sursa nu necesită randare JavaScript.")
        }
        withTimeoutOrNull(25_000) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val view = WebView(context.applicationContext)
                WebView.setWebContentsDebuggingEnabled(false)
                val handler = Handler(Looper.getMainLooper())
                var polling = false
                var disposed = false
                fun cleanUp() { if (!disposed) { disposed = true; handler.removeCallbacksAndMessages(null); view.stopLoading(); view.destroy() } }
                fun fail(message: String) {
                    if (continuation.isActive) { cleanUp(); continuation.resumeWithException(java.io.IOException(message)) }
                }
                view.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    blockNetworkImage = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    safeBrowsingEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    setGeolocationEnabled(false)
                    mediaPlaybackRequiresUserGesture = true
                    userAgentString += " PretRadar/0.2"
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
                val poll = object : Runnable {
                    override fun run() {
                        if (!continuation.isActive) return
                        if (!isSourceUrl(view.url.orEmpty(), store)) {
                            fail("Pagina a părăsit domeniul magazinului."); return
                        }
                        val query = com.google.gson.Gson().toJson(selector)
                        val readyScript = if (hot && store == Store.EMAG)
                            "Array.from(document.querySelectorAll($query)).some(e => /[0-9]/.test(e.textContent))"
                        else "!!document.querySelector($query)"
                        view.evaluateJavascript(readyScript) { ready ->
                            if (!continuation.isActive) return@evaluateJavascript
                            if (ready == "true") {
                                view.evaluateJavascript("document.documentElement.outerHTML") { encoded ->
                                    if (!continuation.isActive) return@evaluateJavascript
                                    val html = runCatching { JsonParser.parseString(encoded).asString }.getOrNull()
                                    if (html == null || html.length > 5_000_000) fail("Pagina nu poate fi procesată.")
                                    else {
                                        val currentUrl = view.url.orEmpty()
                                        if (!isSourceUrl(currentUrl, store)) fail("Adresa paginii nu poate fi verificată.")
                                        else { cleanUp(); continuation.resume(Jsoup.parse(html, currentUrl)) }
                                    }
                                }
                            } else handler.postDelayed(this, 700)
                        }
                    }
                }
                view.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !isSourceUrl(request.url.toString(), store)
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        if (!isSourceUrl(view.url.orEmpty(), store)) {
                            fail("Pagina a părăsit domeniul magazinului."); return
                        }
                        if (view.title.orEmpty().contains("captcha", true)) {
                            onLimited(store, 403, null)
                            fail("Magazinul solicită verificare manuală. Încearcă mai târziu."); return
                        }
                        if (!polling && continuation.isActive) { polling = true; handler.post(poll) }
                    }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (request.isForMainFrame && response.statusCode >= 400) {
                            if (response.statusCode in setOf(403, 429)) onLimited(store, response.statusCode,
                                response.responseHeaders?.entries?.firstOrNull { it.key.equals("Retry-After", true) }?.value)
                            fail("Pagina publică nu este accesibilă (${response.statusCode}).")
                        }
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) fail("Pagina publică nu a putut fi încărcată.")
                    }
                }
                continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { cleanUp() } }
                view.loadUrl(url, mapOf("Accept-Language" to "ro-RO,ro;q=0.9"))
            }
        }
        } ?: throw java.io.IOException("Pagina de căutare nu a furnizat rezultate verificabile în 25 de secunde. Rezultatele celorlalte magazine sunt afișate separat.")
    }
}
