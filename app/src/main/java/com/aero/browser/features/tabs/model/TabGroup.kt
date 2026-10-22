package com.aero.browser.features.tabs.model

/** Logical Chrome-style tab group. */
data class TabGroup(
    val id: String,
    val title: String,
    val colorArgb: Long,
    val tabIds: List<String> = emptyList(),
)
