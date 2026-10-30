package com.example.gavanhomes.ui.accounting

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.DeleteButton
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.inventory.Banner
import com.example.gavanhomes.ui.inventory.DatePick
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.SaveBar
import com.example.gavanhomes.ui.inventory.Waiting
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.whenLabel
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun act(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, ok: String, call: () -> JSONObject, done: () -> Unit) {
    scope.launch {
        try { val r = withContext(Dispatchers.IO) { call() }; Toast.makeText(context, r.str("message") ?: ok, Toast.LENGTH_SHORT).show(); done() }
        catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
    }
}

@Composable
private fun Detail(api: ApiClient, b: Branding, title: String, path: String, version: Int, onBack: () -> Unit, content: @Composable (JSONObject, () -> Unit) -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(path, version, attempt) { error = null; try { d = withContext(Dispatchers.IO) { api.detail(path) } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = d ?: return Waiting(b, title, error, onBack) { attempt++ }
    content(x) { attempt++ }
}

/** A text box in a sheet (a reason, remarks), then [onDone] with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextSheet(title: String, label: String, button: String, c: Color, required: Boolean, onClose: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Input(label, text, { text = it; error = null }, error, singleLine = false)
            ActionButton(button, c) { if (required && text.isBlank()) error = "Please fill this in." else onDone(text.trim()) }
        }
    }
}

// ───────────────────────────── payment details ─────────────────────────────

/** A Payment In / Out: amount, party, how it was paid, category, reference, notes; Approve / Reject / Back to pending; Edit, Delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentDetailScreen(api: ApiClient, b: Branding, uuid: String, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) }
    Detail(api, b, "Payment", "/projects/$uuid/accounting-center/payments/$id", version, onBack) { x, reload ->
        val can = x.optJSONObject("can") ?: JSONObject()
        val incoming = x.optString("type") == "payment_in"
        val st = x.str("status") ?: "pending"
        fun status(s: String, reason: String? = null) = act(context, scope, "Payment ${approval(s).first.lowercase()}", {
            api.send("POST", "/projects/$uuid/accounting-center/payments/$id/status", JSONObject().put("status", s).put("rejection_reason", reason ?: JSONObject.NULL))
        }) { reload(); onChanged() }
        Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
            HeroBar(b, if (incoming) "Payment in" else "Payment out", onBack, actions = {
                if (can.optBoolean("edit")) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                if (can.optBoolean("delete")) DeleteButton { sheet = "delete" }
            }) {
                Column {
                    Text((if (incoming) "+" else "−") + rupees(x.optDouble("amount", 0.0)), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("party"), prettyDay(x.str("date")), x.str("time")).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(approval(st).first, x.str("category"), x.str("method")).toTypedArray())
                }
            }
            if (can.optBoolean("edit")) FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (st != "approved") ActionButton("✓  Approve", KitGood) { status("approved") }
                if (st != "rejected") ActionButton("✕  Reject", KitDanger) { sheet = "reject" }
                if (st != "pending") ActionButton("Back to pending", KitInk) { status("pending") }
            }
            Card("Payment") {
                Fact(if (incoming) "Received from" else "Paid to", x.str("party")); Fact("Mode", x.str("mode")?.replaceFirstChar { it.uppercase() }); Fact("Bank", x.str("bank"))
                Fact("Method", x.str("method")); Fact("Via", x.str("via")); Fact("Category", x.str("category")); Fact("Reference", x.str("reference")); Fact("Notes", x.str("notes"))
                Fact("Rejected because", x.str("rejection_reason"))
            }
            Spacer(Modifier.height(28.dp))
        }
        when (sheet) {
            "reject" -> TextSheet("Reject payment", "Reason", "Reject payment", KitDanger, required = false, onClose = { sheet = null }) { r -> sheet = null; status("rejected", r.ifBlank { null }) }
            "delete" -> SwalConfirm(title = "Delete this payment?", text = "It is removed from the books.", confirmText = "Yes, delete",
                onConfirm = { sheet = null; act(context, scope, "Payment deleted", { api.delete("/projects/$uuid/accounting-center/payments/$id") }) { onChanged(); onBack() } }, onCancel = { sheet = null })
        }
    }
}

// ───────────────────────────── expense details ─────────────────────────────

@Composable
fun ExpenseDetailScreen(api: ApiClient, b: Branding, uuid: String, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askDelete by remember { mutableStateOf(false) }
    Detail(api, b, "Expense", "/projects/$uuid/accounting-center/expenses/$id", version, onBack) { x, _ ->
        val can = x.optJSONObject("can") ?: JSONObject()
        Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
            HeroBar(b, "Expense #${x.optInt("no")}", onBack, actions = {
                if (can.optBoolean("create")) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                if (can.optBoolean("delete")) DeleteButton { askDelete = true }
            }) {
                Column {
                    Text(rupees(x.optDouble("total", 0.0)), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("party"), prettyDay(x.str("date"))).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(x.str("category"), x.str("payment_type"), if (x.optBoolean("gst")) "With GST" else null).toTypedArray())
                }
            }
            Card("Items") {
                x.objs("lines").forEach { l ->
                    MiniRow(l.str("description") ?: "Item", "${plain(l.optDouble("qty", 0.0))} × ${rupees(l.optDouble("rate", 0.0))}" + (if (l.optDouble("discount_percent", 0.0) > 0) " − ${plain(l.optDouble("discount_percent", 0.0))}%" else "") +
                        (if (l.optDouble("tax_percent", 0.0) > 0) " + ${plain(l.optDouble("tax_percent", 0.0))}% tax" else ""), rupees(l.optDouble("amount", 0.0)), "" to KitMuted) {}
                }
                Fact("Subtotal", rupees(x.optDouble("subtotal", 0.0))); if (x.optDouble("tax", 0.0) > 0) Fact("Tax", rupees(x.optDouble("tax", 0.0)))
                if (x.optDouble("round_off", 0.0) != 0.0) Fact("Round off", rupees(x.optDouble("round_off", 0.0))); Fact("Total", rupees(x.optDouble("total", 0.0)))
            }
            Card("Details") { Fact("Party", x.str("party")); Fact("Phone", x.str("phone")); Fact("State of supply", x.str("state")); Fact("Description", x.str("description")) }
            Spacer(Modifier.height(28.dp))
        }
        if (askDelete) SwalConfirm(title = "Delete expense #${x.optInt("no")}?", text = "It and its bill are removed.", confirmText = "Yes, delete",
            onConfirm = { askDelete = false; act(context, scope, "Expense deleted", { api.delete("/projects/$uuid/accounting-center/expenses/$id") }) { onChanged(); onBack() } }, onCancel = { askDelete = false })
    }
}

// ───────────────────────────── supplier invoice ─────────────────────────────

/** A supplier invoice: PO, supplier, amount, paid / due, its payments; Verify → Approve → Record payment, or Reject. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvoiceDetailScreen(api: ApiClient, b: Branding, uuid: String, id: Int, version: Int, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) }
    Detail(api, b, "Supplier invoice", "/projects/$uuid/accounting-center/invoices/$id", version, onBack) { x, reload ->
        val can = x.optJSONObject("can") ?: JSONObject()
        fun step(action: String, body: JSONObject, ok: String) = act(context, scope, ok, { api.send("POST", "/projects/$uuid/accounting-center/invoices/$id/$action", body) }) { reload(); onChanged() }
        Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
            HeroBar(b, "Supplier invoice", onBack) {
                Column {
                    Text(x.str("vendor") ?: "Supplier", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("no")?.let { "Invoice $it" }, x.str("po"), prettyDay(x.str("date"))).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(rupees(x.optDouble("amount", 0.0)), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    HeroChips(invoiceStatus(x.str("status")).first, "Paid ${rupees(x.optDouble("paid", 0.0))}", "Due ${rupees(x.optDouble("due", 0.0))}")
                }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (can.optBoolean("verify")) ActionButton("✓  Verify", InvBlue) { sheet = "verify" }
                if (can.optBoolean("approve")) ActionButton("✓  Approve", KitGood) { step("approve", JSONObject(), "Invoice approved") }
                if (can.optBoolean("pay")) ActionButton("₹  Record payment", KitGood) { sheet = "pay" }
                if (can.optBoolean("reject")) ActionButton("✕  Reject", KitDanger) { sheet = "reject" }
            }
            Card("Invoice") {
                Fact("GRN", x.str("grn")); Fact("Verified by", listOfNotNull(x.str("verified_by"), whenLabel(x.str("verified_at"))).joinToString(" · ").ifBlank { null })
                Fact("Approved by", listOfNotNull(x.str("approved_by"), whenLabel(x.str("approved_at"))).joinToString(" · ").ifBlank { null })
                Fact("Remarks", x.str("remarks")); Fact("Rejected because", x.str("rejection_reason"))
            }
            val pays = x.objs("payments")
            Card("Payments", "${pays.size} made") {
                if (pays.isEmpty()) Text("No payments yet.", color = KitMuted, fontSize = 13.sp)
                pays.forEach { p -> MiniRow(rupees(p.optDouble("amount", 0.0)), listOfNotNull(prettyDay(p.str("date")), p.str("mode")?.replace('_', ' '), p.str("ref"), p.str("by")).joinToString(" · "), null, "Paid" to KitGood) {} }
            }
            Spacer(Modifier.height(28.dp))
        }
        when (sheet) {
            "verify" -> TextSheet("Verify invoice", "Remarks (optional)", "Verify", InvBlue, required = false, onClose = { sheet = null }) { r -> sheet = null; step("verify", JSONObject().put("remarks", r.ifBlank { JSONObject.NULL }), "Invoice verified") }
            "reject" -> TextSheet("Reject invoice", "Reason", "Reject invoice", KitDanger, required = true, onClose = { sheet = null }) { r -> sheet = null; step("reject", JSONObject().put("reason", r), "Invoice rejected") }
            "pay" -> PaySheet(x.optDouble("due", 0.0), onClose = { sheet = null }) { body -> sheet = null; step("pay", body, "Payment recorded") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(due: Double, onClose: () -> Unit, onDone: (JSONObject) -> Unit) {
    var amount by remember { mutableStateOf(if (due > 0) plain(due) else "") }
    var mode by remember { mutableStateOf("bank_transfer") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var ref by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Record payment", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Text("${rupees(due)} still due", color = KitMuted, fontSize = 13.sp)
            Input("Amount (₹)", amount, { amount = it.filter { c -> c.isDigit() || c == '.' }; error = null }, error, keyboard = KeyboardType.Decimal)
            Label("Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("bank_transfer" to "Bank", "upi" to "UPI", "cheque" to "Cheque", "cash" to "Cash").forEach { (k, l) -> Pick(l, mode == k, InvBlue) { mode = k } } }
            DatePick("Paid on", date) { date = it }
            Input("Transaction / cheque no.", ref, { ref = it })
            Input("Remarks", remarks, { remarks = it }, singleLine = false)
            ActionButton("Save payment", KitGood) {
                val a = amount.toDoubleOrNull()
                when {
                    a == null || a <= 0 -> error = "Enter the amount."
                    a > due + 0.009 -> error = "More than is due (${rupees(due)})."
                    else -> onDone(JSONObject().put("amount", a).put("payment_mode", mode).put("payment_date", date).put("transaction_id", ref.trim().ifBlank { JSONObject.NULL }).put("remarks", remarks.trim().ifBlank { JSONObject.NULL }))
                }
            }
        }
    }
}

// ───────────────────────────── forms ─────────────────────────────

@Composable
private fun WithOptions(api: ApiClient, b: Branding, uuid: String, title: String, onBack: () -> Unit, load: (suspend () -> Unit)? = null, content: @Composable (JSONObject) -> Unit) {
    var o by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { error = null; try { load?.invoke(); o = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/options") } } catch (e: Exception) { error = e.message ?: "Could not load the form" } }
    val x = o ?: return Waiting(b, title, error, onBack) { attempt++ }
    content(x)
}

@Composable
private fun FormPage(b: Branding, title: String, back: () -> Unit, banner: String?, saveLabel: String, saving: Boolean, onSave: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, title, back, compact = true)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 8.dp)) { Banner(banner); content(); Spacer(Modifier.height(12.dp)) }
        }
        SaveBar(saveLabel, saving, b, onSave)
    }
}

private fun JSONObject.strings(k: String): List<String> = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

/**
 * Record a Payment In / Out (or edit one) — date and time, amount, party (with the project's parties as suggestions), cash or bank
 * (which bank), method, category, reference and notes. New payments wait for approval unless you may approve them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentFormScreen(api: ApiClient, b: Branding, uuid: String, type: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = remember { mutableStateMapOf("transaction_type" to type, "transaction_date" to LocalDate.now().toString(), "transaction_time" to LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), "payment_mode" to "cash", "method" to "Cash") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val back = leaveGuard(!f["amount"].isNullOrBlank(), onBack)
    val incoming = f["transaction_type"] == "payment_in"
    val title = if (id != null) "Edit payment" else if (incoming) "Payment in" else "Payment out"
    WithOptions(api, b, uuid, title, onBack, load = if (id == null) null else ({
        val x = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/payments/$id") }
        listOf("type" to "transaction_type", "date" to "transaction_date", "time" to "transaction_time", "party" to "party_name", "mode" to "payment_mode", "via" to "via",
            "method" to "method", "category" to "category", "reference" to "reference", "notes" to "notes", "status" to "status").forEach { (s, k) -> x.str(s)?.let { f[k] = it } }
        f["amount"] = plain(x.optDouble("amount", 0.0)); if (x.optInt("bank_id") > 0) f["bank_id"] = x.optInt("bank_id").toString()
    })) { o ->
        fun submit() {
            val e = mutableMapOf<String, String>()
            if ((f["amount"]?.toDoubleOrNull() ?: 0.0) <= 0) e["amount"] = "Enter the amount."
            if (f["payment_mode"] == "bank" && f["bank_id"].isNullOrBlank()) e["bank_id"] = "Which bank account?"
            if (e.isNotEmpty()) { errors = e; banner = e.values.first(); return }
            val body = JSONObject()
            f.forEach { (k, v) -> val t = v.trim(); body.put(k, when { t.isEmpty() -> JSONObject.NULL; k == "amount" -> t.toDouble(); k == "bank_id" -> t.toInt(); else -> t }) }
            if (f["payment_mode"] != "bank") body.put("bank_id", JSONObject.NULL)
            if (id != null) body.put("id", id)
            errors = emptyMap(); banner = null; saving = true
            scope.launch {
                try { withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/accounting-center/payments", body) }; Toast.makeText(context, if (id != null) "Payment saved" else "Payment recorded", Toast.LENGTH_SHORT).show(); onSaved() }
                catch (ex: ApiException) { errors = ex.errors; banner = ex.errors.values.firstOrNull { it.isNotBlank() } ?: ex.message }
                catch (ex: Exception) { banner = ex.message ?: "Could not save" }
                saving = false
            }
        }
        FormPage(b, title, back, banner, if (id != null) "Save payment" else "Record payment", saving, ::submit) {
            Card("Payment") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pick("↓  In", incoming, KitGood) { f["transaction_type"] = "payment_in" }
                    Pick("↑  Out", !incoming, KitDanger) { f["transaction_type"] = "payment_out" }
                }
                Input("Amount (₹)", f["amount"].orEmpty(), { f["amount"] = it.filter { c -> c.isDigit() || c == '.' } }, errors["amount"], keyboard = KeyboardType.Decimal)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1.4f)) { DatePick("Date", f["transaction_date"].orEmpty(), errors["transaction_date"]) { f["transaction_date"] = it } }
                    Box(Modifier.weight(1f)) {
                        PickerField("Time", f["transaction_time"]) {
                            val t = runCatching { LocalTime.parse(f["transaction_time"]) }.getOrDefault(LocalTime.now())
                            android.app.TimePickerDialog(context, { _, h, m -> f["transaction_time"] = "%02d:%02d".format(h, m) }, t.hour, t.minute, false).show()
                        }
                    }
                }
                Input(if (incoming) "Received from" else "Paid to", f["party_name"].orEmpty(), { f["party_name"] = it }, errors["party_name"])
                val parties = o.strings("parties").filter { p -> f["party_name"].orEmpty().let { it.isBlank() || p.contains(it, true) } && p != f["party_name"] }.take(6)
                if (parties.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { parties.forEach { p -> Pick(p, false, InvBlue) { f["party_name"] = p } } }
            }
            Card("How") {
                Label("Cash or bank")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pick("Cash", f["payment_mode"] == "cash", InvBlue) { f["payment_mode"] = "cash" }; Pick("Bank", f["payment_mode"] == "bank", InvBlue) { f["payment_mode"] = "bank" } }
                if (f["payment_mode"] == "bank") PickerField("Bank account", o.objs("banks").firstOrNull { it.optInt("id").toString() == f["bank_id"] }?.optString("name"), errors["bank_id"]) { sheet = "bank" }
                Label("Method")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { o.strings("methods").forEach { m -> Pick(m, f["method"] == m, InvBlue) { f["method"] = m } } }
                PickerField("Category", f["category"]?.ifBlank { null }, errors["category"]) { sheet = "category" }
                Input("Via (person)", f["via"].orEmpty(), { f["via"] = it }, errors["via"])
                Input("Reference / cheque no.", f["reference"].orEmpty(), { f["reference"] = it }, errors["reference"])
                Input("Notes", f["notes"].orEmpty(), { f["notes"] = it }, errors["notes"], singleLine = false)
            }
        }
        when (sheet) {
            "bank" -> ChoiceSheet("Bank account", o.objs("banks").map { Choice(it.optInt("id").toString(), it.optString("name")) }, f["bank_id"], b, onClose = { sheet = null }) { f["bank_id"] = it; sheet = null }
            "category" -> ChoiceSheet("Category", o.strings("categories").map { Choice(it, it) }, f["category"], b, allowNone = true, onClose = { sheet = null }) { f["category"] = it; sheet = null }
        }
    }
}

private class ExpenseLine(desc: String = "", qty: String = "1", rate: String = "", disc: String = "", tax: String = "") {
    var desc by mutableStateOf(desc); var qty by mutableStateOf(qty); var rate by mutableStateOf(rate); var disc by mutableStateOf(disc); var tax by mutableStateOf(tax)
    fun base() = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0) * (1 - (disc.toDoubleOrNull() ?: 0.0) / 100)
    fun total(gst: Boolean) = base() * (1 + if (gst) (tax.toDoubleOrNull() ?: 0.0) / 100 else 0.0)
}

/**
 * New expense / edit expense — the website's expense bill: category (add one here), party and phone, date, payment type, GST on or
 * off, description, and the items (description, quantity, rate, discount %, tax %). The website works out the totals; the app shows them as you type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = remember { mutableStateMapOf("expense_date" to LocalDate.now().toString(), "payment_type" to "Cash") }
    var gst by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf(ExpenseLine()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var newCats by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val back = leaveGuard(lines.any { it.rate.isNotBlank() } || !f["party_name"].isNullOrBlank(), onBack)
    val title = if (id != null) "Edit expense" else "New expense"
    WithOptions(api, b, uuid, title, onBack, load = if (id == null) null else ({
        val x = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/expenses/$id") }
        f["category_id"] = x.optInt("category_id").toString(); f["party_name"] = x.str("party").orEmpty(); f["party_phone"] = x.str("phone").orEmpty()
        f["expense_date"] = x.str("date").orEmpty(); f["payment_type"] = x.str("payment_type").orEmpty(); f["description"] = x.str("description").orEmpty(); gst = x.optBoolean("gst")
        lines.clear(); x.objs("lines").forEach { l -> lines.add(ExpenseLine(l.str("description").orEmpty(), plain(l.optDouble("qty", 0.0)), plain(l.optDouble("rate", 0.0)), l.optDouble("discount_percent", 0.0).takeIf { it > 0 }?.let { plain(it) }.orEmpty(), l.optDouble("tax_percent", 0.0).takeIf { it > 0 }?.let { plain(it) }.orEmpty())) }
        if (lines.isEmpty()) lines.add(ExpenseLine())
    })) { o ->
        val cats = o.objs("expense_categories") + newCats
        fun submit() {
            val e = mutableMapOf<String, String>()
            if (f["category_id"].isNullOrBlank()) e["category_id"] = "Choose a category."
            val filled = lines.filter { (it.rate.toDoubleOrNull() ?: 0.0) > 0 || it.desc.isNotBlank() }
            if (filled.isEmpty()) e["items"] = "Add at least one item."
            if (e.isNotEmpty()) { errors = e; banner = e.values.first(); return }
            val body = JSONObject().put("category_id", f["category_id"]!!.toInt()).put("party_name", f["party_name"].orEmpty().ifBlank { JSONObject.NULL }).put("party_phone", f["party_phone"].orEmpty().ifBlank { JSONObject.NULL })
                .put("expense_date", f["expense_date"].orEmpty().ifBlank { JSONObject.NULL }).put("payment_type", f["payment_type"].orEmpty().ifBlank { JSONObject.NULL }).put("gst_enabled", gst)
                .put("description", f["description"].orEmpty().ifBlank { JSONObject.NULL })
                .put("items", JSONArray(filled.map { l -> JSONObject().put("description", l.desc.trim().ifBlank { JSONObject.NULL }).put("qty", l.qty.toDoubleOrNull() ?: 0.0).put("price_per_unit", l.rate.toDoubleOrNull() ?: 0.0)
                    .put("discount_percent", l.disc.toDoubleOrNull() ?: 0.0).put("tax_percent", if (gst) l.tax.toDoubleOrNull() ?: 0.0 else 0.0) }))
            if (id != null) body.put("id", id)
            errors = emptyMap(); banner = null; saving = true
            scope.launch {
                try { withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/accounting-center/expenses", body) }; Toast.makeText(context, if (id != null) "Expense saved" else "Expense added", Toast.LENGTH_SHORT).show(); onSaved() }
                catch (ex: ApiException) { errors = ex.errors; banner = ex.errors.values.firstOrNull { it.isNotBlank() } ?: ex.message }
                catch (ex: Exception) { banner = ex.message ?: "Could not save" }
                saving = false
            }
        }
        FormPage(b, title, back, banner, if (id != null) "Save expense" else "Add expense", saving, ::submit) {
            Card("Expense") {
                PickerField("Category", cats.firstOrNull { it.optInt("id").toString() == f["category_id"] }?.optString("name"), errors["category_id"]) { sheet = "category" }
                Input("Party", f["party_name"].orEmpty(), { f["party_name"] = it }, errors["party_name"])
                Input("Phone", f["party_phone"].orEmpty(), { f["party_phone"] = it.filter { c -> c.isDigit() || c == '+' } }, errors["party_phone"], keyboard = KeyboardType.Phone)
                DatePick("Date", f["expense_date"].orEmpty(), errors["expense_date"]) { f["expense_date"] = it }
                Label("Paid by")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Cash", "Bank", "UPI", "Cheque").forEach { p -> Pick(p, f["payment_type"] == p, InvBlue) { f["payment_type"] = p } } }
                com.example.gavanhomes.ui.projects.form.SwitchRow("GST bill", "Adds each item's tax %", gst) { gst = it }
                Input("Description", f["description"].orEmpty(), { f["description"] = it }, errors["description"], singleLine = false)
            }
            Card("Items") {
                errors["items"]?.let { Text(it, color = KitDanger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                lines.forEachIndexed { i, l ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Input("Item", l.desc, { l.desc = it }) }
                            Text(rupees(l.total(gst)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            if (lines.size > 1) Text("✕", color = KitDanger, fontSize = 15.sp, modifier = Modifier.clip(CircleShape).background(KitDanger.copy(alpha = .1f)).clickable { lines.removeAt(i) }.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(.8f)) { Input("Qty", l.qty, { l.qty = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal) }
                            Box(Modifier.weight(1f)) { Input("Rate ₹", l.rate, { l.rate = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal) }
                            Box(Modifier.weight(.8f)) { Input("Disc %", l.disc, { l.disc = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal) }
                            if (gst) Box(Modifier.weight(.8f)) { Input("Tax %", l.tax, { l.tax = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal) }
                        }
                    }
                }
                Text("+  Add item", color = b.base, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base.copy(alpha = .12f)).clickable { lines.add(ExpenseLine()) }.padding(vertical = 13.dp))
                Fact("Total", rupees(lines.sumOf { it.total(gst) }))
            }
        }
        when (sheet) {
            "category" -> CategorySheet(cats, f["category_id"], b, onClose = { sheet = null }, onPick = { f["category_id"] = it; sheet = null }) { name ->
                act(context, scope, "Category added", {
                    val r = api.send("POST", "/projects/$uuid/accounting-center/expense-categories", JSONObject().put("name", name))
                    r.optJSONObject("category")?.let { c -> scope.launch { newCats = newCats + c; f["category_id"] = c.optInt("id").toString() } }; r
                }) { sheet = null }
            }
        }
    }
}

/** Pick a category, or type a new one and add it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(cats: List<JSONObject>, current: String?, b: Branding, onClose: () -> Unit, onPick: (String) -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Category", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            cats.forEach { c ->
                val on = current == c.optInt("id").toString()
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else Color(0xFFF7F5F1)).clickable { onPick(c.optInt("id").toString()) }.padding(14.dp)) {
                    Text(c.optString("name"), color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f)); if (on) Text("✓", color = b.base, fontWeight = FontWeight.ExtraBold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Input("New category", name, { name = it }) }
                ActionButton("Add", b.base) { if (name.isNotBlank()) onAdd(name.trim()) }
            }
        }
    }
}
