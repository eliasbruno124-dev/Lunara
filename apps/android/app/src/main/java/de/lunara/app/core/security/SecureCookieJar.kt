package de.lunara.app.core.security

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
) {
    fun toCookie(): Cookie = Cookie.Builder().apply {
        name(name)
        value(value)
        expiresAt(expiresAt)
        path(path)
        if (hostOnly) hostOnlyDomain(domain) else domain(domain)
        if (secure) secure()
        if (httpOnly) httpOnly()
    }.build()

    companion object {
        fun from(cookie: Cookie) = StoredCookie(
            cookie.name, cookie.value, cookie.expiresAt, cookie.domain, cookie.path,
            cookie.secure, cookie.httpOnly, cookie.hostOnly,
        )
    }
}

@Singleton
class SecureCookieJar @Inject constructor(
    private val secretStore: SecretStore,
) : CookieJar {
    private val gson = Gson()
    private val storedCookies = ConcurrentHashMap<String, StoredCookie>()
    private var generation = 0L

    init {
        val type = object : TypeToken<List<StoredCookie>>() {}.type
        runCatching {
            gson.fromJson<List<StoredCookie>>(secretStore.getString(KEY), type)
        }.getOrNull().orEmpty().forEach { storedCookies[id(it)] = it }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        saveForGeneration(generation, cookies)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = loadForGeneration(generation, url)

    /** Each HTTP client captures a generation so late responses cannot repopulate a wiped jar. */
    @Synchronized
    fun scoped(): CookieJar {
        val expectedGeneration = generation
        return object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                saveForGeneration(expectedGeneration, cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                loadForGeneration(expectedGeneration, url)
        }
    }

    @Synchronized
    private fun saveForGeneration(expectedGeneration: Long, cookies: List<Cookie>) {
        if (expectedGeneration != generation) return
        val now = System.currentTimeMillis()
        cookies.forEach {
            val stored = StoredCookie.from(it)
            if (it.expiresAt <= now) storedCookies.remove(id(stored)) else storedCookies[id(stored)] = stored
        }
        persist(now, force = true)
    }

    @Synchronized
    private fun loadForGeneration(expectedGeneration: Long, url: HttpUrl): List<Cookie> {
        if (expectedGeneration != generation) return emptyList()
        val now = System.currentTimeMillis()
        val result = storedCookies.values.mapNotNull { stored ->
            stored.toCookie().takeIf { it.expiresAt > now && it.matches(url) }
        }
        persist(now, force = false)
        return result
    }

    @Synchronized
    fun clear() {
        generation += 1
        storedCookies.clear()
        secretStore.putString(KEY, null)
    }

    private fun persist(now: Long, force: Boolean) {
        val removedExpired = storedCookies.entries.removeIf { it.value.expiresAt <= now }
        if (!force && !removedExpired) return
        secretStore.putString(
            KEY,
            storedCookies.values.toList().takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
        )
    }

    private fun id(cookie: StoredCookie) = "${cookie.name}|${cookie.domain}|${cookie.path}"

    private companion object { const val KEY = "authentik.cookies.v1" }
}
