package com.example.gavanhomes.ui.procurement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.gavanhomes.ui.inventory.FilteredList
import com.example.gavanhomes.ui.inventory.GrnList
import com.example.gavanhomes.ui.inventory.GrnSheet
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.NewestOldest
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.dateGroup
import com.example.gavanhomes.ui.inventory.qty
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.inventory.shortRupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.professionals.ProDonutCard
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ───────────────────────────── how each status looks ─────────────────────────────

/** A purchase order's status as its label and colour. */
internal fun poStatus(s: String?): Pair<String, Color> = when (s?.lowercase()) {
    "draft", "", null -> "Draft" to KitMuted
    "approved" -> "Approved" to InvBlue
    "pending" -> "Pending" to InvAmber
    "sent_to_supplier" -> "Sent to supplier" to InvBlue
    "accepted_by_supplier" -> "Accepted" to KitGood
    "revision_requested" -> "Revision asked" to InvAmber
    "cancelled" -> "Cancelled" to KitDanger
    else -> (s.replace('_', ' ').replaceFirstChar { it.uppercase() }) to if (s.contains("reject")) KitDanger else KitMuted
}

/** Ordered · Partially Delivered · Fully Delivered · PO Closed. */
internal fun receiptStatus(s: String?): Pair<String, Color>? = when (s?.lowercase()) {
    null, "" -> null
    "ordered" -> "Ordered" to InvAmber
    "partially delivered" -> "Partly received" to InvBlue
    "fully delivered" -> "Fully received" to KitGood
    else -> s to KitMuted
}

internal fun deliveryStatus(s: String?): Pair<String, Color> = when (s) {
    "dispatched" -> "Dispatched" to InvBlue
    "in_transit" -> "In transit" to InvPurple
    "arrived" -> "Arrived" to InvAmber
    "delivered" -> "Delivered" to KitGood
    "rejected" -> "Rejected" to KitDanger
    else -> "Pending" to KitMuted
}

internal fun supplierStatus(s: String?): Pair<String, Color> = when (s) {
    "approved" -> "Approved" to KitGood
    "rejected" -> "Rejected" to KitDanger
    else -> "Pending" to InvAmber
}

internal fun indentStatus(s: String?): Pair<String, Color> = if (s?.lowercase() == "closed") "Closed" to KitMuted else "Open" to InvBlue
internal fun fulfilment(s: String?): Pair<String, Color> = when (s?.lowercase()) {
    "partially fulfilled" -> "Partly ordered" to InvBlue
    "closed", "fulfilled" -> "Ordered" to KitGood
    else -> "Not ordered" to InvAmber
}
internal fun priorityTint(s: String?): Color = when (s?.lowercase()) { "high" -> KitDanger; "low" -> KitMuted; else -> InvAmber }

private class Page(val key: String, val label: String)

/**
 * The project's Procurements module, like the website's Procurements tab: the Dashboard (spend, what's open, PO status, top
 * suppliers, recent activity), Indents, Purchase orders, Deliveries, Receipts (GRN), Suppliers, Supplier comparison and Approvals.
 * The + button raises an indent or a purchase order, receives goods against a PO, or adds a supplier.
 * Data: /projects/{uuid}/procurement-center/…  [onOpen] opens indent · po · supplier; [onNew] starts indent · po · receive · supplier.
 */
@Composable
fun ProjectProcurement(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (String, Int) -> Unit, onNew: (String) -> Unit, onChanged: () -> Unit) {
    var view by remember { mutableStateOf("dashboard") }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var local by remember { mutableIntStateOf(0) }                     // bumped after a change made inside this module (a delivery)
    var grn by remember { mutableIntStateOf(0) }
    var delivery by remember { mutableIntStateOf(0) }

    LaunchedEffect(version, attempt, local) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/procurement-center/overview") } }
        catch (e: Exception) { error = e.message ?: "Could not load procurements" }
    }
    LaunchedEffect(uuid, version, local) { options = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/procurement-center/options") } }.getOrNull() }

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
    ProvideFab(listOfNotNull(
        FabAction("indent", "New indent", InvAmber, "✎").takeIf { can.optBoolean("create") },
        FabAction("po", "New purchase order", InvBlue, "▤").takeIf { can.optBoolean("create") },
        FabAction("receive", "Receive goods (GRN)", KitGood, "↓").takeIf { can.optBoolean("edit") },
        FabAction("supplier", "New supplier", KitInk, "☺").takeIf { can.optBoolean("create_supplier") },
    )) { onNew(it) }

    val pages = listOfNotNull(
        Page("dashboard", "Dashboard"), Page("indents", "Indents"), Page("pos", "Purchase orders"), Page("deliveries", "Deliveries"),
        Page("grn", "Receipts (GRN)"), Page("suppliers", "Suppliers").takeIf { can.optBoolean("suppliers") },
        Page("comparison", "Supplier comparison"), Page("approvals", "Approvals"),
    )
    val index = pages.indexOfFirst { it.key == view }.coerceAtLeast(0)
    ModulePages("Procurements", pages.map { it.label }, index) { view = pages[it].key }
    val v = version + local

    when (pages[index].key) {
        "dashboard" -> Dashboard(o, onOpen, onDelivery = { delivery = it }, onPage = { view = it })
        "indents" -> IndentList(api, b, uuid, v) { onOpen("indent", it) }
        "pos" -> PoList(api, b, uuid, v, options) { onOpen("po", it) }
        "deliveries" -> DeliveryList(api, b, uuid, v, options) { delivery = it }
        "grn" -> GrnList(api, b, uuid, v, JSONObject().put("vendors", options?.optJSONArray("suppliers") ?: JSONArray()), note = false) { grn = it }
        "suppliers" -> SupplierList(api, b, uuid, v) { onOpen("supplier", it) }
        "comparison" -> Comparison(api, b, uuid, v) { onOpen("supplier", it) }
        else -> Approvals(api, b, uuid, v, onOpen)
    }

    if (grn > 0) GrnSheet(api, b, grn) { grn = 0 }
    if (delivery > 0) DeliverySheet(api, b, delivery, onClose = { delivery = 0 }) { local++; onChanged() }
}

// ───────────────────────────── dashboard ─────────────────────────────

@Composable
private fun Dashboard(o: JSONObject, onOpen: (String, Int) -> Unit, onDelivery: (Int) -> Unit, onPage: (String) -> Unit) {
    val s = o.optJSONObject("stats") ?: JSONObject()
    Box(Modifier.padding(top = 4.dp)) {
        ProKpiTiles(
            listOf(
                ProKpi(shortRupees(s.optDouble("total_value", 0.0)), "PO value", KitInk), ProKpi(shortRupees(s.optDouble("received_value", 0.0)), "Received", KitGood),
                ProKpi(shortRupees(s.optDouble("pending_value", 0.0)), "Still to come", InvAmber),
                ProKpi("${s.optInt("open_indents")} / ${s.optInt("indents")}", "Open indents", InvAmber), ProKpi("${s.optInt("open_pos")} / ${s.optInt("pos")}", "Open POs", InvBlue),
                ProKpi(s.optInt("pending_deliveries").toString(), "Deliveries due", InvPurple),
                ProKpi(s.optInt("grns").toString(), "GRNs", KitGood), ProKpi(s.optInt("invoices_pending").toString(), "Invoices to verify", KitDanger),
                ProKpi(s.optInt("suppliers").toString(), "Suppliers", KitInk),
            ),
            columns = 3,
        )
    }

    val waiting = s.optInt("to_approve")
    if (waiting > 0) Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(InvAmber.copy(alpha = .12f)).clickable { onPage("approvals") }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⏳", fontSize = 18.sp)
        Text("$waiting waiting for approval", color = InvAmber, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text("See ›", color = InvAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }

    val statuses = o.objs("po_status")
    ProDonutCard("Purchase orders", "How far each one has been received", statuses.map { st ->
        val c = when (st.optString("name")) { "Ordered" -> InvAmber; "Partially Delivered" -> InvBlue; "Fully Delivered" -> KitGood; else -> KitDanger }
        Triple(receiptStatus(st.optString("name"))?.first ?: st.optString("name"), st.optInt("count"), c)
    }, empty = "No purchase orders yet.") { onPage("pos") }

    val top = o.objs("top_suppliers")
    if (top.isNotEmpty()) Card("Top suppliers", "By purchase order value") {
        val max = top.maxOf { it.optDouble("value", 0.0) }.takeIf { it > 0 } ?: 1.0
        top.forEach { t ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row {
                    Text("${t.optString("name")} · ${t.optInt("count")} PO${if (t.optInt("count") == 1) "" else "s"}", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(rupees(t.optDouble("value", 0.0)), color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                ProBar((t.optDouble("value", 0.0) / max).toFloat(), InvBlue)
            }
        }
    }

    val recentPos = o.objs("recent_pos")
    Card("Recent purchase orders") {
        if (recentPos.isEmpty()) Text("None yet.", color = KitMuted, fontSize = 13.sp)
        recentPos.forEach { p -> MiniRow(p.optString("number"), listOfNotNull(p.str("vendor"), prettyDay(p.str("date"))).joinToString(" · "), rupees(p.optDouble("total", 0.0)), poStatus(p.str("status"))) { onOpen("po", p.optInt("id")) } }
    }
    val recentIndents = o.objs("recent_indents")
    Card("Recent indents") {
        if (recentIndents.isEmpty()) Text("None yet.", color = KitMuted, fontSize = 13.sp)
        recentIndents.forEach { i -> MiniRow(i.optString("number"), listOfNotNull(i.str("purpose"), prettyDay(i.str("date"))).joinToString(" · "), null, fulfilment(i.str("po_status"))) { onOpen("indent", i.optInt("id")) } }
    }
    val due = o.objs("pending_deliveries")
    if (due.isNotEmpty()) Card("Deliveries on the way") {
        due.forEach { d -> MiniRow(d.optString("number"), listOfNotNull(d.str("po"), d.str("supplier"), d.str("expected")?.let { "due " + prettyDay(it) }).joinToString(" · "), null, deliveryStatus(d.str("status"))) { onDelivery(d.optInt("id")) } }
    }
}

/** A compact tappable row: title, sub-line, an optional amount, a status chip. */
@Composable
internal fun MiniRow(title: String, sub: String, amount: String?, status: Pair<String, Color>, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Chip(status.first, status.second)
            amount?.let { Text(it, color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

// ───────────────────────────── indents ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndentList(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/procurement-center/indents", version, "Search indent no., purpose or remarks…",
        groups = listOf(
            FilterGroup("status", "Status", listOf(FilterOption("open", "Open", color = InvBlue), FilterOption("closed", "Closed", color = KitMuted))),
            FilterGroup("po_status", "Ordered", listOf(FilterOption("not fulfilled", "Not ordered", color = InvAmber), FilterOption("partially fulfilled", "Partly ordered", color = InvBlue), FilterOption("closed", "Ordered", color = KitGood))),
            FilterGroup("priority", "Priority", listOf("High", "Medium", "Low").map { FilterOption(it.lowercase(), it, color = priorityTint(it)) }),
            dateGroup(),
        ),
        sorts = NewestOldest, empty = "No indents here.", noun = "indent",
    ) { i ->
        RowCard({ onOpen(i.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(InvAmber.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("✎", color = InvAmber, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(i.optString("number"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(listOfNotNull(prettyDay(i.str("date")), i.str("by")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                }
                Text("${i.optInt("items")} item${if (i.optInt("items") == 1) "" else "s"}", color = KitMuted, fontSize = 12.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                indentStatus(i.str("status")).let { (l, c) -> Chip(l, c) }
                fulfilment(i.str("po_status")).let { (l, c) -> Chip(l, c) }
                i.str("priority")?.let { Chip(it, priorityTint(it)) }
                i.str("required_date")?.let { Chip("Needed " + (prettyDay(it) ?: it), KitMuted) }
            }
            (i.str("purpose") ?: i.str("remarks"))?.let { Text(it, color = KitMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

// ───────────────────────────── purchase orders ─────────────────────────────

@Composable
private fun PoList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, onOpen: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/procurement-center/pos", version, "Search PO no., supplier or remarks…",
        groups = listOfNotNull(
            FilterGroup("status", "Status", listOf("draft", "sent_to_supplier", "accepted_by_supplier", "revision_requested", "cancelled").map { k -> poStatus(k).let { (l, c) -> FilterOption(k, l, color = c) } }),
            FilterGroup("delivery_status", "Received", listOf("ordered", "partially delivered", "fully delivered").map { k -> receiptStatus(k)!!.let { (l, c) -> FilterOption(k, l, color = c) } }),
            options?.objs("suppliers")?.takeIf { it.isNotEmpty() }?.let { s -> FilterGroup("vendor_id", "Supplier", s.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
            dateGroup(),
        ),
        sorts = NewestOldest + ("value" to "Highest value first"), empty = "No purchase orders here.", noun = "purchase order",
    ) { p -> PoCard(p) { onOpen(p.optInt("id")) } }
}

/** A purchase order: number, supplier, amount; status and receipt chips; how much has arrived. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PoCard(p: JSONObject, onClick: () -> Unit) {
    val (sl, sc) = poStatus(p.str("status"))
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(sc.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("▤", color = sc, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(p.optString("number"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(p.str("vendor"), prettyDay(p.str("date"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(rupees(p.optDouble("total", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(sl, sc)
            receiptStatus(p.str("delivery_status"))?.let { (l, c) -> Chip(l, c) }
            p.str("expected")?.let { Chip("Due " + (prettyDay(it) ?: it), KitMuted) }
        }
        val pct = p.optInt("received_pct")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ProBar(pct / 100f, if (pct >= 100) KitGood else InvBlue) }
            Text("$pct% received", color = KitMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

// ───────────────────────────── deliveries ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeliveryList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, onOpen: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/procurement-center/deliveries", version, "Search delivery no., vehicle or PO…",
        groups = listOfNotNull(
            FilterGroup("status", "Status", listOf("pending", "dispatched", "in_transit", "arrived", "delivered", "rejected").map { k -> deliveryStatus(k).let { (l, c) -> FilterOption(k, l, color = c) } }),
            options?.objs("suppliers")?.takeIf { it.isNotEmpty() }?.let { s -> FilterGroup("supplier_id", "Supplier", s.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
            dateGroup(),
        ),
        sorts = NewestOldest, empty = "No deliveries here.", noun = "delivery",
    ) { d ->
        val (l, c) = deliveryStatus(d.str("status"))
        RowCard({ onOpen(d.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("⛟", color = c, fontSize = 17.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(d.optString("number"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(listOfNotNull(d.str("po"), d.str("supplier")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Chip(l, c)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.str("vehicle")?.let { Chip("🚚 $it", KitMuted) }
                d.str("expected")?.let { Chip("Due " + (prettyDay(it) ?: it), KitMuted) }
                if (d.optBoolean("arrival_approved")) Chip("Arrival approved", KitGood)
                d.str("acceptance")?.let { Chip(when (it) { "full" -> "Accepted"; "partial" -> "Partly accepted"; else -> "Rejected" }, if (it == "rejected") KitDanger else KitGood) }
                d.str("grn_no")?.let { Chip(it, InvBlue) }
            }
        }
    }
}

// ───────────────────────────── suppliers ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupplierList(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    var counts by remember { mutableStateOf<JSONObject?>(null) }
    FilteredList(
        api, b, "/projects/$uuid/procurement-center/suppliers", version, "Search name, company, phone or city…",
        groups = listOf(FilterGroup("status", "Status", listOf("pending", "approved", "rejected").map { k -> supplierStatus(k).let { (l, c) -> FilterOption(k, l, counts?.optInt(k), c) } })),
        sorts = NewestOldest + listOf("name" to "Name A–Z", "spend" to "Highest spend first"), empty = "No suppliers here.", noun = "supplier",
        onMeta = { counts = it.optJSONObject("counts") },
    ) { s ->
        val (l, c) = supplierStatus(s.str("status"))
        RowCard({ onOpen(s.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.example.gavanhomes.ui.common.Initials(s.optString("name"), size = 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(s.str("company"), s.str("city")).joinToString(" · ").ifEmpty { s.str("phone") ?: "" }, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Chip(l, c)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("${s.optInt("pos")} PO${if (s.optInt("pos") == 1) "" else "s"}", InvBlue)
                if (s.optDouble("spend", 0.0) > 0) Chip(rupees(s.optDouble("spend", 0.0)), KitGood)
                s.str("gst")?.let { Chip("GST $it", KitMuted) }
            }
            s.str("phone")?.let { com.example.gavanhomes.ui.tasks.PhoneActions(it) }
        }
    }
}

// ───────────────────────────── supplier comparison ─────────────────────────────

@Composable
private fun Loaded(api: ApiClient, b: Branding, path: String, version: Int, content: @Composable (JSONObject) -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(path, version, attempt) { error = null; try { data = withContext(Dispatchers.IO) { api.get(path) } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val d = data
    if (d == null) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(error ?: "", color = KitMuted); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") } }
        }
        return
    }
    content(d)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Comparison(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    Loaded(api, b, "/projects/$uuid/procurement-center/comparison", version) { d ->
        val rows = d.objs("data")
        Text("Spend, delivery and quality by supplier", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        if (rows.isEmpty()) Text("No suppliers yet.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
        val top = rows.maxOfOrNull { it.optDouble("spend", 0.0) }?.takeIf { it > 0 } ?: 1.0
        rows.forEach { r ->
            RowCard({ onOpen(r.optInt("id")) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        r.str("company")?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(rupees(r.optDouble("spend", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text("${r.optInt("pos")} PO${if (r.optInt("pos") == 1) "" else "s"} · avg ${shortRupees(r.optDouble("avg", 0.0))}", color = KitMuted, fontSize = 11.sp)
                    }
                }
                ProBar((r.optDouble("spend", 0.0) / top).toFloat(), InvBlue)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Paid ${shortRupees(r.optDouble("paid", 0.0))}", KitGood)
                    Chip("${r.optInt("delivered")} delivered", KitMuted)
                    if (!r.isNull("on_time")) r.optInt("on_time").let { Chip("On time $it%", if (it >= 80) KitGood else if (it >= 50) InvAmber else KitDanger) }
                    if (!r.isNull("quality")) r.optInt("quality").let { Chip("Quality $it%", if (it >= 90) KitGood else if (it >= 70) InvAmber else KitDanger) }
                    if (r.optInt("rejected") > 0) Chip("${r.optInt("rejected")} rejected", KitDanger)
                }
            }
        }
    }
}

// ───────────────────────────── approvals ─────────────────────────────

@Composable
private fun Approvals(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (String, Int) -> Unit) {
    Loaded(api, b, "/projects/$uuid/procurement-center/approvals", version) { r ->
        val d = r.optJSONObject("data") ?: JSONObject()
        val indents = d.objs("indents"); val pos = d.objs("pos"); val invoices = d.objs("invoices")
        if (indents.isEmpty() && pos.isEmpty() && invoices.isEmpty()) Text("Nothing is waiting for approval.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
        if (indents.isNotEmpty()) Card("Indents without a PO", "${indents.size} open, nothing ordered yet") {
            indents.forEach { i -> MiniRow(i.optString("number"), listOfNotNull(i.str("purpose"), prettyDay(i.str("date")), i.str("by")).joinToString(" · "), null, i.str("priority")?.let { it to priorityTint(it) } ?: ("Open" to InvBlue)) { onOpen("indent", i.optInt("id")) } }
        }
        if (pos.isNotEmpty()) Card("Purchase orders in draft", "${pos.size} not yet sent to the supplier") {
            pos.forEach { p -> MiniRow(p.optString("number"), listOfNotNull(p.str("vendor"), prettyDay(p.str("date"))).joinToString(" · "), rupees(p.optDouble("total", 0.0)), poStatus(p.str("status"))) { onOpen("po", p.optInt("id")) } }
        }
        if (invoices.isNotEmpty()) Card("Invoices to verify", "${invoices.size} pending — verified on the website") {
            invoices.forEach { v -> MiniRow(v.str("number") ?: "Invoice #${v.optInt("id")}", listOfNotNull(v.str("po"), v.str("vendor"), prettyDay(v.str("date"))).joinToString(" · "), rupees(v.optDouble("amount", 0.0)), "Pending" to InvAmber) { onOpen("po", v.optInt("po_id")) } }
        }
    }
}

/** "1.5" from a JSON number, without a trailing ".0" — for prefilling quantity fields. */
internal fun plain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else qty(v).replace(",", "")
