package com.example.gavanhomes.ui.procurement

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.gavanhomes.ui.common.ScrollTopButton
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.inventory.Banner
import com.example.gavanhomes.ui.inventory.DatePick
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.MaterialPickSheet
import com.example.gavanhomes.ui.inventory.SaveBar
import com.example.gavanhomes.ui.inventory.Waiting
import com.example.gavanhomes.ui.inventory.qty
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.leads.whenLabel
import com.example.gavanhomes.ui.professionals.ProBar
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

private fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

/** Run [call] off the main thread; toast [ok] (or the server's message) and then [done], or toast the error. */
private fun act(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, ok: String, call: () -> JSONObject, done: () -> Unit) {
    scope.launch {
        try {
            val r = withContext(Dispatchers.IO) { call() }
            Toast.makeText(context, r.str("message") ?: ok, Toast.LENGTH_SHORT).show(); done()
        } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
    }
}

@Composable
private fun LineRow(title: String, sub: String?, right: String, rightTint: Color = KitInk, extra: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                sub?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
            }
            Text(right, color = rightTint, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        extra?.invoke()
    }
}

// ───────────────────────────── indent details ─────────────────────────────

/** An indent: status, who and when, the materials asked for (and how much is already on a PO), the POs raised; Close / Delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndentDetailScreen(api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onChanged: () -> Unit, onOpenPo: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var ask by remember { mutableStateOf<String?>(null) }           // close · delete
    LaunchedEffect(id, version, attempt) { error = null; try { d = withContext(Dispatchers.IO) { api.detail("/procurement-center/indents/$id") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = d ?: return Waiting(b, "Indent", error, onBack) { attempt++ }
    val can = x.optJSONObject("can") ?: JSONObject()
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(KitPage)) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize().verticalScroll(scroll)) {
            HeroBar(b, "Indent", onBack, actions = { if (can.optBoolean("delete")) DeleteButton { ask = "delete" } }) {
                Column {
                    Text(x.optString("number"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(prettyDay(x.str("date")), x.str("by")).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(indentStatus(x.str("status")).first, fulfilment(x.str("po_status")).first, x.str("priority")).toTypedArray())
                }
            }
            if (can.optBoolean("edit") && can.optBoolean("close")) Text(
                "Close indent", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitInk).clickable { ask = "close" }.padding(vertical = 14.dp),
            )
            Card("Details") {
                Fact("Purpose", x.str("purpose")); Fact("Needed by", prettyDay(x.str("required_date"))); Fact("Expected delivery", prettyDay(x.str("expected")))
                Fact("Department", x.str("department")); Fact("Site location", x.str("site_location"))
                Fact("Assigned to", x.strings("people").joinToString(", ").ifBlank { null }); Fact("Remarks", x.str("remarks"))
            }
            Card("Materials", "Asked for, and how much is already on a PO") {
                x.objs("lines").forEach { l ->
                    val want = l.optDouble("qty", 0.0); val got = l.optDouble("fulfilled", 0.0)
                    LineRow(l.optString("name"), listOfNotNull(l.str("purpose"), prettyDay(l.str("required_date"))?.let { "needed $it" }).joinToString(" · ").ifBlank { null },
                        "${qty(want)} ${l.optString("unit")}") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { ProBar(if (want > 0) (got / want).toFloat() else 0f, if (got >= want) KitGood else InvBlue) }
                            Text("${qty(got)} ordered", color = KitMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
            val pos = x.objs("pos")
            if (pos.isNotEmpty()) Card("Purchase orders raised") { pos.forEach { p -> MiniRow(p.optString("number"), p.str("vendor") ?: "", rupees(p.optDouble("total", 0.0)), poStatus(p.str("status"))) { onOpenPo(p.optInt("id")) } } }
            Spacer(Modifier.height(28.dp))
        }
        ScrollTopButton(scroll)
    }
    when (ask) {
        "close" -> SwalConfirm(title = "Close ${x.optString("number")}?", text = "No more purchase orders will be raised against it.", confirmText = "Yes, close it", confirmColor = KitInk,
            onConfirm = { ask = null; act(context, scope, "Indent closed", { api.send("POST", "/procurement-center/indents/$id/close", JSONObject()) }) { attempt++; onChanged() } }, onCancel = { ask = null })
        "delete" -> SwalConfirm(title = "Delete ${x.optString("number")}?", text = "Its \"Process indent\" task goes too. An indent with purchase orders can only be closed.", confirmText = "Yes, delete it",
            onConfirm = { ask = null; act(context, scope, "Indent deleted", { api.delete("/procurement-center/indents/$id") }) { onChanged(); onBack() } }, onCancel = { ask = null })
    }
}

// ───────────────────────────── purchase order details ─────────────────────────────

/** A purchase order: supplier and amount, the lines with what has arrived, charges, GRNs / deliveries / invoices; Send, Cancel, Receive goods. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PoDetailScreen(api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onChanged: () -> Unit, onReceive: (Int) -> Unit, onOpenIndent: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var askSend by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    LaunchedEffect(id, version, attempt) { error = null; try { d = withContext(Dispatchers.IO) { api.detail("/procurement-center/pos/$id") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = d ?: return Waiting(b, "Purchase order", error, onBack) { attempt++ }
    val can = x.optJSONObject("can") ?: JSONObject()
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(KitPage)) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize().verticalScroll(scroll)) {
            HeroBar(b, "Purchase order", onBack) {
                Column {
                    Text(x.optString("number"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("vendor"), prettyDay(x.str("date"))).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(rupees(x.optDouble("total", 0.0)), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    HeroChips(*listOfNotNull(poStatus(x.str("status")).first, receiptStatus(x.str("delivery_status"))?.first, "${x.optInt("received_pct")}% received").toTypedArray())
                }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (can.optBoolean("receive")) ActionButton("↓  Receive goods", KitGood) { onReceive(id) }
                if (can.optBoolean("send")) ActionButton("➤  Send to supplier", InvBlue) { askSend = true }
                if (can.optBoolean("cancel")) ActionButton("✕  Cancel PO", KitDanger) { cancelling = true }
            }
            Card("Details") {
                Fact("Supplier", x.str("vendor")); Fact("Expected delivery", prettyDay(x.str("expected"))); Fact("Payment", x.str("payment_type"))
                Fact("Bill no.", x.str("bill_number")); Fact("Bill date", prettyDay(x.str("bill_date"))); Fact("Raised by", x.str("by"))
                Fact("Deliver to", x.str("delivery_address")); Fact("Bill to", x.str("billing_address"))
                x.optJSONObject("indent")?.let { i -> Row(Modifier.fillMaxWidth().clickable { onOpenIndent(i.optInt("id")) }) { Text("From indent", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(.8f)); Text(i.optString("number") + "  ›", color = InvBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f)) } }
                Fact("Remarks", x.str("remarks")); Fact("Terms", x.str("terms"))
            }
            Card("Items", "Ordered, and what has arrived") {
                x.objs("lines").forEach { l ->
                    val ordered = l.optDouble("qty", 0.0); val got = l.optDouble("received", 0.0)
                    LineRow(l.optString("name"), "${qty(ordered)} ${l.optString("unit")} × ${rupees(l.optDouble("rate", 0.0))}" + (if (l.optDouble("tax_percent", 0.0) > 0) " + ${qty(l.optDouble("tax_percent", 0.0))}% GST" else ""),
                        rupees(l.optDouble("total", 0.0))) {
                        if (l.optString("type") != "service") Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { ProBar(if (ordered > 0) (got / ordered).toFloat() else 0f, if (got >= ordered) KitGood else InvBlue) }
                            Text("${qty(got)} received · ${qty(l.optDouble("left", 0.0))} to come", color = KitMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
                x.objs("charges").forEach { c -> Fact("+ " + c.optString("name"), rupees(c.optDouble("amount", 0.0))) }
                x.objs("deductions").forEach { c -> Fact("− " + c.optString("name"), rupees(c.optDouble("amount", 0.0))) }
                Fact("Total", rupees(x.optDouble("total", 0.0)))
            }
            val grns = x.objs("grns"); val dels = x.objs("deliveries"); val invs = x.objs("invoices")
            if (grns.isNotEmpty()) Card("Goods received (GRN)") { grns.forEach { g -> LineRow(g.optString("number"), prettyDay(g.str("date")), "Qty ${qty(g.optDouble("qty", 0.0))}", KitGood) } }
            if (dels.isNotEmpty()) Card("Deliveries") { dels.forEach { dl -> deliveryStatus(dl.str("status")).let { (l, c) -> LineRow(dl.optString("number"), dl.str("expected")?.let { "due " + prettyDay(it) }, l, c) } } }
            if (invs.isNotEmpty()) Card("Invoices") { invs.forEach { v -> LineRow(v.str("number") ?: "Invoice", listOfNotNull(prettyDay(v.str("date")), v.str("status")?.replaceFirstChar { it.uppercase() }).joinToString(" · "), rupees(v.optDouble("amount", 0.0))) } }
            Spacer(Modifier.height(28.dp))
        }
        ScrollTopButton(scroll)
    }
    if (askSend) SwalConfirm(title = "Send ${x.optString("number")}?", text = "${x.str("vendor") ?: "The supplier"} can then accept it, reject it or ask for changes.", confirmText = "Yes, send it", confirmColor = InvBlue,
        onConfirm = { askSend = false; act(context, scope, "Sent to the supplier", { api.send("POST", "/procurement-center/pos/$id/send", JSONObject()) }) { attempt++; onChanged() } }, onCancel = { askSend = false })
    if (cancelling) ReasonSheet("Cancel ${x.optString("number")}", "Why is it being cancelled?", "Cancel purchase order", KitDanger, onClose = { cancelling = false }) { reason ->
        cancelling = false; act(context, scope, "Purchase order cancelled", { api.send("POST", "/procurement-center/pos/$id/cancel", JSONObject().put("reason", reason)) }) { attempt++; onChanged() }
    }
}

@Composable
internal fun ActionButton(label: String, c: Color, onClick: () -> Unit) {
    Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp))
}

/** Ask for a reason (required), then [onDone] with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReasonSheet(title: String, label: String, button: String, c: Color, onClose: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Input(label, text, { text = it; error = null }, error, singleLine = false)
            Text(button, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c).clickable { if (text.isBlank()) error = "Please give a reason." else onDone(text.trim()) }.padding(vertical = 15.dp))
        }
    }
}

// ───────────────────────────── delivery (sheet) ─────────────────────────────

/** A delivery: vehicle and driver, the items; Approve arrival, then accept it in full, in part (quantity per item) or reject it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DeliverySheet(api: ApiClient, b: Branding, id: Int, onClose: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf<String?>(null) }           // partial · rejected
    val accepted = remember { mutableStateMapOf<Int, String>() }
    var reason by remember { mutableStateOf("") }
    LaunchedEffect(id, attempt) { d = runCatching { withContext(Dispatchers.IO) { api.detail("/procurement-center/deliveries/$id") } }.getOrNull() }
    fun accept(type: String) {
        val body = JSONObject().put("acceptance_type", type)
        if (type == "partial") body.put("accepted_qty", JSONObject().apply { accepted.forEach { (k, v) -> put(k.toString(), v.toDoubleOrNull() ?: 0.0) } })
        if (type == "rejected") body.put("rejection_reason", reason.trim())
        act(context, scope, "Delivery processed", { api.send("POST", "/procurement-center/deliveries/$id/accept", body) }) { mode = null; attempt++; onChanged() }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val x = d
            if (x == null) { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }; return@Column }
            val can = x.optJSONObject("can") ?: JSONObject()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(x.optString("number"), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
                    Text(listOfNotNull(x.str("po"), x.str("supplier")).joinToString(" · "), color = KitMuted, fontSize = 13.sp)
                }
                deliveryStatus(x.str("status")).let { (l, c) -> Chip(l, c) }
            }
            Fact("Vehicle", x.str("vehicle")); Fact("Driver", x.str("driver")); Fact("Dispatched", prettyDay(x.str("dispatched")))
            Fact("Expected", prettyDay(x.str("expected"))); Fact("Arrived", prettyDay(x.str("arrived"))); Fact("GRN", x.str("grn_no"))
            Fact("Remarks", x.str("remarks")); Fact("Rejected because", x.str("rejection_reason"))
            x.str("driver_phone")?.let { com.example.gavanhomes.ui.tasks.PhoneActions(it) }
            Label("Items")
            x.objs("lines").forEach { l ->
                LineRow(l.optString("name"), "Ordered ${qty(l.optDouble("ordered", 0.0))} · delivered ${qty(l.optDouble("delivered", 0.0))}" + (if (!l.isNull("accepted")) " · accepted ${qty(l.optDouble("accepted", 0.0))}" else ""),
                    "${qty(l.optDouble("delivered", 0.0))} ${l.optString("unit")}") {
                    if (mode == "partial") Input("Accepted (max ${qty(l.optDouble("delivered", 0.0))})", accepted[l.optInt("id")] ?: plain(l.optDouble("delivered", 0.0)), { accepted[l.optInt("id")] = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
                }
            }
            if (can.optBoolean("approve")) ActionButton("✓  Approve arrival", KitGood) { act(context, scope, "Arrival approved", { api.send("POST", "/procurement-center/deliveries/$id/approve", JSONObject()) }) { attempt++; onChanged() } }
            if (can.optBoolean("accept")) when (mode) {
                null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Accept all", KitGood) { accept("full") }
                    ActionButton("Accept part", InvBlue) { mode = "partial" }
                    ActionButton("Reject", KitDanger) { mode = "rejected" }
                }
                "partial" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ActionButton("Accept these quantities", KitGood) { accept("partial") }; SmallAction("Back", KitInk) { mode = null } }
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Input("Why is it being rejected?", reason, { reason = it }, singleLine = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ActionButton("Reject delivery", KitDanger) { if (reason.isNotBlank()) accept("rejected") }; SmallAction("Back", KitInk) { mode = null } }
                }
            }
            if (!can.optBoolean("approve") && !can.optBoolean("accept") && x.str("status") != "delivered" && x.str("status") != "rejected" && !x.optBoolean("arrival_approved"))
                Text("Arrival can be approved once the supplier marks it arrived.", color = KitMuted, fontSize = 12.sp)
        }
    }
}

// ───────────────────────────── supplier details ─────────────────────────────

/** A supplier: contact (call / WhatsApp / email), approval status (tap to change), business details and recent POs; Edit, Delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupplierDetailScreen(api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit, onOpenPo: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var statusSheet by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id, version, attempt) { error = null; try { d = withContext(Dispatchers.IO) { api.detail("/procurement-center/suppliers/$id") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = d ?: return Waiting(b, "Supplier", error, onBack) { attempt++ }
    val can = x.optJSONObject("can") ?: JSONObject()
    val (sl, sc) = supplierStatus(x.str("status"))
    Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
        HeroBar(b, "Supplier", onBack, actions = {
            if (can.optBoolean("edit_supplier")) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
            if (can.optBoolean("delete_supplier")) DeleteButton { askDelete = true }
        }) {
            Column {
                Text(x.optString("name"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(listOfNotNull(x.str("company"), x.str("city")).joinToString(" · ").ifEmpty { "Supplier" }, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                HeroChips(sl, "${x.optInt("pos")} POs", rupees(x.optDouble("spend", 0.0)))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            x.str("phone")?.let { com.example.gavanhomes.ui.tasks.PhoneActions(it) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).then(if (can.optBoolean("edit_supplier")) Modifier.clickable { statusSheet = true } else Modifier).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Approval", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Chip(sl + if (can.optBoolean("edit_supplier")) " ▾" else "", sc)
            }
        }
        Card("Contact") {
            Fact("Email", x.str("email")); Fact("Phone", x.str("phone")); Fact("WhatsApp", x.str("whatsapp"))
            Fact("Address", listOfNotNull(x.str("address_line1"), x.str("address_line2"), x.str("city"), x.str("state"), x.str("pincode")).joinToString(", ").ifBlank { null })
        }
        Card("Business") {
            Fact("Company", x.str("company")); Fact("Business type", x.str("business_type")); Fact("GST no.", x.str("gst"))
            Fact("Registration", x.str("company_registration")); Fact("PAN", x.str("pan"))
        }
        val pos = x.objs("recent_pos")
        if (pos.isNotEmpty()) Card("Recent purchase orders") { pos.forEach { p -> MiniRow(p.optString("number"), prettyDay(p.str("date")) ?: "", rupees(p.optDouble("total", 0.0)), poStatus(p.str("status"))) { onOpenPo(p.optInt("id")) } } }
        Spacer(Modifier.height(28.dp))
    }
    if (statusSheet) ChoiceSheet("Approval", listOf("pending", "approved", "rejected").map { k -> supplierStatus(k).let { (l, c) -> Choice(k, l, c) } }, x.str("status"), b, onClose = { statusSheet = false }) { st ->
        statusSheet = false; act(context, scope, "Supplier ${supplierStatus(st).first.lowercase()}", { api.send("POST", "/procurement-center/suppliers/$id/status", JSONObject().put("status", st)) }) { attempt++; onChanged() }
    }
    if (askDelete) SwalConfirm(title = "Delete ${x.optString("name")}?", text = "A supplier with purchase orders can only be rejected, not deleted.", confirmText = "Yes, delete",
        onConfirm = { askDelete = false; act(context, scope, "Supplier deleted", { api.delete("/procurement-center/suppliers/$id") }) { onChanged(); onBack() } }, onCancel = { askDelete = false })
}

// ───────────────────────────── forms ─────────────────────────────

/** Load the module's form pickers (…/procurement-center/options), then show [content]. */
@Composable
private fun WithOptions(api: ApiClient, b: Branding, uuid: String, title: String, onBack: () -> Unit, content: @Composable (JSONObject) -> Unit) {
    var o by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { error = null; try { o = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/procurement-center/options") } } catch (e: Exception) { error = e.message ?: "Could not load the form" } }
    val x = o ?: return Waiting(b, title, error, onBack) { attempt++ }
    content(x)
}

@Composable
private fun FormPage(b: Branding, title: String, back: () -> Unit, banner: String?, saveLabel: String, saving: Boolean, onSave: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, title, back, compact = true)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 8.dp)) {
                Banner(banner); content(); Spacer(Modifier.height(12.dp))
            }
        }
        SaveBar(saveLabel, saving, b, onSave)
    }
}

private class IndentLine(val m: JSONObject) { var amount by mutableStateOf("") }

/**
 * New indent (a material request) — the website's form: priority, needed by / expected delivery, purpose, site location, department,
 * who handles it, remarks, and the materials with a quantity each. Saving also creates the website's "Process indent" task.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndentFormScreen(api: ApiClient, b: Branding, uuid: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var priority by remember { mutableStateOf("Medium") }
    var required by remember { mutableStateOf("") }
    var expected by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var site by remember { mutableStateOf("") }
    var dept by remember { mutableIntStateOf(0) }
    val people = remember { mutableStateListOf<Int>() }
    var remarks by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<IndentLine>() }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val back = leaveGuard(lines.isNotEmpty() || purpose.isNotBlank() || remarks.isNotBlank(), onBack)

    WithOptions(api, b, uuid, "New indent", onBack) { o ->
        fun save() {
            val filled = lines.filter { (it.amount.toDoubleOrNull() ?: 0.0) > 0 }
            if (filled.isEmpty()) { banner = "Add at least one material with a quantity."; errors = mapOf("materials" to banner!!); return }
            val body = JSONObject().put("priority", priority).put("required_date", required.ifBlank { JSONObject.NULL }).put("delivery_date", expected.ifBlank { JSONObject.NULL })
                .put("purpose", purpose.trim().ifBlank { JSONObject.NULL }).put("site_location", site.trim().ifBlank { JSONObject.NULL }).put("remarks", remarks.trim().ifBlank { JSONObject.NULL })
                .put("department_id", if (dept > 0) dept else JSONObject.NULL).put("assigned_to", JSONArray(people.toList()))
                .put("lines", JSONArray(filled.map { JSONObject().put("material_id", it.m.optInt("id")).put("qty", it.amount.toDouble()) }))
            errors = emptyMap(); banner = null; saving = true
            scope.launch {
                try { withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/procurement-center/indents", body) }; Toast.makeText(context, "Indent created", Toast.LENGTH_SHORT).show(); onSaved() }
                catch (e: ApiException) { errors = e.errors; banner = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message }
                catch (e: Exception) { banner = e.message ?: "Could not save" }
                saving = false
            }
        }
        FormPage(b, "New indent", back, banner, "Create indent", saving, ::save) {
            Card("Request", "Number ${o.optJSONObject("next")?.str("indent") ?: "—"} is given when it is saved") {
                Label("Priority")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("High", "Medium", "Low").forEach { p -> Pick(p, priority == p, priorityTint(p)) { priority = p } } }
                DatePick("Needed by", required, errors["required_date"]) { required = it }
                DatePick("Expected delivery", expected, errors["delivery_date"]) { expected = it }
                Input("Purpose", purpose, { purpose = it }, errors["purpose"])
                Input("Site location", site, { site = it }, errors["site_location"])
                PickerField("Department", o.objs("departments").firstOrNull { it.optInt("id") == dept }?.optString("name"), errors["department_id"]) { sheet = "dept" }
                PickerField("Assigned to", people.mapNotNull { p -> o.objs("users").firstOrNull { it.optInt("id") == p }?.optString("name") }.joinToString(", ").ifBlank { null }) { sheet = "people" }
                Input("Remarks", remarks, { remarks = it }, errors["remarks"], singleLine = false)
            }
            Card("Materials", "What is needed, and how much") {
                errors["materials"]?.let { Text(it, color = KitDanger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                lines.forEachIndexed { i, l ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(l.m.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            RemoveX { lines.removeAt(i) }
                        }
                        Input("Quantity (${l.m.optString("unit")})", l.amount, { l.amount = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
                    }
                }
                AddRow("+  Add material", b) { sheet = "material" }
            }
        }
        when (sheet) {
            "material" -> MaterialPickSheet(o.objs("materials"), b, exclude = lines.map { it.m.optInt("id") }.toSet(), showStock = false, onClose = { sheet = null }) { m -> lines.add(IndentLine(m)); sheet = null }
            "dept" -> ChoiceSheet("Department", o.objs("departments").map { Choice(it.optInt("id").toString(), it.optString("name")) }, dept.takeIf { it > 0 }?.toString(), b, allowNone = true, onClose = { sheet = null }) { dept = it.toIntOrNull() ?: 0; sheet = null }
            "people" -> com.example.gavanhomes.ui.inventory.PeoplePick(o.objs("users"), people.toSet(), b, "Assigned to", onClose = { sheet = null }) { people.clear(); people.addAll(it); sheet = null }
        }
    }
}

@Composable
private fun RemoveX(onClick: () -> Unit) {
    Text("✕", color = KitDanger, fontSize = 15.sp, modifier = Modifier.clip(CircleShape).background(KitDanger.copy(alpha = .1f)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
private fun AddRow(label: String, b: Branding, onClick: () -> Unit) {
    Text(label, color = b.base, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base.copy(alpha = .12f)).clickable { onClick() }.padding(vertical = 13.dp))
}

private class PoLine(val m: JSONObject) {
    var amount by mutableStateOf("")
    var rate by mutableStateOf(m.optDouble("rate", 0.0).takeIf { it > 0 }?.let { plain(it) } ?: "")
    var gst by mutableStateOf(m.optDouble("gst", 0.0).takeIf { it > 0 }?.let { plain(it) } ?: "")
    fun base() = (amount.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
    fun tax() = base() * (gst.toDoubleOrNull() ?: 0.0) / 100
}

/**
 * New purchase order — the website's PO form for materials: supplier, delivery and billing address, expected delivery, and per line
 * quantity, rate and GST %; the totals are worked out as you type. Remarks and terms too. (Service lines, charges and deductions are
 * added on the website.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PoFormScreen(api: ApiClient, b: Branding, uuid: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vendor by remember { mutableIntStateOf(0) }
    var deliverTo by remember { mutableIntStateOf(0) }
    var billTo by remember { mutableIntStateOf(0) }
    var expected by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<PoLine>() }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val back = leaveGuard(lines.isNotEmpty() || vendor > 0, onBack)

    WithOptions(api, b, uuid, "New purchase order", onBack) { o ->
        val addresses = o.objs("addresses")
        LaunchedEffect(addresses.size) { if (addresses.size == 1) { deliverTo = addresses[0].optInt("id"); billTo = deliverTo } }
        fun addressLabel(id: Int) = addresses.firstOrNull { it.optInt("id") == id }?.let { a -> listOfNotNull(a.str("company_name"), a.str("address")).joinToString(", ").ifBlank { "Address #${a.optInt("id")}" } }
        fun save() {
            val e = mutableMapOf<String, String>()
            if (vendor <= 0) e["vendor_id"] = "Select the supplier."
            if (deliverTo <= 0) e["delivery_address"] = "Select a delivery address."
            if (billTo <= 0) e["billing_address"] = "Select a billing address."
            val filled = lines.filter { (it.amount.toDoubleOrNull() ?: 0.0) > 0 }
            if (filled.isEmpty()) e["items"] = "Add at least one item to the purchase order."
            if (e.isNotEmpty()) { errors = e; banner = e.values.first(); return }
            val body = JSONObject().put("vendor_id", vendor).put("delivery_address", deliverTo).put("billing_address", billTo).put("expected_delivery", expected.ifBlank { JSONObject.NULL })
                .put("remarks", remarks.trim().ifBlank { JSONObject.NULL }).put("terms_conditions", terms.trim().ifBlank { JSONObject.NULL })
                .put("lines", JSONArray(filled.map { JSONObject().put("material_id", it.m.optInt("id")).put("qty", it.amount.toDouble()).put("rate", it.rate.toDoubleOrNull() ?: 0.0).put("tax_percent", it.gst.toDoubleOrNull() ?: 0.0) }))
            errors = emptyMap(); banner = null; saving = true
            scope.launch {
                try { withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/procurement-center/pos", body) }; Toast.makeText(context, "Purchase order created", Toast.LENGTH_SHORT).show(); onSaved() }
                catch (ex: ApiException) { errors = ex.errors; banner = ex.errors.values.firstOrNull { it.isNotBlank() } ?: ex.message }
                catch (ex: Exception) { banner = ex.message ?: "Could not save" }
                saving = false
            }
        }
        FormPage(b, "New purchase order", back, banner, "Create purchase order", saving, ::save) {
            Card("Supplier & delivery", "Number ${o.optJSONObject("next")?.str("po") ?: "—"} is given when it is saved") {
                PickerField("Supplier", o.objs("suppliers").firstOrNull { it.optInt("id") == vendor }?.optString("name"), errors["vendor_id"]) { sheet = "vendor" }
                if (addresses.isEmpty()) Text("This project has no delivery / billing address yet — add one on the website (Account settings → Addresses).", color = KitDanger, fontSize = 12.sp)
                PickerField("Deliver to", addressLabel(deliverTo), errors["delivery_address"]) { sheet = "deliver" }
                PickerField("Bill to", addressLabel(billTo), errors["billing_address"]) { sheet = "bill" }
                DatePick("Expected delivery", expected, errors["expected_delivery"]) { expected = it }
            }
            Card("Items", "Quantity, rate (before tax) and GST % for each") {
                errors["items"]?.let { Text(it, color = KitDanger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                lines.forEachIndexed { i, l ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(l.m.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(rupees(l.base() + l.tax()), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                            RemoveX { lines.removeAt(i) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { Input("Qty (${l.m.optString("unit")})", l.amount, { l.amount = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal) }
                            Box(Modifier.weight(1f)) { Input("Rate ₹", l.rate, { l.rate = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal) }
                            Box(Modifier.weight(.8f)) { Input("GST %", l.gst, { l.gst = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal) }
                        }
                    }
                }
                AddRow("+  Add material", b) { sheet = "material" }
                if (lines.isNotEmpty()) {
                    val base = lines.sumOf { it.base() }; val tax = lines.sumOf { it.tax() }
                    Fact("Before tax", rupees(base)); Fact("GST", rupees(tax)); Fact("Total", rupees(base + tax))
                }
            }
            Card("Notes") {
                Input("Remarks", remarks, { remarks = it }, errors["remarks"], singleLine = false)
                Input("Terms & conditions", terms, { terms = it }, errors["terms_conditions"], singleLine = false)
            }
        }
        when (sheet) {
            "material" -> MaterialPickSheet(o.objs("materials"), b, exclude = lines.map { it.m.optInt("id") }.toSet(), showStock = false, onClose = { sheet = null }) { m -> lines.add(PoLine(m)); sheet = null }
            "vendor" -> ChoiceSheet("Supplier", o.objs("suppliers").map { s -> Choice(s.optInt("id").toString(), listOfNotNull(s.optString("name"), s.str("company_name")).joinToString(" · "), supplierStatus(s.str("approval_status")).second) }, vendor.takeIf { it > 0 }?.toString(), b, onClose = { sheet = null }) { vendor = it.toIntOrNull() ?: 0; sheet = null }
            "deliver", "bill" -> ChoiceSheet(if (sheet == "deliver") "Deliver to" else "Bill to", addresses.map { a -> Choice(a.optInt("id").toString(), listOfNotNull(a.str("type")?.replaceFirstChar { it.uppercase() }, a.str("company_name"), a.str("address")).joinToString(" · ")) }, (if (sheet == "deliver") deliverTo else billTo).takeIf { it > 0 }?.toString(), b, onClose = { sheet = null }) {
                val v = it.toIntOrNull() ?: 0; if (sheet == "deliver") deliverTo = v else billTo = v; sheet = null
            }
        }
    }
}

/**
 * Receive goods (a GRN) against a purchase order: pick the PO (or come from its page), enter what arrived per material (never more than
 * is still to come), the challan / invoice details and remarks. The stock goes into Inventory.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceiveFormScreen(api: ApiClient, b: Branding, uuid: String, poId: Int, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var po by remember { mutableIntStateOf(poId) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    val got = remember { mutableStateMapOf<Int, String>() }
    var challan by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("") }
    var invoiceAmount by remember { mutableStateOf("") }
    var invoiceDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var remarks by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    LaunchedEffect(po) {
        detail = null; got.clear()
        if (po > 0) detail = runCatching { withContext(Dispatchers.IO) { api.detail("/procurement-center/pos/$po") } }.getOrNull()
        detail?.objs("lines")?.filter { it.optInt("material_id") > 0 && it.optDouble("left", 0.0) > 0 }?.forEach { got[it.optInt("material_id")] = plain(it.optDouble("left", 0.0)) }
    }
    val back = leaveGuard(po > 0 && po != poId, onBack)

    WithOptions(api, b, uuid, "Receive goods", onBack) { o ->
        val open = detail?.objs("lines").orEmpty().filter { it.optInt("material_id") > 0 && it.optDouble("left", 0.0) > 0 }
        fun save() {
            val lines = open.mapNotNull { l -> got[l.optInt("material_id")]?.toDoubleOrNull()?.takeIf { it > 0 }?.let { l to it } }
            when {
                po <= 0 -> { banner = "Choose the purchase order."; return }
                lines.isEmpty() -> { banner = "Enter the quantity received for at least one item."; return }
            }
            lines.firstOrNull { (l, q) -> q > l.optDouble("left", 0.0) + 0.0001 }?.let { (l, _) -> banner = "${l.optString("name")}: only ${qty(l.optDouble("left", 0.0))} still to receive."; return }
            val body = JSONObject().put("lines", JSONArray(lines.map { (l, q) -> JSONObject().put("material_id", l.optInt("material_id")).put("qty", q) }))
                .put("delivery_challan", challan.trim().ifBlank { JSONObject.NULL }).put("invoice_no", invoiceNo.trim().ifBlank { JSONObject.NULL })
                .put("invoice_amount", invoiceAmount.toDoubleOrNull() ?: JSONObject.NULL).put("invoice_date", invoiceDate.ifBlank { JSONObject.NULL }).put("remarks", remarks.trim().ifBlank { JSONObject.NULL })
            errors = emptyMap(); banner = null; saving = true
            scope.launch {
                try { val r = withContext(Dispatchers.IO) { api.send("POST", "/procurement-center/pos/$po/receive", body) }; Toast.makeText(context, r.str("message") ?: "Goods received", Toast.LENGTH_LONG).show(); onSaved() }
                catch (e: ApiException) { errors = e.errors; banner = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message }
                catch (e: Exception) { banner = e.message ?: "Could not save" }
                saving = false
            }
        }
        FormPage(b, "Receive goods", back, banner, "Save GRN", saving, ::save) {
            Card("Purchase order", "Goods are received against one") {
                val pos = o.objs("receivable_pos")
                val label = pos.firstOrNull { it.optInt("id") == po }?.let { "${it.optString("number")} · ${it.str("vendor") ?: ""}" } ?: detail?.let { "${it.optString("number")} · ${it.str("vendor") ?: ""}" }
                if (poId > 0) Fact("PO", label) else PickerField("Purchase order", label) { pick = true }
                if (poId <= 0 && pos.isEmpty()) Text("No purchase order has anything left to receive.", color = KitMuted, fontSize = 12.sp)
            }
            if (po > 0) Card("What arrived", "Prefilled with everything still to come") {
                if (detail == null) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
                else if (open.isEmpty()) Text("Everything on this PO has been received.", color = KitMuted, fontSize = 13.sp)
                open.forEach { l ->
                    val mid = l.optInt("material_id")
                    val over = (got[mid]?.toDoubleOrNull() ?: 0.0) > l.optDouble("left", 0.0) + 0.0001
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(l.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Ordered ${qty(l.optDouble("qty", 0.0))} · received ${qty(l.optDouble("received", 0.0))} · ${qty(l.optDouble("left", 0.0))} to come", color = KitMuted, fontSize = 12.sp)
                        Input("Received now (${l.optString("unit")})", got[mid].orEmpty(), { got[mid] = it.filter { c -> c.isDigit() || c == '.' } }, if (over) "More than is still to come" else null, keyboard = KeyboardType.Decimal)
                    }
                }
            }
            Card("Paperwork") {
                Input("Delivery challan no.", challan, { challan = it }, errors["delivery_challan"])
                Input("Invoice no.", invoiceNo, { invoiceNo = it }, errors["invoice_no"])
                Input("Invoice amount (₹)", invoiceAmount, { invoiceAmount = it.filter { c -> c.isDigit() || c == '.' } }, errors["invoice_amount"], keyboard = KeyboardType.Decimal)
                DatePick("Invoice date", invoiceDate, errors["invoice_date"]) { invoiceDate = it }
                Input("Remarks", remarks, { remarks = it }, errors["remarks"], singleLine = false)
            }
        }
        if (pick) ChoiceSheet("Purchase order", o.objs("receivable_pos").map { Choice(it.optInt("id").toString(), "${it.optString("number")} · ${it.str("vendor") ?: ""}") }, po.takeIf { it > 0 }?.toString(), b, onClose = { pick = false }) { po = it.toIntOrNull() ?: 0; pick = false }
    }
}

/**
 * New supplier / edit supplier — the website's supplier form: name, email, phone (with a portal password for a new one), WhatsApp,
 * company, GST, business type and address. Documents and service categories are added on the website.
 */
@Composable
fun SupplierFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fields = listOf("name", "email", "phone", "password", "whatsapp_number", "company_name", "gst_number", "business_type", "address_line1", "address_line2", "city", "state", "pincode")
    val f = remember { mutableStateMapOf<String, String>() }
    var loaded by remember { mutableStateOf(id == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(attempt) {
        if (id != null) {
            loadError = null
            try {
                val x = withContext(Dispatchers.IO) { api.detail("/procurement-center/suppliers/$id") }
                mapOf("name" to "name", "email" to "email", "phone" to "phone", "whatsapp_number" to "whatsapp", "company_name" to "company", "gst_number" to "gst", "business_type" to "business_type",
                    "address_line1" to "address_line1", "address_line2" to "address_line2", "city" to "city", "state" to "state", "pincode" to "pincode").forEach { (k, src) -> f[k] = x.str(src).orEmpty() }
                loaded = true
            } catch (e: Exception) { loadError = e.message ?: "Could not load" }
        }
        initial = f.toMap()
    }
    val title = if (id != null) "Edit supplier" else "New supplier"
    val back = leaveGuard(initial != null && f.toMap() != initial, onBack)
    if (!loaded) return Waiting(b, title, loadError, onBack) { attempt++ }

    fun save() {
        val e = mutableMapOf<String, String>()
        if (f["name"].isNullOrBlank()) e["name"] = "Enter the supplier's name."
        if (f["email"].isNullOrBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(f["email"]!!.trim()).matches()) e["email"] = "Enter a valid email."
        if (f["phone"].isNullOrBlank()) e["phone"] = "Enter a phone number."
        if (id == null && (f["password"]?.length ?: 0) < 6) e["password"] = "At least 6 characters — the supplier signs in to their portal with it."
        if (e.isNotEmpty()) { errors = e; banner = e.values.first(); return }
        val body = JSONObject()
        fields.forEach { k -> val v = f[k].orEmpty().trim(); if (k == "password") { if (v.isNotEmpty()) body.put(k, v) } else body.put(k, v.ifBlank { JSONObject.NULL }) }
        errors = emptyMap(); banner = null; saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (id != null) api.send("PUT", "/procurement-center/suppliers/$id", body) else api.send("POST", "/projects/$uuid/procurement-center/suppliers", body) }
                Toast.makeText(context, if (id != null) "Supplier saved" else "Supplier added", Toast.LENGTH_SHORT).show(); onSaved()
            } catch (ex: ApiException) { errors = ex.errors; banner = ex.errors.values.firstOrNull { it.isNotBlank() } ?: ex.message }
            catch (ex: Exception) { banner = ex.message ?: "Could not save" }
            saving = false
        }
    }
    FormPage(b, title, back, banner, if (id != null) "Save supplier" else "Add supplier", saving, ::save) {
        Card("Contact") {
            Input("Name", f["name"].orEmpty(), { f["name"] = it }, errors["name"])
            Input("Email", f["email"].orEmpty(), { f["email"] = it }, errors["email"], keyboard = KeyboardType.Email)
            Input("Phone", f["phone"].orEmpty(), { f["phone"] = it.filter { c -> c.isDigit() || c == '+' } }, errors["phone"], keyboard = KeyboardType.Phone)
            Input("WhatsApp", f["whatsapp_number"].orEmpty(), { f["whatsapp_number"] = it.filter { c -> c.isDigit() || c == '+' } }, errors["whatsapp_number"], keyboard = KeyboardType.Phone)
            Input(if (id != null) "New portal password (leave blank to keep)" else "Portal password", f["password"].orEmpty(), { f["password"] = it }, errors["password"], password = true)
        }
        Card("Business") {
            Input("Company", f["company_name"].orEmpty(), { f["company_name"] = it }, errors["company_name"])
            Input("GST no.", f["gst_number"].orEmpty(), { f["gst_number"] = it.uppercase() }, errors["gst_number"])
            Input("Business type", f["business_type"].orEmpty(), { f["business_type"] = it }, errors["business_type"])
        }
        Card("Address") {
            Input("Address line 1", f["address_line1"].orEmpty(), { f["address_line1"] = it }, errors["address_line1"])
            Input("Address line 2", f["address_line2"].orEmpty(), { f["address_line2"] = it }, errors["address_line2"])
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Input("City", f["city"].orEmpty(), { f["city"] = it }, errors["city"]) }
                Box(Modifier.weight(1f)) { Input("State", f["state"].orEmpty(), { f["state"] = it }, errors["state"]) }
            }
            Input("Pincode", f["pincode"].orEmpty(), { f["pincode"] = it.filter(Char::isDigit) }, errors["pincode"], keyboard = KeyboardType.Number)
        }
    }
}
