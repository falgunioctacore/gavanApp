package com.example.gavanhomes.ui.customers

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
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.inventory.Banner
import com.example.gavanhomes.ui.inventory.DatePick
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.SaveBar
import com.example.gavanhomes.ui.inventory.Waiting
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.whenLabel
import com.example.gavanhomes.ui.procurement.ActionButton
import com.example.gavanhomes.ui.procurement.MiniRow
import com.example.gavanhomes.ui.procurement.plain
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
import org.json.JSONObject
import java.time.LocalDate

/** Run [call] off the main thread; toast the server's message (or [ok]) and then [done], or toast the error. */
private fun act(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, ok: String, call: () -> JSONObject, done: () -> Unit) {
    scope.launch {
        try { val r = withContext(Dispatchers.IO) { call() }; Toast.makeText(context, r.str("message") ?: ok, Toast.LENGTH_SHORT).show(); done() }
        catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
    }
}

/** GET [path] → its `data`, with the usual spinner / error + Retry around [content]. */
@Composable
private fun Detail(api: ApiClient, b: Branding, title: String, path: String, version: Int, onBack: () -> Unit, content: @Composable (JSONObject, () -> Unit) -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(path, version, attempt) { error = null; try { d = withContext(Dispatchers.IO) { api.detail(path) } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = d ?: return Waiting(b, title, error, onBack) { attempt++ }
    content(x) { attempt++ }
}

/** A labelled block of facts from a JSON map (label → value). */
@Composable
private fun FactsCard(title: String, o: JSONObject?) {
    val keys = o?.keys()?.asSequence()?.toList().orEmpty()
    if (keys.isEmpty()) return
    Card(title) { keys.forEach { k -> Fact(k, o!!.optString(k)) } }
}

// ───────────────────────────── booking details ─────────────────────────────

/** A booking: buyer, unit and sale value; paid vs due; the applicant, co-applicant and property; the cost sheet; payments. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookingDetailScreen(api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onChanged: () -> Unit, onOpenCustomer: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askDelete by remember { mutableStateOf(false) }
    Detail(api, b, "Booking", "/customer-center/bookings/$id", version, onBack) { x, _ ->
        val can = x.optJSONObject("can") ?: JSONObject()
        Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
            HeroBar(b, "Booking", onBack, actions = { if (can.optBoolean("delete_booking")) DeleteButton { askDelete = true } }) {
                Column {
                    Text(x.optString("customer"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("unit"), x.str("unit_type")).joinToString(" · ").ifEmpty { x.optString("ref") }, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(rupees(x.optDouble("value", 0.0)), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    HeroChips(*listOfNotNull(pretty(x.str("status")), x.str("stage")?.let { pretty(it) }, x.optString("ref"), prettyDay(x.str("date"))).toTypedArray())
                }
            }
            val value = x.optDouble("value", 0.0)
            Card("Payment", "Paid against the sale value") {
                if (value > 0) ProBar((x.optDouble("paid", 0.0) / value).toFloat(), KitGood)
                Fact("Paid", rupees(x.optDouble("paid", 0.0))); Fact("Still due", rupees(x.optDouble("due", 0.0)))
                if (x.optDouble("booking_amount", 0.0) > 0) Fact("Booking amount", rupees(x.optDouble("booking_amount", 0.0)))
                Fact("Payment mode", x.str("payment_mode")); Fact("Payment plan", x.str("payment_plan")?.let { pretty(it) })
                x.str("phone")?.let { com.example.gavanhomes.ui.tasks.PhoneActions(it) }
                if (x.optInt("customer_id") > 0) com.example.gavanhomes.ui.leads.SmallAction("Open customer", InvBlue) { onOpenCustomer(x.optInt("customer_id")) }
            }
            FactsCard("Applicant", x.optJSONObject("applicant"))
            FactsCard("Co-applicant", x.optJSONObject("co_applicant"))
            FactsCard("Property", x.optJSONObject("property"))
            val costs = x.objs("costs")
            if (costs.isNotEmpty()) Card("Cost sheet") { costs.forEach { c -> Fact(c.optString("name"), rupees(c.optDouble("amount", 0.0))) }; Fact("Total", rupees(value)) }
            val pays = x.objs("payments")
            Card("Payments", "${pays.size} recorded") {
                if (pays.isEmpty()) Text("No payments yet.", color = KitMuted, fontSize = 13.sp)
                pays.forEach { p -> MiniRow(rupees(p.optDouble("amount", 0.0)), listOfNotNull(prettyDay(p.str("date")), p.str("method"), p.str("cycle")).joinToString(" · "), null, pretty(p.str("status")) to paymentTint(p.str("status"))) {} }
            }
            x.str("remarks")?.let { Card("Remarks") { Text(it, color = KitInk, fontSize = 14.sp) } }
            Text("New bookings and payment entries are made on the website.", color = KitMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
            Spacer(Modifier.height(16.dp))
        }
        if (askDelete) SwalConfirm(title = "Delete this booking?", text = "The unit goes back to available. A booking with payments can't be deleted.", confirmText = "Yes, delete",
            onConfirm = { askDelete = false; act(context, scope, "Booking deleted", { api.delete("/customer-center/bookings/$id") }) { onChanged(); onBack() } }, onCancel = { askDelete = false })
    }
}

// ───────────────────────────── customer details ─────────────────────────────

/** A customer: contact (call / WhatsApp), identity and address, and their bookings, visits, issues and loans on this project. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerDetailScreen(api: ApiClient, b: Branding, uuid: String, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit, onOpen: (String, Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askDelete by remember { mutableStateOf(false) }
    Detail(api, b, "Customer", "/projects/$uuid/customer-center/customers/$id", version, onBack) { x, _ ->
        val can = x.optJSONObject("can") ?: JSONObject()
        Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
            HeroBar(b, "Customer", onBack, actions = {
                if (can.optBoolean("edit_customer")) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                if (can.optBoolean("delete_customer")) DeleteButton { askDelete = true }
            }) {
                Column {
                    Text(x.optString("name"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("code"), x.str("city")).joinToString(" · ").ifEmpty { "Customer" }, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    HeroChips("${x.optInt("bookings")} booking${if (x.optInt("bookings") == 1) "" else "s"} here")
                }
            }
            x.str("phone")?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { com.example.gavanhomes.ui.tasks.PhoneActions(it) } }
            Card("Contact") {
                Fact("Mobile", x.str("phone")); Fact("WhatsApp", x.str("whatsapp")); Fact("Email", x.str("email"))
                Fact("Address", listOfNotNull(x.str("address_line1"), x.str("address_line2"), x.str("city"), x.str("state"), x.str("pincode")).joinToString(", ").ifBlank { null })
            }
            Card("Identity") { Fact("Gender", x.str("gender")?.let { pretty(it) }); Fact("Date of birth", prettyDay(x.str("dob"))); Fact("Aadhaar", x.str("aadhaar")); Fact("PAN", x.str("pan")); Fact("Occupation", x.str("occupation")) }
            x.objs("booking_list").takeIf { it.isNotEmpty() }?.let { l -> Card("Bookings") { l.forEach { r -> MiniRow(r.optString("ref"), r.str("unit") ?: "", rupees(r.optDouble("value", 0.0)), pretty(r.str("status")) to bookingTint(r.str("status"))) { onOpen("booking", r.optInt("id")) } } } }
            x.objs("visits").takeIf { it.isNotEmpty() }?.let { l -> Card("Visits") { l.forEach { v -> MiniRow(v.optString("type_label"), whenLabel(v.str("at")) ?: "", null, visitStatus(v.str("status"))) { onOpen("visit", v.optInt("id")) } } } }
            x.objs("issues").takeIf { it.isNotEmpty() }?.let { l -> Card("Issues") { l.forEach { i -> MiniRow(i.optString("title"), i.str("priority")?.let { pretty(it) } ?: "", null, issueStatus(i.str("status"))) { onOpen("issue", i.optInt("id")) } } } }
            x.objs("loans").takeIf { it.isNotEmpty() }?.let { l -> Card("Loans") { l.forEach { n -> MiniRow(n.str("bank") ?: "Loan", "Balance ${rupees(n.optDouble("balance", 0.0))}", rupees(n.optDouble("principal", 0.0)), pretty(n.str("status")) to loanTint(n.str("status"))) { onOpen("loan", n.optInt("id")) } } } }
            Spacer(Modifier.height(28.dp))
        }
        if (askDelete) SwalConfirm(title = "Delete ${x.optString("name")}?", text = "A customer with bookings can't be deleted.", confirmText = "Yes, delete",
            onConfirm = { askDelete = false; act(context, scope, "Customer deleted", { api.delete("/projects/$uuid/customer-center/customers/$id") }) { onChanged(); onBack() } }, onCancel = { askDelete = false })
    }
}

// ───────────────────────────── visit details ─────────────────────────────

/** A client visit: who, when, why; Approve (staff + final time), Reject (reason), Record outcome; Edit, Delete. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VisitDetailScreen(api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) }         // approve · reject · track · delete
    Detail(api, b, "Visit", "/customer-center/visits/$id", version, onBack) { x, reload ->
        val can = x.optJSONObject("can") ?: JSONObject()
        val (sl, _) = visitStatus(x.str("status"))
        Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
            HeroBar(b, "Client visit", onBack, actions = {
                if (can.optBoolean("edit_visit")) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                if (can.optBoolean("delete_visit")) DeleteButton { sheet = "delete" }
            }) {
                Column {
                    Text(x.str("customer") ?: "Visit", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("type_label"), whenLabel(x.str("at"))).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(sl, x.str("staff")?.let { "👤 $it" }).toTypedArray())
                }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (can.optBoolean("approve")) ActionButton("✓  Approve", KitGood) { sheet = "approve" }
                if (can.optBoolean("track")) ActionButton("✎  Record outcome", InvBlue) { sheet = "track" }
                if (can.optBoolean("reject")) ActionButton("✕  Reject", KitDanger) { sheet = "reject" }
            }
            x.str("phone")?.let { Box(Modifier.padding(horizontal = 16.dp)) { com.example.gavanhomes.ui.tasks.PhoneActions(it) } }
            Card("Visit") {
                Fact("Purpose", x.str("purpose")); Fact("Details", x.str("details")); Fact("When", whenLabel(x.str("at"))); Fact("Staff", x.str("staff")); Fact("Staff notes", x.str("notes"))
            }
            if (x.str("outcome") != null || x.str("actual") != null) Card("Outcome") {
                Fact("Visited", whenLabel(x.str("actual"))); Fact("Outcome", x.str("outcome")); Fact("Follow-up", x.str("follow_up")?.let { prettyDay(it) ?: it }); Fact("Follow-up notes", x.str("follow_up_notes"))
                Fact("Customer feedback", x.str("feedback"))
            }
            Spacer(Modifier.height(28.dp))
        }
        when (sheet) {
            "approve" -> VisitApproveSheet(b, x, onClose = { sheet = null }) { body -> sheet = null; act(context, scope, "Visit approved", { api.send("POST", "/customer-center/visits/$id/approve", body) }) { reload(); onChanged() } }
            "track" -> VisitTrackSheet(b, onClose = { sheet = null }) { body -> sheet = null; act(context, scope, "Visit updated", { api.send("POST", "/customer-center/visits/$id/track", body) }) { reload(); onChanged() } }
            "reject" -> TextSheet("Reject this visit", "Reason (shared with the customer)", "Reject visit", KitDanger, required = false, onClose = { sheet = null }) { t -> sheet = null; act(context, scope, "Visit rejected", { api.send("POST", "/customer-center/visits/$id/reject", JSONObject().put("reason", t)) }) { reload(); onChanged() } }
            "delete" -> SwalConfirm(title = "Delete this visit?", text = "It will be removed from the project.", confirmText = "Yes, delete",
                onConfirm = { sheet = null; act(context, scope, "Visit deleted", { api.delete("/customer-center/visits/$id") }) { onChanged(); onBack() } }, onCancel = { sheet = null })
        }
    }
}

/** A text box in a sheet (a reason, a note, a comment), then [onDone] with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextSheet(title: String, label: String, button: String, c: Color, required: Boolean = true, onClose: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Input(label, text, { text = it; error = null }, error, singleLine = false)
            Text(button, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c).clickable { if (required && text.isBlank()) error = "Please fill this in." else onDone(text.trim()) }.padding(vertical = 15.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitApproveSheet(b: Branding, v: JSONObject, onClose: () -> Unit, onDone: (JSONObject) -> Unit) {
    val context = LocalContext.current
    var staff by remember { mutableStateOf(v.str("staff").orEmpty()) }
    var at by remember { mutableStateOf(v.str("at") ?: (LocalDate.now().toString() + " 10:00")) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Approve visit", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Input("Staff meeting the customer", staff, { staff = it; error = null }, error)
            PickerField("Final date & time", whenLabel(at)) { pickDateTime(context, at) { at = it } }
            Input("Notes for the staff", notes, { notes = it }, singleLine = false)
            ActionButton("✓  Approve & schedule", KitGood) { if (staff.isBlank()) error = "Who meets the customer?" else onDone(JSONObject().put("assigned_staff", staff.trim()).put("final_visit_at", at).put("staff_notes", notes.trim().ifBlank { JSONObject.NULL })) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VisitTrackSheet(b: Branding, onClose: () -> Unit, onDone: (JSONObject) -> Unit) {
    var status by remember { mutableStateOf("completed") }
    var outcome by remember { mutableStateOf("") }
    var follow by remember { mutableStateOf(false) }
    var followDate by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Record outcome", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("completed", "rescheduled", "scheduled", "cancelled").forEach { s -> Pick(pretty(s), status == s, visitStatus(s).second) { status = s } }
            }
            Input("What happened?", outcome, { outcome = it; error = null }, error, singleLine = false)
            com.example.gavanhomes.ui.projects.form.SwitchRow("Follow-up needed", null, follow) { follow = it }
            if (follow) DatePick("Follow-up date", followDate) { followDate = it }
            ActionButton("Save outcome", b.base) {
                if (outcome.isBlank()) error = "Describe the outcome."
                else onDone(JSONObject().put("status", status).put("visit_outcome", outcome.trim()).put("follow_up_required", if (follow) "yes" else "no").put("follow_up_date", if (follow && followDate.isNotBlank()) followDate else JSONObject.NULL))
            }
        }
    }
}

/** Date then time dialogs → "yyyy-MM-dd HH:mm". */
internal fun pickDateTime(context: android.content.Context, current: String?, set: (String) -> Unit) {
    val d = runCatching { LocalDate.parse(current!!.take(10)) }.getOrDefault(LocalDate.now())
    android.app.DatePickerDialog(context, { _, y, m, day ->
        val date = LocalDate.of(y, m + 1, day).toString()
        val h = current?.drop(11)?.take(2)?.toIntOrNull() ?: 10; val mi = current?.drop(14)?.take(2)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(context, { _, hh, mm -> set("$date ${"%02d".format(hh)}:${"%02d".format(mm)}") }, h, mi, false).show()
    }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

// ───────────────────────────── issue details ─────────────────────────────

/**
 * An issue, through the website's workflow: Assign → Start work → Resolve (notes) → customer Feedback (4★+ closes it, lower sends it
 * back to work) or Close; plus the comment thread. Edit, Delete.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssueDetailScreen(api: ApiClient, b: Branding, uuid: String, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) }         // assign · resolve · feedback · comment · delete
    var users by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(uuid) { users = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/customer-center/options").objs("users") } }.getOrDefault(emptyList()) }
    Detail(api, b, "Issue", "/customer-center/issues/$id", version, onBack) { x, reload ->
        val can = x.optJSONObject("can") ?: JSONObject()
        fun step(action: String, body: JSONObject = JSONObject(), ok: String) = act(context, scope, ok, { api.send("POST", "/customer-center/issues/$id/$action", body) }) { reload(); onChanged() }
        Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
            HeroBar(b, "Issue", onBack, actions = {
                if (can.optBoolean("edit_issue")) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                if (can.optBoolean("delete_issue")) DeleteButton { sheet = "delete" }
            }) {
                Column {
                    Text(x.optString("title"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("customer"), x.str("category")).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(issueStatus(x.str("status")).first, x.str("priority")?.let { pretty(it) }, x.str("assignee")?.let { "👤 $it" }).toTypedArray())
                }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (can.optBoolean("assign")) ActionButton(if (x.str("assignee") != null) "👤  Reassign" else "👤  Assign", InvBlue) { sheet = "assign" }
                if (can.optBoolean("start")) ActionButton("▶  Start work", InvPurple) { step("start", ok = "Work started") }
                if (can.optBoolean("resolve")) ActionButton("✓  Resolve", KitGood) { sheet = "resolve" }
                if (can.optBoolean("feedback")) ActionButton("★  Feedback", InvAmber) { sheet = "feedback" }
                if (can.optBoolean("close")) ActionButton("Close", KitInk) { step("close", ok = "Issue closed") }
            }
            x.str("phone")?.let { Box(Modifier.padding(horizontal = 16.dp)) { com.example.gavanhomes.ui.tasks.PhoneActions(it) } }
            Card("Issue") {
                x.str("description")?.let { Text(it, color = KitInk, fontSize = 14.sp, lineHeight = 20.sp) }
                Fact("Raised by", x.str("raised_by")); Fact("Raised", whenLabel(x.str("created_at"))); Fact("Assigned", whenLabel(x.str("assigned_at")))
                Fact("Started", whenLabel(x.str("started_at"))); Fact("Resolved", whenLabel(x.str("resolved_at"))); Fact("Closed", whenLabel(x.str("closed_at")))
            }
            if (x.str("resolution") != null || x.str("feedback") != null) Card("Resolution") {
                Fact("How it was fixed", x.str("resolution"))
                if (!x.isNull("rating")) Fact("Customer rating", "★".repeat(x.optInt("rating").coerceIn(0, 5)))
                Fact("Customer feedback", x.str("feedback"))
            }
            val comments = x.objs("comments")
            Card("Updates & comments", "${comments.size} so far") {
                comments.forEach { c ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (c.str("type") == "status_update") InvBlue.copy(alpha = .08f) else Color(0xFFF7F5F1)).padding(12.dp)) {
                        Text(listOfNotNull(c.str("by"), whenLabel(c.str("at"))).joinToString(" · "), color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(c.optString("text"), color = KitInk, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                if (can.optBoolean("comment")) com.example.gavanhomes.ui.leads.SmallAction("+  Add comment", b.base) { sheet = "comment" }
            }
            Spacer(Modifier.height(28.dp))
        }
        when (sheet) {
            "assign" -> ChoiceSheet("Assign to", users.map { Choice(it.optInt("id").toString(), it.optString("name")) }, null, b, onClose = { sheet = null }) { u -> sheet = null; step("assign", JSONObject().put("assigned_to", u.toInt()), "Issue assigned") }
            "resolve" -> TextSheet("Resolve issue", "How was it fixed?", "Mark resolved", KitGood, onClose = { sheet = null }) { t -> sheet = null; step("resolve", JSONObject().put("resolution_notes", t), "Issue resolved") }
            "comment" -> TextSheet("Add comment", "Comment", "Post comment", b.base, onClose = { sheet = null }) { t -> sheet = null; step("comment", JSONObject().put("comment", t), "Comment added") }
            "feedback" -> FeedbackSheet(onClose = { sheet = null }) { rating, text -> sheet = null; step("feedback", JSONObject().put("rating", rating).put("feedback", text), "Feedback recorded") }
            "delete" -> SwalConfirm(title = "Delete this issue?", text = "It and its comments will be removed.", confirmText = "Yes, delete",
                onConfirm = { sheet = null; act(context, scope, "Issue deleted", { api.delete("/customer-center/issues/$id") }) { onChanged(); onBack() } }, onCancel = { sheet = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackSheet(onClose: () -> Unit, onDone: (Int, String) -> Unit) {
    var rating by remember { mutableIntStateOf(5) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Customer feedback", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Text("4★ or more closes the issue; lower sends it back to work.", color = KitMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..5).forEach { n -> Text(if (n <= rating) "★" else "☆", color = InvAmber, fontSize = 34.sp, modifier = Modifier.clickable { rating = n }) } }
            Input("What did the customer say?", text, { text = it; error = null }, error, singleLine = false)
            ActionButton("Save feedback", InvAmber) { if (text.isBlank()) error = "Add the customer's words." else onDone(rating, text.trim()) }
        }
    }
}

// ───────────────────────────── loan details ─────────────────────────────

@Composable
fun LoanDetailScreen(api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askDelete by remember { mutableStateOf(false) }
    Detail(api, b, "Loan", "/customer-center/loans/$id", version, onBack) { x, _ ->
        val can = x.optJSONObject("can") ?: JSONObject()
        Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
            HeroBar(b, "Loan", onBack, actions = {
                if (can.optBoolean("edit_loan")) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                if (can.optBoolean("delete_loan")) DeleteButton { askDelete = true }
            }) {
                Column {
                    Text(x.str("customer") ?: "Loan", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("bank"), x.str("type")).joinToString(" · ").ifEmpty { "Home loan" }, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(rupees(x.optDouble("principal", 0.0)), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    HeroChips(pretty(x.str("status")), "Balance ${rupees(x.optDouble("balance", 0.0))}")
                }
            }
            Card("Loan") {
                Fact("Interest rate", if (x.isNull("rate")) null else "${x.optDouble("rate", 0.0)}%"); Fact("Starts", prettyDay(x.str("start"))); Fact("Due", prettyDay(x.str("due")))
                Fact("Disbursed", x.optDouble("disbursed", 0.0).takeIf { it > 0 }?.let { rupees(it) }); Fact("Processing fee", x.optDouble("processing_fee", 0.0).takeIf { it > 0 }?.let { rupees(it) })
                Fact("Branch", x.str("branch")); Fact("Loan account", x.str("account")); Fact("Reference", x.str("reference"))
                Fact("Loan officer", x.str("officer")); Fact("Officer contact", x.str("officer_phone"))
            }
            x.str("officer_phone")?.let { Box(Modifier.padding(horizontal = 16.dp)) { com.example.gavanhomes.ui.tasks.PhoneActions(it) } }
            x.str("terms")?.let { Card("Terms") { Text(it, color = KitInk, fontSize = 14.sp) } }
            x.str("notes")?.let { Card("Notes") { Text(it, color = KitInk, fontSize = 14.sp) } }
            Spacer(Modifier.height(28.dp))
        }
        if (askDelete) SwalConfirm(title = "Delete this loan?", text = "It will be removed from the project.", confirmText = "Yes, delete",
            onConfirm = { askDelete = false; act(context, scope, "Loan deleted", { api.delete("/customer-center/loans/$id") }) { onChanged(); onBack() } }, onCancel = { askDelete = false })
    }
}

// ───────────────────────────── forms ─────────────────────────────

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

/** A searchable list of people / customers in a sheet; [onPick] gets the chosen id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPick(title: String, rows: List<JSONObject>, sub: (JSONObject) -> String?, b: Branding, onClose: () -> Unit, onPick: (JSONObject) -> Unit) {
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.padding(horizontal = 20.dp))
            com.example.gavanhomes.ui.common.SearchBox(q, { q = it }, "Search…")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.filter { q.isBlank() || it.optString("name").contains(q, true) || sub(it).orEmpty().contains(q, true) }.take(200).forEach { r ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onPick(r) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.example.gavanhomes.ui.common.Initials(r.optString("name"), size = 30.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(r.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            sub(r)?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

/** Load the module's pickers (…/customer-center/options), then [content]. */
@Composable
private fun WithOptions(api: ApiClient, b: Branding, uuid: String, title: String, onBack: () -> Unit, content: @Composable (JSONObject) -> Unit) {
    var o by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { error = null; try { o = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/customer-center/options") } } catch (e: Exception) { error = e.message ?: "Could not load the form" } }
    val x = o ?: return Waiting(b, title, error, onBack) { attempt++ }
    content(x)
}

private fun customerSub(c: JSONObject): String? = listOfNotNull(c.str("customer_code"), c.str("mobile_number")).joinToString(" · ").ifBlank { null }

/** Saves [body] (POST to [create] or PUT to [update]); 422 field errors land in [setErrors]. */
private fun submit(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, api: ApiClient, update: String?, create: String, body: JSONObject, ok: String,
                   setSaving: (Boolean) -> Unit, setErrors: (Map<String, String>, String?) -> Unit, done: () -> Unit) {
    setSaving(true)
    scope.launch {
        try { withContext(Dispatchers.IO) { if (update != null) api.send("PUT", update, body) else api.send("POST", create, body) }; Toast.makeText(context, ok, Toast.LENGTH_SHORT).show(); done() }
        catch (e: ApiException) { setErrors(e.errors, e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message) }
        catch (e: Exception) { setErrors(emptyMap(), e.message ?: "Could not save") }
        setSaving(false)
    }
}

/** New customer / edit customer — the website's form: name, contact, identity and address. */
@Composable
fun CustomerFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = remember { mutableStateMapOf<String, String>() }
    var loaded by remember { mutableStateOf(id == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<Map<String, String>?>(null) }
    val fields = listOf("name", "email", "mobile_number", "whatsapp_number", "aadhaar_number", "pan_number", "date_of_birth", "gender", "address_line1", "address_line2", "city", "state", "pincode")
    LaunchedEffect(attempt) {
        if (id != null) try {
            val x = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/customer-center/customers/$id") }
            mapOf("name" to "name", "email" to "email", "mobile_number" to "phone", "whatsapp_number" to "whatsapp", "aadhaar_number" to "aadhaar", "pan_number" to "pan", "date_of_birth" to "dob",
                "gender" to "gender", "address_line1" to "address_line1", "address_line2" to "address_line2", "city" to "city", "state" to "state", "pincode" to "pincode").forEach { (k, s) -> f[k] = x.str(s).orEmpty() }
            loaded = true
        } catch (e: Exception) { loadError = e.message ?: "Could not load" }
        initial = f.toMap()
    }
    val title = if (id != null) "Edit customer" else "New customer"
    val back = leaveGuard(initial != null && f.toMap() != initial, onBack)
    if (!loaded) return Waiting(b, title, loadError, onBack) { attempt++ }
    fun save() {
        if (f["name"].isNullOrBlank()) { errors = mapOf("name" to "Enter the customer's name."); banner = errors["name"]; return }
        val body = JSONObject(); fields.forEach { k -> body.put(k, f[k].orEmpty().trim().ifBlank { JSONObject.NULL }) }
        errors = emptyMap(); banner = null
        submit(context, scope, api, id?.let { "/projects/$uuid/customer-center/customers/$it" }, "/projects/$uuid/customer-center/customers", body, if (id != null) "Customer saved" else "Customer added",
            { saving = it }, { e, m -> errors = e; banner = m }, onSaved)
    }
    FormPage(b, title, back, banner, if (id != null) "Save customer" else "Add customer", saving, ::save) {
        Card("Contact") {
            Input("Name", f["name"].orEmpty(), { f["name"] = it }, errors["name"])
            Input("Mobile", f["mobile_number"].orEmpty(), { f["mobile_number"] = it.filter { c -> c.isDigit() || c == '+' } }, errors["mobile_number"], keyboard = KeyboardType.Phone)
            Input("WhatsApp", f["whatsapp_number"].orEmpty(), { f["whatsapp_number"] = it.filter { c -> c.isDigit() || c == '+' } }, errors["whatsapp_number"], keyboard = KeyboardType.Phone)
            Input("Email", f["email"].orEmpty(), { f["email"] = it }, errors["email"], keyboard = KeyboardType.Email)
        }
        Card("Identity") {
            Label("Gender")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("male", "female", "other").forEach { g -> Pick(pretty(g), f["gender"] == g, InvBlue) { f["gender"] = if (f["gender"] == g) "" else g } } }
            DatePick("Date of birth", f["date_of_birth"].orEmpty(), errors["date_of_birth"]) { f["date_of_birth"] = it }
            Input("Aadhaar no.", f["aadhaar_number"].orEmpty(), { f["aadhaar_number"] = it.filter(Char::isDigit) }, errors["aadhaar_number"], keyboard = KeyboardType.Number)
            Input("PAN", f["pan_number"].orEmpty(), { f["pan_number"] = it.uppercase() }, errors["pan_number"])
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

/** Schedule a visit / edit one — customer, type, date and time, purpose, details. (Scheduled from here it is approved straight away, as on the website.) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisitFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customer by remember { mutableStateOf<JSONObject?>(null) }
    var type by remember { mutableStateOf("site_visit") }
    var at by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(id == null) }
    LaunchedEffect(id) {
        if (id != null) runCatching { withContext(Dispatchers.IO) { api.detail("/customer-center/visits/$id") } }.getOrNull()?.let { x ->
            customer = JSONObject().put("id", x.optInt("customer_id")).put("name", x.optString("customer")); type = x.str("type") ?: "site_visit"
            at = x.str("at").orEmpty(); purpose = x.str("purpose").orEmpty(); details = x.str("details").orEmpty(); loaded = true
        }
    }
    val title = if (id != null) "Edit visit" else "Schedule visit"
    val back = leaveGuard(purpose.isNotBlank() || customer != null, onBack)
    WithOptions(api, b, uuid, title, onBack) { o ->
        if (!loaded) return@WithOptions Waiting(b, title, null, onBack) {}
        fun save() {
            val e = mutableMapOf<String, String>()
            if (customer == null) e["customer_id"] = "Choose the customer."
            if (purpose.isBlank()) e["purpose"] = "Why are they visiting?"
            if (e.isNotEmpty()) { errors = e; banner = e.values.first(); return }
            val body = JSONObject().put("customer_id", customer!!.optInt("id")).put("visit_type", type).put("purpose", purpose.trim()).put("visit_details", details.trim().ifBlank { JSONObject.NULL })
                .put("preferred_date", at.take(10).ifBlank { JSONObject.NULL }).put("preferred_time", at.drop(11).take(5).ifBlank { JSONObject.NULL })
            errors = emptyMap(); banner = null
            submit(context, scope, api, id?.let { "/customer-center/visits/$it" }, "/projects/$uuid/customer-center/visits", body, if (id != null) "Visit saved" else "Visit scheduled", { saving = it }, { er, m -> errors = er; banner = m }, onSaved)
        }
        FormPage(b, title, back, banner, if (id != null) "Save visit" else "Schedule visit", saving, ::save) {
            Card("Who and when") {
                PickerField("Customer", customer?.optString("name"), errors["customer_id"]) { pick = true }
                PickerField("Date & time", whenLabel(at)) { pickDateTime(context, at.ifBlank { null }) { at = it } }
                Label("Type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    o.objs("visit_types").forEach { t -> Pick(t.optString("label"), type == t.optString("key"), InvBlue) { type = t.optString("key") } }
                }
            }
            Card("Why") {
                Input("Purpose", purpose, { purpose = it }, errors["purpose"])
                Input("Details", details, { details = it }, errors["visit_details"], singleLine = false)
            }
        }
        if (pick) SearchPick("Customer", o.objs("customers"), ::customerSub, b, onClose = { pick = false }) { customer = it; pick = false }
    }
}

/** Raise an issue / edit one — customer, category, priority, title, description. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssueFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customer by remember { mutableStateOf<JSONObject?>(null) }
    var category by remember { mutableIntStateOf(0) }
    var priority by remember { mutableStateOf("medium") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(id == null) }
    LaunchedEffect(id) {
        if (id != null) runCatching { withContext(Dispatchers.IO) { api.detail("/customer-center/issues/$id") } }.getOrNull()?.let { x ->
            customer = JSONObject().put("id", x.optInt("customer_id")).put("name", x.optString("customer")); category = x.optInt("category_id")
            priority = x.str("priority") ?: "medium"; title = x.optString("title"); description = x.str("description").orEmpty(); loaded = true
        }
    }
    val heading = if (id != null) "Edit issue" else "Raise issue"
    val back = leaveGuard(title.isNotBlank() || description.isNotBlank(), onBack)
    WithOptions(api, b, uuid, heading, onBack) { o ->
        if (!loaded) return@WithOptions Waiting(b, heading, null, onBack) {}
        fun save() {
            val e = mutableMapOf<String, String>()
            if (id == null && customer == null) e["customer_id"] = "Choose the customer."
            if (category <= 0) e["issue_category_id"] = "Choose a category."
            if (title.isBlank()) e["title"] = "Give it a short title."
            if (description.isBlank()) e["description"] = "Describe the issue."
            if (e.isNotEmpty()) { errors = e; banner = e.values.first(); return }
            val body = JSONObject().put("issue_category_id", category).put("priority", priority).put("title", title.trim()).put("description", description.trim())
            if (id == null) body.put("customer_id", customer!!.optInt("id"))
            errors = emptyMap(); banner = null
            submit(context, scope, api, id?.let { "/customer-center/issues/$it" }, "/projects/$uuid/customer-center/issues", body, if (id != null) "Issue saved" else "Issue raised", { saving = it }, { er, m -> errors = er; banner = m }, onSaved)
        }
        FormPage(b, heading, back, banner, if (id != null) "Save issue" else "Raise issue", saving, ::save) {
            Card("Issue") {
                if (id == null) PickerField("Customer", customer?.optString("name"), errors["customer_id"]) { sheet = "customer" } else Fact("Customer", customer?.optString("name"))
                PickerField("Category", o.objs("categories").firstOrNull { it.optInt("id") == category }?.optString("name"), errors["issue_category_id"]) { sheet = "category" }
                Label("Priority")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("urgent", "high", "medium", "low").forEach { p -> Pick(pretty(p), priority == p, priorityTint(p)) { priority = p } } }
                Input("Title", title, { title = it }, errors["title"])
                Input("Description", description, { description = it }, errors["description"], singleLine = false)
            }
        }
        when (sheet) {
            "customer" -> SearchPick("Customer", o.objs("customers"), ::customerSub, b, onClose = { sheet = null }) { customer = it; sheet = null }
            "category" -> ChoiceSheet("Category", o.objs("categories").map { Choice(it.optInt("id").toString(), it.optString("name")) }, category.takeIf { it > 0 }?.toString(), b, onClose = { sheet = null }) { category = it.toIntOrNull() ?: 0; sheet = null }
        }
    }
}

/** New loan / edit loan — customer, amount, interest, start / due dates, balance, status, terms, notes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoanFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customer by remember { mutableStateOf<JSONObject?>(null) }
    val f = remember { mutableStateMapOf("status" to "active") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(id == null) }
    LaunchedEffect(id) {
        if (id != null) runCatching { withContext(Dispatchers.IO) { api.detail("/customer-center/loans/$id") } }.getOrNull()?.let { x ->
            customer = JSONObject().put("id", x.optInt("customer_id")).put("name", x.optString("customer"))
            f["principal_amount"] = plain(x.optDouble("principal", 0.0)); f["balance"] = plain(x.optDouble("balance", 0.0))
            f["interest_rate"] = if (x.isNull("rate")) "" else plain(x.optDouble("rate", 0.0)); f["start_date"] = x.str("start").orEmpty(); f["due_date"] = x.str("due").orEmpty()
            f["status"] = x.str("status") ?: "active"; f["terms"] = x.str("terms").orEmpty(); f["notes"] = x.str("notes").orEmpty(); loaded = true
        }
    }
    val heading = if (id != null) "Edit loan" else "New loan"
    val back = leaveGuard(customer != null || !f["principal_amount"].isNullOrBlank(), onBack)
    WithOptions(api, b, uuid, heading, onBack) { o ->
        if (!loaded) return@WithOptions Waiting(b, heading, null, onBack) {}
        fun save() {
            val e = mutableMapOf<String, String>()
            if (customer == null) e["customer_id"] = "Choose the customer."
            if ((f["principal_amount"]?.toDoubleOrNull() ?: 0.0) <= 0) e["principal_amount"] = "Enter the loan amount."
            if (e.isNotEmpty()) { errors = e; banner = e.values.first(); return }
            val num = { k: String -> f[k]?.toDoubleOrNull() ?: JSONObject.NULL }
            val body = JSONObject().put("customer_id", customer!!.optInt("id")).put("principal_amount", num("principal_amount")).put("interest_rate", num("interest_rate")).put("balance", num("balance"))
                .put("start_date", f["start_date"].orEmpty().ifBlank { JSONObject.NULL }).put("due_date", f["due_date"].orEmpty().ifBlank { JSONObject.NULL }).put("status", f["status"])
                .put("terms", f["terms"].orEmpty().trim().ifBlank { JSONObject.NULL }).put("notes", f["notes"].orEmpty().trim().ifBlank { JSONObject.NULL })
            errors = emptyMap(); banner = null
            submit(context, scope, api, id?.let { "/customer-center/loans/$it" }, "/projects/$uuid/customer-center/loans", body, if (id != null) "Loan saved" else "Loan added", { saving = it }, { er, m -> errors = er; banner = m }, onSaved)
        }
        val n = { k: String -> { v: String -> f[k] = v.filter { c -> c.isDigit() || c == '.' } } }
        FormPage(b, heading, back, banner, if (id != null) "Save loan" else "Add loan", saving, ::save) {
            Card("Loan") {
                PickerField("Customer", customer?.optString("name"), errors["customer_id"]) { pick = true }
                Input("Loan amount (₹)", f["principal_amount"].orEmpty(), n("principal_amount"), errors["principal_amount"], keyboard = KeyboardType.Decimal)
                Input("Interest rate (%)", f["interest_rate"].orEmpty(), n("interest_rate"), errors["interest_rate"], keyboard = KeyboardType.Decimal)
                Input("Balance (₹) — the full amount when left blank", f["balance"].orEmpty(), n("balance"), errors["balance"], keyboard = KeyboardType.Decimal)
                DatePick("Start date", f["start_date"].orEmpty(), errors["start_date"]) { f["start_date"] = it }
                DatePick("Due date", f["due_date"].orEmpty(), errors["due_date"]) { f["due_date"] = it }
                Label("Status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    o.optJSONArray("loan_statuses")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().forEach { s -> Pick(pretty(s), f["status"] == s, loanTint(s)) { f["status"] = s } }
                }
            }
            Card("Notes") {
                Input("Terms", f["terms"].orEmpty(), { f["terms"] = it }, errors["terms"], singleLine = false)
                Input("Notes", f["notes"].orEmpty(), { f["notes"] = it }, errors["notes"], singleLine = false)
            }
        }
        if (pick) SearchPick("Customer", o.objs("customers"), ::customerSub, b, onClose = { pick = false }) { customer = it; pick = false }
    }
}
