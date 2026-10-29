package com.example.gavanhomes.ui.hr

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import com.example.gavanhomes.ui.common.CardAction
import com.example.gavanhomes.ui.common.CardStat
import com.example.gavanhomes.ui.common.EntityCard
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.common.FilterBar
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.SearchBox
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.professionals.ProBanners
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.professionals.ProBlue
import com.example.gavanhomes.ui.professionals.ProCard
import com.example.gavanhomes.ui.professionals.ProCounter
import com.example.gavanhomes.ui.professionals.ProFact
import com.example.gavanhomes.ui.professionals.ProFilterChip
import com.example.gavanhomes.ui.professionals.ProAmber
import com.example.gavanhomes.ui.professionals.ProRoundAction
import com.example.gavanhomes.ui.professionals.ProSheetAction
import com.example.gavanhomes.ui.professionals.ProTile
import com.example.gavanhomes.ui.professionals.headline
import com.example.gavanhomes.ui.professionals.objects
import com.example.gavanhomes.ui.professionals.prettyDate
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private class Sec(val label: String, val glyph: String, val blurb: String, val group: String, val slug: String? = null)

private val Secs = listOf(
    Sec("Dashboard", "⌂", "Overview and approvals", "Overview"),
    Sec("Employees", "◎", "The people directory", "People", "employees"),
    Sec("Recruitment", "✚", "Candidates and stages", "People", "recruitment"),
    Sec("Attendance", "✓", "This month, per person", "People"),
    Sec("Leave", "↗", "Applications and approvals", "People"),
    Sec("Payroll", "▦", "Salary slips", "People"),
    Sec("Performance", "✦", "Reviews", "People", "performance"),
    Sec("Documents", "◈", "Employee files", "People", "documents"),
    Sec("Exit & F&F", "←", "Leavers and settlements", "People", "exit"),
    Sec("Assets", "▦", "Company assets", "Company", "assets"),
    Sec("Maintenance", "✎", "Service schedule", "Company", "maintenance"),
    Sec("Vehicles", "↗", "Fleet and documents", "Company", "vehicles"),
    Sec("Suppliers", "◉", "Vendors", "Company"),
    Sec("Visitors", "◎", "Check-in log", "Company", "visitors"),
    Sec("Requests", "✉", "Employee requests", "Company", "requests"),
)
private const val DASH = 0
private const val ATTENDANCE = 3
private const val LEAVE = 4
private const val PAYROLL = 5
private const val SUPPLIERS = 12

private val Rupees: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
private fun money(v: Double) = "₹" + Rupees.format(v)
private val MonthName = DateTimeFormatter.ofPattern("MMM yyyy")
private fun monthLabel(m: String) = runCatching { YearMonth.parse(m).format(MonthName) }.getOrDefault(m)
private fun shift(m: String, by: Int) = runCatching { YearMonth.parse(m).plusMonths(by.toLong()).toString() }.getOrDefault(m)

private class Row_(val json: JSONObject, val schema: HrSchema) {
    val id = json.optInt("id")
    val title = schema.titleOf(json)
    val sub = schema.subOf(json)
    val badge = json.str(schema.badge)
    val reference = json.str("reference") ?: json.str("employee_code")
    val phone = json.str("phone")
    val email = json.str("email")
    val banner = ProBanners[(id % ProBanners.size + ProBanners.size) % ProBanners.size]
    fun matches(q: String) = q.isEmpty() || json.keys().asSequence().any { k -> json.str(k)?.lowercase()?.contains(q) == true }
}

/**
 * HR & Admin, all native. The sections (dashboard, employees, recruitment, attendance, leave, payroll, performance,
 * documents, exit, assets, maintenance, vehicles, suppliers, visitors, requests) live in a sidebar that ☰ slides open.
 * The standard modules are driven by the form / list definition the server sends for each (GET /hr/{module}).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HrScreen(
    api: ApiClient, b: Branding, section: Int, onSection: (Int) -> Unit, onBack: () -> Unit,
    onOpen: (String, Int) -> Unit, onAdd: (String) -> Unit, onEdit: (String, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)

    val data = remember { mutableStateMapOf<Int, JSONObject>() }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var attMonth by remember { mutableStateOf(YearMonth.now().toString()) }
    var payMonth by remember { mutableStateOf(YearMonth.now().toString()) }
    val loadedAt = remember { mutableStateMapOf<Int, String>() }
    var refreshing by remember { mutableStateOf(false) }                      // pull-down refresh in progress
    LaunchedEffect(loadedAt[section]) { refreshing = false }                         // fresh data arrived
    LaunchedEffect(refreshing) { if (refreshing) { kotlinx.coroutines.delay(10_000); refreshing = false } }   // never spin forever

    var query by remember(section) { mutableStateOf("") }
    var sel by remember(section) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }   // chosen filters: group → values
    var sort by remember(section) { mutableIntStateOf(0) }
    var sortOpen by remember { mutableStateOf(false) }
    var compact by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Row_?>(null) }
    var confirmDelete by remember { mutableStateOf<Row_?>(null) }
    var remarksFor by remember { mutableStateOf<Pair<Row_, String>?>(null) }   // reject a request with remarks
    var leaveApply by remember { mutableStateOf(false) }
    var leaveAction by remember { mutableStateOf<Triple<Int, String, String>?>(null) }  // id, "approve"|"reject", name
    var payRow by remember { mutableStateOf<JSONObject?>(null) }
    var supplier by remember { mutableStateOf<JSONObject?>(null) }

    val sec = Secs[section]
    val token = "$reload|$attMonth|$payMonth"

    LaunchedEffect(section, token) {
        if (loadedAt[section] == token && data[section] != null) return@LaunchedEffect
        error = null
        try {
            val path = when {
                section == DASH -> "/hr/dashboard"
                sec.slug != null -> "/hr/${sec.slug}"
                section == ATTENDANCE -> "/hr/attendance?month=$attMonth"
                section == LEAVE -> "/hr/leaves"
                section == PAYROLL -> "/hr/payroll?month=$payMonth"
                else -> "/hr/suppliers"
            }
            val r = withContext(Dispatchers.IO) { api.get(path) }
            r.optJSONObject("schema")?.let { HrSchemas.cache[sec.slug.orEmpty()] = parseSchema(it) }
            data[section] = r
            loadedAt[section] = token
        } catch (e: Exception) { error = e.message ?: "Could not load" }
    }

    fun refresh() { loadedAt.clear(); data.remove(section); reload++ }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_LONG).show()

    /** POST an action and refresh the list. */
    fun post(path: String, body: JSONObject = JSONObject(), ok: String? = null) {
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.send("POST", path, body) }
                toast(ok ?: r.optString("message", "Done")); refresh()
            } catch (e: Exception) { toast(e.message ?: "Something went wrong") }
        }
    }

    val counts = Secs.indices.associateWith { i ->
        data[i]?.let { d -> if (Secs[i].slug != null || i == SUPPLIERS || i == LEAVE) d.optJSONArray("data")?.length() else null }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White, windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(b.deep, b.base), start = Offset.Zero, end = Offset(700f, 700f)))
                            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (b.logo != null) { com.example.gavanhomes.ui.common.BrandMark(b, 52.dp); Spacer(Modifier.size(14.dp)) }
                        Column {
                            Text("HR & Admin", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("People, payroll and company", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        }
                        }
                    }
                    var lastGroup = ""
                    Secs.forEachIndexed { i, s ->
                        if (s.group != lastGroup) {
                            lastGroup = s.group
                            Text(s.group.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp))
                        }
                        val on = i == section
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) b.base.copy(alpha = .16f) else Color.Transparent)
                                .clickable { onSection(i); scope.launch { drawer.close() } }.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) b.base else Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) {
                                Text(s.glyph, color = if (on) Color.White else KitInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(s.label, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = KitInk, fontSize = 14.sp)
                                Text(s.blurb, color = KitMuted, fontSize = 11.sp)
                            }
                            counts[i]?.let { n ->
                                Text(n.toString(), color = if (on) b.dark else KitMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Color.White else Color(0xFFF3F2F0)).padding(horizontal = 9.dp, vertical = 3.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(KitPage)) {
            val toggle: @Composable () -> Unit = { HeroButton("☰") { scope.launch { drawer.open() } } }
            val d = data[section]

            if (d == null) {
                HeroBar(b, sec.label, onBack, actions = { toggle() })
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (error == null) CircularProgressIndicator(color = b.base)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { refresh() }) { Text("Retry") }
                    }
                }
                return@Column
            }

            val schema = sec.slug?.let { HrSchemas.cache[it] }
            val rows = if (schema != null) d.objects("data").map { Row_(it, schema) } else emptyList()
            val can = d.optJSONObject("can")

            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; loadedAt.clear(); reload++ }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    HeroBar(
                        b, sec.label, onBack,
                        actions = {
                            toggle()
                            if (sec.slug != null && can?.optBoolean("create") == true) { Spacer(Modifier.size(8.dp)); HeroButton("+") { onAdd(sec.slug) } }
                            if (section == LEAVE && can?.optBoolean("apply") == true) { Spacer(Modifier.size(8.dp)); HeroButton("+") { leaveApply = true } }
                        },
                    ) { Counters(section, d, rows) }
                }

                when {
                    section == DASH -> dashboard(d, onSection)
                    schema != null -> {
                        val badges = rows.mapNotNull { it.badge }.distinct()
                        val passes = { r: Row_, m: Map<String, Set<String>> -> m["status"].orEmpty().let { s -> s.isEmpty() || r.badge in s } }
                        val shown = rows.filter { passes(it, sel) && it.matches(query.trim().lowercase()) }.let { l ->
                            when (sort) { 1 -> l.sortedBy { it.title.lowercase() }; else -> l.sortedByDescending { it.id } }
                        }
                        item { Spacer(Modifier.height(14.dp)) }
                        item {
                            FilterBar(
                                query, { query = it }, "Search ${sec.label.lowercase()}…",
                                groups = if (badges.isEmpty()) emptyList() else listOf(FilterGroup("status", schema.field(schema.badge)?.label ?: "Status", badges.map { st ->
                                    FilterOption(st, schema.field(schema.badge)?.let { f -> schema.display(f, st) } ?: hrStatusLabel(st), rows.count { r -> r.badge == st }, hrStatusColor(st)) })),
                                selected = sel, onApply = { sel = it },
                                countWith = { m -> rows.count { r -> passes(r, m) && r.matches(query.trim().lowercase()) } },
                                sortOptions = listOf("Newest", "A–Z"), sortIndex = sort, onSort = { sort = it },
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${shown.size} ${if (shown.size == 1) "record" else "records"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                com.example.gavanhomes.ui.common.ViewToggle(compact) { compact = it }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                        if (shown.isEmpty()) item {
                            Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (rows.isEmpty()) "Nothing here yet" else "No records match", fontWeight = FontWeight.Bold, color = KitInk)
                                Text(if (rows.isEmpty() && can?.optBoolean("create") == true) "Tap + to add the first one." else "Try another search or clear the filter.", color = KitMuted, fontSize = 13.sp)
                            }
                        }
                        items(shown, key = { it.id }) { r ->
                            if (compact) RowCompact(r, schema, { onOpen(schema.slug, r.id) }, { sheet = r })
                            else RowCard(
                                r, schema, b, { onOpen(schema.slug, r.id) }, { sheet = r },
                                { r.phone?.let { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) } },
                                { r.email?.let { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$it"))) } },
                            )
                        }
                    }
                    section == ATTENDANCE -> attendance(d, attMonth, { attMonth = shift(attMonth, it) })
                    section == LEAVE -> leaves(d, b, { id, a, who -> leaveAction = Triple(id, a, who) })
                    section == PAYROLL -> payroll(d, payMonth, { payMonth = shift(payMonth, it) }) { payRow = it }
                    else -> suppliers(d, b) { supplier = it }
                }
            }
            }
        }
    }

    // ── ⋯ actions on a record ──
    sheet?.let { r ->
        val slug = r.schema.slug
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(r.title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text(r.sub.ifEmpty { r.reference.orEmpty() }, color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                ProSheetAction("◉", "Open details") { sheet = null; onOpen(slug, r.id) }
                ProSheetAction("✎", "Edit") { sheet = null; onEdit(slug, r.id) }
                if (slug == "requests" && r.badge == "pending") {
                    ProSheetAction("✓", "Approve") { sheet = null; post("/hr/requests/${r.id}/approve", JSONObject(), "Request approved") }
                    ProSheetAction("✕", "Reject…", danger = true) { remarksFor = r to "reject"; sheet = null }
                }
                if (slug == "visitors" && r.badge == "expected") ProSheetAction("✓", "Check in") { sheet = null; post("/hr/visitors/${r.id}/check-in") }
                if (slug == "visitors" && r.badge == "checked_in") ProSheetAction("←", "Check out") { sheet = null; post("/hr/visitors/${r.id}/check-out") }
                r.phone?.let { n -> ProSheetAction("✆", "Call $n") { sheet = null; context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) } }
                r.email?.let { m -> ProSheetAction("✉", m) { sheet = null; context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$m"))) } }
                ProSheetAction("✕", "Delete", danger = true) { confirmDelete = r; sheet = null }
            }
        }
    }
    confirmDelete?.let { r ->
        SwalConfirm(
            title = "Delete ${r.title}?", text = "This ${r.schema.singular.lowercase()} can't be restored.", confirmText = "Yes, delete", onCancel = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                scope.launch {
                    try { withContext(Dispatchers.IO) { api.delete("/hr/${r.schema.slug}/${r.id}") }; toast("${r.schema.singular} deleted"); refresh() }
                    catch (e: Exception) { toast(e.message ?: "Could not delete") }
                }
            },
        )
    }
    remarksFor?.let { (r, _) -> RemarksSheet("Reject request", "Remarks for the employee (optional)", "Reject", onDismiss = { remarksFor = null }) { text -> remarksFor = null; post("/hr/requests/${r.id}/reject", JSONObject().put("remarks", text), "Request rejected") } }

    // ── leave ──
    if (leaveApply) LeaveSheet(api, onDismiss = { leaveApply = false }) { leaveApply = false; refresh() }
    leaveAction?.let { (id, action, who) ->
        if (action == "approve") SwalConfirm(
            title = "Approve this leave?", text = "$who is notified and the days are marked as leave.", confirmText = "Yes, approve", confirmColor = KitGood,
            onCancel = { leaveAction = null }, onConfirm = { leaveAction = null; post("/hr/leaves/$id/approve", JSONObject(), "Leave approved") },
        ) else RemarksSheet("Reject leave", "Reason for $who (optional)", "Reject", onDismiss = { leaveAction = null }) { text -> leaveAction = null; post("/hr/leaves/$id/reject", JSONObject().put("remarks", text), "Leave rejected") }
    }

    // ── payroll ──
    payRow?.let { r ->
        val can = data[PAYROLL]?.optJSONObject("can")?.optBoolean("edit") == true
        PayrollSheet(r, payMonth, can, onDismiss = { payRow = null },
            onSlip = { action -> payRow = null; post("/hr/payroll/${r.optInt("user_id")}/$action", JSONObject().put("month", payMonth)) })
    }

    // ── supplier ──
    supplier?.let { s -> InfoSheet(s, onDismiss = { supplier = null }) }
}

// ─────────────────────────────── hero counters ───────────────────────────────

@Composable
private fun Counters(section: Int, d: JSONObject, rows: List<Row_>) {
    val dash = d.optJSONObject("data") ?: d
    fun obj(k: String) = dash.optJSONObject(k)
    fun total(o: JSONObject?) = o?.keys()?.asSequence()?.sumOf { o.optInt(it) } ?: 0
    val items: List<Pair<String, String>> = when {
        section == DASH -> listOf(total(obj("employees")).toString() to "Employees", (obj("employees")?.optInt("active") ?: 0).toString() to "Active",
            dash.optInt("approval_count").toString() to "To approve", (obj("payroll")?.optInt("generated") ?: 0).toString() to "Slips made")
        rows.isNotEmpty() || d.has("schema") -> {
            val top = rows.mapNotNull { it.badge }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
            listOf(rows.size.toString() to "Total") + top.map { it.value.toString() to hrStatusLabel(it.key) }
        }
        section == ATTENDANCE -> { val a = d.objects("data"); listOf(a.size.toString() to "People", a.count { it.optJSONObject("today")?.has("check_in_at") == true && !it.optJSONObject("today")!!.isNull("check_in_at") }.toString() to "In today",
            a.sumOf { it.optInt("late_days") }.toString() to "Late days", a.sumOf { it.optInt("absent_days") }.toString() to "Absent days") }
        section == LEAVE -> { val a = d.objects("data"); listOf(a.size.toString() to "Total", a.count { it.optString("status") == "pending" }.toString() to "Pending", a.count { it.optString("status") == "approved" }.toString() to "Approved", a.count { it.optString("status") == "rejected" }.toString() to "Rejected") }
        section == PAYROLL -> { val a = d.objects("data"); listOf(a.size.toString() to "People", a.count { !it.isNull("slip") }.toString() to "Slips", a.count { it.isNull("slip") }.toString() to "Pending", money(a.sumOf { it.optDouble("net") }) to "Net total") }
        else -> listOf((d.optInt("total")).toString() to "Suppliers")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items.forEachIndexed { i, (v, l) -> ProCounter(v, l, Modifier.weight(if (i == 0) 1.2f else 1f), big = i == 0) } }
}

// ─────────────────────────────── dashboard ───────────────────────────────

private fun LazyListScope.dashboard(root: JSONObject, onSection: (Int) -> Unit) {
    val d = root.optJSONObject("data") ?: JSONObject()
    item { Spacer(Modifier.height(14.dp)) }

    val approvals = d.objects("approvals")
    item {
        ProCard("Needs attention", if (d.optInt("approval_count") == 0) null else "${d.optInt("approval_count")} waiting for a decision") {
            if (approvals.isEmpty()) Text("Nothing is waiting for approval.", color = KitMuted, fontSize = 14.sp)
            approvals.forEach { a ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSection(if (a.optString("tab") == "leave") LEAVE else 14) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.optString("title"), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(a.str("who"), prettyDate(a.str("date"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                    }
                    Pill(a.optString("type"), ProAmber.copy(alpha = .14f), ProAmber)
                }
            }
        }
    }

    val dept = d.optJSONObject("departments")
    item {
        ProCard("Employees by department") {
            val keys = dept?.keys()?.asSequence()?.toList().orEmpty()
            if (keys.isEmpty()) Text("No employees yet.", color = KitMuted, fontSize = 14.sp)
            val max = (keys.maxOfOrNull { dept!!.optInt(it) } ?: 1).coerceAtLeast(1)
            keys.forEachIndexed { i, k ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSection(1) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row { Text(k, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(dept!!.optInt(k).toString(), fontWeight = FontWeight.Bold, color = KitInk) }
                    ProBar(dept!!.optInt(k).toFloat() / max, listOf(ProBlue, KitGood, ProAmber, Color(0xFF8B5CF6))[i % 4])
                }
            }
        }
    }

    listOf("Recruitment pipeline" to "recruitment", "Performance reviews" to "performance", "Documents" to "documents", "Exit & F&F" to "exits").forEach { (title, key) ->
        val m = d.optJSONObject(key)
        val keys = m?.keys()?.asSequence()?.toList().orEmpty()
        if (keys.isNotEmpty()) item {
            ProCard(title) {
                val total = keys.sumOf { m!!.optInt(it) }.coerceAtLeast(1)
                keys.forEach { k ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row { Text(hrStatusLabel(k), color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(m!!.optInt(k).toString(), fontWeight = FontWeight.Bold, color = KitInk) }
                        ProBar(m!!.optInt(k).toFloat() / total, hrStatusColor(k))
                    }
                }
            }
        }
    }

    val pay = d.optJSONObject("payroll")
    item {
        ProCard("Payroll this month") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProTile((pay?.optInt("generated") ?: 0).toString(), "Slips generated", Modifier.weight(1f), KitGood)
                ProTile((pay?.optInt("not_generated") ?: 0).toString(), "Not generated", Modifier.weight(1f), ProAmber)
            }
        }
    }

    val hires = d.objects("recent_hires")
    item {
        ProCard("Recent hires") {
            if (hires.isEmpty()) Text("No hires recorded.", color = KitMuted, fontSize = 14.sp)
            hires.forEach { h ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Initials(h.str("name") ?: "?", 38.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(h.str("name") ?: "—", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp)
                        Text(listOfNotNull(h.str("designation"), h.str("department")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                    }
                    Text(prettyDate(h.str("hire_date")) ?: "", color = KitMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

// ─────────────────────────────── standard record cards ───────────────────────────────

@Composable
private fun RowCard(r: Row_, schema: HrSchema, b: Branding, onClick: () -> Unit, onMore: () -> Unit, onCall: () -> Unit, onMail: () -> Unit) {
    val badgeField = schema.field(schema.badge)
    EntityCard(
        title = r.title, subtitle = r.sub.ifEmpty { null }, accent = r.banner, onClick = onClick, onMore = onMore,
        chips = {
            r.badge?.let { Pill(badgeField?.let { f -> schema.display(f, it) } ?: hrStatusLabel(it), hrStatusColor(it).copy(alpha = .14f), hrStatusColor(it)) }
            Pill(schema.singular, ProBlue.copy(alpha = .14f), ProBlue)
        },
        footerLeft = { Text(r.reference ?: schema.singular, fontSize = 13.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        footerRight = {
            if (r.phone != null) CardAction("✆", b.base, onCall)
            if (r.email != null) CardAction("✉", b.base, onMail)
        },
    )
}

@Composable
private fun RowCompact(r: Row_, schema: HrSchema, onClick: () -> Unit, onMore: () -> Unit) {
    val badgeField = schema.field(schema.badge)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Initials(r.title, 46.dp, hrStatusColor(r.badge))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(r.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.sub.ifEmpty { r.reference.orEmpty() }, fontSize = 12.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        r.badge?.let { Pill(badgeField?.let { f -> schema.display(f, it) } ?: hrStatusLabel(it), hrStatusColor(it).copy(alpha = .14f), hrStatusColor(it)) }
        Text("⋯", fontSize = 22.sp, color = KitMuted, modifier = Modifier.clip(CircleShape).clickable { onMore() }.padding(horizontal = 10.dp))
    }
}

// ─────────────────────────────── attendance ───────────────────────────────

@Composable
private fun MonthBar(month: String, onShift: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text("‹", fontSize = 22.sp, color = KitInk, modifier = Modifier.clip(CircleShape).background(Color.White).clickable { onShift(-1) }.padding(horizontal = 16.dp, vertical = 6.dp))
        Text(monthLabel(month), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 140.dp))
        Text("›", fontSize = 22.sp, color = KitInk, modifier = Modifier.clip(CircleShape).background(Color.White).clickable { onShift(1) }.padding(horizontal = 16.dp, vertical = 6.dp))
    }
}

private fun LazyListScope.attendance(d: JSONObject, month: String, onShift: (Int) -> Unit) {
    val rows = d.objects("data")
    item { MonthBar(month, onShift) }
    if (rows.isEmpty()) item { Text("No one is tracked for attendance.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp)) }
    items(rows, key = { it.optInt("user_id") }) { r ->
        val today = r.optJSONObject("today")
        val inToday = today != null && !today.isNull("check_in_at")
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(r.str("name") ?: "?", 44.dp, if (inToday) KitGood else null)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(r.str("name") ?: "—", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp)
                    Text(if (inToday) "In today" + if (today!!.optBoolean("wfh")) " · WFH" else "" else "No check-in today", color = KitMuted, fontSize = 12.sp)
                }
                today?.str("status")?.let { Pill(hrStatusLabel(it), hrStatusColor(it).copy(alpha = .14f), hrStatusColor(it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Present", r.optInt("present_days").toString(), KitGood, Modifier.weight(1f))
                Metric("Late", r.optInt("late_days").toString(), ProAmber, Modifier.weight(1f))
                Metric("Absent", r.optInt("absent_days").toString(), KitDanger, Modifier.weight(1f))
                Metric("Overtime", "${r.optInt("overtime_minutes") / 60}h", ProBlue, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .1f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = tint)
        Text(label, fontSize = 10.sp, color = KitMuted, maxLines = 1)
    }
}

// ─────────────────────────────── leave ───────────────────────────────

private fun LazyListScope.leaves(d: JSONObject, b: Branding, onAction: (Int, String, String) -> Unit) {
    val rows = d.objects("data")
    val review = d.optJSONObject("can")?.optBoolean("review") == true
    item { Spacer(Modifier.height(14.dp)) }
    if (rows.isEmpty()) item {
        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No leave applications", fontWeight = FontWeight.Bold, color = KitInk)
            Text("Applications show up here.", color = KitMuted, fontSize = 13.sp)
        }
    }
    items(rows, key = { it.optInt("id") }) { l ->
        val status = l.optString("status")
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(l.str("name") ?: "?", 42.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(l.str("name") ?: "—", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp)
                    Text("${hrStatusLabel(l.optString("leave_type"))} leave", color = KitMuted, fontSize = 12.sp)
                }
                Pill(hrStatusLabel(status), hrStatusColor(status).copy(alpha = .14f), hrStatusColor(status))
            }
            Text("${prettyDate(l.str("start_date")) ?: "—"}  →  ${prettyDate(l.str("end_date")) ?: "—"}", color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            l.str("reason")?.let { Text(it, color = KitMuted, fontSize = 13.sp) }
            l.str("remarks")?.let { Text("Remarks: $it", color = KitMuted, fontSize = 12.sp) }
            if (status == "pending" && review && !l.optBoolean("mine")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Reject", color = KitDanger, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).clickable { onAction(l.optInt("id"), "reject", l.optString("name")) }.padding(vertical = 12.dp))
                Text("Approve", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(KitGood).clickable { onAction(l.optInt("id"), "approve", l.optString("name")) }.padding(vertical = 12.dp))
            }
        }
    }
}

// ─────────────────────────────── payroll ───────────────────────────────

private fun LazyListScope.payroll(d: JSONObject, month: String, onShift: (Int) -> Unit, onOpen: (JSONObject) -> Unit) {
    val rows = d.objects("data")
    item { MonthBar(month, onShift) }
    if (rows.isEmpty()) item { Text("No one is on payroll for this month.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp)) }
    items(rows, key = { it.optInt("user_id") }) { r ->
        val slip = r.optJSONObject("slip")
        val tone = when { slip == null -> ProAmber; !slip.isNull("emailed_at") -> ProBlue; else -> KitGood }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).clickable { onOpen(r) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Initials(r.str("name") ?: "?", 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.str("name") ?: "—", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp)
                Text("Gross ${money(r.optDouble("gross"))}", color = KitMuted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(money(r.optDouble("net")), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp)
                Pill(if (slip == null) "No slip" else if (!slip.isNull("emailed_at")) "Emailed" else "Generated", tone.copy(alpha = .14f), tone)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayrollSheet(r: JSONObject, month: String, canEdit: Boolean, onDismiss: () -> Unit, onSlip: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(r.str("name") ?: "—", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
            Text(monthLabel(month), color = KitMuted, fontSize = 13.sp)
            if (!r.optBoolean("has_structure")) Text("No salary structure is set up for this person.", color = ProAmber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            listOf("Basic" to "basic", "Allowances" to "allowances", "Overtime" to "overtime_pay", "Incentives" to "incentives", "Gross" to "gross").forEach { (l, k) -> ProFact(l, money(r.optDouble(k))) }
            Text("DEDUCTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
            listOf("Attendance" to "attendance_deductions", "PF" to "pf", "ESIC" to "esic", "Professional tax" to "professional_tax", "TDS" to "tds").forEach { (l, k) -> ProFact(l, money(r.optDouble(k))) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitGood.copy(alpha = .12f)).padding(14.dp)) {
                Text("Net pay", fontWeight = FontWeight.SemiBold, color = KitInk, modifier = Modifier.weight(1f)); Text(money(r.optDouble("net")), fontWeight = FontWeight.ExtraBold, color = KitGood)
            }
            if (canEdit) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Generate slip", color = KitInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1EFEB)).clickable { onSlip("generate") }.padding(vertical = 14.dp))
                Text("Email slip", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F172A)).clickable { onSlip("send") }.padding(vertical = 14.dp))
            }
        }
    }
}

// ─────────────────────────────── suppliers ───────────────────────────────

private fun LazyListScope.suppliers(d: JSONObject, b: Branding, onOpen: (JSONObject) -> Unit) {
    val rows = d.objects("data")
    item { Spacer(Modifier.height(14.dp)) }
    if (rows.isEmpty()) item { Text("No suppliers yet.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp)) }
    items(rows, key = { it.optInt("id") }) { s ->
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { onOpen(s) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Initials(s.str("name") ?: s.str("company_name") ?: "?", 46.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(s.str("name") ?: s.str("company_name") ?: "Supplier", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(s.str("company_name"), s.str("city")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            s.str("business_type")?.let { Pill(it.headline(), b.base.copy(alpha = .16f), b.dark) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(s: JSONObject, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.str("name") ?: s.str("company_name") ?: "Supplier", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
            s.keys().asSequence().filter { it != "id" && it != "name" }.forEach { k -> ProFact(k.headline(), s.str(k)) }
        }
    }
}

// ─────────────────────────────── sheets ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemarksSheet(title: String, hint: String, confirm: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Input(hint, text, { text = it }, singleLine = false)
            Text(confirm, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitDanger).clickable { onSubmit(text.trim()) }.padding(vertical = 15.dp))
        }
    }
}

/** Apply for leave (not available to super admins): type, dates, reason — POST /hr/leaves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveSheet(api: ApiClient, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val types = listOf("casual", "sick", "paid", "unpaid")
    var type by remember { mutableStateOf("casual") }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pickType by remember { mutableStateOf(false) }
    val shown = DateTimeFormatter.ofPattern("d MMM yyyy")

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Apply for leave", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            PickerField("Leave type", hrStatusLabel(type)) { pickType = true }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { PickerField("From", start.format(shown)) { DatePickerDialog(context, { _, y, m, d -> start = LocalDate.of(y, m + 1, d); if (end.isBefore(start)) end = start }, start.year, start.monthValue - 1, start.dayOfMonth).show() } }
                Box(Modifier.weight(1f)) { PickerField("To", end.format(shown)) { DatePickerDialog(context, { _, y, m, d -> end = LocalDate.of(y, m + 1, d) }, end.year, end.monthValue - 1, end.dayOfMonth).show() } }
            }
            Input("Reason *", reason, { reason = it; error = null }, error, singleLine = false)
            Text(if (busy) "Submitting…" else "Submit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0F172A)).clickable(enabled = !busy) {
                    if (reason.isBlank()) { error = "Enter a reason"; return@clickable }
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { api.send("POST", "/hr/leaves", JSONObject().put("leave_type", type).put("start_date", start.toString()).put("end_date", end.toString()).put("reason", reason.trim())) }
                            Toast.makeText(context, "Leave request submitted", Toast.LENGTH_SHORT).show(); onDone()
                        } catch (e: ApiException) { error = e.errors.values.firstOrNull() ?: e.message }
                        catch (e: Exception) { error = e.message }
                        busy = false
                    }
                }.padding(vertical = 15.dp))
        }
    }
    if (pickType) com.example.gavanhomes.ui.projects.form.ChoiceSheet(
        "Leave type", types.mapIndexed { i, t -> com.example.gavanhomes.ui.projects.form.Choice(i, hrStatusLabel(t)) }, setOf(types.indexOf(type)), multi = false, onDismiss = { pickType = false },
    ) { ids -> ids.firstOrNull()?.let { type = types[it] } }
}
