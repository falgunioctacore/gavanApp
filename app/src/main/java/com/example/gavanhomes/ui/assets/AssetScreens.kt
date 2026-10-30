package com.example.gavanhomes.ui.assets

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
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

/** A labelled block from a JSON object of label → value. */
@Composable
private fun FactsCard(title: String, o: JSONObject?) {
    val keys = o?.keys()?.asSequence()?.toList().orEmpty()
    if (keys.isNotEmpty()) Card(title) { keys.forEach { k -> Fact(k, o!!.optString(k)) } }
}

// ───────────────────────────── asset details ─────────────────────────────

/** An asset: status, value and book value (depreciation), purchase, insurance / warranty / registration, specs, maintenance and moves; Edit, Delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetDetailScreen(api: ApiClient, b: Branding, uuid: String, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit, onOpenMaintenance: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var askDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id, version, attempt) { error = null; try { d = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/asset-center/assets/$id") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = d ?: return Waiting(b, "Asset", error, onBack) { attempt++ }
    val can = x.optJSONObject("can") ?: JSONObject()
    val (sl, _) = assetStatus(x.str("status"))
    Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
        HeroBar(b, "Asset", onBack, actions = {
            if (can.optBoolean("edit")) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
            if (can.optBoolean("delete")) DeleteButton { askDelete = true }
        }) {
            Column {
                Text(x.optString("name"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(listOfNotNull(x.str("code"), x.str("category"), x.str("sub_category")).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text(rupees(x.optDouble("book_value", 0.0)), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("book value", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                HeroChips(*listOfNotNull(sl, x.str("location")?.let { "📍 $it" }, x.str("assignee")?.let { "👤 $it" }).toTypedArray())
            }
        }
        val exp = x.objs("expiring")
        if (exp.isNotEmpty()) Card("Needs attention") { exp.forEach { e -> MiniRow(e.optString("what"), prettyDay(e.str("date")) ?: "", null, (if (e.optBoolean("over")) "Expired" else "Expiring") to (if (e.optBoolean("over")) KitDanger else InvAmber)) {} } }
        x.str("description")?.let { Card("About") { Text(it, color = KitInk, fontSize = 14.sp) } }
        FactsCard("Purchase", x.optJSONObject("purchase"))
        x.optJSONObject("depreciation")?.let { dep -> Card("Depreciation") {
            Fact("Method", pretty(dep.str("method"))); Fact("Rate", "${dep.optDouble("rate", 0.0)}% a year"); Fact("Book value today", rupees(dep.optDouble("book_value", 0.0)))
            if (x.optDouble("maintenance", 0.0) > 0) Fact("Spent on maintenance", rupees(x.optDouble("maintenance", 0.0)))
        } }
        FactsCard("Insurance", x.optJSONObject("insurance"))
        FactsCard("Warranty & AMC", x.optJSONObject("warranty"))
        FactsCard("Registration", x.optJSONObject("registration"))
        FactsCard("Specifications", x.optJSONObject("specs"))
        FactsCard("Disposal", x.optJSONObject("disposal"))
        x.str("gps")?.let { g -> Card("Location") { com.example.gavanhomes.ui.leads.SmallAction("Open in maps", InvBlue) {
            val uri = if (g.startsWith("http")) g else "geo:$g?q=$g"; runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))) }
        } } }
        val jobs = x.objs("maintenances")
        if (jobs.isNotEmpty()) Card("Maintenance") { jobs.forEach { m -> MiniRow(listOfNotNull(m.str("type"), m.str("code")).joinToString(" · "), m.str("next")?.let { "next " + prettyDay(it) } ?: "", if (m.optDouble("actual", 0.0) > 0) rupees(m.optDouble("actual", 0.0)) else null, jobStatus(m.str("status"))) { onOpenMaintenance(m.optInt("id")) } } }
        val moves = x.objs("transfers")
        if (moves.isNotEmpty()) Card("Moves") { moves.forEach { t -> MiniRow("${t.str("from") ?: "?"} → ${t.str("to") ?: "?"}", prettyDay(t.str("date")) ?: "", null, (if (t.str("received") != null) "Arrived" else "On the way") to (if (t.str("received") != null) KitGood else InvAmber)) {} } }
        Spacer(Modifier.height(28.dp))
    }
    if (askDelete) SwalConfirm(title = "Delete ${x.optString("name")}?", text = "It is removed from the register.", confirmText = "Yes, delete",
        onConfirm = { askDelete = false; scope.launch {
            try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/asset-center/assets/$id") }; Toast.makeText(context, "Asset deleted", Toast.LENGTH_SHORT).show(); onChanged(); onBack() }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
        } }, onCancel = { askDelete = false })
}

/** One maintenance job: asset, type and frequency, last / next date, vendor (call), cost, remarks; Edit, Delete. */
@Composable
fun MaintenanceDetailScreen(api: ApiClient, b: Branding, uuid: String, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var m by remember { mutableStateOf<JSONObject?>(null) }
    var can by remember { mutableStateOf(JSONObject()) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var askDelete by remember { mutableStateOf(false) }
    // a job is read from the list endpoint (there is no separate show on the website), by its code filter
    LaunchedEffect(id, version, attempt) {
        error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/asset-center/maintenances?page=1&per=100") }
            val all = mutableListOf<JSONObject>(); all += r.objs("data")
            var page = 1
            while (all.none { it.optInt("id") == id } && page < r.optInt("last_page", 1)) { page++; all += withContext(Dispatchers.IO) { api.get("/projects/$uuid/asset-center/maintenances?page=$page") }.objs("data") }
            m = all.firstOrNull { it.optInt("id") == id } ?: throw IllegalStateException("That maintenance job was not found.")
            can = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/asset-center/options").optJSONObject("can") } }.getOrNull() ?: JSONObject()
        } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    val x = m ?: return Waiting(b, "Maintenance", error, onBack) { attempt++ }
    val (sl, _) = jobStatus(x.str("status"))
    Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
        HeroBar(b, "Maintenance", onBack, actions = {
            if (can.optBoolean("edit")) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
            if (can.optBoolean("delete")) DeleteButton { askDelete = true }
        }) {
            Column {
                Text(x.str("asset") ?: "Asset", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(listOfNotNull(x.str("code"), x.str("type"), x.str("frequency")).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                HeroChips(*listOfNotNull(sl, x.str("next")?.let { "Next " + prettyDay(it) }).toTypedArray())
            }
        }
        Card("Job") {
            Fact("Last done", prettyDay(x.str("last"))); Fact("Next due", prettyDay(x.str("next"))); Fact("Assigned to", x.str("assignee"))
            Fact("Estimated cost", x.optDouble("estimated", 0.0).takeIf { it > 0 }?.let { rupees(it) }); Fact("Actual cost", x.optDouble("actual", 0.0).takeIf { it > 0 }?.let { rupees(it) })
            Fact("Remarks", x.str("remarks"))
        }
        Card("Vendor") {
            Fact("Name", x.str("vendor_name")); Fact("Company", x.str("vendor_company")); Fact("Email", x.str("vendor_email")); Fact("Phone", x.str("vendor_phone"))
            x.str("vendor_phone")?.let { com.example.gavanhomes.ui.tasks.PhoneActions(it) }
        }
        Spacer(Modifier.height(28.dp))
    }
    if (askDelete) SwalConfirm(title = "Delete this maintenance job?", text = "The asset's maintenance total is worked out again.", confirmText = "Yes, delete",
        onConfirm = { askDelete = false; scope.launch {
            try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/asset-center/maintenances/$id") }; Toast.makeText(context, "Maintenance deleted", Toast.LENGTH_SHORT).show(); onChanged(); onBack() }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
        } }, onCancel = { askDelete = false })
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

/** Load …/asset-center/options (and, when editing, the record via [load]) before showing [content]. */
@Composable
private fun Prepared(api: ApiClient, b: Branding, uuid: String, title: String, onBack: () -> Unit, load: (suspend (JSONObject) -> Unit)? = null, content: @Composable (JSONObject) -> Unit) {
    var o by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try { val x = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/asset-center/options") }; load?.invoke(x); o = x } catch (e: Exception) { error = e.message ?: "Could not load the form" }
    }
    val x = o ?: return Waiting(b, title, error, onBack) { attempt++ }
    content(x)
}

private fun save(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, api: ApiClient, method: String, path: String, body: JSONObject, ok: String,
                 setSaving: (Boolean) -> Unit, setErrors: (Map<String, String>, String?) -> Unit, done: () -> Unit) {
    setSaving(true)
    scope.launch {
        try { withContext(Dispatchers.IO) { api.send(method, path, body) }; Toast.makeText(context, ok, Toast.LENGTH_SHORT).show(); done() }
        catch (e: ApiException) { setErrors(e.errors, e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message) }
        catch (e: Exception) { setErrors(emptyMap(), e.message ?: "Could not save") }
        setSaving(false)
    }
}

private val Money = setOf("purchase_price", "current_value", "insurance_premium", "depreciation_rate")

/**
 * New asset / edit asset — the website's asset form: the basics (name, category, status, location, who has it), purchase (vendor,
 * invoice, date, price, current value), insurance, warranty, registration / PUC and depreciation. Files and photos are added on the website.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = remember { mutableStateMapOf("status" to "active") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val title = if (id != null) "Edit asset" else "New asset"
    val back = leaveGuard(!f["name"].isNullOrBlank(), onBack)
    Prepared(api, b, uuid, title, onBack, load = if (id == null) null else ({ _ ->
        val raw = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/asset-center/assets/$id") }.optJSONObject("raw") ?: JSONObject()
        raw.keys().forEach { k -> if (!raw.isNull(k)) f[k] = raw.get(k).let { v -> if (v is Number) plain(v.toDouble()) else v.toString().take(if (k.endsWith("_date")) 10 else 2000) } }
    })) { o ->
        fun submit() {
            if (f["name"].isNullOrBlank()) { errors = mapOf("name" to "Enter the asset's name."); banner = errors["name"]; return }
            val body = JSONObject()
            f.forEach { (k, v) -> val t = v.trim(); body.put(k, when { t.isEmpty() -> JSONObject.NULL; k in Money || k in setOf("category_id", "sub_category", "assigned_to") -> t.toDoubleOrNull()?.let { if (k in Money) it else it.toInt() } ?: JSONObject.NULL; else -> t }) }
            errors = emptyMap(); banner = null
            save(context, scope, api, if (id != null) "PUT" else "POST", if (id != null) "/projects/$uuid/asset-center/assets/$id" else "/projects/$uuid/asset-center/assets", body,
                if (id != null) "Asset saved" else "Asset added", { saving = it }, { e, m -> errors = e; banner = m }, onSaved)
        }
        val cats = o.objs("categories")
        val text = { k: String, label: String -> @Composable { Input(label, f[k].orEmpty(), { f[k] = it }, errors[k]) } }
        val num = { k: String, label: String -> @Composable { Input(label, f[k].orEmpty(), { f[k] = it.filter { c -> c.isDigit() || c == '.' } }, errors[k], keyboard = KeyboardType.Decimal) } }
        val date = { k: String, label: String -> @Composable { DatePick(label, f[k].orEmpty(), errors[k]) { f[k] = it } } }
        FormPage(b, title, back, banner, if (id != null) "Save asset" else "Add asset", saving, ::submit) {
            Card("The asset") {
                text("name", "Name")()
                PickerField("Category", cats.firstOrNull { it.optInt("id").toString() == f["category_id"] }?.optString("name"), errors["category_id"]) { sheet = "category" }
                if (cats.any { it.optInt("parent_id") > 0 && it.optInt("parent_id").toString() == f["category_id"] })
                    PickerField("Sub-category", cats.firstOrNull { it.optInt("id").toString() == f["sub_category"] }?.optString("name"), errors["sub_category"]) { sheet = "sub" }
                Label("Status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    o.optJSONArray("statuses")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().forEach { s -> assetStatus(s).let { (l, c) -> Pick(l, f["status"] == s, c) { f["status"] = s } } }
                }
                text("location", "Location")()
                PickerField("With (person)", o.objs("users").firstOrNull { it.optInt("id").toString() == f["assigned_to"] }?.optString("name"), errors["assigned_to"]) { sheet = "user" }
                Input("Description", f["description"].orEmpty(), { f["description"] = it }, errors["description"], singleLine = false)
            }
            Card("Purchase") { text("vendor", "Vendor")(); text("invoice_no", "Invoice no.")(); date("purchase_date", "Purchased on")(); num("purchase_price", "Price (₹)")(); num("current_value", "Current value (₹)")() }
            Card("Insurance") { text("insurance_provider", "Provider")(); text("insurance_policy_no", "Policy no.")(); num("insurance_premium", "Premium (₹)")(); date("insurance_expiry_date", "Expires")() }
            Card("Warranty") { text("warranty_period", "Period")(); text("warranty_vendor", "Vendor")(); date("warranty_expiry_date", "Expires")() }
            Card("Registration") { text("registration_number", "Registration no.")(); date("registration_expiry_date", "Registration expires")(); date("puc_expiry_date", "PUC expires")() }
            Card("Depreciation", "Left blank, the category's default is used") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pick("Straight line", f["depreciation_method"] == "straight_line", InvBlue) { f["depreciation_method"] = if (f["depreciation_method"] == "straight_line") "" else "straight_line" }
                    Pick("Written-down value", f["depreciation_method"] == "written_down_value", InvBlue) { f["depreciation_method"] = if (f["depreciation_method"] == "written_down_value") "" else "written_down_value" }
                }
                num("depreciation_rate", "Rate (% a year)")()
            }
        }
        when (sheet) {
            "category" -> ChoiceSheet("Category", cats.filter { it.optInt("parent_id") == 0 }.map { Choice(it.optInt("id").toString(), it.optString("name")) }, f["category_id"], b, allowNone = true, onClose = { sheet = null }) { f["category_id"] = it; f["sub_category"] = ""; sheet = null }
            "sub" -> ChoiceSheet("Sub-category", cats.filter { it.optInt("parent_id").toString() == f["category_id"] }.map { Choice(it.optInt("id").toString(), it.optString("name")) }, f["sub_category"], b, allowNone = true, onClose = { sheet = null }) { f["sub_category"] = it; sheet = null }
            "user" -> ChoiceSheet("With", o.objs("users").map { Choice(it.optInt("id").toString(), it.optString("name")) }, f["assigned_to"], b, allowNone = true, onClose = { sheet = null }) { f["assigned_to"] = it; sheet = null }
        }
    }
}

/** Schedule maintenance / edit a job — asset, type, frequency, last / next date, vendor, costs, who handles it, status, remarks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaintenanceFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = remember { mutableStateMapOf("maintenance_type" to "Routine", "frequency" to "Monthly", "status" to "Scheduled") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val title = if (id != null) "Edit maintenance" else "Schedule maintenance"
    val back = leaveGuard(!f["asset_id"].isNullOrBlank(), onBack)
    Prepared(api, b, uuid, title, onBack, load = if (id == null) null else ({ _ ->
        var page = 1; var found: JSONObject? = null
        while (found == null) {
            val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/asset-center/maintenances?page=$page") }
            found = r.objs("data").firstOrNull { it.optInt("id") == id }
            if (page >= r.optInt("last_page", 1)) break; page++
        }
        found?.let { m ->
            f["asset_id"] = m.optInt("asset_id").toString(); m.str("type")?.let { f["maintenance_type"] = it }; m.str("frequency")?.let { f["frequency"] = it }
            m.str("status")?.let { s -> f["status"] = listOf("Scheduled", "In Progress", "Completed", "Overdue", "Escalated").firstOrNull { it.equals(s, true) } ?: "Scheduled" }
            f["last_done_date"] = m.str("last").orEmpty(); f["next_due_date"] = m.str("next").orEmpty(); f["vendor_name"] = m.str("vendor_name").orEmpty(); f["vendor_company"] = m.str("vendor_company").orEmpty()
            f["vendor_phone"] = m.str("vendor_phone").orEmpty(); f["vendor_email"] = m.str("vendor_email").orEmpty(); f["remarks"] = m.str("remarks").orEmpty()
            f["estimated_cost"] = m.optDouble("estimated", 0.0).takeIf { it > 0 }?.let { plain(it) }.orEmpty(); f["actual_cost"] = m.optDouble("actual", 0.0).takeIf { it > 0 }?.let { plain(it) }.orEmpty()
            if (m.optInt("assigned_to") > 0) f["assigned_to"] = m.optInt("assigned_to").toString()
        }
    })) { o ->
        val mo = o.optJSONObject("maintenance") ?: JSONObject()
        fun list(k: String) = mo.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        fun submit() {
            if (f["asset_id"].isNullOrBlank()) { errors = mapOf("asset_id" to "Choose the asset."); banner = errors["asset_id"]; return }
            val body = JSONObject()
            f.forEach { (k, v) -> val t = v.trim(); body.put(k, when { t.isEmpty() -> JSONObject.NULL; k in setOf("estimated_cost", "actual_cost") -> t.toDoubleOrNull() ?: JSONObject.NULL; k in setOf("asset_id", "assigned_to") -> t.toInt(); else -> t }) }
            errors = emptyMap(); banner = null
            save(context, scope, api, if (id != null) "PUT" else "POST", if (id != null) "/projects/$uuid/asset-center/maintenances/$id" else "/projects/$uuid/asset-center/maintenances", body,
                if (id != null) "Maintenance saved" else "Maintenance scheduled", { saving = it }, { e, m -> errors = e; banner = m }, onSaved)
        }
        FormPage(b, title, back, banner, if (id != null) "Save" else "Schedule", saving, ::submit) {
            Card("Job") {
                PickerField("Asset", o.objs("assets").firstOrNull { it.optInt("id").toString() == f["asset_id"] }?.optString("name"), errors["asset_id"]) { sheet = "asset" }
                Label("Type"); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { list("types").forEach { t -> Pick(t, f["maintenance_type"] == t, InvBlue) { f["maintenance_type"] = t } } }
                Label("How often"); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { list("frequencies").forEach { t -> Pick(t, f["frequency"] == t, InvBlue) { f["frequency"] = t } } }
                Label("Status"); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { list("statuses").forEach { t -> Pick(t, f["status"] == t, jobStatus(t).second) { f["status"] = t } } }
                DatePick("Last done", f["last_done_date"].orEmpty(), errors["last_done_date"]) { f["last_done_date"] = it }
                DatePick("Next due", f["next_due_date"].orEmpty(), errors["next_due_date"]) { f["next_due_date"] = it }
                PickerField("Handled by", o.objs("users").firstOrNull { it.optInt("id").toString() == f["assigned_to"] }?.optString("name"), errors["assigned_to"]) { sheet = "user" }
            }
            Card("Vendor & cost") {
                Input("Vendor", f["vendor_name"].orEmpty(), { f["vendor_name"] = it }, errors["vendor_name"])
                Input("Company", f["vendor_company"].orEmpty(), { f["vendor_company"] = it }, errors["vendor_company"])
                Input("Phone", f["vendor_phone"].orEmpty(), { f["vendor_phone"] = it.filter { c -> c.isDigit() || c == '+' } }, errors["vendor_phone"], keyboard = KeyboardType.Phone)
                Input("Email", f["vendor_email"].orEmpty(), { f["vendor_email"] = it }, errors["vendor_email"], keyboard = KeyboardType.Email)
                Input("Estimated cost (₹)", f["estimated_cost"].orEmpty(), { f["estimated_cost"] = it.filter { c -> c.isDigit() || c == '.' } }, errors["estimated_cost"], keyboard = KeyboardType.Decimal)
                Input("Actual cost (₹)", f["actual_cost"].orEmpty(), { f["actual_cost"] = it.filter { c -> c.isDigit() || c == '.' } }, errors["actual_cost"], keyboard = KeyboardType.Decimal)
                Input("Remarks", f["remarks"].orEmpty(), { f["remarks"] = it }, errors["remarks"], singleLine = false)
            }
        }
        when (sheet) {
            "asset" -> ChoiceSheet("Asset", o.objs("assets").map { Choice(it.optInt("id").toString(), listOfNotNull(it.optString("name"), it.str("asset_code")).joinToString(" · ")) }, f["asset_id"], b, onClose = { sheet = null }) { f["asset_id"] = it; sheet = null }
            "user" -> ChoiceSheet("Handled by", o.objs("users").map { Choice(it.optInt("id").toString(), it.optString("name")) }, f["assigned_to"], b, allowNone = true, onClose = { sheet = null }) { f["assigned_to"] = it; sheet = null }
        }
    }
}

/** Move an asset — from / to location, date, who started and approved it, reason, condition. The asset shows as "being moved" until it is confirmed arrived. */
@Composable
fun TransferFormScreen(api: ApiClient, b: Branding, uuid: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = remember { mutableStateMapOf("transfer_date" to LocalDate.now().toString()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val back = leaveGuard(!f["asset_id"].isNullOrBlank(), onBack)
    Prepared(api, b, uuid, "Move an asset", onBack) { o ->
        fun submit() {
            val e = mutableMapOf<String, String>()
            if (f["asset_id"].isNullOrBlank()) e["asset_id"] = "Choose the asset."
            if (f["to_location"].isNullOrBlank()) e["to_location"] = "Where is it going?"
            if (e.isNotEmpty()) { errors = e; banner = e.values.first(); return }
            val body = JSONObject()
            f.forEach { (k, v) -> val t = v.trim(); body.put(k, when { t.isEmpty() -> JSONObject.NULL; k in setOf("asset_id", "initiated_by", "approved_by") -> t.toInt(); else -> t }) }
            errors = emptyMap(); banner = null
            save(context, scope, api, "POST", "/projects/$uuid/asset-center/transfers", body, "Transfer recorded", { saving = it }, { er, m -> errors = er; banner = m }, onSaved)
        }
        FormPage(b, "Move an asset", back, banner, "Record transfer", saving, ::submit) {
            Card("Move") {
                PickerField("Asset", o.objs("assets").firstOrNull { it.optInt("id").toString() == f["asset_id"] }?.optString("name"), errors["asset_id"]) { sheet = "asset" }
                Input("From", f["from_location"].orEmpty(), { f["from_location"] = it }, errors["from_location"])
                Input("To", f["to_location"].orEmpty(), { f["to_location"] = it }, errors["to_location"])
                DatePick("Date", f["transfer_date"].orEmpty(), errors["transfer_date"]) { f["transfer_date"] = it }
                PickerField("Started by", o.objs("users").firstOrNull { it.optInt("id").toString() == f["initiated_by"] }?.optString("name"), errors["initiated_by"]) { sheet = "by" }
                PickerField("Approved by", o.objs("users").firstOrNull { it.optInt("id").toString() == f["approved_by"] }?.optString("name"), errors["approved_by"]) { sheet = "approver" }
                Input("Condition", f["condition"].orEmpty(), { f["condition"] = it }, errors["condition"])
                Input("Reason", f["reason"].orEmpty(), { f["reason"] = it }, errors["reason"], singleLine = false)
            }
        }
        when (sheet) {
            "asset" -> ChoiceSheet("Asset", o.objs("assets").map { Choice(it.optInt("id").toString(), listOfNotNull(it.optString("name"), it.str("location")).joinToString(" · ")) }, f["asset_id"], b, onClose = { sheet = null }) { v ->
                f["asset_id"] = v; o.objs("assets").firstOrNull { it.optInt("id").toString() == v }?.str("location")?.let { if (f["from_location"].isNullOrBlank()) f["from_location"] = it }; sheet = null
            }
            "by", "approver" -> ChoiceSheet(if (sheet == "by") "Started by" else "Approved by", o.objs("users").map { Choice(it.optInt("id").toString(), it.optString("name")) }, f[if (sheet == "by") "initiated_by" else "approved_by"], b, allowNone = true, onClose = { sheet = null }) {
                f[if (sheet == "by") "initiated_by" else "approved_by"] = it; sheet = null
            }
        }
    }
}
