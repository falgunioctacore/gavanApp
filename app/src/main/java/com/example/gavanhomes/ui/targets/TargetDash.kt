package com.example.gavanhomes.ui.targets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.softShadow
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Meter
import com.example.gavanhomes.ui.tasks.names
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import com.example.gavanhomes.ui.tasks.priorityTint
import org.json.JSONObject
import java.util.Locale

private val Amber = Color(0xFFC47A1A)
private val Blue = Color(0xFF1E5FAE)

/** 12,50,00,000 → "₹12.5 Cr", 4,50,000 → "₹4.5 L" — short enough for a card. */
private fun short(v: Double): String = when {
    v >= 1e7 -> "₹" + trim(v / 1e7) + " Cr"
    v >= 1e5 -> "₹" + trim(v / 1e5) + " L"
    v >= 1e3 -> "₹" + trim(v / 1e3) + " K"
    else -> "₹" + trim(v)
}
private fun trim(d: Double) = String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

private fun healthWord(h: String?) = when (h) { "on_track" -> "On track"; "behind" -> "Behind"; "at_risk" -> "At risk"; "achieved" -> "Achieved"; else -> null }
private fun healthTint(h: String?) = when (h) { "on_track", "achieved" -> KitGood; "behind" -> Amber; "at_risk" -> KitDanger; else -> KitMuted }

/**
 * The Targets dashboard: four tap-able numbers (Overdue, Due today, This week, Done this week) that open the list; the targets that need
 * attention (late, behind or due this week) with how far along each is; how many are on track / behind / at risk; each person's load;
 * then completion, priority and type.
 */
@Composable
internal fun TargetDashboard(o: JSONObject, canCreate: Boolean, onNew: () -> Unit, onOpen: (Int) -> Unit, onQuick: (String) -> Unit) {
    val q = o.optJSONObject("quick")
    val numbers = listOf(
        Triple("Overdue", "overdue", KitDanger) to (q?.optInt("overdue") ?: 0), Triple("Due today", "today", Amber) to (q?.optInt("today") ?: 0),
        Triple("This week", "this_week", KitInk) to (q?.optInt("this_week") ?: 0), Triple("Done this week", "done_week", KitGood) to o.optInt("done_week"),
    )
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp).fillMaxWidth().softShadow(5.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(vertical = 14.dp, horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            numbers.forEach { (t, n) ->
                val (label, key, tint) = t
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onQuick(key) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = if (n == 0 && key == "overdue") KitInk else tint)
                    Text(label, fontSize = 11.sp, color = KitMuted, maxLines = 1)
                }
            }
        }
        Text("Tap any number to open the list", color = KitMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }

    // how the open targets are doing against their goals
    val h = o.optJSONObject("health")
    if (h != null && (h.optInt("on_track") + h.optInt("behind") + h.optInt("at_risk")) > 0) Card("How the targets are doing", "Open targets against their goal and time") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("on_track" to "On track", "behind" to "Behind", "at_risk" to "At risk").forEach { (k, l) ->
                val c = healthTint(k)
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = .1f)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(h.optInt(k).toString(), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = c)
                    Text(l, fontSize = 12.sp, color = KitMuted)
                }
            }
        }
    }

    // late, behind or due this week
    val attention = o.objs("attention")
    Card("Needs attention", if (attention.isEmpty()) "Nothing is late, behind or due this week" else "${attention.size} target${if (attention.size == 1) "" else "s"} — late first") {
        if (attention.isEmpty()) Text("All caught up. Targets that pass their due date, fall behind their pace or fall due this week show here.", color = KitMuted, fontSize = 13.sp)
        attention.forEach { t ->
            val late = t.str("late")
            val goal = t.optDouble("goal", 0.0); val got = t.optDouble("achieved", 0.0)
            val hw = healthWord(t.str("health"))
            val tint = if (late != null) KitDanger else healthTint(t.str("health")).takeIf { t.str("health") != null } ?: Amber
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).clickable { onOpen(t.optInt("id")) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(t.str("category"), t.names("assignees").firstOrNull(), if (goal > 0) "${short(got)} of ${short(goal)}" else null).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(com.example.gavanhomes.ui.common.KitLine)) {
                        Box(Modifier.fillMaxWidth((t.optInt("pct") / 100f).coerceIn(0.02f, 1f)).height(4.dp).background(tint))
                    }
                }
                Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.End) {
                    Text(late ?: (prettyDay(t.str("due_date"))?.let { "Due $it" } ?: "No due date"), color = tint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(if (goal > 0) "${t.optInt("pct")}%" + (hw?.let { " · $it" } ?: "") else "${t.optInt("progress")}%", color = KitMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }

    // each person: open targets, how many are behind, and how much of their goals is reached
    val load = o.objs("workload")
    if (load.isNotEmpty()) Card("Team workload", "Open targets and how far along each person is") {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)); listOf("Open", "Behind", "Done").forEach { Text(it, color = KitMuted, fontSize = 10.sp, textAlign = TextAlign.End, modifier = Modifier.width(54.dp)) }
        }
        load.forEach { p ->
            val goal = p.optDouble("goal", 0.0); val got = p.optDouble("achieved", 0.0)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(com.example.gavanhomes.ui.common.KitInkFill), contentAlignment = Alignment.Center) { Text(p.optString("name").take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
                Text(p.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(p.optInt("open").toString(), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(54.dp))
                Text(p.optInt("behind").toString(), color = if (p.optInt("behind") > 0) KitDanger else KitMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(54.dp))
                Text(if (goal > 0) "${(got / goal * 100).toInt().coerceAtMost(100)}%" else "—", color = KitGood, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(54.dp))
            }
        }
    }

    // overall completion
    val rate = o.optInt("completion_rate"); val high = o.optInt("pending_high")
    Card("Completion", if (high == 0) "No high-priority target open" else "$high high-priority target${if (high == 1) "" else "s"} still open") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rate%", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = KitGood)
            Text("of all targets achieved", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }
        Meter(rate / 100f, KitGood)
        Text(listOf("Total" to o.optInt("total")).plus(o.objs("statuses").filter { it.optString("name") != "Overdue" }.map { it.optString("name") to it.optInt("count") }).joinToString("  ·  ") { "${it.first} ${it.second}" }, color = KitMuted, fontSize = 12.sp)
    }

    Card("By priority", "Targets per priority and how many are done") {
        o.objs("priorities").chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    val c = priorityTint(p.optString("priority"))
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = .09f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(p.optString("priority"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c)
                        Text(p.optInt("total").toString(), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = KitInk)
                        Meter(p.optInt("rate") / 100f, c)
                        Text("${p.optInt("completed")} done · ${p.optInt("overdue")} overdue", fontSize = 11.sp, color = KitMuted)
                    }
                }
            }
        }
    }

    val types = o.objs("types")
    if (types.any { it.optInt("count") > 0 }) Card("By type", "Monthly, quarterly and yearly targets") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            types.forEach { t ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Blue.copy(alpha = .09f)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t.optInt("count").toString(), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Blue)
                    Text(t.optString("type"), fontSize = 12.sp, color = KitMuted)
                }
            }
        }
    }
}
