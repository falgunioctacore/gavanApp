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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.Downloads
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitSoft
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.inventory.DatePick
import com.example.gavanhomes.ui.inventory.FilteredList
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.dateGroup
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.inventory.shortRupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/*
 * The rest of the website's Accounting panes: Bookings (sales invoices), Cancel & refund (credit notes), Loan accounts (top-ups and
 * payments), party Ledgers, and CSV downloads. Data: /projects/{uuid}/accounting-center/{sales, credit-notes, loans, ledgers, export/…}
 */

private fun money(v: Double) = rupees(v)
private fun Double.signed(): String = if (this < 0) "−" + rupees(-this) else rupees(this)

private fun saleState(s: String?): Pair<String, Color> = when (s) { "cancelled" -> "Cancelled" to KitDanger; "completed" -> "Completed" to KitGood; else -> "Active" to InvBlue }

// ───────────────────────────── CSV downloads ─────────────────────────────

/** Payments, expenses and the day book as CSV files in Downloads, for all time or a date range. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportCard(api: ApiClient, uuid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    fun download(label: String, path: String, file: String) {
        busy = label
        scope.launch {
            try {
                val (served, bytes) = withContext(Dispatchers.IO) { api.download(path) }
                val name = if (served == "export") "$file.csv" else served
                val uri = withContext(Dispatchers.IO) { Downloads.save(context, name, "text/csv", bytes) }
                Downloads.notifyReady(context, "$label ready", name, "text/csv", uri)
                AppToast.show("$label saved to Downloads/GavanHomes")
            } catch (e: Exception) { AppToast.show(e.message ?: "Could not download") }
            busy = null
        }
    }
    val range = (if (from.isNotBlank()) "&from=$from" else "") + (if (to.isNotBlank()) "&to=$to" else "")
    Card("Download as CSV", "Everything, or just a date range") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { DatePick("From", from, null) { from = it } }
            Box(Modifier.weight(1f)) { DatePick("To", to, null) { to = it } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Payments", "payments", InvBlue), Triple("Expenses", "expenses", InvAmber), Triple("Day book", "daybook", KitGood)).forEach { (label, type, c) ->
                SmallAction(if (busy == label) "Preparing…" else "⤓  $label", c) { if (busy == null) download(label, "/projects/$uuid/accounting-center/export/$type?x=1$range", type) }
            }
            SmallAction(if (busy == "Ledger summary") "Preparing…" else "⤓  Ledger summary", InvAmber) { if (busy == null) download("Ledger summary", "/projects/$uuid/accounting-center/ledger-export?x=1$range", "ledger-summary") }
        }
    }
}

// ───────────────────────────── bookings (sales invoices) ─────────────────────────────

@Composable
fun SalesPage(api: ApiClient, b: Branding, uuid: String, version: Int) {
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var open by remember { mutableStateOf<Int?>(null) }
    stats?.let { s ->
        ProKpiTiles(listOf(
            ProKpi(s.optInt("bookings").toString(), "Bookings", InvBlue), ProKpi(shortRupees(s.optDouble("sale")), "Sale value", KitInk),
            ProKpi(shortRupees(s.optDouble("received")), "Received", KitGood), ProKpi(shortRupees(s.optDouble("balance")), "Balance due", InvAmber),
        ), columns = 2)
    }
    FilteredList(api, b, "/projects/$uuid/accounting-center/sales", version, "Search customer, booking or unit…",
        groups = listOf(FilterGroup("status", "Show", listOf(FilterOption("active", "Active"), FilterOption("due", "Balance due"), FilterOption("cancelled", "Cancelled")))),
        sorts = emptyList(), empty = "No bookings yet.", noun = "booking", single = setOf("status"), onMeta = { stats = it.optJSONObject("stats") }) { r ->
        val (label, c) = saleState(r.optString("status"))
        RowCard({ open = r.optInt("id") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.optString("customer"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(r.str("ref"), r.str("unit"), r.str("date")?.let { prettyDay(it) }).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Chip(label, c)
            }
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column { Text("Sale", color = KitMuted, fontSize = 11.sp); Text(shortRupees(r.optDouble("sale")), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                Column { Text("Received", color = KitMuted, fontSize = 11.sp); Text(shortRupees(r.optDouble("received")), color = KitGood, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                Column { Text("Balance", color = KitMuted, fontSize = 11.sp); Text(shortRupees(r.optDouble("balance")), color = if (r.optDouble("balance") > 0) InvAmber else KitMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
    }
    open?.let { id -> BookingSheet(api, b, uuid, id) { open = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingSheet(api: ApiClient, b: Branding, uuid: String, id: Int, onClose: () -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(id) { d = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/sales/$id") } }.getOrNull() }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val x = d
        if (x == null) { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) }; return@ModalBottomSheet }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(x.optString("customer"), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Text(listOfNotNull(x.str("ref"), x.str("unit"), x.str("phone")).joinToString(" · "), color = KitMuted, fontSize = 13.sp)
            Fact("Sale value", money(x.optDouble("sale"))); Fact("Received", money(x.optDouble("received"))); Fact("Balance due", money(x.optDouble("balance")))
            Fact("Status", saleState(x.optString("status")).first)
            x.opt("cancelled_note")?.takeIf { it != JSONObject.NULL }?.let { Fact("Credit note", "CN-$it") }
            val pays = x.objs("payments")
            Text("Payments received · ${pays.size}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            if (pays.isEmpty()) Text("No payment has been recorded yet.", color = KitMuted, fontSize = 13.sp)
            pays.forEach { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KitSoft).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(money(p.optDouble("amount")), color = KitGood, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text(listOfNotNull(p.str("date")?.let { prettyDay(it) }, p.str("method"), p.str("cycle")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                    }
                    p.str("status")?.let { Chip(it.replaceFirstChar { c -> c.uppercase() }, if (it == "paid" || it == "completed" || it == "cleared") KitGood else InvAmber) }
                }
            }
        }
    }
}

// ───────────────────────────── cancel & refund ─────────────────────────────

@Composable
fun CancelRefundPage(api: ApiClient, b: Branding, uuid: String, version: Int, canCreate: Boolean, canDelete: Boolean) {
    var local by remember { mutableIntStateOf(0) }
    var cancelling by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<JSONObject?>(null) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    if (canCreate) Text("+  Cancel a booking", color = InvAmber, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InvAmber.copy(alpha = .12f)).clickable { cancelling = true }.padding(vertical = 13.dp))
    stats?.let { s -> ProKpiTiles(listOf(ProKpi(s.optInt("notes").toString(), "Credit notes", InvBlue), ProKpi(shortRupees(s.optDouble("refunded")), "Refunded", KitDanger), ProKpi(shortRupees(s.optDouble("charges")), "Charges kept", KitGood)), columns = 3) }
    FilteredList(api, b, "/projects/$uuid/accounting-center/credit-notes", version + local, "Search customer or booking…", groups = emptyList(), sorts = emptyList(),
        empty = "No booking has been cancelled.", noun = "credit note", onMeta = { stats = it.optJSONObject("stats") }) { n ->
        RowCard(if (canDelete) ({ delete = n }) else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n.str("customer") ?: "—", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull("CN-${n.optInt("no")}", n.str("booking"), n.str("date")?.let { prettyDay(it) }).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1)
                }
                Text(money(n.optDouble("refund")), color = KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                n.str("reason")?.let { Chip(it, InvAmber) }
                Chip("Received ${money(n.optDouble("received"))}", KitMuted)
                if (n.optDouble("charge") > 0) Chip("Charge ${money(n.optDouble("charge"))}", KitGood)
            }
        }
    }
    if (cancelling) CancelBookingSheet(api, b, uuid, onClose = { cancelling = false }) { local++ }
    delete?.let { n ->
        SwalConfirm(title = "Delete credit note CN-${n.optInt("no")}?", text = "The note is removed. The booking stays cancelled and the refund payment is not undone.", confirmText = "Yes, delete it", onConfirm = {
            delete = null
            scope.launch {
                try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/accounting-center/credit-notes/${n.optInt("id")}") }; AppToast.show("Credit note deleted"); local++ }
                catch (e: Exception) { AppToast.show(e.message ?: "Could not delete") }
            }
        }, onCancel = { delete = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelBookingSheet(api: ApiClient, b: Branding, uuid: String, onClose: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var o by remember { mutableStateOf<JSONObject?>(null) }
    var booking by remember { mutableStateOf<JSONObject?>(null) }
    var mode by remember { mutableStateOf<JSONObject?>(null) }
    var reason by remember { mutableStateOf("") }
    var charge by remember { mutableStateOf("0") }
    var remarks by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var picking by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { o = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/cancel-options") } }.getOrNull() }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val x = o
        if (x == null) { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) }; return@ModalBottomSheet }
        val received = booking?.optDouble("received") ?: 0.0
        val refund = (received - (charge.toDoubleOrNull() ?: 0.0)).coerceAtLeast(0.0)
        Column(Modifier.navigationBarsPadding().androidxImePad().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cancel a booking", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Text("The booking is cancelled, its unit goes back to stock, and a credit note and the refund payment are created.", color = KitMuted, fontSize = 13.sp)
            PickerField("Booking", booking?.optString("label")) { picking = "booking" }
            if (booking != null) Text("Received so far ${money(received)}", color = KitGood, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Input("Cancellation charge (₹)", charge, { charge = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
            if (booking != null) Text("Refund to the customer ${money(refund)}", color = InvAmber, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            PickerField("Refund mode", mode?.optString("label")) { picking = "mode" }
            PickerField("Reason", reason.ifBlank { null }) { picking = "reason" }
            DatePick("Date", date, null) { if (it.isNotBlank()) date = it }
            Input("Remarks (optional)", remarks, { remarks = it }, singleLine = false)
            error?.let { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Text(if (saving) "Cancelling…" else "Cancel booking & refund", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else KitDanger).clickable(enabled = !saving) {
                    when {
                        booking == null -> error = "Pick the booking to cancel."
                        mode == null -> error = "Pick how the refund is paid."
                        reason.isBlank() -> error = "Pick a reason."
                        else -> { saving = true; error = null; scope.launch {
                            try {
                                val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/accounting-center/cancel-booking", JSONObject().put("booking_id", booking!!.optInt("id")).put("cancellation_charge", charge.toDoubleOrNull() ?: 0.0).put("refund_mode", mode!!.optString("value")).put("reason", reason).put("remarks", remarks.trim()).put("note_date", date)) }
                                AppToast.show(r.str("message") ?: "Booking cancelled"); onDone(); onClose()
                            } catch (e: com.example.gavanhomes.data.ApiException) { error = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message }
                            catch (e: Exception) { error = e.message ?: "Could not cancel" }
                            saving = false
                        } }
                    }
                }.padding(vertical = 15.dp))
        }
        when (picking) {
            "booking" -> ChoiceSheet("Booking to cancel", x.objs("bookings").map { Choice(it.optInt("id").toString(), it.optString("label")) }, booking?.optInt("id")?.toString(), b, onClose = { picking = null }) { id -> booking = x.objs("bookings").firstOrNull { it.optInt("id").toString() == id }; picking = null }
            "mode" -> ChoiceSheet("Refund mode", x.objs("modes").map { Choice(it.optString("value"), it.optString("label")) }, mode?.optString("value"), b, onClose = { picking = null }) { v -> mode = x.objs("modes").firstOrNull { it.optString("value") == v }; picking = null }
            "reason" -> ChoiceSheet("Reason", (0 until x.getJSONArray("reasons").length()).map { x.getJSONArray("reasons").getString(it) }.map { Choice(it, it) }, reason, b, onClose = { picking = null }) { v -> reason = v; picking = null }
        }
    }
}

private fun Modifier.androidxImePad(): Modifier = this.imePadding()

// ───────────────────────────── loan accounts ─────────────────────────────

@Composable
fun LoansPage(api: ApiClient, b: Branding, uuid: String, version: Int, canCreate: Boolean, canDelete: Boolean) {
    var local by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Int?>(null) }
    if (canCreate) Text("+  New loan account", color = InvBlue, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InvBlue.copy(alpha = .12f)).clickable { adding = true }.padding(vertical = 13.dp))
    stats?.let { s -> ProKpiTiles(listOf(ProKpi(s.optInt("active").toString(), "Active loans", InvBlue), ProKpi(shortRupees(s.optDouble("borrowed")), "Borrowed", KitDanger), ProKpi(shortRupees(s.optDouble("lent")), "Lent out", KitGood)), columns = 3) }
    FilteredList(api, b, "/projects/$uuid/accounting-center/loans", version + local, "Search loan or lender…",
        groups = listOf(FilterGroup("status", "Status", listOf(FilterOption("active", "Active"), FilterOption("closed", "Closed")))), sorts = emptyList(),
        empty = "No loan accounts yet.", noun = "loan", single = setOf("status"), onMeta = { stats = it.optJSONObject("stats") }) { l ->
        RowCard({ open = l.optInt("id") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(l.str("lender"), l.str("rate"), l.str("start")?.let { "since " + prettyDay(it) }).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(money(l.optDouble("balance")), color = if (l.optString("type") == "lend") KitGood else KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (l.optString("type") == "lend") "Lent" else "Borrowed", if (l.optString("type") == "lend") KitGood else InvAmber)
                Chip(l.optString("status").replaceFirstChar { it.uppercase() }, if (l.optString("status") == "active") InvBlue else KitMuted)
            }
        }
    }
    if (adding) LoanFormSheet(api, b, uuid, onClose = { adding = false }) { local++ }
    open?.let { id -> LoanSheet(api, b, uuid, id, canCreate, canDelete, onClose = { open = null }) { local++ } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanFormSheet(api: ApiClient, b: Branding, uuid: String, onClose: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var lender by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("borrow") }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var rateType by remember { mutableStateOf("monthly") }
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().androidxImePad().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New loan account", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pick("I borrowed", type == "borrow", InvAmber) { type = "borrow" }; Pick("I lent", type == "lend", KitGood) { type = "lend" } }
            Input("Account name", name, { name = it }); Input("Lender / borrower", lender, { lender = it })
            Input("Amount (₹)", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
            Input("Interest rate (%)", rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pick("A month", rateType == "monthly", InvBlue) { rateType = "monthly" }; Pick("A year", rateType == "yearly", InvBlue) { rateType = "yearly" } }
            DatePick("Start date", start, null) { if (it.isNotBlank()) start = it }
            Input("Notes (optional)", notes, { notes = it }, singleLine = false)
            error?.let { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Text(if (saving) "Saving…" else "Add loan account", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) {
                    if (name.isBlank()) { error = "Give the account a name."; return@clickable }
                    if ((amount.toDoubleOrNull() ?: 0.0) <= 0.0) { error = "Enter the amount."; return@clickable }
                    saving = true; error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                api.send("POST", "/projects/$uuid/accounting-center/loans", JSONObject().put("account_name", name.trim()).put("lender_name", lender.trim().ifBlank { JSONObject.NULL }).put("account_type", type)
                                    .put("outstanding_balance", amount.toDouble()).put("interest_rate", rate.toDoubleOrNull() ?: 0.0).put("interest_rate_type", rateType).put("start_date", start).put("notes", notes.trim().ifBlank { JSONObject.NULL }))
                            }
                            AppToast.show("Loan account added"); onDone(); onClose()
                        } catch (e: com.example.gavanhomes.data.ApiException) { error = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message }
                        catch (e: Exception) { error = e.message ?: "Could not save" }
                        saving = false
                    }
                }.padding(vertical = 15.dp))
        }
    }
}

/** A loan: the money (principal, top-ups, interest to today, recovered, what is left), its top-ups and payments, and the actions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LoanSheet(api: ApiClient, b: Branding, uuid: String, id: Int, canCreate: Boolean, canDelete: Boolean, onClose: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var entry by remember { mutableStateOf<String?>(null) }          // topup · payment
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(id, attempt) { d = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/loans/$id") } }.getOrNull() }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val x = d
        if (x == null) { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) }; return@ModalBottomSheet }
        val lend = x.optString("type") == "lend"
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(x.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Text(listOfNotNull(x.str("lender"), x.str("rate"), if (lend) "Lent" else "Borrowed", x.optString("status").replaceFirstChar { it.uppercase() }).joinToString(" · "), color = KitMuted, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(InvAmber.copy(alpha = .12f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(if (lend) "To get back" else "Amount left to pay", color = KitMuted, fontSize = 12.sp); Text(money(x.optDouble("amount_left")), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
            }
            Fact("Principal", money(x.optDouble("principal"))); Fact("Top-ups", money(x.optDouble("topup_total"))); Fact("Interest to today", money(x.optDouble("interest")))
            Fact("Total", money(x.optDouble("total"))); Fact("Recovered / paid", money(x.optDouble("recovered")))
            if (x.optDouble("advance_payment") > 0) Fact("Advance", money(x.optDouble("advance_payment")))
            if (x.optDouble("additional_charge") > 0) Fact("Additional charge", money(x.optDouble("additional_charge")))
            x.optJSONObject("duration")?.let { du -> Fact("Running for", listOfNotNull(du.optInt("y").takeIf { it > 0 }?.let { "$it yr" }, du.optInt("m").takeIf { it > 0 }?.let { "$it mo" }, "${du.optInt("d")} d").joinToString(" ")) }
            Fact("Started", x.str("start")?.let { prettyDay(it) }); Fact("Notes", x.str("notes"))
            if (canCreate) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                SmallAction("+ Top-up", InvBlue) { entry = "topup" }; SmallAction("+ Payment", KitGood) { entry = "payment" }
                if (canDelete) SmallAction("Delete loan", KitDanger) { deleting = true }
            }
            val tops = x.objs("topups")
            Text("Top-ups · ${tops.size}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            tops.forEach { t -> LoanLine(t, InvBlue) }
            val pays = x.objs("payments")
            Text("Payments · ${pays.size}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            if (pays.isEmpty()) Text("No payment yet.", color = KitMuted, fontSize = 13.sp)
            pays.forEach { p -> LoanLine(p, KitGood) }
        }
    }
    entry?.let { kind ->
        var amount by remember(kind) { mutableStateOf("") }
        var date by remember(kind) { mutableStateOf(LocalDate.now().toString()) }
        var note by remember(kind) { mutableStateOf("") }
        var error by remember(kind) { mutableStateOf<String?>(null) }
        var saving by remember(kind) { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { entry = null }, containerColor = KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().androidxImePad().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (kind == "topup") "Add a top-up" else "Record a payment", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
                Input("Amount (₹)", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
                DatePick("Date", date, null) { if (it.isNotBlank()) date = it }
                Input("Note (optional)", note, { note = it })
                error?.let { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                Text(if (saving) "Saving…" else "Save", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) {
                        if ((amount.toDoubleOrNull() ?: 0.0) <= 0.0) { error = "Enter the amount."; return@clickable }
                        saving = true; error = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/accounting-center/loans/$id/${if (kind == "topup") "topups" else "payments"}", JSONObject().put("amount", amount.toDouble()).put(if (kind == "topup") "topup_date" else "payment_date", date).put("note", note.trim().ifBlank { JSONObject.NULL })) }
                                AppToast.show(if (kind == "topup") "Top-up added" else "Payment recorded"); entry = null; attempt++; onChanged()
                            } catch (e: com.example.gavanhomes.data.ApiException) { error = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message }
                            catch (e: Exception) { error = e.message ?: "Could not save" }
                            saving = false
                        }
                    }.padding(vertical = 15.dp))
            }
        }
    }
    if (deleting) SwalConfirm(title = "Delete this loan account?", text = "Its top-ups and payments go with it.", confirmText = "Yes, delete it", onConfirm = {
        deleting = false
        scope.launch {
            try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/accounting-center/loans/$id") }; AppToast.show("Loan account deleted"); onChanged(); onClose() }
            catch (e: Exception) { AppToast.show(e.message ?: "Could not delete") }
        }
    }, onCancel = { deleting = false })
}

@Composable
private fun LoanLine(r: JSONObject, c: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KitSoft).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.str("date")?.let { prettyDay(it) } ?: "—", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            r.str("note")?.let { Text(it, color = KitMuted, fontSize = 12.sp, maxLines = 2) }
        }
        Text(money(r.optDouble("amount")), color = c, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

// ───────────────────────────── ledgers ─────────────────────────────

/** Every party with what is owed to / by them; tap one for its ledger with a running balance. */
@Composable
fun LedgersPage(api: ApiClient, b: Branding, uuid: String, version: Int) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { DatePick("From", from, null) { from = it } }
        Box(Modifier.weight(1f)) { DatePick("To", to, null) { to = it } }
    }
    stats?.let { s -> ProKpiTiles(listOf(ProKpi(s.optInt("parties").toString(), "Parties", InvBlue), ProKpi(shortRupees(s.optDouble("to_receive")), "To receive", KitGood), ProKpi(shortRupees(s.optDouble("to_pay")), "To pay", KitDanger)), columns = 3) }
    val range = (if (from.isNotBlank()) "&from=$from" else "") + (if (to.isNotBlank()) "&to=$to" else "")
    FilteredList(api, b, "/projects/$uuid/accounting-center/ledgers", version, "Search a party…", groups = emptyList(), sorts = emptyList(), empty = "No ledger entries yet.", noun = "party", extra = range, onMeta = { stats = it.optJSONObject("stats") }) { r ->
        val bal = r.optDouble("balance")
        RowCard({ open = r.optString("party") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.optString("party"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.optInt("count")} entries" + (r.str("last_date")?.let { " · last " + prettyDay(it) } ?: ""), color = KitMuted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(r.optString("balance_label"), color = if (bal > 0) KitGood else if (bal < 0) KitDanger else KitMuted, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(if (bal > 0) "to receive" else if (bal < 0) "to pay" else "settled", color = KitMuted, fontSize = 11.sp)
                }
            }
        }
    }
    open?.let { party -> LedgerSheet(api, b, uuid, party, from, to) { open = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerSheet(api: ApiClient, b: Branding, uuid: String, party: String, from: String, to: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    val q = "party=" + com.example.gavanhomes.ui.inventory.enc(party) + (if (from.isNotBlank()) "&from=$from" else "") + (if (to.isNotBlank()) "&to=$to" else "")
    LaunchedEffect(party) { d = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/ledgers?$q") } }.getOrNull() }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val x = d
        if (x == null) { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) }; return@ModalBottomSheet }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(party, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Text("Dr = they owe you · Cr = you owe them", color = KitMuted, fontSize = 12.sp)
            ProKpiTiles(listOf(ProKpi(money(x.optDouble("opening")), "Opening", KitMuted), ProKpi(money(x.optDouble("debit")), "Debit", KitGood), ProKpi(money(x.optDouble("credit")), "Credit", KitDanger), ProKpi(x.optString("closing_label"), "Closing", KitInk)), columns = 2)
            SmallAction("⤓  Download this ledger (CSV)", InvBlue) {
                scope.launch {
                    try {
                        val (served, bytes) = withContext(Dispatchers.IO) { api.download("/projects/$uuid/accounting-center/ledger-export?$q") }
                        val name = if (served == "export") "ledger-$party.csv" else served
                        val uri = withContext(Dispatchers.IO) { Downloads.save(context, name, "text/csv", bytes) }
                        Downloads.notifyReady(context, "Ledger ready", name, "text/csv", uri); AppToast.show("Saved to Downloads/GavanHomes")
                    } catch (e: Exception) { AppToast.show(e.message ?: "Could not download") }
                }
            }
            val rows = x.objs("rows")
            Text("Entries · ${rows.size}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            if (rows.isEmpty()) Text("No entries in this period.", color = KitMuted, fontSize = 13.sp)
            rows.forEach { r ->
                val debit = r.optDouble("debit"); val credit = r.optDouble("credit")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KitSoft).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.optString("type"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (debit > 0) Text("+" + money(debit), color = KitGood, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) else Text("−" + money(credit), color = KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                    Text(listOfNotNull(r.str("date")?.let { prettyDay(it) }, r.str("ref")?.takeIf { it != "—" }, r.str("note")?.takeIf { it.isNotBlank() }).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Balance " + r.optString("balance_label"), color = KitMuted, fontSize = 11.sp)
                }
            }
        }
    }
}
