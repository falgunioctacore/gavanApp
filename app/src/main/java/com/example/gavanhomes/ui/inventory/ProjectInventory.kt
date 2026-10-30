package com.example.gavanhomes.ui.inventory

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.gavanhomes.ui.common.FabAction
import com.example.gavanhomes.ui.common.FilterBar
import com.example.gavanhomes.ui.common.ProvideFab
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.ModulePages
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.SegTabs
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.Label
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

internal val InvBlue = Color(0xFF1E5FAE)
internal val InvAmber = Color(0xFFC47A1A)
internal val InvPurple = Color(0xFF6A4FB3)

private val Indian: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }
/** 12.50 → "12.5", 1200 → "1,200". */
internal fun qty(v: Double): String = Indian.format(v)
internal fun rupees(v: Double): String = "₹" + Indian.format(v)
/** ₹ in lakh / crore for the KPI tiles. */
internal fun shortRupees(v: Double): String = when {
    v >= 1_00_00_000 -> "₹" + String.format(Locale.US, "%.2f", v / 1_00_00_000).trimEnd('0').trimEnd('.') + " Cr"
    v >= 1_00_000 -> "₹" + String.format(Locale.US, "%.2f", v / 1_00_000).trimEnd('0').trimEnd('.') + " L"
    else -> rupees(v)
}

/** A stock status (available | low_stock | out_of_stock) as its label and colour. */
internal fun stockStatus(s: String?): Pair<String, Color> = when (s) {
    "out_of_stock" -> "Out of stock" to KitDanger
    "low_stock" -> "Low stock" to InvAmber
    else -> "In stock" to KitGood
}

internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** How far back the dashboard's received / issued / returned numbers reach (the website defaults to this month). */
private val Periods = listOf("month" to "This month", "quarter" to "Last 3 months", "year" to "This year", "all" to "All time")
private fun periodQuery(key: String): String {
    val now = LocalDate.now()
    val from = when (key) {
        "quarter" -> now.minusMonths(3)
        "year" -> now.withDayOfYear(1)
        "all" -> LocalDate.of(2000, 1, 1)
        else -> now.withDayOfMonth(1)
    }
    return "&date_from=$from&date_to=$now"
}

private class Tab(val key: String, val label: String)

/**
 * The project's Inventory module, like the website's Inventory tab: the stock Dashboard (KPIs, alerts, value by category, recent
 * transactions, quick actions), Materials / Item Master, Site / Store stock, Material Receipts (GRN), Material Issue, Material Return,
 * Stock Transfer, Stock Alerts and the Audit Trail. Data: /projects/{uuid}/inventory-center/…  [onOpen] opens a material / issue / return,
 * [onNew] starts a new material / issue / return.
 */
@Composable
fun ProjectInventory(
    api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (String, Int) -> Unit, onNew: (String) -> Unit, onChanged: () -> Unit,
    settingsOpen: Boolean = false, onCloseSettings: () -> Unit = {},
) {
    var view by remember { mutableStateOf("dashboard") }
    var period by remember { mutableStateOf("month") }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var local by remember { mutableIntStateOf(0) }                     // bumped after a change made inside this module (transfer, store)
    var sheet by remember { mutableStateOf<String?>(null) }            // transfer · store
    var grn by remember { mutableIntStateOf(0) }

    LaunchedEffect(version, attempt, local, period) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/inventory-center/overview?x=1${periodQuery(period)}") } }
        catch (e: Exception) { error = e.message ?: "Could not load the inventory" }
    }
    LaunchedEffect(uuid, version, local) { options = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/inventory-center/options") } }.getOrNull() }

    val o = overview
    if (o == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
        }
        return
    }
    val can = o.optJSONObject("can") ?: JSONObject()
    if (settingsOpen) {
        com.example.gavanhomes.ui.leads.SettingsEntry(true, "Inventory settings", b.base, onCloseSettings)
        InventorySettings(api, b, uuid, version + local, can) { local++ }
        return
    }
    // the screen's + button: new material, issue, return, transfer, store
    ProvideFab(
        if (!can.optBoolean("create")) emptyList() else listOfNotNull(
            FabAction("material", "New material", KitInk, "▣"),
            FabAction("issue", "Material issue", InvPurple, "↗").takeIf { can.optBoolean("inventory") },
            FabAction("return", "Material return", InvBlue, "↙").takeIf { can.optBoolean("inventory") },
            FabAction("transfer", "Stock transfer", InvAmber, "⇄").takeIf { can.optBoolean("inventory") },
            FabAction("store", "Store / site", KitGood, "⌂").takeIf { can.optBoolean("inventory") },
        ),
    ) { kind -> if (kind == "transfer" || kind == "store") sheet = kind else onNew(kind) }
    val tabs = listOfNotNull(
        Tab("dashboard", "Dashboard"), Tab("materials", "Materials"),
        Tab("stores", "Site / Store").takeIf { can.optBoolean("inventory") },
        Tab("grn", "Receipts (GRN)").takeIf { can.optBoolean("grn") },
        Tab("issues", "Issues").takeIf { can.optBoolean("inventory") },
        Tab("returns", "Returns").takeIf { can.optBoolean("inventory") },
        Tab("transfers", "Transfers").takeIf { can.optBoolean("inventory") },
        Tab("alerts", "Stock alerts"),
        Tab("audit", "Audit trail").takeIf { can.optBoolean("inventory") },
    )
    val index = tabs.indexOfFirst { it.key == view }.coerceAtLeast(0)
    ModulePages("Inventory", tabs.map { it.label }, index) { view = tabs[it].key }
    fun changed() { local++; onChanged() }

    when (tabs[index].key) {
        "dashboard" -> Dashboard(b, o, period, { period = it }, can, onOpen, onGrn = { grn = it }, onAlerts = { view = "alerts" })
        "materials" -> MaterialList(api, b, uuid, version + local, options, periodQuery(period), onOpen)
        "stores" -> StoreList(api, b, uuid, version + local, can, onChanged = ::changed)
        "grn" -> GrnList(api, b, uuid, version + local, options) { grn = it }
        "issues" -> DocList(api, b, "/projects/$uuid/inventory-center/issues", version + local, "issue", options) { onOpen("issue", it) }
        "returns" -> DocList(api, b, "/projects/$uuid/inventory-center/returns", version + local, "return", options) { onOpen("return", it) }
        "transfers" -> TransferList(api, b, uuid, version + local, can, options, onChanged = ::changed)
        "alerts" -> AlertList(api, b, uuid, version + local, options, periodQuery(period)) { onOpen("material", it) }
        else -> AuditList(api, b, uuid, version + local, options)
    }

    when (sheet) {
        "transfer" -> TransferSheet(api, b, uuid, options, onClose = { sheet = null }) { sheet = null; changed() }
        "store" -> StoreSheet(api, b, uuid, null, onClose = { sheet = null }) { sheet = null; changed() }
    }
    if (grn > 0) GrnSheet(api, b, grn) { grn = 0 }
}

// ───────────────────────────── dashboard ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dashboard(
    b: Branding, o: JSONObject, period: String, setPeriod: (String) -> Unit, can: JSONObject,
    onOpen: (String, Int) -> Unit, onGrn: (Int) -> Unit, onAlerts: () -> Unit,
) {
    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Periods.forEach { (k, l) -> Pick(l, period == k, b.base) { setPeriod(k) } }
    }

    val s = o.optJSONObject("stats") ?: JSONObject()
    Box(Modifier.padding(top = 8.dp)) {
        ProKpiTiles(
            listOf(
                ProKpi(s.optInt("materials").toString(), "Materials", KitInk), ProKpi(shortRupees(s.optDouble("value", 0.0)), "Stock value", KitGood),
                ProKpi(s.optInt("low_stock").toString(), "Low stock", InvAmber), ProKpi(s.optInt("out_of_stock").toString(), "Out of stock", KitDanger),
                ProKpi(qty(s.optDouble("received", 0.0)), "Received", InvBlue), ProKpi(qty(s.optDouble("issued", 0.0)), "Issued", InvPurple),
                ProKpi(qty(s.optDouble("returned", 0.0)), "Returned", KitGood), ProKpi(s.optInt("categories").toString(), "Categories", KitInk),
                ProKpi(qty(s.optDouble("qty", 0.0)), "Units on hand", KitInk),
            ),
            columns = 3,
        )
    }

    val inStock = (s.optInt("materials") - s.optInt("low_stock") - s.optInt("out_of_stock")).coerceAtLeast(0)
    com.example.gavanhomes.ui.professionals.ProDonutCard(
        "By stock status", "Materials in stock, low and out",
        listOf(Triple("In stock", inStock, KitGood), Triple("Low stock", s.optInt("low_stock"), InvAmber), Triple("Out of stock", s.optInt("out_of_stock"), KitDanger)),
        empty = "No materials yet.", onClick = onAlerts,
    )

    val alerts = o.objs("alerts")
    Card("Stock alerts", if (alerts.isEmpty()) "Everything is above its minimum" else "Lowest first — tap one to open it") {
        if (alerts.isEmpty()) Text("No low or out-of-stock materials.", color = KitMuted, fontSize = 13.sp)
        alerts.forEach { a ->
            val (label, c) = stockStatus(a.str("status"))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onOpen("material", a.optInt("id")) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${qty(a.optDouble("balance", 0.0))} ${a.optString("unit")} on hand · min ${qty(a.optDouble("min_stock", 0.0))}", color = KitMuted, fontSize = 12.sp)
                }
                Chip(label, c)
            }
        }
        if (alerts.isNotEmpty()) SmallAction("See all alerts", InvAmber, onAlerts)
    }

    val cats = o.objs("category_value")
    if (cats.isNotEmpty()) Card("Stock value by category", "What the stock on hand is worth") {
        val top = cats.maxOf { it.optDouble("value", 0.0) }.takeIf { it > 0 } ?: 1.0
        val palette = listOf(InvBlue, KitGood, InvAmber, InvPurple, KitDanger, Color(0xFF1B8A8F), Color(0xFFB8527A), Color(0xFF6B7A90))
        cats.forEachIndexed { i, c ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row {
                    Text("${c.optString("name")} · ${c.optInt("count")}", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(rupees(c.optDouble("value", 0.0)), color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                ProBar((c.optDouble("value", 0.0) / top).toFloat(), palette[i % palette.size])
            }
        }
    }

    val recent = o.objs("recent")
    Card("Recent transactions", "Receipts, issues and returns in this period") {
        if (recent.isEmpty()) Text("Nothing moved in this period.", color = KitMuted, fontSize = 13.sp)
        recent.forEach { t ->
            val type = t.optString("type")
            val c = when (type) { "Receipt" -> KitGood; "Issue" -> InvPurple; else -> InvBlue }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable {
                    when (type) { "Issue" -> onOpen("issue", t.optInt("id")); "Return" -> onOpen("return", t.optInt("id")); else -> if (can.optBoolean("grn")) onGrn(t.optInt("id")) }
                }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Text(when (type) { "Receipt" -> "↓"; "Issue" -> "↗"; else -> "↙" }, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(t.optString("material"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(t.str("ref"), prettyDay(t.str("date")), t.str("by")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Chip(type, c)
                    Text(qty(t.optDouble("qty", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// ───────────────────────────── settings ─────────────────────────────

/** Inventory settings, like the website's Settings pane: material categories and units (shared by every project). */
@Composable
private fun InventorySettings(api: ApiClient, b: Branding, uuid: String, version: Int, can: JSONObject, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var ask by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    LaunchedEffect(version, attempt) {
        error = null
        try { data = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/inventory-center/settings") } } catch (e: Exception) { error = e.message ?: "Could not load the settings" }
    }
    val d = data
    if (d == null) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(error ?: "", color = KitMuted); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") } }
        }
        return
    }
    val kind = if (tab == 0) "category" else "uom"
    val rows = d.objs(if (tab == 0) "categories" else "uoms")
    ModulePages("Inventory settings", listOf("Categories", "Units"), tab) { tab = it }
    ProvideFab(if (can.optBoolean("create")) listOf(FabAction(kind, if (tab == 0) "New category" else "New unit")) else emptyList()) { edit = it to JSONObject() }
    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val noun = if (tab == 0) (if (rows.size == 1) "category" else "categories") else (if (rows.size == 1) "unit" else "units")
        Text("${rows.size} $noun · shared by every project", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
    if (rows.isEmpty()) Text("Nothing here yet.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
    rows.forEach { r ->
        RowCard(null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.optString("name") + (r.str("code")?.let { " ($it)" } ?: ""), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("${r.optInt("materials")} material${if (r.optInt("materials") == 1) "" else "s"}", color = KitMuted, fontSize = 12.sp)
                }
                if (can.optBoolean("edit")) Text("✎", color = KitInk, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).background(Color(0xFFF1EFEA)).clickable { edit = kind to r }.padding(horizontal = 11.dp, vertical = 6.dp))
                if (can.optBoolean("delete")) { Spacer(Modifier.size(8.dp)); Text("🗑", fontSize = 15.sp, modifier = Modifier.clip(CircleShape).background(KitDanger.copy(alpha = .1f)).clickable { ask = kind to r }.padding(horizontal = 9.dp, vertical = 6.dp)) }
            }
        }
    }
    edit?.let { (k, r) -> SettingSheet(api, b, uuid, k, r, onClose = { edit = null }) { edit = null; attempt++; onChanged() } }
    ask?.let { (k, r) ->
        SwalConfirm(
            title = "Delete ${r.optString("name")}?", text = "Only one that no material uses can be deleted.", confirmText = "Yes, delete",
            onConfirm = {
                ask = null
                scope.launch {
                    try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/inventory-center/settings/$k/${r.optInt("id")}") }; Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show(); attempt++; onChanged() }
                    catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
                }
            },
            onCancel = { ask = null },
        )
    }
}

/** Add or rename a category (name) or a unit (name and code). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSheet(api: ApiClient, b: Branding, uuid: String, kind: String, row: JSONObject, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = row.optInt("id")
    var name by remember { mutableStateOf(row.str("name").orEmpty()) }
    var code by remember { mutableStateOf(row.str("code").orEmpty()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    fun save() {
        val e = mutableMapOf<String, String>()
        if (name.isBlank()) e["name"] = "Enter a name."
        if (kind == "uom" && code.isBlank()) e["code"] = "Enter a short code, e.g. Bag or TON."
        if (e.isNotEmpty()) { errors = e; return }
        saving = true; errors = emptyMap()
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/inventory-center/settings/$kind", JSONObject().put("id", if (id > 0) id else JSONObject.NULL).put("name", name.trim()).put("code", code.trim())) }
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show(); onDone()
            } catch (x: ApiException) { errors = x.errors.ifEmpty { mapOf("name" to (x.message ?: "Could not save")) } }
            catch (x: Exception) { errors = mapOf("name" to (x.message ?: "Could not save")) }
            saving = false
        }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text((if (id > 0) "Edit " else "Add ") + (if (kind == "category") "category" else "unit"), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Input(if (kind == "category") "Category name" else "Unit name", name, { name = it }, errors["name"])
            if (kind == "uom") Input("Code", code, { code = it }, errors["code"])
            Text(
                if (saving) "Saving…" else "Save", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { save() }.padding(vertical = 15.dp),
            )
        }
    }
}

// ───────────────────────────── a paged list ─────────────────────────────

/** GET [path]?page=…[query]; [item] draws one row. Shows the count, a spinner, an error with Retry, or [empty]; "Load more" pages on. */
@Composable
internal fun Paged(
    api: ApiClient, b: Branding, path: String, query: String, version: Int, empty: String, noun: String,
    action: (@Composable () -> Unit)? = null, onMeta: (JSONObject) -> Unit = {}, item: @Composable (JSONObject) -> Unit,
) {
    val rows = remember { mutableStateListOf<JSONObject>() }
    var page by remember { mutableIntStateOf(1) }
    var lastPage by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("$path?page=$p$query") }
            if (p == 1) { rows.clear(); onMeta(r) }
            rows.addAll(r.objs("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1); total = r.optInt("total")
        } catch (e: Exception) { error = e.message ?: "Could not load" }
        loading = false
    }
    LaunchedEffect(path, query, version, attempt) { load(1) }

    Row(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$total $noun${if (total == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        action?.invoke()
    }
    rows.forEach { item(it) }
    when {
        loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
        error != null && rows.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
        }
        rows.isEmpty() -> Text(empty, color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
        loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
    }
    if (rows.isNotEmpty() && page < lastPage && !loading) {
        var more by remember(page) { mutableStateOf(false) }
        LaunchedEffect(more) { if (more) load(page + 1) }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { OutlinedButton(onClick = { more = true }) { Text("Load more") } }
    }
}

@Composable
internal fun NewButton(label: String, b: Branding, onClick: () -> Unit) {
    Text("+  $label", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(b.base).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp))
}

/** A white card row, like the other modules' list cards. */
@Composable
internal fun RowCard(onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(5.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** The search text, settled 350 ms after the last keystroke. */
@Composable
private fun debounced(text: String): String {
    var settled by remember { mutableStateOf(text) }
    LaunchedEffect(text) { delay(350); settled = text }
    return settled
}

// ───────────────────────────── filters (like the other modules') ─────────────────────────────

/** The Date filter every list offers (one choice at a time) and the date range each choice means. */
private val DateChoices = listOf("today" to "Today", "week" to "Last 7 days", "month" to "This month", "quarter" to "Last 3 months", "year" to "This year")
internal fun dateGroup() = FilterGroup("date", "Date", DateChoices.map { FilterOption(it.first, it.second) })
private fun dateRange(key: String): String {
    val now = LocalDate.now()
    val from = when (key) { "today" -> now; "week" -> now.minusDays(6); "quarter" -> now.minusMonths(3); "year" -> now.withDayOfYear(1); else -> now.withDayOfMonth(1) }
    return "&date_from=$from&date_to=$now"
}
internal fun peopleGroup(key: String, title: String, options: JSONObject?) =
    options?.objs("users")?.takeIf { it.isNotEmpty() }?.let { u -> FilterGroup(key, title, u.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) }
internal fun materialGroup(options: JSONObject?) =
    options?.objs("materials")?.takeIf { it.isNotEmpty() }?.let { m -> FilterGroup("material_id", "Material", m.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) }
internal val NewestOldest = listOf("newest" to "Newest first", "oldest" to "Oldest first")

/**
 * A list with the app's usual filter bar (search, Sort, and the Filter page of [groups] with a live count), like Leads and Bids.
 * Groups whose key is in [single] keep one choice (Date always does); a Date choice becomes date_from / date_to. [extra] is added to
 * every request; [onMeta] sees each first page (for counts).
 */
@Composable
internal fun FilteredList(
    api: ApiClient, b: Branding, path: String, version: Int, placeholder: String, groups: List<FilterGroup>, sorts: List<Pair<String, String>>,
    empty: String, noun: String, single: Set<String> = emptySet(), extra: String = "", onMeta: (JSONObject) -> Unit = {}, item: @Composable (JSONObject) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var sort by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(-1) }
    val s = debounced(search)
    val one = single + "date"
    val query = buildString {
        if (s.isNotBlank()) append("&search=").append(enc(s.trim()))
        sel.forEach { (k, v) ->
            if (v.isEmpty()) return@forEach
            if (k == "date") append(dateRange(v.first())) else append("&$k=").append(enc(v.joinToString(",")))
        }
        if (sorts.isNotEmpty()) append("&sort=").append(sorts[sort.coerceIn(0, sorts.lastIndex)].first)
        append(extra)
    }
    FilterBar(
        search, { search = it }, placeholder, groups = groups, selected = sel,
        onApply = { m -> sel = m.mapValues { (k, v) -> if (k in one) v.take(1).toSet() else v }.filterValues { it.isNotEmpty() } },
        countWith = { m -> if (m == sel) total else -1 },
        sortOptions = sorts.map { it.second }, sortIndex = sort, onSort = { sort = it },
    )
    Paged(api, b, path, query, version, empty, noun, onMeta = { total = it.optInt("total"); onMeta(it) }, item = item)
}

// ───────────────────────────── materials ─────────────────────────────

private val MaterialSorts = listOf("name" to "Name A–Z", "balance" to "Lowest stock first", "value" to "Highest value first")

@Composable
private fun MaterialList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, period: String, onOpen: (String, Int) -> Unit) {
    var search by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var sort by remember { mutableIntStateOf(0) }
    var counts by remember { mutableStateOf<JSONObject?>(null) }
    var total by remember { mutableIntStateOf(-1) }
    val s = debounced(search)
    val groups = listOfNotNull(
        FilterGroup("status", "Stock status", listOf("available", "low_stock", "out_of_stock").map { k -> stockStatus(k).let { (l, c) -> FilterOption(k, l, counts?.optInt(k), c) } }),
        options?.objs("categories")?.takeIf { it.isNotEmpty() }?.let { c -> FilterGroup("category_id", "Category", c.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
    )
    val query = buildString {
        if (s.isNotBlank()) append("&search=").append(enc(s.trim()))
        sel.forEach { (k, v) -> if (v.isNotEmpty()) append("&$k=").append(enc(v.joinToString(","))) }
        append("&sort=").append(MaterialSorts[sort].first)
        append(period)
    }
    FilterBar(
        search, { search = it }, "Search material name or code…", groups = groups, selected = sel,
        onApply = { m -> sel = m.filterValues { it.isNotEmpty() } }, countWith = { m -> if (m == sel) total else -1 },
        sortOptions = MaterialSorts.map { it.second }, sortIndex = sort, onSort = { sort = it },
    )
    Paged(
        api, b, "/projects/$uuid/inventory-center/materials", query, version, "No materials match.", "material",
        onMeta = { counts = it.optJSONObject("counts"); total = it.optInt("total") },
    ) { r -> MaterialCard(r) { onOpen("material", r.optInt("id")) } }
}

/** One material: name, code and category, what is on hand (big), its status, value and the period's movement. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MaterialCard(r: JSONObject, onClick: () -> Unit) {
    val (label, c) = stockStatus(r.str("status"))
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Text(r.optString("name").take(1).uppercase().ifEmpty { "?" }, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(r.str("code"), r.str("category")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(qty(r.optDouble("balance", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(r.optString("unit"), color = KitMuted, fontSize = 11.sp)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(label, c)
            if (r.optDouble("value", 0.0) > 0) Chip(rupees(r.optDouble("value", 0.0)), KitGood)
            if (r.optDouble("min_stock", 0.0) > 0) Chip("Min ${qty(r.optDouble("min_stock", 0.0))}", KitMuted)
            if (r.optBoolean("global")) Chip("Catalogue", InvPurple)
        }
        if (r.has("opening")) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).padding(horizontal = 10.dp, vertical = 8.dp)) {
            listOf("opening" to "Opening", "received" to "In", "issued" to "Issued", "returned" to "Returned").forEach { (k, l) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(qty(r.optDouble(k, 0.0)), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(l, color = KitMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun AlertList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, period: String, onOpen: (Int) -> Unit) {
    var counts by remember { mutableStateOf<JSONObject?>(null) }
    FilteredList(
        api, b, "/projects/$uuid/inventory-center/alerts", version, "Search material name or code…",
        groups = listOfNotNull(
            FilterGroup("status", "Stock status", listOf("low_stock", "out_of_stock").map { k -> stockStatus(k).let { (l, c) -> FilterOption(k, l, counts?.optInt(k), c) } }),
            options?.objs("categories")?.takeIf { it.isNotEmpty() }?.let { c -> FilterGroup("category_id", "Category", c.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
        ),
        sorts = listOf("balance" to "Lowest stock first", "value" to "Highest value first", "name" to "Name A–Z"),
        empty = "No low or out-of-stock materials.", noun = "alert", extra = period, onMeta = { counts = it.optJSONObject("counts") },
    ) { r -> MaterialCard(r) { onOpen(r.optInt("id")) } }
}

// ───────────────────────────── issues / returns ─────────────────────────────

@Composable
private fun DocList(api: ApiClient, b: Branding, path: String, version: Int, kind: String, options: JSONObject?, onOpen: (Int) -> Unit) {
    val issue = kind == "issue"
    FilteredList(
        api, b, path, version, if (issue) "Search issue no., material or remarks…" else "Search return no., material or remarks…",
        groups = listOfNotNull(
            dateGroup(),
            if (issue) peopleGroup("user_id", "Issued to", options) else peopleGroup("returned_by", "Returned by", options),
            if (issue) null else peopleGroup("return_to", "Returned to", options),
            materialGroup(options),
        ),
        sorts = NewestOldest, empty = if (issue) "No material issued here." else "No material returned here.", noun = kind,
    ) { r -> DocCard(r, kind) { onOpen(r.optInt("id")) } }
}

/** An issue / return: number and date, what went out (first material or "n items"), quantity, people, remarks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocCard(r: JSONObject, kind: String, onClick: () -> Unit) {
    val c = if (kind == "issue") InvPurple else InvBlue
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(if (kind == "issue") "↗" else "↙", color = c, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.optString("number"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(prettyDay(r.str("date")) ?: "—", color = KitMuted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(qty(r.optDouble("qty", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text("${r.optInt("items")} item${if (r.optInt("items") == 1) "" else "s"}", color = KitMuted, fontSize = 11.sp)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.str("first")?.let { Chip(if (r.optInt("items") > 1) "$it +${r.optInt("items") - 1}" else it, c) }
            if (kind == "issue") r.optJSONArray("people")?.let { a -> (0 until a.length()).forEach { Chip("👤 " + a.getString(it), KitMuted) } }
            else { r.str("returned_by")?.let { Chip("By $it", KitMuted) }; r.str("returned_to")?.let { Chip("To $it", KitMuted) } }
        }
        r.str("remarks")?.let { Text(it, color = KitMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

// ───────────────────────────── transfers ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransferList(api: ApiClient, b: Branding, uuid: String, version: Int, can: JSONObject, options: JSONObject?, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf<JSONObject?>(null) }
    FilteredList(
        api, b, "/projects/$uuid/inventory-center/transfers", version, "Search transfer no. or material…",
        groups = listOfNotNull(
            FilterGroup("direction", "Direction", listOf(FilterOption("out", "Sent out", color = InvAmber), FilterOption("in", "Received", color = KitGood))),
            options?.objs("projects")?.takeIf { it.isNotEmpty() }?.let { p -> FilterGroup("project_id", "Other project", p.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
            dateGroup(),
        ),
        sorts = NewestOldest + ("qty" to "Largest quantity first"), single = setOf("direction"),
        empty = "No stock transfers here.", noun = "transfer",
    ) { t ->
        val out = t.optString("direction") == "out"
        val c = if (out) InvAmber else KitGood
        RowCard(null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(if (out) "⇥" else "⇤", color = c, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.optString("material"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(t.str("number"), prettyDay(t.str("date"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text((if (out) "−" else "+") + qty(t.optDouble("qty", 0.0)), color = c, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text(t.optString("unit"), color = KitMuted, fontSize = 11.sp)
                }
            }
            Text("${t.str("from") ?: "?"}  →  ${t.str("to") ?: "?"}", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip((t.str("status") ?: "completed").replaceFirstChar { it.uppercase() }, KitGood)
                t.str("by")?.let { Chip("By $it", KitMuted) }
            }
            t.str("remarks")?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
            if (t.optBoolean("can_delete") && can.optBoolean("delete")) SmallAction("Reverse & delete", KitDanger) { ask = t }
        }
    }
    ask?.let { t ->
        SwalConfirm(
            title = "Reverse this transfer?", text = "${qty(t.optDouble("qty", 0.0))} ${t.optString("unit")} of ${t.optString("material")} comes back from ${t.str("to") ?: "the other project"}.", confirmText = "Yes, reverse it",
            onConfirm = {
                ask = null
                scope.launch {
                    try { withContext(Dispatchers.IO) { api.delete("/inventory-center/transfers/${t.optInt("id")}") }; Toast.makeText(context, "Transfer reversed", Toast.LENGTH_SHORT).show(); onChanged() }
                    catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not reverse", Toast.LENGTH_LONG).show() }
                }
            },
            onCancel = { ask = null },
        )
    }
}

/** New stock transfer: one material of this project, the destination project, quantity, date, remarks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferSheet(api: ApiClient, b: Branding, uuid: String, options: JSONObject?, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var material by remember { mutableStateOf<JSONObject?>(null) }
    var project by remember { mutableIntStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var remarks by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }
    val own = options?.objs("materials").orEmpty().filter { it.optBoolean("own") }
    val projects = options?.objs("projects").orEmpty()

    fun save() {
        val e = mutableMapOf<String, String>()
        val q = amount.toDoubleOrNull()
        if (material == null) e["material_id"] = "Choose the material to send."
        if (project <= 0) e["to_project_id"] = "Choose where it goes."
        if (q == null || q <= 0) e["quantity"] = "Enter a quantity above 0."
        else if (material != null && q > material!!.optDouble("on_hand", 0.0) + 0.0001) e["quantity"] = "Only ${qty(material!!.optDouble("on_hand", 0.0))} ${material!!.optString("unit")} on hand."
        if (e.isNotEmpty()) { errors = e; return }
        saving = true; errors = emptyMap()
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.send("POST", "/projects/$uuid/inventory-center/transfers", JSONObject().put("material_id", material!!.optInt("id")).put("to_project_id", project)
                        .put("quantity", q).put("transfer_date", date).put("remarks", remarks.trim().ifBlank { JSONObject.NULL }))
                }
                Toast.makeText(context, "Stock transferred", Toast.LENGTH_SHORT).show(); onDone()
            } catch (x: ApiException) { errors = x.errors.ifEmpty { mapOf("quantity" to (x.message ?: "Could not transfer")) } }
            catch (x: Exception) { errors = mapOf("quantity" to (x.message ?: "Could not transfer")) }
            saving = false
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Stock transfer", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Text("Send stock from this project to another one you are on.", color = KitMuted, fontSize = 13.sp)
            PickerField("Material", material?.let { "${it.optString("name")} · ${qty(it.optDouble("on_hand", 0.0))} ${it.optString("unit")} on hand" }, errors["material_id"]) { pick = "material" }
            PickerField("To project", projects.firstOrNull { it.optInt("id") == project }?.optString("name"), errors["to_project_id"]) { pick = "project" }
            Input("Quantity" + (material?.optString("unit")?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""), amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, errors["quantity"], keyboard = KeyboardType.Decimal)
            DatePick("Transfer date", date, errors["transfer_date"]) { date = it }
            Input("Remarks", remarks, { remarks = it }, singleLine = false)
            Text(
                if (saving) "Transferring…" else "Transfer stock", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { save() }.padding(vertical = 15.dp),
            )
        }
    }
    when (pick) {
        "material" -> MaterialPickSheet(own, b, onClose = { pick = null }) { material = it; pick = null }
        "project" -> ChoiceSheet("To project", projects.map { Choice(it.optInt("id").toString(), it.optString("name")) }, project.takeIf { it > 0 }?.toString(), b, onClose = { pick = null }) { project = it.toIntOrNull() ?: 0; pick = null }
    }
}

/** A date field (yyyy-MM-dd) that opens the date dialog. */
@Composable
internal fun DatePick(label: String, value: String, error: String? = null, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val d = runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    PickerField(label, d?.let { prettyDay(it.toString()) }, error) {
        val x = d ?: LocalDate.now()
        android.app.DatePickerDialog(context, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toString()) }, x.year, x.monthValue - 1, x.dayOfMonth).show()
    }
}

/** Search the project's materials (with what is on hand) and pick one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialPickSheet(materials: List<JSONObject>, b: Branding, exclude: Set<Int> = emptySet(), showStock: Boolean = true, onClose: () -> Unit, onPick: (JSONObject) -> Unit) {
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a material", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.padding(horizontal = 20.dp))
            com.example.gavanhomes.ui.common.SearchBox(q, { q = it }, "Search name or code…")
            val shown = materials.filter { it.optInt("id") !in exclude && (q.isBlank() || it.optString("name").contains(q, true) || it.optString("code").contains(q, true)) }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (shown.isEmpty()) Text("No materials match.", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
                shown.forEach { m ->
                    val have = m.optDouble("on_hand", 0.0)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onPick(m) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            m.str("code")?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
                        }
                        if (showStock) Text("${qty(have)} ${m.optString("unit")}", color = if (have > 0) KitGood else KitDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        else m.str("unit")?.let { Text(it, color = KitMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── site / store ─────────────────────────────

@Composable
private fun StoreList(api: ApiClient, b: Branding, uuid: String, version: Int, can: JSONObject, onChanged: () -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(version, attempt) {
        error = null
        try { data = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/inventory-center/stores") } } catch (e: Exception) { error = e.message ?: "Could not load the stores" }
    }
    val d = data
    if (d == null) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(error ?: "", color = KitMuted); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") } }
        }
        return
    }
    val stores = d.objs("stores")
    Row(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${stores.size} store${if (stores.size == 1) "" else "s"} / site${if (stores.size == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
    Text("Stock still balances for the whole project — a store is where a stock entry happened.", color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
    if (stores.isEmpty()) Text("No stores yet.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
    stores.forEach { s -> StoreCard(s.optString("name"), listOfNotNull(s.str("code"), s.str("location")).joinToString(" · "), s.optInt("materials"), s.optDouble("qty", 0.0), s.optBoolean("active", true)) { open = s } }
    d.optJSONObject("unassigned")?.takeIf { it.optInt("materials") > 0 }?.let { u -> StoreCard("Not booked to a store", "Stock entries without a store", u.optInt("materials"), u.optDouble("qty", 0.0), true) { open = JSONObject().put("id", "unassigned").put("name", "Not booked to a store") } }

    open?.let { s -> StoreDetailSheet(api, b, uuid, s, can, onClose = { open = null }, onChanged = { open = null; onChanged() }) }
}

@Composable
private fun StoreCard(name: String, sub: String, materials: Int, total: Double, active: Boolean, onClick: () -> Unit) {
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(KitGood.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Text("⌂", color = KitGood, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) Text(sub, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(qty(total), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text("$materials material${if (materials == 1) "" else "s"}", color = KitMuted, fontSize = 11.sp)
            }
        }
        if (!active) Chip("Inactive", KitMuted)
    }
}

/** What a store holds, with Edit / Delete for a real store. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreDetailSheet(api: ApiClient, b: Branding, uuid: String, s: JSONObject, can: JSONObject, onClose: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>?>(null) }
    var edit by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    val id = s.optString("id")
    val real = id != "unassigned"
    LaunchedEffect(id) { rows = runCatching { withContext(Dispatchers.IO) { api.get("/projects/$uuid/inventory-center/stores/$id").objs("data") } }.getOrDefault(emptyList()) }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            listOfNotNull(s.str("code"), s.str("location")).joinToString(" · ").takeIf { it.isNotBlank() }?.let { Text(it, color = KitMuted, fontSize = 13.sp) }
            if (real) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (can.optBoolean("edit")) SmallAction("Edit store", KitInk) { edit = true }
                if (can.optBoolean("delete")) SmallAction("Delete store", KitDanger) { ask = true }
            }
            Label("Stock here")
            val r = rows
            when {
                r == null -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
                r.isEmpty() -> Text("Nothing is booked to this store.", color = KitMuted, fontSize = 13.sp)
                else -> r.forEach { m ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            m.str("code")?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
                        }
                        Text("${qty(m.optDouble("qty", 0.0))} ${m.optString("unit")}", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    if (edit) StoreSheet(api, b, uuid, s, onClose = { edit = false }) { edit = false; onChanged() }
    if (ask) SwalConfirm(
        title = "Delete this store?", text = "Past stock entries keep their history; the store just stops showing in pickers.", confirmText = "Yes, delete",
        onConfirm = {
            ask = false
            scope.launch {
                try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/inventory-center/stores/$id") }; Toast.makeText(context, "Store removed", Toast.LENGTH_SHORT).show(); onChanged() }
                catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
            }
        },
        onCancel = { ask = false },
    )
}

/** Add a store ([store] null) or edit one: name, code, location and — when editing — active. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreSheet(api: ApiClient, b: Branding, uuid: String, store: JSONObject?, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(store?.str("name").orEmpty()) }
    var code by remember { mutableStateOf(store?.str("code").orEmpty()) }
    var location by remember { mutableStateOf(store?.str("location").orEmpty()) }
    var active by remember { mutableStateOf(store?.optBoolean("active", true) ?: true) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    fun save() {
        if (name.isBlank()) { errors = mapOf("name" to "Enter the store's name."); return }
        saving = true; errors = emptyMap()
        val body = JSONObject().put("name", name.trim()).put("code", code.trim().ifBlank { JSONObject.NULL }).put("location", location.trim().ifBlank { JSONObject.NULL })
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (store == null) api.send("POST", "/projects/$uuid/inventory-center/stores", body)
                    else api.send("PUT", "/projects/$uuid/inventory-center/stores/${store.optInt("id")}", body.put("active", active))
                }
                Toast.makeText(context, if (store == null) "Store added" else "Store saved", Toast.LENGTH_SHORT).show(); onDone()
            } catch (x: ApiException) { errors = x.errors.ifEmpty { mapOf("name" to (x.message ?: "Could not save")) } }
            catch (x: Exception) { errors = mapOf("name" to (x.message ?: "Could not save")) }
            saving = false
        }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (store == null) "Add store / site" else "Edit store", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Input("Store name", name, { name = it }, errors["name"])
            Input("Code", code, { code = it }, errors["code"])
            Input("Location", location, { location = it }, errors["location"])
            if (store != null) com.example.gavanhomes.ui.projects.form.SwitchRow("Active", "Shown in the store pickers", active) { active = it }
            Text(
                if (saving) "Saving…" else if (store == null) "Add store" else "Save store", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { save() }.padding(vertical = 15.dp),
            )
        }
    }
}

// ───────────────────────────── material receipts (GRN) ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GrnList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, note: Boolean = true, onOpen: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/inventory-center/grns", version, "Search GRN, PO or invoice no.…",
        groups = listOfNotNull(
            dateGroup(),
            options?.objs("vendors")?.takeIf { it.isNotEmpty() }?.let { v -> FilterGroup("vendor_id", "Vendor", v.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
        ),
        sorts = NewestOldest, empty = "No material received here.", noun = "receipt",
    ) { g ->
        RowCard({ onOpen(g.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(KitGood.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("↓", color = KitGood, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(g.optString("number"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(listOfNotNull(g.str("vendor"), prettyDay(g.str("date"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(rupees(g.optDouble("amount", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("${g.optInt("items")} item${if (g.optInt("items") == 1) "" else "s"} · ${qty(g.optDouble("qty", 0.0))}", color = KitMuted, fontSize = 11.sp)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.str("po")?.let { Chip(it, InvBlue) }
                g.str("invoice_no")?.let { Chip("Invoice $it", KitMuted) }
                g.str("by")?.let { Chip("By $it", KitMuted) }
            }
        }
    }
    if (note) Text("New receipts are made against a purchase order in Procurements.", color = KitMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
}

/** A GRN: PO, vendor, invoice and the received lines. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GrnSheet(api: ApiClient, b: Branding, id: Int, onClose: () -> Unit) {
    var g by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) { try { g = withContext(Dispatchers.IO) { api.detail("/inventory-center/grns/$id") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val x = g
            if (x == null) { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { if (error == null) CircularProgressIndicator(color = b.base) else Text(error ?: "", color = KitMuted) }; return@Column }
            Text(x.optString("number"), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
            Text("Material receipt", color = KitMuted, fontSize = 13.sp)
            Fact("Received", com.example.gavanhomes.ui.leads.whenLabel(x.str("date")))
            Fact("Purchase order", x.str("po")); Fact("Vendor", x.str("vendor")); Fact("Invoice no.", x.str("invoice_no"))
            if (x.has("invoice_amount") && !x.isNull("invoice_amount")) Fact("Invoice amount", rupees(x.optDouble("invoice_amount", 0.0)))
            Fact("Received by", x.str("by")); Fact("Remarks", x.str("remarks"))
            Label("Items")
            x.objs("lines").forEach { l ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${qty(l.optDouble("qty", 0.0))} ${l.optString("unit")} × ${rupees(l.optDouble("rate", 0.0))}", color = KitMuted, fontSize = 12.sp)
                    }
                    Text(rupees(l.optDouble("amount", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ───────────────────────────── audit trail ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuditList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?) {
    var types by remember { mutableStateOf<List<String>>(emptyList()) }
    FilteredList(
        api, b, "/projects/$uuid/inventory-center/audit", version, "Search changed values…",
        groups = listOfNotNull(
            types.takeIf { it.isNotEmpty() }?.let { t -> FilterGroup("type", "Record", t.map { FilterOption(it, it) }) },
            FilterGroup("event", "Change", listOf(FilterOption("created", "Created", color = KitGood), FilterOption("updated", "Updated", color = InvBlue), FilterOption("deleted", "Deleted", color = KitDanger))),
            peopleGroup("user_id", "Changed by", options),
            dateGroup(),
        ),
        sorts = NewestOldest, empty = "No changes recorded here.", noun = "change",
        onMeta = { r -> if (types.isEmpty()) types = r.optJSONArray("types")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty() },
    ) { a ->
        var open by remember { mutableStateOf(false) }
        val ev = a.optString("event")
        val c = when (ev) { "created" -> KitGood; "deleted" -> KitDanger; else -> InvBlue }
        val changes = a.objs("changes")
        RowCard(if (changes.isNotEmpty()) ({ open = !open }) else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${a.optString("type")} #${a.optInt("record")}", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(listOfNotNull(a.str("by"), com.example.gavanhomes.ui.leads.whenLabel(a.str("at"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                }
                Chip(ev.replaceFirstChar { it.uppercase() }, c)
            }
            if (changes.isNotEmpty()) {
                if (!open) Text("${changes.size} field${if (changes.size == 1) "" else "s"} · tap to see", color = KitMuted, fontSize = 12.sp)
                else changes.forEach { ch ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F5F1)).padding(10.dp)) {
                        Text(ch.optString("field").replaceFirstChar { it.uppercase() }, color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(ch.str("old")?.let { "$it  →  " }, ch.str("new") ?: "—").joinToString(""), color = KitInk, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
