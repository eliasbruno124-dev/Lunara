package de.lunara.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MutationDaoOrderingTest {
    private lateinit var database: LunaraDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            LunaraDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun createAckLeavesDeleteUnresolved() = runBlocking {
        val dao = database.mutationDao()
        dao.enqueue(mutation("create", "client-create", "UPSERT", 0, "2026-08-13T10:00:00.000Z", "IN_FLIGHT"))
        dao.enqueue(mutation("delete", "client-delete", "DELETE", 1, "2026-08-13T10:00:00.001Z"))

        dao.acknowledge(listOf("client-create"))

        assertEquals(1, dao.unresolvedCountForEntity("PERIOD", "period-1"))
        assertEquals(listOf("client-delete"), dao.forEntity("PERIOD", "period-1").map { it.clientMutationId })
    }

    @Test
    fun readyReturnsOnlyOldestMutationForOneEntity() = runBlocking {
        val dao = database.mutationDao()
        dao.enqueue(mutation("create", "client-create", "UPSERT", 0, "2026-08-13T10:00:00.000Z"))
        dao.enqueue(mutation("delete", "client-delete", "DELETE", 1, "2026-08-13T10:00:00.001Z"))

        val ready = dao.ready("2026-08-13T10:00:01.000Z")

        assertEquals(listOf("client-create"), ready.map { it.clientMutationId })
    }

    @Test
    fun resetFailedPreservesAttemptHistory() = runBlocking {
        val dao = database.mutationDao()
        dao.enqueue(
            mutation(
                id = "failed",
                clientMutationId = "client-failed",
                operation = "UPSERT",
                baseVersion = 2,
                createdAt = "2026-08-13T10:00:00.000Z",
                state = "FAILED",
                attemptCount = 8,
                lastErrorCode = "SocketTimeoutException",
            ),
        )

        assertEquals(1, dao.resetFailed("2026-08-13T11:00:00.000Z"))

        val reset = dao.forEntity("PERIOD", "period-1").single()
        assertEquals("PENDING", reset.state)
        assertEquals(8, reset.attemptCount)
        assertEquals("2026-08-13T11:00:00.000Z", reset.nextAttemptAt)
    }

    @Test
    fun reviveFailedForEntityPreservesAttemptHistory() = runBlocking {
        val dao = database.mutationDao()
        dao.enqueue(
            mutation(
                id = "failed",
                clientMutationId = "client-failed",
                operation = "DELETE",
                baseVersion = 3,
                createdAt = "2026-08-13T10:00:00.000Z",
                state = "FAILED",
                attemptCount = 5,
                lastErrorCode = "IOException",
            ),
        )

        assertEquals(1, dao.reviveFailedForEntity("PERIOD", "period-1", "2026-08-13T11:00:00.000Z"))

        val revived = dao.forEntity("PERIOD", "period-1").single()
        assertEquals("PENDING", revived.state)
        assertEquals(5, revived.attemptCount)
        assertEquals("2026-08-13T11:00:00.000Z", revived.nextAttemptAt)
    }

    @Test
    fun attemptedPayloadIsImmutable() = runBlocking {
        val dao = database.mutationDao()
        val originalCreatedAt = "2026-08-13T10:00:00.000Z"
        val originalPayload = "{\"id\":\"period-1\",\"startDate\":\"2026-08-10\"}"
        dao.enqueue(
            mutation(
                id = "attempted",
                clientMutationId = "client-attempted",
                operation = "UPSERT",
                baseVersion = 1,
                createdAt = originalCreatedAt,
                attemptCount = 1,
                payloadJson = originalPayload,
            ),
        )

        val replaced = dao.replacePendingPayload(
            entityType = "PERIOD",
            entityId = "period-1",
            payloadJson = "{\"id\":\"period-1\",\"startDate\":\"2026-08-11\"}",
            createdAt = "2026-08-13T11:00:00.000Z",
            nextAttemptAt = "2026-08-13T11:00:00.000Z",
        )

        assertEquals(0, replaced)
        val unchanged = dao.forEntity("PERIOD", "period-1").single()
        assertEquals(originalPayload, unchanged.payloadJson)
        assertEquals(originalCreatedAt, unchanged.createdAt)
        assertEquals(originalCreatedAt, unchanged.nextAttemptAt)
        assertEquals(1, unchanged.attemptCount)
    }

    @Test
    fun manualSyncMakesBackedOffPendingMutationReadyWithoutResettingAttempts() = runBlocking {
        val dao = database.mutationDao()
        dao.enqueue(
            mutation(
                id = "backoff",
                clientMutationId = "client-backoff",
                operation = "UPSERT",
                baseVersion = 2,
                createdAt = "2026-08-13T10:00:00.000Z",
                attemptCount = 3,
            ).copy(nextAttemptAt = "2026-08-13T11:00:00.000Z"),
        )

        assertEquals(1, dao.makePendingReady("2026-08-13T10:30:00.000Z"))

        val ready = dao.ready("2026-08-13T10:30:00.000Z").single()
        assertEquals("client-backoff", ready.clientMutationId)
        assertEquals(3, ready.attemptCount)
    }

    @Test
    fun conflictIsVisibleAndCanBeCleared() = runBlocking {
        val dao = database.mutationDao()
        dao.enqueue(
            mutation(
                id = "conflict",
                clientMutationId = "client-conflict",
                operation = "UPSERT",
                baseVersion = 4,
                createdAt = "2026-08-13T10:00:00.000Z",
                state = "CONFLICT",
            ),
        )
        dao.enqueue(
            mutation(
                id = "follow-up",
                clientMutationId = "client-follow-up",
                operation = "DELETE",
                baseVersion = 5,
                createdAt = "2026-08-13T10:00:00.001Z",
            ),
        )

        assertEquals(2, dao.observeCount().first())
        assertEquals(1, dao.observeConflictCount().first())
        assertEquals(listOf("client-conflict"), dao.conflicts().map { it.clientMutationId })

        dao.deleteForEntity("PERIOD", "period-1")

        assertEquals(0, dao.observeCount().first())
        assertEquals(emptyList<MutationEntity>(), dao.forEntity("PERIOD", "period-1"))
    }

    @Test
    fun unmatchedResolvingRowsAreRemovedOnlyAfterExplicitFinalization() = runBlocking {
        val period = PeriodEntity(
            id = "period-resolving",
            userId = "tracker",
            startDate = "2026-08-13",
            createdAt = "2026-08-13T10:00:00Z",
            updatedAt = "2026-08-13T10:00:00Z",
            version = -1,
            syncState = "RESOLVING",
        )
        database.periodDao().insert(period)

        assertEquals(period, database.periodDao().findById(period.id))
        assertEquals(1, database.periodDao().deleteUnmatchedResolving("tracker"))
        assertEquals(null, database.periodDao().findById(period.id))
    }

    private fun mutation(
        id: String,
        clientMutationId: String,
        operation: String,
        baseVersion: Long,
        createdAt: String,
        state: String = "PENDING",
        attemptCount: Int = 0,
        payloadJson: String = "{}",
        lastErrorCode: String? = null,
    ) = MutationEntity(
        id = id,
        clientMutationId = clientMutationId,
        entityType = "PERIOD",
        entityId = "period-1",
        operation = operation,
        payloadJson = payloadJson,
        baseVersion = baseVersion,
        createdAt = createdAt,
        attemptCount = attemptCount,
        nextAttemptAt = createdAt,
        state = state,
        lastErrorCode = lastErrorCode,
    )
}
