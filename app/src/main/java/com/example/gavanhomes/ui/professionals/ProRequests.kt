package com.example.gavanhomes.ui.professionals

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.mutableStateListOf
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
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.Downloads
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.KitSoft
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.inventory.DatePick
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.readPicked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/*
 * The Professional app's requests, from the admin app (spec v2): the dashboard cards, "New request" in one screen, and a request with its versions,
 * counts, access log, comments and the Approve / Rework / Cancel actions. Data: /professional-requests/…  (review: /professional-reviews/{id}/review)
 */

private val RqOrange = Color(0xFFD9822B)
private val RqPurple = Color(0xFF6A4FB3)
private fun rqTint(key: String?): Color = when (key) { "in_review" -> RqPurple; "rework" -> KitDanger; "approved" -> KitGood; "cancelled" -> KitMuted; else -> RqOrange }

private fun obj(a: JSONArray?): List<JSONObject> = a?.let { (0 until it.length()).map { i -> it.getJSONObject(i) } } ?: emptyList()
private fun JSONObject.rows(key: String) = obj(optJSONArray(key))

// ───────────────────────────── dashboard cards ─────────────────────────────

/** Open · Overdue · To review · Avg reply, the review queue, and each professional's open / late / on-time %. */
internal fun LazyListScope.requestsDashboard(api: ApiClient, r: JSONObject, b: Branding, onOpenRequest: (Int) -> Unit, onOpenPro: (String) -> Unit, onSection: (Int) -> Unit) {
    item {
        Spacer(Modifier.height(14.dp))
        ProKpiGrid(listOf(
            Triple(r.optInt("open").toString(), "Open", KitInk), Triple(r.optInt("overdue").toString(), "Overdue", KitDanger),
            Triple(r.optInt("to_review").toString(), "To review", RqPurple), Triple(r.opt("avg_reply_days")?.takeIf { it != JSONObject.NULL }?.let { "${r.optDouble("avg_reply_days")}d" } ?: "—", "Avg reply", KitGood),
        ), columns = 4)
    }
    val queue = r.rows("queue")
    item {
        ProCard("Waiting for your review", if (queue.isEmpty()) "Nothing is waiting" else "${queue.size} submission${if (queue.size == 1) "" else "s"}") {
            queue.forEach { q ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenRequest(q.optInt("id")) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(q.optString("name") + " · " + (q.str("professional") ?: ""), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(q.str("project"), q.optInt("version").takeIf { it > 0 }?.let { "v$it" }, q.opt("waiting_days")?.takeIf { it != JSONObject.NULL }?.let { d -> if (q.optInt("waiting_days") == 0) "today" else "${d} days" }).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                    }
                    Text("Review", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(RqPurple).padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
        }
    }
    item { BillsCard(api, b) }
    val pros = r.rows("professionals")
    item {
        ProCard("Professionals", "Open · late · on-time % (submitted by the due date)") {
            if (pros.isEmpty()) Text("No requests have been sent yet.", color = KitMuted, fontSize = 14.sp)
            pros.forEach { p ->
                val pct = if (p.isNull("on_time_pct")) null else p.optInt("on_time_pct")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenPro(p.optString("uuid")) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Initials(p.optString("name"), 36.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(p.optString("name"), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(p.str("type"), p.opt("avg_reply_days")?.takeIf { it != JSONObject.NULL }?.let { "reply ${p.optDouble("avg_reply_days")}d" }, "rework ${p.optInt("rework_rate")}%").joinToString(" · "), color = KitMuted, fontSize = 11.sp)
                    }
                    Text(p.optInt("open").toString(), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.widthIn(min = 34.dp), textAlign = TextAlign.Center)
                    Text(p.optInt("late").toString(), color = if (p.optInt("late") > 0) KitDanger else KitMuted, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.widthIn(min = 34.dp), textAlign = TextAlign.Center)
                    Text(pct?.let { "$it%" } ?: "—", color = when { pct == null -> KitMuted; pct >= 90 -> KitGood; pct >= 70 -> RqOrange; else -> KitDanger }, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.widthIn(min = 46.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

// ───────────────────────────── new request ─────────────────────────────

private class Ref(val name: String, val mime: String, val bytes: ByteArray)

@Composable
private fun Sel(label: String, on: Boolean, c: Color = Color(0xFF0E8A50), onClick: () -> Unit) {
    Text(label, color = if (on) Color.White else KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) c else KitSoft).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), color = KitMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.6.sp)
        content()
    }
}

/** One screen to send a request: who, what kind, what, details, formats, due, priority, reference files, where to save, who reviews. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProRequestScreen(api: ApiClient, b: Branding, onBack: () -> Unit, onSent: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var o by remember { mutableStateOf<JSONObject?>(null) }
    var folders by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var project by remember { mutableStateOf<JSONObject?>(null) }
    var pro by remember { mutableStateOf<Int?>(null) }
    var type by remember { mutableStateOf("drawing") }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    val formats = remember { mutableStateListOf("pdf") }
    var dueKey by remember { mutableStateOf("tomorrow") }
    var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var priority by remember { mutableStateOf("Normal") }
    var reviewer by remember { mutableStateOf<Int?>(null) }
    var folder by remember { mutableStateOf<JSONObject?>(null) }
    val checklist = remember { mutableStateListOf<String>() }
    var newCheck by remember { mutableStateOf("") }
    val refs = remember { mutableStateListOf<Ref>() }
    var picking by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { try { o = withContext(Dispatchers.IO) { api.detail("/professional-requests/options") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    LaunchedEffect(project?.optInt("id")) {
        folder = null
        val pid = project?.optInt("id") ?: return@LaunchedEffect
        folders = runCatching { withContext(Dispatchers.IO) { api.detail("/professional-requests/options?project_id=$pid") }.rows("folders") }.getOrDefault(emptyList())
    }
    LaunchedEffect(o) { if (reviewer == null) reviewer = o?.optInt("me")?.takeIf { it > 0 } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { u -> readPicked(context, u, 50)?.let { f -> refs.add(Ref(f.name, f.mime, f.bytes)) } }
    }
    val x = o
    Column(Modifier.fillMaxSize().background(KitPage).imePadding()) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            HeroBar(b, "New request", onBack) {
                Column {
                    Text("Request from professional", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    HeroChips(project?.optString("name") ?: "Pick a project")
                }
            }
            if (x == null) { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base) else Text(error ?: "", color = KitDanger) }; return@Column }
            val pros = x.rows("professionals")
            ProCard("The request") {
                Section("Project") { Sel(project?.optString("name") ?: "Choose project  ▾", project != null, b.base) { picking = "project" } }
                Section("To") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { pros.forEach { p -> Sel(p.optString("name") + (p.str("type")?.let { " ($it)" } ?: ""), pro == p.optInt("id"), b.base) { pro = p.optInt("id") } } } }
                Section("Type of request") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { x.rows("types").forEach { t -> Sel(t.optString("label"), type == t.optString("key"), b.base) { type = t.optString("key") } } } }
                Input("What do you need? *", title, { title = it; problem = null })
                Input("Details / note", details, { details = it }, singleLine = false)
                Section("Files needed") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { x.rows("formats").forEach { f -> val k = f.optString("key"); Sel(f.optString("label"), k in formats, b.base) { if (k in formats) formats.remove(k) else formats.add(k) } } } }
                Section("Due") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("tomorrow" to "Tomorrow", "3" to "3 days", "7" to "1 week", "pick" to "Pick date").forEach { (k, l) ->
                            Sel(l, dueKey == k, b.base) { dueKey = k; when (k) { "tomorrow" -> dueDate = LocalDate.now().plusDays(1).toString(); "3" -> dueDate = LocalDate.now().plusDays(3).toString(); "7" -> dueDate = LocalDate.now().plusDays(7).toString() } }
                        }
                    }
                    if (dueKey == "pick") DatePick("Due date", dueDate, null) { if (it.isNotBlank()) dueDate = it } else Text("Due " + (prettyDate(dueDate) ?: dueDate), color = KitMuted, fontSize = 12.sp)
                }
                Section("Priority") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Normal" to Color(0xFF2F6FE4), "High" to RqOrange, "Urgent" to KitDanger).forEach { (l, c) -> Sel(l, priority == l, c) { priority = l } } } }
            }
            ProCard("What the professional gets") {
                Section("Reference files for the professional") {
                    refs.forEachIndexed { i, f -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Text(f.name, color = KitInk, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text("Remove", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable { refs.removeAt(i) }.padding(6.dp)) } }
                    Sel(if (refs.isEmpty()) "+ add files" else "+ add more", false) { pick.launch("*/*") }
                }
                Section("Save submitted file in") {
                    Sel(folder?.optString("path")?.let { (project?.optString("name") ?: "") + " › " + it } ?: ((project?.optString("name") ?: "Project") + " › Professionals › " + (x.rows("types").firstOrNull { it.optString("key") == type }?.optString("label") ?: "Other") + "  (automatic)"), true, b.base) { if (project != null) picking = "folder" else AppToast.show("Pick the project first.") }
                }
                Section("Reviewer (approve / rework)") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { x.rows("reviewers").forEach { r -> Sel(r.optString("name"), reviewer == r.optInt("id"), b.base) { reviewer = r.optInt("id") } } } }
                Section("Checklist the professional ticks before submitting (optional)") {
                    checklist.forEachIndexed { i, c -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("☐  $c", color = KitInk, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("✕", color = KitDanger, modifier = Modifier.clickable { checklist.removeAt(i) }.padding(6.dp)) } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Input("e.g. Signed / stamped copy", newCheck, { newCheck = it }) }
                        Text("Add", color = b.base, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { if (newCheck.isNotBlank()) { checklist.add(newCheck.trim()); newCheck = "" } }.padding(12.dp))
                    }
                }
                Section("Reminders") { Text("1 day before and on the due date — in the app and on WhatsApp.", color = KitMuted, fontSize = 13.sp) }
            }
            problem?.let { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.fillMaxWidth().background(KitCard).navigationBarsPadding().padding(16.dp)) {
            Text(if (saving) "Sending…" else "Send request", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving && x != null) {
                    when {
                        project == null -> problem = "Pick the project."
                        pro == null -> problem = "Choose who the request goes to."
                        title.isBlank() -> problem = "Say what you need."
                        formats.isEmpty() -> problem = "Choose at least one file format."
                        reviewer == null -> problem = "Choose the reviewer."
                        else -> {
                            saving = true; problem = null
                            val parts = mutableListOf(FormPart("project_id", project!!.optInt("id").toString()), FormPart("professional_id", pro.toString()), FormPart("request_type", type), FormPart("title", title.trim()),
                                FormPart("due_date", dueDate), FormPart("priority", priority), FormPart("reviewer_id", reviewer.toString()))
                            if (details.isNotBlank()) parts += FormPart("details", details.trim())
                            formats.forEach { parts += FormPart("formats[]", it) }
                            checklist.forEach { parts += FormPart("checklist[]", it) }
                            folder?.let { parts += FormPart("folder_id", it.optInt("id").toString()) }
                            refs.forEach { parts += FormPart("reference_files[]", fileName = it.name, mime = it.mime, bytes = it.bytes) }
                            scope.launch {
                                try { val r = withContext(Dispatchers.IO) { api.sendForm("/professional-requests", parts) }; AppToast.show(r.str("message") ?: "Request sent"); onSent() }
                                catch (e: ApiException) { problem = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message }
                                catch (e: Exception) { problem = e.message ?: "Could not send" }
                                saving = false
                            }
                        }
                    }
                }.padding(vertical = 15.dp))
        }
    }
    when (picking) {
        "project" -> ChoiceSheet("Project", (x?.rows("projects") ?: emptyList()).map { Choice(it.optInt("id").toString(), it.optString("name")) }, project?.optInt("id")?.toString(), b, onClose = { picking = null }) { id -> project = x?.rows("projects")?.firstOrNull { it.optInt("id").toString() == id }; picking = null }
        "folder" -> ChoiceSheet("Save in folder", folders.map { Choice(it.optInt("id").toString(), it.optString("path")) }, folder?.optInt("id")?.toString(), b, allowNone = true, noneLabel = "Automatic (Professionals › type)", onClose = { picking = null }) { id -> folder = folders.firstOrNull { it.optInt("id").toString() == id }; picking = null }
    }
}

// ───────────────────────────── one request ─────────────────────────────

/** A request: versions, downloads, views; locked; the access log (who, what, when); comments; Approve / Rework with a comment; Cancel. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProRequestDetailScreen(api: ApiClient, b: Branding, id: Int, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var approve by remember { mutableStateOf(false) }
    var rework by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    var pinPage by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(id, attempt) { error = null; try { d = withContext(Dispatchers.IO) { api.detail("/professional-requests/$id") } } catch (e: Exception) { if (d == null) error = e.message ?: "Could not load the request" } }
    fun open(file: JSONObject, projectUuid: String?) {
        val fid = file.optInt("id"); opening = fid
        scope.launch {
            try {
                val (served, bytes) = withContext(Dispatchers.IO) { api.download("/projects/$projectUuid/file-center/files/$fid/download") }
                runCatching { withContext(Dispatchers.IO) { api.send("POST", "/projects/$projectUuid/file-center/files/$fid/view", JSONObject()) } }
                val name = if (served == "export") file.optString("name") else served
                val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
                val uri = withContext(Dispatchers.IO) { Downloads.save(context, name, mime, bytes) }
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { AppToast.show("Saved to Downloads/GavanHomes") }
                attempt++
            } catch (e: Exception) { AppToast.show(e.message ?: "Could not open the file") }
            opening = null
        }
    }
    fun decide(decision: String, text: String?, page: String? = null, fail: (String) -> Unit = {}) {
        busy = true
        scope.launch {
            try {
                val body = JSONObject().put("decision", decision)
                if (text != null) body.put("review_comments", text)
                if (!page.isNullOrBlank()) body.put("pin_page", page.toIntOrNull() ?: 0)
                withContext(Dispatchers.IO) { api.send("POST", "/professional-reviews/$id/review", body) }
                AppToast.show(if (decision == "approved") "Approved — the professional is told" else "Sent back for rework"); onDone()
            } catch (e: ApiException) { fail(e.errors["review_comments"] ?: e.message ?: "Could not save") } catch (e: Exception) { fail(e.message ?: "Could not save") }
            busy = false
        }
    }
    val x = d
    if (x == null) { ProLoading(b, "Request", error, onBack) { attempt++ }; return }
    val can = x.optJSONObject("can")
    val key = x.optString("key")
    val projectUuid = x.str("project_uuid")
    Column(Modifier.fillMaxSize().background(KitPage)) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            HeroBar(b, "File", onBack) {
                Column {
                    Text(x.optString("name"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("project"), x.str("professional")?.let { "from $it" }).joinToString(" · "), color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    HeroChips(*listOfNotNull(x.optInt("version").takeIf { it > 0 }?.let { "v$it latest" }, x.optString("status_label"), if (x.optBoolean("locked")) "Locked" else null, if (x.optBoolean("late")) x.optString("due_label") else null).toTypedArray())
                }
            }
            val t = x.optJSONObject("totals")
            ProCard("Versions · downloads · views") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ProCounter((t?.optInt("versions") ?: 0).toString(), "Versions", Modifier.weight(1f), big = true)
                    ProCounter((t?.optInt("downloads") ?: 0).toString(), "Downloads", Modifier.weight(1f), big = true)
                    ProCounter((t?.optInt("views") ?: 0).toString(), "Views", Modifier.weight(1f), big = true)
                }
                if (x.optBoolean("locked")) Text("🔒 Locked: nobody can delete. Changes = new version.", color = KitGood, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            ProCard("The request") {
                x.str("description")?.let { Text(it, color = KitInk, fontSize = 14.sp, lineHeight = 20.sp) }
                ProFact("Type", x.str("type")); ProFact("Formats", x.strings("formats").joinToString(" + ") { it.uppercase() }.ifBlank { null }); ProFact("Requested by", x.str("requested_by")); ProFact("Due", prettyDate(x.str("due_date")))
                ProFact("Reviewer", x.str("reviewer")); ProFact("Saved in", x.str("save_to"))
                x.strings("checklist").takeIf { it.isNotEmpty() }?.let { ProFact("Checklist", it.joinToString(", ")) }
                x.str("cancel_reason")?.let { Text("Cancelled: $it", color = KitMuted, fontSize = 13.sp) }
            }
            val versions = x.rows("versions")
            ProCard("Versions", "Every file is kept") {
                if (versions.isEmpty()) Text("Nothing has been uploaded yet.", color = KitMuted, fontSize = 14.sp)
                versions.forEach { v ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitSoft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("v${v.optInt("version")}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            val c = when (v.optString("status")) { "In review" -> RqPurple; "Rework" -> KitDanger; "Approved" -> KitGood; "Draft" -> RqOrange; else -> KitMuted }
                            Pill(v.optString("status"), c.copy(alpha = .14f), c)
                        }
                        v.rows("files").forEach { f -> Row(Modifier.fillMaxWidth().clickable { open(f, projectUuid) }.padding(vertical = 3.dp)) { Text(f.optString("name"), color = b.base, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(if (opening == f.optInt("id")) "…" else "Open", color = KitMuted, fontSize = 11.sp) } }
                        v.str("note")?.let { Text("“$it”", color = KitInk, fontSize = 12.sp) }
                        Text(listOfNotNull(prettyDate(v.str("date")), "↓ ${v.optInt("downloads")}", "${v.optInt("views")} views").joinToString(" · "), color = KitMuted, fontSize = 11.sp)
                    }
                }
                val ref = x.rows("reference")
                if (ref.isNotEmpty()) { Text("Reference files for the professional", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 13.sp); ref.forEach { f -> Text(f.optString("name"), color = b.base, fontSize = 12.sp, modifier = Modifier.clickable { open(f, projectUuid) }.padding(vertical = 2.dp)) } }
            }
            val log = x.rows("log")
            ProCard("Access log", "Every view, download, upload and decision") {
                if (log.isEmpty()) Text("Nothing yet.", color = KitMuted, fontSize = 14.sp)
                log.take(25).forEach { l -> Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(l.optString("who"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(.9f)); Text(l.optString("what"), color = KitMuted, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(prettyDate(l.str("at")) ?: "", color = KitMuted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(.8f)) } }
            }
            val comments = x.rows("comments")
            ProCard("Comments", if (comments.isEmpty()) "No comments yet" else "${comments.size}") {
                comments.forEach { c ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (c.optBoolean("pro")) b.base.copy(alpha = .1f) else KitSoft).padding(10.dp)) {
                        Text(c.optString("by") + " · " + (prettyDate(c.str("at")) ?: ""), color = if (c.optBoolean("pro")) b.base else KitMuted, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        Text(c.optString("text"), color = KitInk, fontSize = 13.sp)
                        if (c.optInt("pin_version") > 0) Text("Pinned to v${c.optInt("pin_version")}" + (c.optInt("pin_page").takeIf { it > 0 }?.let { " · page $it" } ?: "") + if (c.optBoolean("resolved")) " · ✓ resolved" else "", color = RqPurple, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (can?.optBoolean("comment") == true) {
                    Input("Comment for the professional…", comment, { comment = it }, singleLine = false)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { Input("Page (optional)", pinPage, { pinPage = it.filter { ch -> ch.isDigit() } }, keyboard = androidx.compose.ui.text.input.KeyboardType.Number) }
                        Text("Send", color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (comment.isBlank()) KitMuted else b.base).clickable(enabled = comment.isNotBlank()) {
                            scope.launch {
                                try {
                                    val body = JSONObject().put("content", comment.trim()); x.rows("versions").firstOrNull()?.let { body.put("pin_version", it.optInt("version")) }; if (pinPage.isNotBlank()) body.put("pin_page", pinPage.toInt())
                                    withContext(Dispatchers.IO) { api.send("POST", "/professional-requests/$id/comments", body) }; comment = ""; pinPage = ""; attempt++
                                } catch (e: Exception) { AppToast.show(e.message ?: "Could not send") }
                            }
                        }.padding(horizontal = 20.dp, vertical = 12.dp))
                    }
                }
            }
            if (can?.optBoolean("cancel") == true) Text("Cancel this request", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .08f)).clickable { cancel = true }.padding(vertical = 13.dp))
            Spacer(Modifier.height(16.dp))
        }
        if (can?.optBoolean("review") == true) Row(Modifier.fillMaxWidth().background(KitCard).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Rework", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(KitDanger.copy(alpha = .1f)).clickable(enabled = !busy) { rework = true }.padding(top = 17.dp))
            Text(if (busy) "Saving…" else "Approve", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(KitGood).clickable(enabled = !busy) { approve = true }.padding(top = 17.dp))
        }
    }
    if (approve) SwalConfirm(title = "Approve this version?", text = "The request is closed and the professional is told.", confirmText = "Yes, approve", confirmColor = KitGood, onCancel = { approve = false }, onConfirm = { approve = false; decide("approved", null) { AppToast.show(it) } })
    if (rework) {
        var text by remember { mutableStateOf("") }
        var page by remember { mutableStateOf("") }
        var problem by remember { mutableStateOf<String?>(null) }
        ModalBottomSheet(onDismissRequest = { rework = false }, containerColor = KitCard) {
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Send back for rework", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text("Say what to change — it is pinned to the latest version.", color = KitMuted, fontSize = 13.sp)
                Input("Comment for the professional *", text, { text = it; problem = null }, problem, singleLine = false)
                Input("Page (optional)", page, { page = it.filter { c -> c.isDigit() } }, keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                Text(if (busy) "Saving…" else "Rework", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(KitDanger).clickable(enabled = !busy) {
                        if (text.isBlank()) { problem = "Enter a comment for the professional"; return@clickable }
                        decide("rework", text.trim(), page) { problem = it }
                    }.padding(top = 15.dp))
            }
        }
    }
    if (cancel) {
        var reason by remember { mutableStateOf("") }
        var problem by remember { mutableStateOf<String?>(null) }
        ModalBottomSheet(onDismissRequest = { cancel = false }, containerColor = KitCard) {
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cancel this request", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text("It is no longer needed. The files stay saved.", color = KitMuted, fontSize = 13.sp)
                Input("Reason *", reason, { reason = it; problem = null }, problem, singleLine = false)
                Text(if (busy) "Cancelling…" else "Cancel request", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(KitDanger).clickable(enabled = !busy) {
                        if (reason.isBlank()) { problem = "A reason is required"; return@clickable }
                        busy = true
                        scope.launch {
                            try { withContext(Dispatchers.IO) { api.send("POST", "/professional-requests/$id/cancel", JSONObject().put("reason", reason.trim())) }; AppToast.show("Request cancelled"); onDone() }
                            catch (e: Exception) { problem = e.message ?: "Could not cancel" }
                            busy = false
                        }
                    }.padding(top = 15.dp))
            }
        }
    }
}

// ───────────────────────────── fees: bills waiting for Accounts ─────────────────────────────

/** Bills the professionals raised: approve (Billed → Approved), pay with a UTR (→ Paid) or reject with a reason. TDS is shown separately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BillsCard(api: ApiClient, b: Branding) {
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var paying by remember { mutableStateOf<JSONObject?>(null) }
    var rejecting by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(attempt) { d = runCatching { withContext(Dispatchers.IO) { api.get("/professional-bills?status=billed,approved") } }.getOrNull() }
    val x = d ?: return
    val rows = x.rows("data")
    val can = x.optJSONObject("can")?.optBoolean("act") == true
    val t = x.optJSONObject("totals")
    ProCard("Fees - bills to handle", "${t?.optInt("to_approve") ?: 0} to approve · ${t?.optInt("to_pay") ?: 0} to pay") {
        if (rows.isEmpty()) Text("No bill is waiting.", color = KitMuted, fontSize = 14.sp)
        rows.forEach { r ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KitSoft).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((r.str("no") ?: "Bill") + " · " + (r.str("professional") ?: ""), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val c = if (r.optString("status") == "approved") RqPurple else RqOrange
                    Pill(if (r.optString("status") == "approved") "Approved" else "Billed", c.copy(alpha = .14f), c)
                }
                Text(r.str("request") ?: "", color = KitMuted, fontSize = 12.sp, maxLines = 1)
                Text("₹${"%,.0f".format(r.optDouble("amount"))} + GST ₹${"%,.0f".format(r.optDouble("gst"))} − TDS ₹${"%,.0f".format(r.optDouble("tds"))} = ₹${"%,.0f".format(r.optDouble("payable"))}", color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                if (can) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (r.optString("status") == "billed") Text("Approve", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(KitGood).clickable {
                        scope.launch { try { withContext(Dispatchers.IO) { api.send("POST", "/professional-bills/${r.optInt("id")}/approve", JSONObject()) }; AppToast.show("Bill approved"); attempt++ } catch (e: Exception) { AppToast.show(e.message ?: "Could not approve") } }
                    }.padding(horizontal = 14.dp, vertical = 7.dp))
                    else Text("Pay (UTR)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(b.base).clickable { paying = r }.padding(horizontal = 14.dp, vertical = 7.dp))
                    Text("Reject", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).clickable { rejecting = r }.padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    paying?.let { r ->
        var utr by remember(r) { mutableStateOf("") }
        var problem by remember(r) { mutableStateOf<String?>(null) }
        ModalBottomSheet(onDismissRequest = { paying = null }, containerColor = KitCard) {
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pay bill ${r.optString("no")}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text("₹${"%,.0f".format(r.optDouble("payable"))} to ${r.optString("professional")} (after TDS)", color = KitMuted, fontSize = 13.sp)
                Input("UTR / reference *", utr, { utr = it; problem = null }, problem)
                Text("Record payment", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(b.base).clickable {
                    if (utr.isBlank()) { problem = "Enter the UTR."; return@clickable }
                    scope.launch { try { withContext(Dispatchers.IO) { api.send("POST", "/professional-bills/${r.optInt("id")}/pay", JSONObject().put("utr", utr.trim())) }; AppToast.show("Paid — the professional is told"); paying = null; attempt++ } catch (e: Exception) { problem = e.message ?: "Could not save" } }
                }.padding(vertical = 15.dp))
            }
        }
    }
    rejecting?.let { r ->
        var reason by remember(r) { mutableStateOf("") }
        var problem by remember(r) { mutableStateOf<String?>(null) }
        ModalBottomSheet(onDismissRequest = { rejecting = null }, containerColor = KitCard) {
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reject bill ${r.optString("no")}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Input("Reason *", reason, { reason = it; problem = null }, problem, singleLine = false)
                Text("Reject", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitDanger).clickable {
                    if (reason.isBlank()) { problem = "A reason is required."; return@clickable }
                    scope.launch { try { withContext(Dispatchers.IO) { api.send("POST", "/professional-bills/${r.optInt("id")}/reject", JSONObject().put("reason", reason.trim())) }; AppToast.show("Bill rejected"); rejecting = null; attempt++ } catch (e: Exception) { problem = e.message ?: "Could not save" } }
                }.padding(vertical = 15.dp))
            }
        }
    }
}
