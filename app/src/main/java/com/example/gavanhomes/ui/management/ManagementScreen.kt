package com.example.gavanhomes.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.professionals.ProAmber
import com.example.gavanhomes.ui.professionals.ProCard
import com.example.gavanhomes.ui.professionals.ProTile
import com.example.gavanhomes.ui.professionals.objects
import com.example.gavanhomes.ui.projects.form.Input
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private fun inr(v: Double) = "₹" + "%,.0f".format(v)
private fun cr(v: Double): String = when {
    v >= 1e7 -> "₹" + "%.2f".format(v / 1e7) + " Cr"
    v >= 1e5 -> "₹" + "%.2f".format(v / 1e5) + " L"
    else -> inr(v)
}

/**
 * The website's Management area, native: the MD overview (money, collections, approvals, stock, cost, targets, today),
 * the approval matrix (who approves up to which amount — add / remove rules) and the company / LLP master.
 * Data: /management/md-overview, /management/approval-matrix, /management/companies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementScreen(api: ApiClient, b: Branding, isDirector: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tabs = buildList { if (isDirector) { add("MD overview"); add("Approval matrix") }; add("Companies"); add("Agreements"); add("Documents"); add("Partner update") }
    var tab by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    var md by remember { mutableStateOf<JSONObject?>(null) }
    var matrix by remember { mutableStateOf<JSONObject?>(null) }
    var companies by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var ruleSheet by remember { mutableStateOf(false) }
    var companySheet by remember { mutableStateOf<JSONObject?>(null) }   // the company being edited, or an empty object for a new one
    val f = remember { mutableMapOfFields() }

    val current = tabs[tab]
    LaunchedEffect(current, attempt) {
        error = null
        try {
            withContext(Dispatchers.IO) {
                when (current) {
                    "MD overview" -> md = api.detail("/management/md-overview")
                    "Approval matrix" -> matrix = api.detail("/management/approval-matrix")
                    "Companies" -> companies = api.detail("/management/companies")
                    else -> {}
                }
            }
        } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    fun post(path: String, body: JSONObject, done: () -> Unit) = scope.launch {
        try { val r = withContext(Dispatchers.IO) { api.send("POST", path, body) }; AppToast.show(r.optString("message", "Saved")); done(); attempt++ }
        catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
    }
    fun remove(path: String) = scope.launch {
        try { val r = withContext(Dispatchers.IO) { api.delete(path) }; AppToast.show(r.optString("message", "Removed")); attempt++ }
        catch (e: Exception) { AppToast.show(e.message ?: "Could not remove") }
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, "Management", onBack)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { i, l ->
                Text(l, color = if (tab == i) Color.White else b.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (tab == i) b.base else b.base.copy(alpha = .1f)).clickable { tab = i }.padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (error != null) Text(error!!, color = KitDanger, modifier = Modifier.padding(20.dp))
            when (current) {
                "Agreements" -> AgreementsTab(api, b)
                "Documents" -> DocumentsTab(api, b)
                "Partner update" -> PartnerUpdateTab(api, b)
                "MD overview" -> md?.let { MdOverview(it, b) } ?: Text("Loading…", color = KitMuted, modifier = Modifier.padding(20.dp))
                "Approval matrix" -> {
                    val m = matrix
                    if (m == null) Text("Loading…", color = KitMuted, modifier = Modifier.padding(20.dp))
                    else {
                        Text("+ Add rule", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(b.base)
                            .clickable { f.clear(); f["module"] = m.objects("modules").firstOrNull()?.optString("key").orEmpty(); ruleSheet = true }.padding(horizontal = 16.dp, vertical = 10.dp))
                        val rules = m.objects("rules")
                        m.objects("modules").forEach { mod ->
                            val mine = rules.filter { it.optString("module") == mod.optString("key") }
                            ProCard(mod.optString("label"), "${mine.size} rule(s)") {
                                if (mine.isEmpty()) Text("No rule — only the Director approves.", color = KitMuted, fontSize = 13.sp)
                                mine.forEach { r ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(if (r.isNull("up_to_amount")) "Any amount above the earlier rules" else "Up to " + inr(r.optDouble("up_to_amount")), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp)
                                            Text(listOfNotNull(r.str("label"), "Approver: " + (r.str("permission") ?: "Director only")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                                        }
                                        Text("Remove", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable { remove("/management/approval-matrix/${r.optInt("id")}") }.padding(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val c = companies
                    if (c == null) Text("Loading…", color = KitMuted, modifier = Modifier.padding(20.dp))
                    else {
                        Text("+ Add company", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(b.base)
                            .clickable { f.clear(); f["type"] = "company"; companySheet = JSONObject() }.padding(horizontal = 16.dp, vertical = 10.dp))
                        val list = c.objects("companies")
                        if (list.isEmpty()) ProCard("No company yet") { Text("Add the company or LLP that owns your projects.", color = KitMuted, fontSize = 13.sp) }
                        list.forEach { co ->
                            ProCard(co.optString("name"), listOfNotNull(co.str("type_label"), co.str("parent")?.let { "Under $it" + (co.str("share_in_parent")?.let { s -> " ($s%)" } ?: "") }).joinToString(" · ")) {
                                listOf("PAN" to co.str("pan"), "GSTIN" to co.str("gstin"), "CIN / LLPIN" to co.str("cin"), "Address" to co.str("address")).forEach { (k, v) ->
                                    if (v != null) Row { Text(k, color = KitMuted, fontSize = 12.sp, modifier = Modifier.widthIn(min = 92.dp)); Text(v, color = KitInk, fontSize = 13.sp) }
                                }
                                Text("${co.optInt("projects")} project(s)", color = KitMuted, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("Edit", color = b.base, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                        f.clear(); listOf("id", "name", "type", "pan", "gstin", "cin", "address", "parent_id", "share_in_parent").forEach { k -> co.str(k)?.let { v -> f[k] = v } }; companySheet = co
                                    })
                                    if (co.optInt("projects") == 0) Text("Delete", color = KitDanger, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { remove("/management/companies/${co.optInt("id")}") })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (ruleSheet) ModalBottomSheet(onDismissRequest = { ruleSheet = false }, containerColor = KitCard) {
        Column(Modifier.padding(20.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("New approval rule", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            val mods = matrix?.objects("modules").orEmpty()
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mods.forEach { m -> val on = f["module"] == m.optString("key"); Text(m.optString("label"), color = if (on) Color.White else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) b.base else b.base.copy(alpha = .1f)).clickable { f["module"] = m.optString("key") }.padding(horizontal = 12.dp, vertical = 8.dp)) }
            }
            Input("Up to amount (₹) — blank = any amount", f["up_to_amount"].orEmpty(), { f["up_to_amount"] = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal)
            Text("Who approves (blank = Director only)", color = KitMuted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("") + matrix?.optJSONArray("permissions").let { a -> a?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: emptyList() }).forEach { p ->
                    val on = (f["permission"] ?: "") == p
                    Text(if (p.isEmpty()) "Director only" else p, color = if (on) Color.White else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) b.base else b.base.copy(alpha = .1f)).clickable { f["permission"] = p }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            Input("Label (e.g. Up to ₹1 L — Manager)", f["label"].orEmpty(), { f["label"] = it })
            Text("Save rule", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base).clickable {
                val body = JSONObject().put("module", f["module"].orEmpty())
                f["up_to_amount"]?.takeIf { it.isNotBlank() }?.let { body.put("up_to_amount", it) }
                f["permission"]?.takeIf { it.isNotBlank() }?.let { body.put("permission", it) }
                f["label"]?.takeIf { it.isNotBlank() }?.let { body.put("label", it) }
                post("/management/approval-matrix", body) { ruleSheet = false }
            }.padding(14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
    }

    companySheet?.let { editing ->
        ModalBottomSheet(onDismissRequest = { companySheet = null }, containerColor = KitCard) {
            Column(Modifier.padding(20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (editing.has("id")) "Edit company" else "New company", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Input("Name", f["name"].orEmpty(), { f["name"] = it })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    companies?.objects("types").orEmpty().forEach { t -> val on = f["type"] == t.optString("key"); Text(t.optString("label"), color = if (on) Color.White else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) b.base else b.base.copy(alpha = .1f)).clickable { f["type"] = t.optString("key") }.padding(horizontal = 12.dp, vertical = 8.dp)) }
                }
                Input("PAN", f["pan"].orEmpty(), { f["pan"] = it.uppercase().take(10) })
                Input("GSTIN", f["gstin"].orEmpty(), { f["gstin"] = it.uppercase().take(15) })
                Input("CIN / LLPIN", f["cin"].orEmpty(), { f["cin"] = it })
                Input("Address", f["address"].orEmpty(), { f["address"] = it }, singleLine = false)
                Text("Parent entity (for an LLP of LLPs)", color = KitMuted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf<JSONObject?>(null) + companies?.objects("companies").orEmpty().filter { it.optInt("id") != editing.optInt("id", -1) }).forEach { p ->
                        val on = (f["parent_id"] ?: "") == (p?.optInt("id")?.toString() ?: "")
                        Text(p?.optString("name") ?: "None", color = if (on) Color.White else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) b.base else b.base.copy(alpha = .1f)).clickable { f["parent_id"] = p?.optInt("id")?.toString() ?: "" }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                if (!f["parent_id"].isNullOrBlank()) Input("Share in parent (%)", f["share_in_parent"].orEmpty(), { f["share_in_parent"] = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal)
                Text("Save company", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base).clickable {
                    val body = JSONObject()
                    f.forEach { (k, v) -> if (v.isNotBlank()) body.put(k, v) }
                    post("/management/companies", body) { companySheet = null }
                }.padding(14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun mutableMapOfFields(): androidx.compose.runtime.snapshots.SnapshotStateMap<String, String> = androidx.compose.runtime.mutableStateMapOf()

@Composable
private fun MdOverview(d: JSONObject, b: Branding) {
    val shared = d.optJSONArray("shared_logins")
    if (shared != null && shared.length() > 0) ProCard("Shared sign-ins still active", "Retire these before go-live") {
        for (i in 0 until shared.length()) Text(shared.getString(i), color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
    val sales = d.objects("sales")
    ProCard("Money in the bank", "Real balances, this week's movement") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProTile(cr(d.optDouble("cash")), "Cash in hand", Modifier.weight(1f), b.base)
            ProTile(cr(d.optDouble("week_in")), "In (7 days)", Modifier.weight(1f), KitGood)
        }
        ProTile(cr(d.optDouble("week_out")), "Out (7 days)", Modifier.fillMaxWidth(), KitDanger)
        d.objects("banks").forEach { Row { Text(it.optString("name"), color = KitInk, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(cr(it.optDouble("balance")), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 13.sp) } }
    }
    ProCard("Sales and collection", "Booked, collected, due and overdue by project") {
        if (sales.isEmpty()) Text("No bookings yet.", color = KitMuted, fontSize = 13.sp)
        sales.forEach { s ->
            Text(s.optString("project"), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 14.sp)
            Text("Booked ${cr(s.optDouble("booked"))} · collected ${cr(s.optDouble("collected"))} · due now ${cr(s.optDouble("due_now"))}", color = KitMuted, fontSize = 12.sp)
            if (s.optDouble("overdue") > 0) Text("Overdue ${cr(s.optDouble("overdue"))} (${s.optInt("overdue_count")} instalments)", color = KitDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
    val coll = d.objects("collections")
    ProCard("Collections to chase", "Overdue or due in 7 days") {
        if (coll.isEmpty()) Text("Nothing to chase.", color = KitMuted, fontSize = 13.sp)
        coll.take(25).forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.optString("customer"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(listOfNotNull(c.str("project"), c.str("stage"), c.str("due"), c.str("owner")).joinToString(" · "), color = KitMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) { Text(inr(c.optDouble("amount")), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 13.sp); if (c.optBoolean("overdue")) Pill("Overdue", KitDanger.copy(alpha = .14f), KitDanger) }
            }
        }
    }
    val appr = d.objects("approvals")
    ProCard("Waiting for approval", "${appr.size} item(s), oldest first") {
        if (appr.isEmpty()) Text("Nothing is waiting.", color = KitMuted, fontSize = 13.sp)
        appr.take(25).forEach { a -> Row { Column(Modifier.weight(1f)) { Text("${a.optString("kind")} · ${a.optString("label")}", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text("waiting ${a.optString("age")}", color = if (a.optInt("days") > 2) KitDanger else KitMuted, fontSize = 11.sp) }
            if (!a.isNull("amount")) Text(inr(a.optDouble("amount")), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 13.sp) } }
    }
    ProCard("Stock by size", "Plots and land by area band") {
        d.objects("inventory").forEach { i ->
            Text(i.optString("band"), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 13.sp)
            Text("${i.optInt("total")} units · ${i.optInt("available")} free · ${i.optInt("on_hold")} held · ${i.optInt("booked")} booked · ${i.optInt("sold")} sold · unsold ${cr(i.optDouble("unsold_value"))}", color = KitMuted, fontSize = 11.sp)
        }
    }
    val cost = d.objects("cost")
    if (cost.isNotEmpty()) ProCard("Cost against budget") {
        cost.forEach { c -> Row { Text(c.optString("project"), color = KitInk, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("${cr(c.optDouble("actual"))} of ${cr(c.optDouble("budget"))}", fontWeight = FontWeight.Bold, color = if (c.optDouble("budget") > 0 && c.optDouble("actual") > c.optDouble("budget")) KitDanger else KitInk, fontSize = 13.sp) } }
    }
    val t = d.optJSONObject("targets")
    ProCard("Team targets", "${t?.optInt("count") ?: 0} live") {
        Text(if (t != null && !t.isNull("avg")) "Average progress ${t.optInt("avg")}%" else "No live targets.", color = KitInk, fontSize = 13.sp)
        t?.objects("behind").orEmpty().forEach { Text("Behind: ${it.optString("name")} (${it.optString("project")}) ${it.optInt("pct")}%", color = ProAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
    val a = d.optJSONObject("activity")
    ProCard("Today", "${d.optInt("overdue_tasks")} overdue task(s)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProTile("${a?.optInt("leads") ?: 0}", "Leads", Modifier.weight(1f)); ProTile("${a?.optInt("visits") ?: 0}", "Visits", Modifier.weight(1f)); ProTile("${a?.optInt("bookings") ?: 0}", "Bookings", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProTile("${a?.optInt("receipts") ?: 0}", "Receipts", Modifier.weight(1f)); ProTile("${a?.optInt("grns") ?: 0}", "GRNs", Modifier.weight(1f))
        }
    }
}
