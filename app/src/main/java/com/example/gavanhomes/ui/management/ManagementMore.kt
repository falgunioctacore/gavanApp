package com.example.gavanhomes.ui.management

import android.content.Context
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
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.Downloads
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.listAllMeta
import com.example.gavanhomes.ui.professionals.ProAmber
import com.example.gavanhomes.ui.professionals.ProCard
import com.example.gavanhomes.ui.professionals.objects
import com.example.gavanhomes.ui.projects.form.Input
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
private fun Chip(label: String, on: Boolean, b: Branding, click: () -> Unit) {
    Text(label, color = if (on) Color.White else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) b.base else b.base.copy(alpha = .1f)).clickable { click() }.padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun Primary(label: String, b: Branding, click: () -> Unit) {
    Text(label, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base).clickable { click() }.padding(14.dp))
}

/** A project to work in: a chip row of the projects this person can see. Returns the chosen uuid (the first until one is picked). */
@Composable
private fun ProjectPicker(api: ApiClient, b: Branding, chosen: String?, onChoose: (String) -> Unit) {
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(Unit) {
        list = runCatching { api.listAllMeta("projects").first.filter { !it.optBoolean("is_test") } }.getOrDefault(emptyList())
        if (chosen == null) list.firstOrNull()?.str("uuid")?.let(onChoose)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { p -> Chip(p.optString("name"), p.optString("uuid") == chosen, b) { onChoose(p.optString("uuid")) } }
    }
}

// ───────────────────────────── contractor agreements ─────────────────────────────

/** Draft an agreement from a work order's own terms, send it to the contractor to sign in their app, see who signed. Data: /management/agreements. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgreementsTab(api: ApiClient, b: Branding) {
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(false) }
    var orderId by remember { mutableStateOf<Int?>(null) }
    var body by remember { mutableStateOf("") }
    LaunchedEffect(attempt) { try { d = withContext(Dispatchers.IO) { api.detail("/management/agreements") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    LaunchedEffect(orderId) {
        val id = orderId ?: return@LaunchedEffect
        body = runCatching { withContext(Dispatchers.IO) { api.detail("/management/agreements/draft/$id").optString("body") } }.getOrDefault("")
    }
    fun call(path: String, json: JSONObject, done: () -> Unit = {}) = scope.launch {
        try { val r = withContext(Dispatchers.IO) { api.send("POST", path, json) }; AppToast.show(r.optString("message", "Done")); done(); attempt++ } catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
    }
    val s = d ?: run { Text(error ?: "Loading…", color = if (error != null) KitDanger else KitMuted, modifier = Modifier.padding(20.dp)); return }
    Primary("+ New agreement", b) { orderId = null; body = ""; sheet = true }
    val list = s.objects("agreements")
    if (list.isEmpty()) ProCard("No agreement yet") { Text("Pick a work order; the agreement text is built from its terms.", color = KitMuted, fontSize = 13.sp) }
    list.forEach { a ->
        val st = a.optString("status")
        ProCard(a.optString("title"), listOfNotNull(a.str("contractor"), a.str("work_order")).joinToString(" · ")) {
            Text(when (st) { "signed" -> "Signed" + (a.str("signed_name")?.let { " by $it" } ?: ""); "sent" -> "Sent — waiting for the contractor"; else -> st.replaceFirstChar { it.uppercase() } },
                color = when (st) { "signed" -> KitGood; "sent" -> ProAmber; else -> KitMuted }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (st == "sent") Text("Withdraw", color = KitDanger, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { call("/management/agreements/${a.optInt("id")}/withdraw", JSONObject()) })
        }
    }
    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = KitCard) {
        Column(Modifier.padding(20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("New agreement", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Text("Work order", color = KitMuted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.objects("orders").forEach { o -> Chip("${o.optString("number")} · ${o.optString("contractor")}", orderId == o.optInt("id"), b) { orderId = o.optInt("id") } }
            }
            Input("Agreement text (edit if needed)", body, { body = it }, singleLine = false)
            Primary("Send to contractor", b) { orderId?.let { id -> call("/management/agreements", JSONObject().put("work_order_id", id).put("body", body)) { sheet = false } } ?: AppToast.show("Choose a work order") }
        }
    }
}

// ───────────────────────────── document requests ─────────────────────────────

/** Ask a customer or supplier for a document; they upload it in their app and it is filed in the project's files. Data: /projects/{uuid}/document-requests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsTab(api: ApiClient, b: Branding) {
    val scope = rememberCoroutineScope()
    var uuid by remember { mutableStateOf<String?>(null) }
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(false) }
    val f = remember { mutableStateMapOf<String, String>() }
    ProjectPicker(api, b, uuid) { uuid = it; d = null }
    LaunchedEffect(uuid, attempt) {
        val u = uuid ?: return@LaunchedEffect
        error = null
        try { d = withContext(Dispatchers.IO) { api.detail("/projects/$u/document-requests") } } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    fun call(path: String, json: JSONObject, done: () -> Unit = {}) = scope.launch {
        try { val r = withContext(Dispatchers.IO) { api.send("POST", path, json) }; AppToast.show(r.optString("message", "Done")); done(); attempt++ } catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
    }
    val s = d ?: run { Text(error ?: if (uuid == null) "Choose a project." else "Loading…", color = if (error != null) KitDanger else KitMuted, modifier = Modifier.padding(20.dp)); return }
    Primary("+ Request a document", b) { f.clear(); f["party_type"] = "customer"; sheet = true }
    val list = s.objects("requests")
    if (list.isEmpty()) ProCard("No request yet") { Text("Ask a customer for a PAN copy, or a supplier for a GST certificate.", color = KitMuted, fontSize = 13.sp) }
    list.forEach { r ->
        val st = r.optString("status")
        ProCard(r.optString("title"), "${r.optString("party")} · ${r.optString("party_type")}") {
            r.str("note")?.let { Text(it, color = KitInk, fontSize = 13.sp) }
            Text(st.replaceFirstChar { it.uppercase() } + (r.str("due_date")?.let { " · due ${it.take(10)}" } ?: ""), color = when (st) { "uploaded", "received" -> KitGood; "cancelled" -> KitMuted; else -> ProAmber }, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (st == "requested") Text("Cancel request", color = KitDanger, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { call("/projects/$uuid/document-requests/${r.optInt("id")}/cancel", JSONObject()) })
        }
    }
    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = KitCard) {
        Column(Modifier.padding(20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Request a document", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("From a customer", f["party_type"] == "customer", b) { f["party_type"] = "customer" }
                Chip("From a supplier", f["party_type"] == "supplier", b) { f["party_type"] = "supplier" }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (f["party_type"] == "customer") s.objects("bookings").forEach { x -> Chip(x.optString("label"), f["booking_id"] == x.optInt("id").toString(), b) { f["booking_id"] = x.optInt("id").toString() } }
                else s.objects("suppliers").forEach { x -> Chip(x.optString("name"), f["supplier_id"] == x.optInt("id").toString(), b) { f["supplier_id"] = x.optInt("id").toString() } }
            }
            Input("Which document? (e.g. PAN copy)", f["title"].orEmpty(), { f["title"] = it })
            Input("Note (optional)", f["note"].orEmpty(), { f["note"] = it }, singleLine = false)
            Input("Due date (yyyy-mm-dd, optional)", f["due_date"].orEmpty(), { f["due_date"] = it })
            Primary("Send request", b) {
                val j = JSONObject(); f.forEach { (k, v) -> if (v.isNotBlank()) j.put(k, v) }
                call("/projects/$uuid/document-requests", j) { sheet = false }
            }
        }
    }
}

// ───────────────────────────── partner updates ─────────────────────────────

/** Send a project update to the approved channel partners on WhatsApp. Data: /projects/{uuid}/cp-update. */
@Composable
fun PartnerUpdateTab(api: ApiClient, b: Branding) {
    val scope = rememberCoroutineScope()
    var uuid by remember { mutableStateOf<String?>(null) }
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ProjectPicker(api, b, uuid) { uuid = it; d = null }
    LaunchedEffect(uuid) {
        val u = uuid ?: return@LaunchedEffect
        error = null
        try { d = withContext(Dispatchers.IO) { api.detail("/projects/$u/cp-update") } } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    val s = d ?: run { Text(error ?: if (uuid == null) "Choose a project." else "Loading…", color = if (error != null) KitDanger else KitMuted, modifier = Modifier.padding(20.dp)); return }
    ProCard("Who gets it", "${s.optInt("eligible")} partner(s) will receive this") {
        Text("${s.optInt("approved")} approved · ${s.optInt("pending")} pending · ${s.optInt("opted_out")} opted out · ${s.optInt("available")} unit(s) available", color = KitMuted, fontSize = 13.sp)
        Input("Message to partners", note, { note = it.take(600) }, singleLine = false)
        Text("${note.length}/600", color = KitMuted, fontSize = 11.sp)
        Primary("Send to partners", b) {
            if (note.isBlank()) { AppToast.show("Write the message first"); return@Primary }
            scope.launch {
                try { val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/cp-update", JSONObject().put("note", note)) }; AppToast.show(r.optString("message", "Sent")); note = "" }
                catch (e: Exception) { AppToast.show(e.message ?: "Could not send") }
            }
        }
    }
}

// ───────────────────────────── booking: change unit / name, letters ─────────────────────────────

/** On a booking: move it to another unit, change the name on it, and print the allotment letter or agreement draft. Data: /customer-center/bookings/{id}/… */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingChangesCard(api: ApiClient, b: Branding, id: Int, status: String?, canEdit: Boolean, onChanged: () -> Unit) {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<String?>(null) }   // "transfer" | "rename"
    var busy by remember { mutableStateOf(false) }
    val f = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(id, attempt) { d = runCatching { withContext(Dispatchers.IO) { api.detail("/customer-center/bookings/$id/changes") } }.getOrNull() }
    fun letter(label: String, path: String, file: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val (_, bytes) = withContext(Dispatchers.IO) { api.download(path) }
                val uri = withContext(Dispatchers.IO) { Downloads.save(context, "$file-$id.pdf", "application/pdf", bytes) }
                Downloads.notifyReady(context, "$label ready", "$file-$id.pdf", "application/pdf", uri)
                AppToast.show("$label saved to Downloads/GavanHomes")
            } catch (e: Exception) { AppToast.show(e.message ?: "Could not create the letter") }
            busy = false
        }
    }
    fun send(path: String, j: JSONObject) = scope.launch {
        try { val r = withContext(Dispatchers.IO) { api.send("POST", path, j) }; AppToast.show(r.optString("message", "Saved")); sheet = null; attempt++; onChanged() } catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
    }
    val s = d ?: return
    ProCard("Changes & letters", "Move the unit, change the name, print letters") {
        if (canEdit && status !in listOf("cancelled", "void", "draft")) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Change unit", color = b.base, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { f.clear(); sheet = "transfer" })
            Text("Change name", color = b.base, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { f.clear(); sheet = "rename" })
        }
        if (status == "confirmed") Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (busy) "Preparing…" else "Allotment letter", color = b.base, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { letter("Allotment letter", "/customer-center/bookings/$id/allotment-letter", "allotment") })
            Text("Agreement draft", color = b.base, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { letter("Agreement draft", "/customer-center/bookings/$id/agreement-draft", "agreement-draft") })
        } else Text("Letters are available once the booking is approved.", color = KitMuted, fontSize = 12.sp)
        s.objects("history").forEach { h ->
            Text("${h.optString("type").replaceFirstChar { it.uppercase() }}: ${h.optString("from")} → ${h.optString("to")}" + (if (h.optDouble("fee") > 0) " · fee ₹${"%,.0f".format(h.optDouble("fee"))}" else ""), color = KitInk, fontSize = 13.sp)
            Text("${h.optString("reason")} · ${h.optString("by")} · ${h.optString("at").take(10)}", color = KitMuted, fontSize = 11.sp)
        }
    }
    sheet?.let { which ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = KitCard) {
            Column(Modifier.padding(20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (which == "transfer") {
                    Text("Move to another unit", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                    Text("Now on ${s.str("current") ?: "—"}. Choose a free unit:", color = KitMuted, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.objects("free").forEach { u -> Chip(u.optString("label"), f["inventory_id"] == u.optInt("id").toString(), b) { f["inventory_id"] = u.optInt("id").toString() } }
                    }
                } else {
                    Text("Change the name on the booking", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                    Text("Now: ${s.optString("name")}", color = KitMuted, fontSize = 12.sp)
                    Input("New name", f["new_name"].orEmpty(), { f["new_name"] = it })
                }
                Input("Reason", f["reason"].orEmpty(), { f["reason"] = it }, singleLine = false)
                Input("Fee (₹, optional)", f["fee"].orEmpty(), { f["fee"] = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
                Primary(if (which == "transfer") "Move the booking" else "Change the name", b) {
                    val j = JSONObject(); f.forEach { (k, v) -> if (v.isNotBlank()) j.put(k, v) }
                    send("/customer-center/bookings/$id/$which", j)
                }
            }
        }
    }
}
