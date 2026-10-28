package com.example.gavanhomes.ui.common

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Swipe from the left side of the screen to the right = Back. It presses the same Back as the key (so a form's
 * "Discard changes?" still asks), and it only watches: it never consumes touches, so lists and buttons work as usual.
 */
@Composable
fun Modifier.swipeBack(enabled: Boolean): Modifier {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val on by rememberUpdatedState(enabled)
    val trigger = with(LocalDensity.current) { 90.dp.toPx() }
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startsOnLeft = down.position.x < size.width * 0.35f
            var dx = 0f
            var dy = 0f
            var fired = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                dx = change.position.x - down.position.x
                dy = change.position.y - down.position.y
                if (!fired && on && startsOnLeft && dx > trigger && abs(dx) > abs(dy) * 2f) {
                    fired = true
                    dispatcher?.onBackPressed()
                }
            }
        }
    }
}
