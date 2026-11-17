package com.example.gavanhomes.ui.tasks

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
import org.json.JSONObject

private val Amber = Color(0xFFC47A1A)

private fun plural(n: Int, one: String, many: String = one + "s") = "$n " + if (n == 1) one else many

/**
 * The Tasks dashboard: four tap-able numbers (Overdue, Due today, This week, Done today) that open the list; the overdue and due-today
 * tasks with their owner and how late; the team's workload; tasks by customer-journey step; then the overall completion and priorities.
 * Overdue is worked out by the server from each task's due date and time — it is never a status someone picks.
 */
@Composable
internal fun TaskDashboard(o: JSONObject, canCreate: Boolean, onNew: () -> Unit, onOpen: (Int) -> Unit, onQuick: (String) -> Unit) {
    val c = o.optJSONObject("counts")
    val numbers = listOf(
        Triple("Overdue", "overdue", KitDanger), Triple("Due today", "due_today_open", Amber), Triple("This week", "this_week", KitInk), Triple("Done today", "done_today", KitGood),
    )
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp).fillMaxWidth().softShadow(5.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(vertical = 14.dp, horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            numbers.forEach { (label, key, tint) ->
                val n = c?.optInt(if (key == "due_today_open") "due_today" else key) ?: 0
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onQuick(key) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = if (n == 0 && key == "overdue") KitInk else tint)
                    Text(label, fontSize = 11.sp, color = KitMuted, maxLines = 1)
                }
            }
        }
        Text("Tap any number to open the list", color = KitMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }

    // overdue first, then what is due today
    val attention = o.objs("attention")
    Card("Overdue & due today", if (attention.isEmpty()) "Nothing is late or due today" else "${attention.size} task${if (attention.size == 1) "" else "s"} need attention") {
        if (attention.isEmpty()) Text("You're all caught up. Tasks that pass their due date and time show here in red.", color = KitMuted, fontSize = 13.sp)
        attention.forEach { t ->
            val late = t.optBoolean("overdue")
            val owner = t.names("assignees").firstOrNull()
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).clickable { onOpen(t.optInt("id")) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(t.str("where"), t.str("step_name")?.let { "Step: $it" }).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(com.example.gavanhomes.ui.common.KitLine)) {
                        Box(Modifier.fillMaxWidth((t.optInt("progress") / 100f).coerceIn(0.02f, 1f)).height(4.dp).background(if (late) KitDanger else Amber))
                    }
                }
                Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.End) {
                    if (owner != null) Box(Modifier.size(26.dp).clip(CircleShape).background(com.example.gavanhomes.ui.common.KitInkFill), contentAlignment = Alignment.Center) { Text(owner.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
                    Text(if (late) t.str("late") ?: "Overdue" else "Today" + (t.str("due_time")?.let { " $it" } ?: ""), color = if (late) KitDanger else Amber, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }

    // each person: open, overdue, done this week
    val load = o.objs("workload")
    if (load.isNotEmpty()) Card("Team workload", "Open, overdue and done this week, per person") {
        Row(Modifier.fillMaxWidth()) {
            Spacer1(Modifier.weight(1f)); listOf("Open", "Overdue", "Done wk").forEach { Text(it, color = KitMuted, fontSize = 10.sp, textAlign = TextAlign.End, modifier = Modifier.width(54.dp)) }
        }
        load.take(10).forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(com.example.gavanhomes.ui.common.KitInkFill), contentAlignment = Alignment.Center) { Text(p.optString("name").take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
                Text(p.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(p.optInt("open").toString(), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(54.dp))
                Text(p.optInt("overdue").toString(), color = if (p.optInt("overdue") > 0) KitDanger else KitMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(54.dp))
                Text(p.optInt("done_week").toString(), color = KitGood, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(54.dp))
            }
        }
    }

    // open tasks on each step of the customer journey
    val steps = o.objs("by_step")
    if (steps.isNotEmpty()) Card("By customer journey step", "Open tasks on each step, Lead to Possession") {
        val top = steps.maxOf { it.optInt("count") }.coerceAtLeast(1)
        steps.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(com.example.gavanhomes.ui.common.KitLine)) {
                    Box(Modifier.fillMaxWidth(s.optInt("count") / top.toFloat()).height(8.dp).background(KitGood))
                }
                Text(s.optInt("count").toString(), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
            }
        }
    }

    // overall completion, with the urgent / high counts apart
    val rate = o.optInt("completion_rate")
    val urgent = o.optInt("urgent_open"); val high = o.optInt("high_open")
    val sub = when {
        urgent == 0 && high == 0 -> "No urgent or high-priority task open"
        else -> listOfNotNull(if (urgent > 0) "${plural(urgent, "urgent task")} open" else null, if (high > 0) "${plural(high, "high-priority task")} open" else null).joinToString(" · ")
    }
    Card("Completion", sub) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rate%", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = KitGood)
            Text("of all tasks completed", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }
        Meter(rate / 100f, KitGood)
        Text(listOf("Total" to o.optInt("total")).plus(o.objs("statuses").map { it.optString("name") to it.optInt("count") }).joinToString("  ·  ") { "${it.first} ${it.second}" }, color = KitMuted, fontSize = 12.sp)
    }

    // priorities
    Card("By priority", "Tasks per priority and how many are done") {
        o.objs("priorities").chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    val tint = priorityTint(p.optString("priority"))
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .09f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(p.optString("priority"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tint)
                        Text(p.optInt("total").toString(), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = KitInk)
                        Meter(p.optInt("rate") / 100f, tint)
                        Text("${p.optInt("completed")} done · ${p.optInt("overdue")} overdue", fontSize = 11.sp, color = KitMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer1(m: Modifier) { Box(m) }

/** A white rounded card with no title (the owner card of a task). */
@Composable
internal fun PlainCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().softShadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}
