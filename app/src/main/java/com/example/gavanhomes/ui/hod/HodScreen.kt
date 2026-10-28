package com.example.gavanhomes.ui.hod

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
    Sec("Team overview", "⌂", "Key numbers and rankings"),
    Sec("Staff performance", "◎", "Per salesperson, with targets"),
    Sec("Site performance", "▦", "Per project"),
    Sec("Conversion funnel", "▽", "Leads → calls → visits → bookings"),
    Sec("Delay & pending", "⏱", "Follow-ups that slipped"),
    Sec("Reports", "↗", "Daily, weekly and monthly"),
)
private const val OVERVIEW = 0
private const val STAFF = 1
private const val SITES = 2
private const val FUNNEL = 3
private const val DELAYS = 4
private const val REPORTS = 5

private val Periods = listOf("Today", "Weekly", "Monthly", "Customize")
private val PeriodLabel = mapOf("Today" to "Today", "Weekly" to "This week", "Monthly" to "This month", "Customize" to "Custom")
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
            val q = StringBuilder("/hod/overview?period=$period")
            if (period == "Customize") q.append("&date_from=$from&date_to=$to")
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

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White, windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(b.deep, b.base), start = Offset.Zero, end = Offset(700f, 700f)))
                            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (b.logo != null) { com.example.gavanhomes.ui.common.BrandMark(b, 52.dp); Spacer(Modifier.size(14.dp)) }
                        Column {
                            Text("HOD Sales", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Team and site performance", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Secs.forEachIndexed { i, s ->
                        val on = i == section
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) b.base.copy(alpha = .16f) else Color.Transparent)
                                .clickable { onSection(i); scope.launch { drawer.close() } }.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) b.base else Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) {
                                Text(s.glyph, color = if (on) Color.White else KitInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(s.label, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = KitInk, fontSize = 15.sp)
                                Text(s.blurb, color = KitMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(KitPage)) {
            val toggle: @Composable () -> Unit = { HeroButton("☰") { scope.launch { drawer.open() } } }

            if (d == null) {
                HeroBar(b, sec.label, onBack, actions = { toggle() })
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (error == null) CircularProgressIndicator(color = b.base)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { reload++ }) { Text("Retry") }
                    }
                }
                return@Column
            }

            val kpis = d.optJSONObject("kpis") ?: JSONObject()
            val staff = d.objects("staff")
            val sites = d.objects("sites")
            val delays = d.objects("delays")

            LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    HeroBar(
                        b, sec.label, onBack,
                        actions = {
                            toggle(); Spacer(Modifier.size(8.dp)); HeroButton("⟳") { reload++ }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items.forEachIndexed { i, (v, l) -> ProCounter(v, l, Modifier.weight(if (i == 0) 1.2f else 1f), big = i == 0) } }
                    }
                }

                // ── filters: period, custom dates, site ──
                item {
                    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (section != REPORTS) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Periods.forEach { p -> ProFilterChip(PeriodLabel.getValue(p), null, null, period == p) { period = p } }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (period == "Customize" && section != REPORTS) {
                                DateChip("From ${from.format(Shown)}") { DatePickerDialog(context, { _, y, m, day -> from = LocalDate.of(y, m + 1, day); if (to.isBefore(from)) to = from }, from.year, from.monthValue - 1, from.dayOfMonth).show() }
                                DateChip("To ${to.format(Shown)}") { DatePickerDialog(context, { _, y, m, day -> to = LocalDate.of(y, m + 1, day); if (from.isAfter(to)) from = to }, to.year, to.monthValue - 1, to.dayOfMonth).show() }
                            }
                            Text(
                                "▦ " + (projects.firstOrNull { it.id == projectId }?.label ?: "All sites"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = KitInk, maxLines = 1,
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { siteSheet = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = b.base)
                        }
                        if (section != REPORTS) Text(
                            "${d.optString("from")}${if (d.optString("from") != d.optString("to")) "  →  ${d.optString("to")}" else ""}", color = KitMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }

                when (section) {
                    OVERVIEW -> overview(d, kpis, staff, sites, delays, prefs, b, canTargets, onSection)
                    STAFF -> staffList(staff, canTargets, b) { targetFor = it }
                    SITES -> siteList(sites, b)
                    FUNNEL -> funnel(d.objects("funnel"), b)
                    DELAYS -> delayList(delays, b) { n -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) }
                    else -> reportList(reports, reportTab, { reportTab = it }, b)
                }
            }
        }
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
    prefs: Map<String, Boolean>, b: Branding, canTargets: Boolean, onSection: (Int) -> Unit,
) {
    fun on(key: String) = prefs[key] != false
    item { Spacer(Modifier.height(14.dp)) }

    if (on("kpi")) item {
        val tiles = listOf(
            Triple("Leads", k.optInt("leads").toString(), ProBlue), Triple("Calls", k.optInt("calls").toString(), ProBlue),
            Triple("Follow-ups", k.optInt("followups").toString(), ProAmber), Triple("Site visits", k.optInt("visits").toString(), Color(0xFF8B5CF6)),
            Triple("Line-ups", k.optInt("lineups").toString(), Color(0xFF475569)), Triple("Pending", k.optInt("pending").toString(), KitDanger),
            Triple("Bookings", k.optInt("bookings").toString(), KitGood), Triple("Booking value", rupees(k.optDouble("booking_value")), KitGood),
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { pair.forEach { (l, v, c) -> ProTile(v, l, Modifier.weight(1f), c) } } }
        }
    }

    if (on("rank")) {
        val top = d.objects("top")
        item {
            ProCard("Top performers", "By target achievement") {
                if (top.isEmpty() || top.all { it.optInt("achievement") == 0 }) Text("No bookings against targets yet.", color = KitMuted, fontSize = 14.sp)
                else top.forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontWeight = FontWeight.ExtraBold, color = if (i == 0) ProAmber else KitMuted, fontSize = 18.sp, modifier = Modifier.size(28.dp))
                        Initials(t.str("name") ?: "?", 38.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(t.str("name") ?: "—", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp)
                            Text("${t.optInt("bookings")} bookings", color = KitMuted, fontSize = 12.sp)
                        }
                        Pill("${t.optInt("achievement")}%", tone(t.optInt("achievement")).copy(alpha = .14f), tone(t.optInt("achievement")))
                    }
                }
            }
        }
    }

    if (on("target")) item {
        val target = d.optInt("team_target"); val done = d.optInt("team_bookings")
        val pct = if (target > 0) (done * 100f / target).coerceAtMost(100f) else 0f
        ProCard("Team target", "This month") {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$done", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = KitInk)
                Text(" of $target bookings", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp).weight(1f))
                Text("${pct.toInt()}%", fontWeight = FontWeight.ExtraBold, color = tone(pct.toInt()))
            }
            ProBar(pct / 100f, tone(pct.toInt()))
            if (target == 0) Text("No targets are set — open Staff performance to set them.", color = KitMuted, fontSize = 12.sp)
        }
    }

    if (on("funnel")) item { Column { funnelCard(d.objects("funnel"), b) } }

    if (on("staff")) item {
        ProCard("Staff", "Tap to see everyone") {
            if (staff.isEmpty()) Text("No salespeople yet.", color = KitMuted, fontSize = 14.sp)
            staff.sortedByDescending { it.optInt("bookings") }.take(5).forEach { s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSection(STAFF) }, verticalAlignment = Alignment.CenterVertically) {
                    Initials(s.str("name") ?: "?", 36.dp)
                    Text(s.str("name") ?: "—", fontWeight = FontWeight.SemiBold, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.optInt("leads")} leads · ${s.optInt("bookings")} booked", color = KitMuted, fontSize = 12.sp)
                }
            }
        }
    }

    if (on("site")) item {
        ProCard("Sites", "Tap to see every site") {
            if (sites.isEmpty()) Text("No sites.", color = KitMuted, fontSize = 14.sp)
            sites.sortedByDescending { it.optInt("bookings") * 1000 + it.optInt("leads") }.take(5).forEach { s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSection(SITES) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.str("name") ?: "—", fontWeight = FontWeight.SemiBold, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.optInt("leads")} leads · ${s.optInt("bookings")} booked", color = KitMuted, fontSize = 12.sp)
                }
            }
        }
    }

    if (on("delay")) item {
        ProCard("Delay & pending", "${delays.size} follow-ups need action") {
            if (delays.isEmpty()) Text("Nothing is overdue.", color = KitMuted, fontSize = 14.sp)
            delays.take(4).forEach { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSection(DELAYS) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.str("customer") ?: "Lead #${f.optInt("lead_id")}", fontWeight = FontWeight.SemiBold, color = KitInk, fontSize = 14.sp, maxLines = 1)
                        Text(f.str("assignee") ?: "Unassigned", color = KitMuted, fontSize = 12.sp)
                    }
                    late(f)
                }
            }
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
    item { Spacer(Modifier.height(14.dp)) }
    if (staff.isEmpty()) item { Text("No salespeople have been assigned leads yet.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp)) }
    items(staff.sortedByDescending { it.optInt("achievement") }, key = { it.optInt("user_id") }) { s ->
        val pct = s.optInt("achievement")
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White)
                .then(if (canTargets) Modifier.clickable { onTarget(s) } else Modifier).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(s.str("name") ?: "?", 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.str("name") ?: "—", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp)
                    Text("${rupees(s.optDouble("booking_value"))} booked", color = KitMuted, fontSize = 12.sp)
                }
                Pill("$pct%", tone(pct).copy(alpha = .14f), tone(pct))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { Text("Target", color = KitMuted, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("${s.optInt("bookings")} / ${s.optInt("target")}", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 12.sp) }
                ProBar(pct / 100f, tone(pct))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Leads", s.optInt("leads"), Modifier.weight(1f)); Metric("Calls", s.optInt("calls"), Modifier.weight(1f)); Metric("Follow-ups", s.optInt("followups"), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Visits", s.optInt("visits"), Modifier.weight(1f)); Metric("Pending", s.optInt("pending"), Modifier.weight(1f), if (s.optInt("pending") > 0) KitDanger else KitInk); Metric("Bookings", s.optInt("bookings"), Modifier.weight(1f), KitGood)
            }
            if (canTargets) Text("Tap to set this month's target", color = KitMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier, tint: Color = KitInk) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = tint)
        Text(label, fontSize = 10.sp, color = KitMuted, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSheet(s: JSONObject, month: String, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(s.optInt("target").takeIf { it > 0 }?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s.str("name") ?: "Target", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Text("Booking target for $month. They have ${s.optInt("bookings")} booking${if (s.optInt("bookings") == 1) "" else "s"} in the selected period.", color = KitMuted, fontSize = 13.sp)
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
    item { Spacer(Modifier.height(14.dp)) }
    if (sites.isEmpty()) item { Text("No sites.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp)) }
    items(sites.sortedByDescending { it.optInt("bookings") * 1000 + it.optInt("leads") }, key = { it.optInt("id") }) { s ->
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(b.base.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Text((s.str("name") ?: "?").take(1).uppercase(), color = b.dark, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                Text(s.str("name") ?: "—", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(rupees(s.optDouble("booking_value")), fontWeight = FontWeight.ExtraBold, color = KitGood, fontSize = 14.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Leads", s.optInt("leads"), Modifier.weight(1f)); Metric("Calls", s.optInt("calls"), Modifier.weight(1f)); Metric("Follow-ups", s.optInt("followups"), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Visits", s.optInt("visits"), Modifier.weight(1f)); Metric("Bookings", s.optInt("bookings"), Modifier.weight(1f), KitGood); Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────── funnel ───────────────────────────────

@Composable
private fun funnelCard(stages: List<JSONObject>, b: Branding) {
    ProCard("Conversion funnel", "Each stage against the one before") {
        val top = (stages.firstOrNull()?.optInt("value") ?: 0).coerceAtLeast(1)
        if (stages.all { it.optInt("value") == 0 }) Text("No activity in this period.", color = KitMuted, fontSize = 14.sp)
        stages.forEachIndexed { i, st ->
            val v = st.optInt("value")
            val prev = stages.getOrNull(i - 1)?.optInt("value")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(st.optString("label"), color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (prev != null && prev > 0) Text("${(v * 100f / prev).toInt()}%", color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                    Text(v.toString(), fontWeight = FontWeight.ExtraBold, color = KitInk)
                }
                ProBar(v.toFloat() / top, listOf(b.base, ProBlue, Color(0xFF8B5CF6), KitGood)[i % 4])
            }
        }
    }
}

private fun LazyListScope.funnel(stages: List<JSONObject>, b: Branding) {
    item { Spacer(Modifier.height(14.dp)) }
    item { Column { funnelCard(stages, b) } }
    item {
        val l = stages.getOrNull(0)?.optInt("value") ?: 0; val bk = stages.getOrNull(3)?.optInt("value") ?: 0
        ProCard("Lead to booking") {
            Text(if (l > 0) String.format(java.util.Locale.US, "%.1f%%", bk * 100f / l) else "—", fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = KitInk)
            Text("$bk bookings from $l leads in this period", color = KitMuted, fontSize = 13.sp)
        }
    }
}

// ─────────────────────────────── delays ───────────────────────────────

private fun LazyListScope.delayList(delays: List<JSONObject>, b: Branding, onCall: (String) -> Unit) {
    item { Spacer(Modifier.height(14.dp)) }
    if (delays.isEmpty()) item {
        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing is overdue", fontWeight = FontWeight.Bold, color = KitInk)
            Text("Every follow-up is on time.", color = KitMuted, fontSize = 13.sp)
        }
    }
    items(delays, key = { it.optInt("id") }) { f ->
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(f.str("customer") ?: "Lead #${f.optInt("lead_id")}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(f.str("assignee") ?: "Unassigned", f.str("type"), f.str("status")?.replace('_', ' ')).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                f.str("due")?.let { Text("Due ${it.take(16)}", color = KitMuted, fontSize = 11.sp) }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                late(f)
                f.str("phone")?.let { n -> ProRoundAction("✆", b.base) { onCall(n) } }
            }
        }
    }
}

// ─────────────────────────────── reports ───────────────────────────────

private fun LazyListScope.reportList(reports: List<JSONObject>?, tab: Int, onTab: (Int) -> Unit, b: Branding) {
    item { Spacer(Modifier.height(14.dp)) }
    if (reports == null) { item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) } }; return }
    item {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reports.forEachIndexed { i, r -> ProFilterChip(r.optString("label"), null, null, tab == i) { onTab(i) } }
        }
    }
    val r = reports.getOrNull(tab) ?: return
    val k = r.optJSONObject("kpis") ?: JSONObject()
    item {
        ProCard(r.optString("label") + " report", r.optString("range")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Leads", k.optInt("leads"), Modifier.weight(1f)); Metric("Calls", k.optInt("calls"), Modifier.weight(1f)); Metric("Visits", k.optInt("visits"), Modifier.weight(1f)); Metric("Bookings", k.optInt("bookings"), Modifier.weight(1f), KitGood)
            }
            Text("Booking value ${rupees(k.optDouble("booking_value"))}", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
    val rows = r.objects("rows")
    item { Text("STAFF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 2.dp)) }
    if (rows.isEmpty()) item { Text("No staff data.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
    items(rows.sortedByDescending { it.optInt("achievement") }) { s ->
        val pct = s.optInt("achievement")
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Initials(s.str("name") ?: "?", 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(s.str("name") ?: "—", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp)
                Text("${s.optInt("leads")} leads · ${s.optInt("calls")} calls · ${s.optInt("visits")} visits · ${s.optInt("bookings")} booked", color = KitMuted, fontSize = 12.sp)
            }
            Pill("$pct%", tone(pct).copy(alpha = .14f), tone(pct))
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
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
        val opts = Periods.filter { it != "Customize" }
        ChoiceSheet("Open on period", opts.mapIndexed { i, p -> Choice(i, PeriodLabel.getValue(p)) }, setOf(opts.indexOf(defaultPeriod)), multi = false, onDismiss = { pickPeriod = false }) { ids -> ids.firstOrNull()?.let { defaultPeriod = opts[it] } }
    }
}
