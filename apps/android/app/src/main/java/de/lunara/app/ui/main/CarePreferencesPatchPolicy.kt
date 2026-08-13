package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.UpdateCarePreferencesRequest

internal enum class CarePreferenceOption {
    WARMTH,
    CUDDLING,
    SPACE,
    SNACKS,
    CONVERSATION,
    HOUSEHOLD_HELP,
    REMINDERS,
    NO_UNSOLICITED_ADVICE,
}

internal object CarePreferencesPatchPolicy {
    fun request(option: CarePreferenceOption, enabled: Boolean): UpdateCarePreferencesRequest = when (option) {
        CarePreferenceOption.WARMTH -> UpdateCarePreferencesRequest(likesWarmth = enabled)
        CarePreferenceOption.CUDDLING -> UpdateCarePreferencesRequest(likesCuddling = enabled)
        CarePreferenceOption.SPACE -> UpdateCarePreferencesRequest(likesSpace = enabled)
        CarePreferenceOption.SNACKS -> UpdateCarePreferencesRequest(likesSnacks = enabled)
        CarePreferenceOption.CONVERSATION -> UpdateCarePreferencesRequest(likesConversation = enabled)
        CarePreferenceOption.HOUSEHOLD_HELP -> UpdateCarePreferencesRequest(likesHouseholdHelp = enabled)
        CarePreferenceOption.REMINDERS -> UpdateCarePreferencesRequest(likesReminders = enabled)
        CarePreferenceOption.NO_UNSOLICITED_ADVICE ->
            UpdateCarePreferencesRequest(dislikesUnsolicitedAdvice = enabled)
    }
}
