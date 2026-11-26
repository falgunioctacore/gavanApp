package com.example.gavanhomes.ui.accounting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.inventory.shortRupees
import com.example.gavanhomes.ui.professionals.ProCard
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.professionals.objects
import com.example.gavanhomes.ui.projects.form.Input
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private fun pill(label: String, on: Boolean, b: Branding, click: () -> Unit) = @Composable {
    Text(label, color = if (on) Color.White else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) b.base else b.base.copy(alpha = .1f)).clickable { click() }.padding(horizontal = 12.dp, vertical = 8.dp))
}

private fun hint(e: Exception) = e.message ?: "Could not load"

// ───────────────────────────── Sales money ─────────────────────────────

/** Booked, collected, due now, overdue and not-yet-due — in total, by sales person and booking by booking. Data: /accounting-center/sales-money. */
@Composable
fun SalesMoneyPage(api: ApiClient, b: Branding, uuid: String, version: Int) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uuid, version) {
        try { d = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/sales-money") } } catch (e: Exception) { error = hint(e) }
    }
    val s = d ?: run { Text(error ?: "Loading…", color = if (error != null) KitDanger else KitMuted, modifier = Modifier.padding(20.dp)); return }
    val t = s.optJSONObject("total") ?: JSONObject()
    ProKpiTiles(listOf(
        ProKpi(shortRupees(t.optDouble("booked")), "Booked", InvBlue, "${t.optInt("n")} bookings"), ProKpi(shortRupees(t.optDouble("collected")), "Collected", KitGood),
        ProKpi(shortRupees(t.optDouble("due_now")), "Due now", InvAmber), ProKpi(shortRupees(t.optDouble("overdue")), "Overdue", if (t.optDouble("overdue") > 0) KitDanger else KitInk),
    ), columns = 2)
    val people = s.objects("by_person")
    if (people.isNotEmpty()) ProCard("By sales person", "Who sold and who collected") {
        people.forEach { p ->
            Text(p.optString("person"), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 14.sp)
            Text("${p.optInt("n")} booking(s) · booked ${shortRupees(p.optDouble("booked"))} · collected ${shortRupees(p.optDouble("collected"))} · overdue ${shortRupees(p.optDouble("overdue"))}", color = KitMuted, fontSize = 12.sp)
        }
    }
    val rows = s.objects("rows")
    ProCard("Booking by booking", "${rows.size} booking(s)") {
        if (rows.isEmpty()) Text("No bookings yet.", color = KitMuted, fontSize = 13.sp)
        rows.forEach { r ->
            Text("${r.optString("customer")} · ${r.optString("ref")}", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp)
            Text("${r.optString("person")} · booked ${rupees(r.optDouble("booked"))} · collected ${rupees(r.optDouble("collected"))}", color = KitMuted, fontSize = 12.sp)
            if (r.optDouble("overdue") > 0) Text("Overdue ${rupees(r.optDouble("overdue"))}", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            else if (r.optDouble("due_now") > 0) Text("Due now ${rupees(r.optDouble("due_now"))}", color = InvAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

// ───────────────────────────── Debit notes ─────────────────────────────

/** Debit notes to suppliers: raised against a purchase order, answered by the supplier, adjusted against a later payment. Data: /accounting-center/debit-notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebitNotesPage(api: ApiClient, b: Branding, uuid: String, version: Int, canCreate: Boolean) {
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf(false) }
    val f = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(uuid, version, attempt) {
        try { d = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/debit-notes") } } catch (e: Exception) { error = hint(e) }
    }
    fun call(path: String, body: JSONObject, done: () -> Unit = {}) = scope.launch {
        try { val r = withContext(Dispatchers.IO) { api.send("POST", path, body) }; AppToast.show(r.optString("message", "Saved")); done(); attempt++ }
        catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
    }
    val s = d ?: run { Text(error ?: "Loading…", color = if (error != null) KitDanger else KitMuted, modifier = Modifier.padding(20.dp)); return }
    ProKpiTiles(listOf(ProKpi(rupees(s.optDouble("open")), "Open with suppliers", if (s.optDouble("open") > 0) InvAmber else KitInk), ProKpi(s.objects("notes").size.toString(), "Debit notes", InvBlue)), columns = 2)
    if (canCreate) Text("+ New debit note", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(b.base)
        .clickable { f.clear(); sheet = true }.padding(horizontal = 16.dp, vertical = 10.dp))
    val notes = s.objects("notes")
    if (notes.isEmpty()) ProCard("No debit note yet") { Text("Raise one for short supply, damage or a rate difference.", color = KitMuted, fontSize = 13.sp) }
    notes.forEach { n ->
        val st = n.optString("status")
        ProCard("${n.optString("dn_no")} · ${rupees(n.optDouble("amount"))}", listOfNotNull(n.str("supplier"), n.str("po_number")).joinToString(" · ")) {
            Text(n.optString("reason") + (n.str("clause")?.let { " ($it)" } ?: ""), color = KitInk, fontSize = 13.sp)
            Text(st.replaceFirstChar { it.uppercase() }, color = when (st) { "agreed" -> KitGood; "disputed" -> KitDanger; "adjusted", "withdrawn" -> KitMuted; else -> InvAmber }, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (canCreate && st in listOf("open", "agreed", "disputed")) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (st == "agreed") Text("Mark adjusted", color = b.base, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { call("/projects/$uuid/accounting-center/debit-notes/${n.optInt("id")}/settle", JSONObject().put("to", "adjusted")) })
                Text("Withdraw", color = KitDanger, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { call("/projects/$uuid/accounting-center/debit-notes/${n.optInt("id")}/settle", JSONObject().put("to", "withdrawn")) })
            }
        }
    }
    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = KitCard) {
        Column(Modifier.padding(20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("New debit note", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Text("Against purchase order", color = KitMuted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.objects("pos").forEach { p -> pill("${p.optString("po_number")}${p.str("supplier")?.let { " · $it" } ?: ""}", f["po"] == p.optInt("id").toString(), b) { f["po"] = p.optInt("id").toString() }() }
            }
            Input("Reason (short supply, damage, rate difference…)", f["reason"].orEmpty(), { f["reason"] = it })
            Input("Amount (₹)", f["amount"].orEmpty(), { f["amount"] = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
            Input("Clause (optional)", f["clause"].orEmpty(), { f["clause"] = it })
            Text("Send to supplier", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base).clickable {
                val body = JSONObject().put("purchase_order_id", f["po"]?.toIntOrNull() ?: 0).put("reason", f["reason"].orEmpty()).put("amount", f["amount"].orEmpty())
                f["clause"]?.takeIf { it.isNotBlank() }?.let { body.put("clause", it) }
                call("/projects/$uuid/accounting-center/debit-notes", body) { sheet = false }
            }.padding(14.dp))
        }
    }
}

// ───────────────────────────── Bank reconciliation ─────────────────────────────

/** Tick each entry that has appeared on the bank statement, enter the statement balance, see what is still unmatched. Data: /accounting-center/bank-reconciliation. */
@Composable
fun BankReconPage(api: ApiClient, b: Branding, uuid: String, version: Int, canEdit: Boolean) {
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var bank by remember { mutableStateOf<Int?>(null) }
    var statement by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    val ticks = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(uuid, version, bank, attempt) {
        try {
            val q = buildString { append("?x=1"); bank?.let { append("&bank=$it") }; if (statement.isNotBlank()) append("&statement=$statement") }
            val r = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/bank-reconciliation$q") }
            d = r; ticks.clear(); r.objects("rows").forEach { ticks[it.optInt("id")] = !it.isNull("cleared_on") }
        } catch (e: Exception) { error = hint(e) }
    }
    val s = d ?: run { Text(error ?: "Loading…", color = if (error != null) KitDanger else KitMuted, modifier = Modifier.padding(20.dp)); return }
    val accounts = s.objects("accounts")
    if (accounts.isEmpty()) { ProCard("No bank account") { Text("Add a bank account first (Bank accounts tab).", color = KitMuted, fontSize = 13.sp) }; return }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { a -> pill(a.optString("name"), s.optInt("account_id") == a.optInt("id"), b) { bank = a.optInt("id") }() }
    }
    ProKpiTiles(listOf(ProKpi(rupees(s.optDouble("book")), "Book balance", KitInk), ProKpi(rupees(s.optDouble("cleared")), "Cleared on statement", KitGood)), columns = 2)
    ProCard("Statement balance", "Type the closing balance from your bank statement") {
        Input("Statement balance (₹)", statement, { statement = it.filter { c -> c.isDigit() || c == '.' || c == '-' } }, keyboard = KeyboardType.Decimal)
        Text("Check difference", color = b.base, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { attempt++ })
        if (!s.isNull("difference")) Text(if (kotlin.math.abs(s.optDouble("difference")) < 0.01) "Matched — no difference." else "Difference ${rupees(s.optDouble("difference"))}",
            color = if (kotlin.math.abs(s.optDouble("difference")) < 0.01) KitGood else KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
    val rows = s.objects("rows")
    ProCard("Entries", "${rows.size} — tick what the bank shows") {
        if (rows.isEmpty()) Text("No entries on this account.", color = KitMuted, fontSize = 13.sp)
        rows.forEach { r ->
            val id = r.optInt("id")
            Row(Modifier.fillMaxWidth().clickable(enabled = canEdit) { ticks[id] = !(ticks[id] ?: false) }, verticalAlignment = Alignment.CenterVertically) {
                Text(if (ticks[id] == true) "☑" else "☐", color = if (ticks[id] == true) KitGood else KitMuted, fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(r.str("date")?.take(10), r.optString("type").replace('_', ' '), r.str("reference")).joinToString(" · "), color = KitInk, fontSize = 13.sp)
                    if (!r.isNull("cleared_on")) Text("Cleared ${r.optString("cleared_on").take(10)}", color = KitGood, fontSize = 11.sp)
                }
                Text((if (r.optDouble("effect") < 0) "−" else "+") + rupees(kotlin.math.abs(r.optDouble("effect"))), color = if (r.optDouble("effect") < 0) KitDanger else KitGood, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        if (canEdit && rows.isNotEmpty()) Text("Save reconciliation", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base).clickable {
            scope.launch {
                try {
                    val body = JSONObject().put("bank", s.optInt("account_id"))
                        .put("shown", org.json.JSONArray(rows.map { it.optInt("id") })).put("cleared", org.json.JSONArray(rows.map { it.optInt("id") }.filter { ticks[it] == true }))
                    val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/accounting-center/bank-reconciliation", body) }
                    AppToast.show(r.optString("message", "Saved")); attempt++
                } catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
            }
        }.padding(14.dp))
    }
}
