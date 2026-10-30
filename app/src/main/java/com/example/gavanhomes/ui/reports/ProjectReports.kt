package com.example.gavanhomes.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.ScrollTopButton
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.Paged
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.enc
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private val GroupGlyph = mapOf("Work" to "✔", "Sales & leads" to "⚑", "Stock" to "▣", "Money" to "₹")
private val GroupTint = mapOf("Work" to InvBlue, "Sales & leads" to InvPurple, "Stock" to InvAmber, "Money" to KitGood)

/**
 * The project's Reports module, like the website's Reports tab: every report it serves as data, grouped (Work, Sales & leads, Stock,
 * Money). Tap one to open it. Data: GET /projects/{uuid}/report-center/catalog. [onOpen] gets the report's key and label.
 */
@Composable
fun ProjectReports(api: ApiClient, b: Branding, uuid: String, onOpen: (String, String) -> Unit) {
    var groups by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { error = null; try { groups = withContext(Dispatchers.IO) { api.get("/projects/$uuid/report-center/catalog").objs("data") } } catch (e: Exception) { error = e.message ?: "Could not load reports" } }
    val g = groups
    if (g == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") } }
        }
        return
    }
    if (g.isEmpty()) Text("You don't have access to any reports.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
    g.forEach { grp ->
        val name = grp.optString("group"); val c = GroupTint[name] ?: InvBlue
        Card(name, "${grp.objs("reports").size} reports") {
            grp.objs("reports").forEach { r ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onOpen(r.optString("key"), r.optString("label")) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(GroupGlyph[name] ?: "▮", color = c, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                    Text(r.optString("label"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Text("›", color = KitMuted, fontSize = 20.sp)
                }
            }
        }
    }
}

/** "completionRate" / "total_amount" → "Completion rate" / "Total amount". */
private fun label(k: String): String = k.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().replaceFirstChar { it.uppercase() }

private val Plain: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }
private val MoneyWords = listOf("amount", "value", "total", "sales", "revenue", "spend", "cost", "cash", "balance", "paid", "due", "price", "profit", "income", "expense", "purchase")
private val CountWords = listOf("count", "items", "tasks", "workers", "leads", "bids", "parties", "bookings")

private fun fmt(k: String, v: Double): String {
    val lk = k.lowercase()
    return when {
        lk.contains("rate") || lk.contains("pct") || lk.contains("percent") -> Plain.format(v) + "%"
        MoneyWords.any { lk.contains(it) } && CountWords.none { lk.endsWith(it) } -> "₹" + Plain.format(v)
        else -> Plain.format(v)
    }
}

private val Periods = listOf("all" to "All time", "month" to "This month", "quarter" to "Last 3 months", "year" to "This year")

/**
 * One report: pick the period; its headline numbers; its charts (each series as labelled bars); and its rows, searchable and paged —
 * each row a card of the report's columns. Data: GET /projects/{uuid}/report-center/report?tab=…
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(api: ApiClient, b: Branding, uuid: String, tab: String, title: String, onBack: () -> Unit) {
    var period by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    var settled by remember { mutableStateOf("") }
    var meta by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(search) { delay(350); settled = search }
    val now = LocalDate.now()
    val range = when (period) {
        "month" -> "&date_from=${now.withDayOfMonth(1)}&date_to=$now"
        "quarter" -> "&date_from=${now.minusMonths(3)}&date_to=$now"
        "year" -> "&date_from=${now.withDayOfYear(1)}&date_to=$now"
        else -> ""
    }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(KitPage)) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize().verticalScroll(scroll)) {
            HeroBar(b, "Report", onBack) {
                Column {
                    Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(Periods.first { it.first == period }.second, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Periods.forEach { (k, l) -> Pick(l, period == k, b.base) { period = k; meta = null } }
            }
            val m = meta
            val stats = m?.optJSONObject("stats")
            val keys = stats?.keys()?.asSequence()?.toList().orEmpty()
            if (keys.isNotEmpty()) ProKpiTiles(keys.take(12).map { k -> ProKpi(fmt(k, stats!!.optDouble(k, 0.0)), label(k), KitInk) }, columns = if (keys.size % 3 == 0 || keys.size > 4) 3 else 2)
            m?.objs("charts")?.forEach { ch ->
                val labels = ch.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                val series = ch.objs("series")
                if (labels.isNotEmpty() && series.isNotEmpty()) Card(label(ch.optString("title")), series.joinToString(" · ") { it.optString("name") }) {
                    val colors = listOf(InvBlue, KitGood, InvAmber, InvPurple)
                    val top = series.flatMap { s -> s.optJSONArray("data")?.let { a -> (0 until a.length()).map { a.optDouble(it, 0.0) } }.orEmpty() }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                    labels.forEachIndexed { i, l ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(l, color = KitInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            series.forEachIndexed { si, s ->
                                val v = s.optJSONArray("data")?.optDouble(i, 0.0) ?: 0.0
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { ProBar((v / top).toFloat(), colors[si % colors.size]) }
                                    Text(Plain.format(v), color = KitMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp).widthIn(min = 40.dp), textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                }
            }
            if (m == null) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            com.example.gavanhomes.ui.common.SearchBox(search, { search = it }, "Search this report…")
            Paged(
                api, b, "/projects/$uuid/report-center/report", "&tab=$tab$range" + (if (settled.isBlank()) "" else "&search=${enc(settled.trim())}"), 0,
                "Nothing in this report for the period.", "row", onMeta = { meta = it },
            ) { r ->
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
