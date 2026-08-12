package de.lunara.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerAddressParserTest {
    @Test
    fun `sanitizes URL and separates Authentik app password`() {
        val parsed = ServerAddressParser.parse(
            " https://alex:app%20password@LUNARA.Example.DE:443/instance/ ",
            allowInsecureHttp = false,
        )

        assertEquals("https://lunara.example.de/instance/", parsed.baseUrl)
        assertEquals(GatewayCredentials("alex", "app password"), parsed.gatewayCredentials)
    }

    @Test
    fun `plain HTTPS address has no gateway credentials`() {
        val parsed = ServerAddressParser.parse("https://cycle.example.de", false)
        assertEquals("https://cycle.example.de/", parsed.baseUrl)
        assertNull(parsed.gatewayCredentials)
    }

    @Test
    fun `rejects HTTP unless development mode explicitly allows it`() {
        assertThrows(ServerAddressException.HttpsRequired::class.java) {
            ServerAddressParser.parse("http://10.0.2.2:3000", false)
        }
        assertEquals(
            "http://10.0.2.2:3000/",
            ServerAddressParser.parse("http://10.0.2.2:3000", true).baseUrl,
        )
    }

    @Test
    fun `rejects partial credentials and dangerous URL components`() {
        assertThrows(ServerAddressException.IncompleteCredentials::class.java) {
            ServerAddressParser.parse("https://alex@cycle.example.de", false)
        }
        assertThrows(ServerAddressException.InvalidUrl::class.java) {
            ServerAddressParser.parse("https://cycle.example.de/?redirect=evil", false)
        }
        assertThrows(ServerAddressException.InvalidUrl::class.java) {
            ServerAddressParser.parse("https://cycle.example.de/#token", false)
        }
    }
}
