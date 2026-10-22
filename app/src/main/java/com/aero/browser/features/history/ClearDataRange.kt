package com.aero.browser.features.history

import java.util.Calendar

enum class ClearDataRange(val title: String) {
    TODAY("Today"),
    LAST_WEEK("Last week"),
    LAST_MONTH("Last month"),
    FULL("Full");

    fun cutoff(now: Long = System.currentTimeMillis()): Long? {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        return when (this) {
            TODAY -> calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            LAST_WEEK -> calendar.apply {
                add(Calendar.DAY_OF_YEAR, -6)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            LAST_MONTH -> calendar.apply {
                add(Calendar.MONTH, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            FULL -> null
        }
    }
}
