package com.example.gavanhomes.ui.comms

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.gavanhomes.ui.common.FilterBar
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.procurement.ActionButton
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
 * The project's Communications module, like the website's Communications tab: a Dashboard (the last 30 days by channel, delivery
 * rate, people reached, a 14-day trend, recent messages and failures, who can be reached, the template library), the sent History,
 * and the Message (WhatsApp / SMS) and Email templates — made, changed and deleted here. The + button sends a WhatsApp, an SMS or
 * an email, or starts a template. Data: /projects/{uuid}/comms-center/… [onCompose] opens the send screen for a channel,
 * optionally from a template (id).
 */
@Composable
fun ProjectComms(api: ApiClient, b: Branding, uuid: String, version: Int, onCompose: (String, Int) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var local by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }        // message · email, with the template ({} = new)
    val v = version + local
    LaunchedEffect(v, attempt) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/comms-center/overview") } } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    val can = overview?.optJSONObject("can") ?: JSONObject()
    ProvideFab(listOfNotNull(
        FabAction("whatsapp", "Send WhatsApp", WhatsApp, "✆").takeIf { can.optBoolean("send", true) },
        FabAction("sms", "Send SMS", InvBlue, "✉").takeIf { can.optBoolean("send", true) },
        FabAction("email", "Send email", InvPurple, "@").takeIf { can.optBoolean("send", true) },
        FabAction("t-message", "New message template", InvAmber, "❏").takeIf { can.optBoolean("create_message_templates") },
        FabAction("t-email", "New email template", KitInk, "✎").takeIf { can.optBoolean("create_email_templates") },
    )) { k -> if (k.startsWith("t-")) editing = k.removePrefix("t-") to JSONObject() else onCompose(k, 0) }
    val pages = listOf("Dashboard", "History", "Message templates", "Email templates")
    ModulePages("Communications", pages, page) { page = it }
    when (page) {
        0 -> {
            val o = overview
            if (o == null) Loading(b, error) { attempt++ } else Dashboard(o, onPage = { page = it })
        }
        1 -> FilteredList(
            api, b, "/projects/$uuid/comms-center/history", v, "Search name, number, email or text…",
            groups = listOf(
                FilterGroup("channel", "Channel", listOf("whatsapp", "sms", "email").map { FilterOption(it, channelLabel(it)) }),
                FilterGroup("status", "Status", listOf("sent" to "Sent", "failed" to "Failed", "skipped" to "Not sent").map { FilterOption(it.first, it.second) }),
                dateGroup(),
            ),
            sorts = NewestOldest, empty = "Nothing has been sent from this project yet.", noun = "message",
        ) { HistoryRow(it) }
        else -> Templates(api, b, uuid, v, email = page == 3, onUse = onCompose, onEdit = { kind, t -> editing = kind to t }, onChanged = { local++ })
    }
    editing?.let { (kind, t) -> TemplateForm(api, b, uuid, kind == "email", t, onClose = { editing = null }) { editing = null; local++ } }
}

@Composable
private fun Loading(b: Branding, error: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
        if (error == null) CircularProgressIndicator(color = b.base)
        else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text(error, color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onRetry) { Text("Retry") } }
    }
}

@Composable
private fun Dashboard(o: JSONObject, onPage: (Int) -> Unit) {
    val s = o.optJSONObject("stats") ?: JSONObject()
    val m = o.optJSONObject("month") ?: JSONObject()
    val t = o.optJSONObject("templates") ?: JSONObject()
    Text("Last 30 days", color = KitMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, top = 10.dp))
    ProKpiTiles(listOf(
        ProKpi(m.optInt("sent").toString(), "Sent", KitGood), ProKpi(if (m.isNull("delivery_rate")) "—" else "${m.optInt("delivery_rate")}%", "Delivered", InvBlue),
        ProKpi(m.optInt("reached").toString(), "People reached", InvPurple), ProKpi(m.optInt("failed").toString(), "Failed", KitDanger),
        ProKpi(m.optInt("skipped").toString(), "Not sent", InvAmber), ProKpi(s.optInt("today").toString(), "Sent today", KitInk),
    ), columns = 3)
    // per channel: sent / failed / not sent over the 30 days
    val ch = m.optJSONObject("channels") ?: JSONObject()
    Card("By channel", "Last 30 days") {
        listOf("whatsapp", "sms", "email").forEach { c ->
            val x = ch.optJSONObject(c) ?: JSONObject()
            val total = x.optInt("sent") + x.optInt("failed") + x.optInt("skipped")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(channelLabel(c), color = channelColor(c), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${x.optInt("sent")} sent · ${x.optInt("failed")} failed · ${x.optInt("skipped")} not sent", color = KitMuted, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF0EEEA))) {
                    if (total > 0) listOf(x.optInt("sent") to KitGood, x.optInt("failed") to KitDanger, x.optInt("skipped") to InvAmber).filter { it.first > 0 }.forEach { (n, col) ->
                        Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(col))
                    }
                }
            }
        }
    }
    // 14 days of sent messages, a bar per day split by channel
    val trend = o.objs("trend")
    if (trend.isNotEmpty()) Card("Last 14 days", "Messages sent each day") {
        val top = trend.maxOf { it.optInt("email") + it.optInt("sms") + it.optInt("whatsapp") }.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            trend.forEach { d ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    listOf("whatsapp" to WhatsApp, "sms" to InvBlue, "email" to InvPurple).forEach { (k, col) ->
                        val n = d.optInt(k)
                        if (n > 0) Box(Modifier.fillMaxWidth().height((100.0 * n / top).dp).background(col))
                    }
                    if (d.optInt("email") + d.optInt("sms") + d.optInt("whatsapp") == 0) Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFE8E5DE)))
                }
            }
        }
        Row {
            Text(com.example.gavanhomes.ui.tasks.prettyDay(trend.first().str("date")) ?: "", color = KitMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text("Today", color = KitMuted, fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("WhatsApp" to WhatsApp, "SMS" to InvBlue, "Email" to InvPurple).forEach { (l, col) -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(col)); Text(" $l", color = KitMuted, fontSize = 11.sp) } }
        }
    }
    ProDonutCard("All time, by channel", "Every message sent from this project", listOf(Triple("WhatsApp", s.optInt("whatsapp"), WhatsApp), Triple("SMS", s.optInt("sms"), InvBlue), Triple("Email", s.optInt("email"), InvPurple)), empty = "Nothing sent yet.") { onPage(1) }
    val failures = o.objs("failures")
    if (failures.isNotEmpty()) Card("Recent failures", "Why they didn't go") { failures.forEach { f -> MiniLog(f) } }
    val recent = o.objs("recent")
    Card("Recent messages", if (recent.isEmpty()) "Nothing sent in the last 30 days" else "Tap History for everything") {
        recent.forEach { MiniLog(it) }
        if (recent.isNotEmpty()) Text("Open History  ›", color = InvBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { onPage(1) }.padding(vertical = 4.dp))
    }
    // who the send screens can reach
    o.optJSONObject("audience")?.let { a ->
        Card("Who you can reach", "With an email address · with a phone number") {
            listOf("leads" to "Leads", "customers" to "Customers", "suppliers" to "Bid suppliers").forEach { (k, l) ->
                val x = a.optJSONObject(k) ?: JSONObject()
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l, color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${x.optInt("total")} in this project", color = KitMuted, fontSize = 12.sp)
                    }
                    Chip("@ ${x.optInt("email")}", InvPurple); Spacer(Modifier.size(6.dp)); Chip("✆ ${x.optInt("phone")}", WhatsApp)
                }
            }
        }
    }
    Card("Templates", "Saved messages to start from") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("WhatsApp", t.optInt("whatsapp"), 2), Triple("SMS", t.optInt("sms"), 2), Triple("Email", t.optInt("email"), 3)).forEach { (l, n, p) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onPage(p) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n.toString(), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text(l, color = KitMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

/** One sent message in a dashboard card: who, when, channel and outcome (and why it failed). */
@Composable
private fun MiniLog(m: JSONObject) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.str("name") ?: m.optString("to"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Chip(channelLabel(m.str("channel")), channelColor(m.str("channel")))
        }
        Text(listOfNotNull(whenLabel(m.str("at")), m.str("subject") ?: m.str("message")?.take(60)).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        m.str("error")?.let { Text(it, color = KitDanger, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
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

private fun moduleLabel(m: String?) = when (m) { "lead" -> "Lead"; "customer" -> "Customer"; "bids" -> "Bids"; "other" -> "Other"; null, "" -> null; else -> m.replaceFirstChar { it.uppercase() } }

/** The Message or Email templates: search, filter by channel / module (/ status), each with Send, Edit, Delete and (email) Preview. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Templates(api: ApiClient, b: Branding, uuid: String, version: Int, email: Boolean, onUse: (String, Int) -> Unit, onEdit: (String, JSONObject) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var sel by remember(email) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var open by remember { mutableStateOf<JSONObject?>(null) }
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    var askDelete by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(version, attempt) { error = null; try { t = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/comms-center/templates?all=1") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = t ?: return Loading(b, error) { attempt++ }
    val can = x.optJSONObject("can") ?: JSONObject()
    val kind = if (email) "email" else "message"
    if (!can.optBoolean("view_${kind}_templates") && !can.optBoolean("send")) { Text("You can't see these templates.", color = KitMuted, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center); return }
    val all = x.objs(kind)
    val modules = x.objs("modules")
    val groups = listOfNotNull(
        if (email) FilterGroup("status", "Status", listOf(FilterOption("active", "Active"), FilterOption("inactive", "Inactive")))
        else FilterGroup("channel", "Channel", listOf(FilterOption("whatsapp", "WhatsApp"), FilterOption("sms", "SMS"))),
        FilterGroup("module", "Module", modules.map { FilterOption(it.optString("value"), it.optString("label")) }),
    )
    val shown = all.filter { r ->
        (search.isBlank() || listOf("name", "subject", "text").any { r.optString(it).contains(search, true) }) &&
            sel.all { (k, vs) -> vs.isEmpty() || r.optString(k) in vs }
    }
    FilterBar(search, { search = it }, if (email) "Search name, subject or text…" else "Search name or text…", groups = groups, selected = sel,
        onApply = { sel = it.filterValues { v -> v.isNotEmpty() } },
        countWith = { m -> all.count { r -> m.all { (k, vs) -> vs.isEmpty() || r.optString(k) in vs } } })
    Text("${shown.size} template${if (shown.size == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    if (shown.isEmpty()) Text(if (all.isEmpty()) "No ${if (email) "email" else "message"} templates yet${if (can.optBoolean("create_${kind}_templates")) " — add one with +." else "."}" else "No template matches.", color = KitMuted, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
    shown.forEach { r ->
        RowCard({ open = r }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                r.str("updated")?.let { Text(com.example.gavanhomes.ui.tasks.prettyDay(it) ?: "", color = KitMuted, fontSize = 11.sp) }
            }
            r.str("subject")?.let { Text(it, color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            r.str("text")?.let { Text(it, color = KitMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!email) Chip(channelLabel(r.str("channel")), channelColor(r.str("channel")))
                moduleLabel(r.str("module"))?.let { Chip(it, KitMuted) }
                if (email) Chip(if (r.str("status") == "inactive") "Inactive" else "Active", if (r.str("status") == "inactive") KitMuted else KitGood)
                if (r.optBoolean("rich")) Chip("Formatted", InvAmber)
            }
        }
    }
    open?.let { r ->
        val ch = if (email) "email" else if (r.str("channel") == "sms") "sms" else "whatsapp"
        ModalBottomSheet(onDismissRequest = { open = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(r.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
                r.str("subject")?.let { Text(it, color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                Text(r.optString("text").ifBlank { "(empty)" }, color = KitInk.copy(alpha = .85f), fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp))
                if (can.optBoolean("send") && r.str("status") != "inactive") ActionButton("Send ${channelLabel(ch)}", channelColor(ch)) { open = null; onUse(ch, r.optInt("id")) }
                if (!email && can.optBoolean("send")) {
                    val other = if (ch == "sms") "whatsapp" else "sms"
                    SmallAction("Send as ${channelLabel(other)} instead", channelColor(other)) { open = null; onUse(other, r.optInt("id")) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (email && !r.str("body").isNullOrBlank()) SmallAction("Preview", InvBlue) { open = null; preview = r }
                    if (can.optBoolean("edit_${kind}_templates")) SmallAction("Edit", InvPurple) { open = null; onEdit(kind, r) }
                    if (can.optBoolean("delete_${kind}_templates")) SmallAction("Delete", KitDanger) { open = null; askDelete = r }
                }
            }
        }
    }
    preview?.let { r ->
        ModalBottomSheet(onDismissRequest = { preview = null }, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(r.optString("subject").ifBlank { r.optString("name") }, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = KitInk, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx -> android.webkit.WebView(ctx).apply { settings.javaScriptEnabled = false; loadDataWithBaseURL(null, "<meta name=viewport content='width=device-width,initial-scale=1'><body style='font-family:sans-serif;padding:8px'>" + r.optString("body") + "</body>", "text/html", "utf-8", null) } },
                    modifier = Modifier.fillMaxWidth().height(460.dp),
                )
            }
        }
    }
    askDelete?.let { r ->
        SwalConfirm(title = "Delete ${r.optString("name")}?", text = "The template is removed for everyone. Messages already sent stay in History.", confirmText = "Yes, delete",
            onConfirm = { askDelete = null; scope.launch {
                try { withContext(Dispatchers.IO) { api.delete("/projects/$uuid/comms-center/templates/$kind/${r.optInt("id")}") }; Toast.makeText(context, "Template deleted", Toast.LENGTH_SHORT).show(); onChanged() }
                catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
            } }, onCancel = { askDelete = null })
    }
}

/** The placeholders a template may use — tap one to add it where the text ends. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaceholderChips(b: Branding, onAdd: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Add:", color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        listOf("{{name}}", "{{email}}", "{{phone}}").forEach { p ->
            Text(p, color = b.base, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(b.base.copy(alpha = .1f)).clickable { onAdd(p) }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

/** New or edit a template: message (name, WhatsApp / SMS, module, message) or email (name, module, status, subject, body). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TemplateForm(api: ApiClient, b: Branding, uuid: String, email: Boolean, t: JSONObject, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = t.optInt("id")
    var name by remember { mutableStateOf(t.optString("name")) }
    var channel by remember { mutableStateOf(t.str("channel") ?: "whatsapp") }
    var module by remember { mutableStateOf(t.str("module") ?: "lead") }
    var status by remember { mutableStateOf(t.str("status") ?: "active") }
    var subject by remember { mutableStateOf(t.optString("subject")) }
    val original = t.optString("text")
    var text by remember { mutableStateOf(original) }
    var pickModule by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    val rich = t.optBoolean("rich")
    fun save() {
        val body = JSONObject().put("name", name.trim()).put("module", module)
        if (id > 0) body.put("id", id)
        if (email) {
            body.put("subject", subject.trim()).put("status", status)
            // a formatted template keeps its body unless the text was changed here
            if (id > 0 && text == original) body.put("keep_body", 1) else body.put("body", text)
        } else body.put("channel", channel).put("message", text)
        saving = true; errors = emptyMap()
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/comms-center/templates/${if (email) "email" else "message"}", body) }
                Toast.makeText(context, r.str("message") ?: "Saved", Toast.LENGTH_SHORT).show(); onDone()
            } catch (e: ApiException) { errors = e.errors.mapKeys { (k, _) -> if (k == "body") "message" else k }.ifEmpty { mapOf("name" to (e.message ?: "Could not save")) } }
            catch (e: Exception) { errors = mapOf("name" to (e.message ?: "Could not save")) }
            saving = false
        }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text((if (id > 0) "Edit " else "New ") + if (email) "email template" else "message template", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Input("Template name", name, { name = it }, errors["name"])
            if (!email) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.gavanhomes.ui.tasks.Pick("WhatsApp", channel == "whatsapp", WhatsApp) { channel = "whatsapp" }
                com.example.gavanhomes.ui.tasks.Pick("SMS", channel == "sms", InvBlue) { channel = "sms" }
            }
            errors["channel"]?.let { Text(it, color = KitDanger, fontSize = 12.sp) }
            PickerField("Module", moduleLabel(module), errors["module"]) { pickModule = true }
            if (email) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.gavanhomes.ui.tasks.Pick("Active", status == "active", KitGood) { status = "active" }
                    com.example.gavanhomes.ui.tasks.Pick("Inactive", status == "inactive", KitMuted) { status = "inactive" }
                }
                Input("Subject", subject, { subject = it }, errors["subject"])
            }
            if (rich) Text("This email was designed on the website with formatting (tables, images or colours). Leave the text as it is to keep that design — changing it saves plain text.",
                color = InvAmber, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(InvAmber.copy(alpha = .1f)).padding(10.dp))
            Input(if (email) "Email text" else "Message", text, { text = it }, errors["message"], singleLine = false)
            PlaceholderChips(b) { text = if (text.isEmpty() || text.endsWith(" ") || text.endsWith("\n")) text + it else "$text $it" }
            Text("Placeholders are filled in for each person when you send.", color = KitMuted, fontSize = 12.sp)
            ActionButton(if (saving) "Saving…" else if (id > 0) "Save template" else "Add template", b.base) {
                if (saving) return@ActionButton
                val e = mutableMapOf<String, String>()
                if (name.isBlank()) e["name"] = "Give the template a name."
                if (email && subject.isBlank()) e["subject"] = "Enter a subject."
                if (text.isBlank()) e["message"] = "Write the message."
                errors = e
                if (e.isEmpty()) save()
            }
        }
    }
    if (pickModule) ChoiceSheet("Module", listOf(Choice("lead", "Lead"), Choice("customer", "Customer"), Choice("bids", "Bids"), Choice("other", "Other")), module, b, onClose = { pickModule = false }) { module = it; pickModule = false }
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
                PlaceholderChips(b) { message = if (message.isEmpty() || message.endsWith(" ") || message.endsWith("\n")) message + it else "$message $it" }
                Label("${message.length} characters · {{name}} {{email}} {{phone}} are filled in for each person")
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
