package com.example.gavanhomes.ui.comms

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
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
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.ModulePages
import com.example.gavanhomes.ui.common.ProvideFab
import com.example.gavanhomes.ui.inventory.Banner
import com.example.gavanhomes.ui.inventory.FilteredList
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.NewestOldest
import com.example.gavanhomes.ui.inventory.PeoplePick
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.SaveBar
import com.example.gavanhomes.ui.inventory.dateGroup
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.whenLabel
import com.example.gavanhomes.ui.professionals.ProDonutCard
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Label
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val WhatsApp = Color(0xFF1FA855)

internal fun channelLabel(c: String?) = when (c) { "whatsapp" -> "WhatsApp"; "sms" -> "SMS"; "email" -> "Email"; else -> c ?: "—" }
internal fun channelColor(c: String?) = when (c) { "whatsapp" -> WhatsApp; "sms" -> InvBlue; else -> InvPurple }
private fun statusColor(s: String?) = when (s) { "sent" -> KitGood; "failed" -> KitDanger; else -> InvAmber }

/**
 * The project's Communications module, like the website's Communications tab: a dashboard of what was sent, the sent History
 * and the saved Templates. The + button sends a WhatsApp, an SMS or an email. Data: /projects/{uuid}/comms-center/…
 * [onCompose] opens the send screen for a channel, optionally starting from a template (id).
 */
@Composable
fun ProjectComms(api: ApiClient, b: Branding, uuid: String, version: Int, onCompose: (String, Int) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(version, attempt) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/comms-center/overview") } } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    ProvideFab(listOf(FabAction("whatsapp", "Send WhatsApp", WhatsApp, "✆"), FabAction("sms", "Send SMS", InvBlue, "✉"), FabAction("email", "Send email", InvPurple, "@"))) { onCompose(it, 0) }
    ModulePages("Communications", listOf("Dashboard", "History", "Templates"), page) { page = it }
    when (page) {
        0 -> {
            val o = overview
            if (o == null) Loading(b, error) { attempt++ } else Dashboard(o) { page = 1 }
        }
        1 -> FilteredList(
            api, b, "/projects/$uuid/comms-center/history", version, "Search name, number, email or text…",
            groups = listOf(
                FilterGroup("channel", "Channel", listOf("whatsapp", "sms", "email").map { FilterOption(it, channelLabel(it)) }),
                FilterGroup("status", "Status", listOf("sent" to "Sent", "failed" to "Failed", "skipped" to "Not sent").map { FilterOption(it.first, it.second) }),
                dateGroup(),
            ),
            sorts = NewestOldest, empty = "Nothing has been sent from this project yet.", noun = "message",
        ) { HistoryRow(it) }
        else -> Templates(api, b, uuid, version) { ch, id -> onCompose(ch, id) }
    }
}

@Composable
private fun Loading(b: Branding, error: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
        if (error == null) CircularProgressIndicator(color = b.base)
        else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text(error, color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onRetry) { Text("Retry") } }
    }
}

@Composable
private fun Dashboard(o: JSONObject, onHistory: () -> Unit) {
    val s = o.optJSONObject("stats") ?: JSONObject()
    val t = o.optJSONObject("templates") ?: JSONObject()
    Box(Modifier.padding(top = 4.dp)) {
        ProKpiTiles(listOf(
            ProKpi(s.optInt("whatsapp").toString(), "WhatsApp", WhatsApp), ProKpi(s.optInt("sms").toString(), "SMS", InvBlue), ProKpi(s.optInt("email").toString(), "Emails", InvPurple),
            ProKpi(s.optInt("today").toString(), "Sent today", KitInk), ProKpi(s.optInt("failed").toString(), "Failed", KitDanger), ProKpi((t.optInt("email") + t.optInt("whatsapp")).toString(), "Templates", InvAmber),
        ), columns = 3)
    }
    ProDonutCard("By channel", "Every message sent from this project", listOf(Triple("WhatsApp", s.optInt("whatsapp"), WhatsApp), Triple("SMS", s.optInt("sms"), InvBlue), Triple("Email", s.optInt("email"), InvPurple)), empty = "Nothing sent yet.") { onHistory() }
    ProDonutCard("Delivery", null, listOf(Triple("Sent", s.optInt("sent"), KitGood), Triple("Failed", s.optInt("failed"), KitDanger), Triple("Not sent", s.optInt("skipped"), InvAmber)), empty = "Nothing sent yet.") { onHistory() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRow(m: JSONObject) {
    var open by remember { mutableStateOf(false) }
    RowCard({ open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.str("name") ?: m.optString("to"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.str("name") != null) Text(m.optString("to"), color = KitMuted, fontSize = 12.sp, maxLines = 1)
            }
            Text(whenLabel(m.str("at")) ?: "", color = KitMuted, fontSize = 12.sp)
        }
        m.str("subject")?.let { Text(it, color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
        m.str("message")?.let { Text(it, color = KitInk.copy(alpha = .8f), fontSize = 13.sp, maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
        m.str("error")?.let { Text(it, color = KitDanger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(channelLabel(m.str("channel")), channelColor(m.str("channel")))
            val st = m.str("status")
            Chip(if (st == "skipped") "Not sent" else st?.replaceFirstChar { it.uppercase() } ?: "—", statusColor(st))
            m.str("by")?.let { Chip("By $it", KitMuted) }
        }
    }
}

@Composable
private fun Templates(api: ApiClient, b: Branding, uuid: String, version: Int, onUse: (String, Int) -> Unit) {
    var t by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(version, attempt) { error = null; try { t = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/comms-center/templates") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = t ?: return Loading(b, error) { attempt++ }
    val messages = x.objs("message"); val emails = x.objs("email")
    if (messages.isEmpty() && emails.isEmpty()) Text("No templates yet — they are made on the website.", color = KitMuted, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
    if (messages.isNotEmpty()) Card("WhatsApp & SMS", "${messages.size} templates · tap to send") {
        messages.forEach { m -> TemplateRow(m.optString("name"), m.str("text"), listOfNotNull(m.str("module"))) { onUse(if (m.str("channel") == "sms") "sms" else "whatsapp", m.optInt("id")) } }
    }
    if (emails.isNotEmpty()) Card("Email", "${emails.size} templates · tap to send") {
        emails.forEach { m -> TemplateRow(m.optString("name"), listOfNotNull(m.str("subject"), m.str("text")).joinToString("\n"), listOfNotNull(m.str("module"))) { onUse("email", m.optInt("id")) } }
    }
}

@Composable
private fun TemplateRow(name: String, text: String?, chips: List<String>, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onClick() }.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            chips.forEach { Chip(it, KitMuted) }
        }
        text?.let { Text(it, color = KitMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
    }
}

// ───────────────────────────── send ─────────────────────────────

private val Sources = listOf("lead" to "Leads", "customer" to "Customers", "bids" to "Bid suppliers")

/**
 * Send a WhatsApp, SMS or email: recipients picked from the project's leads, customers or bid suppliers (or typed), an optional
 * template, the message (and subject / CC for email). The website sends and logs it and says how many went.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(api: ApiClient, b: Branding, uuid: String, channel: String, templateId: Int, onBack: () -> Unit, onSent: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val email = channel == "email"
    val to = remember { mutableStateMapOf<String, String>() }                 // number / email → name
    var typed by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var template by remember { mutableStateOf<String?>(null) }
    var templates by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var source by remember { mutableStateOf<Pair<String, List<JSONObject>>?>(null) }
    var loadingSource by remember { mutableStateOf<String?>(null) }
    var pickTemplate by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun use(t: JSONObject) {
        template = t.optInt("id").toString()
        if (email) { subject = t.optString("subject"); message = t.optString("text") } else message = t.optString("text")
    }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/comms-center/templates") } }.getOrNull()?.let { x ->
            templates = if (email) x.objs("email") else x.objs("message")
            templates.firstOrNull { it.optInt("id") == templateId }?.let(::use)
        }
    }
    fun addTyped() {
        val v = typed.trim()
        if (v.isEmpty()) return
        val ok = if (email) android.util.Patterns.EMAIL_ADDRESS.matcher(v).matches() else Regex("^\\+?[0-9][0-9\\s\\-]{5,18}$").matches(v)
        if (!ok) { errors = mapOf("to" to if (email) "That is not an email address." else "That is not a phone number."); return }
        to[v] = to[v] ?: ""; typed = ""; errors = errors - "to"
    }
    fun openSource(type: String) {
        loadingSource = type
        scope.launch {
            try {
                val a = withContext(Dispatchers.IO) { api.get("/projects/$uuid/comms-center/recipients?type=$type&channel=${if (email) "email" else "phone"}") }.optJSONArray("data") ?: JSONArray()
                val rows = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.str(if (email) "email" else "phone") != null }
                if (rows.isEmpty()) Toast.makeText(context, "No ${Sources.first { it.first == type }.second.lowercase()} with ${if (email) "an email" else "a number"}", Toast.LENGTH_SHORT).show()
                else source = type to rows
            } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not load", Toast.LENGTH_LONG).show() }
            loadingSource = null
        }
    }
    fun send() {
        if (typed.isNotBlank()) addTyped()
        val e = mutableMapOf<String, String>()
        if (to.isEmpty()) e["to"] = "Add at least one recipient."
        if (email && subject.isBlank()) e["subject"] = "Enter a subject."
        if (message.isBlank()) e["message"] = "Write a message."
        errors = e; if (e.isNotEmpty()) return
        val body = JSONObject().put("channel", channel).put("recipients", JSONArray(to.keys.toList()))
        if (email) {
            body.put("subject", subject.trim()).put("body", message)
            cc.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let { body.put("cc", JSONArray(it)) }
        } else body.put("message", message)
        template?.let { body.put("template_id", it) }
        saving = true; banner = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/comms-center/send", body) }
                Toast.makeText(context, r.str("message") ?: "Sent", Toast.LENGTH_LONG).show(); onSent()
            } catch (x: ApiException) { errors = x.errors.mapKeys { (k, _) -> if (k.startsWith("recipients")) "to" else if (k == "body") "message" else k }; banner = x.message }
            catch (x: Exception) { banner = x.message ?: "Could not send" }
            saving = false
        }
    }

    val title = "Send " + if (email) "email" else channelLabel(channel)
    Column(Modifier.fillMaxSize().background(KitPage).imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            HeroBar(b, title, onBack, compact = true)
            Banner(banner)
            Card("To", if (to.isEmpty()) "Pick from the project or type ${if (email) "an email" else "a number"}" else "${to.size} recipient${if (to.size == 1) "" else "s"}") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sources.forEach { (k, l) -> com.example.gavanhomes.ui.tasks.Pick(if (loadingSource == k) "Loading…" else "+ $l", false, b.base) { if (loadingSource == null) openSource(k) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Input(if (email) "Email address" else "Phone number", typed, { typed = it }, errors["to"], keyboard = if (email) KeyboardType.Email else KeyboardType.Phone) }
                    Text("Add", color = b.base, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(12.dp)).clickable { addTyped() }.padding(10.dp))
                }
                if (to.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    to.forEach { (v, n) ->
                        Text("${n.ifBlank { v }}  ✕", color = KitInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(b.base.copy(alpha = .12f)).clickable { to.remove(v) }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            Card("Message") {
                if (templates.isNotEmpty()) PickerField("Template", templates.firstOrNull { it.optInt("id").toString() == template }?.optString("name") ?: "Write my own") { pickTemplate = true }
                if (email) {
                    Input("Subject", subject, { subject = it }, errors["subject"])
                    Input("CC (optional, comma between)", cc, { cc = it }, errors["cc"], keyboard = KeyboardType.Email)
                }
                Input("Message", message, { message = it }, errors["message"], singleLine = false)
                Label("${message.length} characters")
            }
            Spacer(Modifier.height(20.dp))
        }
        SaveBar(if (to.size > 1) "Send to ${to.size}" else "Send", saving, b) { if (!saving) send() }
    }
    source?.let { (type, rows) ->
        val key = if (email) "email" else "phone"
        PeoplePick(rows, rows.filter { it.optString(key) in to }.map { it.optInt("id") }.toSet(), b, Sources.first { it.first == type }.second, onClose = { source = null }) { ids ->
            rows.forEach { r -> val v = r.optString(key); if (r.optInt("id") in ids) to[v] = r.optString("name") else if (to[v] == r.optString("name")) to.remove(v) }
            source = null; errors = errors - "to"
        }
    }
    if (pickTemplate) ChoiceSheet("Template", templates.map { Choice(it.optInt("id").toString(), it.optString("name")) }, template, b, allowNone = true, noneLabel = "Write my own", onClose = { pickTemplate = false }) { v ->
        pickTemplate = false
        templates.firstOrNull { it.optInt("id").toString() == v }?.let(::use) ?: run { template = null }
    }
}
