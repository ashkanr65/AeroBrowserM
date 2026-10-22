package com.aero.browser

import com.aero.browser.features.history.ClearDataRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ClearDataRangeTest {
    @Test
    fun `today starts at local midnight`() {
        val now = Calendar.getInstance().apply {
            set(2025, Calendar.MARCH, 17, 16, 42, 21)
            set(Calendar.MILLISECOND, 321)
        }.timeInMillis
        val expected = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected, ClearDataRange.TODAY.cutoff(now))
    }

    @Test
    fun `last week covers today and prior six calendar days`() {
        val now = Calendar.getInstance().apply { set(2025, Calendar.JANUARY, 5, 12, 0, 0) }.timeInMillis
        val expected = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected, ClearDataRange.LAST_WEEK.cutoff(now))
    }

    @Test
    fun `last month subtracts a calendar month and full is unbounded`() {
        val now = Calendar.getInstance().apply { set(2025, Calendar.MARCH, 31, 19, 0, 0) }.timeInMillis
        val expected = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected, ClearDataRange.LAST_MONTH.cutoff(now))
        assertNull(ClearDataRange.FULL.cutoff(now))
    }
}
