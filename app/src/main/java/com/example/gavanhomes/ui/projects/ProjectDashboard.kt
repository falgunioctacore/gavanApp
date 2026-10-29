package com.example.gavanhomes.ui.projects

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.horizontalScroll
import com.example.gavanhomes.ui.professionals.ProDonutCard
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.professionals.ProKpi
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Blue = Color(0xFF1E5FAE)
private val Amber = Color(0xFFC47A1A)
private val Purple = Color(0xFF7C3AED)
private val Fallback = listOf(Blue, Amber, KitGood, KitDanger, Purple, Color(0xFF0E9AA7))

/** Slice colours follow the meaning of the label, like the website's charts: done = green, in progress = blue, waiting = amber, bad = red. */
private fun sliceColor(label: String, index: Int): Color = when (label.lowercase()) {
    "completed", "achieved", "available", "awarded" -> KitGood
    "in progress", "cold", "open" -> Blue
    "pending", "warm", "booked", "no status", "draft" -> Amber
    "hot", "missed", "sold", "closed", "overdue" -> KitDanger
    else -> Fallback[index % Fallback.size]
}

private val Rupees: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
private val Short = DateTimeFormatter.ofPattern("d MMM")

private fun JSONObject.list(key: String): List<JSONObject> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()

/**
 * The project's dashboard — the website's project → Dashboard page, native: headline numbers, donut charts of the status mixes,
 * what is overdue / due soon, and the money position. Data: GET /projects/{uuid}/dashboard.
 */
@Composable
fun ProjectDashboard(data: JSONObject?, error: String?, b: Branding, onRetry: () -> Unit) {
    if (data == null) {
        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(error, color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
        }
        return
    }
    val k = data.getJSONObject("kpis")
    fun n(key: String) = k.optInt(key)

    // the dashboard is long, so it is split into tabs
    var tab by remember { mutableIntStateOf(0) }
    com.example.gavanhomes.ui.common.PillTabs(
        listOf("Overview", "Work", "Sales", "Finance", "Site").map { com.example.gavanhomes.ui.common.PillTab(it) }, tab, b.base,
    ) { tab = it }

    if (tab == 0) {
    // ── project health: how far the tasks have got, at a glance ──
    val rate = n("completion_rate")
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(26.dp), ambientColor = b.base, spotColor = b.base)
            .clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(b.deep, b.base))).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(104.dp)) {
                val stroke = 11.dp.toPx()
                val box = Size(size.width - stroke, size.height - stroke)
                drawArc(Color.White.copy(alpha = .25f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), box, style = Stroke(stroke))
                drawArc(Color.White, -90f, 360f * rate / 100f, false, Offset(stroke / 2, stroke / 2), box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$rate%", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("done", color = Color.White.copy(alpha = .85f), fontSize = 11.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Project health", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("${n("completed")} of ${n("tasks")} tasks completed", color = Color.White.copy(alpha = .9f), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HealthChip("${n("tasks_in_progress")} in progress", Color.White.copy(alpha = .22f))
                HealthChip("${n("overdue")} overdue", if (n("overdue") > 0) Color(0xFFE5484D).copy(alpha = .85f) else Color.White.copy(alpha = .22f))
            }
        }
    }

    // ── key numbers ──
    ProKpiTiles(listOf(
        ProKpi(n("targets").toString(), "Targets", Amber, "${n("targets_achieved")} achieved"),
        ProKpi(n("leads").toString(), "Leads", Blue, "All leads"),
        ProKpi(n("inventory").toString(), "Inventory", Blue, "${n("inventory_available")} available"),
        ProKpi(n("booked_units").toString(), "Booked units", KitGood, "${n("sold_units")} sold"),
        ProKpi(n("members").toString(), "Members", Purple, if (k.optBoolean("has_leader")) "With a leader" else "No leader"),
        ProKpi(n("pending_tasks").toString(), "Pending tasks", KitDanger, "${n("overdue")} overdue"),
    ))
    }

    // ── how things split ──
    val charts = data.getJSONObject("charts")
    val donuts = when (tab) {
        1 -> listOf("tasks" to "Tasks by status", "targets" to "Targets by status", "bids" to "Bids by status")
        2 -> listOf("leads" to "Leads by label", "followups" to "Follow-ups", "inventory" to "Inventory status")
        else -> emptyList()
    }
    donuts
        .forEach { (key, title) ->
            val slices = charts.list(key)
            ProDonutCard(title, null, slices.mapIndexed { i, s -> Triple(s.str("label") ?: "", s.optInt("value"), sliceColor(s.str("label") ?: "", i)) }, "No data yet.")
        }

    // ── deadlines ──
    if (tab == 0) {
    data.optJSONObject("more")?.list("today")?.takeIf { it.isNotEmpty() }?.let { today ->
        DashCard("Today's deadlines", "${today.size} to finish today") { today.forEach { d -> ListRow(d.str("name") ?: "—", d.str("type"), "Today", KitDanger) } }
    }
    val overdue = data.list("overdue"); val upcoming = data.list("upcoming")
    if (overdue.isNotEmpty()) DeadlineCard("Overdue", "Past their due date", overdue, KitDanger)
    if (upcoming.isNotEmpty()) DeadlineCard("Due this week", "Next 7 days", upcoming, Amber)
    }

    // ── money: the cash position first, then the detail ──
    if (tab == 3) {
    val m = data.getJSONObject("money")
    fun rupees(key: String) = "₹" + Rupees.format(m.optDouble(key))
    val pin = m.optDouble("payment_in"); val pout = m.optDouble("payment_out"); val net = pin - pout
    DashCard("Money", "Approved payments, expenses and what customers still owe") {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF7F5F1)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NET CASH", color = KitMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
            Text((if (net < 0) "−" else "") + "₹" + Rupees.format(kotlin.math.abs(net)), color = if (net < 0) KitDanger else KitGood, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            val top = maxOf(pin, pout, 1.0)
            MoneyBar("In", (pin / top).toFloat(), KitGood, "₹" + Rupees.format(pin))
            MoneyBar("Out", (pout / top).toFloat(), KitDanger, "₹" + Rupees.format(pout))
        }
        MoneyRow("Expenses", rupees("expenses"), KitInk)
        MoneyRow("Booking value", rupees("booking_value"), KitInk)
        MoneyRow("Collected from customers", rupees("collected"), KitGood)
        MoneyRow("Outstanding", rupees("outstanding"), if (m.optDouble("outstanding") > 0) Amber else KitGood)
    }
    }

    data.optJSONObject("more")?.let { MoreSections(data, it, k, tab) }
}

@Composable
private fun HealthChip(text: String, bg: Color) {
    Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp))
}

@Composable
private fun MoneyBar(label: String, fraction: Float, color: Color, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = KitMuted, fontSize = 12.sp, modifier = Modifier.width(28.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = .14f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
        Text(value, color = KitInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
    }
}

private class Kpi(val label: String, val value: String, val sub: String, val tint: Color)

@Composable
private fun KpiTile(k: Kpi, modifier: Modifier) {
    Column(modifier.shadow(5.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(k.tint))
        Column(Modifier.padding(14.dp)) {
            Text(k.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.5.sp, maxLines = 1)
            Text(k.value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = k.tint)
            Text(k.sub, fontSize = 12.sp, color = KitMuted, maxLines = 1)
        }
    }
}

@Composable
private fun DonutCard(title: String, slices: List<JSONObject>) {
    val total = slices.sumOf { it.optInt("value") }
    DashCard(title, if (total > 0) "$total in total" else null) {
        if (total == 0) {
            Text("No data yet.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            return@DashCard
        }
        val colors = slices.mapIndexed { i, s -> sliceColor(s.str("label") ?: "", i) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)) {
                val stroke = 26.dp.toPx()
                val box = Size(size.width - stroke, size.height - stroke)
                var start = -90f
                slices.forEachIndexed { i, s ->
                    val sweep = 360f * s.optInt("value") / total
                    drawArc(colors[i], start, (sweep - if (slices.size > 1) 1.5f else 0f).coerceAtLeast(0.5f), false, Offset(stroke / 2, stroke / 2), box, style = Stroke(stroke))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total", fontSize = 12.sp, color = KitMuted)
                Text(total.toString(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = KitInk)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { i, s ->
                val v = s.optInt("value")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(colors[i]))
                    Text(s.str("label") ?: "", color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Text("$v · ${Math.round(v * 100f / total)}%", color = KitMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DeadlineCard(title: String, sub: String, items: List<JSONObject>, tint: Color) {
    DashCard(title, sub) {
        items.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(tint))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(d.str("name") ?: "—", color = KitInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(d.str("type") ?: "", color = KitMuted, fontSize = 12.sp)
                }
                Text(runCatching { LocalDate.parse(d.getString("due")).format(Short) }.getOrDefault(""), color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MoneyRow(label: String, value: String, tint: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = KitMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@Composable
private fun DashCard(title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
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

// ───────────────────────────── the rest of the website's dashboard ─────────────────────────────

private fun JSONObject.nums(key: String): List<Int> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList()
private fun JSONObject.texts(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

private val Palette = listOf(Blue, KitGood, Amber, Purple, Color(0xFFEC4899), Color(0xFF0E9AA7))

/** A line in a "recent …" list: a title and a small line under it on the left, an optional badge and an optional amount on the right. */
@Composable
private fun ListRow(title: String, sub: String?, badge: String? = null, badgeColor: Color = KitMuted, value: String? = null, valueColor: Color = KitInk) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, color = valueColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        if (badge != null) Text(badge, color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp)).background(badgeColor.copy(alpha = .14f)).padding(horizontal = 9.dp, vertical = 4.dp))
    }
}

@Composable
private fun Chips(items: List<Pair<String, Color>>) {
    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (t, c) -> Text(t, color = c, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 7.dp)) }
    }
}

private fun money(v: Double) = "₹" + Rupees.format(v)

/** Six months of what was created, as grouped bars. */
@Composable
private fun TrendChart(labels: List<String>, series: List<Triple<String, List<Int>, Color>>) {
    val top = (series.maxOfOrNull { it.second.maxOrNull() ?: 0 } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            labels.forEachIndexed { i, _ ->
                Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                    series.forEach { (_, vals, c) ->
                        val v = vals.getOrElse(i) { 0 }
                        Box(Modifier.width(7.dp).fillMaxHeight((v.toFloat() / top).coerceIn(0.02f, 1f)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (v == 0) c.copy(alpha = .25f) else c))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { labels.forEach { Text(it, color = KitMuted, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) } }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            series.forEach { (name, vals, c) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(c)); Text("$name · ${vals.sum()}", color = KitInk, fontSize = 12.sp)
                }
            }
        }
    }
}

/** Every other section of the website's project dashboard, in its order. [data] is the whole response, [more] its `more` part. */
@Composable
private fun MoreSections(data: JSONObject, more: JSONObject, k: JSONObject, tab: Int) {
    // ── activity trend ──
    if (tab == 0) {
    more.optJSONObject("trend")?.let { t ->
        DashCard("Activity trend", "Created in the last 6 months") {
            TrendChart(t.texts("labels"), listOf(Triple("Tasks", t.nums("tasks"), Blue), Triple("Leads", t.nums("leads"), KitGood), Triple("Targets", t.nums("targets"), Amber)))
        }
    }
    }

    // ── progress overview ──
    if (tab == 0) {
    val tasks = k.optInt("tasks"); val targets = k.optInt("targets"); val units = k.optInt("inventory")
    DashCard("Progress overview", "How far each part of the project has got") {
        @Composable fun bar(label: String, done: Int, total: Int, color: Color) {
            val pct = if (total > 0) done * 100 / total else 0
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { Text(label, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text("$done / $total · $pct%", color = KitMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = .15f))) { Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(color)) }
            }
        }
        bar("Tasks completed", k.optInt("completed"), tasks, KitGood)
        bar("Targets achieved", k.optInt("targets_achieved"), targets, Amber)
        bar("Units sold or booked", k.optInt("sold_units") + k.optInt("booked_units"), units, Blue)
    }
    }

    // ── team ──
    if (tab == 1) {
    more.optJSONObject("team")?.let { team ->
        val leader = team.optJSONObject("leader"); val members = team.list("members")
        DashCard("Team", "${members.size} member${if (members.size == 1) "" else "s"}" + if (leader == null) " · no leader set" else "") {
            leader?.let { l -> ListRow(l.str("name") ?: "—", "Team leader" + (l.str("role")?.let { " · $it" } ?: ""), "Leader", Purple) }
            members.forEach { m -> ListRow(m.str("name") ?: "—", m.str("role")) }
            if (leader == null && members.isEmpty()) Text("Nobody is on this project yet.", color = KitMuted, fontSize = 14.sp)
        }
    }
    }

    // ── recent tasks ──
    if (tab == 1) {
    val recentTasks = more.list("recent_tasks")
    if (recentTasks.isNotEmpty()) DashCard("Recent tasks") {
        recentTasks.forEach { t -> ListRow(t.str("name") ?: "Task", t.str("due")?.let { "Due ${shortDate(it)}" }, t.str("status"), taskTone(t.str("status"))) }
    }
    }

    // ── inventory summary ──
    if (tab == 2) {
    val inv = more.list("inventory_types")
    if (inv.isNotEmpty()) DashCard("Inventory summary", "Units by type") {
        inv.forEach { r ->
            val total = r.optInt("total").coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { Text(r.optString("type"), color = KitInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text("${r.optInt("total")} units", color = KitMuted, fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEDEBE7)), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    listOf(r.optInt("available") to KitGood, r.optInt("booked") to Amber, r.optInt("sold") to KitDanger).filter { it.first > 0 }.forEach { (n, c) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(c)) }
                }
                Chips(listOf("${r.optInt("available")} available" to KitGood, "${r.optInt("booked")} booked" to Amber, "${r.optInt("sold")} sold" to KitDanger))
            }
        }
    }
    }

    // ── leads ──
    if (tab == 2) {
    more.optJSONObject("leads")?.let { l ->
        DashCard("Leads", "${k.optInt("leads")} in total") {
            Chips(listOf("${l.optInt("working")} working" to Blue, "${l.optInt("disqualified")} disqualified" to KitDanger, "${l.optInt("not_reachable")} not reachable" to Amber))
            l.list("recent").forEach { r -> ListRow(r.str("name") ?: "Lead", listOfNotNull(r.str("status"), r.str("at")?.let { shortDate(it) }).joinToString(" · "), r.str("label"), leadTone(r.str("label"))) }
        }
    }
    }

    // ── follow-ups ──
    if (tab == 2) {
    more.optJSONObject("followups")?.let { f ->
        val up = f.list("upcoming"); val od = f.list("overdue")
        if (up.isNotEmpty() || od.isNotEmpty() || f.optInt("today") > 0) DashCard("Follow-ups", "${f.optInt("today")} today") {
            if (od.isNotEmpty()) { Text("OVERDUE", color = KitDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp); od.forEach { r -> ListRow(r.str("lead") ?: "Lead", r.str("type"), r.str("at"), KitDanger) } }
            if (up.isNotEmpty()) { Text("UPCOMING", color = KitGood, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp); up.forEach { r -> ListRow(r.str("lead") ?: "Lead", r.str("type"), r.str("at"), KitGood) } }
        }
    }
    }

    // ── bids ──
    if (tab == 1) {
    val bids = more.list("bids_recent")
    if (bids.isNotEmpty()) DashCard("Bids overview", "The latest bids") {
        bids.forEach { r -> ListRow(r.str("title") ?: "Bid", r.str("end")?.let { "Closes ${shortDate(it)}" }, r.str("status"), bidTone(r.str("status"))) }
    }
    }

    // ── accounting: the latest money movements ──
    if (tab == 3) {
    val txns = more.list("transactions")
    if (txns.isNotEmpty()) DashCard("Accounting", "Latest payments") {
        txns.forEach { t ->
            val inn = t.optBoolean("in")
            ListRow(t.str("party") ?: if (inn) "Payment in" else "Payment out", listOfNotNull(t.str("category"), t.str("date")?.let { shortDate(it) }, t.str("status")).joinToString(" · "), value = (if (inn) "+" else "−") + money(t.optDouble("amount")), valueColor = if (inn) KitGood else KitDanger)
        }
    }
    }

    // ── procurements ──
    if (tab == 3) {
    more.optJSONObject("procurement")?.let { p ->
        DashCard("Procurements", "${p.optInt("pos")} purchase orders · ${money(p.optDouble("po_value"))}") {
            Chips(listOf("${p.optInt("pending_indents")} indents pending" to Amber, "${p.optInt("indents")} indents in all" to Blue))
            val slices = p.list("po_status")
            if (slices.isNotEmpty()) Chips(slices.map { "${it.optInt("value")} ${it.str("label")}" to Blue })
            p.list("recent").forEach { r -> ListRow(r.str("po") ?: "PO", r.str("vendor"), r.str("status"), Blue, money(r.optDouble("amount"))) }
        }
    }
    }

    // ── assets ──
    if (tab == 4) {
    more.optJSONObject("assets")?.let { a ->
        if (a.optInt("total") > 0) DashCard("Assets", "${a.optInt("total")} assets · worth ${money(a.optDouble("value"))}") {
            if (a.optInt("maintenance_due") > 0) Chips(listOf("${a.optInt("maintenance_due")} maintenance due this week" to KitDanger))
            val slices = a.list("status")
            if (slices.isNotEmpty()) Chips(slices.map { "${it.optInt("value")} ${it.str("label")}" to Blue })
            a.list("recent").forEach { r -> ListRow(r.str("name") ?: "Asset", r.str("code"), r.str("status"), Blue, money(r.optDouble("value"))) }
        }
    }
    }

    // ── attendance today ──
    if (tab == 4) {
    more.optJSONObject("attendance")?.let { a ->
        if (a.optInt("workers") > 0) DashCard("Attendance", "Today · ${a.optInt("marked")} of ${a.optInt("workers")} workers marked") {
            Chips(listOf("${a.optInt("present")} present" to KitGood, "${a.optInt("half_day")} half day" to Amber, "${a.optInt("absent")} absent" to KitDanger, "${(a.optInt("workers") - a.optInt("marked")).coerceAtLeast(0)} not marked" to KitMuted))
        }
    }
    }

    // ── customers ──
    if (tab == 2) {
    more.optJSONObject("customers")?.let { c ->
        val recent = c.list("recent")
        if (c.optInt("total") > 0 || recent.isNotEmpty()) DashCard("Customers", "${c.optInt("total")} customer${if (c.optInt("total") == 1) "" else "s"} with bookings") {
            recent.forEach { r -> ListRow(r.str("customer") ?: "Customer", r.str("ref"), r.str("status"), Amber, money(r.optDouble("amount"))) }
        }
    }
    }

}

private val ShortParse = DateTimeFormatter.ofPattern("d MMM")
private fun shortDate(s: String): String = runCatching { LocalDate.parse(s.take(10)).format(ShortParse) }.getOrDefault(s)
private fun taskTone(s: String?): Color = when (s) { "Completed" -> KitGood; "In Progress" -> Blue; "Overdue" -> KitDanger; "Cancelled" -> KitMuted; else -> Amber }
private fun leadTone(s: String?): Color = when (s) { "Hot" -> KitDanger; "Warm" -> Amber; "Cold" -> Blue; else -> KitMuted }
private fun bidTone(s: String?): Color = when (s?.lowercase()) { "awarded" -> KitGood; "open" -> Blue; "closed" -> KitDanger; else -> Amber }
