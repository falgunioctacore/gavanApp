package com.example.gavanhomes.ui.bids

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.PillTab
import com.example.gavanhomes.ui.common.PillTabs
import com.example.gavanhomes.ui.common.ScrollTopButton
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.tasks.CallChip
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.Label
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.SendButton
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Blue = Color(0xFF1E5FAE)
private val Amber = Color(0xFFC47A1A)

// ───────────────────────────── bid details ─────────────────────────────

/**
 * One bid, like the website's bid page: **Details** (dates, description, terms, send to suppliers), **Items** (what is being asked for), **Suppliers**
 * (who was invited) and **Submissions** (each supplier's quote, with Accept / Reject).
 * Data: GET /bid-center/{id} · POST …/send · POST /bid-center/submissions/{id}/accept | reject · DELETE /bid-center/{id}.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BidDetailScreen(api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit, initialTab: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(initialTab) }
    var busy by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var askSend by remember { mutableStateOf(false) }
    var acceptFor by remember { mutableStateOf<JSONObject?>(null) }
    var rejectFor by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(id, version, attempt) {
        error = null
        try { d = withContext(Dispatchers.IO) { api.detail("/bid-center/$id") } } catch (e: Exception) { error = e.message ?: "Could not load the bid" }
    }
    BackHandler(enabled = true) { onBack() }

    fun act(success: String?, call: () -> JSONObject?, after: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { call() }
                val msg = success ?: r?.optString("message")?.takeIf { it.isNotBlank() }
                if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                after(); onChanged(); attempt++
            } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not do that", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        val x = d
        if (x == null) {
            HeroBar(b, "Bid", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = b.base)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
                }
            }
            return@Column
        }
        val can = x.optJSONObject("can")
        val canEdit = can?.optBoolean("edit") == true
        val canSend = can?.optBoolean("send") == true
        val canDelete = can?.optBoolean("delete") == true
        val canReview = can?.optBoolean("review") == true
        val items = x.objs("items"); val suppliers = x.objs("supplier_list"); val subs = x.objs("submission_list")
        val pageScroll = rememberScrollState()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize().verticalScroll(pageScroll).imePadding()) {
                HeroBar(
                    b, "Bid", onBack,
                    actions = {
                        if (canEdit) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                        if (canDelete) DeleteButton { askDelete = true }
                    },
                ) {
                    Column {
                        Text(x.optString("title"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(x.str("project") ?: "", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        HeroChips(*listOfNotNull(x.str("status_label"), bidDay(x.str("end_date"))?.let { "Ends $it" }, if (x.optBoolean("locked")) "Sent" else null).toTypedArray())
                    }
                }

                PillTabs(listOf(PillTab("Details"), PillTab("Items", items.size), PillTab("Suppliers", suppliers.size), PillTab("Submissions", subs.size)), tab, b.base) { tab = it }

                when (tab) {
                    0 -> {
                        if (canSend) Text(
                            "➤  Send to suppliers", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(b.base).clickable { askSend = true }.padding(vertical = 14.dp),
                        )
                        x.str("awarded_supplier")?.let { s -> Card("Awarded to") { Text(s, color = KitGood, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) } }
                        Card("When") {
                            Fact("Starts", bidDay(x.str("start_date"), true))
                            Fact("Ends", bidDay(x.str("end_date"), true))
                            Fact("Status", x.str("status_label"))
                            Fact("Created", listOfNotNull(bidDay(x.str("created_at")), x.str("created_by")?.let { "by $it" }).joinToString(" "))
                        }
                        x.str("description")?.let { Card("Description") { Text(it, color = KitInk, fontSize = 14.sp, lineHeight = 21.sp) } }
                        x.str("special_requirements")?.let { Card("Special requirements") { Text(it, color = KitInk, fontSize = 14.sp, lineHeight = 21.sp) } }
                        x.str("terms")?.let { t -> Card("Terms & conditions", t) { (x.str("terms_html") ?: x.str("terms_content"))?.let { c -> com.example.gavanhomes.ui.common.HtmlText(c, color = KitInk, fontSize = 14.sp) } } }
                    }

                    1 -> Card("Items", if (items.isEmpty()) "Nothing added" else "${items.size} asked for") {
                        if (items.isEmpty()) Text("No items.", color = KitMuted, fontSize = 14.sp)
                        items.forEach { i ->
                            val service = i.optString("kind") == "service"
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Chip(if (service) "Service" else "Material", if (service) Amber else Blue)
                                Text(i.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                                Text(listOfNotNull(i.str("quantity")?.removeSuffix(".00"), i.str("unit")).joinToString(" "), color = KitMuted, fontSize = 13.sp)
                            }
                        }
                    }

                    2 -> Card("Suppliers", if (suppliers.isEmpty()) "Nobody invited" else "${suppliers.size} invited") {
                        if (suppliers.isEmpty()) Text("No suppliers.", color = KitMuted, fontSize = 14.sp)
                        suppliers.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Initials(s.optString("name"), size = 40.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(s.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(listOfNotNull(s.str("phone"), s.str("email")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                                }
                                s.str("phone")?.let { CallChip(it) }
                            }
                        }
                    }

                    else -> {
                        if (subs.isEmpty()) Text("No submissions yet.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp))
                        subs.forEach { s ->
                            val st = s.optString("status")
                            val (label, c) = when (st) { "approved" -> "Accepted" to KitGood; "rejected" -> "Rejected" to KitDanger; else -> "Pending" to Amber }
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(s.optString("supplier"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                        Text(bidDay(s.str("submitted_at"), true) ?: "", color = KitMuted, fontSize = 12.sp)
                                    }
                                    Chip(label, c)
                                }
                                Text(rupees(s.optDouble("amount")), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                                s.objs("lines").forEach { l ->
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(l.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text("${l.str("quantity")?.removeSuffix(".00") ?: "—"} × ${rupees(l.optDouble("rate"))}" + (l.str("gst")?.let { " + $it% GST" } ?: ""), color = KitMuted, fontSize = 12.sp)
                                        }
                                        Text(rupees(l.optDouble("total")), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                s.str("proposal")?.let { Text(it, color = KitInk, fontSize = 13.sp, lineHeight = 19.sp) }
                                s.str("rejection_reason")?.let { Text("Reason: $it", color = KitDanger, fontSize = 12.sp) }
                                if (st == "submitted" && canReview) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Accept", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(KitGood).clickable { acceptFor = s }.padding(vertical = 12.dp))
                                    Text("Reject", color = KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).clickable { rejectFor = s }.padding(vertical = 12.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
            ScrollTopButton(pageScroll)
        }

        if (askSend) SwalConfirm(
            title = "Send to suppliers?", text = "Every supplier on this bid is told, and the bid is locked — it can no longer be edited.", confirmText = "Yes, send it", confirmColor = b.base,
            onConfirm = { askSend = false; act(null, { api.send("POST", "/bid-center/$id/send", JSONObject()) }) }, onCancel = { askSend = false },
        )
        if (askDelete) SwalConfirm(
            title = "Delete this bid?", text = "It will be removed from the project.", confirmText = "Yes, delete it",
            onConfirm = { askDelete = false; act("Bid deleted", { api.delete("/bid-center/$id") }) { onBack() } }, onCancel = { askDelete = false },
        )
        acceptFor?.let { s -> SwalConfirm(
            title = "Accept ${s.optString("supplier")}'s quote?", text = "The other pending submissions are rejected, and a purchase order and payment are created.", confirmText = "Yes, accept", confirmColor = KitGood,
            onConfirm = { acceptFor = null; act(null, { api.send("POST", "/bid-center/submissions/${s.optInt("id")}/accept", JSONObject()) }) }, onCancel = { acceptFor = null },
        ) }
        rejectFor?.let { s -> RejectSheet(s.optString("supplier"), onClose = { rejectFor = null }) { reason ->
            rejectFor = null
            act(null, { api.send("POST", "/bid-center/submissions/${s.optInt("id")}/reject", JSONObject().put("rejection_reason", reason.ifBlank { JSONObject.NULL })) })
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RejectSheet(supplier: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reject $supplier's quote", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Input("Reason (shown to the supplier)", reason, { reason = it }, singleLine = false)
            SendButton("Reject submission", true) { onSave(reason.trim()) }
        }
    }
}

// ───────────────────────────── create / edit ─────────────────────────────

private class BidRow(val kind: String, val id: Int, val name: String, qty: String = "", unit: String = "") {
    var qty by mutableStateOf(qty)
    var unit by mutableStateOf(unit)
}

private fun pickDateTime(context: android.content.Context, current: String, set: (String) -> Unit) {
    val date = runCatching { LocalDate.parse(current.take(10)) }.getOrDefault(LocalDate.now())
    DatePickerDialog(context, { _, y, m, day ->
        val picked = LocalDate.of(y, m + 1, day).toString()
        val h = current.drop(11).take(2).toIntOrNull() ?: 10; val mi = current.drop(14).take(2).toIntOrNull() ?: 0
        TimePickerDialog(context, { _, hh, mm -> set("$picked %02d:%02d".format(hh, mm)) }, h, mi, true).show()
    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
}

/** New bid / edit bid — the website's bid form: title, dates, who to invite, what to ask for (services and materials), terms. Save as a draft or save and send. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BidFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit, taskId: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = id != null

    var options by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var special by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var termsId by remember { mutableIntStateOf(0) }
    var suppliers by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val rows = remember { mutableStateListOf<BidRow>() }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }       // terms · suppliers · items
    var askSend by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<List<Any>?>(null) }
    fun snapshot(): List<Any> = listOf(title, description, special, start, end, termsId, suppliers, rows.map { listOf(it.kind, it.id, it.qty, it.unit) })

    LaunchedEffect(attempt) {
        loadError = null
        try {
            val o = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/bid-center/options") }
            if (id != null) {
                val x = withContext(Dispatchers.IO) { api.detail("/bid-center/$id") }
                title = x.optString("title"); description = x.str("description").orEmpty(); special = x.str("special_requirements").orEmpty()
                start = x.str("start_date").orEmpty(); end = x.str("end_date").orEmpty(); termsId = x.optInt("terms_id")
                suppliers = x.optJSONArray("supplier_ids")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
                rows.clear(); x.objs("items").forEach { rows.add(BidRow(it.optString("kind"), it.optInt("id"), it.optString("name"), it.str("quantity")?.removeSuffix(".00").orEmpty(), it.str("unit").orEmpty())) }
            }
            else if (taskId > 0) {
                // a bid for a task starts with the materials / services the task asks for
                val t = withContext(Dispatchers.IO) { api.detail("/bid-center/tasks/$taskId/items") }
                if (title.isBlank()) title = t.optString("task")
                rows.clear(); t.objs("items").forEach { rows.add(BidRow(it.optString("kind"), it.optInt("id"), it.optString("name"), it.str("quantity")?.let { q -> q.toDoubleOrNull()?.let { n -> if (n % 1.0 == 0.0) n.toLong().toString() else n.toString() } }.orEmpty(), it.str("unit").orEmpty())) }
            }
            options = o
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
        initial = snapshot()
    }

    fun save(send: Boolean) {
        val problems = mutableMapOf<String, String>()
        if (title.isBlank()) problems["title"] = "Enter a bid title"
        if (start.isBlank()) problems["start_date"] = "Pick the start date"
        if (end.isBlank()) problems["end_date"] = "Pick the end date"
        if (start.isNotBlank() && end.isNotBlank() && end <= start) problems["end_date"] = "End date must be after the start date."
        if (suppliers.isEmpty()) problems["supplier_ids"] = "Select at least one supplier."
        if (rows.isEmpty()) problems["new_items"] = "Add at least one item or service."
        else if (rows.any { (it.qty.toDoubleOrNull() ?: 0.0) <= 0.0 }) problems["new_items"] = "Enter a quantity for every item."
        if (problems.isNotEmpty()) { errors = problems; banner = problems.values.first(); return }
        val nul = JSONObject.NULL
        val body = JSONObject().put("title", title.trim()).put("description", description.trim().ifBlank { nul }).put("special_requirements", special.trim().ifBlank { nul })
            .put("start_date", start).put("end_date", end).put("terms_condition_id", if (termsId > 0) termsId else nul).put("supplier_ids", JSONArray(suppliers.sorted()))
            .put("new_items", JSONArray(rows.map { JSONObject().put("kind", it.kind).put("id", it.id).put("quantity", it.qty.toDouble()).put("unit", it.unit.trim().ifBlank { nul }) }))
            .put("action", if (send) "save_send" else "save").apply { if (taskId > 0 && !editing) put("task_id", taskId) }
        errors = emptyMap(); banner = null; saving = true
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { if (id != null) api.send("PUT", "/bid-center/$id", body) else api.send("POST", "/projects/$uuid/bid-center", body) }
                Toast.makeText(context, r.optString("message", "Bid saved"), Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: ApiException) {
                errors = e.errors; banner = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message
            } catch (e: Exception) { banner = e.message ?: "Could not save" }
            saving = false
        }
    }

    val back = leaveGuard(initial != null && snapshot() != initial, onBack)
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, if (editing) "Edit bid" else "New bid", back, compact = true)
        val o = options
        when {
            loadError != null -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(loadError ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
            o == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            else -> {
                val supplierList = o.objs("suppliers")
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 8.dp)) {
                        banner?.let { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).padding(12.dp)) }

                        Card("The bid", "What is it for?") {
                            Input("Title", title, { title = it }, errors["title"])
                            Input("Description", description, { description = it }, singleLine = false)
                            Input("Special requirements", special, { special = it }, singleLine = false)
                        }
                        Card("When", "Suppliers can answer between these times") {
                            PickerField("Starts", bidDay(start, true), errors["start_date"]) { pickDateTime(context, start) { start = it } }
                            PickerField("Ends", bidDay(end, true), errors["end_date"]) { pickDateTime(context, end) { end = it } }
                        }
                        Card("Suppliers", "${suppliers.size} invited") {
                            val chosen = supplierList.filter { it.optInt("id") in suppliers }.map { it.optString("name") }
                            PickerField("Invite suppliers", chosen.takeIf { it.isNotEmpty() }?.let { if (it.size <= 2) it.joinToString(", ") else "${it.take(2).joinToString(", ")} +${it.size - 2}" }, errors["supplier_ids"]) { sheet = "suppliers" }
                            if (supplierList.isEmpty()) Text("This project has no approved suppliers yet.", color = KitMuted, fontSize = 12.sp)
                        }
                        Card("Items", if (rows.isEmpty()) "Services and materials to quote" else "${rows.size} added") {
                            errors["new_items"]?.let { Text(it, color = KitDanger, fontSize = 12.sp) }
                            rows.forEachIndexed { i, r ->
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Chip(if (r.kind == "service") "Service" else "Material", if (r.kind == "service") Amber else Blue)
                                        Text(r.name, color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                                        Text("✕", color = KitDanger, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable { rows.removeAt(i) }.padding(8.dp))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(Modifier.weight(1f)) { Input("Quantity", r.qty, { r.qty = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal) }
                                        Box(Modifier.weight(1f)) { Input("Unit", r.unit, { r.unit = it }) }
                                    }
                                }
                            }
                            Text("+  Add an item or service", color = b.base, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.5.dp, b.base.copy(alpha = .5f), RoundedCornerShape(14.dp)).clickable { sheet = "items" }.padding(vertical = 13.dp))
                        }
                        Card("Terms & conditions") { PickerField("Terms", o.objs("terms").firstOrNull { it.optInt("id") == termsId }?.optString("title")) { sheet = "terms" } }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (saving) "Saving…" else if (editing) "Save draft" else "Create draft", color = b.base, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).border(1.5.dp, b.base, RoundedCornerShape(16.dp)).clickable(enabled = !saving) { save(false) }.padding(vertical = 14.dp),
                    )
                    Text(
                        "Save & send", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { askSend = true }.padding(vertical = 14.dp),
                    )
                }
                if (askSend) SwalConfirm(
                    title = "Save and send?", text = "Every supplier you picked is told right away, and the bid is locked — it can no longer be edited.", confirmText = "Yes, send it", confirmColor = b.base,
                    onConfirm = { askSend = false; save(true) }, onCancel = { askSend = false },
                )

                if (sheet == "terms") ChoiceSheet("Terms & conditions", o.objs("terms").map { Choice(it.optInt("id").toString(), it.optString("title")) }, termsId.takeIf { it > 0 }?.toString(), b, allowNone = true, onClose = { sheet = null }) { sheet = null; termsId = it.toIntOrNull() ?: 0 }
                if (sheet == "suppliers") ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Invite suppliers", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                            Text("Done", color = b.base, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { sheet = null }.padding(8.dp))
                        }
                        Column(Modifier.height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (supplierList.isEmpty()) Text("This project has no approved suppliers yet.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                            supplierList.forEach { s ->
                                val sid = s.optInt("id"); val on = sid in suppliers
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else Color(0xFFF7F5F1)).clickable { suppliers = if (on) suppliers - sid else suppliers + sid }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Initials(s.optString("name"), size = 34.dp)
                                    Text(s.optString("name"), color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (on) b.base else Color.Transparent).border(2.dp, if (on) b.base else Color(0xFF9E9E9E), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                        if (on) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }
                if (sheet == "items") ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    var q by remember { mutableStateOf("") }
                    Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add an item or service", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                        Input("Search", q, { q = it })
                        Column(Modifier.height(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val all = o.objs("materials").map { Triple("material", it.optInt("id"), it) } + o.objs("services").map { Triple("service", it.optInt("id"), it) }
                            val shown = all.filter { (k, i, j) -> rows.none { r -> r.kind == k && r.id == i } && j.optString("name").contains(q.trim(), true) }
                            if (shown.isEmpty()) Text("Nothing to add.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                            shown.forEach { (k, i, j) ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { rows.add(BidRow(k, i, j.optString("name"), "", j.str("unit").orEmpty())); sheet = null }.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Chip(if (k == "service") "Service" else "Material", if (k == "service") Amber else Blue)
                                    Text(j.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                                    j.str("unit")?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
