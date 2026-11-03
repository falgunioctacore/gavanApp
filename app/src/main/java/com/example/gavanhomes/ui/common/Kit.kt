package com.example.gavanhomes.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal val KitInk: Color get() = if (AppTheme.dark) Color(0xFFEDEBE6) else Color(0xFF1F2A44)
internal val KitMuted: Color get() = if (AppTheme.dark) Color(0xFFA19E97) else Color(0xFF8A8F9C)
internal val KitPage: Color get() = if (AppTheme.dark) Color(0xFF121211) else Color(0xFFF3F2F0)
internal val KitDanger = Color(0xFFD03B3B)
internal val KitGood = Color(0xFF0E8A50)

private val AvatarColors = listOf(
    Color(0xFF2F6FE4), Color(0xFF1BAF7A), Color(0xFFEB6834), Color(0xFF8B5CF6),
    Color(0xFFEC4899), Color(0xFF0E8A7E), Color(0xFFC2410C), Color(0xFF475569),
)

/** Every page of a paginated list route (up to [maxPages]); the screens filter client-side. */
internal suspend fun ApiClient.listAll(resource: String, maxPages: Int = 8): List<JSONObject> = withContext(Dispatchers.IO) {
    val out = mutableListOf<JSONObject>()
    var page = 1
    while (page <= maxPages) {
        val p = list(resource, page)
        out += p.items
        if (page >= p.lastPage) break
        page++
    }
    out
}

/** Brand-gradient header with a back button (and [leading], e.g. the drawer ☰, beside it on the left) and title; [content] sits under the title row. */
@Composable
internal fun HeroBar(b: Branding, title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, compact: Boolean = false, showBell: Boolean = true, leading: (@Composable () -> Unit)? = null, content: @Composable () -> Unit = {}) {
    val drift by rememberDrift()
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(b.deep, b.base), start = Offset.Zero, end = Offset(900f, 1100f)))
            // decorative circle, drawn (not a child) so it never makes the header taller
            .drawBehind { drawCircle(Color.White.copy(alpha = .08f), radius = 100.dp.toPx(), center = Offset(size.width - 30.dp.toPx() - drift * 16.dp.toPx(), 30.dp.toPx() + drift * 10.dp.toPx())) },
    ) {
        Skyline(Modifier.align(Alignment.BottomEnd).fillMaxWidth(.6f).height(56.dp))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = if (compact) 16.dp else 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).glass(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Text("←", color = Color.White, fontSize = 20.sp) }
                if (leading != null) { Spacer(Modifier.size(8.dp)); leading() }
                Text(
                    title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                actions()
                if (showBell && !compact && LocalOpenNotifications.current != null) { Spacer(Modifier.size(8.dp)); ChatBell(); Spacer(Modifier.size(8.dp)); NotificationBell() }
            }
            if (!compact) {
                Spacer(Modifier.height(18.dp))
                content()
            }
        }
    }
}

@Composable
internal fun HeroNumber(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
    }
}

@Composable
internal fun HeroChips(vararg chips: String) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach {
            Text(
                it, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.glass(RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * A flat tab strip: bold dark label with a thick underline for the open tab, muted labels for the rest, a hairline underneath.
 * A few tabs share the width evenly; many scroll sideways.
 */
@Composable
internal fun SegTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val ink = if (AppTheme.dark) Color(0xFFF2F0EB) else Color(0xFF0F172A)         // the open tab: near-black on the light page, near-white on the dark one
    @Composable
    fun Tab(i: Int, t: String, modifier: Modifier) {
        val on = i == selected
        Column(
            modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onSelect(i) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                t, fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1,
                color = if (on) ink else KitMuted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
            Box(Modifier.height(3.dp).width(if (on) 34.dp else 0.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(ink))
        }
    }
    Column(Modifier.padding(top = 6.dp, bottom = 12.dp).fillMaxWidth()) {
        if (tabs.size <= 3) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { tabs.forEachIndexed { i, t -> Tab(i, t, Modifier.weight(1f)) } }
        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) { tabs.forEachIndexed { i, t -> Tab(i, t, Modifier) } }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KitLine))
    }
}

@Composable
internal fun Initials(name: String, size: Dp = 46.dp, dot: Color? = null) {
    val words = name.trim().split(Regex("\\s+"))
    val text = (words.firstOrNull()?.take(1).orEmpty() + words.getOrNull(1)?.take(1).orEmpty()).uppercase()
    Box {
        Box(
            Modifier.size(size).clip(CircleShape).background(AvatarColors[Math.floorMod(name.hashCode(), AvatarColors.size)]),
            contentAlignment = Alignment.Center,
        ) { Text(text.ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * .34f).sp) }
        if (dot != null) Box(Modifier.size(12.dp).align(Alignment.BottomEnd).clip(CircleShape).background(KitCard).padding(2.dp).clip(CircleShape).background(dot))
    }
}

@Composable
internal fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
internal fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String) {
    androidx.compose.material3.TextField(
        value = value, onValueChange = onChange, singleLine = true, placeholder = { Text(placeholder, color = KitMuted) },
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = KitCard, unfocusedContainerColor = KitCard,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = KitInk, unfocusedTextColor = KitInk, cursorColor = KitInk,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).softShadow(3.dp, RoundedCornerShape(16.dp)),
    )
}

@Composable
internal fun HeroButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).glass(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color.White, fontSize = 20.sp) }
}

/** Like [listAll] but also returns the first page's raw JSON, for routes that add extra fields (e.g. `can`). */
internal suspend fun ApiClient.listAllMeta(resource: String, maxPages: Int = 8): Pair<List<JSONObject>, JSONObject> = withContext(Dispatchers.IO) {
    val out = mutableListOf<JSONObject>()
    var meta = JSONObject()
    var page = 1
    while (page <= maxPages) {
        val p = list(resource, page)
        if (page == 1) meta = p.raw
        out += p.items
        if (page >= p.lastPage) break
        page++
    }
    out to meta
}

/** The delete button of a details header: a round see-through glass button like the other header buttons, with a crisp drawn trash bin (lid, handle, body and two ribs). */
@Composable
internal fun DeleteButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).glass(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
            val w = size.width; val h = size.height
            val stroke = 1.9.dp.toPx()
            val ink = Color(0xFFFFD6D6)        // white with a hint of red: transparent glass like the other header buttons, still readable as delete
            val round = androidx.compose.ui.graphics.StrokeCap.Round
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(ink, androidx.compose.ui.geometry.Offset(w * x1, h * y1), androidx.compose.ui.geometry.Offset(w * x2, h * y2), stroke, round)
            line(.12f, .26f, .88f, .26f)                                    // lid
            line(.38f, .26f, .38f, .12f); line(.62f, .26f, .62f, .12f); line(.38f, .12f, .62f, .12f)   // handle
            val body = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * .22f, h * .36f); lineTo(w * .27f, h * .85f)
                quadraticBezierTo(w * .28f, h * .92f, w * .35f, h * .92f); lineTo(w * .65f, h * .92f)
                quadraticBezierTo(w * .72f, h * .92f, w * .73f, h * .85f); lineTo(w * .78f, h * .36f)
            }
            drawPath(body, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            line(.42f, .5f, .42f, .78f); line(.58f, .5f, .58f, .78f)        // ribs
        }
    }
}
