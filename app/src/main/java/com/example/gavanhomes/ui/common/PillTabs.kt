package com.example.gavanhomes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One tab: its label and an optional count shown in a small badge. */
class PillTab(val label: String, val count: Int? = null)

/**
 * A segmented tab bar: a soft grey track with the open tab lifted onto a white pill (bold label, a count badge in the brand colour).
 * With many tabs it scrolls sideways and keeps the open one in view.
 */
@Composable
fun PillTabs(tabs: List<PillTab>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    val scroll = rememberScrollState()
    val lefts = remember { mutableMapOf<Int, Int>() }
    val widths = remember { mutableMapOf<Int, Int>() }
    var viewport by remember { mutableIntStateOf(0) }
    LaunchedEffect(selected, viewport) {
        val l = lefts[selected] ?: return@LaunchedEffect
        val w = widths[selected] ?: 0
        scroll.animateScrollTo((l - (viewport - w) / 2).coerceAtLeast(0))      // keep the open tab in the middle of the bar
    }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFEAE7E1)).padding(4.dp).onSizeChanged { viewport = it.width }) {
        Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                Row(
                    Modifier.onGloballyPositioned { lefts[i] = it.positionInParent().x.toInt(); widths[i] = it.size.width }
                        .then(if (on) Modifier.shadow(3.dp, RoundedCornerShape(16.dp)) else Modifier)
                        .clip(RoundedCornerShape(16.dp)).background(if (on) Color.White else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(t.label, fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) KitInk else KitMuted, maxLines = 1)
                    if (t.count != null && t.count > 0) Box(
                        Modifier.clip(CircleShape).background(if (on) accent else Color(0xFFD5D1C9)).padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(t.count.toString(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Color.White else KitInk) }
                }
            }
        }
    }
}
