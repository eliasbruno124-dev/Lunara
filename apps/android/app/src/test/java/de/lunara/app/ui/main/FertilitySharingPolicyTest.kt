package de.lunara.app.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FertilitySharingPolicyTest {
    @Test
    fun `sharing cannot be newly enabled before tracker enables the estimate`() {
        assertFalse(
            FertilitySharingPolicy.mayEnableSharing(
                fertilityPredictionEnabled = false,
                currentShareFertileWindow = false,
                requested = true,
            ),
        )
    }

    @Test
    fun `sharing may be disabled at all times`() {
        assertTrue(
            FertilitySharingPolicy.mayEnableSharing(
                fertilityPredictionEnabled = false,
                currentShareFertileWindow = true,
                requested = false,
            ),
        )
    }
}
