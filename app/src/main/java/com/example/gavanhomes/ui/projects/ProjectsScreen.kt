package com.example.gavanhomes.ui.projects

import com.example.gavanhomes.ui.common.HeroSummary
import com.example.gavanhomes.ui.common.ScrollTopButton
import com.example.gavanhomes.ui.common.FabAction
import com.example.gavanhomes.ui.common.FabLift
import com.example.gavanhomes.ui.common.QuickFab
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.CardAction
import com.example.gavanhomes.ui.common.CardStat
import com.example.gavanhomes.ui.common.EntityCard
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.common.FilterBar
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.SearchBox
import com.example.gavanhomes.ui.common.listAllMeta
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Banners = listOf(
    Color(0xFF5B9BFF) to Color(0xFF2F6FE4), Color(0xFF1BAF7A) to Color(0xFF0E8A7E), Color(0xFFF59E0B) to Color(0xFFC2410C),
    Color(0xFF8B5CF6) to Color(0xFF4A3AA7), Color(0xFFEC4899) to Color(0xFF9D174D), Color(0xFF475569) to Color(0xFF1E293B),
)
private val Amber = Color(0xFFC47A1A)
private val Blue = Color(0xFF2F6FE4)

private fun statusKind(name: String?): String = when {
    name == null -> "info"
    name.contains("progress", true) -> "info"
    name.contains("pending", true) -> "warn"
    name.contains("complete", true) -> "done"
    name.contains("overdue", true) || name.contains("cancel", true) -> "crit"
    else -> "info"
}

private fun statusColor(name: String?): Color = when (statusKind(name)) { "warn" -> Amber; "done" -> KitGood; "crit" -> KitDanger; else -> Blue }
private fun date(s: String?): LocalDate? = runCatching { LocalDate.parse(s!!.take(10)) }.getOrNull()
private val Pretty = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
private fun LocalDate?.pretty(): String = this?.format(Pretty) ?: "—"

/** One project with its derived timeline numbers. */
private class P(val json: JSONObject) {
    val id = json.optInt("id")
    val name = json.str("name") ?: "Project"
    val code = json.str("code")
    val status = json.str("status")
    val active = json.optBoolean("active")
    val types = json.optJSONArray("type")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    val start = date(json.str("start_date"))
    val end = date(json.str("end_date"))
    val left: Int? = end?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }
    val progress: Float? = if (start != null && end != null && end.isAfter(start))
        (ChronoUnit.DAYS.between(start, LocalDate.now()).toFloat() / ChronoUnit.DAYS.between(start, end)).coerceIn(0f, 1f) else null
    val tasks = json.optInt("tasks_count")
    val members = json.optInt("members_count")
    val phone = json.str("phone")
    val email = json.str("email")
    val detailsPath = json.str("details_path")
    val editPath = json.str("edit_path")
    val uuid = json.str("uuid")
    val banner = Banners[(id % Banners.size + Banners.size) % Banners.size]
    fun matches(q: String) = q.isEmpty() || listOfNotNull(name, code, status).any { it.lowercase().contains(q) } || types.any { it.lowercase().contains(q) }
}

private val SortNames = listOf("Newest", "Name A–Z", "Ending soon")

/**
 * Projects — GET /api/mobile/admin/projects. Hero with live counts, search, status chips, sort and a cards / compact
 * layout switch. A project opens its native details page; ⋯ opens actions (open, edit, call, email).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(api: ApiClient, b: Branding, onBack: () -> Unit, onOpenDetails: (String) -> Unit, onOpenForm: (String?) -> Unit) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf<List<P>?>(null) }
    var canCreate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }   // chosen filters: group → values
    var sort by remember { mutableIntStateOf(0) }
    var compact by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<P?>(null) }
    var refreshing by remember { mutableStateOf(false) }                      // pull-down refresh in progress
    LaunchedEffect(projects) { refreshing = false }                         // fresh data arrived
    LaunchedEffect(refreshing) { if (refreshing) { kotlinx.coroutines.delay(10_000); refreshing = false } }   // never spin forever

    LaunchedEffect(reload) {
        error = null
        try {
            val (items, meta) = api.listAllMeta("projects")
            projects = items.map(::P)
            canCreate = meta.optJSONObject("can")?.optBoolean("create") == true
        } catch (e: Exception) { error = e.message ?: "Could not load projects" }
    }

    val all = projects
    Column(Modifier.fillMaxSize().background(KitPage)) {
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

        val statuses = all.mapNotNull { it.status }.distinct().sorted()
        val passes = { p: P, m: Map<String, Set<String>> -> m["status"].orEmpty().let { s -> s.isEmpty() || s.any { v -> (v == "~inactive" && !p.active) || p.status == v } } }
        val shown = all
            .filter { p -> passes(p, sel) && p.matches(query.trim().lowercase()) }
            .let { list ->
                when (sort) {
                    1 -> list.sortedBy { it.name.lowercase() }
                    2 -> list.sortedBy { it.end?.toEpochDay() ?: Long.MAX_VALUE }
                    else -> list.sortedByDescending { it.id }
                }
            }
        val inProgress = all.count { statusKind(it.status) == "info" }
        val pending = all.count { statusKind(it.status) == "warn" }
        val done = all.count { statusKind(it.status) == "done" }

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.fillMaxSize()) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
            // ── header: title, add / refresh, and live counters ──
            item {
                HeroBar(
                    b, "Projects", onBack,
                ) {
                    HeroSummary(listOf(all.size.toString() to (if (all.size == 1) "project" else "projects"), inProgress.toString() to "In progress", pending.toString() to "Pending", done.toString() to "Done"), bar = true)
                }
            }

            item { Spacer(Modifier.height(14.dp)) }
            item {
                FilterBar(
                    query, { query = it }, "Search projects…",
                    groups = listOf(FilterGroup("status", "Status", statuses.map { st -> FilterOption(st, st, all.count { p -> p.status == st }, statusColor(st)) } + FilterOption("~inactive", "Inactive", all.count { p -> !p.active }, KitMuted))),
                    selected = sel, onApply = { sel = it },
                    countWith = { m -> all.count { p -> passes(p, m) && p.matches(query.trim().lowercase()) } },
                    sortOptions = SortNames, sortIndex = sort, onSort = { sort = it },
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${shown.size} project${if (shown.size == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    com.example.gavanhomes.ui.common.ViewToggle(compact) { compact = it }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }

            if (shown.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No projects match", fontWeight = FontWeight.Bold, color = KitInk)
                    Text("Try another search or clear the filter.", color = KitMuted, fontSize = 13.sp)
                }
            }
            items(shown, key = { it.id }) { p ->
                if (compact) CompactRow(p, onClick = { p.uuid?.let(onOpenDetails) }, onMore = { sheet = p })
                else ProjectCard(p, b, onClick = { p.uuid?.let(onOpenDetails) }, onMore = { sheet = p },
                    onCall = { p.phone?.let { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) } },
                    onMail = { p.email?.let { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$it"))) } })
            }
        }
        ScrollTopButton(listState, lift = if (canCreate) FabLift else 0.dp)
        QuickFab(b, if (canCreate) listOf(FabAction("project", "New project")) else emptyList()) { onOpenForm(null) }
        }
    }

    // ── ⋯ actions ──
    sheet?.let { p ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(p.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text(p.code ?: "No code", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                SheetAction("◉", "Open details") { sheet = null; p.uuid?.let(onOpenDetails) }
                if (p.editPath != null && p.uuid != null) SheetAction("✎", "Edit project") { sheet = null; onOpenForm(p.uuid) }
                p.phone?.let { n -> SheetAction("✆", "Call $n") { sheet = null; context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) } }
                p.email?.let { m -> SheetAction("✉", m) { sheet = null; context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$m"))) } }
            }
        }
    }
}

// ─────────────────────────────── pieces ───────────────────────────────

@Composable
private fun Counter(value: String, label: String, modifier: Modifier, big: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = if (big) .26f else .18f)).padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontSize = if (big) 28.sp else 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = .85f), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun FilterChip(label: String, count: Int, dot: Color?, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF0F172A) else Color.White)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.size(7.dp)) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1)
        Text(" $count", fontSize = 13.sp, color = if (on) Color.White.copy(alpha = .7f) else KitMuted)
    }
}

/** Ring showing how far through its timeline a project is, with the days left in the middle. */
@Composable
private fun TimelineRing(p: P, tint: Color) {
    val left = p.left
    val late = left != null && left < 0
    val color = if (late) KitDanger else tint
    Box(
        Modifier.size(52.dp).drawBehind {
            val stroke = 5.dp.toPx(); val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(color.copy(alpha = .16f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * (p.progress ?: 0f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (left == null) "—" else kotlin.math.abs(left).toString(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = color)
            Text(if (left == null) "" else if (late) "late" else "days", fontSize = 8.sp, color = KitMuted)
        }
    }
}

/**
 * A project card, in three bands: who it is (avatar, name, code, status), where it stands (property types, the timeline as a bar with the days left),
 * and the numbers with the quick actions (tasks, members, call, mail, more).
 */
@Composable
private fun ProjectCard(p: P, b: Branding, onClick: () -> Unit, onMore: () -> Unit, onCall: () -> Unit, onMail: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val left = p.left
    val late = left != null && left < 0
    val tone = if (late) KitDanger else b.base
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(4.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
            .clip(shape).background(Color.White).clickable { onClick() },
    ) {
        // ── who ──
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(p.banner.first, p.banner.second))), contentAlignment = Alignment.Center) {
                Text(p.name.take(1).uppercase().ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(p.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = KitInk, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                Text(p.code ?: "No code", fontSize = 12.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            if (p.status != null) Pill(p.status, statusColor(p.status).copy(alpha = .14f), statusColor(p.status))
        }

        // ── where it stands ──
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (p.types.isNotEmpty()) Text(p.types.joinToString("  ·  "), fontSize = 12.5.sp, color = b.dark, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.start != null || p.end != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.start.pretty()}  →  ${p.end.pretty()}", fontSize = 12.sp, color = KitMuted, modifier = Modifier.weight(1f))
                    if (left != null) Text(if (late) "${-left} days late" else "$left days left", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = tone)
                }
                if (p.progress != null) Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(tone.copy(alpha = .16f))) {
                    Box(Modifier.fillMaxWidth(p.progress.coerceIn(0f, 1f)).fillMaxHeight().background(tone))
                }
            }
        }

        // ── numbers and actions ──
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CardStat("${p.tasks}", if (p.tasks == 1) "task" else "tasks")
                CardStat("${p.members}", if (p.members == 1) "member" else "members")
                if (!p.active) Text("Inactive", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5F6B76), modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFEDEBE7)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p.phone != null) CardAction("✆", b.base, onCall)
                if (p.email != null) CardAction("✉", b.base, onMail)
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF3F2F0)).clickable { onMore() }, contentAlignment = Alignment.Center) { Text("⋯", color = KitInk, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CompactRow(p: P, onClick: () -> Unit, onMore: () -> Unit) {
    val (c1, c2) = p.banner
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(Color.White).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(c1, c2))), contentAlignment = Alignment.Center) {
            Text(p.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(p.code, p.left?.let { if (it < 0) "${-it}d late" else "${it}d left" }, "${p.tasks} tasks").joinToString(" · "),
                fontSize = 12.sp, color = if ((p.left ?: 0) < 0) KitDanger else KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (p.status != null) Pill(p.status, statusColor(p.status).copy(alpha = .14f), statusColor(p.status))
        Text("⋯", fontSize = 22.sp, color = KitMuted, modifier = Modifier.clip(CircleShape).clickable { onMore() }.padding(horizontal = 10.dp))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk)
        Text(" $label", fontSize = 12.sp, color = KitMuted)
    }
}

@Composable
private fun RoundAction(glyph: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = .15f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SheetAction(glyph: String, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) { Text(glyph, fontSize = 17.sp, color = KitInk) }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = KitInk, modifier = Modifier.padding(start = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
