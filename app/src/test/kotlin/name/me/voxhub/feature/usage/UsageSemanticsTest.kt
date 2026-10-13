package name.me.voxhub.feature.usage

import java.util.Calendar
import java.util.TimeZone
import name.me.voxhub.domain.model.UsageOverview
import org.junit.Assert.*
import org.junit.Test

class UsageSemanticsTest {
    @Test fun successRateExcludesCancelledRunningAndInterrupted() {
        val overview = UsageOverview(count = 100, succeeded = 3, failed = 1, cancelled = 90, running = 4, interrupted = 2)
        assertEquals(0.75, overview.successRate!!, 0.0)
        assertNull(UsageOverview(cancelled = 10, running = 1, interrupted = 2).successRate)
    }

    @Test fun localStartUsesCalendarDaysAcrossDst() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val now = Calendar.getInstance().apply {
                clear(); set(2026, Calendar.MARCH, 8, 12, 0)
            }.timeInMillis
            val start = localStart(now)
            val end = localStart(now, 1)
            assertEquals(23 * 60 * 60 * 1000L, end - start)
            val local = Calendar.getInstance().apply { timeInMillis = start }
            assertEquals(8, local.get(Calendar.DAY_OF_MONTH))
            assertEquals(0, local.get(Calendar.HOUR_OF_DAY))
            assertEquals(start, localStart(start + 12 * 60 * 60 * 1000))
        } finally { TimeZone.setDefault(previous) }
    }
}
