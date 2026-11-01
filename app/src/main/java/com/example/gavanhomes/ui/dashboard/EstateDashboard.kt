package com.example.gavanhomes.ui.dashboard

import androidx.compose.foundation.background
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import org.json.JSONObject

private val Ink: Color get() = com.example.gavanhomes.ui.common.KitInk
private val Muted: Color get() = com.example.gavanhomes.ui.common.KitMuted
private val Soft: Color get() = com.example.gavanhomes.ui.common.KitSoft
private val Avail = Color(0xFF0E8A50)      // available — green
private val Booked = Color(0xFFC47A1A)     // booked — amber
private val Sold = Color(0xFF2F6FE4)       // sold — blue
private val Red = Color(0xFFD64545)

/** ₹ in the Indian way: 2.50 Cr, 15.7 L, 45,000. */
internal fun inr(v: Double): String = "₹" + when {
    v >= 1e7 -> "%.2f Cr".format(v / 1e7)
    v >= 1e5 -> "%.1f L".format(v / 1e5)
    else -> java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(v.toLong())
}

private fun JSONObject.rows(k: String): List<JSONObject> = optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()

@Composable
private fun EstateCard(title: String, sub: String? = null, action: Pair<String, () -> Unit>? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                sub?.let { Text(it, color = Muted, fontSize = 12.sp) }
            }
            action?.let { (l, go) -> Text("$l  ›", color = Sold, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { go() }.padding(6.dp)) }
        }
        content()
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 11.dp)) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1)
        Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
        sub?.let { Text(it, color = Muted.copy(alpha = .8f), fontSize = 10.sp, maxLines = 1) }
    }
}

/** A bar split into parts (each a share of the whole). */
@Composable
private fun SplitBar(parts: List<Pair<Int, Color>>, height: Int = 14) {
    val total = parts.sumOf { it.first }
    Row(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape((height / 2).dp)).background(com.example.gavanhomes.ui.common.KitLine)) {
        if (total > 0) parts.filter { it.first > 0 }.forEach { (n, c) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(c)) }
    }
}

@Composable
private fun Progress(pct: Int, color: Color) {
    Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(com.example.gavanhomes.ui.common.KitLine)) {
        if (pct > 0) Box(Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f).fillMaxHeight().background(color))
    }
}

@Composable
private fun Legend(label: String, value: String, c: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Text(" $label ", color = Muted, fontSize = 12.sp)
        Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Overview: the units, sales & collections, the next six months' trend, the buyer funnel, the agenda, dues, the top projects. */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.estateOverview(e: JSONObject, b: Branding, onTab: (Int) -> Unit, onOpenProject: (String) -> Unit) {
    e.optJSONObject("units")?.let { u -> item { UnitsCard(u) } }
    e.optJSONObject("sales")?.let { s ->
        item {
            EstateCard("Sales & collections", "Bookings and money received", action = "Details" to { onTab(1) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(inr(s.optDouble("value")), "Booked value", Sold, Modifier.weight(1f), "${s.optInt("bookings")} bookings")
                    Stat(inr(s.optDouble("received")), "Collected", Avail, Modifier.weight(1f), "${s.optInt("collection_rate")}% of value")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(inr(s.optDouble("due")), "Still to collect", if (s.optDouble("due") > 0) Red else Muted, Modifier.weight(1f), "${s.optInt("pending_payments")} payments pending")
                    Stat(inr(s.optDouble("avg_ticket")), "Average deal", Ink, Modifier.weight(1f), "this month: ${s.optInt("month_bookings")} booked")
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row { Text("Collection", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("${s.optInt("collection_rate")}%", color = Avail, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
                    Progress(s.optInt("collection_rate"), Avail)
                }
            }
        }
        val trend = s.rows("trend")
        if (trend.isNotEmpty()) item { TrendCard(trend) }
    }
    e.optJSONObject("funnel")?.let { f -> item { FunnelCard(f) } }
    val agenda = e.rows("agenda")
    if (agenda.isNotEmpty()) item {
        EstateCard("Coming up", "Site visits, follow-ups and payments due") {
            agenda.forEach { a ->
                val (sign, c) = when (a.optString("kind")) { "visit" -> "⌂" to Sold; "payment" -> "₹" to Booked; else -> "☏" to Avail }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Text(sign, color = c, fontWeight = FontWeight.ExtraBold) }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(a.optString("title"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(a.str("at")?.let(::whenText), a.str("project")).joinToString(" · "), color = Muted, fontSize = 11.sp)
                    }
                    if (!a.isNull("amount")) Text(inr(a.optDouble("amount")), color = Booked, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
        }
    }
    val dues = e.optJSONObject("sales")?.rows("dues").orEmpty()
    if (dues.isNotEmpty()) item {
        EstateCard("Payments to collect", "Pending instalments, oldest first") {
            dues.forEach { d ->
                val late = d.optBoolean("overdue")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (late) Red.copy(alpha = .06f) else Soft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.str("buyer") ?: "Buyer", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(d.str("project"), d.str("date")?.let { (if (late) "was due " else "due ") + whenText(it) } ?: "no due date").joinToString(" · "), color = if (late) Red else Muted, fontSize = 11.sp)
                    }
                    Text(inr(d.optDouble("amount")), color = if (late) Red else Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
    }
    val projects = e.rows("projects")
    if (projects.count { it.optInt("units") > 0 } >= 2) item { ProjectsChart(projects, onOpenProject) }
    if (projects.isNotEmpty()) item {
        EstateCard("Projects", "${projects.size} projects · tap one to open it", action = "All" to { onTab(2) }) {
            projects.take(3).forEach { p -> ProjectRow(p) { onOpenProject(p.optString("uuid")) } }
        }
    }
    e.optJSONObject("site")?.let { s -> item { SiteCard(s) { onTab(3) } } }
}

// ── chart colours: the reference categorical order, checked with the palette validator (all pairs pass, colour-blind too) ──
private val CAvail = Color(0xFF1BAF7A)     // aqua — available (light on white: always shown with its number)
private val CBooked = Color(0xFFEB6834)    // orange — booked
private val CSold = Color(0xFF2A78D6)      // blue — sold
private val Grid: Color get() = com.example.gavanhomes.ui.common.KitLine

/**
 * A donut of parts that make a whole, with a 2-px surface gap between slices and the headline in the middle. Tap a slice (or
 * its legend row) to pick it; the middle then shows that part instead.
 */
@Composable
private fun Donut(parts: List<Triple<String, Int, Color>>, centre: String, centreLabel: String, size: Int = 132) {
    var picked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(
                Modifier.size(size.dp).pointerInput(parts) {
                    detectTapGestures { o ->
                        val c = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
                        var a = Math.toDegrees(kotlin.math.atan2((o.y - c.y).toDouble(), (o.x - c.x).toDouble())) + 90
                        if (a < 0) a += 360
                        var run = 0.0
                        picked = parts.indexOfFirst { p -> run += 360.0 * p.second / total; a <= run }.takeIf { it >= 0 }?.let { if (it == picked) null else it }
                    }
                },
            ) {
                val stroke = this.size.width * .16f
                val gap = if (parts.count { it.second > 0 } > 1) 2.5f else 0f
                var start = -90f
                if (parts.all { it.second == 0 }) drawArc(Grid, 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                parts.forEachIndexed { i, (_, v, c) ->
                    if (v <= 0) return@forEachIndexed
                    val sweep = 360f * v / total
                    val w = if (picked == i) stroke * 1.22f else stroke
                    drawArc(if (picked == null || picked == i) c else c.copy(alpha = .35f), start + gap / 2, (sweep - gap).coerceAtLeast(.5f), false,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke * .6f, stroke * .6f), size = androidx.compose.ui.geometry.Size(this.size.width - stroke * 1.2f, this.size.height - stroke * 1.2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                    start += sweep
                }
            }
            val p = picked?.let { parts[it] }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p?.let { "${it.second}" } ?: centre, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(p?.first?.lowercase() ?: centreLabel, color = Muted, fontSize = 11.sp)
            }
        }
        // the legend, always shown, each row with its count and share — identity never by colour alone
        Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEachIndexed { i, (l, v, c) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (picked == i) c.copy(alpha = .1f) else Color.Transparent).clickable { picked = if (picked == i) null else i }.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                    Text(" $l", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("$v", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text("  ${v * 100 / total}%", color = Muted, fontSize = 11.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitsCard(u: JSONObject) {
    val total = u.optInt("total")
    EstateCard("Unit inventory", "$total units across your projects · tap a slice") {
        Donut(
            listOfNotNull(Triple("Available", u.optInt("available"), CAvail), Triple("Booked", u.optInt("booked"), CBooked), Triple("Sold", u.optInt("sold"), CSold),
                Triple("Other", u.optInt("other"), Muted).takeIf { u.optInt("other") > 0 }),
            "${u.optInt("sold_rate")}%", "sold or booked",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(inr(u.optDouble("stock_value")), "Unsold stock value", Avail, Modifier.weight(1f))
            Stat(inr(u.optDouble("sold_value")), "Booked & sold value", Sold, Modifier.weight(1f))
        }
        val types = u.rows("by_type")
        if (types.size > 1 || types.firstOrNull()?.optString("type")?.equals("Other", true) == false) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BY TYPE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            types.forEach { t ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row {
                        Text(t.optString("type"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("${t.optInt("available")} free · ${t.optInt("sold")} gone · ${t.optInt("total")}", color = Muted, fontSize = 12.sp)
                    }
                    SplitBar(listOf(t.optInt("available") to CAvail, t.optInt("sold") to CSold, (t.optInt("total") - t.optInt("available") - t.optInt("sold")).coerceAtLeast(0) to Grid), height = 8)
                }
            }
        }
        val sizes = u.rows("by_size")
        if (sizes.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sizes.forEach { s -> Stat("${s.optInt("available")}/${s.optInt("total")}", s.optString("size"), Ink, Modifier.widthIn(min = 82.dp), "available") }
        }
    }
}

/**
 * Change over time as lines on one ₹ axis: each series a 2-px line with 8-px markers, three recessive gridlines with their values,
 * the months along the bottom. Tap anywhere to pick the nearest month — a guide line and the month's exact figures appear.
 */
@Composable
private fun LineChart(labels: List<String>, series: List<Triple<String, List<Double>, Color>>, format: (Double) -> String) {
    var picked by androidx.compose.runtime.remember(labels) { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val top = series.flatMap { it.second }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val n = labels.size
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // the picked month's figures (the tooltip), or a hint
        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            val i = picked
            if (i == null) Text("Tap the chart to see a month", color = Muted, fontSize = 11.sp)
            else {
                Text(labels[i], color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                series.forEach { (name, v, c) -> Spacer(Modifier.width(12.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Text(" $name ${format(v[i])}", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Row {
            // the axis values, in text ink
            Column(Modifier.height(140.dp).width(54.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(top, top / 2, 0.0).forEach { Text(format(it), color = Muted, fontSize = 9.sp, maxLines = 1) }
            }
            androidx.compose.foundation.Canvas(
                Modifier.weight(1f).height(140.dp).pointerInput(labels) {
                    detectTapGestures { o -> if (n > 1) picked = ((o.x / this.size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1).let { if (it == picked) null else it } }
                },
            ) {
                val w = this.size.width; val h = this.size.height; val pad = 6.dp.toPx()
                val x = { i: Int -> if (n == 1) w / 2 else pad + (w - 2 * pad) * i / (n - 1) }
                val y = { v: Double -> (h - pad) - ((h - 2 * pad) * (v / top)).toFloat() }
                listOf(0.0, top / 2, top).forEach { g -> drawLine(Grid, androidx.compose.ui.geometry.Offset(0f, y(g)), androidx.compose.ui.geometry.Offset(w, y(g)), 1.dp.toPx()) }
                picked?.let { i -> drawLine(Muted.copy(alpha = .5f), androidx.compose.ui.geometry.Offset(x(i), 0f), androidx.compose.ui.geometry.Offset(x(i), h), 1.dp.toPx()) }
                series.forEach { (_, v, c) ->
                    val path = androidx.compose.ui.graphics.Path()
                    v.forEachIndexed { i, value -> if (i == 0) path.moveTo(x(i), y(value)) else path.lineTo(x(i), y(value)) }
                    // a soft fill under the line, then the line itself
                    val fill = androidx.compose.ui.graphics.Path().apply { addPath(path); lineTo(x(v.lastIndex), h - pad); lineTo(x(0), h - pad); close() }
                    drawPath(fill, c.copy(alpha = .08f))
                    drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    v.forEachIndexed { i, value ->
                        val r = if (picked == i) 5.dp.toPx() else 4.dp.toPx()
                        drawCircle(Color.White, r + 2.dp.toPx(), androidx.compose.ui.geometry.Offset(x(i), y(value)))   // a surface ring where markers overlap
                        drawCircle(c, r, androidx.compose.ui.geometry.Offset(x(i), y(value)))
                    }
                }
            }
        }
        Row(Modifier.padding(start = 54.dp)) { labels.forEach { Text(it, color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) } }
    }
}

@Composable
private fun TrendCard(trend: List<JSONObject>) {
    EstateCard("Last six months", "Booked value against money collected") {
        LineChart(
            trend.map { it.optString("month") },
            listOf(Triple("Booked", trend.map { it.optDouble("value") }, CSold), Triple("Collected", trend.map { it.optDouble("collected") }, CAvail)),
            ::inr,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { Legend("Booked", inr(trend.sumOf { it.optDouble("value") }), CSold); Legend("Collected", inr(trend.sumOf { it.optDouble("collected") }), CAvail) }
    }
}

/** Projects side by side: one bar per project of how much of it is sold or booked — one hue, the value printed at each bar. */
@Composable
private fun ProjectsChart(projects: List<JSONObject>, onOpen: (String) -> Unit) {
    val list = projects.filter { it.optInt("units") > 0 }.sortedByDescending { it.optInt("sold_rate") }.take(8)
    if (list.size < 2) return
    EstateCard("Projects compared", "Share of units sold or booked · tap one to open it") {
        list.forEach { p ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(p.optString("uuid")) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.optString("name"), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(Grid)) {
                    if (p.optInt("sold_rate") > 0) Box(Modifier.fillMaxWidth(p.optInt("sold_rate") / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(CSold))
                }
                Text("${p.optInt("sold_rate")}%", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
            }
            Text("${p.optInt("sold")} of ${p.optInt("units")} units", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 110.dp))
        }
    }
}

/** Leads → hot → site visits → bookings, each a bar as wide as its share of the leads. */
@Composable
private fun FunnelCard(f: JSONObject) {
    val steps = listOfNotNull(
        Triple("Leads", f.optInt("leads"), Sold), Triple("Hot leads", f.optInt("hot"), Red), Triple("Site visits this month", f.optInt("visits_month"), Booked),
        Triple("Converted", f.optInt("converted"), Avail), if (!f.isNull("bookings")) Triple("Bookings", f.optInt("bookings"), Ink) else null,
    )
    val top = steps.maxOf { it.second }.coerceAtLeast(1)
    EstateCard("Buyer funnel", "${f.optInt("new_month")} new leads this month") {
        steps.forEach { (l, n, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(22.dp)) {
                    Box(Modifier.fillMaxWidth((n.toFloat() / top).coerceIn(.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = .85f)))
                }
                Text(java.text.NumberFormat.getIntegerInstance().format(n), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(54.dp))
            }
        }
        val today = f.optInt("followups_today"); val late = f.optInt("followups_overdue")
        if (today + late > 0) Text("☏ $today follow-up${if (today == 1) "" else "s"} today" + if (late > 0) " · $late overdue" else "", color = if (late > 0) Red else Avail, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProjectRow(p: JSONObject, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Soft).clickable { onClick() }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.optString("name"), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(p.str("code"), "${p.optInt("units")} units", "${p.optInt("available")} available").joinToString(" · "), color = Muted, fontSize = 11.sp)
            }
            if (p.optDouble("value") > 0) Text(inr(p.optDouble("value")), color = Sold, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sold", color = Muted, fontSize = 11.sp, modifier = Modifier.width(62.dp)); Box(Modifier.weight(1f)) { Progress(p.optInt("sold_rate"), Sold) }
            Text(" ${p.optInt("sold_rate")}%", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        }
        if (p.optDouble("value") > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Collected", color = Muted, fontSize = 11.sp, modifier = Modifier.width(62.dp)); Box(Modifier.weight(1f)) { Progress(p.optInt("collection_rate"), Avail) }
            Text(" ${p.optInt("collection_rate")}%", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        }
        if (!p.isNull("progress")) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Site work", color = Muted, fontSize = 11.sp, modifier = Modifier.width(62.dp)); Box(Modifier.weight(1f)) { Progress(p.optInt("progress"), Booked) }
            Text(" ${p.optInt("progress")}%", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun SiteCard(s: JSONObject, onMore: () -> Unit) {
    EstateCard("Construction", "Site work, purchasing and spend", action = "Details" to onMore) {
        if (s.has("tasks")) {
            Row { Text("Work done", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("${s.optInt("progress")}%  ·  ${s.optInt("tasks_done")} of ${s.optInt("tasks")} tasks", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Progress(s.optInt("progress"), Booked)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.has("tasks_overdue")) Stat("${s.optInt("tasks_overdue")}", "Overdue tasks", if (s.optInt("tasks_overdue") > 0) Red else Muted, Modifier.weight(1f))
            if (s.has("open_indents")) Stat("${s.optInt("open_indents")}", "Open indents", Booked, Modifier.weight(1f))
            if (s.has("po_value")) Stat(inr(s.optDouble("po_value")), "Purchase orders", Sold, Modifier.weight(1f))
        }
        if (s.has("expenses_month")) Text("Expenses this month: ${inr(s.optDouble("expenses_month"))}", color = Muted, fontSize = 12.sp)
    }
}

/** Sales & buyers: the bookings just made, then the funnel. */
internal fun LazyListScope.estateSales(e: JSONObject) {
    val s = e.optJSONObject("sales") ?: return
    val recent = s.rows("recent")
    item {
        EstateCard("Latest bookings", if (recent.isEmpty()) "No bookings yet" else "${s.optInt("bookings")} in all · ${s.optInt("month_bookings")} this month (${inr(s.optDouble("month_value"))})") {
            recent.forEach { r ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Soft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Sold.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Text("⌂", color = Sold, fontWeight = FontWeight.ExtraBold) }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(r.str("buyer") ?: "Buyer", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(r.str("unit"), r.str("project"), r.str("date")?.let(::whenText)).joinToString(" · "), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(inr(r.optDouble("value")), color = Sold, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
        }
    }
    e.optJSONObject("funnel")?.let { f -> item { FunnelCard(f) } }
}

/** Projects: every project with its units, sales, collections and site progress. */
internal fun LazyListScope.estateProjects(e: JSONObject, onOpenProject: (String) -> Unit) {
    val projects = e.rows("projects")
    if (projects.isEmpty()) { item { Text("No projects to show.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp)) }; return }
    item { Text("${projects.size} projects · busiest first", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
    projects.forEach { p -> item { Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) { ProjectRow(p) { onOpenProject(p.optString("uuid")) } } } }
}

/** "2026-11-03 10:00" → "Today 10:00", "Tomorrow", "3 Nov". */
private fun whenText(s: String): String = runCatching {
    val d = java.time.LocalDate.parse(s.take(10)); val today = java.time.LocalDate.now()
    val time = s.drop(11).take(5).takeIf { it.length == 5 && it != "00:00" }?.let { " $it" } ?: ""
    when (d) { today -> "Today$time"; today.plusDays(1) -> "Tomorrow$time"; today.minusDays(1) -> "Yesterday"; else -> d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) + time }
}.getOrDefault(s)

// ───────────────────────────── Finance ─────────────────────────────

/**
 * Finance: cash in hand, this month's money in and out, six months of it, what buyers still owe (overdue first), what is owed to
 * suppliers, and where the money went. [w] is the website's dashboard data, for the all-time totals.
 */
internal fun LazyListScope.estateFinance(e: JSONObject, w: JSONObject) {
    val f = e.optJSONObject("finance")
    val acct = w.optJSONObject("acctStats")
    f?.let { x ->
        item {
            EstateCard("Cash in hand", "Cash and bank together") {
                Text(inr(x.optDouble("cash") + x.optDouble("bank")), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(inr(x.optDouble("cash")), "Cash", Avail, Modifier.weight(1f))
                    Stat(inr(x.optDouble("bank")), "Bank", Sold, Modifier.weight(1f))
                    Stat("${x.optInt("cheques_open")}", "Open cheques", Booked, Modifier.weight(1f))
                }
            }
        }
        item {
            val net = x.optDouble("net_month")
            EstateCard("This month", "Money in and out since the 1st") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(inr(x.optDouble("in_month")), "Money in", Avail, Modifier.weight(1f))
                    Stat(inr(x.optDouble("out_month")), "Money out", Red, Modifier.weight(1f))
                    Stat((if (net < 0) "−" else "+") + inr(kotlin.math.abs(net)), "Net", if (net < 0) Red else Avail, Modifier.weight(1f))
                }
            }
        }
        val flow = x.rows("flow")
        if (flow.any { it.optDouble("in") > 0 || it.optDouble("out") > 0 }) item {
            val top = flow.maxOf { maxOf(it.optDouble("in"), it.optDouble("out")) }.coerceAtLeast(1.0)
            EstateCard("Last six months", "Buyer payments and receipts against expenses and payments out") {
                Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    flow.forEach { m ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.height(104.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.width(11.dp).height((100 * m.optDouble("in") / top).coerceAtLeast(2.0).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Avail))
                                Box(Modifier.width(11.dp).height((100 * m.optDouble("out") / top).coerceAtLeast(2.0).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Red))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(m.optString("month"), color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { Legend("In", inr(x.optDouble("in_6m")), Avail); Legend("Out", inr(x.optDouble("out_6m")), Red) }
            }
        }
        x.optJSONObject("receivable")?.let { r ->
            val sales = e.optJSONObject("sales")
            item {
                EstateCard("Buyers owe you", "${r.optInt("count")} instalments pending") {
                    sales?.let { s ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(inr(s.optDouble("due")), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            Text("${s.optInt("collection_rate")}% collected", color = Avail, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Progress(s.optInt("collection_rate"), Avail)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(inr(r.optDouble("overdue")), "Overdue", if (r.optDouble("overdue") > 0) Red else Muted, Modifier.weight(1f), "${r.optInt("overdue_count")} instalments")
                        Stat(inr(r.optDouble("soon")), "Due in 30 days", Booked, Modifier.weight(1f))
                        if (r.optDouble("undated") > 0) Stat(inr(r.optDouble("undated")), "No due date", Muted, Modifier.weight(1f))
                    }
                }
            }
        }
        x.optJSONObject("payable")?.let { p ->
            item {
                EstateCard("You owe suppliers", "Supplier invoices not yet paid") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(inr(p.optDouble("amount")), "Still to pay", if (p.optDouble("amount") > 0) Red else Muted, Modifier.weight(1f), "${p.optInt("count")} invoices")
                        Stat("${p.optInt("awaiting_approval")}", "Waiting for approval", Booked, Modifier.weight(1f))
                    }
                }
            }
        }
        val spend = x.rows("spend")
        item {
            EstateCard("Where the money went", if (spend.isEmpty()) "No expenses in the last 3 months" else "Expenses, last 3 months · ${inr(x.optDouble("spend_total"))}") {
                val top = spend.maxOfOrNull { it.optDouble("amount") }?.coerceAtLeast(1.0) ?: 1.0
                spend.forEachIndexed { i, c ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row { Text(c.optString("name"), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(inr(c.optDouble("amount")), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
                        Progress((100 * c.optDouble("amount") / top).toInt(), listOf(Red, Booked, Sold, Avail, Muted, Ink)[i % 6])
                    }
                }
            }
        }
    }
    acct?.let { a ->
        item {
            EstateCard("All-time totals", "Across all projects") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(inr(a.optDouble("grossSales")), "Gross sales", Sold, Modifier.weight(1f))
                    Stat(inr(a.optDouble("received")), "Received", Avail, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(inr(a.optDouble("purchases")), "Purchases", Booked, Modifier.weight(1f))
                    Stat(inr(a.optDouble("expensesMonth")), "Expenses this month", Red, Modifier.weight(1f))
                }
            }
        }
    }
}

// ───────────────────────────── People ─────────────────────────────

private fun initials(name: String) = name.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1) }.uppercase()

/**
 * People: the team at a glance, who is in today (staff and site workers), who is on leave, what waits for approval, pay this
 * month, the departments, and files & chat activity. [w] is the website's dashboard data, for labour and files.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.estatePeople(e: JSONObject, w: JSONObject) {
    val p = e.optJSONObject("people")
    val labour = w.optJSONObject("labourStats")
    val files = w.optJSONObject("fileStats")
    val staff = p?.optJSONObject("staff")
    val workers = p?.optJSONObject("workers_today")
    if (p != null || labour != null) item {
        EstateCard("Your team", "Everyone who builds and sells your projects") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p?.has("users") == true) Stat("${p.optInt("users")}", "App users", Sold, Modifier.weight(1f))
                staff?.let { Stat("${it.optInt("total")}", "Staff", Avail, Modifier.weight(1f)) }
                (workers?.optInt("total") ?: labour?.optInt("workers"))?.let { Stat("$it", "Site workers", Booked, Modifier.weight(1f)) }
            }
            val roles = p?.rows("roles").orEmpty()
            if (roles.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                roles.forEach { r ->
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Soft).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.optString("role"), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("  ${r.optInt("count")}", color = Sold, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
    if (staff != null || workers != null) item {
        EstateCard("Today", "Who is in") {
            staff?.let { s ->
                Row { Text("Office staff", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${s.optInt("in")} of ${s.optInt("total")} checked in", color = Muted, fontSize = 12.sp) }
                SplitBar(listOf(s.optInt("in") to Avail, s.optInt("not_in") to com.example.gavanhomes.ui.common.KitLine), height = 10)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend("In", "${s.optInt("in")}", Avail); Legend("Not in yet", "${s.optInt("not_in")}", Muted)
                    if (s.optInt("late") > 0) Legend("Late", "${s.optInt("late")}", Red)
                    if (s.optInt("wfh") > 0) Legend("Working from home", "${s.optInt("wfh")}", Sold)
                }
            }
            workers?.let { x ->
                val marked = x.optInt("present") + x.optInt("half_day") + x.optInt("absent")
                Row { Text("Site workers", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(if (marked == 0) "attendance not marked yet" else "$marked of ${x.optInt("total")} marked", color = Muted, fontSize = 12.sp) }
                SplitBar(listOf(x.optInt("present") to Avail, x.optInt("half_day") to Booked, x.optInt("absent") to Red, (x.optInt("total") - marked).coerceAtLeast(0) to com.example.gavanhomes.ui.common.KitLine), height = 10)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) { Legend("Present", "${x.optInt("present")}", Avail); Legend("Half day", "${x.optInt("half_day")}", Booked); Legend("Absent", "${x.optInt("absent")}", Red) }
            }
        }
    }
    if (p?.has("leave_today") == true) {
        val leave = p.rows("leave_today")
        item {
            EstateCard("On leave today", if (leave.isEmpty()) "Nobody — everyone is expected in" else "${leave.size} away") {
                leave.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Booked.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                            Text(initials(l.optString("name")), color = Booked, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(l.optString("name"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(listOfNotNull(l.str("type")?.replaceFirstChar { c -> c.uppercase() }, l.str("until")?.let { "back after " + whenText(it) }).joinToString(" · "), color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
    p?.optJSONObject("approvals")?.let { a ->
        val total = a.optInt("leaves") + a.optInt("requests")
        item {
            EstateCard("Waiting for approval", if (total == 0) "Nothing waiting" else "$total to review in HR & Admin") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${a.optInt("leaves")}", "Leave requests", if (a.optInt("leaves") > 0) Booked else Muted, Modifier.weight(1f))
                    Stat("${a.optInt("requests")}", "Other requests", if (a.optInt("requests") > 0) Booked else Muted, Modifier.weight(1f))
                }
            }
        }
    }
    if (p?.has("payroll") == true || labour != null) item {
        EstateCard("Pay this month", "Staff payroll and site wages") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p?.optJSONObject("payroll")?.let { pr -> Stat(inr(pr.optDouble("month")), "Staff payroll", Sold, Modifier.weight(1f), "${pr.optInt("paid")} of ${pr.optInt("slips")} slips paid") }
                labour?.let { l -> Stat(inr(l.optDouble("wagesMonth")), "Site wages", Booked, Modifier.weight(1f), if (l.optDouble("unpaid") > 0) "${inr(l.optDouble("unpaid"))} unpaid" else "nothing unpaid") }
            }
        }
    }
    val depts = staff?.rows("departments").orEmpty()
    if (depts.isNotEmpty()) item {
        EstateCard("Departments", "Staff in each") {
            val top = depts.maxOf { it.optInt("count") }.coerceAtLeast(1)
            depts.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.optString("name").lowercase().replaceFirstChar { it.uppercase() }, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.weight(1f)) { Progress(100 * d.optInt("count") / top, Sold) }
                    Text("${d.optInt("count")}", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
                }
            }
        }
    }
    files?.let { x ->
        item {
            EstateCard("Documents & chat", "Project files and team discussion") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${x.optInt("files")}", "Files", Sold, Modifier.weight(1f), "%.1f MB".format(x.optDouble("size") / 1_048_576))
                    Stat("${x.optInt("uploadsMonth")}", "Uploaded this month", Avail, Modifier.weight(1f))
                    Stat("${x.optInt("messagesWeek")}", "Messages this week", Booked, Modifier.weight(1f))
                }
                if (x.optInt("accessRequests") > 0) Text("🔒 ${x.optInt("accessRequests")} file access request(s) waiting", color = Booked, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ───────────────────────────── shared pieces for the Sales and Site tabs ─────────────────────────────

private fun JSONObject.nameOf(key: String): String? = optJSONObject(key)?.str("name")
private fun shortDay(s: String?): String? = s?.takeIf { it.length >= 10 }?.let { whenText(it.take(10)) }

/** Counts by name as horizontal bars in one hue, the count printed at the end of each — biggest first. */
@Composable
private fun BarList(counts: JSONObject, color: Color, limit: Int = 8) {
    val rows = counts.keys().asSequence().map { it to counts.optInt(it) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).toList()
    if (rows.isEmpty()) { Text("Nothing yet.", color = Muted, fontSize = 13.sp); return }
    val top = rows.maxOf { it.second }.coerceAtLeast(1)
    rows.forEach { (l, n) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.replace('_', ' ').replaceFirstChar { it.uppercase() }, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(112.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Grid)) {
                Box(Modifier.fillMaxWidth(n.toFloat() / top).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
            }
            Text("$n", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        }
    }
}

/** One line of a short list: a title, a muted line under it, and a tag on the right. */
@Composable
private fun ListLine(title: String, sub: String?, tag: String?, tagColor: Color = Muted) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Soft).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        tag?.let { Text(it, color = tagColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).background(tagColor.copy(alpha = .1f)).padding(horizontal = 7.dp, vertical = 3.dp)) }
    }
}

/** A status name's colour: done green, trouble red, waiting amber, anything else blue. */
private fun statusTint(s: String?): Color {
    val n = s?.lowercase() ?: return Muted
    return when {
        listOf("complete", "done", "approved", "delivered", "received", "active", "won", "paid").any { it in n } -> Avail
        listOf("overdue", "late", "cancel", "reject", "out", "lost", "failed").any { it in n } -> Red
        listOf("pending", "draft", "low", "due", "waiting").any { it in n } -> Booked
        else -> Sold
    }
}

// ───────────────────────────── Sales & buyers: the rest of the selling side ─────────────────────────────

/** Leads by stage and by source, follow-ups due, the latest leads, the next site visits and customer care — from the website's dashboard data. */
internal fun LazyListScope.estateSalesMore(w: JSONObject) {
    w.optJSONObject("leadStats")?.let { ls ->
        ls.optJSONObject("by_status")?.let { s -> item { EstateCard("Lead pipeline", "${ls.optInt("total")} leads by stage") { BarList(s, CSold) } } }
    }
    w.optJSONObject("leadSources")?.let { s -> item { EstateCard("Where leads come from", "By source") { BarList(s, CBooked) } } }
    w.optJSONObject("followupStats")?.let { fs ->
        val rows = w.rows("overdueFollowups").map { it to true } + w.rows("upcomingFollowups").map { it to false }
        item {
            EstateCard("Follow-ups", "${fs.optInt("pending")} pending · ${fs.optInt("overdue")} overdue") {
                if (rows.isEmpty()) Text("No follow-ups scheduled.", color = Muted, fontSize = 13.sp)
                rows.take(6).forEach { (f, late) ->
                    ListLine(f.optJSONObject("lead")?.str("client_name") ?: "Lead", (f.str("followup_type") ?: "Follow-up").replaceFirstChar { it.uppercase() } + (shortDay(f.str("next_followup_at"))?.let { " · $it" } ?: ""),
                        if (late) "Late" else "Due", if (late) Red else Booked)
                }
            }
        }
    }
    val leads = w.rows("recentLeads")
    if (leads.isNotEmpty()) item {
        EstateCard("Latest leads", "Newest enquiries") {
            leads.take(5).forEach { l -> ListLine(l.str("client_name") ?: "Lead", listOfNotNull(l.str("phone_number"), l.optJSONObject("assignee")?.str("name") ?: "Unassigned").joinToString(" · "), l.str("status"), statusTint(l.str("status"))) }
        }
    }
    val visits = w.rows("upcomingVisits")
    w.optJSONObject("careStats")?.let { c ->
        item {
            EstateCard("Customer care", "Site visits and open issues") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${c.optInt("upcomingVisits")}", "Visits coming up", Sold, Modifier.weight(1f))
                    Stat("${c.optInt("visitsMonth")}", "Visits this month", Avail, Modifier.weight(1f))
                    Stat("${c.optInt("openIssues")}", "Open issues", if (c.optInt("openIssues") > 0) Red else Muted, Modifier.weight(1f))
                }
                visits.take(4).forEach { v -> ListLine(v.str("customer") ?: "Visitor", (v.str("visit_type") ?: "Visit").replaceFirstChar { it.uppercase() } + (shortDay(v.str("visit_at"))?.let { " · $it" } ?: ""), v.str("status")?.replaceFirstChar { it.uppercase() }, statusTint(v.str("status"))) }
            }
        }
    }
}

// ───────────────────────────── Site & stock ─────────────────────────────

/**
 * Site & stock: building the projects — task progress as a donut, targets, what is due this week, the team's workload, each
 * project's completion — then materials, purchasing, tenders and equipment. From the website's dashboard data.
 */
internal fun LazyListScope.estateSite(w: JSONObject, onOpenProject: (String) -> Unit) {
    w.optJSONObject("taskStats")?.let { t ->
        item {
            val done = t.optInt("completed"); val overdue = t.optInt("overdue"); val pending = t.optInt("pending")
            val open = (t.optInt("total") - done - overdue - pending - t.optInt("cancelled")).coerceAtLeast(0)
            EstateCard("Site work", "${t.optInt("total")} tasks · tap a slice") {
                Donut(listOf(Triple("Completed", done, CAvail), Triple("In progress", open, CSold), Triple("Pending", pending, CBooked), Triple("Overdue", overdue, Red)), "${t.optInt("completion")}%", "done")
            }
        }
    }
    w.optJSONObject("targetStats")?.let { t ->
        item {
            EstateCard("Milestones & targets", "${t.optInt("total")} targets") {
                Row { Text("Achieved", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("${t.optInt("completion")}%  ·  ${t.optInt("completed")} of ${t.optInt("total")}", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Progress(t.optInt("completion"), CAvail)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${t.optInt("pending")}", "Pending", Booked, Modifier.weight(1f))
                    Stat("${t.optInt("overdue")}", "Overdue", if (t.optInt("overdue") > 0) Red else Muted, Modifier.weight(1f))
                }
            }
        }
    }
    val due = (w.rows("dueSoonTasks").map { it to "Task" } + w.rows("dueSoonTargets").map { it to "Target" }).filter { it.first.str("due_date") != null }.sortedBy { it.first.str("due_date") }.take(6)
    if (due.isNotEmpty()) item {
        EstateCard("Due this week", "Tasks and targets due in the next 7 days") {
            due.forEach { (o, kind) -> ListLine(o.str("name") ?: kind, "$kind · ${o.nameOf("project") ?: "—"}", shortDay(o.str("due_date")), Booked) }
        }
    }
    val workload = w.rows("workload")
    if (workload.isNotEmpty()) item {
        EstateCard("Team workload", "Open and finished tasks per person") {
            val top = workload.maxOf { it.optInt("open") + it.optInt("completed") }.coerceAtLeast(1)
            workload.take(8).forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.str("name") ?: "—", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(104.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth((p.optInt("open") + p.optInt("completed")).toFloat() / top).height(14.dp).clip(RoundedCornerShape(4.dp))) {
                            if (p.optInt("completed") > 0) Box(Modifier.weight(p.optInt("completed").toFloat()).fillMaxHeight().background(CAvail))
                            if (p.optInt("open") > 0) Box(Modifier.weight(p.optInt("open").toFloat()).fillMaxHeight().background(CSold))
                        }
                    }
                    Text(if (p.optInt("overdue") > 0) "${p.optInt("overdue")} late" else "${p.optInt("open")} open", color = if (p.optInt("overdue") > 0) Red else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { Legend("Finished", "", CAvail); Legend("Open", "", CSold) }
        }
    }
    val projects = w.rows("projects")
    if (projects.isNotEmpty()) item {
        EstateCard("Project completion", "Share of each project's tasks finished") {
            projects.forEach { p ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row { Text(p.str("name") ?: "Project", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${p.optInt("completion")}%", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
                    Progress(p.optInt("completion"), CSold)
                    Text("${p.optInt("completed_tasks")} of ${p.optInt("total_tasks")} tasks", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
    w.optJSONObject("stockStats")?.let { s ->
        val alerts = w.rows("stockAlerts")
        item {
            EstateCard("Materials", "Stock across your projects") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${s.optInt("materials")}", "Materials", Sold, Modifier.weight(1f))
                    Stat("${s.optInt("low")}", "Low", if (s.optInt("low") > 0) Booked else Muted, Modifier.weight(1f))
                    Stat("${s.optInt("out")}", "Out", if (s.optInt("out") > 0) Red else Muted, Modifier.weight(1f))
                }
                Text("Stock value ${inr(s.optDouble("value"))} · ${s.optInt("movesMonth")} movements this month", color = Muted, fontSize = 12.sp)
                alerts.take(5).forEach { a -> ListLine(a.str("name") ?: "Material", listOfNotNull(a.str("project"), "balance ${a.optDouble("balance")}").joinToString(" · "), if (a.str("status") == "out") "Out" else "Low", if (a.str("status") == "out") Red else Booked) }
            }
        }
    }
    if (w.has("procStats") || w.has("poStatusCounts")) item {
        val p = w.optJSONObject("procStats") ?: JSONObject()
        EstateCard("Purchasing", if (w.has("poValue")) "${inr(w.optDouble("poValue"))} ordered in all" else "Orders, deliveries and receipts") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${p.optInt("openIndents")}", "Open indents", Booked, Modifier.weight(1f))
                Stat("${p.optInt("pendingDeliveries")}", "Deliveries due", Sold, Modifier.weight(1f))
                Stat("${p.optInt("grns")}", "Goods received", CAvail, Modifier.weight(1f))
            }
            w.optJSONObject("poStatusCounts")?.let { c -> Text("PURCHASE ORDERS BY STATUS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp); BarList(c, CSold, 6) }
            w.rows("recentPOs").take(4).forEach { po -> ListLine(po.str("po_number") ?: "PO", "${po.nameOf("project") ?: "—"} · ${inr(po.optDouble("total_amount"))}", po.str("status")?.replaceFirstChar { it.uppercase() }, statusTint(po.str("status"))) }
        }
    }
    w.optJSONObject("bidStatusCounts")?.let { c ->
        item {
            EstateCard("Tenders & bids", "Bids by status") {
                BarList(c, CBooked, 6)
                w.rows("recentBids").take(3).forEach { bd -> ListLine(bd.str("title") ?: "Bid", bd.nameOf("project"), bd.str("bid_status")?.replaceFirstChar { it.uppercase() }, statusTint(bd.str("bid_status"))) }
            }
        }
    }
    w.optJSONObject("assetStats")?.let { a ->
        val maint = (w.rows("overdueMaintenances") + w.rows("upcomingMaintenances")).take(4)
        item {
            EstateCard("Equipment & assets", "${a.optInt("total")} assets · ${inr(a.optDouble("value"))}") {
                w.optJSONObject("assetsByCategory")?.let { BarList(it, CSold, 6) }
                if (maint.isNotEmpty()) Text("MAINTENANCE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                maint.forEach { m -> ListLine(m.optJSONObject("asset")?.str("name") ?: "Asset", (m.str("maintenance_type") ?: "Maintenance").replaceFirstChar { it.uppercase() }, shortDay(m.str("next_due_date")), Booked) }
            }
        }
    }
}
