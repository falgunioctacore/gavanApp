package com.example.gavanhomes.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One thing to do with the selected rows: a glyph over a short label ("Status", "Assign", "Delete" …). */
class BulkAction(val key: String, val label: String, val glyph: String, val danger: Boolean = false)

/** What the bar shows while rows are selected: how many, what can be done, and what each button does. */
class BulkSpec(val count: Int, val actions: List<BulkAction>, val onAction: (String) -> Unit, val onSelectAll: () -> Unit, val onClear: () -> Unit)

/** The spec of one module, kept stable so the bar can follow the count as it changes. */
class BulkHolder { var spec by mutableStateOf<BulkSpec?>(null) }

/**
 * A list drawn inside a scrolling page can't pin a bar to the screen's bottom itself, so it hands the bar's content to the screen through this
 * slot ([ProvideBulk]); the screen draws the newest one with [BulkBar]. Null when the screen offers no slot.
 */
val LocalBulk = staticCompositionLocalOf<SnapshotStateList<BulkHolder>?> { null }

/** Show the bulk bar on the hosting screen while [spec] is not null (rows are selected). */
@Composable
fun ProvideBulk(spec: BulkSpec?) {
    val slot = LocalBulk.current ?: return
    val holder = remember { BulkHolder() }
    holder.spec = spec
    DisposableEffect(slot, spec != null) {
        if (spec != null) slot.add(holder)
        onDispose { slot.remove(holder) }
    }
}

/**
 * The bulk action bar: a card floating at the bottom of the screen — a close button, "3 selected" and Select all on top, then the actions as
 * round buttons with a label under each (Delete in red). Slides up when the first row is selected and away when the selection is cleared.
 */
@Composable
fun BoxScope.BulkBar(spec: BulkSpec?) {
    var last by remember { mutableStateOf<BulkSpec?>(null) }
    if (spec != null) last = spec
    AnimatedVisibility(
        visible = spec != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(240)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
    ) {
        val s = last ?: return@AnimatedVisibility
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth().softShadow(14.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF1B1E26)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable { s.onClear() }, contentAlignment = Alignment.Center) {
                    Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("${s.count} selected", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("Choose what to do with them", color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
                }
                Text("Select all", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .12f)).clickable { s.onSelectAll() }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .1f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                s.actions.forEach { a ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { s.onAction(a.key) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(if (a.danger) Color(0xFFD03B3B).copy(alpha = .28f) else Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                            Text(a.glyph, color = if (a.danger) Color(0xFFFF8A80) else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(a.label, color = if (a.danger) Color(0xFFFF8A80) else Color.White.copy(alpha = .92f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}
