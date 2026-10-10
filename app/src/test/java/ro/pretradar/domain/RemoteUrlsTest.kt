package ro.pretradar.domain

import org.junit.Assert.*
import org.junit.Test
import ro.pretradar.data.isSourceUrl

class RemoteUrlsTest {
    @Test fun remoteContentCannotLoadFilesCustomSchemesOrExplicitLocalAddresses() {
        listOf("file:///data/data/ro.pretradar/files/private.png", "content://contacts/1", "javascript:alert(1)",
            "data:image/png;base64,AAAA", "http://altex.ro/photo.png", "https://localhost/photo.png",
            "https://127.0.0.1/photo.png", "https://0x7f.0.0.1/photo.png", "https://[::1]/photo.png",
            "https://printer.local/photo.png", "https://camera.internal/photo.png",
            "https://user:secret@altex.ro/photo.png", "https://altex.ro:8443/photo.png", "https://altex.ro\\@evil.example/photo.png"
        ).forEach { url -> assertEquals(url, "", RemoteUrls.image(url)) }
        assertEquals("https://s13emagst.akamaized.net/product.jpg", RemoteUrls.image("https://s13emagst.akamaized.net/product.jpg"))
    }
    @Test fun aStoreUrlMustMatchTheActualHttpsHostAndNormalPort() {
        listOf("https://altex.ro.evil.example/cpd/A/", "https://evil-altex.ro/cpd/A/",
            "https://altex.ro@evil.example/cpd/A/", "https://altex.ro:8443/cpd/A/")
            .forEach { assertFalse(it, isSourceUrl(it, Store.ALTEX)) }
        assertTrue(isSourceUrl("https://altex.ro:443/cpd/A/", Store.ALTEX))
        assertTrue(isSourceUrl("https://www.ALTEX.ro/cpd/A/", Store.ALTEX))
    }
}
