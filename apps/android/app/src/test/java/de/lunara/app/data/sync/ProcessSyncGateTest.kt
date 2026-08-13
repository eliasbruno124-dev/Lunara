package de.lunara.app.data.sync

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessSyncGateTest {
    @Test
    fun `serializes the complete worker body`() = runTest {
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = launch {
            ProcessSyncGate.serialized {
                val count = active.incrementAndGet()
                maximumActive.updateAndGet { maxOf(it, count) }
                firstEntered.complete(Unit)
                releaseFirst.await()
                active.decrementAndGet()
            }
        }
        firstEntered.await()
        val second = launch {
            ProcessSyncGate.serialized {
                val count = active.incrementAndGet()
                maximumActive.updateAndGet { maxOf(it, count) }
                active.decrementAndGet()
            }
        }

        runCurrent()
        assertEquals(1, maximumActive.get())
        releaseFirst.complete(Unit)
        joinAll(first, second)
        assertEquals(1, maximumActive.get())
    }
}
