package com.example.gavanhomes.ui.audit

import com.example.gavanhomes.ui.common.HeroSummary
import com.example.gavanhomes.ui.common.ScrollTopButton
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.common.SearchBox
import com.example.gavanhomes.ui.professionals.ProAmber
import com.example.gavanhomes.ui.professionals.ProBlue
import com.example.gavanhomes.ui.professionals.ProCounter
import com.example.gavanhomes.ui.professionals.ProFilterChip
import com.example.gavanhomes.ui.professionals.headline
import com.example.gavanhomes.ui.professionals.objects
import com.example.gavanhomes.ui.professionals.prettyDate
import com.example.gavanhomes.ui.projects.form.Choice
import com.example.gavanhomes.ui.projects.form.ChoiceSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private class Tab(val slug: String, val label: String, val glyph: String, val blurb: String)

private val Tabs = listOf(
    Tab("logins", "Login activity", "⏻", "Sign-ins, sign-outs and failures"),
    Tab("changes", "Data changes", "✎", "Who changed which record"),
    Tab("visits", "Page visits", "◉", "Pages people opened"),
)
private val Shown = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun eventColor(e: String?) = when (e) { "login", "created" -> KitGood; "failed", "deleted" -> KitDanger; "updated" -> ProAmber; else -> Color(0xFF9AA5B8) }

/** "2026-10-28 14:22:28" → "28 Oct 2026, 2:22 PM". */
private fun whenText(s: String?): String {
    val d = runCatching { java.time.LocalDateTime.parse(s!!.replace(' ', 'T')) }.getOrNull() ?: return s.orEmpty()
    return d.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a"))
}

private class Filters {
    var event by mutableStateOf<String?>(null)
    var guard by mutableStateOf<String?>(null)
    var user by mutableStateOf<String?>(null)
    var type by mutableStateOf<String?>(null)
    var from by mutableStateOf<LocalDate?>(null)
    var to by mutableStateOf<LocalDate?>(null)
    var url by mutableStateOf("")          // the search box: email / IP / record / page address
    var date by mutableStateOf<String?>(null)
    var sort by mutableStateOf(0)          // 0 newest first · 1 oldest first
    val active get() = listOf(event, guard, user, type, from, to).any { it != null } || url.isNotBlank()
    fun clear() { event = null; guard = null; user = null; type = null; from = null; to = null; date = null; url = "" }
    /** The Date choice → from / to, the same choices as every other list. */
    fun pickDate(key: String?) {
        val now = LocalDate.now()
        date = key
        from = when (key) { null -> null; "today" -> now; "week" -> now.minusDays(6); "quarter" -> now.minusMonths(3); "year" -> now.withDayOfYear(1); else -> now.withDayOfMonth(1) }
        to = if (key == null) null else now
    }
    fun query(): String = buildString {
        fun add(k: String, v: Any?) { if (v != null && v.toString().isNotBlank()) append("&").append(k).append("=").append(URLEncoder.encode(v.toString(), "UTF-8")) }
        add("event", event); add("guard", guard); add("user", user); add("type", type); add("from", from); add("to", to); add("search", url.trim())
        if (sort == 1) add("sort", "oldest")
    }
}

/**
 * Audit logs, all native (super admins). The three logs — login activity, data changes, page visits — sit in a ☰ sidebar; each
 * has the website's filters (event, portal, person, record type, dates, URL text) and loads 25 rows at a time.
 * Data: GET /audit/stats, /audit/{log}/options, /audit/{log}?page=&filters.
 */
@Composable
fun AuditScreen(api: ApiClient, b: Branding, section: Int, onSection: (Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val tab = Tabs[section]
    val filters = remember(section) { Filters() }

    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember(section) { mutableStateOf<JSONObject?>(null) }
    val rows = remember(section) { mutableStateListOf<JSONObject>() }
    var page by remember(section) { mutableIntStateOf(0) }
    var lastPage by remember(section) { mutableIntStateOf(1) }
    var total by remember(section) { mutableIntStateOf(0) }
    var loading by remember(section) { mutableStateOf(true) }
    var error by remember(section) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }                      // pull-down refresh in progress
    LaunchedEffect(loading) { if (!loading) refreshing = false }
    LaunchedEffect(refreshing) { if (refreshing) { kotlinx.coroutines.delay(10_000); refreshing = false } }   // never spin forever
    var expanded by remember(section) { mutableStateOf<Int?>(null) }
    val query = filters.query()

    LaunchedEffect(Unit) { runCatching { stats = withContext(Dispatchers.IO) { api.detail("/audit/stats") } } }
    LaunchedEffect(section) { runCatching { options = withContext(Dispatchers.IO) { api.detail("/audit/${tab.slug}/options") } } }

    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/audit/${tab.slug}?page=$p$query") }
            if (p == 1) rows.clear()
            rows.addAll(r.objects("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1); total = r.optInt("total")
        } catch (e: Exception) { error = e.message ?: "Could not load" }
        loading = false
    }
    LaunchedEffect(section, query, reload) { if (filters.url.isNotBlank()) kotlinx.coroutines.delay(350); load(1) }   // a short pause while typing a search

    fun choices(key: String): List<JSONObject> = options?.objects(key).orEmpty()

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = com.example.gavanhomes.ui.common.KitCard, windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(b.deep, b.base), start = Offset.Zero, end = Offset(700f, 700f)))
                            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (b.logo != null) { com.example.gavanhomes.ui.common.BrandMark(b, 52.dp); Spacer(Modifier.size(14.dp)) }
                        Column {
                            Text("Audit logs", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Everything that happens, on record", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Tabs.forEachIndexed { i, t ->
                        val on = i == section
                        val count = stats?.optJSONObject(t.slug)?.optInt("total")
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) b.base.copy(alpha = .16f) else Color.Transparent)
                                .clickable { onSection(i); scope.launch { drawer.close() } }.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) b.base else com.example.gavanhomes.ui.common.KitPage), contentAlignment = Alignment.Center) {
                                Text(t.glyph, color = if (on) Color.White else KitInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(t.label, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = KitInk, fontSize = 15.sp)
                                Text(t.blurb, color = KitMuted, fontSize = 12.sp)
                            }
                            count?.let { Text(it.toString(), color = if (on) b.dark else KitMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Color.White else com.example.gavanhomes.ui.common.KitPage).padding(horizontal = 9.dp, vertical = 3.dp)) }
                        }
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(KitPage)) {
            val s = stats?.optJSONObject(tab.slug)
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.fillMaxSize()) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    HeroBar(
                        b, tab.label, onBack,
                        leading = { HeroButton("☰") { scope.launch { drawer.open() } } },
                    ) {
                        val c = when (tab.slug) {
                            "logins" -> listOf(s?.optInt("total") to "Total", s?.optInt("today") to "Today", s?.optInt("activeUsersToday") to "People", s?.optInt("failedToday") to "Failed")
                            "changes" -> listOf(s?.optInt("total") to "Total", s?.optInt("today") to "Today", s?.optInt("createdToday") to "Created", s?.optInt("deletedToday") to "Deleted")
                            else -> listOf(s?.optInt("total") to "Total", s?.optInt("today") to "Today", s?.optInt("activeUsersToday") to "People", s?.optInt("uniqueUrlsToday") to "Pages")
                        }
                        HeroSummary(c.map { (v, l) -> (v?.toString() ?: "–") to l })
                    }
                }

                // ── filters: the shared filter bar (search · Sort · Filter page), like every other list ──
                item {
                    val sel = buildMap {
                        filters.event?.let { put("event", setOf(it)) }; filters.guard?.let { put("guard", setOf(it)) }
                        filters.user?.let { put("user", setOf(it)) }; filters.type?.let { put("type", setOf(it)) }; filters.date?.let { put("date", setOf(it)) }
                    }
                    val opt = { k: String -> choices(k).map { com.example.gavanhomes.ui.common.FilterOption(it.optString("value"), it.optString("label")) } }
                    val groups = listOfNotNull(
                        choices("events").takeIf { it.isNotEmpty() }?.let { ev -> com.example.gavanhomes.ui.common.FilterGroup("event", "Event", ev.map { com.example.gavanhomes.ui.common.FilterOption(it.optString("value"), it.optString("label"), color = eventColor(it.optString("value"))) }) },
                        opt("guards").takeIf { it.isNotEmpty() }?.let { com.example.gavanhomes.ui.common.FilterGroup("guard", "Portal", it) },
                        opt("people").takeIf { it.isNotEmpty() }?.let { com.example.gavanhomes.ui.common.FilterGroup("user", "Person", it) },
                        if (tab.slug == "changes") opt("types").takeIf { it.isNotEmpty() }?.let { com.example.gavanhomes.ui.common.FilterGroup("type", "Record type", it) } else null,
                        com.example.gavanhomes.ui.inventory.dateGroup(),
                    )
                    Column(Modifier.padding(top = 10.dp)) {
                        com.example.gavanhomes.ui.common.FilterBar(
                            filters.url, { filters.url = it },
                            when (tab.slug) { "visits" -> "Search the page address…"; "changes" -> "Search record, type or IP…"; else -> "Search email or IP…" },
                            groups = groups, selected = sel,
                            onApply = { m ->
                                filters.event = m["event"]?.firstOrNull(); filters.guard = m["guard"]?.firstOrNull()
                                filters.user = m["user"]?.firstOrNull(); filters.type = m["type"]?.firstOrNull(); filters.pickDate(m["date"]?.firstOrNull())
                            },
                            countWith = { m -> if (m == sel) total else -1 },
                            sortOptions = listOf("Newest first", "Oldest first"), sortIndex = filters.sort, onSort = { filters.sort = it },
                        )
                        Text(if (loading && rows.isEmpty()) "Loading…" else "$total ${if (total == 1) "record" else "records"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    }
                }

                if (error != null && rows.isEmpty()) item {
                    com.example.gavanhomes.ui.common.EstateError(error, modifier = Modifier.fillMaxWidth().padding(32.dp), onRetry = { reload++ })
                } else if (loading && rows.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) } }
                else if (rows.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nothing recorded", fontWeight = FontWeight.Bold, color = KitInk)
                        Text(if (filters.active) "Try clearing a filter." else "No entries yet.", color = KitMuted, fontSize = 13.sp)
                    }
                }

                itemsIndexed(rows, key = { _, r -> r.optInt("id") }) { _, r ->
                    when (tab.slug) {
                        "logins" -> LoginCard(r)
                        "changes" -> ChangeCard(r, expanded == r.optInt("id")) { expanded = if (expanded == r.optInt("id")) null else r.optInt("id") }
                        else -> VisitCard(r)
                    }
                }

                if (rows.isNotEmpty() && page < lastPage) item {
                    Text(
                        if (loading) "Loading…" else "Load more  (${rows.size} of $total)", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(com.example.gavanhomes.ui.common.KitCard).clickable(enabled = !loading) { scope.launch { load(page + 1) } }.padding(vertical = 15.dp),
                    )
                }
            }
            ScrollTopButton(listState)
            }
        }
    }

    picker?.let { key ->
        val list = choices(when (key) { "guard" -> "guards"; "user" -> "people"; else -> "types" })
        val current = when (key) { "guard" -> filters.guard; "user" -> filters.user; else -> filters.type }
        ChoiceSheet(
            when (key) { "guard" -> "Portal"; "user" -> "Person"; else -> "Record type" },
            list.mapIndexed { i, o -> Choice(i, o.optString("label")) }, setOfNotNull(list.indexOfFirst { it.optString("value") == current }.takeIf { it >= 0 }),
            multi = false, allowNone = true, onDismiss = { picker = null },
        ) { ids ->
            val v = ids.firstOrNull()?.let { list[it].optString("value") }
            when (key) { "guard" -> filters.guard = v; "user" -> filters.user = v; else -> filters.type = v }
        }
    }
}

@Composable
private fun FilterButton(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF0F172A) else com.example.gavanhomes.ui.common.KitCard).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun Card(modifier: Modifier = Modifier, flagged: Boolean = false, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(if (flagged) Color(0xFFFFF4F2) else com.example.gavanhomes.ui.common.KitCard).then(modifier).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun LoginCard(r: JSONObject) {
    val e = r.optString("event")
    Card(flagged = r.optBoolean("suspicious")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Initials(r.str("user") ?: "?", 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.str("user") ?: "—", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.str("email") ?: "", color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Pill(e.headline(), eventColor(e).copy(alpha = .14f), eventColor(e))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            r.str("portal")?.let { Pill(it, com.example.gavanhomes.ui.common.KitSoft, KitInk) }
            r.str("ip")?.let { Pill(it, com.example.gavanhomes.ui.common.KitSoft, KitMuted) }
            if (r.optBoolean("suspicious")) Pill("Suspicious", KitDanger.copy(alpha = .14f), KitDanger)
        }
        r.str("device")?.let { Text(it, color = KitMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Text(whenText(r.str("at")), color = KitMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChangeCard(r: JSONObject, open: Boolean, onToggle: () -> Unit) {
    val e = r.optString("event")
    val changes = r.objects("changes")
    Card(Modifier.clickable { onToggle() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${r.optString("model")}  #${r.optInt("record_id")}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp)
                Text("by ${r.str("by") ?: "System"}${r.str("by_type")?.let { " · $it" } ?: ""}", color = KitMuted, fontSize = 12.sp)
            }
            Pill(e.headline(), eventColor(e).copy(alpha = .14f), eventColor(e))
        }
        if (changes.isNotEmpty()) Text(changes.joinToString(", ") { it.optString("field") }, color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = if (open) 10 else 1, overflow = TextOverflow.Ellipsis)
        if (open) changes.forEach { c ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(c.optString("field"), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 12.sp)
                Text("Before: ${c.str("old") ?: "—"}", color = KitDanger, fontSize = 12.sp)
                Text("After: ${c.str("new") ?: "—"}", color = KitGood, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(whenText(r.str("at")), color = KitMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (changes.isNotEmpty()) Text(if (open) "Hide values ▴" else "View values ▾", color = ProBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VisitCard(r: JSONObject) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(r.optString("method"), ProBlue.copy(alpha = .14f), ProBlue)
            Text(r.str("path") ?: r.optString("url"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
        }
        Text("${r.str("user") ?: "—"}${r.str("portal")?.let { " · $it" } ?: ""}${r.str("ip")?.let { " · $it" } ?: ""}", color = KitMuted, fontSize = 12.sp)
        Text(whenText(r.str("at")), color = KitMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
