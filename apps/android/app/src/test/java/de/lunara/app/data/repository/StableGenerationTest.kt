package de.lunara.app.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StableGenerationTest {
    @Test
    fun returnsFirstResultWhenNothingChanges() = runBlocking {
        var calls = 0
        val result = fetchWithStableGeneration({ 7L }) { generation -> calls++; "g$generation" }
        assertEquals("g7", result)
        assertEquals(1, calls)
    }

    @Test
    fun retriesAfterAChangeAndReturnsTheStableResult() = runBlocking {
        var generation = 0L
        val seen = mutableListOf<Long>()
        val result = fetchWithStableGeneration({ generation }) { requested ->
            seen += requested
            if (seen.size == 1) generation++ // settings change during the first request
            "g$requested"
        }
        assertEquals("g1", result)
        assertEquals(listOf(0L, 1L), seen)
    }

    @Test
    fun neverReturnsAResultFromBeforeAChangeOnTheLastAttempt() {
        var generation = 0L
        var calls = 0
        assertThrows(PredictionOutdatedException::class.java) {
            runBlocking {
                fetchWithStableGeneration({ generation }) { calls++; generation++; "stale" }
            }
        }
        assertEquals(3, calls)
    }
}
