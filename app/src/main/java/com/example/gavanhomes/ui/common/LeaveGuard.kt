package com.example.gavanhomes.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Leaving a form (the Back key or the ← button): straight out when nothing was changed, otherwise a "Discard changes?"
 * confirmation first. Returns the function the ← button should call; the Back key is handled here.
 */
@Composable
fun leaveGuard(dirty: Boolean, onLeave: () -> Unit): () -> Unit {
    var ask by remember { mutableStateOf(false) }
    BackHandler(enabled = true) { if (dirty) ask = true else onLeave() }
    if (ask) SwalConfirm(
        title = "Discard changes?", text = "What you entered won't be saved.", confirmText = "Yes, discard", cancelText = "Keep editing",
        onConfirm = { ask = false; onLeave() }, onCancel = { ask = false },
    )
    return { if (dirty) ask = true else onLeave() }
}
