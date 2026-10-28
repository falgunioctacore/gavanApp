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
 * The one list card of the app (projects, people, professionals, HR records): a quiet white card with a hairline border —
 *   header   avatar tile · title / subtitle · optional trailing widget (e.g. a progress ring)
 *   chips    status and tags
 *   meta     one muted line (dates …)
 *   footer   key numbers on the left; call / mail actions and ⋯ on the right
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
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(3.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
            .clip(shape).background(Color.White).border(1.dp, Color(0xFFEFECE6), shape).clickable { onClick() }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(accent.first, accent.second))),
                contentAlignment = Alignment.Center,
            ) { Text(avatarText.ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            trailing()
        }

        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { chips() }

        if (!meta.isNullOrBlank()) Text(meta, fontSize = 12.sp, color = KitMuted, modifier = Modifier.padding(top = 10.dp))

        Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(Color(0xFFF2F0EB)))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { footerLeft() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                footerRight()
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF3F2F0)).clickable { onMore() }, contentAlignment = Alignment.Center) {
                    Text("⋯", color = KitInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** A footer figure: bold number and a muted label ("12 tasks"). */
@Composable
fun CardStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk)
        Text(" $label", fontSize = 12.sp, color = KitMuted)
    }
}

/** A round tinted action button of a card footer (call, mail). */
@Composable
fun CardAction(glyph: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = .15f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
