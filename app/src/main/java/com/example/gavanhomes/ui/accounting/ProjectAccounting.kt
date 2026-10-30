package com.example.gavanhomes.ui.accounting

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
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.NewestOldest
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.dateGroup
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.inventory.shortRupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.procurement.MiniRow
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun approval(s: String?): Pair<String, Color> = when (s) { "approved" -> "Approved" to KitGood; "rejected" -> "Rejected" to KitDanger; else -> "Pending" to InvAmber }
internal fun invoiceStatus(s: String?): Pair<String, Color> = when (s) { "verified" -> "Verified" to InvBlue; "approved" -> "Approved" to KitGood; "rejected" -> "Rejected" to KitDanger; "paid" -> "Paid" to KitGood; else -> "To verify" to InvAmber }

private class Page(val key: String, val label: String)

/**
 * The project's Accounting module for the phone: the Dashboard (money in and out, balance, expenses, what suppliers are owed, where
 * money went), Payment In / Out, Expenses and Supplier invoices. The + button records a payment in or out, or an expense. (Ledgers,
 * day book, sales, banks, cheques and loans stay on the website.) Data: /projects/{uuid}/accounting-center/…
 * [onOpen] opens payment · expense · invoice; [onNew] starts payment_in · payment_out · expense.
 */
@Composable
fun ProjectAccounting(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (String, Int) -> Unit, onNew: (String) -> Unit) {
    var view by remember { mutableStateOf("dashboard") }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(version, attempt) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/overview") } } catch (e: Exception) { error = e.message ?: "Could not load accounting" }
    }
    LaunchedEffect(uuid, version) { options = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/accounting-center/options") } }.getOrNull() }
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
        FabAction("payment_in", "Payment in", KitGood, "↓"), FabAction("payment_out", "Payment out", KitDanger, "↑"), FabAction("expense", "New expense", InvAmber, "₹"),
    )) { onNew(it) }
    val pages = listOf(Page("dashboard", "Dashboard"), Page("payments", "Payment in / out"), Page("expenses", "Expenses"), Page("invoices", "Supplier invoices"))
    val index = pages.indexOfFirst { it.key == view }.coerceAtLeast(0)
    ModulePages("Accounting", pages.map { it.label }, index) { view = pages[it].key }
    when (pages[index].key) {
        "dashboard" -> Dashboard(o, onOpen) { view = it }
        "payments" -> PaymentList(api, b, uuid, version, options) { onOpen("payment", it) }
        "expenses" -> ExpenseList(api, b, uuid, version, options) { onOpen("expense", it) }
        else -> InvoiceList(api, b, uuid, version) { onOpen("invoice", it) }
    }
}

@Composable
private fun Dashboard(o: JSONObject, onOpen: (String, Int) -> Unit, onPage: (String) -> Unit) {
    val s = o.optJSONObject("stats") ?: JSONObject()
    Box(Modifier.padding(top = 4.dp)) {
        ProKpiTiles(listOf(
            ProKpi(shortRupees(s.optDouble("in", 0.0)), "Money in", KitGood), ProKpi(shortRupees(s.optDouble("out", 0.0)), "Money out", KitDanger),
            ProKpi(shortRupees(s.optDouble("balance", 0.0)), "Balance", if (s.optDouble("balance", 0.0) < 0) KitDanger else KitInk),
            ProKpi(shortRupees(s.optDouble("in_month", 0.0)), "In this month", KitGood), ProKpi(shortRupees(s.optDouble("out_month", 0.0)), "Out this month", KitDanger),
            ProKpi(s.optInt("pending").toString(), "To approve", InvAmber),
            ProKpi(shortRupees(s.optDouble("expenses", 0.0)), "Expenses", InvAmber), ProKpi(shortRupees(s.optDouble("supplier_due", 0.0)), "Owed to suppliers", KitDanger),
            ProKpi(s.optInt("invoices_pending").toString(), "Invoices to verify", InvBlue),
        ), columns = 3)
    }
    val inV = s.optDouble("in", 0.0); val outV = s.optDouble("out", 0.0)
    if (inV + outV > 0) Card("In vs out", "Approved and pending payments") {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            if (inV > 0) Box(Modifier.weight(inV.toFloat()).height(14.dp).background(KitGood))
            if (outV > 0) Box(Modifier.weight(outV.toFloat()).height(14.dp).background(KitDanger))
        }
        Row { Text("In ${rupees(inV)}", color = KitGood, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("Out ${rupees(outV)}", color = KitDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
    val cats = o.objs("by_category")
    if (cats.isNotEmpty()) Card("Where money went", "Payments out and expenses, by category") {
        val top = cats.maxOf { it.optDouble("value", 0.0) }.takeIf { it > 0 } ?: 1.0
        cats.forEach { c ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row { Text(c.optString("name"), color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(rupees(c.optDouble("value", 0.0)), color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                ProBar((c.optDouble("value", 0.0) / top).toFloat(), InvAmber)
            }
        }
    }
    val recent = o.objs("recent")
    Card("Latest payments") {
        if (recent.isEmpty()) Text("None yet.", color = KitMuted, fontSize = 13.sp)
        recent.forEach { t -> MiniRow((if (t.optString("type") == "payment_in") "↓ " else "↑ ") + rupees(t.optDouble("amount", 0.0)), listOfNotNull(t.str("party"), t.str("category"), prettyDay(t.str("date"))).joinToString(" · "), null, approval(t.str("status"))) { onOpen("payment", t.optInt("id")) } }
        if (recent.isNotEmpty()) com.example.gavanhomes.ui.leads.SmallAction("See all", InvBlue) { onPage("payments") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, onOpen: (Int) -> Unit) {
    var totals by remember { mutableStateOf<JSONObject?>(null) }
    fun names(k: String) = options?.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    totals?.let { t -> Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("In ${rupees(t.optDouble("in", 0.0))}", KitGood); Chip("Out ${rupees(t.optDouble("out", 0.0))}", KitDanger)
    } }
    FilteredList(
        api, b, "/projects/$uuid/accounting-center/payments", version, "Search party, category, reference or notes…",
        groups = listOfNotNull(
            FilterGroup("type", "Type", listOf(FilterOption("payment_in", "Payment in", color = KitGood), FilterOption("payment_out", "Payment out", color = KitDanger))),
            FilterGroup("status", "Approval", listOf("pending", "approved", "rejected").map { k -> approval(k).let { (l, c) -> FilterOption(k, l, color = c) } }),
            names("methods").takeIf { it.isNotEmpty() }?.let { m -> FilterGroup("method", "Method", m.map { FilterOption(it, it) }) },
            names("categories").takeIf { it.isNotEmpty() }?.let { m -> FilterGroup("category", "Category", m.map { FilterOption(it, it) }) },
            dateGroup(),
        ),
        sorts = NewestOldest + ("amount" to "Largest first"), empty = "No payments here.", noun = "payment", onMeta = { totals = it.optJSONObject("totals") },
    ) { t -> PaymentCard(t) { onOpen(t.optInt("id")) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaymentCard(t: JSONObject, onClick: () -> Unit) {
    val incoming = t.optString("type") == "payment_in"
    val c = if (incoming) KitGood else KitDanger
    val (al, ac) = approval(t.str("status"))
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(if (incoming) "↓" else "↑", color = c, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.str("party") ?: if (incoming) "Payment in" else "Payment out", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(prettyDay(t.str("date")), t.str("time"), t.str("method")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
            }
            Text((if (incoming) "+" else "−") + rupees(t.optDouble("amount", 0.0)), color = c, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(al, ac); t.str("category")?.let { Chip(it, InvBlue) }; t.str("reference")?.let { Chip("Ref $it", KitMuted) }
        }
        t.str("notes")?.let { Text(it, color = KitMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, onOpen: (Int) -> Unit) {
    var sum by remember { mutableStateOf<Double?>(null) }
    sum?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { Chip("Total ${rupees(it)}", InvAmber) } }
    FilteredList(
        api, b, "/projects/$uuid/accounting-center/expenses", version, "Search party, item, description or no.…",
        groups = listOfNotNull(
            options?.objs("expense_categories")?.takeIf { it.isNotEmpty() }?.let { c -> FilterGroup("category_id", "Category", c.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
            dateGroup(),
        ),
        sorts = NewestOldest + ("amount" to "Largest first"), empty = "No expenses here.", noun = "expense", onMeta = { sum = it.optDouble("sum", 0.0) },
    ) { e ->
        RowCard({ onOpen(e.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(InvAmber.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("₹", color = InvAmber, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(e.str("party") ?: e.str("category") ?: "Expense", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull("#${e.optInt("no")}", prettyDay(e.str("date")), e.str("payment_type")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(rupees(e.optDouble("total", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                e.str("category")?.let { Chip(it, InvBlue) }
                if (e.optBoolean("gst")) Chip("GST ${rupees(e.optDouble("tax", 0.0))}", KitMuted)
                Chip("${e.optInt("items")} item${if (e.optInt("items") == 1) "" else "s"}", KitMuted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvoiceList(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/accounting-center/invoices", version, "Search invoice, PO or supplier…",
        groups = listOf(FilterGroup("status", "Status", listOf("pending", "verified", "approved", "rejected").map { k -> invoiceStatus(k).let { (l, c) -> FilterOption(k, l, color = c) } }), dateGroup()),
        sorts = NewestOldest + ("amount" to "Largest first"), empty = "No supplier invoices here.", noun = "invoice",
    ) { i ->
        val (l, c) = invoiceStatus(i.str("status"))
        RowCard({ onOpen(i.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("▤", color = c, fontSize = 17.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(i.str("vendor") ?: "Supplier", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(i.str("no")?.let { "Inv $it" }, i.str("po"), prettyDay(i.str("date"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(rupees(i.optDouble("amount", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(l, c); Chip("Paid ${rupees(i.optDouble("paid", 0.0))}", KitGood)
                if (i.optDouble("due", 0.0) > 0) Chip("Due ${rupees(i.optDouble("due", 0.0))}", KitDanger)
            }
        }
    }
}
