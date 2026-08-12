package de.lunara.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PeriodEntity::class,
        DailyLogEntity::class,
        MutationEntity::class,
        SyncMetadataEntity::class,
        PartnerDashboardEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LunaraDatabase : RoomDatabase() {
    abstract fun periodDao(): PeriodDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun mutationDao(): MutationDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun partnerDashboardDao(): PartnerDashboardDao
}
