package com.example.gavanhomes.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One thing the floating + button can start: [key] is handed back to the screen, [label] / [glyph] / [color] are what it shows. */
class FabAction(val key: String, val label: String, val color: Color = KitInk, val glyph: String = "+")

/** How far a screen's scroll-to-top button moves up to clear the floating + button. */
val FabLift: Dp = 72.dp

/**
 * The app's create button: a round + at the bottom-right of the screen. A tap always fans the actions out above it (even a single
 * one, like the Inventory menu) as labelled round buttons; a tap outside, or Back, folds them away. Nothing shows when [actions] is empty.
 */
@Composable
fun BoxScope.QuickFab(b: Branding, actions: List<FabAction>, onPick: (String) -> Unit) {
    if (actions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }
    val quiet = remember { MutableInteractionSource() }
    if (open) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .28f)).clickable(indication = null, interactionSource = quiet) { open = false })
    Column(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom), exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(indication = null, interactionSource = quiet) { open = false; onPick(a.key) }) {
                        Text(a.label, color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 9.dp))
                        Spacer(Modifier.size(10.dp))
                        Box(Modifier.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(a.color), contentAlignment = Alignment.Center) {
                            Text(a.glyph, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
        Box(
            Modifier.size(58.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(if (open) KitInk else b.base).clickable { open = !open },
            contentAlignment = Alignment.Center,
        ) { Text(if (open) "✕" else "+", color = Color.White, fontSize = if (open) 22.sp else 30.sp, fontWeight = FontWeight.Bold) }
    }
}

/** What the screen hosting [LocalFab] should show: the open module's create actions and what to do with a pick. */
class FabSpec(val actions: List<FabAction>, val onPick: (String) -> Unit)

/**
 * A module drawn inside a scrolling page can't pin a button to the screen's corner itself, so it hands its actions to the screen
 * through this slot ([ProvideFab]); the screen draws the newest one with [QuickFab]. It is a stack: a page opened inside a module
 * (its Settings, a Lists tab) offers its own actions while it is on screen, and the module's come back when it goes away.
 * Null when the screen offers no slot.
 */
val LocalFab = staticCompositionLocalOf<SnapshotStateList<FabSpec>?> { null }

/** Offer [actions] on the hosting screen's + button while this is on screen (an empty list offers none). */
@Composable
fun ProvideFab(actions: List<FabAction>, onPick: (String) -> Unit) {
    val slot = LocalFab.current ?: return
    val pick by rememberUpdatedState(onPick)
    val spec = remember(actions.map { it.key to it.label }) { FabSpec(actions) { pick(it) } }
    DisposableEffect(slot, spec) {
        slot.add(spec)
        onDispose { slot.remove(spec) }
    }
}
