package com.example.gavanhomes.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.FabAction
import com.example.gavanhomes.ui.common.FilterBar
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.ModulePages
import com.example.gavanhomes.ui.common.ProvideFab
import com.example.gavanhomes.ui.common.ScrollTopButton
import com.example.gavanhomes.ui.common.SearchBox
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.Paged
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.enc
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.professionals.ProDonutCard
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private const val AssetGroup = "Assets · all projects"
private val GroupGlyph = mapOf("Overview" to "◎", "Work" to "✔", "Sales & leads" to "⚑", "Stock" to "▣", "Money" to "₹", AssetGroup to "⚙")
private val GroupTint = mapOf("Overview" to KitInk, "Work" to InvBlue, "Sales & leads" to InvPurple, "Stock" to InvAmber, "Money" to KitGood, AssetGroup to Color(0xFF4A8AAD))
private val GroupBlurb = mapOf("Overview" to "The project at a glance, and its profit & loss", AssetGroup to "Asset reports across every project you can see", "Work" to "Tasks, targets, operations and attendance", "Sales & leads" to "Leads, follow-ups, sales and bids",
    "Stock" to "Inventory, stock levels and items", "Money" to "Expenses, purchases, cash flow and parties")
private val Palette = listOf(InvBlue, KitGood, InvAmber, InvPurple, KitDanger, KitMuted)

/** The report each group's dashboard card shows (the first of these this person may open). */
private val Featured = listOf("business-intelligence", "asset-register", "task-reports", "target-reports", "leads-pipeline", "sale-report", "inventory-report", "stock-summary", "cash-flow-report", "expense-report")

private fun tint(group: String) = GroupTint[group] ?: InvBlue

/**
 * The project's Reports module, like the website's Reports tab, laid out like the other modules: a Dashboard with each group's
 * headline numbers, All reports (searchable), and a page per group (Work, Sales & leads, Stock, Money) in the bottom-left menu.
 * The + button opens the most used reports straight away. Data: /projects/{uuid}/report-center/… [onOpen] gets a report's key and label.
 */
@Composable
fun ProjectReports(api: ApiClient, b: Branding, uuid: String, onOpen: (String, String) -> Unit) {
    var groups by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { error = null; try { groups = withContext(Dispatchers.IO) { api.get("/projects/$uuid/report-center/catalog").objs("data") } } catch (e: Exception) { error = e.message ?: "Could not load reports" } }
    val g = groups
    if (g == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") } }
        }
        return
    }
    if (g.isEmpty()) { Text("You don't have access to any reports.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp)); return }
    // every report with its group, and each group's featured one
    val all = g.flatMap { grp -> grp.objs("reports").map { grp.optString("group") to it } }
    val featured = g.mapNotNull { grp -> val r = grp.objs("reports").filter { !it.optBoolean("unavailable") }; (Featured.firstNotNullOfOrNull { k -> r.firstOrNull { it.optString("key") == k } } ?: r.firstOrNull())?.let { grp.optString("group") to it } }
    ProvideFab(featured.take(6).map { (grp, r) -> FabAction(r.optString("key"), r.optString("label") + " report", tint(grp), GroupGlyph[grp] ?: "▮") }) { k ->
        all.firstOrNull { it.second.optString("key") == k }?.let { onOpen(k, it.second.optString("label")) }
    }
    val pages = listOf("Dashboard", "All reports") + g.map { it.optString("group") }
    val p = page.coerceIn(0, pages.lastIndex)
    ModulePages("Reports", pages, p) { page = it }

    when (p) {
        0 -> {
            Box(Modifier.padding(top = 4.dp)) {
                ProKpiTiles(listOf(ProKpi(all.size.toString(), "Reports", KitInk, "${all.count { !it.second.optBoolean("unavailable") }} with data")) + g.map { grp -> ProKpi(grp.objs("reports").size.toString(), grp.optString("group").substringBefore(" ·"), tint(grp.optString("group"))) }, columns = 3)
            }
            featured.forEach { (grp, r) -> Highlight(api, uuid, grp, r, onOpen = { onOpen(r.optString("key"), r.optString("label")) }, onMore = { page = pages.indexOf(grp) }) }
        }
        1 -> {
            var q by remember { mutableStateOf("") }
            SearchBox(q, { q = it }, "Search reports…")
            val shown = all.filter { (grp, r) -> q.isBlank() || r.optString("label").contains(q, true) || grp.contains(q, true) }
            if (shown.isEmpty()) Text("No report matches “$q”.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
            shown.groupBy { it.first }.forEach { (grp, rows) -> Card(grp, "${rows.size} report${if (rows.size == 1) "" else "s"}") { rows.forEach { (_, r) -> ReportRow(grp, r) { onOpen(r.optString("key"), r.optString("label")) } } } }
        }
        else -> {
            val grp = g[p - 2]; val name = grp.optString("group")
            Text(GroupBlurb[name] ?: "", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            grp.objs("reports").forEach { r -> RowCard({ onOpen(r.optString("key"), r.optString("label")) }) { ReportLine(name, r.optString("label"), r.optBoolean("unavailable")) } }
        }
    }
}

@Composable
private fun ReportLine(group: String, label: String, unavailable: Boolean = false) {
    val c = if (unavailable) KitMuted else tint(group)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(GroupGlyph[group] ?: "▮", color = c, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, color = if (unavailable) KitMuted else KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(if (unavailable) "Not available yet" else group, color = KitMuted, fontSize = 12.sp)
        }
        Text("›", color = KitMuted, fontSize = 22.sp)
    }
}

@Composable
private fun ReportRow(group: String, r: JSONObject, onClick: () -> Unit) {
    val off = r.optBoolean("unavailable")
    val c = if (off) KitMuted else tint(group)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(GroupGlyph[group] ?: "▮", color = c, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
        Text(r.optString("label"), color = if (off) KitMuted else KitInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text(if (off) "Not yet" else "›", color = KitMuted, fontSize = if (off) 11.sp else 20.sp)
    }
}

/** A group's card on the dashboard: its featured report's headline numbers (loaded on their own), Open, and the group's page. */
@Composable
private fun Highlight(api: ApiClient, uuid: String, group: String, r: JSONObject, onOpen: () -> Unit, onMore: () -> Unit) {
    val key = r.optString("key")
    var stats by remember(key) { mutableStateOf<List<Pair<String, String>>?>(null) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        try { stats = headline(withContext(Dispatchers.IO) { api.get("/projects/$uuid/report-center/report?tab=$key&stats_only=1") }) }
        catch (e: Exception) { failed = true }
    }
    val c = tint(group)
    Card(group.substringBefore(" ·"), r.optString("label") + if (group == AssetGroup) " · all projects" else " · all time") {
        val s = stats
        when {
            s == null && !failed -> Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) }
            s == null || s.isEmpty() -> Text(if (failed) "Couldn't load the numbers — open the report." else "No headline numbers for this report.", color = KitMuted, fontSize = 13.sp)
            else -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                s.take(6).forEach { (l, v) ->
                    Column(Modifier.widthIn(min = 96.dp).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(v, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1)
                        Text(l, color = KitMuted, fontSize = 11.sp, maxLines = 2)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Open report", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c).clickable { onOpen() }.padding(horizontal = 14.dp, vertical = 8.dp))
            Text("All ${group.substringBefore(" ·")} reports", color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = .1f)).clickable { onMore() }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

/** A report's headline numbers as label → shown value: its raw numbers, or the formatted ones the asset reports send. */
private fun headline(m: JSONObject): List<Pair<String, String>> {
    m.optJSONObject("stats")?.let { s -> if (s.length() > 0) return s.keys().asSequence().map { k -> label(k) to fmt(k, s.optDouble(k, 0.0)) }.toList() }
    return m.objs("kpis").map { it.optString("label") to it.optString("value") }
}

/** "completionRate" / "total_amount" → "Completion rate" / "Total amount". */
private fun label(k: String): String = k.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().replaceFirstChar { it.uppercase() }

private val Plain: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }
private val MoneyWords = listOf("amount", "value", "total", "sales", "revenue", "spend", "cost", "cash", "balance", "paid", "due", "price", "profit", "income", "expense", "purchase", "in", "out")
private val CountWords = listOf("count", "items", "tasks", "workers", "leads", "bids", "parties", "bookings", "qty")

private fun fmt(k: String, v: Double): String {
    val words = label(k).lowercase().split(' ')
    return when {
        words.any { it == "rate" || it == "pct" || it == "percent" } -> Plain.format(v) + "%"
        words.any { it in MoneyWords } && CountWords.none { words.last() == it } -> "₹" + Plain.format(v)
        else -> Plain.format(v)
    }
}

private val Periods = listOf("today" to "Today", "week" to "Last 7 days", "month" to "This month", "last_month" to "Last month", "quarter" to "Last 3 months", "year" to "This year", "last_year" to "Last year")
private fun range(key: String?): String {
    val now = LocalDate.now()
    val (from, to) = when (key) {
        "today" -> now to now; "week" -> now.minusDays(6) to now; "month" -> now.withDayOfMonth(1) to now
        "last_month" -> now.minusMonths(1).withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) }
        "quarter" -> now.minusMonths(3) to now; "year" -> now.withDayOfYear(1) to now
        "last_year" -> now.minusYears(1).withDayOfYear(1).let { it to it.plusYears(1).minusDays(1) }
        else -> return ""
    }
    return "&date_from=$from&date_to=$to"
}

/** A small list a report sends (status groups, sources, categories …) is worth showing only when its rows carry numbers. */
private fun JSONObject.hasNumber() = keys().asSequence().any { optString(it).replace(",", "").toDoubleOrNull() != null }

/**
 * One report: the usual filter bar (search, Filter → period); its headline numbers; its charts (a donut for a small single series,
 * labelled bars otherwise); its breakdowns; and its rows, paged — each row a card of the report's columns.
 * Data: GET /projects/{uuid}/report-center/report?tab=…
 */
@Composable
fun ReportScreen(api: ApiClient, b: Branding, uuid: String, tab: String, title: String, onBack: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var settled by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var meta by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(search) { delay(350); settled = search }
    val period = sel["date"]?.firstOrNull()
    val query = "&tab=$tab" + range(period) + (if (settled.isBlank()) "" else "&search=${enc(settled.trim())}")
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(KitPage)) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize().verticalScroll(scroll)) {
            HeroBar(b, "Report", onBack) {
                Column {
                    Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(Periods.firstOrNull { it.first == period }?.second ?: "All time", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    meta?.let { Spacer(Modifier.height(14.dp)); HeroChips("${it.optInt("total")} rows", "${it.optJSONArray("charts")?.length() ?: 0} charts") }
                }
            }
            FilterBar(
                search, { search = it }, "Search this report…",
                groups = listOf(FilterGroup("date", "Period", Periods.map { FilterOption(it.first, it.second) })), selected = sel,
                onApply = { m -> val next = m.mapValues { (_, v) -> v.take(1).toSet() }.filterValues { it.isNotEmpty() }; if (next != sel) { sel = next; meta = null } },
                countWith = { m -> if (m == sel) meta?.optInt("total") ?: -1 else -1 },
            )
            val m = meta
            m?.optString("unavailable")?.takeIf { it.isNotBlank() }?.let { why ->
                Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⌛", fontSize = 34.sp)
                    Text(why, color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
            }
            m?.optString("note")?.takeIf { it.isNotBlank() }?.let { Text("⚠  $it", color = InvAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InvAmber.copy(alpha = .1f)).padding(12.dp)) }
            val heads = m?.let(::headline).orEmpty()
            if (heads.isNotEmpty()) ProKpiTiles(heads.take(16).mapIndexed { i, (l, v) -> ProKpi(v, l, if (i == 0) KitInk else Palette[(i - 1) % 4]) }, columns = if (heads.size % 3 == 0 || heads.size > 4) 3 else 2)
            m?.objs("charts")?.forEach { ch -> Chart(ch) }
            m?.optJSONObject("lists")?.let { lists ->
                lists.keys().asSequence().filter { !it.startsWith("all") }.forEach { k ->
                    val rows = lists.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
                    if (rows.isNotEmpty() && rows.all { it.hasNumber() }) Card(label(k), "${rows.size} rows") {
                        rows.forEach { r ->
                            val cols = r.keys().asSequence().toList()
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(cols.firstOrNull()?.let { r.optString(it) } ?: "", color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(cols.drop(1).map { r.optString(it) }.filter { it.isNotBlank() && it != "null" }.joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
            if (m == null) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            Paged(api, b, "/projects/$uuid/report-center/report", query, 0, if (m?.optString("unavailable").isNullOrBlank()) "Nothing in this report for the period." else "", "row", onMeta = { meta = it }) { r ->
                val cols = r.keys().asSequence().toList()
                RowCard(null) {
                    cols.firstOrNull()?.let { Text(r.optString(it), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    cols.drop(1).filter { r.optString(it).isNotBlank() && r.optString(it) != "—" }.forEach { k ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(label(k), color = KitMuted, fontSize = 12.sp, modifier = Modifier.weight(.9f))
                            Text(r.optString(k), color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        ScrollTopButton(scroll)
    }
}

/** One chart: a donut when it is a single series of a few whole-number parts, otherwise each label with a bar per series. */
@Composable
private fun Chart(ch: JSONObject) {
    val labels = ch.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    val series = ch.objs("series")
    if (labels.isEmpty() || series.isEmpty()) return
    val values = { s: JSONObject -> s.optJSONArray("data")?.let { a -> (0 until a.length()).map { a.optDouble(it, 0.0) } }.orEmpty() }
    val first = values(series[0])
    if (series.size == 1 && ch.optString("type") != "area" && (labels.size <= 6 || (ch.optString("type") == "donut" && labels.size <= 8)) && first.all { it >= 0 && it == Math.floor(it) }) {
        ProDonutCard(label(ch.optString("title")), series[0].optString("name"), labels.mapIndexed { i, l -> Triple(l, first.getOrElse(i) { 0.0 }.toInt(), Palette[i % Palette.size]) }, empty = "Nothing to show yet.")
        return
    }
    Card(label(ch.optString("title")), series.joinToString(" · ") { it.optString("name") }) {
        if (series.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            series.forEachIndexed { si, s -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(Palette[si % 4])); Text(" " + s.optString("name"), color = KitMuted, fontSize = 11.sp) } }
        }
        val top = series.flatMap(values).maxOrNull()?.takeIf { it > 0 } ?: 1.0
        labels.forEachIndexed { i, l ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(l, color = KitInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                series.forEachIndexed { si, s ->
                    val v = values(s).getOrElse(i) { 0.0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ProBar((v / top).toFloat(), Palette[si % 4]) }
                        Text(Plain.format(v), color = KitMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp).widthIn(min = 40.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}
