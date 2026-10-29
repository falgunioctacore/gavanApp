package com.example.gavanhomes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Dots = listOf(Color(0xFFFFFFFF), Color(0xFFFFC857), Color(0xFF7EE2B8), Color(0xFF8EC5FF), Color(0xFFFF9AA2), Color(0xFFC9B6FF))

/**
 * The header of every list page, drawn the same way: the main number big with its label beside it, an optional bar showing how it splits
 * (when the other numbers are parts of the first), and one small chip per other number. [items] are (value, label) pairs; the first one is the headline.
 */
@Composable
fun HeroSummary(items: List<Pair<String, String>>, bar: Boolean = false) {
    if (items.isEmpty()) return
    val (headValue, headLabel) = items.first()
    val rest = items.drop(1)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(headValue, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 48.sp)
            Text(headLabel, color = Color.White.copy(alpha = .85f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp, bottom = 7.dp))
        }
        if (bar && rest.isNotEmpty()) {
            val parts = rest.mapIndexed { i, (v, _) -> (v.toFloatOrNull() ?: 0f) to Dots[i % Dots.size] }.filter { it.first > 0f }
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .22f)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                parts.forEach { (n, c) -> Box(Modifier.weight(n).fillMaxHeight().background(c)) }
            }
        }
        if (rest.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rest.forEachIndexed { i, (v, l) ->
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .18f)).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Dots[i % Dots.size]))
                    Text("$v $l", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
