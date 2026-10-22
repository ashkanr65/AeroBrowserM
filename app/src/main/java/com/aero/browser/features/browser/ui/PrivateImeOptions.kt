package com.aero.browser.features.browser.ui

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PlatformImeOptions

/** Requests a non-personalized keyboard session when text is entered in private browsing. */
fun KeyboardOptions.withPrivateIme(privateMode: Boolean): KeyboardOptions = if (privateMode) {
    copy(
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
        platformImeOptions = PlatformImeOptions(privateImeOptions = "noPersonalizedLearning"),
    )
} else this
