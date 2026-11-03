package com.example.gavanhomes.ui.accounting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.inventory.Banner
import com.example.gavanhomes.ui.inventory.DatePick
import com.example.gavanhomes.ui.inventory.FilteredList
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.NewestOldest
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.dateGroup
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Label
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

private fun chequeStatus(s: String?): Pair<String, Color> = when (s) {
    "presented" -> "Presented" to InvBlue; "cleared" -> "Cleared" to KitGood; "bounced" -> "Bounced" to KitDanger; "cancelled" -> "Cancelled" to KitMuted
    else -> "Pending" to InvAmber
}

@Composable
private fun TopAction(label: String, c: Color, onClick: () -> Unit) {
    Text(label, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = .12f)).clickable { onClick() }.padding(vertical = 13.dp))
}

/** A bottom sheet with a title, fields and one save button; [save] returns the error to show, or null once it went through. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormSheet(title: String, button: String, b: Branding, onClose: () -> Unit, save: suspend () -> String?, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            content()
            Banner(error)
            Text(if (saving) "Saving…" else button, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) {
                    saving = true; error = null
                    scope.launch { error = save(); saving = false; if (error == null) onClose() }
                }.padding(vertical = 15.dp))
        }
    }
}

private suspend fun <T> tryApi(call: suspend () -> T): String? = try { call(); null } catch (e: ApiException) { e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message } catch (e: Exception) { e.message ?: "Could not save" }

// ───────────────────────────── day book ─────────────────────────────

/** A day's sales, purchases and payments with money in and out. Data: GET /accounting-center/daybook?date=… */
@Composable
fun DayBookPage(api: ApiClient, b: Branding, uuid: String, version: Int) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(date, version, attempt) {
        error = null
        try { d = withContext(Dispatchers.IO) { api.get("/projects/$uuid/accounting-center/daybook?date=$date") } } catch (e: Exception) { error = e.message ?: "Could not load the day book" }
    }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { DatePick("Day", date, null) { if (it.isNotBlank()) date = it } }
    val x = d
    if (x == null) {
        Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
            if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base) else com.example.gavanhomes.ui.common.EstateError(error, color = b.base, modifier = Modifier.padding(24.dp), onRetry = { attempt++ })
        }
        return
    }
    val t = x.optJSONObject("totals") ?: JSONObject()
    ProKpiTiles(listOf(ProKpi(rupees(t.optDouble("in", 0.0)), "Money in", KitGood), ProKpi(rupees(t.optDouble("out", 0.0)), "Money out", KitDanger),
        ProKpi(rupees(t.optDouble("net", 0.0)), "Net", if (t.optDouble("net", 0.0) < 0) KitDanger else KitInk)), columns = 3)
    val rows = x.objs("data")
    Card("Entries", "${t.optInt("count")} on this day") {
        if (rows.isEmpty()) Text("Nothing was booked on this day.", color = KitMuted, fontSize = 13.sp)
        rows.forEach { r ->
            val incoming = r.optDouble("in", 0.0) > 0
            val c = when (r.optString("kind")) { "Sale", "Payment-In" -> KitGood; "Payment-Out" -> KitDanger; else -> InvBlue }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(if (incoming) "↓" else "↑", color = c, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(r.str("name") ?: r.optString("kind"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(r.optString("kind"), r.str("ref"), r.str("mode")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text((if (incoming) "+" else "−") + rupees(r.optDouble("total", 0.0)), color = c, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}

// ───────────────────────────── bank accounts ─────────────────────────────

@Composable
fun BankAccountsPage(api: ApiClient, b: Branding, uuid: String, version: Int, canCreate: Boolean) {
    var total by remember { mutableStateOf<Double?>(null) }
    var local by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var adding by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    if (canCreate) TopAction("+  Add bank account", InvBlue) { adding = true }
    total?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { Chip("Total balance ${rupees(it)}", KitGood) } }
    FilteredList(api, b, "/projects/$uuid/accounting-center/bank-accounts", version + local, "Search account, bank or number…", groups = emptyList(), sorts = emptyList(),
        empty = "No bank accounts yet.", noun = "account", onMeta = { total = it.optDouble("total_balance", 0.0) }) { a ->
        RowCard({ edit = a }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(InvBlue.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("🏦", fontSize = 18.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(a.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(a.str("bank"), a.str("number")?.let { "•••• " + it.takeLast(4) }, a.str("upi")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(rupees(a.optDouble("balance", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            if (!a.optBoolean("active", true)) Chip("Inactive", KitMuted)
        }
    }
    if (adding || edit != null) {
        val a = edit
        var name by remember(a) { mutableStateOf(a?.optString("name") ?: "") }
        var bank by remember(a) { mutableStateOf(a?.str("bank") ?: "") }
        var number by remember(a) { mutableStateOf(a?.str("number") ?: "") }
        var ifsc by remember(a) { mutableStateOf(a?.str("ifsc") ?: "") }
        var upi by remember(a) { mutableStateOf(a?.str("upi") ?: "") }
        var holder by remember(a) { mutableStateOf(a?.str("holder") ?: "") }
        var balance by remember(a) { mutableStateOf(a?.let { it.optDouble("balance", 0.0).let { v -> if (v == 0.0) "" else v.toString().removeSuffix(".0") } } ?: "") }
        FormSheet(if (a != null) "Edit bank account" else "New bank account", if (a != null) "Save changes" else "Add account", b, onClose = { adding = false; edit = null },
            save = {
                if (name.isBlank()) "Enter the account name." else tryApi {
                    withContext(Dispatchers.IO) {
                        api.send("POST", "/projects/$uuid/accounting-center/bank-accounts", JSONObject().apply {
                            a?.let { put("id", it.optInt("id")) }
                            put("account_name", name.trim()); put("bank_name", bank.trim().ifBlank { JSONObject.NULL }); put("account_number", number.trim().ifBlank { JSONObject.NULL })
                            put("ifsc_code", ifsc.trim().ifBlank { JSONObject.NULL }); put("upi_id", upi.trim().ifBlank { JSONObject.NULL }); put("account_holder_name", holder.trim().ifBlank { JSONObject.NULL })
                            put("opening_balance", balance.toDoubleOrNull() ?: 0.0)
                        })
                    }
                    AppToast.show(if (a != null) "Bank account saved" else "Bank account added"); local++
                }
            }) {
            Input("Account name", name, { name = it }); Input("Bank", bank, { bank = it }); Input("Account number", number, { number = it.filter { c -> c.isLetterOrDigit() } })
            Input("IFSC code", ifsc, { ifsc = it.uppercase() }); Input("UPI id", upi, { upi = it }); Input("Account holder", holder, { holder = it })
            Input("Balance (₹)", balance, { balance = it.filter { c -> c.isDigit() || c == '.' || c == '-' } }, keyboard = KeyboardType.Decimal)
            if (a != null) Text("Delete this account", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { delete = a; edit = null }.padding(vertical = 6.dp))
        }
    }
    delete?.let { a ->
        SwalConfirm(title = "Delete ${a.optString("name")}?", text = "The account is removed from the list.", confirmText = "Yes, delete it", onConfirm = {
            delete = null
            scope.launch {
                try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/accounting-center/bank-accounts/${a.optInt("id")}") }; AppToast.show("Bank account deleted"); local++ }
                catch (e: Exception) { AppToast.show(e.message ?: "Could not delete") }
            }
        }, onCancel = { delete = null })
    }
}

// ───────────────────────────── cash in hand ─────────────────────────────

@Composable
fun CashPage(api: ApiClient, b: Branding, uuid: String, version: Int, canCreate: Boolean, canDelete: Boolean) {
    var head by remember { mutableStateOf<JSONObject?>(null) }
    var local by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<String?>(null) }          // "add" | "reduce"
    var delete by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    val h = head
    ProKpiTiles(listOf(ProKpi(rupees(h?.optDouble("balance", 0.0) ?: 0.0), "Cash in hand", if ((h?.optDouble("balance", 0.0) ?: 0.0) < 0) KitDanger else KitInk),
        ProKpi(rupees(h?.optDouble("in", 0.0) ?: 0.0), "Cash in", KitGood), ProKpi(rupees(h?.optDouble("out", 0.0) ?: 0.0), "Cash out", KitDanger)), columns = 3)
    if (canCreate) Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { Text("+  Add cash", color = KitGood, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitGood.copy(alpha = .12f)).clickable { sheet = "add" }.padding(vertical = 13.dp)) }
        Box(Modifier.weight(1f)) { Text("−  Reduce cash", color = KitDanger, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .12f)).clickable { sheet = "reduce" }.padding(vertical = 13.dp)) }
    }
    FilteredList(api, b, "/projects/$uuid/accounting-center/cash", version + local, "Search cash entries…", groups = emptyList(), sorts = emptyList(), empty = "No cash entries yet.", noun = "entry", onMeta = { head = it }) { r ->
        val incoming = r.optBoolean("in"); val c = if (incoming) KitGood else KitDanger
        RowCard(if (r.optBoolean("adjustment") && canDelete) ({ delete = r }) else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(if (incoming) "↓" else "↑", color = c, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(r.str("name") ?: r.optString("label"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(r.optString("label"), prettyDay(r.str("date"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                }
                Text((if (incoming) "+" else "−") + rupees(r.optDouble("amount", 0.0)), color = c, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
    sheet?.let { kind ->
        var amount by remember(kind) { mutableStateOf("") }
        var day by remember(kind) { mutableStateOf(LocalDate.now().toString()) }
        var note by remember(kind) { mutableStateOf("") }
        FormSheet(if (kind == "add") "Add cash" else "Reduce cash", if (kind == "add") "Add to cash" else "Reduce cash", b, onClose = { sheet = null },
            save = {
                if ((amount.toDoubleOrNull() ?: 0.0) <= 0) "Enter an amount greater than 0." else tryApi {
                    withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/accounting-center/cash/adjust", JSONObject().put("type", kind).put("amount", amount.toDouble()).put("adjustment_date", day).put("description", note.trim().ifBlank { JSONObject.NULL })) }
                    AppToast.show("Cash updated"); local++
                }
            }) {
            Input("Amount (₹)", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
            DatePick("Date", day, null) { if (it.isNotBlank()) day = it }
            Input("Note", note, { note = it }, singleLine = false)
        }
    }
    delete?.let { r ->
        SwalConfirm(title = "Delete this entry?", text = "${r.optString("label")} of ${rupees(r.optDouble("amount", 0.0))} is removed from cash in hand.", confirmText = "Yes, delete it", onConfirm = {
            delete = null
            scope.launch {
                try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/accounting-center/cash/adjust/${r.optInt("id")}") }; AppToast.show("Entry deleted"); local++ }
                catch (e: Exception) { AppToast.show(e.message ?: "Could not delete") }
            }
        }, onCancel = { delete = null })
    }
}

// ───────────────────────────── cheques ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChequesPage(api: ApiClient, b: Branding, uuid: String, version: Int, canCreate: Boolean, canDelete: Boolean) {
    var meta by remember { mutableStateOf<JSONObject?>(null) }
    var local by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var adding by remember { mutableStateOf(false) }
    var statusFor by remember { mutableStateOf<JSONObject?>(null) }
    var delete by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    if (canCreate) TopAction("+  Add cheque", InvBlue) { adding = true }
    meta?.optJSONObject("summary")?.let { s ->
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Pending ${rupees(s.optDouble("pending", 0.0))}", InvAmber); Chip("Cleared ${rupees(s.optDouble("cleared", 0.0))}", KitGood)
            if (s.optInt("bounced") > 0) Chip("${s.optInt("bounced")} bounced", KitDanger)
        }
    }
    FilteredList(api, b, "/projects/$uuid/accounting-center/cheques", version + local, "Search cheque no., party or bank…",
        groups = listOf(
            FilterGroup("type", "Type", listOf(FilterOption("received", "Received", color = KitGood), FilterOption("issued", "Issued", color = KitDanger))),
            FilterGroup("status", "Status", listOf("pending", "presented", "cleared", "bounced", "cancelled").map { k -> chequeStatus(k).let { (l, c) -> FilterOption(k, l, color = c) } }), dateGroup(),
        ),
        sorts = NewestOldest + ("amount" to "Largest first"), empty = "No cheques yet.", noun = "cheque", onMeta = { meta = it }) { c ->
        val (sl, sc) = chequeStatus(c.str("status")); val received = c.optString("type") == "received"
        RowCard({ edit = c }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(sc.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("✎", color = sc, fontSize = 18.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(c.str("party") ?: "Cheque ${c.optString("no")}", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull("No. ${c.optString("no")}", c.str("bank"), prettyDay(c.str("date"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text((if (received) "+" else "−") + rupees(c.optDouble("amount", 0.0)), color = if (received) KitGood else KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(sl, sc); Chip(if (received) "Received" else "Issued", KitMuted)
                if (canCreate) Text("Change status", color = InvBlue, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { statusFor = c }.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
    }
    if (adding || edit != null) {
        val c = edit
        var no by remember(c) { mutableStateOf(c?.optString("no") ?: "") }
        var type by remember(c) { mutableStateOf(c?.str("type") ?: "received") }
        var amount by remember(c) { mutableStateOf(c?.let { plainAmount(it.optDouble("amount", 0.0)) } ?: "") }
        var party by remember(c) { mutableStateOf(c?.str("party") ?: "") }
        var bank by remember(c) { mutableStateOf(c?.str("bank") ?: "") }
        var day by remember(c) { mutableStateOf(c?.str("date_raw") ?: "") }
        var status by remember(c) { mutableStateOf(c?.str("status") ?: "pending") }
        var notes by remember(c) { mutableStateOf(c?.str("notes") ?: "") }
        FormSheet(if (c != null) "Edit cheque" else "New cheque", if (c != null) "Save changes" else "Add cheque", b, onClose = { adding = false; edit = null },
            save = {
                if (no.isBlank()) "Enter the cheque number." else if ((amount.toDoubleOrNull() ?: 0.0) <= 0) "Enter an amount greater than 0." else tryApi {
                    withContext(Dispatchers.IO) {
                        api.send("POST", "/projects/$uuid/accounting-center/cheques", JSONObject().apply {
                            c?.let { put("id", it.optInt("id")) }
                            put("cheque_no", no.trim()); put("type", type); put("amount", amount.toDouble()); put("status", status)
                            put("party_name", party.trim().ifBlank { JSONObject.NULL }); put("bank_name", bank.trim().ifBlank { JSONObject.NULL })
                            put("cheque_date", day.ifBlank { JSONObject.NULL }); put("notes", notes.trim().ifBlank { JSONObject.NULL })
                        })
                    }
                    AppToast.show(if (c != null) "Cheque saved" else "Cheque added"); local++
                }
            }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pick("Received", type == "received", KitGood) { type = "received" }; Pick("Issued", type == "issued", KitDanger) { type = "issued" } }
            Input("Cheque number", no, { no = it }); Input("Amount (₹)", amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, keyboard = KeyboardType.Decimal)
            Input("Party", party, { party = it }); Input("Bank", bank, { bank = it }); DatePick("Cheque date", day, null) { day = it }
            Label("Status")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pending", "presented", "cleared", "bounced", "cancelled").forEach { s -> chequeStatus(s).let { (l, col) -> Pick(l, status == s, col) { status = s } } }
            }
            Input("Notes", notes, { notes = it }, singleLine = false)
            if (c != null && canDelete) Text("Delete this cheque", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { delete = c; edit = null }.padding(vertical = 6.dp))
        }
    }
    statusFor?.let { c ->
        com.example.gavanhomes.ui.leads.ChoiceSheet("Cheque ${c.optString("no")}", listOf("pending", "presented", "cleared", "bounced", "cancelled").map { k -> chequeStatus(k).let { (l, col) -> com.example.gavanhomes.ui.leads.Choice(k, l, col) } },
            c.str("status"), b, onClose = { statusFor = null }) { k ->
            statusFor = null
            scope.launch {
                try { withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/accounting-center/cheques/${c.optInt("id")}/status", JSONObject().put("status", k)) }; AppToast.show("Cheque marked $k"); local++ }
                catch (e: Exception) { AppToast.show(e.message ?: "Could not update") }
            }
        }
    }
    delete?.let { c ->
        SwalConfirm(title = "Delete cheque ${c.optString("no")}?", text = "It is removed from the register.", confirmText = "Yes, delete it", onConfirm = {
            delete = null
            scope.launch {
                try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/accounting-center/cheques/${c.optInt("id")}") }; AppToast.show("Cheque deleted"); local++ }
                catch (e: Exception) { AppToast.show(e.message ?: "Could not delete") }
            }
        }, onCancel = { delete = null })
    }
}

private fun plainAmount(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
