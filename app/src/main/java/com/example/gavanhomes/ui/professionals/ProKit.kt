package com.example.gavanhomes.ui.professionals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal val ProAmber = Color(0xFFF59E0B)
internal val ProBlue = Color(0xFF2F6FE4)
internal val ProBanners = listOf(
    Color(0xFF5B9BFF) to Color(0xFF2F6FE4), Color(0xFF1BAF7A) to Color(0xFF0E8A7E), Color(0xFFF59E0B) to Color(0xFFC2410C),
    Color(0xFF8B5CF6) to Color(0xFF4A3AA7), Color(0xFFEC4899) to Color(0xFF9D174D), Color(0xFF475569) to Color(0xFF1E293B),
)

internal fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
internal fun JSONObject.objects(key: String): List<JSONObject> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()

internal fun String.headline(): String = replace('_', ' ').replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

private val Pretty = DateTimeFormatter.ofPattern("d MMM yyyy")
internal fun proDate(s: String?): LocalDate? = runCatching { LocalDate.parse(s!!.take(10)) }.getOrNull()
internal fun prettyDate(s: String?): String? = proDate(s)?.format(Pretty)
internal fun daysFromToday(s: String?): Long? = proDate(s)?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

/** Status colours: active / on hold / inactive / blacklisted, and the task-assignment statuses. */
internal fun statusColor(s: String?): Color = when (s) {
    "active", "approved" -> KitGood
    "onhold", "submitted", "pending", "rework" -> ProAmber
    "blacklisted" -> KitDanger
    "assigned", "in_progress" -> ProBlue
    else -> Color(0xFF9AA5B8)
}

internal fun statusLabel(s: String?): String = when (s) { "onhold" -> "On hold"; null -> "—"; else -> s.headline() }

/** A licence's state for the chips: red when expired, amber when it lapses within 60 days. */
internal fun licenceTone(expiry: String?): Pair<String, Color>? {
    val d = daysFromToday(expiry) ?: return null
    return when {
        d < 0 -> "Licence expired" to KitDanger
        d <= 60 -> "Licence in ${d}d" to ProAmber
        else -> null
    }
}

@Composable
internal fun ProCounter(value: String, label: String, modifier: Modifier, big: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = if (big) .26f else .18f)).padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontSize = if (big) 28.sp else 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = .85f), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
internal fun ProFilterChip(label: String, count: Int?, dot: Color?, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF0F172A) else Color.White)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.size(7.dp)) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1)
        if (count != null) Text(" $count", fontSize = 13.sp, color = if (on) Color.White.copy(alpha = .7f) else KitMuted)
    }
}

@Composable
internal fun ProRoundAction(glyph: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = .15f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ProSheetAction(glyph: String, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(if (danger) KitDanger.copy(alpha = .12f) else Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) {
            Text(glyph, fontSize = 17.sp, color = if (danger) KitDanger else KitInk)
        }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (danger) KitDanger else KitInk, modifier = Modifier.padding(start = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ProCard(title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk)
            if (sub != null) Text(sub, color = KitMuted, fontSize = 12.sp)
        }
        content()
    }
}

@Composable
internal fun ProTile(value: String, label: String, modifier: Modifier, tint: Color = KitInk) {
    Column(
        modifier.shadow(5.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = tint)
        Text(label, fontSize = 11.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A label / value line; left out when there is no value, tappable when [onClick] is given. */
@Composable
internal fun ProFact(label: String, value: String?, onClick: (() -> Unit)? = null) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(.8f))
        Text(value, color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}

@Composable
internal fun ProDoc(label: String, path: String?, view: (String) -> Unit) {
    if (path.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            "View", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KitInk,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1EFEB)).clickable { view(path) }.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun ProBar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEDEBE7))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxSize().background(color))
    }
}

/** The hero + a spinner or an error with Retry, for a screen whose data hasn't arrived. */
@Composable
internal fun ProLoading(b: Branding, title: String, error: String?, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HeroBar(b, title, onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(error, color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
