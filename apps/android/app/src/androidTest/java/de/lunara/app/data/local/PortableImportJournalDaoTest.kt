package de.lunara.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableImportJournalDaoTest {
    private lateinit var database: LunaraDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            LunaraDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun pendingImportSurvivesUntilExplicitCompletion() = runTest {
        val row = PortableImportJournalEntity(
            userId = "f7dfb598-8da2-4d81-990a-a434795b8e54",
            settingsJson = "{}",
            createdAt = Instant.parse("2026-08-25T12:00:00Z").toString(),
        )

        database.portableImportJournalDao().save(row)
        assertEquals(row, database.portableImportJournalDao().pending())
        assertEquals(1, database.portableImportJournalDao().complete(row.id))
        assertNull(database.portableImportJournalDao().pending())
    }
}
