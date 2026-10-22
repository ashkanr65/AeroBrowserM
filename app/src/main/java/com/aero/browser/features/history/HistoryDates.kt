package com.aero.browser.features.history

import java.text.DateFormat
import java.util.Calendar
import java.util.Date

internal object HistoryDates {
    fun label(timestamp: Long): String {
        val visit = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(visit, today) -> "Today"
            sameDay(visit, yesterday) -> "Yesterday"
            else -> DateFormat.getDateInstance(DateFormat.LONG).format(Date(timestamp))
        }
    }

    fun sortOrder(label: String, timestamp: Long): Long = when (label) {
        "Today" -> Long.MIN_VALUE
        "Yesterday" -> Long.MIN_VALUE + 1
        else -> Long.MAX_VALUE - timestamp
    }

    private fun sameDay(first: Calendar, second: Calendar): Boolean =
        first.get(Calendar.YEAR) == second.get(Calendar.YEAR) && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}
