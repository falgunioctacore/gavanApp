package com.example.gavanhomes.ui.hod

import com.example.gavanhomes.ui.common.appear
import com.example.gavanhomes.ui.common.HeroSummary
import com.example.gavanhomes.ui.common.accentText
import com.example.gavanhomes.ui.common.ScrollTopButton
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
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
import com.example.gavanhomes.ui.common.PageDrawer
import com.example.gavanhomes.ui.professionals.ProAmber
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.professionals.ProBlue
import com.example.gavanhomes.ui.professionals.ProCard
import com.example.gavanhomes.ui.professionals.ProCounter
import com.example.gavanhomes.ui.professionals.ProFilterChip
import com.example.gavanhomes.ui.professionals.ProRoundAction
import com.example.gavanhomes.ui.professionals.ProTile
import com.example.gavanhomes.ui.professionals.objects
import com.example.gavanhomes.ui.projects.form.Choice
import com.example.gavanhomes.ui.projects.form.ChoiceSheet
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private class Sec(val label: String, val glyph: String, val blurb: String)

private val Secs = listOf(
    Sec("Overview", "⌂", "What was booked and how"),
    Sec("Salespeople", "◎", "Per person, with targets"),
    Sec("Projects", "▦", "Enquiries and bookings per site"),
    Sec("Pipeline", "▽", "Leads → calls → visits → bookings"),
    Sec("Needs attention", "⏱", "Follow-ups that slipped"),
    Sec("Reports", "↗", "Daily, weekly and monthly"),
)
private const val OVERVIEW = 0
private const val STAFF = 1
private const val SITES = 2
private const val FUNNEL = 3
private const val DELAYS = 4
private const val REPORTS = 5

private val Periods = listOf("Today", "Weekly", "Monthly", "LastMonth", "Quarter", "Year", "All", "Customize")
private val PeriodLabel = mapOf(
    "Today" to "Today", "Weekly" to "This week", "Monthly" to "This month", "LastMonth" to "Last month",
    "Quarter" to "Last 3 months", "Year" to "This year", "All" to "All time", "Customize" to "Custom",
)

/** The website knows Today / Weekly / Monthly / Customize; the app's longer periods are sent as a custom date range. */
private fun rangeFor(period: String): Pair<LocalDate, LocalDate>? {
    val now = LocalDate.now()
    return when (period) {
        "LastMonth" -> now.minusMonths(1).withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) }
        "Quarter" -> now.minusMonths(3) to now
        "Year" -> now.withDayOfYear(1) to now
        "All" -> LocalDate.of(2000, 1, 1) to now
        else -> null
    }
}
private val Shown = DateTimeFormatter.ofPattern("d MMM")

/** ₹ in lakh / crore, the way the website prints booking values. */
private fun rupees(v: Double): String = when {
    v >= 1e7 -> "₹" + String.format(java.util.Locale.US, "%.2f Cr", v / 1e7)
    v >= 1e5 -> "₹" + String.format(java.util.Locale.US, "%.2f L", v / 1e5)
    else -> "₹" + String.format(java.util.Locale.US, "%,.0f", v)
}

private fun tone(pct: Int) = if (pct >= 80) KitGood else if (pct >= 40) ProAmber else KitDanger

/**
 * HOD Sales, all native: the website's six panes in a ☰ sidebar. A filter bar (period, custom dates, site) drives every pane;
 * targets can be set per person; "Customize" chooses which widgets show on the overview (saved to the account, like the website).
 * Data: GET /hod/overview, /hod/reports; POST /hod/target/{user}, /hod/preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HodScreen(api: ApiClient, b: Branding, section: Int, onSection: (Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)

    var period by remember { mutableStateOf("Today") }
    var from by remember { mutableStateOf(LocalDate.now()) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    var projectId by remember { mutableStateOf<Int?>(null) }
    var projects by remember { mutableStateOf<List<Choice>>(emptyList()) }
    var root by remember { mutableStateOf<JSONObject?>(null) }
    var reports by remember { mutableStateOf<List<JSONObject>?>(null) }
    var reportTab by remember { mutableIntStateOf(0) }
    var prefsApplied by remember { mutableStateOf(false) }
    val prefs = remember { mutableStateMapOf<String, Boolean>() }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }

    var siteSheet by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }                              // narrows the salespeople and projects by name
    var refreshing by remember { mutableStateOf(false) }                      // pull-down refresh in progress
    LaunchedEffect(loading) { if (!loading) refreshing = false }
    LaunchedEffect(refreshing) { if (refreshing) { kotlinx.coroutines.delay(10_000); refreshing = false } }   // never spin forever
    var customize by remember { mutableStateOf(false) }
    var targetFor by remember { mutableStateOf<JSONObject?>(null) }

    val filter = "$period|$from|$to|$projectId|$reload"

    LaunchedEffect(Unit) {
        runCatching {
            val o = withContext(Dispatchers.IO) { api.detail("/hod/options") }
            projects = o.objects("projects").map { Choice(it.optInt("id"), it.optString("name")) }
        }
    }
    LaunchedEffect(filter) {
        loading = true; error = null
        try {
            val range = rangeFor(period)
            val q = StringBuilder("/hod/overview?period=" + if (range != null) "Customize" else period)
            if (range != null) q.append("&date_from=${range.first}&date_to=${range.second}")
            else if (period == "Customize") q.append("&date_from=$from&date_to=$to")
            projectId?.let { q.append("&project_id=$it") }
            val r = withContext(Dispatchers.IO) { api.get(q.toString()) }
            root = r
            if (!prefsApplied) {   // the account's saved period and widget choices apply the first time
                r.optJSONObject("prefs")?.let { p ->
                    listOf("kpi", "staff", "site", "delay", "funnel", "rank", "target").forEach { k -> prefs[k] = p.optBoolean(k, true) }
                    val saved = p.optString("period", "Today")
                    prefsApplied = true
                    if (saved != "Today" && saved != "Customize" && saved != period) { period = saved; return@LaunchedEffect }
                }
                prefsApplied = true
            }
        } catch (e: Exception) { error = e.message ?: "Could not load" }
        loading = false
    }
    LaunchedEffect(section == REPORTS, projectId, reload) {
        if (section != REPORTS) return@LaunchedEffect
        try {
            reports = withContext(Dispatchers.IO) { api.get("/hod/reports" + (projectId?.let { "?project_id=$it" } ?: "")) }.objects("data")
        } catch (e: Exception) { error = e.message }
    }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_LONG).show()
    val d = root?.optJSONObject("data")
    val canTargets = root?.optJSONObject("can")?.optBoolean("targets") == true
    val sec = Secs[section]

    val pageSlot = remember { androidx.compose.runtime.mutableStateListOf<com.example.gavanhomes.ui.common.PageSpec>() }
    Box(Modifier.fillMaxSize()) {
    androidx.compose.runtime.CompositionLocalProvider(com.example.gavanhomes.ui.common.LocalPages provides pageSlot) {
        Column(Modifier.fillMaxSize().background(KitPage)) {
            com.example.gavanhomes.ui.common.ModulePages("Sales", Secs.map { it.label }, section) { onSection(it) }

            if (d == null) {
                HeroBar(b, sec.label, onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base)
                    else com.example.gavanhomes.ui.common.EstateError(error, modifier = Modifier.padding(24.dp), onRetry = { reload++ })
                }
                return@Column
            }

            val kpis = d.optJSONObject("kpis") ?: JSONObject()
            val q = search.trim()
            val staff = d.objects("staff").filter { q.isEmpty() || it.optString("name").contains(q, true) }
            val sites = d.objects("sites").filter { q.isEmpty() || it.optString("name").contains(q, true) }
            val delays = d.objects("delays")

            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.fillMaxSize()) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    HeroBar(
                        b, if (section == OVERVIEW) "Sales" else sec.label, onBack,
                        actions = {
                            if (section == OVERVIEW) { Spacer(Modifier.size(8.dp)); HeroButton("⚙") { customize = true } }
                        },
                    ) {
                        val items = when (section) {
                            OVERVIEW -> listOf(kpis.optInt("leads").toString() to "Leads", kpis.optInt("calls").toString() to "Calls", kpis.optInt("bookings").toString() to "Bookings", rupees(kpis.optDouble("booking_value")) to "Value")
                            STAFF -> listOf(staff.size.toString() to "Staff", staff.sumOf { it.optInt("bookings") }.toString() to "Bookings", d.optInt("team_target").toString() to "Target", staff.sumOf { it.optInt("pending") }.toString() to "Pending")
                            SITES -> listOf(sites.size.toString() to "Sites", sites.sumOf { it.optInt("leads") }.toString() to "Leads", sites.sumOf { it.optInt("visits") }.toString() to "Visits", sites.sumOf { it.optInt("bookings") }.toString() to "Bookings")
                            FUNNEL -> d.objects("funnel").map { it.optInt("value").toString() to it.optString("label").substringBefore(' ') }
                            DELAYS -> listOf(delays.size.toString() to "Pending", delays.count { (it.optInt("days_late")) > 7 }.toString() to "Over a week", delays.count { it.str("assignee") != null }.toString() to "Assigned")
                            else -> listOf((reports?.size ?: 0).toString() to "Periods")
                        }
                        if (section == OVERVIEW) SalesHeadline(kpis, d) else HeroSummary(items)
                    }
                }

                // ── filters: the same bar as Projects — search, then a Filter page with the period and the site ──
                item {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val sel = buildMap {
                            if (section != REPORTS) put("period", setOf(period))
                            projectId?.let { put("site", setOf(it.toString())) }
                        }
                        com.example.gavanhomes.ui.common.FilterBar(
                            search, { search = it }, "Search salespeople or projects…",
                            groups = listOfNotNull(
                                if (section != REPORTS) com.example.gavanhomes.ui.common.FilterGroup("period", "Period", Periods.map { com.example.gavanhomes.ui.common.FilterOption(it, PeriodLabel.getValue(it)) }) else null,
                                projects.takeIf { it.isNotEmpty() }?.let { ps -> com.example.gavanhomes.ui.common.FilterGroup("site", "Site", ps.map { com.example.gavanhomes.ui.common.FilterOption(it.id.toString(), it.label) }) },
                            ),
                            selected = sel,
                            onApply = { m ->
                                if (section != REPORTS) period = m["period"]?.firstOrNull() ?: "Today"
                                projectId = m["site"]?.firstOrNull()?.toIntOrNull()
                            },
                            countWith = { -1 }, single = setOf("period", "site"),
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (period == "Customize" && section != REPORTS) {
                                DateChip("From ${from.format(Shown)}") { DatePickerDialog(context, { _, y, m, day -> from = LocalDate.of(y, m + 1, day); if (to.isBefore(from)) to = from }, from.year, from.monthValue - 1, from.dayOfMonth).show() }
                                DateChip("To ${to.format(Shown)}") { DatePickerDialog(context, { _, y, m, day -> to = LocalDate.of(y, m + 1, day); if (from.isAfter(to)) from = to }, to.year, to.monthValue - 1, to.dayOfMonth).show() }
                            }
                            if (section != REPORTS) Text(
                                "${d.optString("from")}${if (d.optString("from") != d.optString("to")) "  →  ${d.optString("to")}" else ""}", color = KitMuted, fontSize = 12.sp,
                            )
                            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = b.base)
                        }
                    }
                }

                when (section) {
                    OVERVIEW -> overview(d, kpis, staff, sites, delays, prefs, b, canTargets, onSection) { n -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) }
                    STAFF -> staffList(staff, canTargets, b) { targetFor = it }
                    SITES -> siteList(sites, b)
                    FUNNEL -> funnel(d.objects("funnel"), b)
                    DELAYS -> delayList(delays, b) { n -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) }
                    else -> reportList(reports, reportTab, { reportTab = it }, b)
                }
            }
            ScrollTopButton(listState)
            }
        }
    }
    PageDrawer(b, pageSlot.lastOrNull())
    }

    if (siteSheet) ChoiceSheet("Site", projects, setOf(projectId ?: -1), multi = false, allowNone = true, onDismiss = { siteSheet = false }) { projectId = it.firstOrNull() }

    targetFor?.let { s ->
        TargetSheet(s, d?.optString("month").orEmpty(), onDismiss = { targetFor = null }) { value ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { api.send("POST", "/hod/target/${s.optInt("user_id")}", JSONObject().put("month", d?.optString("month")).put("booking_target", value)) }
                    toast("Target saved"); targetFor = null; reload++
                } catch (e: ApiException) { toast(e.errors.values.firstOrNull() ?: e.message ?: "Could not save") }
                catch (e: Exception) { toast(e.message ?: "Could not save") }
            }
        }
    }

    if (customize) CustomizeSheet(prefs, period, onDismiss = { customize = false }) { body ->
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.send("POST", "/hod/preferences", body) }
                toast("Dashboard customization saved"); customize = false
            } catch (e: Exception) { toast(e.message ?: "Could not save") }
        }
    }
}

@Composable
private fun DateChip(text: String, onClick: () -> Unit) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF0F172A)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp))
}

// ─────────────────────────────── overview ───────────────────────────────

private fun LazyListScope.overview(
    d: JSONObject, k: JSONObject, staff: List<JSONObject>, sites: List<JSONObject>, delays: List<JSONObject>,
    prefs: Map<String, Boolean>, b: Branding, canTargets: Boolean, onSection: (Int) -> Unit, onCall: (String) -> Unit = {},
) {
    fun on(key: String) = prefs[key] != false
    item { Spacer(Modifier.height(6.dp)) }

    // the pipeline: each stage as a bar narrowing towards bookings, with the share that moved on
    if (on("funnel")) item {
        val stages = d.objects("funnel")
        SalesCard("Sales pipeline", "From first enquiry to booking", action = "Details" to { onSection(FUNNEL) }) {
            val top = (stages.firstOrNull()?.optInt("value") ?: 0).coerceAtLeast(1)
            if (stages.all { it.optInt("value") == 0 }) Text("No activity in this period.", color = KitMuted, fontSize = 14.sp)
            else stages.forEachIndexed { i, st ->
                val v = st.optInt("value")
                val prev = stages.getOrNull(i - 1)?.optInt("value")
                if (prev != null) Text(if (prev > 0) "↓ ${(v * 100f / prev).toInt()}% moved on" else "↓", color = KitMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val c = listOf(b.base, ProBlue, Color(0xFF8B5CF6), KitGood, ProAmber)[i % 5]
                    Row(
                        Modifier.fillMaxWidth((.35f + .65f * v / top).coerceIn(.35f, 1f)).height(40.dp).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = .9f)).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(st.optString("label"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(v.toString(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    // the day-to-day work behind it
    if (on("kpi")) item {
        val tiles = listOf(
            Triple("☏", "Calls", k.optInt("calls")) to ProBlue, Triple("↻", "Follow-ups", k.optInt("followups")) to ProAmber,
            Triple("⌂", "Site visits", k.optInt("visits")) to Color(0xFF8B5CF6), Triple("☰", "Line-ups", k.optInt("lineups")) to Color(0xFF475569),
            Triple("⚑", "New leads", k.optInt("leads")) to b.base, Triple("⏱", "Pending", k.optInt("pending")) to KitDanger,
        )
        SalesCard("Activity", "What the team did") {
            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (t, c) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = .08f)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(com.example.gavanhomes.ui.common.KitCard), contentAlignment = Alignment.Center) { Text(t.first, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                            Text(t.third.toString(), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
                            Text(t.second, color = KitMuted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    // the month's team target as a ring
    if (on("target")) item {
        val target = d.optInt("team_target"); val done = d.optInt("team_bookings")
        val pct = if (target > 0) (done * 100 / target).coerceAtMost(100) else 0
        SalesCard("Team target", "This month", action = if (canTargets) "Set targets" to { onSection(STAFF) } else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.size(96.dp)) {
                        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * .12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawArc(com.example.gavanhomes.ui.common.KitLine, -90f, 360f, false, style = st)
                        if (pct > 0) drawArc(tone(pct), -90f, 360f * pct / 100f, false, style = st)
                    }
                    Text("$pct%", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("$done of $target", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = KitInk)
                    Text("bookings", color = KitMuted, fontSize = 13.sp)
                    Text(if (target == 0) "No targets set yet" else if (done >= target) "Target reached 🎉" else "${target - done} more to go", color = if (target == 0) KitMuted else tone(pct), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    // who is leading
    if (on("rank")) item {
        val top = d.objects("top")
        SalesCard("Leaderboard", "By target achievement", action = "Team" to { onSection(STAFF) }) {
            if (top.isEmpty() || top.all { it.optInt("achievement") == 0 && it.optInt("bookings") == 0 }) Text("No bookings against targets yet.", color = KitMuted, fontSize = 14.sp)
            else top.forEachIndexed { i, t ->
                val pct = t.optInt("achievement")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (i == 0) ProAmber.copy(alpha = .08f) else com.example.gavanhomes.ui.common.KitSoft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(listOf("🥇", "🥈", "🥉").getOrElse(i) { "${i + 1}" }, fontSize = 20.sp, modifier = Modifier.size(30.dp), textAlign = TextAlign.Center)
                    Initials(t.str("name") ?: "?", 38.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t.str("name") ?: "—", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${t.optInt("bookings")} bookings", color = KitMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp)); ProBar(pct / 100f, tone(pct))
                    }
                    Text("$pct%", fontWeight = FontWeight.ExtraBold, color = tone(pct), fontSize = 15.sp)
                }
            }
        }
    }

    // the team, busiest first
    if (on("staff")) item {
        val list = staff.sortedByDescending { it.optInt("bookings") * 1000 + it.optInt("leads") }.take(5)
        val most = (list.maxOfOrNull { it.optInt("leads") } ?: 0).coerceAtLeast(1)
        SalesCard("Salespeople", "${staff.size} on the team", action = "All" to { onSection(STAFF) }) {
            if (list.isEmpty()) Text("No salespeople yet.", color = KitMuted, fontSize = 14.sp)
            list.forEach { s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSection(STAFF) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Initials(s.str("name") ?: "?", 36.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Row { Text(s.str("name") ?: "—", fontWeight = FontWeight.SemiBold, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${s.optInt("bookings")} booked", color = KitGood, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(4.dp)); ProBar(s.optInt("leads").toFloat() / most, ProBlue)
                        Text("${s.optInt("leads")} leads · ${s.optInt("visits")} visits", color = KitMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    // the projects, by what they sold
    if (on("site")) item {
        val list = sites.sortedByDescending { it.optDouble("booking_value") * 1000 + it.optInt("leads") }.take(5)
        val most = (list.maxOfOrNull { it.optInt("leads") } ?: 0).coerceAtLeast(1)
        SalesCard("Projects", "Enquiries and bookings by site", action = "All" to { onSection(SITES) }) {
            if (list.isEmpty()) Text("No sites.", color = KitMuted, fontSize = 14.sp)
            list.forEach { s ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).clickable { onSection(SITES) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(b.base.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text((s.str("name") ?: "?").take(1).uppercase(), color = b.accentText, fontWeight = FontWeight.ExtraBold) }
                        Text(s.str("name") ?: "—", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(rupees(s.optDouble("booking_value")), fontWeight = FontWeight.ExtraBold, color = KitGood, fontSize = 13.sp)
                    }
                    ProBar(s.optInt("leads").toFloat() / most, b.base)
                    Text("${s.optInt("leads")} leads · ${s.optInt("visits")} visits · ${s.optInt("bookings")} bookings", color = KitMuted, fontSize = 11.sp)
                }
            }
        }
    }

    // follow-ups that slipped, with a call button
    if (on("delay")) item {
        SalesCard("Needs attention", if (delays.isEmpty()) "Every follow-up is on time" else "${delays.size} follow-ups slipped", action = if (delays.isNotEmpty()) "All" to { onSection(DELAYS) } else null) {
            if (delays.isEmpty()) Text("✓ Nothing is overdue.", color = KitGood, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            delays.take(4).forEach { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.str("customer") ?: "Lead #${f.optInt("lead_id")}", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f.str("assignee") ?: "Unassigned", color = KitMuted, fontSize = 12.sp)
                    }
                    late(f)
                    f.str("phone")?.let { n -> Spacer(Modifier.size(8.dp)); ProRoundAction("✆", b.base) { onCall(n) } }
                }
            }
        }
    }
}

/** A white card of the Sales page: a title, a line under it, an optional link on the right, then the content. */
@Composable
private fun SalesCard(title: String, sub: String? = null, action: Pair<String, () -> Unit>? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.appear().padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(5.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                sub?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
            }
            action?.let { (l, go) -> Text("$l  ›", color = ProBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { go() }.padding(6.dp)) }
        }
        content()
    }
}

/** The header of the Sales page: booking value, bookings against leads, and the conversion as a ring. */
@Composable
private fun SalesHeadline(k: JSONObject, d: JSONObject) {
    val leads = k.optInt("leads"); val bookings = k.optInt("bookings")
    val conv = if (leads > 0) bookings * 100f / leads else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("BOOKED", color = Color.White.copy(alpha = .75f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(rupees(k.optDouble("booking_value")), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("$bookings booking${if (bookings == 1) "" else "s"} from $leads lead${if (leads == 1) "" else "s"}", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
        }
        Box(Modifier.size(82.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(82.dp)) {
                val st = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * .11f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(Color.White.copy(alpha = .22f), -90f, 360f, false, style = st)
                if (conv > 0) drawArc(Color.White, -90f, 360f * (conv / 100f).coerceIn(0.02f, 1f), false, style = st)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(java.util.Locale.US, "%.1f%%", conv), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("convert", color = Color.White.copy(alpha = .8f), fontSize = 9.sp)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("☏ ${k.optInt("calls")} calls", "⌂ ${k.optInt("visits")} visits", "↻ ${k.optInt("followups")} follow-ups").forEach {
            Text(it, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .18f)).padding(horizontal = 11.dp, vertical = 6.dp))
        }
        if (k.optInt("pending") > 0) Text("⏱ ${k.optInt("pending")} pending", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitDanger.copy(alpha = .55f)).padding(horizontal = 11.dp, vertical = 6.dp))
    }
}

/** Today · This week · This month · Custom, as one segmented control. */
@Composable
private fun PeriodSwitch(period: String, b: Branding, onPick: (String) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(4.dp)) {
        Periods.forEach { p ->
            val on = p == period
            Text(
                PeriodLabel.getValue(p), color = if (on) Color.White else KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) b.base else Color.Transparent).clickable { onPick(p) }.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun late(f: JSONObject) {
    val days = if (f.isNull("days_late")) null else f.optInt("days_late")
    val c = if (days == null) KitMuted else if (days > 7) KitDanger else ProAmber
    Pill(if (days == null) "No date" else if (days <= 0) "Due" else "${days}d late", c.copy(alpha = .14f), c)
}

// ─────────────────────────────── staff ───────────────────────────────

private fun LazyListScope.staffList(staff: List<JSONObject>, canTargets: Boolean, b: Branding, onTarget: (JSONObject) -> Unit) {
    item { Spacer(Modifier.height(6.dp)) }
    if (staff.isEmpty()) { item { Empty("No salespeople yet", "People appear here once leads are assigned to them.") }; return }
    // the team in one line: bookings against target, value, pending
    item {
        val booked = staff.sumOf { it.optInt("bookings") }; val target = staff.sumOf { it.optInt("target") }
        val pct = if (target > 0) (booked * 100 / target).coerceAtMost(100) else 0
        SalesCard("The team", "${staff.size} salespeople") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("$booked / $target", "Booked / target", tone(pct), Modifier.weight(1f))
                Stat(rupees(staff.sumOf { it.optDouble("booking_value") }), "Value", KitGood, Modifier.weight(1f))
                Stat("${staff.sumOf { it.optInt("pending") }}", "Pending", if (staff.sumOf { it.optInt("pending") } > 0) KitDanger else KitMuted, Modifier.weight(1f))
            }
            ProBar(pct / 100f, tone(pct))
        }
    }
    val ranked = staff.sortedWith(compareByDescending<JSONObject> { it.optInt("achievement") }.thenByDescending { it.optInt("bookings") }.thenByDescending { it.optInt("leads") })
    items(ranked.withIndex().toList(), key = { it.value.optInt("user_id") }) { (i, s) ->
        val pct = s.optInt("achievement")
        Column(
            Modifier.appear().padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(5.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listOf("🥇", "🥈", "🥉").getOrElse(i) { "#${i + 1}" }, fontSize = if (i < 3) 20.sp else 13.sp, fontWeight = FontWeight.ExtraBold, color = KitMuted, modifier = Modifier.size(32.dp), textAlign = TextAlign.Center)
                Initials(s.str("name") ?: "?", 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.str("name") ?: "—", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${rupees(s.optDouble("booking_value"))} booked", color = KitGood, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                SmallRing(pct, tone(pct))
            }
            MiniPipeline(listOf("Leads" to s.optInt("leads"), "Calls" to s.optInt("calls"), "Visits" to s.optInt("visits"), "Booked" to s.optInt("bookings")), b)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row { Text("Monthly target", color = KitMuted, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(if (s.optInt("target") > 0) "${s.optInt("bookings")} of ${s.optInt("target")}" else "not set", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 12.sp) }
                ProBar(pct / 100f, tone(pct))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("↻ ${s.optInt("followups")} follow-ups", color = KitMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (s.optInt("pending") > 0) Text("⏱ ${s.optInt("pending")} pending", color = KitDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (canTargets) Text("◎  Set this month's target", color = b.accentText, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base.copy(alpha = .14f)).clickable { onTarget(s) }.padding(vertical = 11.dp))
        }
    }
}

/** Four steps side by side, joined by arrows: Leads › Calls › Visits › Booked. */
@Composable
private fun MiniPipeline(steps: List<Pair<String, Int>>, b: Branding) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, (l, v) ->
            if (i > 0) Text("›", color = KitMuted, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 3.dp))
            val last = i == steps.lastIndex
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (last) KitGood.copy(alpha = .1f) else com.example.gavanhomes.ui.common.KitSoft).padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (last) KitGood else KitInk)
                Text(l, fontSize = 10.sp, color = KitMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SmallRing(pct: Int, c: Color) {
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(52.dp)) {
            val st = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * .12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(com.example.gavanhomes.ui.common.KitLine, -90f, 360f, false, style = st)
            if (pct > 0) drawArc(c, -90f, 360f * pct.coerceAtMost(100) / 100f, false, style = st)
        }
        Text("$pct%", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = KitInk)
    }
}

@Composable
private fun Stat(value: String, label: String, c: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 11.dp)) {
        Text(value, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
        Text(label, color = KitMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun Empty(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp)
        Text(text, color = KitMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier, tint: Color = KitInk) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = tint)
        Text(label, fontSize = 10.sp, color = KitMuted, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSheet(s: JSONObject, month: String, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(s.optInt("target").takeIf { it > 0 }?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.example.gavanhomes.ui.common.KitCard) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(s.str("name") ?: "?", 44.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(s.str("name") ?: "Target", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                    Text("Booking target for $month", color = KitMuted, fontSize = 13.sp)
                }
            }
            Text("They have ${s.optInt("bookings")} booking${if (s.optInt("bookings") == 1) "" else "s"} in the selected period.", color = KitMuted, fontSize = 13.sp)
            Input("Bookings target", text, { text = it.filter(Char::isDigit); error = null }, error, keyboard = KeyboardType.Number)
            Text("Save target", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0F172A)).clickable {
                    val v = text.toIntOrNull()
                    if (v == null || v > 100000) error = "Enter a number from 0 to 100000" else onSave(v)
                }.padding(vertical = 15.dp))
        }
    }
}

// ─────────────────────────────── sites ───────────────────────────────

private fun LazyListScope.siteList(sites: List<JSONObject>, b: Branding) {
    item { Spacer(Modifier.height(6.dp)) }
    if (sites.isEmpty()) { item { Empty("No projects", "Projects appear here once they have leads.") }; return }
    item {
        SalesCard("All projects", "${sites.size} sites in this period") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${sites.sumOf { it.optInt("leads") }}", "Leads", ProBlue, Modifier.weight(1f))
                Stat("${sites.sumOf { it.optInt("visits") }}", "Site visits", Color(0xFF8B5CF6), Modifier.weight(1f))
                Stat("${sites.sumOf { it.optInt("bookings") }}", "Bookings", KitGood, Modifier.weight(1f))
            }
            Text("Value booked: ${rupees(sites.sumOf { it.optDouble("booking_value") })}", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
    val most = (sites.maxOfOrNull { it.optInt("leads") } ?: 0).coerceAtLeast(1)
    items(sites.sortedByDescending { it.optDouble("booking_value") * 1000 + it.optInt("leads") }, key = { it.optInt("id") }) { s ->
        val leads = s.optInt("leads"); val conv = if (leads > 0) s.optInt("bookings") * 100f / leads else 0f
        Column(
            Modifier.appear().padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(5.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(b.base, b.deep))), contentAlignment = Alignment.Center) {
                    Text((s.str("name") ?: "?").take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.str("name") ?: "—", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(String.format(java.util.Locale.US, "%.1f%% of leads booked", conv), color = KitMuted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(rupees(s.optDouble("booking_value")), fontWeight = FontWeight.ExtraBold, color = KitGood, fontSize = 15.sp)
                    Text("booked", color = KitMuted, fontSize = 11.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text("Share of all leads", color = KitMuted, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text("$leads", color = KitInk, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                ProBar(leads.toFloat() / most, b.base)
            }
            MiniPipeline(listOf("Leads" to leads, "Calls" to s.optInt("calls"), "Visits" to s.optInt("visits"), "Booked" to s.optInt("bookings")), b)
            Text("↻ ${s.optInt("followups")} follow-ups in this period", color = KitMuted, fontSize = 12.sp)
        }
    }
}

// ─────────────────────────────── funnel ───────────────────────────────

@Composable
private fun funnelCard(stages: List<JSONObject>, b: Branding) {
    SalesCard("Sales pipeline", "Each stage narrows towards bookings") {
        val top = (stages.firstOrNull()?.optInt("value") ?: 0).coerceAtLeast(1)
        if (stages.all { it.optInt("value") == 0 }) Text("No activity in this period.", color = KitMuted, fontSize = 14.sp)
        else stages.forEachIndexed { i, st ->
            val v = st.optInt("value"); val prev = stages.getOrNull(i - 1)?.optInt("value")
            if (prev != null) Text(if (prev > 0) "↓ ${(v * 100f / prev).toInt()}% moved on" else "↓", color = KitMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val c = listOf(b.base, ProBlue, Color(0xFF8B5CF6), KitGood, ProAmber)[i % 5]
                Row(Modifier.fillMaxWidth((.35f + .65f * v / top).coerceIn(.35f, 1f)).height(44.dp).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = .9f)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(st.optString("label"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(v.toString(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }
        }
    }
}

private fun LazyListScope.funnel(stages: List<JSONObject>, b: Branding) {
    item { Spacer(Modifier.height(6.dp)) }
    item { Column { funnelCard(stages, b) } }
    val l = stages.firstOrNull()?.optInt("value") ?: 0; val bk = stages.lastOrNull()?.optInt("value") ?: 0
    item {
        SalesCard("Lead to booking", "The share of enquiries that became a booking") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallRing(if (l > 0) (bk * 100 / l) else 0, KitGood)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(if (l > 0) String.format(java.util.Locale.US, "%.1f%%", bk * 100f / l) else "—", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = KitInk)
                    Text("$bk bookings from $l leads", color = KitMuted, fontSize = 13.sp)
                }
            }
        }
    }
    // where most people drop out
    val drops = stages.zipWithNext { a, c -> Triple(a.optString("label"), c.optString("label"), if (a.optInt("value") > 0) 100 - c.optInt("value") * 100 / a.optInt("value") else 0) }
    drops.maxByOrNull { it.third }?.takeIf { it.third > 0 }?.let { (from, to, lost) ->
        item {
            SalesCard("Biggest drop", "Where the most people are lost") {
                Text("$lost% stop between $from and $to", color = KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("Focus follow-ups here to lift bookings the most.", color = KitMuted, fontSize = 12.sp)
                drops.forEach { (a, c, p) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$a → $c", color = KitInk, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("−$p%", color = if (p == lost) KitDanger else KitMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────── delays ───────────────────────────────

private fun LazyListScope.delayList(delays: List<JSONObject>, b: Branding, onCall: (String) -> Unit) {
    item { Spacer(Modifier.height(6.dp)) }
    if (delays.isEmpty()) { item { Empty("✓ Nothing is overdue", "Every follow-up is on time.") }; return }
    val days = { f: JSONObject -> if (f.isNull("days_late")) null else f.optInt("days_late") }
    val groups = listOf(
        Triple("Over a week late", KitDanger, delays.filter { (days(it) ?: -1) > 7 }),
        Triple("Late this week", ProAmber, delays.filter { days(it)?.let { d -> d in 0..7 } == true }),
        Triple("No follow-up date", KitMuted, delays.filter { days(it) == null || days(it)!! < 0 }),
    ).filter { it.third.isNotEmpty() }
    item {
        SalesCard("Follow-ups that slipped", "${delays.size} need a call") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { groups.forEach { (l, c, list) -> Stat("${list.size}", l, c, Modifier.weight(1f)) } }
        }
    }
    groups.forEach { (label, c, list) ->
        item { Row(Modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Text("  ${label.uppercase()} · ${list.size}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = KitMuted, letterSpacing = 0.6.sp) } }
        items(list, key = { it.optInt("id") }) { f ->
            Row(
                Modifier.appear().padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(4.dp, 44.dp).clip(RoundedCornerShape(2.dp)).background(c))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(f.str("customer") ?: "Lead #${f.optInt("lead_id")}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(f.str("assignee") ?: "Unassigned", f.str("type"), f.str("status")?.replace('_', ' ')).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    f.str("due")?.let { Text("Was due ${it.take(16)}", color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    late(f)
                    f.str("phone")?.let { n -> ProRoundAction("✆", b.base) { onCall(n) } }
                }
            }
        }
    }
}

// ─────────────────────────────── reports ───────────────────────────────

private fun LazyListScope.reportList(reports: List<JSONObject>?, tab: Int, onTab: (Int) -> Unit, b: Branding) {
    item { Spacer(Modifier.height(6.dp)) }
    if (reports == null) { item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) } }; return }
    if (reports.isEmpty()) { item { Empty("No reports yet", "Reports appear once there is sales activity.") }; return }
    // the periods as one segmented control
    item {
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(4.dp)) {
            reports.forEachIndexed { i, r ->
                val on = i == tab
                Text(r.optString("label"), color = if (on) Color.White else KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) b.base else Color.Transparent).clickable { onTab(i) }.padding(vertical = 10.dp))
            }
        }
    }
    val r = reports.getOrNull(tab) ?: return
    val k = r.optJSONObject("kpis") ?: JSONObject()
    item {
        SalesCard(r.optString("label") + " report", r.optString("range")) {
            Text(rupees(k.optDouble("booking_value")), color = KitGood, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            Text("booked", color = KitMuted, fontSize = 12.sp)
            MiniPipeline(listOf("Leads" to k.optInt("leads"), "Calls" to k.optInt("calls"), "Visits" to k.optInt("visits"), "Booked" to k.optInt("bookings")), b)
        }
    }
    val rows = r.objects("rows").sortedByDescending { it.optInt("achievement") * 1000 + it.optInt("bookings") }
    item {
        SalesCard("Salespeople", if (rows.isEmpty()) "No staff data" else "Ranked by target achievement") {
            rows.forEachIndexed { i, s ->
                val pct = s.optInt("achievement")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(listOf("🥇", "🥈", "🥉").getOrElse(i) { "${i + 1}" }, fontSize = if (i < 3) 18.sp else 12.sp, fontWeight = FontWeight.ExtraBold, color = KitMuted, modifier = Modifier.size(28.dp), textAlign = TextAlign.Center)
                    Initials(s.str("name") ?: "?", 34.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(s.str("name") ?: "—", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.optInt("leads")} leads · ${s.optInt("visits")} visits · ${s.optInt("bookings")} booked", color = KitMuted, fontSize = 11.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp)); ProBar(pct / 100f, tone(pct))
                    }
                    Text("$pct%", fontWeight = FontWeight.ExtraBold, color = tone(pct), fontSize = 13.sp)
                }
            }
        }
    }
}

// ─────────────────────────────── customize ───────────────────────────────

private val Widgets = listOf(
    "kpi" to "Key numbers", "rank" to "Top performers", "target" to "Team target", "funnel" to "Conversion funnel",
    "staff" to "Staff summary", "site" to "Site summary", "delay" to "Delay & pending",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeSheet(prefs: MutableMap<String, Boolean>, period: String, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var defaultPeriod by remember { mutableStateOf(if (period == "Customize") "Today" else period) }
    var pickPeriod by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.example.gavanhomes.ui.common.KitCard) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Customize overview", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Text("Choose what the overview shows. Saved to your account, on every device.", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            Widgets.forEach { (key, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = KitInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(checked = prefs[key] != false, onCheckedChange = { prefs[key] = it })
                }
            }
            Spacer(Modifier.height(6.dp))
            PickerField("Open on period", PeriodLabel[defaultPeriod]) { pickPeriod = true }
            Spacer(Modifier.height(6.dp))
            Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0F172A)).clickable {
                    val body = JSONObject(); Widgets.forEach { (k, _) -> body.put(k, prefs[k] != false) }; body.put("period", defaultPeriod); onSave(body)
                }.padding(vertical = 15.dp))
        }
    }
    if (pickPeriod) {
        val opts = listOf("Today", "Weekly", "Monthly")   // the defaults the website can store
        ChoiceSheet("Open on period", opts.mapIndexed { i, p -> Choice(i, PeriodLabel.getValue(p)) }, setOf(opts.indexOf(defaultPeriod)), multi = false, onDismiss = { pickPeriod = false }) { ids -> ids.firstOrNull()?.let { defaultPeriod = opts[it] } }
    }
}
