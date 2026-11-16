package com.example.gavanhomes.ui.cp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.KitSoft
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun cr(v: Double): String = when {
    v >= 10_000_000 -> String.format(java.util.Locale.US, "₹%.2f Cr", v / 10_000_000)
    v >= 100_000 -> String.format(java.util.Locale.US, "₹%.2f L", v / 100_000)
    else -> "₹" + String.format(java.util.Locale.US, "%,.0f", v)
}

private fun statusColor(s: String) = when (s) { "approved" -> KitGood; "pending" -> InvAmber; "rejected" -> KitDanger; else -> KitMuted }
private fun levelColor(name: String?) = when (name) { "Bronze" -> Color(0xFFB07A3A); "Silver" -> Color(0xFF7C8794); "Gold" -> Color(0xFFD4A017); "Platinum" -> Color(0xFF5B8DB8); "Ennovia" -> Color(0xFF6A4FB3); "Elite" -> Color(0xFF1C2330); else -> KitMuted }

/**
 * The CP Registration Desk and Channel Partner database. List by status (waiting for approval first), the registration link to share, approve / reject
 * with a reason, the partner's page with the Annual League (level, brokerage %, rupees left for the next level), leads / visits / bookings / business,
 * commission earned / paid / payable with its monthly statement, the leaderboard, and a WhatsApp update to partners who have not opted out.
 * [projectUuid] shows the numbers of that project only (null = all projects — the central list).
 * Data: GET /cp-center · /cp-center/{id} · /cp-center/leaderboard; POST …/approve · reject · payments · broadcast.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChannelPartnerScreen(api: ApiClient, b: Branding, projectUuid: String?, version: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf("all") }                   // all · pending · approved · inactive · rejected · board
    var open by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var broadcast by remember { mutableStateOf(false) }
    val q = if (projectUuid != null) "?project=$projectUuid" else ""
    LaunchedEffect(version, attempt, projectUuid) {
        error = null
        try { data = withContext(Dispatchers.IO) { api.get("/cp-center$q") } } catch (e: Exception) { error = e.message ?: "Could not load" }
    }

    open?.let { id -> PartnerPage(api, b, id, projectUuid, onBack = { open = null; attempt++ }, onChanged = { attempt++ }); return }

    val d = data
    if (d == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base) else com.example.gavanhomes.ui.common.EstateError(error, color = b.base, modifier = Modifier.padding(24.dp), onRetry = { attempt++ })
        }
        return
    }
    val can = d.optJSONObject("can") ?: JSONObject()
    val counts = d.optJSONObject("counts") ?: JSONObject()
    val link = d.optString("link")
    val all = d.objs("data")
    val rows = if (tab == "all") all else all.filter { it.optString("status") == tab }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("All ${all.size}", tab == "all", b.base) { tab = "all" }
            listOf("pending" to "Waiting", "approved" to "Approved", "inactive" to "Inactive", "rejected" to "Rejected").forEach { (k, l) ->
                Chip("$l ${counts.optInt(k, 0)}", tab == k, if (k == "pending" && counts.optInt(k, 0) > 0) InvAmber else b.base) { tab = k }
            }
            Chip("Leaderboard", tab == "board", b.base) { tab = "board" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (can.optBoolean("create")) Text("+ Add partner", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitInk).clickable { adding = true }.padding(horizontal = 14.dp, vertical = 9.dp))
            Text("🔗 Share link", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KitInk, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitSoft)
                .clickable { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Register as a Gavan Group Channel Partner: $link"), "Share the registration link")) }.padding(horizontal = 14.dp, vertical = 9.dp))
            if (can.optBoolean("broadcast")) Text("✉ Send update", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KitInk, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitSoft).clickable { broadcast = true }.padding(horizontal = 14.dp, vertical = 9.dp))
        }

        if (tab == "board") Leaderboard(api, b) { open = it }
        else {
            if (rows.isEmpty()) Text(when (tab) { "pending" -> "No registrations waiting."; "all" -> "No partners yet. Share the registration link or add one."; else -> "No partners here." }, color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(36.dp))
            rows.forEach { r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitCard).clickable { open = r.optInt("id") }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(b.base.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text(r.optString("name").trim().take(1).uppercase(), color = b.base, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(r.optString("name"), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(r.str("code"), r.str("firm"), r.str("mobile")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (tab == "all") Text(r.optString("status").replaceFirstChar { it.uppercase() }, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = statusColor(r.optString("status")), modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp)).background(statusColor(r.optString("status")).copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 3.dp))
                        r.str("level")?.let { Text(it, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(levelColor(it)).padding(horizontal = 9.dp, vertical = 4.dp)) }
                    }
                    if (r.optString("status") == "approved") Row {
                        Stat("Leads", r.optInt("leads").toString(), Modifier.weight(1f)); Stat("Bookings", r.optInt("bookings").toString(), Modifier.weight(1f))
                        Stat("Business", cr(r.optDouble("business")), Modifier.weight(1.3f)); Stat("Payable", cr(r.optDouble("payable")), Modifier.weight(1.2f))
                    } else Text("Registered ${prettyDay(r.str("registered_at")) ?: ""}", color = KitMuted, fontSize = 12.sp)
                }
            }
        }

        // the registration link — at the bottom of the list
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Registration link", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp)
            Text("Send this link to a partner. They register once, you approve, and they get a CP code.", color = KitMuted, fontSize = 12.sp)
            Text(link, color = b.base, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copy", fontWeight = FontWeight.Bold, color = KitInk, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(KitSoft)
                    .clickable { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("CP link", link)); AppToast.show("Link copied") }.padding(vertical = 11.dp))
                Text("Share", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(b.base)
                    .clickable { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Register as a Gavan Group Channel Partner: $link"), "Share the link")) }.padding(vertical = 11.dp))
            }
        }
    }

    if (adding) PartnerForm(api, b, null, onDismiss = { adding = false }, onSaved = { adding = false; attempt++ })
    if (broadcast) UpdateSheet(api, onDismiss = { broadcast = false })
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) { Text(value, fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 13.sp, maxLines = 1); Text(label, color = KitMuted, fontSize = 11.sp) }
}

@Composable
private fun Chip(text: String, on: Boolean, tint: Color, onClick: () -> Unit) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) tint else KitSoft).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp))
}

// ───────────────────────────── leaderboard ─────────────────────────────

@Composable
private fun Leaderboard(api: ApiClient, b: Branding, onOpen: (Int) -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { d = runCatching { withContext(Dispatchers.IO) { api.get("/cp-center/leaderboard") } }.getOrNull() }
    val x = d ?: return
    Text("Annual League ${x.optString("year")} · business this financial year", color = KitMuted, fontSize = 12.sp)
    val rows = x.objs("data")
    if (rows.isEmpty()) Text("No partners yet.", color = KitMuted, modifier = Modifier.padding(30.dp))
    rows.forEach { r ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitCard).clickable { onOpen(r.optInt("id")) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(listOf("🥇", "🥈", "🥉").getOrElse(r.optInt("rank") - 1) { "#${r.optInt("rank")}" }, fontSize = if (r.optInt("rank") <= 3) 20.sp else 13.sp, fontWeight = FontWeight.ExtraBold, color = KitMuted, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
            Column(Modifier.weight(1f)) {
                Text(r.optString("name"), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 14.sp, maxLines = 1)
                Text(listOfNotNull(r.str("code"), r.str("level"), r.str("reward")).joinToString(" · "), color = KitMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) { Text(cr(r.optDouble("business")), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 13.sp); Text("${r.optInt("bookings")} bookings", color = KitMuted, fontSize = 11.sp) }
        }
    }
    val eligible = x.objs("eligible")
    if (eligible.isNotEmpty()) Card("Reward eligibility", "Partners who have reached a level this year") {
        eligible.forEach { e -> Fact(e.optString("name") + " · " + e.optString("level"), e.str("reward")) }
    }
}

// ───────────────────────────── one partner ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PartnerPage(api: ApiClient, b: Branding, id: Int, projectUuid: String?, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var reject by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var pay by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val q = if (projectUuid != null) "?project=$projectUuid" else ""
    LaunchedEffect(id, attempt) { error = null; try { d = withContext(Dispatchers.IO) { api.detail("/cp-center/$id$q") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    val x = d
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("‹ All partners", color = b.base, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onBack() }.padding(vertical = 6.dp, horizontal = 4.dp))
        if (x == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base) else Text(error ?: "", color = KitDanger) }
            return@Column
        }
        val can = x.optJSONObject("can")
        val s = x.optJSONObject("standing") ?: JSONObject()
        val status = x.optString("status")
        fun act(path: String, body: JSONObject = JSONObject(), ok: String) {
            scope.launch {
                try { val r = withContext(Dispatchers.IO) { api.send("POST", path, body) }; AppToast.show(r.optString("message", ok)); attempt++; onChanged() } catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
            }
        }

        // header
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(x.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk, modifier = Modifier.weight(1f))
                Text(status.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = statusColor(status), modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(statusColor(status).copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp))
                if (can?.optBoolean("edit") == true) RoundIcon("✎", KitInk, KitSoft) { edit = true }
                if (can?.optBoolean("delete") == true) RoundIcon("🗑", KitDanger, KitDanger.copy(alpha = .12f)) { askDelete = true }
            }
            Text(listOfNotNull(x.str("code") ?: "No CP code yet", x.str("firm")).joinToString(" · "), color = KitMuted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                x.str("mobile")?.let { com.example.gavanhomes.ui.tasks.CallChip(it) }
            }
            if (status == "rejected") x.str("rejected_reason")?.let { Text("Rejected: $it", color = KitDanger, fontSize = 12.sp) }
            // approval
            if (can?.optBoolean("approve") == true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (status == "pending" || status == "rejected") Action("✓ Approve", KitGood) { act("/cp-center/$id/approve", ok = "Approved") }
                if (status == "pending") Action("Reject", KitDanger) { reject = true; reason = "" }
                if (status == "approved") Action("Deactivate", KitMuted) { act("/cp-center/$id/deactivate", ok = "Deactivated") }
                if (status == "inactive") Action("Reactivate", KitGood) { act("/cp-center/$id/reactivate", ok = "Active") }
            }
        }

        if (status == "approved" || status == "inactive") {
            // the Annual League
            val level = s.optJSONObject("level"); val next = s.optJSONObject("next_level")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Annual League ${s.optString("year")}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(level?.optString("name") ?: "Below Bronze", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(levelColor(level?.optString("name"))).padding(horizontal = 14.dp, vertical = 6.dp))
                    Text(if (level != null) "  ${level.optDouble("pct")}% brokerage" else "  no brokerage yet", color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                level?.str("reward")?.let { Text("Reward: $it", color = KitMuted, fontSize = 12.sp) }
                val yearBusiness = s.optDouble("year_business")
                if (next != null) {
                    val from = next.optDouble("from")
                    ProBar((yearBusiness / from).toFloat().coerceIn(0f, 1f), levelColor(next.optString("name")))
                    Text("${cr(next.optDouble("remaining"))} more to reach ${next.optString("name")} (${next.optDouble("pct")}%)", color = KitInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else Text("Top level reached", color = KitGood, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { Stat("Leads given", s.optInt("leads").toString(), Modifier.weight(1f)); Stat("Site visits", s.optInt("site_visits").toString(), Modifier.weight(1f)); Stat("Bookings", s.optInt("bookings").toString(), Modifier.weight(1f)) }
                Row { Stat("Business", cr(s.optDouble("business")), Modifier.weight(1f)); Stat("Earned", cr(s.optDouble("earned")), Modifier.weight(1f)); Stat("Paid", cr(s.optDouble("paid")), Modifier.weight(1f)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(cr(s.optDouble("payable")), fontWeight = FontWeight.ExtraBold, color = InvAmber, fontSize = 18.sp); Text("Commission payable", color = KitMuted, fontSize = 11.sp) }
                    if (can?.optBoolean("commission") == true) Text("+ Record payment", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(b.base).clickable { pay = true }.padding(horizontal = 14.dp, vertical = 10.dp))
                }
                Text("Business counts bookings of this partner's leads that have received their agreement-stage payment (at most 20% of the sale). A cancelled booking is taken off automatically.", color = KitMuted, fontSize = 11.sp)
            }
            val st = x.objs("statement")
            if (st.isNotEmpty()) Card("Commission statement", "Month by month") {
                st.forEach { m -> Fact(m.optString("month") + " · ${m.optInt("bookings")} booking${if (m.optInt("bookings") == 1) "" else "s"} · ${cr(m.optDouble("value"))} @ ${m.optDouble("pct")}%", "payable ${cr(m.optDouble("payable"))} · paid ${cr(m.optDouble("paid"))} · balance ${cr(m.optDouble("balance"))}") }
            }
            val pays = x.objs("payments")
            if (pays.isNotEmpty()) Card("Brokerage paid") { pays.forEach { p -> Fact((prettyDay(p.str("date")) ?: "") + (p.str("mode")?.let { " · $it" } ?: "") + (p.str("reference")?.let { " · $it" } ?: ""), cr(p.optDouble("amount"))) } }
            val leads = x.objs("recent_leads")
            if (leads.isNotEmpty()) Card("Leads given", "Newest first") { leads.forEach { l -> Fact(l.optString("name") + " · " + (l.str("status") ?: "New"), (l.str("at") ?: "") + if (l.optBoolean("booked")) " · Booked" else "") } }
        }

        Card("Details") {
            Fact("Mobile", x.str("mobile") + if (x.optBoolean("whatsapp")) " (WhatsApp)" else ""); Fact("Second mobile", x.str("mobile2")); Fact("Email", x.str("email"))
            Fact("Works in", x.optJSONArray("verticals")?.let { a -> (0 until a.length()).joinToString(", ") { a.getString(it).replaceFirstChar { c -> c.uppercase() } } }); Fact("Experience", x.optInt("experience_years").takeIf { it > 0 }?.let { "$it years" })
            Fact("PAN", x.str("pan")); Fact("GST", x.str("gst")); Fact("RERA agent no.", x.str("rera_agent_no"))
            x.optJSONObject("bank")?.let { bk -> Fact("Bank", listOfNotNull(bk.str("bank"), bk.str("account"), bk.str("ifsc")).joinToString(" · ")); Fact("Account holder", bk.str("holder")) }
            Fact("WhatsApp updates", if (x.optBoolean("notify_optin")) "On" else "Opted out")
            Fact("Registered", prettyDay(x.str("registered_at")) + " (" + x.optString("registered_via") + ")")
            listOf("photo" to "Photo", "visiting_card" to "Visiting card").forEach { (k, l) -> x.str(k)?.let { u -> Text("View $l  ›", color = b.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u))) } }.padding(vertical = 5.dp)) } }
        }
        Spacer(Modifier.height(10.dp))

        if (askDelete) com.example.gavanhomes.ui.common.SwalConfirm(
            title = "Delete " + x.optString("name") + "?",
            text = if (x.optInt("lead_count") > 0) "This partner has ${x.optInt("lead_count")} lead(s), so it can't be deleted. Deactivate it instead." else "The partner and its registration are removed. This can't be undone from the app.",
            confirmText = if (x.optInt("lead_count") > 0) "OK" else "Yes, delete",
            onCancel = { askDelete = false },
            onConfirm = {
                askDelete = false
                if (x.optInt("lead_count") == 0) scope.launch {
                    try { val r = withContext(Dispatchers.IO) { api.delete("/cp-center/$id") }; AppToast.show(r.optString("message", "Deleted")); onChanged(); onBack() }
                    catch (e: Exception) { AppToast.show(e.message ?: "Could not delete") }
                }
            },
        )
        if (reject) ModalBottomSheet(onDismissRequest = { reject = false }, containerColor = KitPage) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Reject this registration?", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Input("Reason *", reason, { reason = it }, singleLine = false)
                Text("Reject", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (reason.isBlank()) Color(0xFFC9C7C2) else KitDanger).clickable(enabled = reason.isNotBlank()) { reject = false; act("/cp-center/$id/reject", JSONObject().put("reason", reason.trim()), "Rejected") }.padding(vertical = 14.dp))
            }
        }
        if (pay) PaymentSheet(api, id, s.optDouble("payable"), onDismiss = { pay = false }, onSaved = { pay = false; attempt++; onChanged() })
        if (edit) PartnerForm(api, b, x, onDismiss = { edit = false }, onSaved = { edit = false; attempt++; onChanged() })
    }
}

/** A small round icon button (edit, delete) for a page header. */
@Composable
private fun RoundIcon(glyph: String, tint: Color, bg: Color, onClick: () -> Unit) {
    Box(Modifier.padding(start = 8.dp).size(36.dp).clip(CircleShape).background(bg).clickable { onClick() }, contentAlignment = Alignment.Center) { Text(glyph, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Action(text: String, tint: Color, onClick: () -> Unit) {
    Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(tint).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp))
}

// ───────────────────────────── sheets ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentSheet(api: ApiClient, id: Int, payable: Double, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(if (payable > 0) payable.toLong().toString() else "") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var mode by remember { mutableStateOf("NEFT") }
    var ref by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = KitPage) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Record brokerage paid", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Input("Amount (₹)", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
            Input("Date (yyyy-mm-dd)", date, { date = it })
            Input("Mode (NEFT, cheque, UPI…)", mode, { mode = it }); Input("Reference / UTR", ref, { ref = it })
            Text(if (busy) "Saving…" else "Save payment", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (amount.toDoubleOrNull() ?: 0.0 > 0 && !busy) KitGood else Color(0xFFC9C7C2)).clickable(enabled = (amount.toDoubleOrNull() ?: 0.0) > 0 && !busy) {
                    busy = true
                    scope.launch {
                        try { withContext(Dispatchers.IO) { api.send("POST", "/cp-center/$id/payments", JSONObject().put("amount", amount.toDouble()).put("paid_on", date).put("mode", mode).put("reference", ref)) }; AppToast.show("Payment recorded"); onSaved() }
                        catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
                        busy = false
                    }
                }.padding(vertical = 14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSheet(api: ApiClient, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = KitPage) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("WhatsApp update to partners", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Text("Goes to every approved partner with WhatsApp who has not opted out. A link to stop the updates is added.", color = KitMuted, fontSize = 12.sp)
            Input("Message", text, { text = it }, singleLine = false)
            Text(if (busy) "Sending…" else "Send to all", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (text.isBlank() || busy) Color(0xFFC9C7C2) else KitInk).clickable(enabled = text.isNotBlank() && !busy) {
                    busy = true
                    scope.launch {
                        try { val r = withContext(Dispatchers.IO) { api.send("POST", "/cp-center/broadcast", JSONObject().put("message", text.trim())) }; AppToast.show(r.optString("message")); onDismiss() }
                        catch (e: Exception) { AppToast.show(e.message ?: "Could not send") }
                        busy = false
                    }
                }.padding(vertical = 14.dp))
        }
    }
}

/** Add a partner by hand (approved straight away) or edit one. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PartnerForm(api: ApiClient, b: Branding, src: JSONObject?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val f = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().also { m -> src?.let { s -> listOf("name", "mobile", "mobile2", "email", "pan", "gst", "rera_agent_no").forEach { k -> s.str(k)?.let { v -> m[k] = v } }; s.str("firm")?.let { m["firm_name"] = it }; s.optInt("experience_years").takeIf { it > 0 }?.let { m["experience_years"] = it.toString() }
        s.optJSONObject("bank")?.let { bk -> bk.str("holder")?.let { m["bank_account_name"] = it }; bk.str("bank")?.let { m["bank_name"] = it }; bk.str("account")?.let { m["bank_account_no"] = it }; bk.str("ifsc")?.let { m["bank_ifsc"] = it } } } } }
    var wa by remember { mutableStateOf(src?.optBoolean("whatsapp", true) ?: true) }
    var verticals by remember { mutableStateOf(src?.optJSONArray("verticals")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = KitPage) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Text(if (src == null) "Add a Channel Partner" else "Edit partner", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                @Composable fun field(k: String, l: String, kb: KeyboardType = KeyboardType.Text) = Input(l, f[k].orEmpty(), { f[k] = if (kb == KeyboardType.Phone) it.filter(Char::isDigit).take(10) else it }, errors[k], keyboard = kb)
                field("name", "Name *"); field("firm_name", "Firm name"); field("mobile", "Mobile *", KeyboardType.Phone)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("This number is on WhatsApp", color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(wa, { wa = it }, colors = SwitchDefaults.colors(checkedTrackColor = KitGood, checkedThumbColor = Color.White)) }
                field("mobile2", "Second mobile", KeyboardType.Phone); field("email", "Email", KeyboardType.Email)
                Text("WORKS IN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("residential", "plots", "industrial", "commercial").forEach { v -> Chip(v.replaceFirstChar { it.uppercase() }, v in verticals, b.base) { verticals = if (v in verticals) verticals - v else verticals + v } }
                }
                field("experience_years", "Years of experience", KeyboardType.Number); field("pan", "PAN"); field("gst", "GST (optional)"); field("rera_agent_no", "RERA agent no.")
                field("bank_account_name", "Bank account holder"); field("bank_name", "Bank"); field("bank_account_no", "Account number", KeyboardType.Number); field("bank_ifsc", "IFSC")
                error?.let { Text(it, color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
            Text(if (busy) "Saving…" else "Save", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (busy) KitMuted else b.base).clickable(enabled = !busy) {
                    busy = true; error = null; errors = emptyMap()
                    val body = JSONObject(); f.forEach { (k, v) -> if (v.isNotBlank()) body.put(k, if (k == "experience_years") (v.toIntOrNull() ?: 0) else if (k == "pan" || k == "bank_ifsc") v.uppercase() else v) }
                    body.put("mobile_whatsapp", wa).put("verticals", org.json.JSONArray(verticals.toList()))
                    scope.launch {
                        try { withContext(Dispatchers.IO) { if (src == null) api.send("POST", "/cp-center", body) else api.send("PUT", "/cp-center/${src.optInt("id")}", body) }; AppToast.show("Saved"); onSaved() }
                        catch (e: com.example.gavanhomes.data.ApiException) { errors = e.errors; error = e.errors.values.firstOrNull() ?: e.message }
                        catch (e: Exception) { error = e.message ?: "Could not save" }
                        busy = false
                    }
                }.padding(vertical = 15.dp))
        }
    }
}
