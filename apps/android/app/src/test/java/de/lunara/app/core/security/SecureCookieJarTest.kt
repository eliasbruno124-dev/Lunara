package de.lunara.app.core.security

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureCookieJarTest {
    @Test
    fun `late response from a cleared generation cannot restore cookies`() {
        val store = InMemorySecretStore()
        val jar = SecureCookieJar(store)
        val staleClientJar = jar.scoped()
        val url = "https://lunara.example.test/".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("auth")
            .value("opaque-test-value")
            .hostOnlyDomain(url.host)
            .path("/")
            .secure()
            .expiresAt(System.currentTimeMillis() + 60_000)
            .build()

        jar.clear()
        staleClientJar.saveFromResponse(url, listOf(cookie))

        assertTrue(jar.loadForRequest(url).isEmpty())
        assertNull(store.getString("authentik.cookies.v1"))
    }
}

private class InMemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, ByteArray>()

    override fun putString(key: String, value: String?) {
        putBytes(key, value?.encodeToByteArray())
    }

    override fun getString(key: String): String? = values[key]?.decodeToString()

    override fun putBytes(key: String, value: ByteArray?) {
        if (value == null) values.remove(key) else values[key] = value.copyOf()
    }

    override fun getBytes(key: String): ByteArray? = values[key]?.copyOf()

    override fun clear() {
        values.clear()
    }
}
