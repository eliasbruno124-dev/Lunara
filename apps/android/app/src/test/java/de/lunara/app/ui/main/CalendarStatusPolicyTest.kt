package de.lunara.app.ui.main

import com.google.gson.Gson
import de.lunara.app.data.remote.dto.FertilityEstimateDto
import de.lunara.app.data.remote.dto.PartnerCalendarAccessDto
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PredictionDto
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarStatusPolicyTest {
    private fun status(dashboard: PartnerDashboardDto?, unavailable: Boolean = false, refreshing: Boolean = false) =
        CalendarStatusPolicy.partner(dashboard, unavailable, refreshing)

    @Test fun initialRefreshTakesPrecedenceOverDefaultUnavailable() {
        val actual = status(null, unavailable = true, refreshing = true)
        assertEquals(CalendarLoadStatus.LOADING, actual.load)
        assertEquals(CalendarAccessStatus.LOADING, actual.periodAccess)
        assertEquals(CalendarAccessStatus.LOADING, actual.fertilityAccess)
        assertNull(actual.periodNotice)
        assertNull(actual.fertilityNotice)
        assertFalse(actual.showPeriodDates)
    }

    @Test fun missingSnapshotIsUnavailableNotDenied() {
        for (unavailable in listOf(false, true)) {
            val actual = status(null, unavailable)
            assertEquals(CalendarLoadStatus.UNAVAILABLE, actual.load)
            assertEquals(CalendarAccessStatus.UNKNOWN, actual.periodAccess)
            assertEquals(CalendarAccessStatus.UNKNOWN, actual.fertilityAccess)
            assertNull(actual.periodNotice)
            assertNull(actual.fertilityNotice)
        }
    }

    @Test fun configuredGsonKeepsServerDateOnlyContractAndFertilitySafety() {
        // ApiClientFactory uses GsonConverterFactory.create(): the default Gson configuration.
        val dashboard = Gson().fromJson("""
            {"sharingPaused":false,"calendarAccess":{"periodDates":true,"fertileWindow":true},
             "periodDates":[{"startDate":"2026-10-03","endDate":"2026-10-05"}],
             "prediction":{"confidence":"MEDIUM","fertileWindowFrom":"2026-10-15","fertileWindowTo":"2026-10-17",
              "fertilityEstimate":{"enabled":true,"available":true,"estimated":true,"reason":"AVAILABLE",
               "basedOnCycleCount":3,"notContraception":true,"doesNotConfirmOvulation":true}}}
        """, PartnerDashboardDto::class.java)
        val range = dashboard.periodDates.single()
        assertEquals("2026-10-03", range["startDate"])
        for (day in 3..5) assertTrue(calendarRangeContains(LocalDate.of(2026, 10, day), range["startDate"] as String, range["endDate"] as String))
        for (day in listOf(2, 6)) assertFalse(calendarRangeContains(LocalDate.of(2026, 10, day), range["startDate"] as String, range["endDate"] as String))
        assertEquals(PredictionDateRange("2026-10-15", "2026-10-17"), PredictionPresentationPolicy.fertileWindow(dashboard.prediction))
        assertEquals(CalendarAccessStatus.SHARED, status(dashboard).periodAccess)
        assertEquals(CalendarAccessStatus.SHARED, status(dashboard).fertilityAccess)
        assertNull(status(dashboard).periodNotice)
        assertNull(status(dashboard).fertilityNotice)
        // An instant is not a date-only value. Do not silently invent a time-zone conversion.
        assertFalse(calendarRangeContains(LocalDate.of(2026, 10, 3), "2026-10-03T00:00:00.000Z", "2026-10-05"))
    }

    @Test fun oldServerAndPartialAccessObjectStayUnknown() {
        for (json in listOf("{}", "{\"calendarAccess\":{}}", "{\"calendarAccess\":null}")) {
            val dashboard = Gson().fromJson(json, PartnerDashboardDto::class.java)
            val actual = status(dashboard)
            assertEquals(CalendarAccessStatus.UNKNOWN, actual.periodAccess)
            assertEquals(CalendarAccessStatus.UNKNOWN, actual.fertilityAccess)
            assertEquals(CalendarPeriodNotice.UNKNOWN, actual.periodNotice)
            assertEquals(CalendarFertilityNotice.UNAVAILABLE, actual.fertilityNotice)
        }
    }

    @Test fun oldServerSharedDatesRemainVisibleWithoutClaimingKnownPermission() {
        val dashboard = Gson().fromJson("""{"periodDates":[{"startDate":"2026-10-03","endDate":"2026-10-05"}]}""", PartnerDashboardDto::class.java)
        val actual = status(dashboard)
        assertTrue(actual.showPeriodDates)
        assertNull(actual.periodNotice)
        assertEquals(CalendarAccessStatus.UNKNOWN, actual.periodAccess)
    }

    @Test fun knownPermissionAndNoRecordsDoesNotMeanDenied() {
        val actual = status(PartnerDashboardDto(calendarAccess = PartnerCalendarAccessDto(true, true)))
        assertEquals(CalendarPeriodNotice.EMPTY, actual.periodNotice)
        assertEquals(CalendarFertilityNotice.UNAVAILABLE, actual.fertilityNotice)
    }

    @Test fun cachedErrorKeepsAllowedSnapshotAndLabelsItStale() {
        val actual = status(sharedDashboard(), unavailable = true, refreshing = true)
        assertEquals(CalendarLoadStatus.CACHED, actual.load)
        assertTrue(actual.refreshing)
        assertTrue(actual.showPeriodDates)
        assertTrue(actual.showFertility)
        assertTrue(actual.showPrediction)
        assertEquals(CalendarAccessStatus.SHARED, actual.periodAccess)
    }

    @Test fun explicitDenialWinsOverAccidentallyPresentDataAndPrivateReasons() {
        val actual = status(sharedDashboard().copy(calendarAccess = PartnerCalendarAccessDto(false, false)))
        assertFalse(actual.showPeriodDates)
        assertFalse(actual.showFertility)
        assertEquals(CalendarPeriodNotice.NOT_SHARED, actual.periodNotice)
        assertEquals(CalendarFertilityNotice.NOT_SHARED, actual.fertilityNotice)
        assertEquals(CalendarAccessStatus.NOT_SHARED, actual.periodAccess)
        assertEquals(CalendarAccessStatus.NOT_SHARED, actual.fertilityAccess)
    }

    @Test fun pauseOverridesEvenCachedStaleAllowedFields() {
        val actual = status(sharedDashboard().copy(sharingPaused = true), unavailable = true)
        assertTrue(actual.sharingPaused)
        assertFalse(actual.showPeriodDates)
        assertFalse(actual.showFertility)
        assertFalse(actual.showPrediction)
        assertNull(actual.periodNotice)
        assertNull(actual.fertilityNotice)
        assertEquals(CalendarAccessStatus.PAUSED, actual.periodAccess)
        assertEquals(CalendarAccessStatus.PAUSED, actual.fertilityAccess)
    }

    @Test fun onlyExplicitNestedWhitelistedFertilityReasonsAreExplained() {
        val expected = mapOf("DISABLED" to CalendarFertilityNotice.DISABLED,
            "INSUFFICIENT_DATA" to CalendarFertilityNotice.INSUFFICIENT_DATA,
            "UNRELIABLE_DATA" to CalendarFertilityNotice.UNRELIABLE_DATA)
        for ((reason, notice) in expected) {
            val prediction = PredictionDto(confidence = "LOW", reason = "UNRELATED", fertilityEstimate = FertilityEstimateDto(reason = reason))
            assertEquals(notice, status(PartnerDashboardDto(prediction = prediction)).fertilityNotice)
            assertEquals(CalendarFertilityNotice.UNAVAILABLE, status(PartnerDashboardDto(prediction = prediction.copy(reason = reason, fertilityEstimate = null))).fertilityNotice)
        }
        val unknown = PredictionDto(confidence = "LOW", fertilityEstimate = FertilityEstimateDto(reason = "FUTURE_REASON"))
        assertEquals(CalendarFertilityNotice.UNAVAILABLE, status(PartnerDashboardDto(prediction = unknown)).fertilityNotice)
    }

    @Test fun incompleteSafetyNeverProducesAnAvailableEstimate() {
        val dashboard = sharedDashboard()
        val unsafe = dashboard.prediction!!.copy(fertilityEstimate = dashboard.prediction.fertilityEstimate!!.copy(notContraception = false))
        assertNull(PredictionPresentationPolicy.fertileWindow(unsafe))
        assertEquals(CalendarFertilityNotice.UNAVAILABLE, status(dashboard.copy(prediction = unsafe)).fertilityNotice)
    }

    private fun sharedDashboard() = PartnerDashboardDto(
        calendarAccess = PartnerCalendarAccessDto(true, true),
        periodDates = listOf(mapOf("startDate" to "2026-10-03", "endDate" to "2026-10-05")),
        prediction = PredictionDto(confidence = "MEDIUM", fertileWindowFrom = "2026-10-15", fertileWindowTo = "2026-10-17",
            fertilityEstimate = FertilityEstimateDto(true, true, true, "AVAILABLE", 3, true, true)),
    )
}
