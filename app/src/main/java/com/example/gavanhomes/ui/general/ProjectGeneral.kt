package com.example.gavanhomes.ui.general

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.example.gavanhomes.ui.common.FabAction
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.ModulePages
import com.example.gavanhomes.ui.common.ProvideFab
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.inventory.FilteredList
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.NewestOldest
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.Waiting
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.procurement.ActionButton
import com.example.gavanhomes.ui.procurement.MiniRow
import com.example.gavanhomes.ui.professionals.ProDonutCard
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.Label
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private class Page(val key: String, val label: String)

private fun JSONObject.strings(k: String): List<String> = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

/**
 * The project's General module, like the website's General tab: the people and businesses the project works with — Contractors,
 * Workers, Engineers and Parties (in the books). The + button adds any of them. Data: /projects/{uuid}/general-center/…
 * [onOpen] opens contractor · worker; [onNew] adds a worker (the Attendance worker form).
 */
@Composable
fun ProjectGeneral(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (String, Int) -> Unit, onNewWorker: () -> Unit) {
    var view by remember { mutableStateOf("dashboard") }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var local by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }      // contractor · engineer · party, with the row being edited ({} = new)
    LaunchedEffect(version, attempt, local) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/general-center/overview") } } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    val o = overview
    if (o == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") } }
        }
        return
    }
    val can = o.optJSONObject("can") ?: JSONObject()
    ProvideFab(listOfNotNull(
        FabAction("contractor", "New contractor", KitInk, "⚒").takeIf { can.optBoolean("create_contractors") },
        FabAction("worker", "Add worker", InvBlue, "☺").takeIf { can.optBoolean("create_workers") },
        FabAction("engineer", "New engineer", InvPurple, "✎").takeIf { can.optBoolean("create_engineers") },
        FabAction("party", "New party", KitGood, "◍").takeIf { can.optBoolean("create_parties") },
    )) { k -> if (k == "worker") onNewWorker() else sheet = k to JSONObject() }
    val pages = listOfNotNull(
        Page("dashboard", "Dashboard"), Page("contractors", "Contractors").takeIf { can.optBoolean("contractors") }, Page("workers", "Workers").takeIf { can.optBoolean("workers") },
        Page("engineers", "Engineers").takeIf { can.optBoolean("engineers") }, Page("parties", "Parties").takeIf { can.optBoolean("parties") },
    )
    val index = pages.indexOfFirst { it.key == view }.coerceAtLeast(0)
    ModulePages("General", pages.map { it.label }, index) { view = pages[it].key }
    val v = version + local
    when (pages[index].key) {
        "dashboard" -> Dashboard(o) { view = it }
        "contractors" -> FilteredList(
            api, b, "/projects/$uuid/general-center/contractors", v, "Search name, company, phone or email…",
            groups = listOf(FilterGroup("type", "Type", o.strings("types").map { FilterOption(it, it) })), sorts = NewestOldest + ("name" to "Name A–Z"),
            empty = "No contractors on this project.", noun = "contractor",
        ) { c -> Person(c.optString("name"), listOfNotNull(c.str("company"), c.str("type")).joinToString(" · "), c.str("phone"), listOfNotNull(c.optInt("workers").takeIf { !c.isNull("workers") }?.let { "$it worker${if (it == 1) "" else "s"}" to InvBlue }, ("Shared" to KitMuted).takeIf { !c.optBoolean("home") })) { onOpen("contractor", c.optInt("id")) } }
        "workers" -> FilteredList(
            api, b, "/projects/$uuid/general-center/workers", v, "Search name, phone or trade…",
            groups = listOf(FilterGroup("type", "Labour", listOf(FilterOption("my_labour", "My labour"), FilterOption("vendor", "Vendor labour")))),
            sorts = NewestOldest + listOf("name" to "Name A–Z", "wage" to "Highest wage first"), empty = "No workers on this project.", noun = "worker",
        ) { w -> Person(w.optString("name"), listOfNotNull(w.str("trade"), w.str("contractor")).joinToString(" · "), w.str("phone"), listOf("${rupees(w.optDouble("daily_wage", 0.0))}/day" to KitGood, (if (w.str("type") == "vendor") "Vendor" else "My labour") to KitMuted)) { onOpen("worker", w.optInt("id")) } }
        "engineers" -> FilteredList(
            api, b, "/projects/$uuid/general-center/engineers", v, "Search name, email or phone…",
            groups = listOfNotNull(o.strings("specializations").takeIf { it.isNotEmpty() }?.let { s -> FilterGroup("specialization", "Specialisation", s.map { FilterOption(it, it) }) }),
            sorts = NewestOldest + ("name" to "Name A–Z"), empty = "No engineers on this project.", noun = "engineer",
        ) { e -> Person(e.optString("name"), listOfNotNull(e.str("specialization"), e.str("email")).joinToString(" · "), e.str("phone"), emptyList()) { if (can.optBoolean("edit_engineers") || can.optBoolean("delete_engineers")) sheet = "engineer" to e } }
        else -> FilteredList(
            api, b, "/projects/$uuid/general-center/parties", v, "Search name, GSTIN, phone or email…",
            groups = listOf(FilterGroup("gst_type", "GST", o.strings("gst_types").map { FilterOption(it, it) })), sorts = NewestOldest + ("name" to "Name A–Z"),
            empty = "No parties on this project.", noun = "party",
        ) { p -> Person(p.optString("name"), listOfNotNull(p.str("gstin"), p.str("state")).joinToString(" · "), p.str("phone"), listOfNotNull(p.str("gst_type")?.let { it.take(24) to KitMuted })) { if (can.optBoolean("edit_parties") || can.optBoolean("delete_parties")) sheet = "party" to p } }
    }
    sheet?.let { (kind, row) -> EditSheet(api, b, uuid, kind, row, o, can, onClose = { sheet = null }) { sheet = null; local++ } }
}

@Composable
private fun Dashboard(o: JSONObject, onPage: (String) -> Unit) {
    val s = o.optJSONObject("stats") ?: JSONObject()
    Box(Modifier.padding(top = 4.dp)) {
        ProKpiTiles(listOf(
            ProKpi(s.optInt("contractors").toString(), "Contractors", KitInk), ProKpi(s.optInt("workers").toString(), "Workers", InvBlue), ProKpi(s.optInt("engineers").toString(), "Engineers", InvPurple),
            ProKpi(s.optInt("my_labour").toString(), "My labour", KitGood), ProKpi(s.optInt("vendor_labour").toString(), "Vendor labour", InvAmber), ProKpi(s.optInt("parties").toString(), "Parties", KitGood),
        ), columns = 3)
    }
    val palette = listOf(InvBlue, KitGood, InvAmber, InvPurple, KitDanger, KitMuted)
    val types = o.optJSONObject("contractor_types")
    val slices = types?.keys()?.asSequence()?.toList().orEmpty().mapIndexed { i, k -> Triple(k, types!!.optInt(k), palette[i % palette.size]) }
    ProDonutCard("Contractors by type", null, slices, empty = "No contractors yet.") { onPage("contractors") }
    if (s.optInt("workers") > 0) ProDonutCard("Workers", "Own labour and vendor labour", listOf(Triple("My labour", s.optInt("my_labour"), KitGood), Triple("Vendor labour", s.optInt("vendor_labour"), InvAmber))) { onPage("workers") }
}

/** One person / business: initials, name, a sub-line, chips, and Call / WhatsApp when there is a phone number. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Person(name: String, sub: String, phone: String?, chips: List<Pair<String, Color>>, onClick: () -> Unit) {
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.example.gavanhomes.ui.common.Initials(name, size = 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) Text(sub, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { chips.forEach { (l, c) -> Chip(l, c) } }
        phone?.let { com.example.gavanhomes.ui.tasks.PhoneActions(it) }
    }
}

/** Add or edit a contractor / engineer / party in a sheet (with Delete for an existing one). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditSheet(api: ApiClient, b: Branding, uuid: String, kind: String, row: JSONObject, o: JSONObject, can: JSONObject, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = row.optInt("id")
    val fields = when (kind) {
        "contractor" -> listOf("name" to "Name", "company_name" to "Company", "phone" to "Phone", "email" to "Email", "address" to "Address", "username" to "Portal username", "password" to "Portal password")
        "engineer" -> listOf("name" to "Name", "specialization" to "Specialisation", "phone" to "Phone", "email" to "Email", "address" to "Address")
        else -> listOf("name" to "Name", "gstin" to "GSTIN", "phone" to "Phone", "email" to "Email", "billing_address" to "Billing address")
    }
    val src = mapOf("company_name" to "company")
    val f = remember { mutableStateMapOf<String, String>().apply { fields.forEach { (k, _) -> put(k, row.str(src[k] ?: k).orEmpty().takeIf { k != "password" }.orEmpty()) }; row.str("type")?.let { put("type", it) }; row.str("gst_type")?.let { put("gst_type", it) }; row.str("state")?.let { put("state", it) } } }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    val noun = mapOf("contractor" to "contractor", "engineer" to "engineer", "party" to "party")[kind]!!
    val plural = mapOf("contractor" to "contractors", "engineer" to "engineers", "party" to "parties")[kind]!!
    fun save() {
        if (f["name"].isNullOrBlank()) { errors = mapOf("name" to "Enter a name."); return }
        val body = JSONObject(); f.forEach { (k, v) -> if (k == "password") { if (v.isNotBlank()) body.put(k, v) } else body.put(k, v.trim().ifBlank { JSONObject.NULL }) }
        if (id > 0) body.put("id", id)
        saving = true; errors = emptyMap()
        scope.launch {
            try { withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/general-center/$plural", body) }; Toast.makeText(context, if (id > 0) "Saved" else "Added", Toast.LENGTH_SHORT).show(); onDone() }
            catch (e: ApiException) { errors = e.errors.ifEmpty { mapOf("name" to (e.message ?: "Could not save")) } }
            catch (e: Exception) { errors = mapOf("name" to (e.message ?: "Could not save")) }
            saving = false
        }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text((if (id > 0) "Edit " else "New ") + noun, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            fields.forEach { (k, label) ->
                Input(if (k == "password" && id > 0) "$label (blank keeps it)" else label, f[k].orEmpty(), { f[k] = if (k == "phone") it.filter { c -> c.isDigit() || c == '+' } else if (k == "gstin") it.uppercase() else it }, errors[k],
                    singleLine = k != "address" && k != "billing_address", keyboard = when (k) { "phone" -> KeyboardType.Phone; "email" -> KeyboardType.Email; else -> KeyboardType.Text }, password = k == "password")
            }
            if (kind == "contractor") PickerField("Type", f["type"]?.ifBlank { null }, errors["type"]) { pick = "type" }
            if (kind == "party") {
                PickerField("GST type", f["gst_type"]?.ifBlank { null }, errors["gst_type"]) { pick = "gst" }
                PickerField("State", f["state"]?.ifBlank { null }, errors["state"]) { pick = "state" }
            }
            ActionButton(if (saving) "Saving…" else if (id > 0) "Save" else "Add $noun", b.base) { if (!saving) save() }
            if (id > 0 && can.optBoolean("delete_$plural")) SmallAction("Delete this $noun", KitDanger) { askDelete = true }
        }
    }
    when (pick) {
        "type" -> ChoiceSheet("Type", o.strings("types").map { Choice(it, it) }, f["type"], b, allowNone = true, onClose = { pick = null }) { f["type"] = it; pick = null }
        "gst" -> ChoiceSheet("GST type", o.strings("gst_types").map { Choice(it, it) }, f["gst_type"], b, allowNone = true, onClose = { pick = null }) { f["gst_type"] = it; pick = null }
        "state" -> ChoiceSheet("State", o.strings("states").map { Choice(it, it) }, f["state"], b, allowNone = true, onClose = { pick = null }) { f["state"] = it; pick = null }
    }
    if (askDelete) SwalConfirm(title = "Delete ${row.optString("name")}?", text = "They are removed from this project's lists.", confirmText = "Yes, delete",
        onConfirm = { askDelete = false; scope.launch {
            try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/general-center/$plural/$id") }; Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show(); onDone() }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
        } }, onCancel = { askDelete = false })
}

/** A contractor: contact (call / WhatsApp), type, company, and the workers filed under them here; Edit, Delete. */
@Composable
fun ContractorDetailScreen(api: ApiClient, b: Branding, uuid: String, id: Int, version: Int, onBack: () -> Unit, onChanged: () -> Unit, onOpenWorker: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var o by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id, version, attempt) {
        error = null
        try { d = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/general-center/contractors/$id") }; o = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/general-center/overview") } }
        catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    val x = d ?: return Waiting(b, "Contractor", error, onBack) { attempt++ }
    val can = x.optJSONObject("can") ?: JSONObject()
    Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
        HeroBar(b, "Contractor", onBack, actions = {
            if (can.optBoolean("edit_contractors")) { HeroButton("✎") { edit = true }; Spacer(Modifier.size(8.dp)) }
            if (can.optBoolean("delete_contractors")) DeleteButton { askDelete = true }
        }) {
            Column {
                Text(x.optString("name"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(listOfNotNull(x.str("company"), x.str("type")).joinToString(" · ").ifEmpty { "Contractor" }, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                if (!x.isNull("workers")) { Spacer(Modifier.height(14.dp)); HeroChips("${x.optInt("workers")} workers here") }
            }
        }
        x.str("phone")?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { com.example.gavanhomes.ui.tasks.PhoneActions(it) } }
        Card("Contact") { Fact("Phone", x.str("phone")); Fact("Email", x.str("email")); Fact("Address", x.str("address")); Fact("Portal username", x.str("username")) }
        val workers = x.objs("worker_list")
        if (workers.isNotEmpty()) Card("Workers", "${workers.size} on this project") { workers.forEach { w -> MiniRow(w.optString("name"), w.str("trade") ?: "", "${rupees(w.optDouble("daily_wage", 0.0))}/day", (if (w.str("type") == "vendor") "Vendor" else "My labour") to KitMuted) { onOpenWorker(w.optInt("id")) } } }
        Spacer(Modifier.height(28.dp))
    }
    if (edit) o?.let { EditSheet(api, b, uuid, "contractor", x, it, can, onClose = { edit = false }) { edit = false; attempt++; onChanged() } }
    if (askDelete) SwalConfirm(title = "Delete ${x.optString("name")}?", text = "A contractor with workers can't be deleted.", confirmText = "Yes, delete",
        onConfirm = { askDelete = false; scope.launch {
            try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/general-center/contractors/$id") }; Toast.makeText(context, "Contractor deleted", Toast.LENGTH_SHORT).show(); onChanged(); onBack() }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
        } }, onCancel = { askDelete = false })
}
