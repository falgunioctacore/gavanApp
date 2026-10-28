package com.example.gavanhomes.ui.common

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

internal val KitInk = Color(0xFF1F2A44)
internal val KitMuted = Color(0xFF8A8F9C)
internal val KitPage = Color(0xFFF3F2F0)
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

/** Brand-gradient header with a back button and title; [content] sits under the title row. */
@Composable
internal fun HeroBar(b: Branding, title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, compact: Boolean = false, content: @Composable () -> Unit = {}) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(b.deep, b.base), start = Offset.Zero, end = Offset(900f, 1100f)))
            // decorative circle, drawn (not a child) so it never makes the header taller
            .drawBehind { drawCircle(Color.White.copy(alpha = .08f), radius = 100.dp.toPx(), center = Offset(size.width - 30.dp.toPx(), 30.dp.toPx())) },
    ) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = if (compact) 16.dp else 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Text("←", color = Color.White, fontSize = 20.sp) }
                Text(
                    title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                actions()
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
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .2f)).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/** The white pill of tabs; the selected one is dark. Scrolls sideways when there are many. */
@Composable
internal fun SegTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(Color.White).horizontalScroll(rememberScrollState()).padding(5.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            Text(
                t, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                color = if (on) Color.White else KitMuted,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Color(0xFF0F172A) else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
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
        if (dot != null) Box(Modifier.size(12.dp).align(Alignment.BottomEnd).clip(CircleShape).background(Color.White).padding(2.dp).clip(CircleShape).background(dot))
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
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = KitInk, unfocusedTextColor = KitInk, cursorColor = KitInk,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).shadow(3.dp, RoundedCornerShape(16.dp)),
    )
}

@Composable
internal fun HeroButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)).clickable { onClick() },
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
