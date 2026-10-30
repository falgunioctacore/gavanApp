package com.example.gavanhomes.ui.attendance

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.inventory.Banner
import com.example.gavanhomes.ui.inventory.DatePick
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.SaveBar
import com.example.gavanhomes.ui.inventory.Waiting
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.procurement.ActionButton
import com.example.gavanhomes.ui.procurement.MiniRow
import com.example.gavanhomes.ui.procurement.plain
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.Label
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/**
 * A worker: details and wages; what is owed for a period (the website's payable review — present / half days, overtime, advance
 * applied) with Save payout; their payouts (mark paid), advances (give one) and the last 60 days of attendance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkerDetailScreen(api: ApiClient, b: Branding, uuid: String, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf(LocalDate.now().minusMonths(1).toString()) }
    var to by remember { mutableStateOf(LocalDate.now().toString()) }
    var applyAdvance by remember { mutableStateOf(true) }
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var advance by remember { mutableStateOf(false) }
    LaunchedEffect(id, version, attempt, from, to, applyAdvance) {
        error = null
        try { d = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/attendance-center/workers/$id?from_date=$from&to_date=$to&applyAdvance=${if (applyAdvance) 1 else 0}") } }
        catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    fun call(ok: String, run: () -> JSONObject) {
        scope.launch {
            try { val r = withContext(Dispatchers.IO) { run() }; Toast.makeText(context, r.str("message") ?: ok, Toast.LENGTH_SHORT).show(); attempt++; onChanged() }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
        }
    }
    val x = d ?: return Waiting(b, "Worker", error, onBack) { attempt++ }
    val can = x.optJSONObject("can") ?: JSONObject()
    val review = x.optJSONObject("review") ?: JSONObject()
    Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
        HeroBar(b, "Worker", onBack, actions = { if (can.optBoolean("edit_worker")) HeroButton("✎") { onEdit(id) } }) {
            Column {
                Text(x.optString("name"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(listOfNotNull(x.str("trade"), x.str("contractor")).joinToString(" · ").ifEmpty { "Worker" }, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                HeroChips("${rupees(x.optDouble("daily_wage", 0.0))}/day", "OT ${rupees(x.optDouble("overtime_wage", 0.0))}/h", if (x.str("type") == "vendor") "Vendor labour" else "My labour")
            }
        }
        x.str("phone")?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { com.example.gavanhomes.ui.tasks.PhoneActions(it) } }
        Card("What is owed", "For the period below") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { DatePick("From", from) { from = it } }
                Box(Modifier.weight(1f)) { DatePick("To", to) { to = it } }
            }
            com.example.gavanhomes.ui.projects.form.SwitchRow("Take advances off", "Advances given are deducted from this payout", applyAdvance) { applyAdvance = it }
            if (review.has("error")) Text(review.optString("error"), color = KitDanger, fontSize = 13.sp)
            else {
                Fact("Present days", review.optInt("present_days").toString()); Fact("Half days", review.optInt("half_days").toString()); Fact("Overtime", "${review.optInt("overtime_hours")} h")
                Fact("Earned", rupees(review.optDouble("payable_amount", 0.0))); Fact("Advance taken off", rupees(review.optDouble("advance_applied", 0.0)))
                Fact("Advance still open", rupees(review.optDouble("advance_left", 0.0)))
                Text("To pay  " + rupees(review.optDouble("due_amount", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                if (can.optBoolean("mark") && review.optDouble("payable_amount", 0.0) > 0) ActionButton("Save payout", KitGood) {
                    call("Payout saved") { api.send("POST", "/projects/$uuid/attendance-center/workers/$id/payout", JSONObject().put("from_date", from).put("to_date", to).put("applyAdvance", if (applyAdvance) "1" else "0")) }
                }
                Text("Saving a payout marks those days as paid, so they aren't counted again.", color = KitMuted, fontSize = 11.sp)
            }
        }
        val payouts = x.objs("payouts")
        if (payouts.isNotEmpty()) Card("Payouts") {
            payouts.forEach { p ->
                val paid = p.optString("status") == "paid"
                MiniRow(rupees(p.optDouble("due", 0.0)), "${prettyDay(p.str("from"))} – ${prettyDay(p.str("to"))}" + (if (p.optDouble("advance_applied", 0.0) > 0) " · advance ${rupees(p.optDouble("advance_applied", 0.0))}" else ""), null,
                    (if (paid) "Paid" else "Pending") to (if (paid) KitGood else InvAmber)) {}
                if (!paid && can.optBoolean("mark")) SmallAction("Mark ${rupees(p.optDouble("due", 0.0))} paid", KitGood) { call("Payout marked paid") { api.send("POST", "/projects/$uuid/attendance-center/payouts/${p.optInt("id")}/paid", JSONObject()) } }
            }
        }
        Card("Advances", "Money given ahead of a payout") {
            val adv = x.objs("advances")
            if (adv.isEmpty()) Text("None given.", color = KitMuted, fontSize = 13.sp)
            adv.forEach { a -> MiniRow(rupees(a.optDouble("amount", 0.0)), listOfNotNull(prettyDay(a.str("date")), a.str("mode"), a.str("remarks")).joinToString(" · "), null, (if (a.str("paid_by") == "member") "By member" else "By company") to InvBlue) {} }
            if (can.optBoolean("edit_worker")) SmallAction("+  Give advance", b.base) { advance = true }
        }
        val hist = x.objs("history")
        Card("Last 60 days", "${hist.count { it.optString("status") == "present" }} present · ${hist.count { it.optString("status") == "half_day" }} half · ${hist.count { it.optString("status") == "absent" }} absent") {
            if (hist.isEmpty()) Text("No attendance marked yet.", color = KitMuted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hist.forEach { h -> val (l, c) = markStyle(h.str("status")); Chip("${prettyDay(h.str("date"))?.substringBeforeLast(' ')} · ${l.take(1)}${if (h.optInt("overtime") > 0) " +${h.optInt("overtime")}h" else ""}", c) }
            }
        }
        Card("Details") { Fact("Joined", prettyDay(x.str("joined"))); Fact("Phone", x.str("phone")); Fact("Aadhaar", x.str("aadhaar")); Fact("Contractor", x.str("contractor")) }
        Spacer(Modifier.height(28.dp))
    }
    if (advance) AdvanceSheet(b, onClose = { advance = false }) { body -> advance = false; call("Advance saved") { api.send("POST", "/projects/$uuid/attendance-center/workers/$id/advance", body) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvanceSheet(b: Branding, onClose: () -> Unit, onDone: (JSONObject) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Cash") }
    var paidBy by remember { mutableStateOf("company") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var txn by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Give advance", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Input("Amount (₹)", amount, { amount = it.filter { c -> c.isDigit() || c == '.' }; error = null }, error, keyboard = KeyboardType.Decimal)
            Label("Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Cash", "Bank", "UPI", "Other").forEach { m -> Pick(m, mode == m, InvBlue) { mode = m } } }
            Label("Paid by")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pick("Company", paidBy == "company", InvBlue) { paidBy = "company" }; Pick("Member", paidBy == "member", InvBlue) { paidBy = "member" } }
            DatePick("Date", date) { date = it }
            Input("Transaction / reference no.", txn, { txn = it })
            Input("Remarks", remarks, { remarks = it }, singleLine = false)
            ActionButton("Save advance", b.base) {
                val a = amount.toDoubleOrNull()
                if (a == null || a <= 0) error = "Enter the amount."
                else onDone(JSONObject().put("amount", a).put("mode", mode).put("paid_by", paidBy).put("advance_date", date).put("transaction_id", txn.trim().ifBlank { JSONObject.NULL }).put("remarks", remarks.trim().ifBlank { JSONObject.NULL }))
            }
        }
    }
}

/** Add a worker to the project / edit one — the website's worker form: name, phone, joining date, contractor, labour type, wages. */
@Composable
fun WorkerFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = remember { mutableStateMapOf("type" to "my_labour", "joined_at" to LocalDate.now().toString()) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(attempt) {
        loadError = null
        try {
            options = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/attendance-center/options") }
            if (id != null) {
                val x = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/attendance-center/workers/$id") }
                f["name"] = x.optString("name"); f["phone"] = x.str("phone").orEmpty(); f["joined_at"] = x.str("joined").orEmpty(); f["type"] = x.str("type") ?: "my_labour"
                f["daily_wage"] = plain(x.optDouble("daily_wage", 0.0)); f["overtime_wage"] = plain(x.optDouble("overtime_wage", 0.0))
                options?.objs("contractors")?.firstOrNull { it.optString("name") == x.str("contractor") }?.let { f["contractor_id"] = it.optInt("id").toString() }
            }
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
        initial = f.toMap()
    }
    val title = if (id != null) "Edit worker" else "Add worker"
    val back = leaveGuard(initial != null && f.toMap() != initial, onBack)
    val o = options ?: return Waiting(b, title, loadError, onBack) { attempt++ }
    fun save() {
        val e = mutableMapOf<String, String>()
        if (f["name"].isNullOrBlank()) e["name"] = "Enter the worker's name."
        if (f["contractor_id"].isNullOrBlank()) e["contractor_id"] = "Choose the contractor they work under."
        if (f["joined_at"].isNullOrBlank()) e["joined_at"] = "When did they join?"
        if (e.isNotEmpty()) { errors = e; banner = e.values.first(); return }
        val body = JSONObject().put("name", f["name"]!!.trim()).put("phone", f["phone"].orEmpty().ifBlank { JSONObject.NULL }).put("joined_at", f["joined_at"]).put("contractor_id", f["contractor_id"]!!.toInt())
            .put("type", f["type"]).put("daily_wage", f["daily_wage"]?.toDoubleOrNull() ?: 0.0).put("overtime_wage", f["overtime_wage"]?.toDoubleOrNull() ?: 0.0)
        errors = emptyMap(); banner = null; saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (id != null) api.send("PUT", "/projects/$uuid/attendance-center/workers/$id", body) else api.send("POST", "/projects/$uuid/attendance-center/workers", body) }
                Toast.makeText(context, if (id != null) "Worker saved" else "Worker added", Toast.LENGTH_SHORT).show(); onSaved()
            } catch (ex: ApiException) { errors = ex.errors; banner = ex.errors.values.firstOrNull { it.isNotBlank() } ?: ex.message }
            catch (ex: Exception) { banner = ex.message ?: "Could not save" }
            saving = false
        }
    }
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, title, back, compact = true)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 8.dp)) {
                Banner(banner)
                Card("Worker") {
                    Input("Name", f["name"].orEmpty(), { f["name"] = it }, errors["name"])
                    Input("Phone", f["phone"].orEmpty(), { f["phone"] = it.filter(Char::isDigit).take(15) }, errors["phone"], keyboard = KeyboardType.Phone)
                    DatePick("Joined on", f["joined_at"].orEmpty(), errors["joined_at"]) { f["joined_at"] = it }
                    PickerField("Contractor", o.objs("contractors").firstOrNull { it.optInt("id").toString() == f["contractor_id"] }?.optString("name"), errors["contractor_id"]) { pick = true }
                    Label("Labour")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { o.objs("types").forEach { t -> Pick(t.optString("label"), f["type"] == t.optString("key"), InvBlue) { f["type"] = t.optString("key") } } }
                }
                Card("Wages") {
                    Input("Daily wage (₹)", f["daily_wage"].orEmpty(), { f["daily_wage"] = it.filter { c -> c.isDigit() || c == '.' } }, errors["daily_wage"], keyboard = KeyboardType.Decimal)
                    Input("Overtime per hour (₹)", f["overtime_wage"].orEmpty(), { f["overtime_wage"] = it.filter { c -> c.isDigit() || c == '.' } }, errors["overtime_wage"], keyboard = KeyboardType.Decimal)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        SaveBar(if (id != null) "Save worker" else "Add worker", saving, b, ::save)
    }
    if (pick) ChoiceSheet("Contractor", o.objs("contractors").map { Choice(it.optInt("id").toString(), it.optString("name")) }, f["contractor_id"], b, onClose = { pick = false }) { f["contractor_id"] = it; pick = false }
}
