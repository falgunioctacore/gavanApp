package com.example.gavanhomes.ui.professionals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitSoft
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.projects.form.Input
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun rows(a: JSONArray?): List<JSONObject> = a?.let { (0 until it.length()).map { i -> it.getJSONObject(i) } } ?: emptyList()
private fun inr(v: Double) = "₹" + "%,.0f".format(v)
private val Amber = Color(0xFFC47A1A)

/**
 * Contractors, from the admin app (Contractor App spec v2): the dashboard (labour today, behind plan, to verify, payable), the live task photos to verify or send
 * back for rework, RA bills to approve / pay / reject, and the requests the contractors send from the Add sheet. Data: the contractor-hub routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContractorHubSection(api: ApiClient, b: Branding) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var dash by remember { mutableStateOf<JSONObject?>(null) }
    var proofs by remember { mutableStateOf<JSONObject?>(null) }
    var bills by remember { mutableStateOf<JSONObject?>(null) }
    var reqs by remember { mutableStateOf<JSONObject?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf<Triple<String, JSONObject, String>?>(null) }   // action · row · title
    var text by remember { mutableStateOf("") }
    var text2 by remember { mutableStateOf("") }
    LaunchedEffect(attempt) {
        error = null
        try {
            withContext(Dispatchers.IO) {
                dash = api.get("/contractor-hub/dashboard"); proofs = api.get("/contractor-hub/proofs"); bills = api.get("/contractor-hub/bills?status=submitted,approved"); reqs = api.get("/contractor-hub/requests")
            }
        } catch (e: Exception) { if (dash == null) error = e.message ?: "Could not load" }
    }
    fun call(path: String, body: JSONObject) = scope.launch {
        try { val r = withContext(Dispatchers.IO) { api.send("POST", path, body) }; AppToast.show(r.optString("message", "Done")); ask = null; text = ""; text2 = ""; attempt++ }
        catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
    }
    val t = dash?.optJSONObject("totals")
    val labels = listOf("Today", "To verify (${proofs?.optJSONArray("data")?.length() ?: 0})", "Bills (${bills?.optJSONArray("data")?.length() ?: 0})", "Requests")
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l ->
                Text(l, color = if (tab == i) Color.White else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (tab == i) b.base else b.base.copy(alpha = .1f)).clickable { tab = i }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        if (dash == null) { Text(error ?: "Loading…", color = KitMuted, modifier = Modifier.padding(20.dp)); return@Column }
        when (tab) {
            0 -> {
                ProCard("Today on site", "${t?.optInt("contractors") ?: 0} contractors") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("${t?.optInt("labour_today") ?: 0}", "Labour today", Modifier.weight(1f))
                        Tile("${t?.optInt("behind") ?: 0}", "Behind plan", Modifier.weight(1f), if ((t?.optInt("behind") ?: 0) > 0) KitDanger else KitInk)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("${t?.optInt("to_verify") ?: 0}", "To verify", Modifier.weight(1f), Amber)
                        Tile(inr(t?.optDouble("payable") ?: 0.0), "Payable", Modifier.weight(1f), b.base)
                    }
                    if ((t?.optInt("no_report") ?: 0) > 0) Text("${t?.optInt("no_report")} contractor(s) have not sent today's report.", color = KitDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                rows(dash?.optJSONArray("data")).forEach { c ->
                    ProCard(c.optString("name"), listOfNotNull(c.str("trade"), if (c.optString("supply_type") == "with_material") "With material" else "Labour only").joinToString(" · ")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Labour ${c.optInt("labour_today")}/${c.optInt("labour_total")}", color = KitInk, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Pill(if (c.optBoolean("report_today")) "Report sent" else "No report", (if (c.optBoolean("report_today")) KitGood else KitDanger).copy(alpha = .14f), if (c.optBoolean("report_today")) KitGood else KitDanger)
                        }
                        Text("Done ${c.optDouble("done_pct")}% · plan ${c.optDouble("plan_pct")}%" + if (c.optBoolean("behind")) " · behind" else "", color = if (c.optBoolean("behind")) KitDanger else KitMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("To verify ${c.optInt("to_verify")} · open bills ${c.optInt("bills_open")} · payable ${inr(c.optDouble("payable"))}", color = KitMuted, fontSize = 12.sp)
                    }
                }
            }
            1 -> {
                val can = proofs?.optJSONObject("can")?.optBoolean("act") == true
                val list = rows(proofs?.optJSONArray("data"))
                if (list.isEmpty()) ProCard("Nothing to verify") { Text("New task photos from contractors show up here.", color = KitMuted, fontSize = 13.sp) }
                list.forEach { p ->
                    ProCard(p.optString("task"), listOfNotNull(p.str("project"), p.str("contractor"), p.str("at")).joinToString(" · ")) {
                        Text(if (p.opt("inside_site") == true) "Photo taken inside the site boundary" else if (p.opt("inside_site") == false) "Outside the site boundary" else "Place not checked (no boundary set)", color = if (p.opt("inside_site") == true) KitGood else KitMuted, fontSize = 12.sp)
                        p.str("photo")?.let { Text("View photo: $it", color = b.base, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        if (can) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("Verify", KitGood) { call("/contractor-hub/proofs/${p.optInt("id")}/verify", JSONObject().put("decision", "verified")) }
                            Btn("Rework", KitDanger) { text = ""; ask = Triple("rework", p, "What should be fixed?") }
                        }
                    }
                }
            }
            2 -> {
                val can = bills?.optJSONObject("can")?.optBoolean("act") == true
                val list = rows(bills?.optJSONArray("data"))
                if (list.isEmpty()) ProCard("No bill is waiting") { Text("RA bills sent by contractors show up here.", color = KitMuted, fontSize = 13.sp) }
                list.forEach { r ->
                    ProCard(r.optString("bill_no"), r.optString("contractor")) {
                        Text("Work ${inr(r.optDouble("work_done"))} − retention ${inr(r.optDouble("retention"))} − TDS ${inr(r.optDouble("tds"))} − penalty ${inr(r.optDouble("penalty"))} − recovery ${inr(r.optDouble("recovery"))}", color = KitMuted, fontSize = 12.sp)
                        Text("Net ${inr(r.optDouble("net"))} · ${r.optString("status")}", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        if (can) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (r.optString("status") == "submitted") Btn("Approve", KitGood) { text = ""; text2 = ""; ask = Triple("approve", r, "Approve ${r.optString("bill_no")}") }
                            else Btn("Pay (UTR)", b.base) { text = ""; ask = Triple("pay", r, "Pay ${r.optString("bill_no")}") }
                            Btn("Reject", KitDanger) { text = ""; ask = Triple("reject", r, "Why reject this bill?") }
                        }
                    }
                }
            }
            else -> {
                val can = reqs?.optJSONObject("can")?.optBoolean("act") == true
                val list = rows(reqs?.optJSONArray("data"))
                if (list.isEmpty()) ProCard("No requests") { Text("Material requests, issues and plans from contractors show here.", color = KitMuted, fontSize = 13.sp) }
                list.forEach { r ->
                    ProCard(r.optString("title"), r.optString("contractor") + " · " + r.optString("kind")) {
                        r.str("note")?.let { Text(it, color = KitInk, fontSize = 13.sp) }
                        if (r.optString("kind") == "material" && !r.isNull("qty")) Text("Qty ${r.optString("qty")} ${r.optString("unit")}", color = KitMuted, fontSize = 12.sp)
                        if (r.optString("status") == "open") { if (can) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Btn("Done", KitGood) { text = ""; ask = Triple("done", r, "Reply to the contractor (optional)") }; Btn("Reject", KitDanger) { text = ""; ask = Triple("rejected", r, "Reply to the contractor") } } }
                        else Pill(r.optString("status").replaceFirstChar { it.uppercase() }, KitGood.copy(alpha = .14f), KitGood)
                    }
                }
            }
        }
    }
    ask?.let { (action, row, title) ->
        ModalBottomSheet(onDismissRequest = { ask = null }, containerColor = KitCard) {
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                when (action) {
                    "approve" -> { Input("Penalty to cut (₹)", text, { text = it.filter { c -> c.isDigit() || c == '.' } }); Input("Material recovery (₹)", text2, { text2 = it.filter { c -> c.isDigit() || c == '.' } }) }
                    "pay" -> Input("UTR / reference *", text, { text = it })
                    else -> Input(if (action == "done") "Reply" else "Reason *", text, { text = it }, singleLine = false)
                }
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(b.base).clickable {
                    val id = row.optInt("id")
                    when (action) {
                        "rework" -> if (text.isBlank()) AppToast.show("Say what to fix.") else call("/contractor-hub/proofs/$id/verify", JSONObject().put("decision", "rework").put("note", text.trim()))
                        "approve" -> call("/contractor-hub/bills/$id/decide", JSONObject().put("decision", "approve").put("penalty", text.toDoubleOrNull() ?: 0.0).put("recovery", text2.toDoubleOrNull() ?: 0.0))
                        "pay" -> if (text.isBlank()) AppToast.show("Enter the UTR.") else call("/contractor-hub/bills/$id/decide", JSONObject().put("decision", "pay").put("utr", text.trim()))
                        "reject" -> if (text.isBlank()) AppToast.show("Give a reason.") else call("/contractor-hub/bills/$id/decide", JSONObject().put("decision", "reject").put("reason", text.trim()))
                        else -> call("/contractor-hub/requests/$id/reply", JSONObject().put("status", action).put("reply", text.trim()))
                    }
                }.padding(vertical = 15.dp))
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier, c: Color = KitInk) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(KitSoft).padding(12.dp)) {
        Text(value, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
        Text(label, color = KitMuted, fontSize = 11.sp)
    }
}

@Composable
private fun Btn(label: String, c: Color, onClick: () -> Unit) {
    Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 7.dp))
}

/**
 * Suppliers, from the admin app (Supplier App spec v2): spend by supplier, invoices to check with the 3-way match (approve / return), the questions and issues
 * suppliers send, and the default terms per material category. Data: the supplier-hub routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupplierHubSection(api: ApiClient, b: Branding) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var dash by remember { mutableStateOf<JSONObject?>(null) }
    var invs by remember { mutableStateOf<JSONObject?>(null) }
    var reqs by remember { mutableStateOf<JSONObject?>(null) }
    var terms by remember { mutableStateOf<JSONObject?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf<Triple<String, JSONObject, String>?>(null) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(attempt) {
        error = null
        try { withContext(Dispatchers.IO) { dash = api.get("/supplier-hub/dashboard"); invs = api.get("/supplier-hub/invoices?status=pending,approved"); reqs = api.get("/supplier-hub/requests"); terms = api.get("/supplier-hub/terms") } }
        catch (e: Exception) { if (dash == null) error = e.message ?: "Could not load" }
    }
    fun call(path: String, body: JSONObject) = scope.launch {
        try { val r = withContext(Dispatchers.IO) { api.send("POST", path, body) }; AppToast.show(r.optString("message", "Done")); ask = null; text = ""; attempt++ } catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
    }
    val t = dash?.optJSONObject("totals")
    val labels = listOf("Spend", "Invoices (${invs?.optJSONArray("data")?.length() ?: 0})", "Requests", "Terms")
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l -> Text(l, color = if (tab == i) Color.White else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (tab == i) b.base else b.base.copy(alpha = .1f)).clickable { tab = i }.padding(horizontal = 12.dp, vertical = 8.dp)) }
        }
        if (dash == null) { Text(error ?: "Loading…", color = KitMuted, modifier = Modifier.padding(20.dp)); return@Column }
        when (tab) {
            0 -> {
                ProCard("Purchase spend", "${t?.optInt("suppliers") ?: 0} suppliers") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tile(inr(t?.optDouble("po_value") ?: 0.0), "PO value", Modifier.weight(1f)); Tile("${t?.optInt("checking") ?: 0}", "Invoices to check", Modifier.weight(1f), Amber) }
                    if ((t?.optInt("mismatch") ?: 0) > 0) Text("${t?.optInt("mismatch")} invoice(s) flagged by the 3-way match.", color = KitDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                rows(dash?.optJSONArray("data")).forEach { s -> ProCard(s.optString("name")) { Text("PO ${inr(s.optDouble("po_value"))} · invoiced ${inr(s.optDouble("invoiced"))} · checking ${s.optInt("checking")}" + if (s.optInt("mismatch") > 0) " · mismatch ${s.optInt("mismatch")}" else "", color = KitMuted, fontSize = 12.sp) } }
            }
            1 -> {
                val can = invs?.optJSONObject("can")?.optBoolean("act") == true
                val list = rows(invs?.optJSONArray("data"))
                if (list.isEmpty()) ProCard("No invoice is waiting") { Text("Supplier invoices appear here for the 3-way match.", color = KitMuted, fontSize = 13.sp) }
                list.forEach { r ->
                    ProCard((r.str("invoice_no") ?: "Invoice") + " · " + (r.str("supplier") ?: ""), r.str("po")) {
                        Text("${inr(r.optDouble("amount"))} · ${r.optString("status")}" + (r.str("due_date")?.let { " · due $it" } ?: ""), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text(if (r.optString("match") == "mismatch") "3-way match flagged: ${r.optString("match_note")}" else "3-way match OK (PO rate × GRN qty = invoice)", color = if (r.optString("match") == "mismatch") KitDanger else KitGood, fontSize = 12.sp)
                        if (!r.optBoolean("has_eway")) Text("E-way bill copy missing", color = Amber, fontSize = 12.sp)
                        if (can && r.optString("status") == "pending") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("Approve", KitGood) { call("/supplier-hub/invoices/${r.optInt("id")}/decide", JSONObject().put("decision", "approve").put("override", r.optString("match") == "mismatch")) }
                            Btn("Return", KitDanger) { text = ""; ask = Triple("return", r, "Why return this invoice?") }
                        }
                    }
                }
            }
            2 -> {
                val can = reqs?.optJSONObject("can")?.optBoolean("act") == true
                val list = rows(reqs?.optJSONArray("data"))
                if (list.isEmpty()) ProCard("No questions or issues") { Text("Supplier questions about requests to quote and issues show here.", color = KitMuted, fontSize = 13.sp) }
                list.forEach { r ->
                    ProCard(r.optString("title"), r.optString("supplier") + " · " + r.optString("kind")) {
                        r.str("note")?.let { Text(it, color = KitInk, fontSize = 13.sp) }
                        if (r.optString("status") == "open") { if (can) Btn("Reply", b.base) { text = ""; ask = Triple("reply", r, "Your answer") } } else Text("Answered: ${r.optString("reply")}", color = KitGood, fontSize = 12.sp)
                    }
                }
            }
            else -> {
                val d = terms?.optJSONObject("data")
                val def = d?.optJSONObject("defaults")
                ProCard("Default terms", "Printed on every request to quote and PO") {
                    def?.let { x -> listOf("payment_term" to "Payment", "advance" to "Advance", "clock_start" to "Clock starts", "retention" to "Retention", "late_penalty" to "Late penalty").forEach { (k, l) -> Text("$l: ${x.optString(k)}", color = KitInk, fontSize = 13.sp) } }
                    Text("Reply ${def?.optInt("reply_hours")} h · replacement ${def?.optInt("replacement_days")} days · collect ${def?.optInt("collect_days")} days · dispute ${def?.optInt("dispute_days")} working days", color = KitMuted, fontSize = 12.sp)
                }
                d?.optJSONObject("categories")?.let { c -> c.keys().forEach { k -> ProCard("Terms — $k") { val x = c.getJSONObject(k); x.keys().forEach { kk -> Text("${kk.replace('_', ' ')}: ${x.optString(kk)}", color = KitInk, fontSize = 13.sp) } } } }
            }
        }
    }
    ask?.let { (action, row, title) ->
        ModalBottomSheet(onDismissRequest = { ask = null }, containerColor = KitCard) {
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Input("Reason / answer *", text, { text = it }, singleLine = false)
                Text("Send", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(b.base).clickable {
                    if (text.isBlank()) { AppToast.show("Write something first."); return@clickable }
                    if (action == "return") call("/supplier-hub/invoices/${row.optInt("id")}/decide", JSONObject().put("decision", "return").put("reason", text.trim()))
                    else call("/supplier-hub/requests/${row.optInt("id")}/reply", JSONObject().put("reply", text.trim()).put("share", row.optString("kind") == "question"))
                }.padding(vertical = 15.dp))
            }
        }
    }
}
