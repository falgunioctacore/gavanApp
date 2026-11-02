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

/** A small sign for a page, from words in its name; anything else shows its first letter. */
private fun pageGlyph(label: String): String {
    val l = label.lowercase()
    return when {
        "dashboard" in l || "overview" in l -> "▦"
        "setting" in l -> "⚙"
        "trash" in l -> "✕"
        "history" in l || "log" in l || "audit" in l || "activity" in l -> "⟲"
        "calendar" in l || "timeline" in l || "month" in l || "day" in l -> "◷"
        "board" in l -> "▥"
        "report" in l || "performance" in l || "comparison" in l -> "▮"
        "template" in l -> "❏"
        "request" in l || "approval" in l -> "✓"
        "shared" in l -> "⇄"
        "recent" in l -> "↻"
        "payment" in l || "invoice" in l || "expense" in l || "payout" in l -> "₹"
        "list" in l || "all " in l || l.startsWith("all") -> "☰"
        else -> label.trim().take(1).uppercase()
    }
}

/**
 * The page menu at the bottom-left. A dark pill shows the open page and where it is ("Materials · 2/9"); a tap slides up one
 * card — the module's name, then every page as a tile in two columns (the open one filled with the brand colour) — and a tap on
 * a tile goes there. A tap outside, or Back, closes it. Nothing shows for fewer than two pages.
 */
@Composable
fun BoxScope.PageDrawer(b: Branding, spec: PageSpec?) {
    if (spec == null || spec.pages.size < 2) return
    var open by remember { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }
    val quiet = remember { MutableInteractionSource() }
    val selected = spec.selected.coerceIn(0, spec.pages.lastIndex)

    if (open) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .32f)).clickable(indication = null, interactionSource = quiet) { open = false })
    Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 22.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom), exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            Column(
                Modifier.widthIn(max = 380.dp).softShadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(KitCard)
                    .clickable(indication = null, interactionSource = quiet) { }.padding(14.dp),
            ) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(spec.title, color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${spec.pages.size} pages", color = KitMuted, fontSize = 12.sp)
                    }
                    Box(Modifier.size(32.dp).clip(CircleShape).background(KitSoft).clickable { open = false }, contentAlignment = Alignment.Center) {
                        Text("✕", color = KitInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // nine pages (Materials) fit; more scroll inside the card
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    spec.pages.withIndex().chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (i, label) ->
                                val on = i == selected
                                Row(
                                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) b.base else KitSoft)
                                        .clickable { open = false; if (!on) spec.onSelect(i) }.padding(horizontal = 10.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (on) Color.White.copy(alpha = .22f) else KitCard), contentAlignment = Alignment.Center) {
                                        Text(pageGlyph(label), color = if (on) Color.White else b.base, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Text(
                                        label, color = if (on) Color.White else KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 13.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp, modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Row(
            Modifier.widthIn(max = 240.dp).softShadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(KitInkFill).clickable { open = !open }
                .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(b.base), contentAlignment = Alignment.Center) {
                Text(pageGlyph(spec.pages[selected]), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(spec.pages[selected], color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).weight(1f, fill = false))
            Text("  ${selected + 1}/${spec.pages.size}  " + if (open) "▾" else "▴", color = Color.White.copy(alpha = .7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
