package com.example.gavanhomes.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App-wide chrome switches a nested screen can flip: [hideBar] hides the footer tabs while a form with its own Save bar is open. */
object Chrome {
    var hideBar by mutableStateOf(false)
}
