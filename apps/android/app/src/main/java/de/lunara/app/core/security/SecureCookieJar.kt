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

    init {
        val type = object : TypeToken<List<StoredCookie>>() {}.type
        runCatching {
            gson.fromJson<List<StoredCookie>>(secretStore.getString(KEY), type)
        }.getOrNull().orEmpty().forEach { storedCookies[id(it)] = it }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        cookies.forEach {
            val stored = StoredCookie.from(it)
            if (it.expiresAt <= now) storedCookies.remove(id(stored)) else storedCookies[id(stored)] = stored
        }
        persist(now)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = storedCookies.values.mapNotNull { stored ->
            stored.toCookie().takeIf { it.expiresAt > now && it.matches(url) }
        }
        persist(now)
        return result
    }

    /** True only when a non-expired Authentik cookie is valid for this exact request URL. */
    @Synchronized
    fun hasValidFor(url: HttpUrl): Boolean {
        val now = System.currentTimeMillis()
        return storedCookies.values.any { stored ->
            stored.expiresAt > now && runCatching { stored.toCookie().matches(url) }.getOrDefault(false)
        }
    }

    fun clear() {
        storedCookies.clear()
        secretStore.putString(KEY, null)
    }

    private fun persist(now: Long) {
        storedCookies.entries.removeIf { it.value.expiresAt <= now }
        secretStore.putString(KEY, gson.toJson(storedCookies.values.toList()))
    }

    private fun id(cookie: StoredCookie) = "${cookie.name}|${cookie.domain}|${cookie.path}"

    private companion object { const val KEY = "authentik.cookies.v1" }
}
