package com.example.gavanhomes.ui.targets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import com.example.gavanhomes.ui.tasks.statusTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Loads GET /projects/{uuid}/target-center/timeline once and hands the rows to [content], with a status filter on top. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineLoader(api: ApiClient, b: Branding, uuid: String, version: Int, content: @Composable (List<JSONObject>) -> Unit) {
    var rows by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableIntStateOf(0) }
    LaunchedEffect(uuid, version, attempt) {
        error = null
        try { rows = withContext(Dispatchers.IO) { api.get("/projects/$uuid/target-center/timeline").objs("data") } }
        catch (e: Exception) { error = e.message ?: "Could not load the timeline" }
    }
    val all = rows
    if (all == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
        }
        return
    }
    val statuses = all.map { it.optInt("status_id") to it.optString("status") }.distinct()
    if (statuses.size > 1) FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pick("All", status == 0, KitInk) { status = 0 }
        statuses.forEach { (sid, name) -> Pick(name, status == sid, statusTint(name)) { status = sid } }
    }
    content(if (status == 0) all else all.filter { it.optInt("status_id") == status })
}

private fun day(s: String?): LocalDate? = runCatching { LocalDate.parse(s!!.take(10)) }.getOrNull()

// ───────────────────────────── calendar ─────────────────────────────

/** A month grid (Monday first) with the targets that fall due each day; tap a day to list them. */
@Composable
fun TargetCalendar(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    TimelineLoader(api, b, uuid, version) { rows ->
        var month by remember { mutableStateOf(YearMonth.now()) }
        var picked by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
        val byDay = rows.filter { day(it.str("due_date")) != null }.groupBy { day(it.str("due_date"))!! }
        val today = LocalDate.now()

        Card(month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 26.sp, color = KitInk, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable { month = month.minusMonths(1) }.padding(horizontal = 14.dp, vertical = 2.dp))
                Text("Today", color = b.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { month = YearMonth.now(); picked = today }.padding(8.dp))
                Text("›", fontSize = 26.sp, color = KitInk, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable { month = month.plusMonths(1) }.padding(horizontal = 14.dp, vertical = 2.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
                    Text(d, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (i >= 5) KitDanger.copy(alpha = .7f) else KitMuted)
                }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth()
            for (w in 0 until (cells + 6) / 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val n = w * 7 + c - offset + 1
                        if (n < 1 || n > month.lengthOfMonth()) { Spacer(Modifier.weight(1f).aspectRatio(1f)); continue }
                        val date = month.atDay(n)
                        val list = byDay[date].orEmpty()
                        val on = picked == date
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (on) b.base else if (date == today) b.base.copy(alpha = .12f) else Color.Transparent).clickable { picked = date },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(n.toString(), fontSize = 14.sp, fontWeight = if (date == today || list.isNotEmpty()) FontWeight.ExtraBold else FontWeight.Medium, color = if (on) Color.White else KitInk)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(7.dp).padding(top = 2.dp)) {
                                    list.take(3).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) Color.White else statusTint(it.optString("status")))) }
                                }
                            }
                        }
                    }
                }
            }
        }

        val sel = picked
        val list = sel?.let { byDay[it] }.orEmpty()
        Card(sel?.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)) ?: "Pick a day", if (list.isEmpty()) "Nothing due" else "${list.size} due") {
            if (list.isEmpty()) Text("No targets are due on this day.", color = KitMuted, fontSize = 14.sp)
            list.forEach { t ->
                val tint = statusTint(t.optString("status"))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .1f)).clickable { onOpen(t.optInt("id")) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(t.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(t.str("target_no"), t.str("department")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                    }
                    Text(t.optString("status"), color = tint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        val undated = rows.count { day(it.str("due_date")) == null }
        if (undated > 0) Text("$undated target${if (undated == 1) " has" else "s have"} no due date and ${if (undated == 1) "is" else "are"} not shown.", color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
}

// ───────────────────────────── gantt ─────────────────────────────

private val PerDay = 26.dp

/** A bar per target across a shared, horizontally scrolling date ruler: green when completed, red when overdue (with the days late), amber otherwise. */
@Composable
fun TargetGantt(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    TimelineLoader(api, b, uuid, version) { rows ->
        if (rows.isEmpty()) { Text("No targets to display", color = KitMuted, modifier = Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center); return@TimelineLoader }
        val today = LocalDate.now()
        fun startOf(t: JSONObject): LocalDate = day(t.str("start_date")) ?: day(t.str("created_date")) ?: day(t.str("due_date"))?.minusDays(5) ?: today
        fun endOf(t: JSONObject, s: LocalDate): LocalDate = day(t.str("due_date"))?.takeIf { it.isAfter(s) } ?: s.plusDays(3)
        var from = today; var to = today.plusDays(14)
        rows.forEach { t -> val s = startOf(t); val e = endOf(t, s); if (s.isBefore(from)) from = s; if (e.isAfter(to)) to = e }
        from = from.minusDays(2); to = to.plusDays(2)
        val total = ChronoUnit.DAYS.between(from, to).toInt().coerceAtLeast(7)
        val scroll = rememberScrollState()
        val trackWidth = PerDay * total

        Column(Modifier.padding(vertical = 4.dp)) {
            // the ruler: a label every week
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().horizontalScroll(scroll)) {
                Box(Modifier.width(trackWidth).height(28.dp)) {
                    for (d in 0..total step 7) {
                        Text(from.plusDays(d.toLong()).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)), fontSize = 10.sp, color = KitMuted, modifier = Modifier.offset(PerDay * d + 3.dp, 6.dp))
                        Box(Modifier.offset(PerDay * d, 0.dp).width(1.dp).height(28.dp).background(Color(0xFFE6E3DD)))
                    }
                    val t = ChronoUnit.DAYS.between(from, today).toInt()
                    if (t in 0..total) Box(Modifier.offset(PerDay * t, 0.dp).width(2.dp).height(28.dp).background(Color(0xFF60A5FA)))
                }
            }
            rows.forEach { t ->
                val s = startOf(t); val e = endOf(t, s)
                val done = t.optString("status").equals("completed", true)
                val over = t.optBoolean("is_overdue")
                val color = if (done) KitGood else if (over) KitDanger else Color(0xFFE0A324)
                val left = PerDay * ChronoUnit.DAYS.between(from, s).toInt()
                val width = (PerDay * ChronoUnit.DAYS.between(s, e).toInt()).coerceAtLeast(60.dp)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 7.dp).clickable { onOpen(t.optInt("id")) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (over) Text("DELAYED ${t.optInt("delayed_days")}D", color = KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Text(listOfNotNull(t.str("target_no"), t.str("department")?.let { "Dept: $it" }, prettyDay(t.str("due_date"))?.let { "Due $it" }).joinToString(" · "), color = KitMuted, fontSize = 11.sp)
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().horizontalScroll(scroll)) {
                        Box(Modifier.width(trackWidth).height(26.dp)) {
                            Box(Modifier.offset(left, 0.dp).width(width).height(26.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.CenterEnd) {
                                Text("${t.optInt("progress")}%", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
                            }
                        }
                    }
                }
            }
            Text("Swipe sideways to move along the dates · blue line is today", color = KitMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
    }
}
