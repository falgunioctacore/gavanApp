package com.example.gavanhomes.ui.attendance

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.FabAction
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.ModulePages
import com.example.gavanhomes.ui.common.ProvideFab
import com.example.gavanhomes.ui.inventory.FilteredList
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.inventory.shortRupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** present · half_day · absent (null: not marked) as a label and colour. */
internal fun markStyle(s: String?): Pair<String, Color> = when (s) {
    "present" -> "Present" to KitGood
    "half_day" -> "Half day" to InvAmber
    "absent" -> "Absent" to KitDanger
    else -> "Not marked" to KitMuted
}

private class Page(val key: String, val label: String)

/**
 * The project's Attendance module, like the website's Attendance tab: the Day sheet (mark each worker present / half day / absent,
 * overtime, "mark the rest present"), the Month view and each worker's record (wages, payouts, advances). The + button adds a worker.
 * Data: /projects/{uuid}/attendance-center/…  [onOpen] opens a worker; [onNew] adds one.
 */
@Composable
fun ProjectAttendance(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit, onNew: () -> Unit) {
    var view by remember { mutableStateOf("day") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var head by remember { mutableStateOf<JSONObject?>(null) }
    var local by remember { mutableIntStateOf(0) }                     // reloads the list too (after a bulk mark)
    var tick by remember { mutableIntStateOf(0) }                      // reloads just the day's numbers (after one mark)
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(version, local, tick, date, attempt) {
        error = null
        try { head = withContext(Dispatchers.IO) { api.get("/projects/$uuid/attendance-center/sheet?date=$date&page=1") } } catch (e: Exception) { error = e.message ?: "Could not load attendance" }
    }
    val h = head
    if (h == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
        }
        return
    }
    val can = h.optJSONObject("can") ?: JSONObject()
    ProvideFab(listOfNotNull(FabAction("worker", "Add worker", KitInk, "☺").takeIf { can.optBoolean("create_worker") })) { onNew() }
    val pages = listOf(Page("day", "Day sheet"), Page("month", "Month"))
    val index = pages.indexOfFirst { it.key == view }.coerceAtLeast(0)
    ModulePages("Attendance", pages.map { it.label }, index) { view = pages[it].key }
    when (pages[index].key) {
        "day" -> DaySheet(api, b, uuid, version + local, date, { date = it }, h, can.optBoolean("mark"), onOpen, onMarked = { tick++ }) { local++ }
        else -> MonthView(api, b, uuid, version + local, onOpen)
    }
}

// ───────────────────────────── day sheet ─────────────────────────────

private val Shown = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

@Composable
private fun DaySheet(api: ApiClient, b: Branding, uuid: String, version: Int, date: LocalDate, setDate: (LocalDate) -> Unit, head: JSONObject, canMark: Boolean, onOpen: (Int) -> Unit, onMarked: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val marks = remember(date, version) { mutableStateMapOf<Int, String>() }     // marked here, before the list reloads
    val ot = remember(date, version) { mutableStateMapOf<Int, Int>() }
    val today = LocalDate.now()

    fun call(ok: String?, run: () -> JSONObject, done: () -> Unit = {}) {
        scope.launch {
            try { val r = withContext(Dispatchers.IO) { run() }; ok?.let { Toast.makeText(context, r.str("message") ?: it, Toast.LENGTH_SHORT).show() }; done() }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
        }
    }

    // the day: ‹ date ›, tap the date to pick one
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = KitInk, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable { setDate(date.minusDays(1)) }.padding(horizontal = 16.dp, vertical = 4.dp))
        Column(Modifier.weight(1f).clickable { android.app.DatePickerDialog(context, { _, y, m, d -> LocalDate.of(y, m + 1, d).let { if (!it.isAfter(today)) setDate(it) } }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show() }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.format(Shown), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(if (date == today) "Today" else "Tap to change", color = KitMuted, fontSize = 11.sp)
        }
        Text("›", color = if (date < today) KitInk else KitMuted.copy(alpha = .4f), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable(enabled = date < today) { setDate(date.plusDays(1)) }.padding(horizontal = 16.dp, vertical = 4.dp))
    }

    val s = head.optJSONObject("stats") ?: JSONObject()
    ProKpiTiles(
        listOf(
            ProKpi(s.optInt("total").toString(), "Workers", KitInk), ProKpi(s.optInt("present").toString(), "Present", KitGood), ProKpi(s.optInt("half_day").toString(), "Half day", InvAmber),
            ProKpi(s.optInt("absent").toString(), "Absent", KitDanger), ProKpi(s.optInt("not_marked").toString(), "Not marked", KitMuted), ProKpi("${s.optInt("overtime")} h", "Overtime", InvBlue),
        ),
        columns = 3,
    )
    if (s.optInt("total") > 0) Card("Wages for the day", "Present + half days + overtime") {
        Text(rupees(s.optDouble("wages", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        val done = s.optInt("total") - s.optInt("not_marked")
        ProBar(done.toFloat() / s.optInt("total").coerceAtLeast(1), KitGood)
        Text("$done of ${s.optInt("total")} marked", color = KitMuted, fontSize = 12.sp)
        if (canMark && s.optInt("not_marked") > 0) com.example.gavanhomes.ui.procurement.ActionButton("✓  Mark the other ${s.optInt("not_marked")} present", KitGood) {
            call("Marked present", { api.send("POST", "/projects/$uuid/attendance-center/mark-many", JSONObject().put("unmarked", true).put("status", "present").put("attendance_date", date.toString())) }, onChanged)
        }
    }

    FilteredList(
        api, b, "/projects/$uuid/attendance-center/sheet", version, "Search worker, trade or contractor…",
        groups = listOf(
            FilterGroup("status", "Marked", listOf("present", "half_day", "absent", "not_marked").map { k -> markStyle(k.takeIf { it != "not_marked" }).let { (l, c) -> FilterOption(k, l, color = c) } }),
            FilterGroup("type", "Labour", listOf(FilterOption("my_labour", "My labour"), FilterOption("vendor", "Vendor labour"))),
        ),
        sorts = emptyList(), empty = "No workers on this project yet — add one with +.", noun = "worker", extra = "&date=$date",
    ) { w ->
        val id = w.optInt("id")
        val status = marks[id] ?: w.str("status")
        val hours = ot[id] ?: w.optInt("overtime")
        val (sl, sc) = markStyle(status)
        RowCard({ onOpen(id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.example.gavanhomes.ui.common.Initials(w.optString("name"), size = 42.dp, dot = sc)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(w.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(w.str("trade"), w.str("contractor"), "${rupees(w.optDouble("daily_wage", 0.0))}/day").joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Chip(sl, sc)
            }
            if (canMark) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("present" to "P", "half_day" to "½", "absent" to "A").forEach { (k, l) ->
                    val on = status == k; val c = markStyle(k).second
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (on) c else c.copy(alpha = .12f)).clickable {
                        if (!on) { marks[id] = k; if (k == "absent") ot[id] = 0; call(null, { api.send("POST", "/projects/$uuid/attendance-center/mark", JSONObject().put("worker_id", id).put("status", k).put("attendance_date", date.toString())) }, onMarked) }
                    }, contentAlignment = Alignment.Center) { Text(l, color = if (on) Color.White else c, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
                }
                Spacer(Modifier.weight(1f))
                // overtime: only on a day already marked present / half (it needs the saved attendance row)
                val aid = w.optInt("attendance_id")
                if (aid > 0 && status != "absent" && marks[id] == null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OT", color = KitMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("−", color = KitInk, fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(Color(0xFFF1EFEA)).clickable(enabled = hours > 0) {
                        ot[id] = hours - 1; call(null, { api.send("PUT", "/attendance-center/$aid/overtime", JSONObject().put("overtime_hours", hours - 1)) })
                    }.padding(horizontal = 11.dp, vertical = 2.dp))
                    Text("${hours}h", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    Text("+", color = KitInk, fontSize = 18.sp, modifier = Modifier.clip(CircleShape).background(Color(0xFFF1EFEA)).clickable(enabled = hours < 24) {
                        ot[id] = hours + 1; call(null, { api.send("PUT", "/attendance-center/$aid/overtime", JSONObject().put("overtime_hours", hours + 1)) })
                    }.padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
    }
}

// ───────────────────────────── month ─────────────────────────────

private val MonthName = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

@Composable
private fun MonthView(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(month, version) { data = null; error = null; try { data = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/attendance-center/month?month=$month") } } catch (e: Exception) { error = e.message } }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = KitInk, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable { month = month.minusMonths(1) }.padding(horizontal = 16.dp, vertical = 4.dp))
        Text(month.format(MonthName), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        val next = month < YearMonth.now()
        Text("›", color = if (next) KitInk else KitMuted.copy(alpha = .4f), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable(enabled = next) { month = month.plusMonths(1) }.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    val d = data
    if (d == null) { Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) { if (error == null) CircularProgressIndicator(color = b.base) else Text(error ?: "", color = KitMuted) }; return }
    val workers = d.objs("workers")
    Card("Wages earned", "Present + half days + overtime this month") { Text(rupees(d.optDouble("earned", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
    val days = d.objs("days")
    val most = (workers.size).coerceAtLeast(1)
    Card("Day by day", "Workers present each day") {
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { day ->
                val p = day.optInt("present") + day.optInt("half_day") / 2f
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.fillMaxWidth().height((80 * (p / most)).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (p > 0) KitGood else Color(0xFFE6E3DD)))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) { Text("1", color = KitMuted, fontSize = 10.sp, modifier = Modifier.weight(1f)); Text("${days.size}", color = KitMuted, fontSize = 10.sp) }
    }
    Card("Workers", "${workers.size} on this project") {
        if (workers.isEmpty()) Text("No workers yet.", color = KitMuted, fontSize = 13.sp)
        workers.forEach { w ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onOpen(w.optInt("id")) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("P ${w.optInt("present")} · ½ ${w.optInt("half_day")} · A ${w.optInt("absent")} · OT ${w.optInt("overtime")}h", color = KitMuted, fontSize = 12.sp)
                }
                Text(shortRupees(w.optDouble("earned", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}

/** "2026-10-30" → "30 Oct 2026". */
internal fun day(s: String?): String? = prettyDay(s)
