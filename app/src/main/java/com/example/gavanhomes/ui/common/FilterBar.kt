package com.example.gavanhomes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** One choice inside a filter group; [count] is how many records have it, [color] a status dot. */
class FilterOption(val value: String, val label: String, val count: Int? = null, val color: Color? = null)

/** A category of filters (e.g. Status, Role); any number of its options can be chosen. */
class FilterGroup(val key: String, val title: String, val options: List<FilterOption>)

private val Ink = Color(0xFF0F172A)
private val Blue = Color(0xFF2874F0)       // the "apply" blue
private val Rail = Color(0xFFF1F3F6)       // the grey category rail
private val Line = Color(0xFFE0E0E0)

/**
 * The app's filter experience, modelled on Flipkart's:
 *  • a search box, and under it a split bar with **Sort** and **Filter** (a badge counts the applied filters);
 *  • the applied filters as removable chips;
 *  • **Filter** opens a full page: categories down the left (a dot marks the ones in use), checkboxes for the chosen category
 *    on the right (pick as many as you like), a live result count, and Close / Apply at the bottom;
 *  • **Sort** opens a sheet with one choice.
 *
 * [selected] maps a group key to the chosen option values; [countWith] says how many records a candidate selection would show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    query: String, onQuery: (String) -> Unit, placeholder: String,
    groups: List<FilterGroup>, selected: Map<String, Set<String>>, onApply: (Map<String, Set<String>>) -> Unit,
    countWith: (Map<String, Set<String>>) -> Int,
    sortOptions: List<String> = emptyList(), sortIndex: Int = 0, onSort: (Int) -> Unit = {},
) {
    var filterOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val applied = groups.flatMap { g -> (selected[g.key] ?: emptySet()).mapNotNull { v -> g.options.firstOrNull { it.value == v }?.let { g to it } } }
    val badge = applied.size

    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.material3.TextField(
            value = query, onValueChange = onQuery, singleLine = true, placeholder = { Text(placeholder, color = KitMuted) },
            leadingIcon = { Text("⌕", color = KitMuted, fontSize = 20.sp) },
            trailingIcon = if (query.isNotEmpty()) ({ Text("✕", color = KitMuted, fontSize = 15.sp, modifier = Modifier.clip(CircleShape).clickable { onQuery("") }.padding(10.dp)) }) else null,
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = KitInk, unfocusedTextColor = KitInk, cursorColor = KitInk,
            ),
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(16.dp)),
        )

        // the split Sort | Filter bar
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(50.dp).shadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sortOptions.isNotEmpty()) {
                Row(Modifier.weight(1f).fillMaxHeight().clickable { sortOpen = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("⇅", color = KitInk, fontSize = 17.sp)
                    Text("  Sort", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("  ·  " + sortOptions.getOrElse(sortIndex) { "" }, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Box(Modifier.width(1.dp).height(26.dp).background(Line))
            }
            Row(Modifier.weight(1f).fillMaxHeight().clickable { filterOpen = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("⚲", color = KitInk, fontSize = 17.sp)
                Text("  Filter", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (badge > 0) Box(Modifier.padding(start = 8.dp).size(20.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) {
                    Text(badge.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        if (applied.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            applied.forEach { (g, o) ->
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(20.dp))
                        .clickable { onApply(selected + (g.key to (selected[g.key].orEmpty() - o.value))) }.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(o.label, color = KitInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("✕", color = KitMuted, fontSize = 11.sp)
                }
            }
            Text("Clear all", color = Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onApply(emptyMap()) }.padding(horizontal = 10.dp, vertical = 8.dp))
        }
    }

    // ── Sort ──
    if (sortOpen) ModalBottomSheet(onDismissRequest = { sortOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("SORT BY", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.8.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
            sortOptions.forEachIndexed { i, label ->
                val on = sortIndex == i
                Row(Modifier.fillMaxWidth().clickable { onSort(i); sortOpen = false }.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = if (on) Blue else KitInk, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) Blue else Color(0xFFB0B0B0), CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(11.dp).clip(CircleShape).background(Blue))
                    }
                }
                Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F0)))
            }
        }
    }

    // ── Filter page ──
    if (filterOpen) FilterPage(groups, selected, countWith, onClose = { filterOpen = false }, onApply = { onApply(it); filterOpen = false })
}

@Composable
private fun FilterPage(groups: List<FilterGroup>, selected: Map<String, Set<String>>, countWith: (Map<String, Set<String>>) -> Int, onClose: () -> Unit, onApply: (Map<String, Set<String>>) -> Unit) {
    var draft by remember { mutableStateOf(selected) }
    var active by remember { mutableIntStateOf(0) }
    val group = groups.getOrNull(active)
    val count = countWith(draft)
    val total = draft.values.sumOf { it.size }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
            // top bar
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("←", fontSize = 22.sp, color = KitInk) }
                Text("Filters", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = KitInk, modifier = Modifier.weight(1f).padding(start = 6.dp))
                Text(
                    "CLEAR ALL", color = if (total > 0) Blue else KitMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = total > 0) { draft = emptyMap() }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))

            Row(Modifier.weight(1f).fillMaxWidth()) {
                // left rail: the categories
                Column(Modifier.width(128.dp).fillMaxHeight().background(Rail)) {
                    groups.forEachIndexed { i, g ->
                        val on = i == active
                        val n = draft[g.key]?.size ?: 0
                        Row(
                            Modifier.fillMaxWidth().background(if (on) Color.White else Color.Transparent).clickable { active = i }.padding(vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(4.dp).height(22.dp).background(if (on) Blue else Color.Transparent))
                            Text(g.title, color = if (on) Blue else KitInk, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 14.dp), maxLines = 2)
                            if (n > 0) Box(Modifier.padding(end = 12.dp).size(18.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) { Text(n.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    }
                }
                // right: the options of the open category, as checkboxes
                if (group != null) LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                    items(group.options, key = { it.value }) { o ->
                        val on = o.value in (draft[group.key] ?: emptySet())
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val cur = draft[group.key] ?: emptySet()
                                draft = draft + (group.key to if (on) cur - o.value else cur + o.value)
                            }.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(if (on) Blue else Color.Transparent).border(2.dp, if (on) Blue else Color(0xFF9E9E9E), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center,
                            ) { if (on) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                            o.color?.let { Box(Modifier.padding(start = 14.dp).size(9.dp).clip(CircleShape).background(it)) }
                            Text(o.label, color = KitInk, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = if (o.color == null) 14.dp else 8.dp), maxLines = 2)
                            o.count?.let { Text("($it)", color = KitMuted, fontSize = 13.sp) }
                        }
                        Box(Modifier.padding(start = 54.dp).fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F0)))
                    }
                }
            }

            // bottom bar: Close · N results · Apply
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Close", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clickable { onClose() }.padding(vertical = 20.dp))
                Box(Modifier.width(1.dp).height(30.dp).background(Line))
                Column(Modifier.weight(1.4f).fillMaxHeight().background(Blue).clickable { onApply(draft) }, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Apply", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    if (count >= 0) Text("$count ${if (count == 1) "result" else "results"}", color = Color.White.copy(alpha = .85f), fontSize = 11.sp)   // a negative count = not known without asking the server
                }
            }
        }
    }
}

/**
 * The cards / compact switch that sits at the right of a list's "N results" line: two icons in one rounded segment,
 * the active one highlighted. Cards = a big square; compact = three rows.
 */
@Composable
fun ViewToggle(compact: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun Seg(on: Boolean, target: Boolean, content: @Composable () -> Unit) {
            Box(
                Modifier.size(width = 38.dp, height = 30.dp).clip(RoundedCornerShape(9.dp)).background(if (on) Ink else Color.Transparent).clickable { onChange(target) },
                contentAlignment = Alignment.Center,
            ) { content() }
        }
        // cards
        Seg(!compact, false) {
            val c = if (!compact) Color.White else KitMuted
            Box(Modifier.size(15.dp).border(2.dp, c, RoundedCornerShape(4.dp)))
        }
        // compact list
        Seg(compact, true) {
            val c = if (compact) Color.White else KitMuted
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                repeat(3) { Box(Modifier.size(width = 15.dp, height = 2.5.dp).clip(RoundedCornerShape(2.dp)).background(c)) }
            }
        }
    }
}
