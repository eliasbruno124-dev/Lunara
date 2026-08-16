package de.lunara.app.ui

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.mutableStateOf
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.ui.main.CalendarScreen
import de.lunara.app.ui.theme.LunaraTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarPeriodComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun trackerCanNavigateBackAndAddACompleteHistoricalPeriod() {
        val month = YearMonth.now().minusMonths(1)
        val start = month.atDay(3)
        val end = month.atDay(minOf(7, month.lengthOfMonth()))
        var created: Pair<LocalDate, LocalDate?>? = null
        compose.setContent {
            LunaraTheme {
                CalendarScreen(
                    periods = emptyList(),
                    role = UserRole.TRACKER,
                    prediction = null,
                    sharedPeriodDates = emptyList(),
                    onPeriodAdded = { selectedStart, selectedEnd ->
                        created = selectedStart to selectedEnd
                    },
                    onPeriodUpdated = { _, _, _ -> },
                    onPeriodDeleted = {},
                )
            }
        }

        compose.onNodeWithTag("calendar-previous-month").performClick()
        compose.onNodeWithTag("calendar-day-$start").assertIsDisplayed().performClick()
        compose.onNodeWithTag("period-create-start-input").assertIsDisplayed()
        compose.onNodeWithTag("period-create-end-input").performTextInput(end.toString())
        compose.onNodeWithTag("period-create-confirm").performClick()

        assertEquals(start to end, created)
    }

    @Test
    fun tappingARecordedDayOpensTheExistingPeriodEditor() {
        val month = YearMonth.now().minusMonths(1)
        val start = month.atDay(8)
        val end = month.atDay(12)
        val period = PeriodEntity(
            id = "period-1",
            userId = "tracker",
            startDate = start.toString(),
            endDate = end.toString(),
            createdAt = "",
            updatedAt = "",
            syncState = "SYNCED",
        )
        compose.setContent {
            LunaraTheme {
                CalendarScreen(
                    periods = listOf(period),
                    role = UserRole.TRACKER,
                    prediction = null,
                    sharedPeriodDates = emptyList(),
                    onPeriodAdded = { _, _ -> },
                    onPeriodUpdated = { _, _, _ -> },
                    onPeriodDeleted = {},
                )
            }
        }

        compose.onNodeWithTag("calendar-previous-month").performClick()
        compose.onNodeWithTag("calendar-day-${start.plusDays(2)}").performClick()
        compose.onNodeWithTag("period-start-input").assertIsDisplayed()
        compose.onNodeWithTag("period-end-input").assertIsDisplayed()
    }

    @Test
    fun partnerCalendarRemainsReadOnly() {
        val today = LocalDate.now()
        compose.setContent {
            LunaraTheme {
                CalendarScreen(
                    periods = emptyList(),
                    role = UserRole.PARTNER,
                    prediction = null,
                    sharedPeriodDates = listOf(
                        mapOf("startDate" to today.toString(), "endDate" to today.toString()),
                    ),
                    onPeriodAdded = { _, _ -> error("Partner must not create periods") },
                    onPeriodUpdated = { _, _, _ -> error("Partner must not edit periods") },
                    onPeriodDeleted = { error("Partner must not delete periods") },
                )
            }
        }

        compose.onNodeWithTag("calendar-day-$today").assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun createCannotBeSubmittedTwiceAndClosesOnlyAfterRoomStateConfirmsIt() {
        val month = YearMonth.now().minusMonths(1)
        val start = month.atDay(4)
        val end = month.atDay(minOf(8, month.lengthOfMonth()))
        val periods = mutableStateOf(emptyList<PeriodEntity>())
        var createCalls = 0
        compose.setContent {
            LunaraTheme {
                CalendarScreen(
                    periods = periods.value,
                    role = UserRole.TRACKER,
                    prediction = null,
                    sharedPeriodDates = emptyList(),
                    onPeriodAdded = { _, _ -> createCalls += 1 },
                    onPeriodUpdated = { _, _, _ -> },
                    onPeriodDeleted = {},
                )
            }
        }

        compose.onNodeWithTag("calendar-previous-month").performClick()
        compose.onNodeWithTag("calendar-day-$start").performClick()
        compose.onNodeWithTag("period-create-end-input").performTextInput(end.toString())
        compose.onNodeWithTag("period-create-confirm").performClick()

        compose.onNodeWithTag("period-create-confirm").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, createCalls) }

        compose.runOnIdle {
            periods.value = listOf(
                PeriodEntity(
                    id = "created-period",
                    userId = "tracker",
                    startDate = start.toString(),
                    endDate = end.toString(),
                    createdAt = "",
                    updatedAt = "",
                    syncState = "PENDING",
                ),
            )
        }
        compose.onNodeWithTag("period-create-confirm").assertDoesNotExist()
    }

    @Test
    fun failedCreateKeepsDraftOpenAndUnlocksItForAnExplicitRetry() {
        val month = YearMonth.now().minusMonths(1)
        val start = month.atDay(5)
        val end = month.atDay(minOf(9, month.lengthOfMonth()))
        val failureRevision = mutableStateOf(0L)
        var createCalls = 0
        compose.setContent {
            LunaraTheme {
                CalendarScreen(
                    periods = emptyList(),
                    role = UserRole.TRACKER,
                    prediction = null,
                    sharedPeriodDates = emptyList(),
                    onPeriodAdded = { _, _ -> createCalls += 1 },
                    onPeriodUpdated = { _, _, _ -> },
                    onPeriodDeleted = {},
                    periodFailureRevision = failureRevision.value,
                )
            }
        }

        compose.onNodeWithTag("calendar-previous-month").performClick()
        compose.onNodeWithTag("calendar-day-$start").performClick()
        compose.onNodeWithTag("period-create-end-input").performTextInput(end.toString())
        compose.onNodeWithTag("period-create-confirm").performClick()
        compose.onNodeWithTag("period-create-confirm").assertIsNotEnabled()

        compose.runOnIdle { failureRevision.value += 1 }

        compose.onNodeWithTag("period-create-end-input")
            .assertIsDisplayed()
            .assertTextContains(end.toString())
        compose.onNodeWithTag("period-create-confirm").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, createCalls) }
    }
}
