package com.example.gavanhomes.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.User
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.SegTabs
import com.example.gavanhomes.ui.shell.Module
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar

private val Ink = Color(0xFF1F2A44)
private val Muted = Color(0xFF8A8F9C)
private val PageBg = Color(0xFFF3F2F0)
private val Danger = Color(0xFFD03B3B)

// ───────────────────────────── JSON / format helpers ─────────────────────────────

private fun JSONObject.list(key: String): List<JSONObject> =
    optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()

private fun JSONObject.has(vararg keys: String) = keys.any { has(it) && !isNull(it) }

private fun num(v: Number): String = java.text.NumberFormat.getIntegerInstance().format(v.toLong())

/** ₹ with Cr / L suffix — same rule as the website's dashboard. */
private fun money(v: Double): String = "₹" + when {
    kotlin.math.abs(v) >= 10_000_000 -> String.format("%.2f Cr", v / 10_000_000)
    kotlin.math.abs(v) >= 100_000 -> String.format("%.2f L", v / 100_000)
    else -> num(v)
}

private fun JSONObject.moneyOf(key: String) = money(optDouble(key, 0.0))
private fun day(s: String?): String? = s?.takeIf { it.length >= 10 }?.take(10)
private fun JSONObject.nestedName(key: String): String? = optJSONObject(key)?.str("name")

private class Row3(val title: String, val subtitle: String = "", val badge: String? = null)

private val Green = Color(0xFF0E8A50)
private val Blue = Color(0xFF2F6FE4)
private val Amber = Color(0xFFC47A1A)

/** Colour that carries meaning: done = green, active = blue, waiting = amber, trouble = red; anything else = brand. */
private fun statusColor(name: String?, fallback: Color): Color {
    val n = name?.lowercase() ?: return fallback
    return when {
        listOf("complete", "done", "approved", "active", "delivered", "visited", "won", "sold", "paid", "verified").any { n.contains(it) } -> Green
        listOf("overdue", "late", "cancel", "disqualif", "reject", "cold", "out", "failed", "lost", "unpaid").any { n == it || n.contains(it) } -> Danger
        listOf("pending", "call back", "warm", "due", "low", "booked", "draft").any { n.contains(it) } -> Amber
        listOf("progress", "working", "new", "open", "created", "hot", "coming").any { n.contains(it) } -> Blue
        else -> fallback
    }
}

private class Kpi(val label: String, val value: String, val sub: String, val glyph: String, val accent: Color, val progress: Float? = null, val path: String? = null)

/**
 * Native dashboard — GET /api/mobile/admin/dashboard. The `full` object is the same data the website's
 * dashboard shows (see Admin\DashboardController::payload), so this screen has the same sections and the same
 * tabs (Overview · Work · Sales · Stock & Buying · Finance · People). Only what the user may see is present.
 */
@Composable
fun DashboardScreen(
    api: ApiClient,
    user: User,
    branding: Branding,
    modules: List<Module>,
    onOpenModule: (Module) -> Unit,
    onOpenPath: (String) -> Unit,
    onLogout: () -> Unit,
    cachedJson: String? = null,
    onCache: (String) -> Unit = {},
    onUnauthorized: () -> Unit = {},
) {
    // the last dashboard is painted immediately; the fresh one replaces it when it arrives
    var data by remember { mutableStateOf<JSONObject?>(cachedJson?.let { runCatching { JSONObject(it) }.getOrNull() }) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        error = null
        try {
            val fresh = withContext(Dispatchers.IO) { api.dashboard() }
            data = fresh
            onCache(fresh.toString())
        } catch (e: com.example.gavanhomes.data.ApiException) {
            if (e.code == 401) onUnauthorized() else if (data == null) error = e.message ?: "Could not load the dashboard"
        } catch (e: Exception) {
            if (data == null) error = e.message ?: "Could not load the dashboard"   // offline: keep showing the saved copy
        }
    }

    Box(Modifier.fillMaxSize().background(PageBg), contentAlignment = Alignment.TopCenter) {
        val d = data
        when {
            d == null && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = branding.base) }
            d == null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(error ?: "", color = Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { reload++ }) { Text("Retry") }
            }
            else -> Content(d, user, branding, modules, onOpenModule, onOpenPath, onLogout, onRefresh = { reload++ })
        }
    }
}

private val TabNames = listOf("Overview", "Work", "Sales", "Stock & Buying", "Finance", "People")

@Composable
private fun Content(
    d: JSONObject, user: User, b: Branding, modules: List<Module>,
    onOpenModule: (Module) -> Unit, onOpenPath: (String) -> Unit, onLogout: () -> Unit, onRefresh: () -> Unit,
) {
    val w = d.optJSONObject("full") ?: JSONObject()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when { hour < 12 -> "Good morning,"; hour < 17 -> "Good afternoon,"; else -> "Good evening," }
    val taskStats = w.optJSONObject("taskStats")
    val leadStats = w.optJSONObject("leadStats")
    val (headLabel, headValue) = when {
        leadStats != null -> "total leads" to leadStats.optInt("total")
        w.has("projectCount") -> "total projects" to w.optInt("projectCount")
        else -> "notifications" to d.optInt("unread_notifications")
    }
    var tab by remember { mutableIntStateOf(0) }

    LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        // ── hero ──
        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.linearGradient(listOf(b.deep, b.base), start = Offset.Zero, end = Offset(900f, 1300f))),
            ) {
                Box(Modifier.size(220.dp).offset(x = 230.dp, y = (-60).dp).clip(CircleShape).background(Color.White.copy(alpha = .08f)))
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 26.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
                            Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(greeting, color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                            Text(user.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        RoundGlyph("⟳", onRefresh)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Log out", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .2f)).clickable { onLogout() }.padding(horizontal = 14.dp, vertical = 11.dp),
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(num(headValue), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                headLabel + (leadStats?.let { " · +${it.optInt("new_month")} this month" } ?: ""),
                                color = Color.White.copy(alpha = .85f), fontSize = 13.sp,
                            )
                        }
                        if (taskStats != null) Ring(taskStats.optInt("completion"))
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (w.has("projectCount")) HeroChip("${w.optInt("projectCount")} projects")
                        if ((taskStats?.optInt("overdue") ?: 0) > 0) HeroChip("${taskStats!!.optInt("overdue")} overdue", Danger.copy(alpha = .55f))
                        if (leadStats != null) HeroChip("${leadStats.optInt("hot")} hot leads")
                    }
                }
            }
        }

        item { SegTabs(TabNames, tab) { tab = it } }

        when (tab) {
            0 -> overview(w, b, onOpenPath)
            1 -> work(w, b)
            2 -> sales(w, b)
            3 -> buying(w, b)
            4 -> finance(w, b)
            else -> people(w, b)
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

// ───────────────────────────── tabs ─────────────────────────────

private fun LazyListScope.overview(w: JSONObject, b: Branding, onOpenPath: (String) -> Unit) {
    // attention chips
    val overdueTasks = w.optJSONObject("taskStats")?.optInt("overdue") ?: 0
    val overdueFu = w.optJSONObject("followupStats")?.optInt("overdue") ?: 0
    if (overdueTasks > 0 || overdueFu > 0) item {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (overdueTasks > 0) AlertChip("$overdueTasks overdue task${if (overdueTasks > 1) "s" else ""}", Danger)
            if (overdueFu > 0) AlertChip("$overdueFu overdue follow-up${if (overdueFu > 1) "s" else ""}", Color(0xFFB7791F))
        }
    }

    // KPI cards — the same set as the website, each with an accent, an icon chip and (where it applies) a progress bar
    val kpis = buildList<Kpi> {
        if (w.has("projectCount")) add(Kpi("PROJECTS", num(w.optInt("projectCount")), "active workspaces", "▦", Blue, path = "/admin/projects"))
        w.optJSONObject("taskStats")?.let { add(Kpi("TASKS", num(it.optInt("total")), "${it.optInt("completion")}% completed", "✓", Green, it.optInt("completion") / 100f)) }
        w.optJSONObject("targetStats")?.let { add(Kpi("TARGETS", num(it.optInt("total")), "${it.optInt("completion")}% achieved", "◎", Color(0xFF8B5CF6), it.optInt("completion") / 100f)) }
        w.optJSONObject("leadStats")?.let {
            add(Kpi("LEADS", num(it.optInt("total")), "+${it.optInt("new_week")} this week", "⚑", Color(0xFF0E8A7E)))
            add(Kpi("HOT LEADS", num(it.optInt("hot")), "${it.optInt("warm")} warm · ${it.optInt("cold")} cold", "★", Color(0xFFEB6834)))
        }
        w.optJSONObject("bidStats")?.let { add(Kpi("BIDS", num(it.optInt("total")), "${it.optInt("approved")} proposals approved", "▤", Amber)) }
        w.optJSONObject("assetStats")?.let { add(Kpi("ASSET VALUE", it.moneyOf("value"), "${it.optInt("total")} assets", "◈", Color(0xFF475569))) }
        if (w.has("customerCount")) add(Kpi("CUSTOMERS", num(w.optInt("customerCount")), "${w.optInt("bookingCount")} bookings", "◑", Color(0xFFEC4899)))
        if (w.has("userCount")) add(Kpi("TEAM", num(w.optInt("userCount")), "registered users", "◉", Blue, path = "/admin/roles"))
        w.optJSONObject("stockStats")?.let { add(Kpi("STOCK", num(it.optInt("materials")), "${it.optInt("out") + it.optInt("low")} low / out", "▣", Color(0xFF0E8A7E))) }
        w.optJSONObject("procStats")?.let { add(Kpi("PURCHASES", it.moneyOf("purchases"), "${it.optInt("openIndents")} open indents", "⛟", Color(0xFF475569))) }
        w.optJSONObject("acctStats")?.let { add(Kpi("GROSS SALES", it.moneyOf("grossSales"), "${money(it.optDouble("receivable"))} receivable", "₹", Green)) }
        w.optJSONObject("labourStats")?.let { add(Kpi("WORKFORCE", num(it.optInt("workers")), "${money(it.optDouble("wagesMonth"))} wages / month", "✚", Amber)) }
    }
    items(kpis.chunked(2)) { pair ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { k -> KpiCard(k, Modifier.weight(1f).fillMaxHeight()) { k.path?.let(onOpenPath) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    // latest notifications
    val notes = w.list("notifications")
    if (notes.isNotEmpty()) listCard("Latest notifications", notes.map { n ->
        val data = n.optJSONObject("data")
        Row3(data?.str("title") ?: data?.str("message") ?: "Notification", day(n.str("created_at")) ?: "", if (n.isNull("read_at")) "New" else null)
    }, b)
}

private fun LazyListScope.work(w: JSONObject, b: Branding) {
    w.optJSONObject("taskStats")?.optJSONObject("by_status")?.let { barsCard("Task status", it, b.base, "${w.optJSONObject("taskStats")?.optInt("total")} total") }
    w.optJSONObject("targetStats")?.optJSONObject("by_status")?.let { barsCard("Target status", it, b.dark, "${w.optJSONObject("targetStats")?.optInt("total")} total") }

    val due = (w.list("dueSoonTasks").map { it to "Task" } + w.list("dueSoonTargets").map { it to "Target" })
        .filter { (o, _) -> o.str("due_date") != null }.sortedBy { (o, _) -> o.str("due_date") }.take(10)
    if (due.isNotEmpty()) listCard("Due within 7 days", due.map { (o, kind) ->
        Row3(o.str("name") ?: kind, "$kind · ${o.nestedName("project") ?: "—"}", day(o.str("due_date")))
    }, b)

    val tasks = w.list("recentTasks")
    if (tasks.isNotEmpty()) listCard("Recent tasks", tasks.map {
        Row3(it.str("name") ?: "Task", listOfNotNull(it.nestedName("project"), day(it.str("due_date"))?.let { d -> "due $d" }).joinToString(" · "), it.nestedName("status"))
    }, b)

    val workload = w.list("workload")
    if (workload.isNotEmpty()) listCard("Team workload", workload.map {
        Row3(it.str("name") ?: "—", "${it.optInt("open")} open · ${it.optInt("completed")} done", if (it.optInt("overdue") > 0) "${it.optInt("overdue")} overdue" else null)
    }, b)

    val projects = w.list("projects")
    if (projects.isNotEmpty()) item {
        Card("Project completion") {
            projects.forEach { p ->
                val pct = p.optInt("completion")
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.str("name") ?: "Project", fontSize = 13.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("$pct%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                LinearProgressIndicator(
                    progress = { pct / 100f }, color = b.base, trackColor = b.base.copy(alpha = .15f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(CircleShape),
                )
                Text("${p.optInt("completed_tasks")} of ${p.optInt("total_tasks")} tasks", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
    emptyHint(w, "taskStats", "targetStats", "projects")
}

private fun LazyListScope.sales(w: JSONObject, b: Branding) {
    w.optJSONObject("leadStats")?.let { ls ->
        ls.optJSONObject("by_status")?.let { barsCard("Lead pipeline", it, b.dark, "${ls.optInt("total")} total") }
    }
    w.optJSONObject("leadSources")?.let { barsCard("Lead sources", it, b.base) }

    val leads = w.list("recentLeads")
    if (leads.isNotEmpty()) listCard("Recent leads", leads.map {
        Row3(it.str("client_name") ?: "Lead", listOfNotNull(it.str("phone_number"), it.optJSONObject("assignee")?.str("name") ?: "Unassigned").joinToString(" · "), it.str("status"))
    }, b)

    w.optJSONObject("followupStats")?.let { fs ->
        val rows = (w.list("overdueFollowups").map { it to true } + w.list("upcomingFollowups").map { it to false }).take(8)
        listCard("Follow-ups · ${fs.optInt("pending")} pending · ${fs.optInt("overdue")} overdue", rows.map { (f, late) ->
            Row3(f.optJSONObject("lead")?.str("client_name") ?: "—", "${(f.str("followup_type") ?: "follow-up").replaceFirstChar(Char::uppercase)} · ${day(f.str("next_followup_at")) ?: "—"}", if (late) "Late" else "Due")
        }, b, empty = "No follow-ups scheduled")
    }

    w.optJSONObject("inventoryStats")?.let {
        kvCard("Unit inventory", listOf("Total units" to num(it.optInt("total")), "Available" to num(it.optInt("available")), "Booked" to num(it.optInt("booked")), "Sold" to num(it.optInt("sold")), "Sold value" to it.moneyOf("sold_value")))
    }
    w.optJSONObject("paymentStats")?.let {
        kvCard("Customer payments", listOf("Received (total)" to it.moneyOf("received"), "Received this month" to it.moneyOf("received_month"), "Outstanding" to it.moneyOf("outstanding")))
    }
    w.optJSONObject("careStats")?.let {
        kvCard("Customer care", listOf("Upcoming visits" to num(it.optInt("upcomingVisits")), "Visits this month" to num(it.optInt("visitsMonth")), "Open issues" to num(it.optInt("openIssues"))))
    }
    val visits = w.list("upcomingVisits")
    if (visits.isNotEmpty()) listCard("Next site visits", visits.map {
        Row3(it.str("customer") ?: "—", "${(it.str("visit_type") ?: "visit").replaceFirstChar(Char::uppercase)} · ${day(it.str("visit_at")) ?: "—"}", it.str("status")?.replaceFirstChar(Char::uppercase))
    }, b)
    emptyHint(w, "leadStats", "inventoryStats", "paymentStats", "careStats")
}

private fun LazyListScope.buying(w: JSONObject, b: Branding) {
    w.optJSONObject("stockStats")?.let {
        kvCard("Stock", listOf("Materials" to num(it.optInt("materials")), "Low stock" to num(it.optInt("low")), "Out of stock" to num(it.optInt("out")), "Stock value" to it.moneyOf("value"), "Movements this month" to num(it.optInt("movesMonth"))))
    }
    val alerts = w.list("stockAlerts")
    if (alerts.isNotEmpty()) listCard("Stock alerts", alerts.map {
        Row3(it.str("name") ?: "—", "${it.str("project") ?: ""} balance ${it.optDouble("balance")}", if (it.str("status") == "out") "Out" else "Low")
    }, b)

    w.optJSONObject("procStats")?.let {
        kvCard("Procurement", listOf("Indents" to num(it.optInt("indents")), "Open indents" to num(it.optInt("openIndents")), "Pending deliveries" to num(it.optInt("pendingDeliveries")), "GRNs" to num(it.optInt("grns")), "Purchases" to it.moneyOf("purchases")))
    }
    w.optJSONObject("poStatusCounts")?.let { barsCard("Purchase orders", it, b.base, if (w.has("poValue")) money(w.optDouble("poValue")) else null) }
    val pos = w.list("recentPOs")
    if (pos.isNotEmpty()) listCard("Recent POs", pos.map {
        Row3(it.str("po_number") ?: "PO", "${it.nestedName("project") ?: "—"} · ${money(it.optDouble("total_amount"))}", it.str("status"))
    }, b)

    w.optJSONObject("bidStatusCounts")?.let { barsCard("Bid status", it, b.dark) }
    val bids = w.list("recentBids")
    if (bids.isNotEmpty()) listCard("Recent bids", bids.map {
        Row3(it.str("title") ?: "Bid", it.nestedName("project") ?: "—", it.str("bid_status"))
    }, b)

    w.optJSONObject("assetStats")?.let { kvCard("Assets", listOf("Total assets" to num(it.optInt("total")), "Total value" to it.moneyOf("value"))) }
    w.optJSONObject("assetsByCategory")?.let { barsCard("Assets by category", it, b.base) }
    w.optJSONObject("assetsByLocation")?.let { barsCard("Assets by location", it, b.dark) }

    val maint = (w.list("overdueMaintenances") + w.list("upcomingMaintenances")).take(8)
    if (maint.isNotEmpty()) listCard("Maintenance", maint.map {
        Row3(it.optJSONObject("asset")?.str("name") ?: "—", (it.str("maintenance_type") ?: "").replaceFirstChar(Char::uppercase), day(it.str("next_due_date")))
    }, b)
    val transfers = w.list("recentTransfers")
    if (transfers.isNotEmpty()) listCard("Recent asset transfers", transfers.map {
        Row3(it.optJSONObject("asset")?.str("name") ?: "—", "${it.str("from_location") ?: "—"} → ${it.str("to_location") ?: "—"}", day(it.str("transfer_date")))
    }, b)
    emptyHint(w, "stockStats", "procStats", "poStatusCounts", "bidStats", "assetStats")
}

private fun LazyListScope.finance(w: JSONObject, b: Branding) {
    w.optJSONObject("acctStats")?.let {
        kvCard("Accounts", listOf(
            "Gross sales" to it.moneyOf("grossSales"), "Received" to it.moneyOf("received"), "Receivable" to it.moneyOf("receivable"),
            "Purchases" to it.moneyOf("purchases"), "Expenses this month" to it.moneyOf("expensesMonth"),
            "Cash" to it.moneyOf("cash"), "Bank" to it.moneyOf("bank"), "Open cheques" to num(it.optInt("chequesOpen")),
        ))
    }
    w.optJSONObject("paymentStats")?.let {
        kvCard("Customer payments", listOf("Received (total)" to it.moneyOf("received"), "Received this month" to it.moneyOf("received_month"), "Outstanding" to it.moneyOf("outstanding")))
    }
    emptyHint(w, "acctStats", "paymentStats")
}

private fun LazyListScope.people(w: JSONObject, b: Branding) {
    w.optJSONObject("hrStats")?.let {
        kvCard("HR & Admin", listOf("Active employees" to num(it.optInt("employees")), "Pending leaves" to num(it.optInt("pendingLeaves")), "Pending requests" to num(it.optInt("pendingRequests")), "Payroll this month" to it.moneyOf("payrollMonth")))
    }
    w.optJSONObject("labourStats")?.let {
        kvCard("Labour", listOf("Workers" to num(it.optInt("workers")), "Wages this month" to it.moneyOf("wagesMonth"), "Unpaid" to it.moneyOf("unpaid")))
    }
    w.optJSONObject("usersByRole")?.let { barsCard("Users by role", it, b.base) }
    w.optJSONObject("attendanceToday")?.let { barsCard("Worker attendance today", it, b.dark) }
    w.optJSONObject("fileStats")?.let {
        kvCard("Files & discussions", listOf(
            "Files" to num(it.optInt("files")), "Storage used" to String.format("%.1f MB", it.optDouble("size") / 1_048_576),
            "Uploads this month" to num(it.optInt("uploadsMonth")), "Pending access requests" to num(it.optInt("accessRequests")), "Messages (7 days)" to num(it.optInt("messagesWeek")),
        ))
    }
    emptyHint(w, "hrStats", "labourStats", "usersByRole", "attendanceToday", "fileStats")
}

// ───────────────────────────── cards ─────────────────────────────

/** Shown when none of a tab's sections exist for this user. */
private fun LazyListScope.emptyHint(w: JSONObject, vararg keys: String) {
    if (keys.none { w.has(it) }) item {
        Text("Nothing to show here for your role.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
    }
}

private fun LazyListScope.barsCard(title: String, counts: JSONObject, color: Color, sub: String? = null, semantic: Boolean = true) {
    val keys = counts.keys().asSequence().toList().sortedByDescending { counts.optInt(it) }
    if (keys.isEmpty()) return
    val max = (keys.maxOfOrNull { counts.optInt(it) } ?: 1).coerceAtLeast(1)
    item {
        Card(title, sub) {
            keys.forEach { k ->
                val c = if (semantic) statusColor(k, color) else color
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(c))
                    Text(k, fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(num(counts.optInt(k)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                LinearProgressIndicator(
                    progress = { counts.optInt(k).toFloat() / max }, color = c, trackColor = c.copy(alpha = .14f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(CircleShape),
                )
            }
        }
    }
}

private fun LazyListScope.kvCard(title: String, rows: List<Pair<String, String>>) {
    item {
        Card(title) {
            rows.forEachIndexed { i, (k, v) ->
                Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 4.dp else 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, fontSize = 13.sp, color = Muted)
                    Text(v, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }
    }
}

private fun LazyListScope.listCard(title: String, rows: List<Row3>, b: Branding, empty: String? = null) {
    if (rows.isEmpty() && empty == null) return
    item {
        Card(title, if (rows.isNotEmpty()) "${rows.size}" else null) {
            if (rows.isEmpty()) Text(empty ?: "", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            rows.forEachIndexed { i, r ->
                val tone = statusColor(r.badge, b.base)
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EEEA)))
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(tone.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Text(r.title.trim().take(1).uppercase().ifEmpty { "•" }, color = tone, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink)
                        if (r.subtitle.isNotBlank()) Text(r.subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    r.badge?.takeIf { it.isNotBlank() }?.let { badge ->
                        Text(
                            badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = tone,
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(12.dp)).background(tone.copy(alpha = .13f)).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A KPI tile: tinted icon chip, big value, caption and an optional progress bar. */
@Composable
private fun KpiCard(k: Kpi, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.shadow(4.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White)
            .then(if (k.path != null) Modifier.clickable { onClick() } else Modifier).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(k.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Text(k.glyph, color = k.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(k.label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Text(k.value, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        if (k.progress != null) {
            Box(Modifier.padding(top = 6.dp, bottom = 4.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(k.accent.copy(alpha = .14f))) {
                Box(Modifier.fillMaxWidth(k.progress.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(k.accent))
            }
        }
        Text(k.sub, color = Muted, fontSize = 11.sp, maxLines = 2)
    }
}
@Composable
private fun Card(title: String, sub: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).shadow(3.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f))
            if (sub != null) Text(sub, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SectionTitle(text: String, top: Int = 20) {
    Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = top.dp, bottom = 10.dp))
}

@Composable
private fun AlertChip(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun RoundGlyph(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, color = Color.White, fontSize = 18.sp)
    }
}

@Composable
private fun HeroChip(text: String, bg: Color = Color.White.copy(alpha = .2f)) {
    Text(
        text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun Ring(percent: Int) {
    Box(
        Modifier.size(84.dp).drawBehind {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = .25f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(Color.White, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text("tasks done", color = Color.White.copy(alpha = .8f), fontSize = 9.sp)
        }
    }
}
