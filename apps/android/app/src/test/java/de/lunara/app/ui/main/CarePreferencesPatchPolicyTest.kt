package de.lunara.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarePreferencesPatchPolicyTest {
    @Test
    fun `every care preference maps to exactly its server field`() {
        val expectations = mapOf(
            CarePreferenceOption.WARMTH to "likesWarmth",
            CarePreferenceOption.CUDDLING to "likesCuddling",
            CarePreferenceOption.SPACE to "likesSpace",
            CarePreferenceOption.SNACKS to "likesSnacks",
            CarePreferenceOption.CONVERSATION to "likesConversation",
            CarePreferenceOption.HOUSEHOLD_HELP to "likesHouseholdHelp",
            CarePreferenceOption.REMINDERS to "likesReminders",
            CarePreferenceOption.NO_UNSOLICITED_ADVICE to "dislikesUnsolicitedAdvice",
        )

        expectations.forEach { (option, expectedField) ->
            val request = CarePreferencesPatchPolicy.request(option, true)
            val values = mapOf(
                "likesWarmth" to request.likesWarmth,
                "likesCuddling" to request.likesCuddling,
                "likesSpace" to request.likesSpace,
                "likesSnacks" to request.likesSnacks,
                "likesConversation" to request.likesConversation,
                "likesHouseholdHelp" to request.likesHouseholdHelp,
                "likesReminders" to request.likesReminders,
                "dislikesUnsolicitedAdvice" to request.dislikesUnsolicitedAdvice,
            )

            assertEquals(true, values.getValue(expectedField))
            values.filterKeys { it != expectedField }.values.forEach(::assertNull)
        }
    }

    @Test
    fun `false is sent explicitly instead of being mistaken for no change`() {
        val request = CarePreferencesPatchPolicy.request(CarePreferenceOption.SPACE, false)

        assertEquals(false, request.likesSpace)
        assertNull(request.likesWarmth)
    }
}
