package com.example.gavanhomes.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A screen's pages (what used to be its row of tabs): [title] heads the drawer, [selected] is the open one. */
class PageSpec(val title: String, val pages: List<String>, val selected: Int, val onSelect: (Int) -> Unit)

/**
 * A module drawn inside a scrolling page hands its pages to the screen through this slot ([ModulePages]); the screen draws the
 * newest one with [PageDrawer]. A stack, like [LocalFab]: a settings page's own pages take over while it is open.
 */
val LocalPages = staticCompositionLocalOf<SnapshotStateList<PageSpec>?> { null }

/**
 * A module's pages, in place of a tab strip: offered to the screen's bottom-left page drawer when the screen has one, otherwise
 * (no slot) shown as the old tab strip so nothing is lost.
 */
@Composable
fun ModulePages(title: String, pages: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val slot = LocalPages.current
    if (slot == null) { SegTabs(pages, selected, onSelect); return }
    val pick by rememberUpdatedState(onSelect)
    val spec = remember(title, pages, selected) { PageSpec(title, pages, selected) { pick(it) } }
    DisposableEffect(slot, spec) {
        slot.add(spec)
        onDispose { slot.remove(spec) }
    }
}

/**
 * The page menu, in the quick-action style of the + button but at the bottom-left: a pill showing the open page ("▤ Materials");
 * a tap fans every page out above it as labelled round buttons (the open one filled, with ✓) — pick one to go there. A tap
 * outside, or Back, folds them away. Nothing shows for fewer than two pages.
 */
@Composable
fun BoxScope.PageDrawer(b: Branding, spec: PageSpec?) {
    if (spec == null || spec.pages.size < 2) return
    var open by remember { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }
    val quiet = remember { MutableInteractionSource() }

    if (open) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .28f)).clickable(indication = null, interactionSource = quiet) { open = false })
    Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 22.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom), exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            // a long list (Inventory has nine pages) scrolls instead of running off the top of the screen
            Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                spec.pages.forEachIndexed { i, label ->
                    val on = i == spec.selected
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(indication = null, interactionSource = quiet) { open = false; spec.onSelect(i) }) {
                        Box(Modifier.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(if (on) b.base else Color.White), contentAlignment = Alignment.Center) {
                            Text(if (on) "✓" else "${i + 1}", color = if (on) Color.White else KitInk, fontSize = if (on) 18.sp else 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(
                            label, color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp).shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
        Row(
            Modifier.widthIn(max = 220.dp).shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(KitInk).clickable { open = !open }
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(b.base), contentAlignment = Alignment.Center) { Text(if (open) "✕" else "▤", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            Text(if (open) spec.title else spec.pages.getOrElse(spec.selected) { spec.pages[0] }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
        }
    }
}
