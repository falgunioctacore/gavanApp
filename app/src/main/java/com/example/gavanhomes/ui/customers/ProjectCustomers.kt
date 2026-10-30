package com.example.gavanhomes.ui.customers

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
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.NewestOldest
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.dateGroup
import com.example.gavanhomes.ui.inventory.peopleGroup
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.inventory.shortRupees
import com.example.gavanhomes.ui.leads.Chip
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
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ───────────────────────────── how each status looks ─────────────────────────────

internal fun pretty(s: String?): String = s.orEmpty().replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }.ifEmpty { "—" }

internal fun bookingTint(s: String?): Color = when (s?.lowercase()) {
    "confirmed", "approved", "completed", "registered", "active" -> KitGood
    "cancelled", "rejected" -> KitDanger
    "draft" -> KitMuted
    else -> InvAmber
}

internal fun visitStatus(s: String?): Pair<String, Color> = when (s) {
    "approved", "scheduled" -> pretty(s) to InvBlue
    "completed" -> "Completed" to KitGood
    "rescheduled" -> "Rescheduled" to InvPurple
    "cancelled", "rejected" -> pretty(s) to KitDanger
    else -> "Requested" to InvAmber
}

internal fun issueStatus(s: String?): Pair<String, Color> = when (s) {
    "assigned" -> "Assigned" to InvBlue
    "in_progress" -> "In progress" to InvPurple
    "resolved" -> "Resolved" to KitGood
    "closed" -> "Closed" to KitMuted
    "reopened" -> "Reopened" to KitDanger
    else -> "Open" to InvAmber
}

internal fun priorityTint(s: String?): Color = when (s?.lowercase()) { "urgent" -> KitDanger; "high" -> Color(0xFFE0662B); "low" -> KitMuted; else -> InvAmber }

internal fun loanTint(s: String?): Color = when (s) { "active", "approved" -> KitGood; "closed" -> KitMuted; "defaulted" -> KitDanger; else -> InvAmber }

internal fun paymentTint(s: String?): Color = when (s?.lowercase()) { "paid", "completed", "cleared", "received", "success" -> KitGood; "failed", "bounced", "cancelled" -> KitDanger; else -> InvAmber }

private class Page(val key: String, val label: String)

/**
 * The project's Customers module, like the website's Customers tab: the Dashboard (sales, collections, visits, issues, loans),
 * Bookings, Payments, Customers, Client visits, Issues and Loans. The + button adds a customer, schedules a visit, raises an issue
 * or adds a loan (bookings are made on the website's booking form).
 * Data: /projects/{uuid}/customer-center/…  [onOpen] opens booking · customer · issue · visit · loan; [onNew] starts customer · visit · issue · loan.
 */
@Composable
fun ProjectCustomers(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (String, Int) -> Unit, onNew: (String) -> Unit) {
    var view by remember { mutableStateOf("dashboard") }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(version, attempt) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/customer-center/overview") } }
        catch (e: Exception) { error = e.message ?: "Could not load customers" }
    }
    LaunchedEffect(uuid, version) { options = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/customer-center/options") } }.getOrNull() }

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
        FabAction("customer", "New customer", KitInk, "☺").takeIf { can.optBoolean("create_customer") },
        FabAction("visit", "Schedule visit", InvBlue, "⌂").takeIf { can.optBoolean("create_visit") },
        FabAction("issue", "Raise issue", KitDanger, "!").takeIf { can.optBoolean("create_issue") },
        FabAction("loan", "New loan", KitGood, "₹").takeIf { can.optBoolean("create_loan") },
    )) { onNew(it) }

    val pages = listOfNotNull(
        Page("dashboard", "Dashboard"), Page("bookings", "Bookings").takeIf { can.optBoolean("bookings") }, Page("payments", "Payments").takeIf { can.optBoolean("bookings") },
        Page("customers", "Customers"), Page("visits", "Client visits").takeIf { can.optBoolean("visits") },
        Page("issues", "Issues").takeIf { can.optBoolean("issues") }, Page("loans", "Loans").takeIf { can.optBoolean("loans") },
    )
    val index = pages.indexOfFirst { it.key == view }.coerceAtLeast(0)
    ModulePages("Customers", pages.map { it.label }, index) { view = pages[it].key }

    when (pages[index].key) {
        "dashboard" -> Dashboard(o, onOpen) { view = it }
        "bookings" -> BookingList(api, b, uuid, version) { onOpen("booking", it) }
        "payments" -> PaymentList(api, b, uuid, version) { onOpen("booking", it) }
        "customers" -> CustomerList(api, b, uuid, version) { onOpen("customer", it) }
        "visits" -> VisitList(api, b, uuid, version) { onOpen("visit", it) }
        "issues" -> IssueList(api, b, uuid, version, options) { onOpen("issue", it) }
        else -> LoanList(api, b, uuid, version) { onOpen("loan", it) }
    }
}

// ───────────────────────────── dashboard ─────────────────────────────

@Composable
private fun Dashboard(o: JSONObject, onOpen: (String, Int) -> Unit, onPage: (String) -> Unit) {
    val s = o.optJSONObject("stats") ?: JSONObject()
    Box(Modifier.padding(top = 4.dp)) {
        ProKpiTiles(
            listOf(
                ProKpi(s.optInt("bookings").toString(), "Bookings", KitInk), ProKpi(shortRupees(s.optDouble("sale_value", 0.0)), "Sale value", KitGood),
                ProKpi(shortRupees(s.optDouble("collected", 0.0)), "Collected", InvBlue), ProKpi(shortRupees(s.optDouble("outstanding", 0.0)), "Outstanding", InvAmber),
                ProKpi(s.optInt("customers").toString(), "Buyers here", KitInk), ProKpi(s.optInt("all_customers").toString(), "All customers", KitMuted),
                ProKpi(s.optInt("visits_upcoming").toString(), "Visits ahead", InvBlue), ProKpi(s.optInt("visits_pending").toString(), "Visits requested", InvAmber),
                ProKpi(s.optInt("issues_open").toString(), "Open issues", KitDanger), ProKpi(s.optInt("loans").toString(), "Loans", KitGood),
                ProKpi(shortRupees(s.optDouble("loan_balance", 0.0)), "Loan balance", InvPurple), ProKpi(s.optInt("issues_urgent").toString(), "Urgent issues", KitDanger),
            ),
            columns = 3,
        )
    }
    val sale = s.optDouble("sale_value", 0.0)
    if (sale > 0) Card("Collections", "Received against the sale value") {
        val pct = (s.optDouble("collected", 0.0) / sale).coerceIn(0.0, 1.0)
        ProBar(pct.toFloat(), KitGood)
        Text("${(pct * 100).toInt()}% collected · ${rupees(s.optDouble("outstanding", 0.0))} still to come", color = KitMuted, fontSize = 12.sp)
    }
    val palette = listOf(InvAmber, KitGood, InvBlue, InvPurple, KitDanger, Color(0xFF1B8A8F))
    ProDonutCard("Bookings by status", null, o.objs("by_status").map { Triple(pretty(it.optString("name")), it.optInt("count"), bookingTint(it.optString("name"))) }, empty = "No bookings yet.") { onPage("bookings") }
    ProDonutCard("Bookings by stage", "Token, agreement, registration …", o.objs("by_stage").mapIndexed { i, st -> Triple(pretty(st.optString("name")), st.optInt("count"), palette[i % palette.size]) }, empty = "No bookings yet.")

    val recent = o.objs("recent_bookings")
    if (recent.isNotEmpty()) Card("Recent bookings") { recent.forEach { r -> MiniRow(r.optString("customer"), listOfNotNull(r.str("unit"), prettyDay(r.str("date"))).joinToString(" · "), rupees(r.optDouble("value", 0.0)), pretty(r.str("status")) to bookingTint(r.str("status"))) { onOpen("booking", r.optInt("id")) } } }
    val visits = o.objs("upcoming_visits")
    if (visits.isNotEmpty()) Card("Upcoming visits") { visits.forEach { v -> MiniRow(v.str("customer") ?: "Visit", listOfNotNull(v.str("type_label"), whenLabel(v.str("at"))).joinToString(" · "), null, visitStatus(v.str("status"))) { onOpen("visit", v.optInt("id")) } } }
    val issues = o.objs("open_issues")
    if (issues.isNotEmpty()) Card("Open issues") { issues.forEach { i -> MiniRow(i.optString("title"), listOfNotNull(i.str("customer"), i.str("priority")?.let { pretty(it) }).joinToString(" · "), null, issueStatus(i.str("status"))) { onOpen("issue", i.optInt("id")) } } }
}

// ───────────────────────────── bookings, payments ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingList(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    var meta by remember { mutableStateOf<JSONObject?>(null) }
    fun names(k: String) = meta?.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    FilteredList(
        api, b, "/projects/$uuid/customer-center/bookings", version, "Search buyer, phone, flat or booking ref…",
        groups = listOfNotNull(
            names("statuses").takeIf { it.isNotEmpty() }?.let { s -> FilterGroup("status", "Status", s.map { FilterOption(it, pretty(it), color = bookingTint(it)) }) },
            names("stages").takeIf { it.isNotEmpty() }?.let { s -> FilterGroup("stage", "Stage", s.map { FilterOption(it, pretty(it)) }) },
            dateGroup(),
        ),
        sorts = NewestOldest + ("value" to "Highest value first"), empty = "No bookings here.", noun = "booking", onMeta = { meta = it },
    ) { r ->
        RowCard({ onOpen(r.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.example.gavanhomes.ui.common.Initials(r.optString("customer").ifBlank { "?" }, size = 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(r.optString("customer"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(r.str("unit"), r.str("unit_type")).joinToString(" · ").ifEmpty { r.optString("ref") }, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(shortRupees(r.optDouble("value", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(pretty(r.str("status")), bookingTint(r.str("status")))
                r.str("stage")?.let { Chip(pretty(it), InvBlue) }
                Chip(r.optString("ref"), KitMuted)
                prettyDay(r.str("date"))?.let { Chip(it, KitMuted) }
            }
            val value = r.optDouble("value", 0.0)
            if (value > 0 && !r.isNull("paid")) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ProBar((r.optDouble("paid", 0.0) / value).toFloat(), KitGood) }
                Text("${shortRupees(r.optDouble("paid", 0.0))} paid", color = KitMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
            }
            r.str("phone")?.let { com.example.gavanhomes.ui.tasks.PhoneActions(it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentList(api: ApiClient, b: Branding, uuid: String, version: Int, onOpenBooking: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/customer-center/payments", version, "Search buyer, method or notes…",
        groups = listOf(
            FilterGroup("status", "Status", listOf("paid", "pending", "failed").map { FilterOption(it, pretty(it), color = paymentTint(it)) }),
            dateGroup(),
        ),
        sorts = NewestOldest + ("amount" to "Largest first"), empty = "No payments here.", noun = "payment",
    ) { p ->
        RowCard(if (p.optInt("booking_id") > 0) ({ onOpenBooking(p.optInt("booking_id")) }) else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(paymentTint(p.str("status")).copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("₹", color = paymentTint(p.str("status")), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(p.str("customer") ?: "Payment", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(prettyDay(p.str("date")), p.str("method"), p.str("cycle")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(rupees(p.optDouble("amount", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Chip(pretty(p.str("status")), paymentTint(p.str("status")))
                }
            }
            p.str("notes")?.let { Text(it, color = KitMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

// ───────────────────────────── customers ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomerList(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/customer-center/customers", version, "Search name, phone, email, code or city…",
        groups = listOf(
            FilterGroup("scope", "Show", listOf(FilterOption("here", "Booked on this project", color = KitGood))),
            FilterGroup("gender", "Gender", listOf("male", "female", "other").map { FilterOption(it, pretty(it)) }),
        ),
        sorts = NewestOldest + ("name" to "Name A–Z"), empty = "No customers here.", noun = "customer",
    ) { c ->
        RowCard({ onOpen(c.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.example.gavanhomes.ui.common.Initials(c.optString("name"), size = 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(c.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(c.str("code"), c.str("phone"), c.str("city")).joinToString(" · ").ifEmpty { c.str("email") ?: "" }, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (c.optInt("bookings") > 0) Chip("${c.optInt("bookings")} booking${if (c.optInt("bookings") == 1) "" else "s"}", KitGood)
            }
            c.str("phone")?.let { com.example.gavanhomes.ui.tasks.PhoneActions(it) }
        }
    }
}

// ───────────────────────────── visits ─────────────────────────────

private val VisitTypes = listOf("site_visit" to "Site visit", "office_meeting" to "Office meeting", "document_submission" to "Document submission", "query_complaint" to "Query / complaint",
    "progress_check" to "Progress check", "payment" to "Payment discussion", "site_inspection" to "Site inspection", "feedback" to "Feedback visit", "booking" to "Booking visit")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisitList(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/customer-center/visits", version, "Search customer, purpose or staff…",
        groups = listOf(
            FilterGroup("when", "When", listOf(FilterOption("upcoming", "Upcoming"), FilterOption("past", "Past"))),
            FilterGroup("status", "Status", listOf("pending", "approved", "completed", "rescheduled", "cancelled", "rejected").map { k -> visitStatus(k).let { (l, c) -> FilterOption(k, l, color = c) } }),
            FilterGroup("type", "Type", VisitTypes.map { FilterOption(it.first, it.second) }),
            dateGroup(),
        ),
        sorts = NewestOldest + ("soonest" to "Soonest first"), single = setOf("when"), empty = "No visits here.", noun = "visit",
    ) { v ->
        val (l, c) = visitStatus(v.str("status"))
        RowCard({ onOpen(v.optInt("id")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("⌂", color = c, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(v.str("customer") ?: "Visit", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(v.str("type_label"), whenLabel(v.str("at"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Chip(l, c)
            }
            v.str("purpose")?.let { Text(it, color = KitInk, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                v.str("staff")?.let { Chip("👤 $it", KitMuted) }
                v.str("follow_up")?.let { Chip("Follow-up " + (prettyDay(it) ?: it), InvAmber) }
            }
        }
    }
}

// ───────────────────────────── issues ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, onOpen: (Int) -> Unit) {
    var counts by remember { mutableStateOf<JSONObject?>(null) }
    FilteredList(
        api, b, "/projects/$uuid/customer-center/issues", version, "Search title, description or customer…",
        groups = listOfNotNull(
            FilterGroup("status", "Status", listOf("open", "assigned", "in_progress", "resolved", "closed", "reopened").map { k -> issueStatus(k).let { (l, c) -> FilterOption(k, l, counts?.optInt(k), c) } }),
            FilterGroup("priority", "Priority", listOf("urgent", "high", "medium", "low").map { FilterOption(it, pretty(it), color = priorityTint(it)) }),
            options?.objs("categories")?.takeIf { it.isNotEmpty() }?.let { c -> FilterGroup("category_id", "Category", c.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
            peopleGroup("assigned_to", "Assigned to", options),
            dateGroup(),
        ),
        sorts = NewestOldest + ("priority" to "Most urgent first"), empty = "No issues here.", noun = "issue", onMeta = { counts = it.optJSONObject("counts") },
    ) { i -> IssueCard(i) { onOpen(i.optInt("id")) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IssueCard(i: JSONObject, onClick: () -> Unit) {
    val (l, c) = issueStatus(i.str("status"))
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(priorityTint(i.str("priority")).copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("!", color = priorityTint(i.str("priority")), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(i.optString("title"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(i.str("customer"), whenLabel(i.str("created_at"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(l, c)
            i.str("priority")?.let { Chip(pretty(it), priorityTint(it)) }
            i.str("category")?.let { Chip(it, KitMuted) }
            i.str("assignee")?.let { Chip("👤 $it", KitMuted) }
        }
    }
}

// ───────────────────────────── loans ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoanList(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    FilteredList(
        api, b, "/projects/$uuid/customer-center/loans", version, "Search customer, bank or account no.…",
        groups = listOf(FilterGroup("status", "Status", listOf("active", "pending", "approved", "closed", "defaulted").map { FilterOption(it, pretty(it), color = loanTint(it)) }), dateGroup()),
        sorts = NewestOldest + ("balance" to "Highest balance first"), empty = "No loans here.", noun = "loan",
    ) { l -> LoanCard(l) { onOpen(l.optInt("id")) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoanCard(l: JSONObject, onClick: () -> Unit) {
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(loanTint(l.str("status")).copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("₹", color = loanTint(l.str("status")), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(l.str("customer") ?: "Loan", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(l.str("bank"), l.str("type")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(rupees(l.optDouble("principal", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Balance ${shortRupees(l.optDouble("balance", 0.0))}", color = KitMuted, fontSize = 11.sp)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(pretty(l.str("status")), loanTint(l.str("status")))
            if (!l.isNull("rate")) Chip("${l.optDouble("rate", 0.0)}% interest", KitMuted)
            l.str("due")?.let { Chip("Due " + (prettyDay(it) ?: it), KitMuted) }
        }
    }
}
