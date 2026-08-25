package de.lunara.app.security

/**
 * Selects the OAuth redirect for a concrete app distribution.
 *
 * A production distribution always uses its publisher-controlled verified App
 * Link, independently from the API or OIDC issuer host. A fully independent
 * distributor builds with their own app-link host and assetlinks.json. The
 * custom scheme exists only for explicitly non-production local/debug builds;
 * PKCE cannot authenticate the identity of an OAuth public client by itself.
 */
object OidcRedirectPolicy {
    private const val HTTPS_CALLBACK_PATH = "/auth/android-callback"
    private const val CUSTOM_CALLBACK_PATH = "/oauth2redirect"

    fun select(
        packageName: String,
        verifiedAppLinkEnabled: Boolean,
        appLinkHost: String,
    ): String {
        return if (verifiedAppLinkEnabled) {
            httpsRedirect(appLinkHost)
        } else {
            customRedirect(packageName)
        }
    }

    fun isAllowed(
        uri: String,
        packageName: String,
        verifiedAppLinkEnabled: Boolean,
        appLinkHost: String,
    ): Boolean = uri == select(packageName, verifiedAppLinkEnabled, appLinkHost)

    private fun httpsRedirect(host: String): String =
        "https://${host.lowercase()}$HTTPS_CALLBACK_PATH"

    private fun customRedirect(packageName: String): String =
        "$packageName:$CUSTOM_CALLBACK_PATH"
}
