package com.example.gavanhomes.ui.targets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.FormPart
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
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.readPicked
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** "10 Cr", "50 lakh", "1,00,00,000", "₹2.5 L" → a number; null when it is not one. */
internal fun parseAmount(text: String): Double? {
    val t = text.lowercase().replace("₹", "").replace("rs.", "").replace("rs", "").replace(",", "").replace(" ", "")
    val m = Regex("^(-?\\d+(?:\\.\\d+)?)(crore|cr|lakh|lac|l|thousand|k)?$").find(t) ?: return null
    val n = m.groupValues[1].toDouble()
    return when (m.groupValues[2]) { "crore", "cr" -> n * 10_000_000; "lakh", "lac", "l" -> n * 100_000; "thousand", "k" -> n * 1_000; else -> n }
}

/** ₹ in lakh / crore for money, a plain number for counts. */
internal fun amountText(v: Double, unit: String): String = if (unit == "count") String.format(java.util.Locale.US, "%,.0f", v) else when {
    v >= 10_000_000 -> String.format(java.util.Locale.US, "₹%.2f Cr", v / 10_000_000)
    v >= 100_000 -> String.format(java.util.Locale.US, "₹%.2f L", v / 100_000)
    else -> "₹" + String.format(java.util.Locale.US, "%,.0f", v)
}

internal fun healthLabel(s: String?) = when (s) { "achieved" -> "Achieved"; "on_track" -> "On track"; "behind" -> "Behind"; "at_risk" -> "At risk"; else -> "" }
internal fun healthColor(s: String?) = when (s) { "achieved", "on_track" -> KitGood; "behind" -> InvAmber; "at_risk" -> KitDanger; else -> KitMuted }

/**
 * A target's progress: achieved of target, %, days left, what is needed per day, and On track / Behind / At risk; where the figure comes from
 * (typed in, or read from the project's receipts / bookings / visits / leads); the full history of updates; and "+ Update progress" (a value, a note, a photo).
 * Data: POST /target-center/{id}/progress · DELETE …/progress/{entry} · POST …/source.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetProgressCard(api: ApiClient, b: Branding, id: Int, d: JSONObject, canEdit: Boolean, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val info = d.optJSONObject("progress_info") ?: return
    if (info.isNull("goal")) return
    val unit = info.optString("unit", "rupees")
    val goal = info.optDouble("goal"); val achieved = info.optDouble("achieved")
    val status = info.str("status")
    var update by remember { mutableStateOf(false) }
    var sourceSheet by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf(false) }
    val canUpdate = d.optJSONObject("can")?.optBoolean("progress") == true
    val entries = d.objs("progress_entries")

    Card("Progress", info.str("source_label")?.let { "Updates itself from: $it" } ?: "Updated by hand") {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(amountText(achieved, unit), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = healthColor(status))
                Text("of ${amountText(goal, unit)} target", color = KitMuted, fontSize = 13.sp)
            }
            if (status != null) Text(healthLabel(status), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = healthColor(status),
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(healthColor(status).copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 5.dp))
        }
        ProBar(info.optInt("pct") / 100f, healthColor(status))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("${info.optInt("pct")}%", "done", Modifier.weight(1f))
            val left = if (info.isNull("days_left")) null else info.optInt("days_left")
            Tile(left?.let { if (it < 0) "${-it} days over" else "$it days" } ?: "—", if (left != null && left < 0) "overdue" else "left", Modifier.weight(1f), if (left != null && left < 0) KitDanger else KitInk)
            Tile(if (info.isNull("required_per_day")) "—" else amountText(info.optDouble("required_per_day"), unit), "needed / day", Modifier.weight(1f), InvAmber)
        }
        if (info.optDouble("auto") > 0 || info.optDouble("typed") != 0.0) Text(
            buildString { if (info.optDouble("auto") > 0) append("From records ${amountText(info.optDouble("auto"), unit)}"); if (info.optDouble("typed") != 0.0) { if (isNotEmpty()) append(" + "); append("typed in ${amountText(info.optDouble("typed"), unit)}") } },
            color = KitMuted, fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canUpdate) Text("+ Update progress", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(b.base).clickable { update = true }.padding(vertical = 12.dp))
            if (canEdit) Text("Source", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(KitSoft).clickable { sourceSheet = true }.padding(horizontal = 18.dp, vertical = 12.dp))
        }
        if (entries.isNotEmpty()) {
            Text("PROGRESS HISTORY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 4.dp))
            (if (all) entries else entries.take(4)).forEach { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KitSoft).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((prettyDay(e.str("date")) ?: "") + (e.str("by")?.let { " · $it" } ?: ""), color = KitMuted, fontSize = 11.sp)
                        e.str("note")?.let { Text(it, color = KitInk, fontSize = 13.sp) }
                    }
                    Text((if (e.optDouble("value") >= 0) "+" else "") + amountText(e.optDouble("value"), unit), color = if (e.optDouble("value") >= 0) KitGood else KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    if (canEdit) Text("✕", color = KitMuted, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        scope.launch { try { withContext(Dispatchers.IO) { api.delete("/target-center/$id/progress/${e.optInt("id")}") }; AppToast.show("Entry removed"); onChanged() } catch (ex: Exception) { AppToast.show(ex.message ?: "Could not remove") } }
                    }.padding(8.dp))
                }
            }
            if (entries.size > 4) Text(if (all) "Show less" else "Show all ${entries.size}", color = b.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = !all }.padding(6.dp))
        }
    }

    if (update) UpdateSheet(api, id, unit, mandatoryPhoto = d.optBoolean("mandatory_photo_uploads"), onDismiss = { update = false }, onSaved = { update = false; onChanged() })
    if (sourceSheet) ModalBottomSheet(onDismissRequest = { sourceSheet = false }, containerColor = KitPage) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Where does the progress come from?", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Text("Linked to the project's own records, it updates by itself — no one has to type it.", color = KitMuted, fontSize = 12.sp)
            val current = info.str("source")
            (listOf<Pair<String?, String>>(null to "By hand — people update it") + d.objs("sources").map { it.optString("key") to it.optString("label") }).forEach { (key, label) ->
                Text(label, color = if (current == key) Color.White else KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (current == key) b.base else KitSoft).clickable {
                        sourceSheet = false
                        scope.launch { try { val r = withContext(Dispatchers.IO) { api.send("POST", "/target-center/$id/source", JSONObject().put("source", key ?: JSONObject.NULL)) }; AppToast.show(r.optString("message")); onChanged() } catch (ex: Exception) { AppToast.show(ex.message ?: "Could not save") } }
                    }.padding(14.dp))
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier, tint: Color = KitInk) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(KitSoft).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(value, color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
        Text(label, color = KitMuted, fontSize = 11.sp)
    }
}

/** "+ Update progress": how much more is done — "5 L", "50 lakh", "2 Cr" or a plain number — with a note and, if wanted (or required), a photo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSheet(api: ApiClient, id: Int, unit: String, mandatoryPhoto: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<com.example.gavanhomes.ui.projects.form.PickedFile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { photo = readPicked(context, it) } }
    val amount = parseAmount(value)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = KitPage) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Update progress", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Text(if (unit == "count") "How many more are done?" else "How much more is done? Type 50000, 5 L or 2 Cr.", color = KitMuted, fontSize = 12.sp)
            Input("Done now", value, { value = it; error = null }, error, keyboard = KeyboardType.Text)
            if (amount != null) Text("= " + amountText(amount, unit), color = KitGood, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Input("Note (what was done)", note, { note = it }, singleLine = false)
            Text(photo?.let { "📷 ${it.name}" } ?: if (mandatoryPhoto) "Add a photo (required)" else "Add a photo (optional)", color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KitSoft).clickable { pick.launch("image/*") }.padding(14.dp))
            val ok = amount != null && !busy && (!mandatoryPhoto || photo != null)
            Text(if (busy) "Saving…" else "Save progress", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ok) KitGood else Color(0xFFC9C7C2)).clickable(enabled = ok) {
                    busy = true
                    val parts = listOfNotNull(FormPart("value", amount.toString()), FormPart("note", note.trim()), photo?.let { FormPart("photo", fileName = it.name, mime = it.mime, bytes = it.bytes) })
                    scope.launch {
                        try { withContext(Dispatchers.IO) { api.sendForm("/target-center/$id/progress", parts) }; AppToast.show("Progress updated"); onSaved() }
                        catch (e: Exception) { error = e.message ?: "Could not save" }
                        busy = false
                    }
                }.padding(vertical = 14.dp))
        }
    }
}

/** The dashboard's progress card per target: achieved / target, a bar and the On track / Behind / At risk tag. */
@Composable
fun TargetProgressCards(cards: List<JSONObject>, onOpen: (Int) -> Unit) {
    if (cards.isEmpty()) return
    Card("Target progress", "Each open target with a goal") {
        cards.forEach { c ->
            val unit = if (c.str("source")?.let { it == "bookings_count" || it == "site_visits" || it == "leads" } == true) "count" else "rupees"
            val s = c.str("health")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitSoft).clickable { onOpen(c.optInt("id")) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    if (s != null) Text(healthLabel(s), color = healthColor(s), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(healthColor(s).copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                ProBar(c.optInt("pct") / 100f, healthColor(s))
                Text(amountText(c.optDouble("achieved"), unit) + " of " + amountText(c.optDouble("goal"), unit) + " · ${c.optInt("pct")}%" + (if (!c.isNull("days_left")) " · " + (c.optInt("days_left").let { if (it < 0) "${-it} days over" else "$it days left" }) else ""), color = KitMuted, fontSize = 11.sp)
            }
        }
    }
}
