package com.example.gavanhomes.ui.customers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.gavanhomes.data.ApiConfig
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.KitSoft
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.Waiting
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.readPicked
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private fun initialsOf(name: String) = name.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }
private fun lakh(v: Double): String = when {
    v >= 10_000_000 -> String.format(java.util.Locale.US, "₹%.2f Cr", v / 10_000_000)
    v >= 100_000 -> String.format(java.util.Locale.US, "₹%.2f L", v / 100_000)
    else -> "₹" + String.format(java.util.Locale.US, "%,.0f", v)
}

// ───────────────────────────── the customer list: one card per customer, by journey step ─────────────────────────────

/**
 * The customers of the project as journey cards: unit, "Step n/11 · name", a progress bar, the next action and the amount due. Filter chips by step with
 * counts (on the main Customers page); sorted by what needs doing first. [steps] limits the list to those steps (the Agreements & legal, Dastavej … pages).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyCustomerList(api: ApiClient, b: Branding, uuid: String, version: Int, steps: Set<Int>? = null, chips: Boolean = true, onOpen: (Int) -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(0) }       // 0 = all
    LaunchedEffect(uuid, version, attempt) {
        error = null
        try { data = withContext(Dispatchers.IO) { api.get("/projects/$uuid/customer-center/journey/customers") } } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    val d = data
    if (d == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base) else com.example.gavanhomes.ui.common.EstateError(error, color = b.base, modifier = Modifier.padding(24.dp), onRetry = { attempt++ })
        }
        return
    }
    val all = d.objs("data").filter { steps == null || it.optInt("step") in steps }
    val counts = all.groupBy { it.optInt("step") }.mapValues { it.value.size }
    val q = query.trim().lowercase()
    val rows = all.filter { (step == 0 || it.optInt("step") == step) && (q.isEmpty() || (it.optString("name") + " " + it.optString("phone") + " " + it.optString("unit")).lowercase().contains(q)) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.example.gavanhomes.ui.common.SearchBox(query, { query = it }, "Search name, phone, unit…")
        if (chips) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepChip("All ${all.size}", step == 0, b.base) { step = 0 }
            d.objs("steps").filter { (counts[it.optInt("n")] ?: 0) > 0 }.forEach { s -> StepChip("${s.optString("name")} ${counts[s.optInt("n")]}", step == s.optInt("n"), b.base) { step = s.optInt("n") } }
        }
        Text("${rows.size} customer${if (rows.size == 1) "" else "s"} · sorted by next action", color = KitMuted, fontSize = 12.sp)
        if (rows.isEmpty()) Text("No customers here.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
        rows.forEach { r ->
            val overdue = r.optBoolean("overdue")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitCard).clickable { onOpen(r.optInt("booking_id")) }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(b.base.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text(initialsOf(r.optString("name")), color = b.base, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(r.optString("name"), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(r.str("unit"), r.str("phone")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    r.str("phone")?.let { com.example.gavanhomes.ui.tasks.CallChip(it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (r.optBoolean("complete")) "Journey complete" else "Step ${r.optInt("step")}/11 · ${r.optString("step_name")}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = b.base,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(b.base.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 4.dp))
                    if (r.optInt("stuck_days") > 15 && !r.optBoolean("complete")) Text("${r.optInt("stuck_days")} days here", fontSize = 11.sp, color = InvAmber, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
                ProBar(r.optInt("progress") / 100f, KitGood)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Next: " + r.optString("next"), color = KitInk, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (r.optDouble("due_amount", 0.0) > 0) Text(lakh(r.optDouble("due_amount")) + (r.str("due_date")?.let { " due " + (prettyDay(it) ?: it) } ?: ""), color = if (overdue) KitDanger else InvAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    else if (r.optBoolean("paid_all")) Text("All paid", color = KitGood, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StepChip(text: String, on: Boolean, tint: Color, onClick: () -> Unit) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) tint else KitSoft).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp))
}

// ───────────────────────────── the module's dashboard ─────────────────────────────

/** Due this week, overdue, received this month, customers in each journey step, and the ones stuck more than 15 days. [onOpen] opens a customer. */
@Composable
fun JourneyDashboard(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit, onTile: (String) -> Unit = {}) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(uuid, version) { d = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/customer-center/journey/dashboard") } }.getOrNull() }
    val x = d ?: return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitCard).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).clickable { onTile("collections") }) { Text(lakh(x.optDouble("due_week")), color = InvAmber, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Text("Due this week", color = KitMuted, fontSize = 11.sp) }
            Column(Modifier.weight(1f).clickable { onTile("collections") }) { Text(lakh(x.optDouble("overdue")), color = KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Text("Overdue", color = KitMuted, fontSize = 11.sp) }
            Column(Modifier.weight(1f).clickable { onTile("payments") }) { Text(lakh(x.optDouble("received_month")), color = KitGood, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Text("Received this month", color = KitMuted, fontSize = 11.sp) }
        }
        val steps = x.objs("steps")
        val max = (steps.maxOfOrNull { it.optInt("count") } ?: 1).coerceAtLeast(1)
        Card("Customers by journey step", "${x.optInt("total")} customers in total") {
            steps.filter { it.optInt("n") >= 2 }.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.optString("name"), fontSize = 12.sp, color = KitInk, modifier = Modifier.width(96.dp), maxLines = 1)
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(KitSoft)) { Box(Modifier.fillMaxWidth(s.optInt("count") / max.toFloat()).height(10.dp).clip(RoundedCornerShape(5.dp)).background(b.base)) }
                    Text(s.optInt("count").toString(), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = KitInk, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
                }
            }
        }
        val stuck = x.objs("stuck")
        if (stuck.isNotEmpty()) Card("Stuck more than 15 days") {
            stuck.forEach { s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(s.optInt("booking_id")) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.optString("name") + " · " + s.optString("step_name"), color = KitInk, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.optInt("days")} days", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// ───────────────────────────── Customer 360 ─────────────────────────────

private val Tabs = listOf("Overview", "Payments", "Documents", "Loan", "Visits", "Issues")

/**
 * One page per customer, moving step by step from lead to possession. Totals (cost / received / balance), the 11-step journey with its dates, the
 * current step highlighted with its next action and the buttons that move it on — Mark done (with a reason when it would complete on its own),
 * Upload document, Skip (where it makes sense), the legal checklist — and tabs for Overview, Payments, Documents, Loan, Visits and Issues, so the
 * customer is never chosen again. [onOpenBooking] opens the booking (record a payment, edit the form).
 * Data: GET /customer-center/journey/{booking} · POST …/steps/{n}/done · skip · reopen · data · upload.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomerJourneyScreen(api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onChanged: () -> Unit, onOpenBooking: (Int) -> Unit, onOpenLead: ((Int) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var ask by remember { mutableStateOf<Pair<String, Int>?>(null) }          // "done" | "skip" | "reopen" to the step number
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var uploadFor by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, version, attempt) { error = null; try { d = withContext(Dispatchers.IO) { api.detail("/customer-center/journey/$id") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = d ?: return Waiting(b, "Customer", error, onBack) { attempt++ }

    fun reload() { attempt++; onChanged() }
    fun post(path: String, body: JSONObject, ok: String) {
        busy = true
        scope.launch {
            try { val r = withContext(Dispatchers.IO) { api.send("POST", path, body) }; AppToast.show(r.optString("message", ok)); ask = null; reason = ""; reload() }
            catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
            busy = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val n = uploadFor
        if (uri != null && n > 0) {
            val file = readPicked(context, uri)
            if (file == null) AppToast.show("Could not read that file") else scope.launch {
                try { val r = withContext(Dispatchers.IO) { api.sendForm("/customer-center/journey/$id/steps/$n/upload", listOf(FormPart("file", fileName = file.name, mime = file.mime, bytes = file.bytes))) }; AppToast.show(r.optString("message", "Uploaded")); reload() }
                catch (e: Exception) { AppToast.show(e.message ?: "Could not upload") }
            }
        }
    }

    val steps = x.objs("steps")
    val current = x.optInt("current")
    val total = x.optDouble("total"); val received = x.optDouble("received"); val balance = (total - received).coerceAtLeast(0.0)
    val canEdit = x.optJSONObject("can")?.optBoolean("edit") == true
    fun statusColor(s: String) = when (s) { "done" -> KitGood; "skipped" -> KitMuted; else -> InvAmber }
    val viewFile = { path: String -> runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("${ApiConfig.BASE_URL}/storage/$path"))) }.onFailure { AppToast.show("No app can open this file") } }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            HeroBar(b, "Customer", onBack) {
                Column {
                    Text(x.optString("name"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("code"), x.str("project"), x.str("unit")).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(if (x.optBoolean("complete")) "Journey complete" else "Step $current/11", steps.getOrNull(current - 1)?.optString("name")).toTypedArray())
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // money
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) { Text(lakh(total), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp); Text("Total cost", color = KitMuted, fontSize = 11.sp) }
                        Column(Modifier.weight(1f)) { Text(lakh(received), fontWeight = FontWeight.ExtraBold, color = KitGood, fontSize = 16.sp); Text("Received", color = KitMuted, fontSize = 11.sp) }
                        Column(Modifier.weight(1f)) { Text(lakh(balance), fontWeight = FontWeight.ExtraBold, color = InvAmber, fontSize = 16.sp); Text("Balance", color = KitMuted, fontSize = 11.sp) }
                    }
                    ProBar(if (total > 0) (received / total).toFloat().coerceIn(0f, 1f) else 0f, KitGood)
                }

                // the journey
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Customer journey", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                    steps.forEach { s ->
                        val n = s.optInt("n"); val st = s.optString("status"); val isCur = n == current && !x.optBoolean("complete")
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(if (st == "done") KitGood else if (st == "skipped") KitMuted else Color.Transparent).border(2.dp, if (isCur) InvAmber else if (st == "pending") KitMuted.copy(alpha = .4f) else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (st != "pending") Text(if (st == "done") "✓" else "–", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) else if (isCur) Box(Modifier.size(8.dp).clip(CircleShape).background(InvAmber)) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Row {
                                    Text("$n. ${s.optString("name")}", fontWeight = if (isCur) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (st == "pending" && !isCur) KitMuted else KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Text(if (st == "skipped") "Skipped" else (s.str("date")?.let { prettyDay(it) ?: it } ?: ""), color = KitMuted, fontSize = 11.sp)
                                }
                                val info = s.str("info"); if (info != null && (st != "pending" || isCur)) Text(info, color = KitMuted, fontSize = 12.sp)
                                if (st != "pending" && s.str("reason") != null) Text("Reason: " + s.optString("reason"), color = KitMuted, fontSize = 11.sp)
                                if (st == "done" && s.str("file") != null) Text("View document  ›", color = b.base, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewFile(s.optString("file")) }.padding(vertical = 3.dp))
                                val needsDoc = (s.optJSONArray("docs")?.length() ?: 0) > 0 && n in listOf(5, 7, 9, 10, 11)
                                if (canEdit && st == "done" && needsDoc && s.str("file") == null) Text("⚠ Document pending — upload it now  ›", color = InvAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { uploadFor = n; picker.launch(arrayOf("application/pdf", "image/*")) }.padding(vertical = 3.dp))
                                if (canEdit && st == "done" && s.str("file") != null && needsDoc) Text("Replace document", color = KitMuted, fontSize = 11.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { uploadFor = n; picker.launch(arrayOf("application/pdf", "image/*")) }.padding(vertical = 3.dp))
                                if (canEdit && st != "pending" && s.optString("how") == "manual") Text("Reopen", color = KitMuted, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { ask = "reopen" to n }.padding(vertical = 3.dp))

                                if (isCur) {
                                    Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InvAmber.copy(alpha = .12f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Next action: " + (s.str("next") ?: "Mark ${s.optString("name")} done"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        // step-specific fields
                                        if (n == 6) {
                                            val items = x.optJSONObject("legal_items")
                                            val saved = s.optJSONObject("data")?.optJSONObject("items")
                                            items?.keys()?.forEach { k ->
                                                var on by remember(saved?.optInt(k)) { mutableStateOf(saved?.optInt(k, 0) == 1 || saved?.optBoolean(k, false) == true) }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(items.optString(k), color = KitInk, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                    Switch(checked = on, enabled = canEdit, onCheckedChange = { v ->
                                                        on = v
                                                        val all = JSONObject(); items.keys().forEach { kk -> all.put(kk, if (kk == k) (if (v) 1 else 0) else (saved?.optInt(kk, 0) ?: 0)) }
                                                        post("/customer-center/journey/$id/steps/6/data", JSONObject().put("items", all), "Saved")
                                                    }, colors = SwitchDefaults.colors(checkedTrackColor = KitGood, checkedThumbColor = Color.White))
                                                }
                                            }
                                        }
                                        if (n == 9) {
                                            var date by remember { mutableStateOf(s.optJSONObject("data")?.optString("registration_date").orEmpty()) }
                                            var office by remember { mutableStateOf(s.optJSONObject("data")?.optString("sub_registrar").orEmpty()) }
                                            Input("Registration date (yyyy-mm-dd)", date, { date = it }); Input("Sub-registrar office", office, { office = it })
                                            if (canEdit) Text("Save details", color = b.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { post("/customer-center/journey/$id/steps/9/data", JSONObject().put("registration_date", date).put("sub_registrar", office), "Saved") }.padding(vertical = 4.dp))
                                        }
                                        if (canEdit) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (s.optJSONArray("docs")?.length() ?: 0 > 0 && n in listOf(5, 7, 9, 10, 11)) StepButton("↑ Upload document", KitInk) { uploadFor = n; picker.launch(arrayOf("application/pdf", "image/*")) }
                                            StepButton("✓ Mark done", KitGood) { ask = "done" to n }
                                            if (n in listOf(3, 6, 7, 8)) StepButton("Skip", KitMuted) { ask = "skip" to n }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // tabs
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Tabs.forEachIndexed { i, t -> StepChip(t, tab == i, b.base) { tab = i } } }
                when (tab) {
                    0 -> {
                        val cost = x.optJSONObject("cost")
                        if (cost != null && cost.optDouble("total") > 0) Card("Cost sheet") {
                            Fact("Base price", rupees(cost.optDouble("bsp"))); Fact("GST", rupees(cost.optDouble("gst"))); Fact("Stamp duty", rupees(cost.optDouble("stamp_duty")))
                            Fact("Registration", rupees(cost.optDouble("registration"))); Fact("Total", rupees(cost.optDouble("total")))
                        }
                        Card("Details") { Fact("Booking", x.str("ref")); Fact("Booked on", prettyDay(x.str("booking_date"))); Fact("Mobile", x.str("phone")); Fact("Email", x.str("email")); Fact("Lead source", x.str("lead_source")) }
                        val hist = x.objs("history")
                        if (hist.isNotEmpty()) Card("History", "Who changed what, and when") { hist.forEach { h -> Text(listOfNotNull(h.str("at"), h.str("user"), h.optString("action") + (h.str("step_name")?.let { " · $it" } ?: ""), h.str("note")).joinToString(" · "), color = KitMuted, fontSize = 12.sp) } }
                    }
                    1 -> {
                        val sched = x.objs("schedule")
                        Card("Instalments", "${sched.count { it.optString("status") == "paid" }} of ${sched.size} paid") {
                            if (sched.isEmpty()) Text("No payment plan yet.", color = KitMuted, fontSize = 13.sp)
                            sched.forEach { s ->
                                val c = when (s.optString("status")) { "paid" -> KitGood; "overdue" -> KitDanger; else -> KitMuted }
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(s.str("label") ?: "Instalment", color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(prettyDay(s.str("due")) ?: "", color = KitMuted, fontSize = 11.sp) }
                                    Text(lakh(s.optDouble("amount")), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                                    Text(s.optString("status").replaceFirstChar { it.uppercase() }, color = c, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = .12f)).padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                        val pays = x.objs("payments")
                        if (pays.isNotEmpty()) Card("Payments received") { pays.forEach { p -> Fact((prettyDay(p.str("date")) ?: "—") + (p.str("method")?.let { " · $it" } ?: ""), rupees(p.optDouble("amount"))) } }
                    }
                    2 -> Card("Documents by step", "What each step needs on file") {
                        steps.filter { (it.optJSONArray("docs")?.length() ?: 0) > 0 }.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${s.optInt("n")}. ${s.optString("name")}", fontWeight = FontWeight.SemiBold, color = KitInk, fontSize = 13.sp)
                                    val docs = s.optJSONArray("docs"); Text((0 until (docs?.length() ?: 0)).joinToString(" · ") { docs!!.getString(it) }, color = KitMuted, fontSize = 11.sp)
                                }
                                if (s.str("file") != null) Text("View", color = b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewFile(s.optString("file")) }.padding(8.dp))
                                else if (s.optString("status") == "done" && canEdit && s.optInt("n") in listOf(5, 7, 9, 10, 11)) Text("Upload", color = InvAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { uploadFor = s.optInt("n"); picker.launch(arrayOf("application/pdf", "image/*")) }.padding(8.dp))
                                else Text(if (s.optString("status") == "done") "Done" else "Missing", color = if (s.optString("status") == "done") KitGood else KitDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    3 -> {
                        val loan = x.optJSONObject("loan")
                        Card("Home loan", if (loan?.optBoolean("required") == true) null else "No loan for this booking") {
                            if (loan?.optBoolean("required") == true) { Fact("Bank", loan.str("bank")); Fact("Applied", rupees(loan.optDouble("applied"))) }
                            val acc = loan?.objs("accounts").orEmpty()
                            acc.forEach { a -> Fact((a.str("bank") ?: "Loan") + " · " + (a.str("status") ?: ""), "disbursed " + rupees(a.optDouble("disbursed")) + " of " + rupees(a.optDouble("principal"))) }
                            acc.firstOrNull()?.str("officer")?.let { Fact("Contact", it + (acc.first().str("officer_phone")?.let { p -> " · $p" } ?: "")) }
                        }
                    }
                    4 -> Card("Visits") { val v = x.objs("visits"); if (v.isEmpty()) Text("No visits yet.", color = KitMuted, fontSize = 13.sp); v.forEach { Fact((it.str("type") ?: "Visit") + " · " + (it.str("at") ?: ""), it.str("status")?.replaceFirstChar { c -> c.uppercase() }) } }
                    else -> Card("Issues") { val v = x.objs("issues"); if (v.isEmpty()) Text("No issues.", color = KitMuted, fontSize = 13.sp); v.forEach { Fact(it.optString("title"), it.str("status")?.replaceFirstChar { c -> c.uppercase() }) } }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Box(Modifier.fillMaxWidth().background(KitCard).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("+ Record payment / open booking", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(b.base).clickable { onOpenBooking(id) }.padding(vertical = 15.dp))
        }
    }

    // reason popup for Mark done / Skip / Reopen
    ask?.let { (kind, n) ->
        val step = steps.getOrNull(n - 1)
        val needs = kind == "skip" || (kind == "done" && n in listOf(2, 3, 4, 8))
        ModalBottomSheet(onDismissRequest = { ask = null; reason = "" }, containerColor = KitPage) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when (kind) { "skip" -> "Skip ${step?.optString("name")}?"; "reopen" -> "Reopen ${step?.optString("name")}?"; else -> "Mark ${step?.optString("name")} done?" }, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text(when (kind) {
                    "skip" -> "Skipping is logged with your reason."
                    "reopen" -> "The step goes back to pending."
                    else -> if (needs) "This step normally completes on its own. Doing it by hand needs a reason, which is logged." else "It is logged with your name and the time."
                }, color = KitMuted, fontSize = 13.sp)
                if (needs || kind == "reopen") Input(if (needs) "Reason *" else "Reason (optional)", reason, { reason = it }, singleLine = false)
                Text(if (busy) "Saving…" else when (kind) { "skip" -> "Skip step"; "reopen" -> "Reopen"; else -> "Mark done" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (needs && reason.isBlank() || busy) Color(0xFFC9C7C2) else KitGood).clickable(enabled = !busy && !(needs && reason.isBlank())) {
                        post("/customer-center/journey/$id/steps/$n/" + (if (kind == "skip") "skip" else if (kind == "reopen") "reopen" else "done"), JSONObject().put("reason", reason.trim()), "Saved")
                    }.padding(vertical = 15.dp))
            }
        }
    }
}

@Composable
private fun StepButton(text: String, tint: Color, onClick: () -> Unit) {
    Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(tint).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp))
}
