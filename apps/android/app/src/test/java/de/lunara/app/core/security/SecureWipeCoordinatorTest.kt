package de.lunara.app.core.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureWipeCoordinatorTest {
    @Test
    fun `remote logout failure never blocks ordered local wipe`() = runTest {
        val calls = mutableListOf<String>()

        SecureWipeCoordinator.execute(
            bestEffortRemoteLogout = {
                calls += "remote"
                error("offline")
            },
            localSteps = listOf(
                { calls += "database" },
                { calls += "cookies" },
                { calls += "secrets" },
                { calls += "preferences" },
            ),
        )

        assertEquals(listOf("remote", "database", "cookies", "secrets", "preferences"), calls)
    }

    @Test
    fun `all local erase steps run and first failure is reported`() = runTest {
        val calls = mutableListOf<String>()
        val first = IllegalStateException("database")

        val thrown = runCatching {
            SecureWipeCoordinator.execute(
                localSteps = listOf(
                    { calls += "database"; throw first },
                    { calls += "cookies" },
                    { calls += "secrets"; error("alias") },
                    { calls += "preferences" },
                ),
            )
        }.exceptionOrNull()

        assertTrue(thrown is SecureWipeException)
        val wipeFailure = thrown as SecureWipeException
        assertEquals(listOf("database", "alias"), wipeFailure.failures.map { it.message })
        assertEquals(1, wipeFailure.suppressed.size)
        assertEquals(listOf("database", "cookies", "secrets", "preferences"), calls)
    }
}
