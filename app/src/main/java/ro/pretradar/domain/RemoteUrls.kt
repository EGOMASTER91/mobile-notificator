package ro.pretradar.domain

import java.net.URI

/** Data from store HTML is untrusted. Never pass local-file or custom-scheme URLs to image loaders. */
object RemoteUrls {
    fun httpsUri(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf { uri ->
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme == "https" && uri.userInfo == null && uri.port in setOf(-1, 443) &&
            host.contains('.') && host.split('.').all { label ->
                label.isNotEmpty() && label.all { it.isLetterOrDigit() || it == '-' } &&
                    !label.startsWith('-') && !label.endsWith('-')
            } && host.substringAfterLast('.').any { it in 'a'..'z' } &&
            !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal") &&
            !host.endsWith(".test") && !host.endsWith(".invalid")
    }
    fun image(value: String): String = value.takeIf { httpsUri(it) != null }.orEmpty()
}
