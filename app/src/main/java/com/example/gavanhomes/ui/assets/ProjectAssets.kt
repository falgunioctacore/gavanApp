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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.FabAction
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.ModulePages
import com.example.gavanhomes.ui.common.ProvideFab
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.inventory.FilteredList
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.NewestOldest
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.dateGroup
import com.example.gavanhomes.ui.inventory.peopleGroup
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.inventory.shortRupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.leads.whenLabel
import com.example.gavanhomes.ui.procurement.MiniRow
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.professionals.ProDonutCard
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun pretty(s: String?): String = s.orEmpty().replace('_', ' ').trim().replaceFirstChar { it.uppercase() }.ifEmpty { "—" }

internal fun assetStatus(s: String?): Pair<String, Color> = when (s) {
    "active" -> "Active" to KitGood
    "under_maintenance" -> "Under maintenance" to InvAmber
    "in_storage" -> "In storage" to InvBlue
    "transferred" -> "Being moved" to InvPurple
    "disposed_sold" -> "Disposed / sold" to KitMuted
    "lost_damaged" -> "Lost / damaged" to KitDanger
    "inactive" -> "Inactive" to KitMuted
    else -> pretty(s) to KitMuted
}

internal fun jobStatus(s: String?): Pair<String, Color> = when (s?.lowercase()) {
    "completed" -> "Completed" to KitGood
    "in progress" -> "In progress" to InvBlue
    "overdue" -> "Overdue" to KitDanger
    "escalated" -> "Escalated" to KitDanger
    else -> "Scheduled" to InvAmber
}

private class Page(val key: String, val label: String)

/**
 * The project's Assets module, like the website's Assets tab: the Dashboard (value, status, category, maintenance due, documents
 * expiring), the Asset register, Maintenance and Transfers. The + button adds an asset, schedules maintenance or moves an asset.
 * Data: /projects/{uuid}/asset-center/…  [onOpen] opens asset · maintenance; [onNew] starts asset · maintenance · transfer.
 */
@Composable
fun ProjectAssets(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (String, Int) -> Unit, onNew: (String) -> Unit, onChanged: () -> Unit) {
    var view by remember { mutableStateOf("dashboard") }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var local by remember { mutableIntStateOf(0) }
    LaunchedEffect(version, attempt, local) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/asset-center/overview") } } catch (e: Exception) { error = e.message ?: "Could not load assets" }
    }
    LaunchedEffect(uuid, version) { options = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/asset-center/options") } }.getOrNull() }
    val o = overview
    if (o == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") } }
        }
        return
    }
    val can = o.optJSONObject("can") ?: JSONObject()
    ProvideFab(if (!can.optBoolean("create")) emptyList() else listOf(
        FabAction("asset", "New asset", KitInk, "◈"), FabAction("maintenance", "Schedule maintenance", InvAmber, "⚙"), FabAction("transfer", "Move an asset", InvPurple, "⇄"),
    )) { onNew(it) }
    val pages = listOf(Page("dashboard", "Dashboard"), Page("assets", "Assets"), Page("maintenance", "Maintenance"), Page("transfers", "Transfers"))
    val index = pages.indexOfFirst { it.key == view }.coerceAtLeast(0)
    ModulePages("Assets", pages.map { it.label }, index) { view = pages[it].key }
    val v = version + local
    when (pages[index].key) {
        "dashboard" -> Dashboard(o, onOpen) { view = it }
        "assets" -> AssetList(api, b, uuid, v, options) { onOpen("asset", it) }
        "maintenance" -> MaintenanceList(api, b, uuid, v, options) { onOpen("maintenance", it) }
        else -> TransferList(api, b, uuid, v, can) { local++; onChanged() }
    }
}

@Composable
private fun Dashboard(o: JSONObject, onOpen: (String, Int) -> Unit, onPage: (String) -> Unit) {
    val s = o.optJSONObject("stats") ?: JSONObject()
    Box(Modifier.padding(top = 4.dp)) {
        ProKpiTiles(listOf(
            ProKpi(s.optInt("assets").toString(), "Assets", KitInk), ProKpi(shortRupees(s.optDouble("purchase", 0.0)), "Bought for", KitInk),
            ProKpi(shortRupees(s.optDouble("book_value", 0.0)), "Book value", KitGood), ProKpi(shortRupees(s.optDouble("maintenance_spent", 0.0)), "Maintenance spent", InvAmber),
            ProKpi(s.optInt("maintenance_due").toString(), "Maintenance due", InvAmber), ProKpi(s.optInt("expiring").toString(), "Papers expiring", KitDanger),
            ProKpi(s.optInt("under_maintenance").toString(), "In repair", InvBlue), ProKpi(s.optInt("transfers_pending").toString(), "Moves pending", InvPurple), ProKpi(s.optInt("active").toString(), "Active", KitGood),
        ), columns = 3)
    }
    ProDonutCard("By status", null, o.objs("by_status").map { st -> assetStatus(st.optString("name")).let { (l, c) -> Triple(l, st.optInt("count"), c) } }, empty = "No assets yet.") { onPage("assets") }
    val cats = o.objs("by_category")
    if (cats.isNotEmpty()) Card("By category", "Count and book value") {
        val top = cats.maxOf { it.optDouble("value", 0.0) }.takeIf { it > 0 } ?: 1.0
        cats.forEach { c ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row { Text("${c.optString("name")} · ${c.optInt("count")}", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(rupees(c.optDouble("value", 0.0)), color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                ProBar((c.optDouble("value", 0.0) / top).toFloat(), InvBlue)
            }
        }
    }
    val due = o.objs("due")
    if (due.isNotEmpty()) Card("Maintenance due", "In the next two weeks, or overdue") { due.forEach { m -> MiniRow(m.str("asset") ?: "Asset", listOfNotNull(m.str("type"), m.str("next")?.let { "due " + prettyDay(it) }).joinToString(" · "), null, jobStatus(m.str("status"))) { onOpen("maintenance", m.optInt("id")) } } }
    val exp = o.objs("expiring_list")
    if (exp.isNotEmpty()) Card("Papers expiring", "Insurance, warranty, AMC, registration, PUC — within 30 days") {
        exp.forEach { e -> MiniRow(e.optString("asset"), "${e.optString("what")} · ${prettyDay(e.str("date"))}", null, (if (e.optBoolean("over")) "Expired" else "Expiring") to (if (e.optBoolean("over")) KitDanger else InvAmber)) { onOpen("asset", e.optInt("id")) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, onOpen: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/asset-center/assets", version, "Search name, code, location, vendor or reg. no.…",
        groups = listOfNotNull(
            FilterGroup("status", "Status", listOf("active", "under_maintenance", "in_storage", "transferred", "inactive", "disposed_sold", "lost_damaged").map { k -> assetStatus(k).let { (l, c) -> FilterOption(k, l, color = c) } }),
            options?.objs("categories")?.takeIf { it.isNotEmpty() }?.let { c -> FilterGroup("category_id", "Category", c.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
            peopleGroup("assigned_to", "With", options),
        ),
        sorts = NewestOldest + listOf("value" to "Highest price first", "name" to "Name A–Z"), empty = "No assets here.", noun = "asset",
    ) { a ->
        val (l, c) = assetStatus(a.str("status"))
        RowCard({ onOpen(a.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("◈", color = c, fontSize = 19.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(a.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(a.str("code"), a.str("category")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(shortRupees(a.optDouble("book_value", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("book value", color = KitMuted, fontSize = 10.sp)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(l, c)
                a.str("location")?.let { Chip("📍 $it", KitMuted) }
                a.str("assignee")?.let { Chip("👤 $it", KitMuted) }
                if (a.optDouble("maintenance", 0.0) > 0) Chip("Repairs ${shortRupees(a.optDouble("maintenance", 0.0))}", InvAmber)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaintenanceList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, onOpen: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/asset-center/maintenances", version, "Search asset, code, vendor or remarks…",
        groups = listOfNotNull(
            FilterGroup("due", "When", listOf(FilterOption("soon", "Due in 2 weeks / overdue", color = InvAmber))),
            FilterGroup("status", "Status", listOf("Scheduled", "In Progress", "Completed", "Overdue", "Escalated").map { k -> jobStatus(k).let { (l, c) -> FilterOption(k, l, color = c) } }),
            FilterGroup("type", "Type", listOf("Routine", "Emergency", "AMC", "Ad-hoc").map { FilterOption(it, it) }),
            options?.objs("assets")?.takeIf { it.isNotEmpty() }?.let { a -> FilterGroup("asset_id", "Asset", a.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
        ),
        sorts = listOf("due" to "Next due first") + NewestOldest, empty = "No maintenance here.", noun = "job", single = setOf("due"),
    ) { m -> MaintenanceCard(m) { onOpen(m.optInt("id")) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MaintenanceCard(m: JSONObject, onClick: () -> Unit) {
    val (l, c) = jobStatus(m.str("status"))
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("⚙", color = c, fontSize = 18.sp) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(m.str("asset") ?: "Asset", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(m.str("code"), m.str("type"), m.str("frequency")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Chip(l, c)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            m.str("next")?.let { Chip("Next " + (prettyDay(it) ?: it), InvAmber) }
            m.str("last")?.let { Chip("Last " + (prettyDay(it) ?: it), KitMuted) }
            m.str("vendor")?.let { Chip(it, KitMuted) }
            if (m.optDouble("actual", 0.0) > 0) Chip(rupees(m.optDouble("actual", 0.0)), KitGood) else if (m.optDouble("estimated", 0.0) > 0) Chip("Est. " + rupees(m.optDouble("estimated", 0.0)), KitMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransferList(api: ApiClient, b: Branding, uuid: String, version: Int, can: JSONObject, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf<JSONObject?>(null) }
    fun call(ok: String, run: () -> JSONObject) = scope.launch {
        try { val r = withContext(Dispatchers.IO) { run() }; Toast.makeText(context, r.str("message") ?: ok, Toast.LENGTH_SHORT).show(); onChanged() }
        catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
    }
    FilteredList(
        api, b, "/projects/$uuid/asset-center/transfers", version, "Search asset, code or location…",
        groups = listOf(FilterGroup("received", "Arrived", listOf(FilterOption("no", "Not yet", color = InvAmber), FilterOption("yes", "Arrived", color = KitGood))), dateGroup()),
        sorts = NewestOldest, single = setOf("received"), empty = "No transfers here.", noun = "transfer",
    ) { t ->
        val arrived = t.str("received") != null
        RowCard(null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(InvPurple.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("⇄", color = InvPurple, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.str("asset") ?: "Asset", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(t.str("code"), prettyDay(t.str("date"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                }
                Chip(if (arrived) "Arrived" else "On the way", if (arrived) KitGood else InvAmber)
            }
            Text("${t.str("from") ?: "?"}  →  ${t.str("to") ?: "?"}", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                t.str("by")?.let { Chip("By $it", KitMuted) }; t.str("approver")?.let { Chip("Approved by $it", KitMuted) }; t.str("condition")?.let { Chip(it, KitMuted) }
                if (arrived) Chip("Received ${whenLabel(t.str("received"))}" + (t.str("received_by")?.let { " · $it" } ?: ""), KitGood)
            }
            t.str("reason")?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (can.optBoolean("edit")) SmallAction(if (arrived) "Undo arrival" else "✓  Confirm arrived", if (arrived) KitMuted else KitGood) {
                    call(if (arrived) "Arrival undone" else "Arrival confirmed") { api.send("POST", "/projects/$uuid/asset-center/transfers/${t.optInt("id")}/confirm", JSONObject().put("confirmed", if (arrived) "no" else "yes")) }
                }
                if (can.optBoolean("delete")) SmallAction("Delete", KitDanger) { ask = t }
            }
        }
    }
    ask?.let { t -> SwalConfirm(title = "Delete this transfer?", text = "The record goes; the asset keeps its current location and status.", confirmText = "Yes, delete",
        onConfirm = { ask = null; call("Transfer deleted") { api.delete("/projects/$uuid/asset-center/transfers/${t.optInt("id")}") } }, onCancel = { ask = null }) }
}
