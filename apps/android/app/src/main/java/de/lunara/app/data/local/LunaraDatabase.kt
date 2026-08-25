package de.lunara.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PeriodEntity::class,
        DailyLogEntity::class,
        MutationEntity::class,
        SyncMetadataEntity::class,
        PartnerDashboardEntity::class,
        PortableImportJournalEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LunaraDatabase : RoomDatabase() {
    abstract fun periodDao(): PeriodDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun mutationDao(): MutationDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun partnerDashboardDao(): PartnerDashboardDao
    abstract fun portableImportJournalDao(): PortableImportJournalDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `portable_import_journal` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `settingsJson` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
