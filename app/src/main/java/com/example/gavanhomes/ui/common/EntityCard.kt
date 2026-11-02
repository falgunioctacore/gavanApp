package com.example.gavanhomes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one list card of the app (people, professionals, HR records), in the same tight style as the project cards:
 *   line 1   a small avatar tile · title / subtitle · optional trailing widget · ⋯
 *   line 2   chips (status, tags) and the key numbers side by side, with call / mail at the end
 *   meta     one muted line (dates …), only when there is one
 * Each screen fills the slots, so every list looks the same.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntityCard(
    title: String,
    subtitle: String?,
    accent: Pair<Color, Color>,
    onClick: () -> Unit,
    onMore: () -> Unit,
    avatarText: String = title.trim().take(1).uppercase(),
    trailing: @Composable () -> Unit = {},
    chips: @Composable () -> Unit = {},
    meta: String? = null,
    footerLeft: @Composable RowScope.() -> Unit = {},
    footerRight: @Composable RowScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.appear().padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().shadow(2.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
            .clip(shape).background(KitCard).bounceClick { onClick() }.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(accent.first, accent.second))),
                contentAlignment = Alignment.Center,
            ) { Text(avatarText.ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 11.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { onMore() }, contentAlignment = Alignment.Center) {
                Text("⋯", color = KitMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.padding(top = 7.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                chips()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { footerLeft() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { footerRight() }
        }
        if (!meta.isNullOrBlank()) Text(meta, fontSize = 11.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, end = 8.dp))
    }
}

/** A footer figure: bold number and a muted label ("12 tasks"). */
@Composable
fun CardStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = KitInk)
        Text(" $label", fontSize = 11.sp, color = KitMuted)
    }
}

/** A round tinted action button of a card footer (call, mail). */
@Composable
fun CardAction(glyph: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = .15f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
