package com.example.gavanhomes.ui.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitSoft
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.inventory.shortRupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/*
 * Spec v2, pages 17–18: the Customer 360 on the customer's screen, and the Customer requests inbox of the Customers module.
 * Data: GET /projects/{uuid}/customer-center/customers/{id}/360 · /requests
 */

private fun ratingTint(r: String?): Color = when (r) { "A" -> KitGood; "B" -> InvAmber; else -> KitDanger }

/** One screen's worth of facts for sales / CRM: visits, dues so far, on time, average delay, pending, interest if charged, the agreement conditions and the journey step with its owner. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Customer360(api: ApiClient, b: Branding, uuid: String, id: Int) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(id) { d = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/customer-center/customers/$id/360") } }.getOrNull() }
    val x = d ?: return
    val m = x.optJSONObject("money")
    val o = x.optJSONObject("on_time")
    val v = x.optJSONObject("visits")
    if (x.optJSONObject("booking") == null) return
    Card("Customer 360", listOfNotNull(x.optJSONObject("booking")?.str("unit"), x.optJSONObject("booking")?.str("ref")).joinToString(" · ")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            x.str("rating")?.let { Chip("Rating $it", ratingTint(it)) }
            x.str("loan_bank")?.let { Chip("Loan $it", InvBlue) }
            x.optJSONObject("journey")?.let { j -> Chip("Step ${j.optInt("number")}: ${j.optString("name")}" + (j.str("owner")?.let { " · owner $it" } ?: ""), KitMuted) }
        }
        ProKpiTiles(listOf(
            ProKpi((v?.optInt("count") ?: 0).toString(), "Visits", KitInk), ProKpi((m?.optInt("dues") ?: 0).toString(), "Dues so far", KitInk),
            ProKpi("${o?.optInt("on_time")}/${o?.optInt("dues")}", "On time", if ((o?.optInt("late") ?: 0) > 0) InvAmber else KitGood), ProKpi("${o?.optInt("avg_delay")} d", "Avg delay", if ((o?.optInt("avg_delay") ?: 0) > 15) KitDanger else KitInk),
        ), columns = 2)
        v?.str("last")?.let { Text("Last visit $it", color = KitMuted, fontSize = 12.sp) }
    }
    Card("Money") {
        Row3("Due so far", rupees(m?.optDouble("due_so_far") ?: 0.0), KitInk)
        Row3("Paid", rupees(m?.optDouble("paid") ?: 0.0), KitGood)
        Row3("Pending" + (m?.optInt("pending_days_late")?.takeIf { it > 0 }?.let { " ($it days late)" } ?: ""), rupees(m?.optDouble("pending") ?: 0.0), if ((m?.optInt("pending") ?: 0) > 0) InvAmber else KitMuted)
        Row3("Interest if charged (waived)", rupees(m?.optDouble("interest") ?: 0.0), InvAmber)
        Text("Interest = unpaid amount × ${m?.optInt("interest_rate")}% × days late ÷ 365 — the rate comes from the agreement.", color = KitMuted, fontSize = 11.sp)
    }
    Card("Agreement conditions check") {
        x.objs("conditions").forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (c.optBoolean("ok")) "✓" else "✗", color = if (c.optBoolean("ok")) KitGood else KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(end = 10.dp))
                Text(c.optString("name"), color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                c.str("note")?.let { Text(it, color = if (c.optBoolean("ok")) KitMuted else KitDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
    }
    val vs = v?.objs("list").orEmpty()
    if (vs.isNotEmpty()) Card("Visits (${v?.optInt("count")})") {
        vs.forEach { r -> Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(r.optString("date"), color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(.5f)); Text(r.optString("what"), color = KitInk, fontSize = 13.sp, modifier = Modifier.weight(1.3f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(r.str("by") ?: "", color = KitMuted, fontSize = 12.sp) } }
    }
}

@Composable
private fun Row3(label: String, value: String, c: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(label, color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(value, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
}

// ───────────────────────────── the requests inbox ─────────────────────────────

/** Issues, visits and loans of the project's customers in one list, routed to the owner with a reply timer; late ones are marked escalated. */
@Composable
fun CustomerRequestsInbox(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (String, Int) -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version) { try { d = withContext(Dispatchers.IO) { api.get("/projects/$uuid/customer-center/requests") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = d
    if (x == null) { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base) else Text(error ?: "", color = KitDanger) }; return }
    val c = x.optJSONObject("counts")
    ProKpiTiles(listOf(
        ProKpi((c?.optInt("issues") ?: 0).toString(), "Issues", KitDanger), ProKpi((c?.optInt("visits") ?: 0).toString(), "Visits", InvBlue),
        ProKpi((c?.optInt("loans") ?: 0).toString(), "Loans", InvAmber), ProKpi((c?.optInt("late") ?: 0).toString(), "Past reply time", if ((c?.optInt("late") ?: 0) > 0) KitDanger else KitGood),
    ), columns = 2)
    if ((c?.optInt("late") ?: 0) > 0) Text("${c?.optInt("late")} past the reply time — escalated to the director", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
    val rows = x.objs("data")
    Card("Inbox — auto routed", "${rows.size} open") {
        if (rows.isEmpty()) Text("No open customer requests. 🎉", color = KitMuted, fontSize = 14.sp)
    }
    rows.forEach { r ->
        val tint = when (r.optString("kind")) { "issue" -> KitDanger; "visit" -> InvBlue; else -> InvAmber }
        RowCard({ if (r.optString("kind") != "loan") onOpen(r.optString("kind"), r.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(tint))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(r.optString("title"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(r.str("who"), r.str("sub")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1)
                    Text("To: " + r.optString("owner"), color = b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(if (r.optBoolean("confirm")) "Confirm" else if (r.optBoolean("late")) "${r.optInt("late_hours")} h late" else hoursText(r.optInt("age_hours")), color = if (r.optBoolean("late")) KitDanger else KitMuted, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
    }
    val rules = x.optJSONArray("rules")
    Card("Routing rules") { (0 until (rules?.length() ?: 0)).forEach { Text("• " + rules!!.getString(it), color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) } }
}


private fun hoursText(h: Int): String = if (h < 24) "$h h" else "${h / 24} d"
