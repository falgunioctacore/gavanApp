package com.example.gavanhomes.ui.projects

import com.example.gavanhomes.ui.common.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Banners = listOf(
    Color(0xFF5B9BFF) to Color(0xFF2F6FE4), Color(0xFF1BAF7A) to Color(0xFF0E8A7E), Color(0xFFF59E0B) to Color(0xFFC2410C),
    Color(0xFF8B5CF6) to Color(0xFF4A3AA7), Color(0xFFEC4899) to Color(0xFF9D174D), Color(0xFF475569) to Color(0xFF1E293B),
)

private fun statusKind(name: String?): String = when {
    name == null -> "info"
    name.contains("progress", true) -> "info"
    name.contains("pending", true) -> "warn"
    name.contains("complete", true) -> "done"
    name.contains("overdue", true) || name.contains("cancel", true) -> "crit"
    else -> "info"
}

private fun date(s: String?): LocalDate? = runCatching { LocalDate.parse(s!!.take(10)) }.getOrNull()

/**
 * Native copy of the website's Projects page (admin_mobile/projects): hero with counts, status tabs, search,
 * project cards with a timeline bar. Data: GET /api/mobile/admin/projects. Tapping a card opens its website page.
 */
@Composable
fun ProjectsScreen(api: ApiClient, b: Branding, onBack: () -> Unit, onOpenWeb: (String) -> Unit) {
    var projects by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(reload) {
        error = null
        try { projects = api.listAll("projects") } catch (e: Exception) { error = e.message ?: "Could not load projects" }
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        val all = projects
        if (all == null) {
            HeroBar(b, "Projects", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = b.base)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { reload++ }) { Text("Retry") }
                }
            }
            return@Column
        }

        val statuses = all.mapNotNull { it.str("status") }.distinct().sorted()
        val tabs = listOf("All ${all.size}") + statuses.map { s -> "$s ${all.count { it.str("status") == s }}" } +
            listOf("Inactive ${all.count { !it.optBoolean("active") }}")
        if (tab >= tabs.size) tab = 0
        val shown = all.filter { p ->
            val okTab = when {
                tab == 0 -> true
                tab == tabs.lastIndex -> !p.optBoolean("active")
                else -> p.str("status") == statuses[tab - 1]
            }
            val q = query.trim().lowercase()
            okTab && (q.isEmpty() || listOfNotNull(p.str("name"), p.str("code"), p.str("status")).any { it.lowercase().contains(q) } ||
                (p.optJSONArray("type")?.let { a -> (0 until a.length()).any { a.getString(it).lowercase().contains(q) } } ?: false))
        }

        LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                HeroBar(b, "Projects", onBack) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { HeroNumber(all.size.toString(), "projects · ${all.count { it.optBoolean("active") }} active") }
                    }
                    Spacer(Modifier.height(14.dp))
                    HeroChips(
                        "${all.count { statusKind(it.str("status")) == "info" }} in progress",
                        "${all.count { statusKind(it.str("status")) == "warn" }} pending",
                        "${all.count { statusKind(it.str("status")) == "done" }} done",
                    )
                }
            }
            item { SegTabs(tabs, tab) { tab = it } }
            item { SearchBox(query, { query = it }, "Search name, code, type…") }
            item {
                Text(
                    "${shown.size} projects", color = KitMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp),
                )
            }
            if (shown.isEmpty()) item {
                Text("No projects match", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
            }
            items(shown) { p -> ProjectCard(p, b) { onOpenWeb("/admin/project/${p.optString("uuid")}") } }
        }
    }
}

@Composable
private fun ProjectCard(p: JSONObject, b: Branding, onClick: () -> Unit) {
    val (c1, c2) = Banners[(p.optInt("id") % Banners.size + Banners.size) % Banners.size]
    val name = p.str("name") ?: "Project"
    val status = p.str("status")
    val kind = statusKind(status)
    val types = p.optJSONArray("type")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    val start = date(p.str("start_date")); val end = date(p.str("end_date"))
    val today = LocalDate.now()
    val left = end?.let { ChronoUnit.DAYS.between(today, it).toInt() }
    val pct = if (start != null && end != null && end.isAfter(start))
        (ChronoUnit.DAYS.between(start, today).toFloat() / ChronoUnit.DAYS.between(start, end)).coerceIn(0f, 1f) else null

    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp)).background(Color.White).clickable { onClick() }.padding(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(c1, c2), start = Offset.Zero, end = Offset(800f, 600f))),
        ) {
            Text(
                name.take(1).uppercase(), color = Color.White.copy(alpha = .16f), fontSize = 140.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-24).dp),
            )
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (status != null) Pill(
                    status, Color.White.copy(alpha = .94f),
                    when (kind) { "warn" -> Color(0xFFB7791F); "done" -> KitGood; "crit" -> KitDanger; else -> Color(0xFF1A5FA8) },
                )
                Pill(if (p.optBoolean("active")) "Active" else "Inactive", Color.White.copy(alpha = .94f), if (p.optBoolean("active")) KitGood else Color(0xFF5F6B76))
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.str("code") ?: "No code", fontSize = 11.sp, color = KitMuted)
                }
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) { Text("↗", color = Color.White, fontSize = 18.sp) }
            }
        }

        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
            if (types.isNotEmpty()) {
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.take(3).forEach { Pill(it, b.base.copy(alpha = .16f), b.dark) }
                    if (types.size > 3) Pill("+${types.size - 3}", b.base.copy(alpha = .16f), b.dark)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${start ?: "—"} → ${end ?: "—"}", fontSize = 12.sp, color = KitMuted)
                if (left != null) Text(
                    if (left < 0) "${-left}d overdue" else if (left == 0) "Ends today" else "${left}d left",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (left < 0) KitDanger else b.dark,
                )
            }
            if (pct != null) {
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(7.dp).clip(CircleShape).background(b.base.copy(alpha = .15f))) {
                    Box(Modifier.fillMaxWidth(pct).height(7.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(b.base, b.dark))))
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${p.optInt("tasks_count")} tasks", fontSize = 12.sp, color = KitInk)
                Text("${p.optInt("members_count")} members", fontSize = 12.sp, color = KitInk)
            }
        }
    }
}
