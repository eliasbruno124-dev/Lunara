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

    /** A user action deliberately replaces only the manual slot, never mutation retries. */
    fun requestManual() {
        enqueueOneTime(MANUAL, ExistingWorkPolicy.REPLACE)
    }

    /**
     * Local mutations are committed while [ProcessSyncGate] is held and call this only after
     * the previous worker has left that gate. APPEND_OR_REPLACE preserves an existing WorkManager
     * retry/backoff instead of cancelling the only scheduled retry when another local edit is
     * made. If the old chain is terminal, the new request becomes a fresh root.
     */
    fun requestAfterMutation() {
        enqueueOneTime(MUTATION, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /** Continues a deliberately capped successful drain without WorkManager retry backoff. */
    fun requestContinuation() {
        enqueueOneTime(CONTINUATION, ExistingWorkPolicy.APPEND_OR_REPLACE)
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
        workManager.cancelUniqueWork(MANUAL).result.await()
        workManager.cancelUniqueWork(MUTATION).result.await()
        workManager.cancelUniqueWork(CONTINUATION).result.await()
        workManager.cancelUniqueWork(PERIODIC).result.await()
    }

    private companion object {
        const val IMMEDIATE = "lunara-sync-now"
        const val MANUAL = "lunara-sync-manual"
        const val MUTATION = "lunara-sync-mutation"
        const val CONTINUATION = "lunara-sync-continuation"
        const val PERIODIC = "lunara-sync-periodic"
        const val SYNC_TAG = "lunara-sync"
    }
}
