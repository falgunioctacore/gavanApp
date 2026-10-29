package com.example.gavanhomes.ui.leads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.PillTab
import com.example.gavanhomes.ui.common.PillTabs
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.professionals.ProDonutCard
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Meter
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.SendButton
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

private val Blue = Color(0xFF1E5FAE)
private val Amber = Color(0xFFC47A1A)

/** Runs a write, toasts the outcome (the server's message when it sends one) and then [after]. */
private fun runAct(scope: kotlinx.coroutines.CoroutineScope, context: android.content.Context, success: String?, call: () -> JSONObject?, after: () -> Unit) {
    scope.launch {
        try {
            val r = withContext(Dispatchers.IO) { call() }
            val msg = r?.optString("message")?.takeIf { it.isNotBlank() } ?: success
            if (msg != null) android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            after()
        } catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "Could not do that", android.widget.Toast.LENGTH_LONG).show() }
    }
}

@Composable
private fun Loading(loading: Boolean, empty: Boolean, error: String?, emptyText: String, b: Branding, retry: () -> Unit) {
    when {
        loading && empty -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
        error != null && empty -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error, color = KitMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = retry) { Text("Retry") }
        }
        empty -> Text(emptyText, color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
    }
}

@Composable
private fun Header(title: String, sub: String, button: String?, accent: Color, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(sub, color = KitMuted, fontSize = 12.sp)
        }
        if (button != null) Text(button, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(accent).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp))
    }
}

@Composable
private fun IconBtns(onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    if (onEdit != null) Text("✎", color = KitInk, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).background(Color(0xFFF1EFEA)).clickable { onEdit() }.padding(horizontal = 11.dp, vertical = 6.dp))
    if (onDelete != null) Text("🗑", fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(KitDanger.copy(alpha = .1f)).clickable { onDelete() }.padding(horizontal = 9.dp, vertical = 6.dp))
}

// ───────────────────────────── lead lists ─────────────────────────────

/** The website's Lead Lists: every list of leads in the project with its numbers; tap one to see its leads. Data: GET …/lead-center/lists · POST …/lists · DELETE /lead-center/lists/{id}. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LeadLists(api: ApiClient, b: Branding, uuid: String, version: Int, onOpenList: (Int) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var canManage by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(version, attempt) {
        loading = true; error = null
        try { val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/lead-center/lists") }; rows = r.objs("data"); canManage = r.optJSONObject("can")?.optBoolean("manage") == true }
        catch (e: Exception) { error = e.message ?: "Could not load the lists" }
        loading = false
    }
    fun done() { attempt++; onChanged() }

    Header("Lead lists", "${rows.size} list${if (rows.size == 1) "" else "s"} of leads", if (canManage) "+  New list" else null, b.base) { editing = JSONObject() }
    rows.forEach { l ->
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(5.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White)
                .clickable { onOpenList(l.optInt("id")) }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (l.optBoolean("pinned")) Chip("★ Pinned", Amber)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Blue.copy(alpha = .08f)).padding(12.dp)) { Text(l.optInt("leads").toString(), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Blue); Text("Leads", color = KitMuted, fontSize = 12.sp) }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(KitGood.copy(alpha = .08f)).padding(12.dp)) { Text(l.optInt("working").toString(), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitGood); Text("Working", color = KitMuted, fontSize = 12.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (l.optBoolean("auto_distribute")) Chip("Auto-distribute on", KitGood)
                Spacer(Modifier.weight(1f))
                if (canManage) {
                    if (l.optBoolean("auto_distribute")) Text("Run now", color = b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(10.dp)).background(b.base.copy(alpha = .1f))
                        .clickable { runAct(scope, context, "Distributed", { api.send("POST", "/lead-center/lists/${l.optInt("id")}/distribute", JSONObject()) }, ::done) }.padding(horizontal = 12.dp, vertical = 7.dp))
                    IconBtns({ editing = l }, { deleting = l })
                }
            }
        }
    }
    Loading(loading, rows.isEmpty(), error, "No lead lists yet.", b) { attempt++ }

    editing?.let { l ->
        var name by remember { mutableStateOf(l.optString("name")) }
        var pinned by remember { mutableStateOf(l.optBoolean("pinned")) }
        var auto by remember { mutableStateOf(l.optBoolean("auto_distribute")) }
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (l.has("id")) "Edit lead list" else "New lead list", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Input("Name", name, { name = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pick(if (pinned) "★ Pinned" else "Pin to top", pinned, Amber) { pinned = !pinned }
                    Pick(if (auto) "Auto-distribute on" else "Auto-distribute off", auto, KitGood) { auto = !auto }
                }
                SendButton(if (l.has("id")) "Save list" else "Create list", name.isNotBlank()) {
                    editing = null
                    runAct(scope, context, "Saved", { api.send("POST", "/projects/$uuid/lead-center/lists", JSONObject().put("id", if (l.has("id")) l.optInt("id") else JSONObject.NULL).put("name", name.trim()).put("pinned", pinned).put("auto_distribute", auto)) }, ::done)
                }
            }
        }
    }
    deleting?.let { l -> SwalConfirm(
        title = "Delete this list?", text = "${l.optString("name")} — a list that still has leads cannot be deleted.", confirmText = "Yes, delete it",
        onConfirm = { deleting = null; runAct(scope, context, "List deleted", { api.delete("/lead-center/lists/${l.optInt("id")}") }, ::done) }, onCancel = { deleting = null },
    ) }
}

// ───────────────────────────── export requests ─────────────────────────────

/** The website's Export Requests: ask an admin for permission to export leads; admins approve or deny. Data: GET …/lead-center/export-requests · POST … · POST /lead-center/export-requests/{id}/approve | deny. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ExportRequests(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var counts by remember { mutableStateOf<JSONObject?>(null) }
    var canReview by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("all") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var request by remember { mutableStateOf(false) }
    var decide by remember { mutableStateOf<Pair<JSONObject, String>?>(null) }

    LaunchedEffect(status, version, attempt) {
        loading = true; error = null
        try { val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/lead-center/export-requests?status=$status") }; rows = r.objs("data"); counts = r.optJSONObject("counts"); canReview = r.optJSONObject("can")?.optBoolean("review") == true }
        catch (e: Exception) { error = e.message ?: "Could not load the requests" }
        loading = false
    }
    val c = counts
    if (c != null) Box(Modifier.padding(top = 8.dp)) {
        ProKpiTiles(listOf(ProKpi(c.optInt("total").toString(), "Total", KitInk), ProKpi(c.optInt("pending").toString(), "Pending", Amber), ProKpi(c.optInt("approved").toString(), "Approved", KitGood), ProKpi(c.optInt("denied").toString(), "Denied", KitDanger)), columns = 2)
    }
    Header("Export requests", if (canReview) "Review who asked to export leads" else "Ask an admin to let you export leads", "+  Request export", b.base) { request = true }
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("all" to "All", "pending" to "Pending", "approved" to "Approved", "denied" to "Denied").forEach { (k, l) -> Pick(l, status == k, b.base) { status = k } }
    }
    Spacer(Modifier.height(8.dp))

    rows.forEach { r ->
        val st = r.optString("status")
        val sc = when (st) { "approved" -> KitGood; "denied" -> KitDanger; else -> Amber }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(5.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.str("requester") ?: "—", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(bidDayOf(r.str("created_at")) ?: "", color = KitMuted, fontSize = 12.sp)
                }
                Chip(st.replaceFirstChar { it.uppercase() }, sc)
            }
            Chip(r.str("list") ?: "All lists", Blue)
            Text(r.optString("reason"), color = KitInk, fontSize = 14.sp, lineHeight = 20.sp)
            if (st != "pending") Text("Reviewed by ${r.str("reviewer") ?: "—"}" + (bidDayOf(r.str("reviewed_at"))?.let { ", $it" } ?: ""), color = KitMuted, fontSize = 12.sp)
            r.str("notes")?.let { Text("Notes: $it", color = KitMuted, fontSize = 12.sp) }
            if (st == "pending" && canReview) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Approve", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(KitGood).clickable { decide = r to "approve" }.padding(vertical = 12.dp))
                Text("Deny", color = KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).clickable { decide = r to "deny" }.padding(vertical = 12.dp))
            } else if (st == "pending") Text("Awaiting review", color = Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Loading(loading, rows.isEmpty(), error, "No export requests.", b) { attempt++ }

    if (request) {
        var reason by remember { mutableStateOf("") }
        var listId by remember { mutableIntStateOf(0) }
        var pickList by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { request = false }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Request a lead export", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                PickerField("Lead list", options?.objs("lists")?.firstOrNull { it.optInt("id") == listId }?.optString("name") ?: "All lists") { pickList = true }
                Input("Why do you need it?", reason, { reason = it }, singleLine = false)
                SendButton("Send request", reason.isNotBlank()) {
                    request = false
                    runAct(scope, context, "Request sent", { api.send("POST", "/projects/$uuid/lead-center/export-requests", JSONObject().put("reason", reason.trim()).put("list_id", if (listId > 0) listId else JSONObject.NULL)) }, { attempt++ })
                }
            }
        }
        if (pickList) ChoiceSheet("Lead list", options?.objs("lists").orEmpty().map { Choice(it.optInt("id").toString(), it.optString("name")) }, listId.takeIf { it > 0 }?.toString(), b, allowNone = true, noneLabel = "All lists", onClose = { pickList = false }) { pickList = false; listId = it.toIntOrNull() ?: 0 }
    }
    decide?.let { (r, action) ->
        var notes by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { decide = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text((if (action == "approve") "Approve " else "Deny ") + "${r.str("requester") ?: "this"}'s request", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Input("Notes (optional)", notes, { notes = it }, singleLine = false)
                SendButton(if (action == "approve") "Approve" else "Deny", true) {
                    decide = null
                    runAct(scope, context, if (action == "approve") "Request approved" else "Request denied", { api.send("POST", "/lead-center/export-requests/${r.optInt("id")}/$action", JSONObject().put("reviewer_notes", notes.trim().ifBlank { JSONObject.NULL })) }, { attempt++ })
                }
            }
        }
    }
}

private fun bidDayOf(s: String?): String? = runCatching {
    val d = java.time.LocalDate.parse(s!!.take(10)).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
    if (s.length >= 16) "$d · ${s.substring(11, 16)}" else d
}.getOrNull()

// ───────────────────────────── pipeline report ─────────────────────────────

/** The website's Pipeline Report: how many leads are active, converted or lost, where they stand, where they come from and the last six months. Data: GET …/lead-center/pipeline. */
@Composable
internal fun PipelineReport(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(version, attempt) {
        error = null
        try { d = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/lead-center/pipeline") } } catch (e: Exception) { error = e.message ?: "Could not load the report" }
    }
    val x = d
    if (x == null) { Loading(true, true, error, "", b) { attempt++ }; return }

    Box(Modifier.padding(top = 12.dp)) {
        ProKpiTiles(
            listOf(
                ProKpi(x.optInt("total").toString(), "Total leads", KitInk), ProKpi(x.optInt("active").toString(), "Active", Blue), ProKpi(x.optInt("converted").toString(), "Converted", KitGood),
                ProKpi(x.optInt("lost").toString(), "Lost", KitDanger), ProKpi(x.optInt("new_this_month").toString(), "New this month", Amber), ProKpi("${x.optInt("rate")}%", "Conversion", KitGood),
            ),
            columns = 3,
        )
    }
    ProDonutCard("By status", "Where the leads stand", x.objs("by_status").map { Triple(it.optString("name"), it.optInt("count"), statusTintOf(options, it.optString("name"))) })
    ProDonutCard("By label", "Hot, warm and cold", x.objs("by_label").map { Triple(it.optString("name"), it.optInt("count"), labelTintOf(options, it.optString("name"))) })

    val trend = x.objs("trend")
    val peak = (trend.maxOfOrNull { it.optInt("total") } ?: 0).coerceAtLeast(1)
    Card("Last six months", "New leads and how many converted") {
        trend.forEach { m ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text(m.optString("month"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("${m.optInt("total")} new · ${m.optInt("converted")} converted", color = KitMuted, fontSize = 12.sp) }
                Meter(m.optInt("total") / peak.toFloat(), Blue)
            }
        }
    }
    val sources = x.objs("sources")
    Card("By source", "Which source brings the most leads") {
        if (sources.isEmpty()) Text("No data.", color = KitMuted, fontSize = 14.sp)
        sources.forEach { s ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text(s.optString("source"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("${s.optInt("total")} · ${s.optInt("converted")} converted · ${s.optInt("pct")}%", color = KitMuted, fontSize = 12.sp) }
                Meter(s.optInt("pct") / 100f, Amber)
            }
        }
    }
}

// ───────────────────────────── lead settings ─────────────────────────────

private class Kind(val key: String, val tab: String, val noun: String, val colored: Boolean)
private val Kinds = listOf(
    Kind("status", "Statuses", "status", true), Kind("label", "Labels", "label", true), Kind("followup-status", "Follow-up statuses", "follow-up status", true),
    Kind("followup-type", "Follow-up types", "follow-up type", true), Kind("email-template", "Email templates", "email template", false), Kind("message-template", "WA & SMS templates", "message template", false),
)
private val Swatches = listOf("#1A5FA8", "#2E7D52", "#0FD225", "#CFA251", "#C47A1A", "#B93232", "#AB7EC9", "#6B7A90", "#1B8A8F", "#B8527A")

/**
 * The website's Lead Settings: the statuses and labels leads can carry, the follow-up statuses and types, and the email and WhatsApp / SMS templates.
 * Admins can add, edit and delete; everyone else sees them. Data: GET …/lead-center/settings · POST …/settings/{kind} · DELETE …/settings/{kind}/{id}.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LeadSettings(api: ApiClient, b: Branding, uuid: String, version: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var openTpl by remember { mutableIntStateOf(0) }

    LaunchedEffect(version, attempt) {
        error = null
        try { d = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/lead-center/settings") } } catch (e: Exception) { error = e.message ?: "Could not load the settings" }
    }
    val x = d
    if (x == null) { Loading(true, true, error, "", b) { attempt++ }; return }
    val canManage = x.optJSONObject("can")?.optBoolean("manage") == true
    val kind = Kinds[tab]
    val rows = x.objs(when (kind.key) { "status" -> "statuses"; "label" -> "labels"; "followup-status" -> "followup_statuses"; "followup-type" -> "followup_types"; "email-template" -> "email_templates"; else -> "message_templates" })
    fun done() { attempt++; onChanged() }

    PillTabs(Kinds.map { PillTab(it.tab) }, tab, b.base) { tab = it }
    Header(kind.tab, if (canManage) "Add, edit or remove" else "Only admins can change these", if (canManage) "+  New" else null, b.base) { editing = JSONObject() }

    rows.forEach { r ->
        val core = r.optBoolean("core")
        val tpl = kind.key == "email-template" || kind.key == "message-template"
        val open = tpl && openTpl == r.optInt("id")
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White)
                .then(if (tpl) Modifier.clickable { openTpl = if (open) 0 else r.optInt("id") } else Modifier).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (kind.colored) Box(Modifier.padding(end = 12.dp).size(14.dp).clip(CircleShape).background(tint(r.str("color"))))
            Column(Modifier.weight(1f)) {
                Text(r.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                val sub = when (kind.key) {
                    "email-template" -> r.str("subject")
                    "message-template" -> (r.str("channel")?.uppercase() ?: "") + (r.str("message")?.let { " · $it" } ?: "")
                    else -> "Order ${r.optInt("sort_order")}" + if (core) " · built in" else ""
                }
                sub?.let { Text(it, color = KitMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            if (kind.colored || kind.key == "email-template") {
                val on = if (kind.key == "email-template") r.optString("status") != "inactive" else r.optBoolean("active")
                Chip(if (on) "Active" else "Inactive", if (on) KitGood else KitMuted)
            }
            if (canManage) { Spacer(Modifier.size(8.dp)); IconBtns({ editing = r }, if (core) null else ({ deleting = r })) }
        }
        // a template opens to its formatted text (bold, lists, links …) as it looks on the website
        if (open) com.example.gavanhomes.ui.common.HtmlText(r.optString(if (kind.key == "email-template") "body_html" else "message_html").ifBlank { r.optString(if (kind.key == "email-template") "body" else "message") }, fontSize = 14.sp)
        }
    }
    if (rows.isEmpty()) Text("Nothing here yet.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))

    editing?.let { r -> SettingSheet(kind, r, onClose = { editing = null }) { body ->
        editing = null
        runAct(scope, context, "Saved", { api.send("POST", "/projects/$uuid/lead-center/settings/${kind.key}", body.apply { if (r.has("id")) put("id", r.optInt("id")) }) }, ::done)
    } }
    deleting?.let { r -> SwalConfirm(
        title = "Delete this ${kind.noun}?", text = r.optString("name"), confirmText = "Yes, delete it",
        onConfirm = { deleting = null; runAct(scope, context, "Deleted", { api.delete("/projects/$uuid/lead-center/settings/${kind.key}/${r.optInt("id")}") }, ::done) }, onCancel = { deleting = null },
    ) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingSheet(kind: Kind, r: JSONObject, onClose: () -> Unit, onSave: (JSONObject) -> Unit) {
    val editing = r.has("id")
    var name by remember { mutableStateOf(r.optString("name")) }
    var color by remember { mutableStateOf(r.str("color") ?: Swatches.first()) }
    var order by remember { mutableStateOf(if (editing) r.optInt("sort_order").toString() else "0") }
    var active by remember { mutableStateOf(if (kind.key == "email-template") r.optString("status") != "inactive" else if (editing) r.optBoolean("active") else true) }
    var subject by remember { mutableStateOf(r.optString("subject")) }
    var body by remember { mutableStateOf(if (kind.key == "email-template") r.optString("body") else r.optString("message")) }
    var channel by remember { mutableStateOf(r.str("channel") ?: "whatsapp") }
    val hexOk = Regex("^#[0-9A-Fa-f]{6}$").matches(color)
    val template = !kind.colored
    val valid = name.isNotBlank() && (if (kind.colored) hexOk else body.isNotBlank() && (kind.key != "email-template" || subject.isNotBlank()))

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text((if (editing) "Edit " else "New ") + kind.noun, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Input("Name", name, { name = it })
            if (kind.colored) {
                Text("COLOUR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Swatches.forEach { h ->
                        Box(Modifier.size(34.dp).clip(CircleShape).background(tint(h)).then(if (color.equals(h, true)) Modifier.border(3.dp, KitInk, CircleShape) else Modifier).clickable { color = h })
                    }
                }
                Input("Hex colour", color, { color = it.take(7) }, if (hexOk) null else "Use the form #RRGGBB")
                Input("Order", order, { order = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number)
                if (editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pick("Active", active, KitGood) { active = true }; Pick("Inactive", !active, KitMuted) { active = false } }
            }
            if (kind.key == "email-template") {
                Input("Subject", subject, { subject = it })
                Input("Body", body, { body = it }, singleLine = false)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pick("Active", active, KitGood) { active = true }; Pick("Inactive", !active, KitMuted) { active = false } }
            }
            if (kind.key == "message-template") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pick("WhatsApp", channel == "whatsapp", Color(0xFF25A244)) { channel = "whatsapp" }; Pick("SMS", channel == "sms", Blue) { channel = "sms" } }
                Input("Message", body, { body = it }, singleLine = false)
            }
            SendButton(if (editing) "Save" else "Add", valid) {
                val o = JSONObject().put("name", name.trim())
                if (kind.colored) { o.put("color", color).put("sort_order", order.toIntOrNull() ?: 0); if (editing) o.put("is_active", if (active) 1 else 0) }
                if (kind.key == "email-template") o.put("subject", subject.trim()).put("body", body.trim()).put("status", if (active) "active" else "inactive")
                if (kind.key == "message-template") o.put("channel", channel).put("message", body.trim())
                onSave(o)
            }
        }
    }
}

// ───────────────────────────── schedule a follow-up ─────────────────────────────

/** Search the project's leads and pick one — the first step of "Schedule follow-up". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeadPickSheet(api: ApiClient, uuid: String, onClose: () -> Unit, onPick: (JSONObject) -> Unit) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(query) {
        delay(300); loading = true
        rows = runCatching { withContext(Dispatchers.IO) { api.get("/projects/$uuid/lead-center/list?search=${URLEncoder.encode(query.trim(), "UTF-8")}").objs("data") } }.getOrDefault(emptyList())
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Which lead?", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Input("Search by name or phone", query, { query = it })
            Column(Modifier.height(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (loading) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else if (rows.isEmpty()) Text("No leads match.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                rows.forEach { r ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onPick(r) }.padding(14.dp)) {
                        Text(r.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(listOfNotNull(r.str("phone"), r.str("status")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
