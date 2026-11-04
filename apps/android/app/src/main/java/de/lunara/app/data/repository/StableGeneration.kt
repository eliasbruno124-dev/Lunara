package de.lunara.app.data.repository

/** Thrown when the cycle settings kept changing while a prediction was being loaded. */
internal class PredictionOutdatedException : IllegalStateException("Cycle settings changed while loading the prediction")

/**
 * Runs [fetch] until no settings change happened during the call. Every attempt is checked, so a
 * result from before a change is never returned. After [attempts] unstable tries this gives up with
 * [PredictionOutdatedException]; callers keep their previous value and refresh again later.
 */
internal suspend fun <T> fetchWithStableGeneration(
    currentGeneration: () -> Long,
    attempts: Int = 3,
    fetch: suspend (generation: Long) -> T,
): T {
    require(attempts > 0)
    repeat(attempts) {
        val generation = currentGeneration()
        val result = fetch(generation)
        if (generation == currentGeneration()) return result
    }
    throw PredictionOutdatedException()
}
