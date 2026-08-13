package de.lunara.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.guava.await

@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun requestImmediate() {
        enqueueOneTime(IMMEDIATE, ExistingWorkPolicy.KEEP)
    }

    /**
     * Local mutations are committed while [ProcessSyncGate] is held and call this only after
     * the previous worker has left that gate. A separate REPLACE slot therefore cannot cancel
     * an in-progress database/network sync, but it does guarantee that a fresh mutation is not
     * lost while WorkManager is still finalising the preceding WorkSpec.
     */
    fun requestAfterMutation() {
        enqueueOneTime(MUTATION, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueOneTime(uniqueName: String, policy: ExistingWorkPolicy) {
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag(SYNC_TAG)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(uniqueName, policy, work)
    }

    fun schedulePeriodic() {
        val work = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(6))
            .addTag(SYNC_TAG)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    suspend fun cancelAllAndWait() {
        workManager.cancelAllWorkByTag(SYNC_TAG).result.await()
        // Also cancel requests created by versions that predate the common tag.
        workManager.cancelUniqueWork(IMMEDIATE).result.await()
        workManager.cancelUniqueWork(MUTATION).result.await()
        workManager.cancelUniqueWork(PERIODIC).result.await()
    }

    private companion object {
        const val IMMEDIATE = "lunara-sync-now"
        const val MUTATION = "lunara-sync-mutation"
        const val PERIODIC = "lunara-sync-periodic"
        const val SYNC_TAG = "lunara-sync"
    }
}
