package com.example.gavanhomes.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

/** Series colours in the reference categorical order — blue, orange, aqua — checked with the palette validator (all pairs pass, colour-blind too). */
private val SeriesColors = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A))

/** ₹ in lakh / crore, a quantity, or a count — whichever the chart's unit is. */
private fun formatFor(unit: String): (Double) -> String = when (unit) {
    "money" -> { v -> "₹" + when { v >= 1e7 -> "%.2f Cr".format(v / 1e7); v >= 1e5 -> "%.1f L".format(v / 1e5); v >= 1e3 -> "%.1f K".format(v / 1e3); else -> v.roundToInt().toString() } }
    "qty" -> { v -> if (v >= 1e3) "%.1f K".format(v / 1e3) else if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v) }
    else -> { v -> v.roundToInt().toString() }
}

/**
 * The trend card at the top of a project module's dashboard: six months of that module's activity as lines on one axis, with the
 * total for each series. Data: GET /projects/{uuid}/trends/{module}. Draws nothing until it has loaded (or if it can't load).
 */
@Composable
fun ModuleTrend(api: ApiClient, uuid: String, module: String, version: Int = 0) {
    var data by remember(module) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(module, version) { data = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/trends/$module") } }.getOrNull() }
    val d = data ?: return
    val months = d.optJSONArray("months")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    val series = d.optJSONArray("series")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        .mapIndexed { i, s -> Triple(s.optString("name"), s.optJSONArray("data")?.let { a -> (0 until a.length()).map { a.optDouble(it, 0.0) } }.orEmpty(), SeriesColors[i % SeriesColors.size]) }
    if (months.isEmpty() || series.isEmpty()) return
    val fmt = formatFor(d.optString("unit"))
    Column(
        Modifier.appear().padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(KitCard).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text(d.optString("title"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(d.optString("sub") + " · last six months", color = KitMuted, fontSize = 12.sp)
        }
        if (series.all { s -> s.second.all { it == 0.0 } }) Text("Nothing recorded in the last six months yet.", color = KitMuted, fontSize = 13.sp)
        else TrendLines(months, series, fmt)
        // the legend: each series with its six-month total — identity is never colour alone
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            series.forEach { (name, v, c) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                    Text(" $name ", color = KitMuted, fontSize = 12.sp)
                    Text(fmt(v.sum()), color = KitInk, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * Lines on one axis: 2-px lines with 8-px markers, three recessive gridlines with their values, months along the bottom. Tap
 * anywhere to pick the nearest month — a guide line and that month's figures appear above the chart.
 */
@Composable
fun TrendLines(labels: List<String>, series: List<Triple<String, List<Double>, Color>>, format: (Double) -> String) {
    var picked by remember(labels) { mutableStateOf<Int?>(null) }
    val top = series.flatMap { it.second }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val n = labels.size
    val grid = KitLine; val muted = KitMuted; val surface = KitCard
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            val i = picked
            if (i == null) Text("Tap the chart to see a month", color = KitMuted, fontSize = 11.sp)
            else {
                Text(labels[i], color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                series.forEach { (name, v, c) -> Spacer(Modifier.width(10.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Text(" $name ${format(v[i])}", color = KitInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
        Row {
            Column(Modifier.height(130.dp).width(50.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(top, top / 2, 0.0).forEach { Text(format(it), color = KitMuted, fontSize = 9.sp, maxLines = 1) }
            }
            Canvas(
                Modifier.weight(1f).height(130.dp).pointerInput(labels) {
                    detectTapGestures { o -> if (n > 1) picked = ((o.x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1).let { if (it == picked) null else it } }
                },
            ) {
                val w = size.width; val h = size.height; val pad = 6.dp.toPx()
                val x = { i: Int -> if (n == 1) w / 2 else pad + (w - 2 * pad) * i / (n - 1) }
                val y = { v: Double -> (h - pad) - ((h - 2 * pad) * (v / top)).toFloat() }
                listOf(0.0, top / 2, top).forEach { g -> drawLine(grid, Offset(0f, y(g)), Offset(w, y(g)), 1.dp.toPx()) }
                picked?.let { i -> drawLine(muted.copy(alpha = .5f), Offset(x(i), 0f), Offset(x(i), h), 1.dp.toPx()) }
                series.forEach { (_, v, c) ->
                    val path = Path()
                    v.forEachIndexed { i, value -> if (i == 0) path.moveTo(x(i), y(value)) else path.lineTo(x(i), y(value)) }
                    val fill = Path().apply { addPath(path); lineTo(x(v.lastIndex), h - pad); lineTo(x(0), h - pad); close() }
                    drawPath(fill, c.copy(alpha = .08f))
                    drawPath(path, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    v.forEachIndexed { i, value ->
                        val r = if (picked == i) 5.dp.toPx() else 4.dp.toPx()
                        drawCircle(surface, r + 2.dp.toPx(), Offset(x(i), y(value)))     // a surface ring where markers overlap
                        drawCircle(c, r, Offset(x(i), y(value)))
                    }
                }
            }
        }
        Row(Modifier.padding(start = 50.dp)) { labels.forEach { Text(it, color = KitMuted, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) } }
    }
}
