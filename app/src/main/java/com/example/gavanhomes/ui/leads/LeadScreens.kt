package com.example.gavanhomes.ui.leads

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.DeleteButton
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.PillTab
import com.example.gavanhomes.ui.common.PillTabs
import com.example.gavanhomes.ui.common.ScrollTopButton
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.Label
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.PhoneActions
import com.example.gavanhomes.ui.tasks.SendButton
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import com.example.gavanhomes.ui.tasks.statusTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

private val Blue = Color(0xFF1E5FAE)

// ───────────────────────────── lead details ─────────────────────────────

/**
 * One lead, like the website's lead page: **Details** (call / WhatsApp, status, label, who has it, notes), **Follow-ups** (add, edit, change status, delete),
 * **Tasks** (the reminders made from follow-ups) and **Activity** (calls and meetings you log, and every change).
 * Data: GET /lead-center/{id} · POST …/quick · …/followups · …/activities · DELETE /lead-center/{id}.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LeadDetailScreen(api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onOpenTask: (Int) -> Unit, onChanged: () -> Unit, initialTab: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(initialTab) }
    var busy by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }                  // "assign"
    var followupSheet by remember { mutableStateOf<JSONObject?>(null) }      // the follow-up being edited, or an empty object to add
    var statusFor by remember { mutableStateOf<JSONObject?>(null) }
    var deleteFollowup by remember { mutableStateOf<JSONObject?>(null) }
    var activitySheet by remember { mutableStateOf(false) }
    var activityVersion by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(id, version, attempt) {
        error = null
        try {
            val x = withContext(Dispatchers.IO) { api.detail("/lead-center/$id") }
            d = x; notes = x.optString("notes").takeIf { it != "null" }.orEmpty()
            if (options == null) options = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/${x.optString("project_uuid")}/lead-center/options") } }.getOrNull()
        } catch (e: Exception) { error = e.message ?: "Could not load the lead" }
    }
    BackHandler(enabled = true) { onBack() }

    fun act(success: String?, call: () -> Unit, after: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { call() }
                if (success != null) Toast.makeText(context, success, Toast.LENGTH_SHORT).show()
                after(); onChanged(); attempt++; activityVersion++
            } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }
    fun quick(msg: String, vararg pairs: Pair<String, Any?>) = act(msg, { api.send("POST", "/lead-center/$id/quick", JSONObject().apply { pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }) })

    Column(Modifier.fillMaxSize().background(KitPage)) {
        val x = d
        if (x == null) {
            HeroBar(b, "Lead", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = b.base)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
                }
            }
            return@Column
        }
        val canEdit = x.optJSONObject("can")?.optBoolean("edit") == true
        val canDelete = x.optJSONObject("can")?.optBoolean("delete") == true
        val followups = x.objs("followups"); val tasks = x.objs("tasks")
        val pageScroll = rememberScrollState()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize().verticalScroll(pageScroll).imePadding()) {
                HeroBar(
                    b, "Lead", onBack,
                    actions = {
                        if (canEdit) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                        if (canDelete) DeleteButton { askDelete = true }
                    },
                ) {
                    Column {
                        Text(x.optString("name"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(listOfNotNull(x.str("project"), x.str("list")).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        HeroChips(*listOfNotNull(x.str("status") ?: "New", x.str("label"), x.str("source"), if (x.optBoolean("converted")) "Converted" else null).toTypedArray())
                    }
                }

                PillTabs(listOf(PillTab("Details"), PillTab("Follow-ups", followups.size), PillTab("Tasks", tasks.size), PillTab("Activity")), tab, b.base) { tab = it }

                when (tab) {
                    0 -> {
                        Card("Contact") {
                            x.str("phone")?.let { PhoneActions(it) }
                            x.str("alt_phone")?.let { Label("Alternate number"); PhoneActions(it) }
                            Fact("Email", x.str("email"))
                            Fact("Company", x.str("company"))
                        }
                        Card("Status & label", "Where this lead stands") {
                            PickerField("Status", x.str("status") ?: "New") { if (canEdit) sheet = "status" }
                            PickerField("Label", x.str("label")) { if (canEdit) sheet = "label" }
                        }
                        Card("Assigned to") { PickerField("Owner", x.str("assignee")) { if (canEdit) sheet = "assign" } }
                        Card("Notes", "Anything worth remembering") {
                            Input("Notes", notes, { notes = it }, singleLine = false, readOnly = !canEdit)
                            if (canEdit && notes != x.optString("notes").takeIf { it != "null" }.orEmpty()) SendButton("Save notes", true) { quick("Notes saved", "notes" to notes.trim()) }
                        }
                        Card("About this lead") {
                            Fact("Lead list", x.str("list"))
                            Fact("Source", x.str("source"))
                            Fact("Interested in", x.str("interested_in"))
                            Fact("Customer", x.str("customer"))
                            Fact("Created", listOfNotNull(prettyDay(x.str("created_at")), x.str("created_by")?.let { "by $it" }).joinToString(" "))
                        }
                    }

                    1 -> {
                        if (canEdit) Text(
                            "+  Add follow-up", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitInk).clickable { followupSheet = JSONObject() }.padding(vertical = 14.dp),
                        )
                        if (followups.isEmpty()) Text("No follow-ups yet.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                        followups.forEach { f ->
                            FollowupCard(f, options, onStatus = if (f.optBoolean("can_status")) ({ statusFor = f }) else null, onEdit = if (f.optBoolean("can_edit")) ({ followupSheet = f }) else null, onDelete = if (f.optBoolean("can_edit")) ({ deleteFollowup = f }) else null)
                        }
                    }

                    2 -> {
                        if (tasks.isEmpty()) Text("No tasks yet — a follow-up creates one.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                        tasks.forEach { t ->
                            val tc = statusTint(t.optString("status"))
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable { onOpenTask(t.optInt("id")) }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(listOfNotNull(t.str("priority"), prettyDay(t.str("due_date"))?.let { "Due $it" }, t.objs("assignees").takeIf { false }?.let { "" }).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                                }
                                Chip(t.optString("status"), tc)
                            }
                        }
                    }

                    else -> LeadActivity(api, b, id, activityVersion, canEdit, onLog = { activitySheet = true })
                }
                Spacer(Modifier.height(28.dp))
            }
            ScrollTopButton(pageScroll)
        }

        if (sheet == "status") ChoiceSheet("Status", options?.objs("statuses").orEmpty().map { Choice(it.optString("name"), it.optString("name"), tint(it.str("color"))) }, x.str("status"), b, onClose = { sheet = null }) { sheet = null; if (it != x.optString("status")) quick("Status changed", "status" to it) }
        if (sheet == "label") ChoiceSheet("Label", options?.objs("labels").orEmpty().map { Choice(it.optString("name"), it.optString("name"), labelTintOf(options, it.optString("name"))) }, x.str("label"), b, allowNone = true, onClose = { sheet = null }) { sheet = null; if (it != x.optString("label")) quick("Label changed", "label" to it.ifBlank { null }) }
        if (sheet == "assign") ChoiceSheet("Assign to", options?.objs("users").orEmpty().map { Choice(it.optInt("id").toString(), it.optString("name")) }, x.optInt("assigned_to").takeIf { it > 0 }?.toString(), b, allowNone = true, onClose = { sheet = null }) {
            sheet = null; quick("Assigned", "assigned_to" to it.toIntOrNull())
        }
        followupSheet?.let { f -> FollowupSheet(b, options, f, onClose = { followupSheet = null }) { body ->
            followupSheet = null
            if (f.has("id")) act("Follow-up saved", { api.send("PUT", "/lead-center/$id/followups/${f.optInt("id")}", body) })
            else act("Follow-up added", { api.send("POST", "/lead-center/$id/followups", body) })
        } }
        statusFor?.let { f -> StatusSheet(b, options, f, onClose = { statusFor = null }) { status, note ->
            statusFor = null
            act("Follow-up marked ${status}", { api.send("POST", "/lead-center/$id/followups/${f.optInt("id")}/status", JSONObject().put("status", status).put("notes", note)) })
        } }
        if (activitySheet) ActivitySheet(onClose = { activitySheet = false }) { body ->
            activitySheet = false
            act("Activity logged", { api.send("POST", "/lead-center/$id/activities", body) })
        }
        deleteFollowup?.let { f -> SwalConfirm(
            title = "Delete this follow-up?", text = "Its reminder task goes too, unless it was already completed.", confirmText = "Yes, delete it",
            onConfirm = { deleteFollowup = null; act("Follow-up deleted", { api.delete("/lead-center/$id/followups/${f.optInt("id")}") }) }, onCancel = { deleteFollowup = null },
        ) }
        if (askDelete) SwalConfirm(
            title = "Delete this lead?", text = "It will be removed from the project.", confirmText = "Yes, delete it",
            onConfirm = { askDelete = false; act("Lead deleted", { api.delete("/lead-center/$id") }) { onBack() } }, onCancel = { askDelete = false },
        )
    }
}

@Composable
internal fun SmallAction(label: String, c: Color, onClick: () -> Unit) {
    Text(label, color = c, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = .1f)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp))
}

private fun pickDateTime(context: android.content.Context, current: String, set: (String) -> Unit) {
    val date = runCatching { LocalDate.parse(current.take(10)) }.getOrDefault(LocalDate.now())
    DatePickerDialog(context, { _, y, m, day ->
        val picked = LocalDate.of(y, m + 1, day).toString()
        val h = current.drop(11).take(2).toIntOrNull() ?: 10; val mi = current.drop(14).take(2).toIntOrNull() ?: 0
        TimePickerDialog(context, { _, hh, mm -> set("$picked %02d:%02d".format(hh, mm)) }, h, mi, true).show()
    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
}

/** Add / edit a follow-up: type, when, who, what to say. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FollowupSheet(b: Branding, options: JSONObject?, f: JSONObject, onClose: () -> Unit, onSave: (JSONObject) -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(f.optString("type").takeIf { it.isNotBlank() && it != "null" } ?: options?.objs("followup_types")?.firstOrNull()?.optString("name").orEmpty()) }
    var at by remember { mutableStateOf(f.optString("at").takeIf { it != "null" }.orEmpty()) }
    var remarks by remember { mutableStateOf(f.optString("remarks").takeIf { it != "null" }.orEmpty()) }
    var assignee by remember { mutableIntStateOf(f.optInt("assigned_to")) }
    var pickAssignee by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (f.has("id")) "Edit follow-up" else "Add follow-up", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Label("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options?.objs("followup_types").orEmpty().forEach { t -> Pick(t.optString("name"), type == t.optString("name"), Blue) { type = t.optString("name") } }
            }
            PickerField("When", whenLabel(at)) { pickDateTime(context, at) { at = it } }
            PickerField("Assign to", options?.objs("users")?.firstOrNull { it.optInt("id") == assignee }?.optString("name")) { pickAssignee = true }
            Input("Remarks", remarks, { remarks = it }, singleLine = false)
            SendButton(if (f.has("id")) "Save follow-up" else "Add follow-up", type.isNotBlank()) {
                onSave(JSONObject().put("followup_type", type).put("remarks", remarks.trim().ifBlank { JSONObject.NULL }).put("next_followup_at", at.ifBlank { JSONObject.NULL })
                    .put("assigned_to", if (assignee > 0) assignee else JSONObject.NULL).apply { if (f.has("status")) put("status", f.optString("status")) })
            }
        }
    }
    if (pickAssignee) ChoiceSheet("Assign to", options?.objs("users").orEmpty().map { Choice(it.optInt("id").toString(), it.optString("name")) }, assignee.takeIf { it > 0 }?.toString(), b, allowNone = true, onClose = { pickAssignee = false }) {
        pickAssignee = false; assignee = it.toIntOrNull() ?: 0
    }
}

/** Change a follow-up's status; the website requires a note with it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StatusSheet(b: Branding, options: JSONObject?, f: JSONObject, onClose: () -> Unit, onSave: (String, String) -> Unit) {
    var status by remember { mutableStateOf(f.optString("status", "pending")) }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Change status", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pending", "completed", "missed", "rescheduled").forEach { s ->
                    val c = tint(options?.objs("followup_statuses")?.firstOrNull { it.optString("slug") == s }?.str("color"), when (s) { "completed" -> KitGood; "missed" -> KitDanger; else -> Blue })
                    Pick(s.replaceFirstChar { it.uppercase() }, status == s, c) { status = s }
                }
            }
            Input("Note (required)", note, { note = it }, singleLine = false)
            SendButton("Update status", note.isNotBlank()) { onSave(status, note.trim()) }
        }
    }
}

private val ActivityTypes = listOf("Call", "Meeting", "Email", "WhatsApp", "Site Visit", "Note")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ActivitySheet(onClose: () -> Unit, onSave: (JSONObject) -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf("Call") }
    var at by remember { mutableStateOf(LocalDate.now().toString() + " 00:00") }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Log an activity", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { ActivityTypes.forEach { t -> Pick(t, type == t, Blue) { type = t } } }
            PickerField("When", whenLabel(at) ?: at.take(10)) { pickDateTime(context, at) { at = it } }
            Input("Note", note, { note = it }, singleLine = false)
            SendButton("Log activity", true) {
                onSave(JSONObject().put("activity_type", type).put("activity_date", at.take(10)).put("activity_time", at.drop(11).take(5)).put("note", note.trim().ifBlank { JSONObject.NULL }))
            }
        }
    }
}

/** The lead's activity feed — logged calls / meetings and every change — 15 at a time. */
@Composable
private fun LeadActivity(api: ApiClient, b: Branding, id: Int, version: Int, canEdit: Boolean, onLog: () -> Unit) {
    val rows = remember { mutableStateListOf<JSONObject>() }
    var page by remember { mutableIntStateOf(1) }
    var lastPage by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/lead-center/$id/activities?page=$p") }
            if (p == 1) rows.clear()
            rows.addAll(r.objs("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1)
        } catch (e: Exception) { error = e.message ?: "Could not load the activity" }
        loading = false
    }
    LaunchedEffect(id, version) { load(1) }

    if (canEdit) Text(
        "+  Log activity", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitInk).clickable { onLog() }.padding(vertical = 14.dp),
    )
    Card("Activity", "Who did what, and when") {
        if (loading && rows.isEmpty()) Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
        error?.let { Text(it, color = KitDanger, fontSize = 13.sp) }
        if (!loading && rows.isEmpty() && error == null) Text("No activity yet.", color = KitMuted, fontSize = 14.sp)
        rows.forEach { a ->
            val manual = a.optString("kind") == "activity"
            val tintC = if (manual) Blue else when (a.optString("event")) { "created", "followup_created" -> KitGood; "deleted", "followup_deleted" -> KitDanger; else -> Color(0xFFC47A1A) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(if (manual) a.optString("activity_type") else a.optString("event").replace('_', ' ').replaceFirstChar { it.uppercase() }, tintC)
                    a.str("tag")?.let { Spacer(Modifier.size(6.dp)); Chip(it, KitMuted) }
                }
                Text("${a.optString("user")} · ${a.optString("when")}", color = KitMuted, fontSize = 12.sp)
                if (manual) a.str("note")?.let { Text(it, color = KitInk, fontSize = 13.sp, lineHeight = 18.sp) }
                else {
                    val new = a.optJSONObject("new_values"); val old = a.optJSONObject("old_values")
                    new?.keys()?.asSequence()?.filter { it !in setOf("updated_at", "created_at", "site_id", "followup_id") }?.take(6)?.forEach { k ->
                        val nv = new.opt(k)?.toString()?.takeIf { it != "null" }; val ov = old?.opt(k)?.toString()?.takeIf { it != "null" }
                        Text(k.replace('_', ' ').replaceFirstChar { it.uppercase() } + ": " + (if (ov != null) "$ov → " else "") + (nv ?: "—"), color = KitInk, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
        if (rows.isNotEmpty() && page < lastPage && !loading) {
            var more by remember(page) { mutableStateOf(false) }
            LaunchedEffect(more) { if (more) load(page + 1) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { OutlinedButton(onClick = { more = true }) { Text("Load more") } }
        }
    }
}

// ───────────────────────────── create / edit ─────────────────────────────

/** New lead / edit lead — the website's lead form. Data: GET …/lead-center/options · GET /lead-center/{id} · POST …/lead-center · PUT /lead-center/{id}. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LeadFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = id != null

    var options by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var interested by remember { mutableStateOf("") }
    var listId by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var assignee by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }           // list · assignee
    var initial by remember { mutableStateOf<List<Any>?>(null) }
    fun snapshot(): List<Any> = listOf(name, phone, alt, email, company, source, interested, listId, status, label, assignee, notes)
    fun s(v: String?) = v?.takeIf { it != "null" }.orEmpty()

    LaunchedEffect(attempt) {
        loadError = null
        try {
            val o = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/lead-center/options") }
            if (id != null) {
                val x = withContext(Dispatchers.IO) { api.detail("/lead-center/$id") }
                name = s(x.optString("name")); phone = s(x.optString("phone")); alt = s(x.optString("alt_phone")); email = s(x.optString("email")); company = s(x.optString("company"))
                source = s(x.optString("source")); interested = s(x.optString("interested_in")); listId = x.optInt("list_id"); status = s(x.optString("status")); label = s(x.optString("label"))
                assignee = x.optInt("assigned_to"); notes = s(x.optString("notes"))
            } else if (o.objs("lists").size == 1) listId = o.objs("lists").first().optInt("id")
            options = o
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
        initial = snapshot()
    }

    fun save() {
        val problems = mutableMapOf<String, String>()
        if (name.isBlank()) problems["client_name"] = "Enter the client's name"
        if (!Regex("^\\d{10}$").matches(phone)) problems["phone_number"] = "Enter a 10-digit mobile number."
        if (alt.isNotBlank() && !Regex("^\\d{10}$").matches(alt)) problems["alternate_phone_number"] = "Enter a 10-digit mobile number."
        if (source.isBlank()) problems["lead_source"] = "Enter where the lead came from"
        if (listId <= 0) problems["list_id"] = "Choose the lead list this lead belongs to."
        if (problems.isNotEmpty()) { errors = problems; banner = problems.values.first(); return }
        val nul = JSONObject.NULL
        val body = JSONObject().put("client_name", name.trim()).put("phone_number", phone).put("alternate_phone_number", alt.ifBlank { nul }).put("email", email.trim().ifBlank { nul })
            .put("company_name", company.trim().ifBlank { nul }).put("lead_source", source.trim()).put("interested_in", interested.trim().ifBlank { nul }).put("list_id", listId)
            .put("status", status.ifBlank { nul }).put("label", label.ifBlank { nul }).put("assigned_to", if (assignee > 0) assignee else nul).put("notes", notes.trim().ifBlank { nul })
        errors = emptyMap(); banner = null; saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (id != null) api.send("PUT", "/lead-center/$id", body) else api.send("POST", "/projects/$uuid/lead-center", body) }
                Toast.makeText(context, if (editing) "Lead saved" else "Lead created", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: ApiException) {
                errors = e.errors; banner = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message
            } catch (e: Exception) { banner = e.message ?: "Could not save" }
            saving = false
        }
    }

    val back = leaveGuard(initial != null && snapshot() != initial, onBack)
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, if (editing) "Edit lead" else "New lead", back, compact = true)
        val o = options
        when {
            loadError != null -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(loadError ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
            o == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 8.dp)) {
                        banner?.let { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).padding(12.dp)) }

                        Card("Who is the lead?", "Name and how to reach them") {
                            Input("Client name", name, { name = it }, errors["client_name"])
                            Input("Mobile number", phone, { phone = it.filter(Char::isDigit).take(10) }, errors["phone_number"], keyboard = KeyboardType.Phone)
                            Input("Alternate number", alt, { alt = it.filter(Char::isDigit).take(10) }, errors["alternate_phone_number"], keyboard = KeyboardType.Phone)
                            Input("Email", email, { email = it }, errors["email"], keyboard = KeyboardType.Email)
                            Input("Company", company, { company = it })
                        }
                        Card("Where from?", "Source, list and interest") {
                            Input("Lead source", source, { source = it }, errors["lead_source"])
                            val sources = o.optJSONArray("sources")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().take(8)
                            if (sources.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { sources.forEach { src -> Pick(src, source == src, Blue) { source = src } } }
                            PickerField("Lead list", o.objs("lists").firstOrNull { it.optInt("id") == listId }?.optString("name"), errors["list_id"]) { sheet = "list" }
                            Input("Interested in", interested, { interested = it }, singleLine = false)
                        }
                        Card("Status & owner") {
                            Label("Status")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pick("None", status.isEmpty(), KitMuted) { status = "" }
                                o.objs("statuses").forEach { st -> Pick(st.optString("name"), status == st.optString("name"), tint(st.str("color"))) { status = st.optString("name") } }
                            }
                            Label("Label")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pick("None", label.isEmpty(), KitMuted) { label = "" }
                                o.objs("labels").forEach { l -> Pick(l.optString("name"), label == l.optString("name"), labelTintOf(o, l.optString("name"))) { label = l.optString("name") } }
                            }
                            PickerField("Assigned to", o.objs("users").firstOrNull { it.optInt("id") == assignee }?.optString("name")) { sheet = "assignee" }
                        }
                        Card("Notes") { Input("Notes", notes, { notes = it }, singleLine = false) }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)) {
                    Text(
                        if (saving) "Saving…" else if (editing) "Save lead" else "Create lead", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { save() }.padding(vertical = 15.dp),
                    )
                }
                if (sheet == "list") ChoiceSheet("Lead list", o.objs("lists").map { Choice(it.optInt("id").toString(), it.optString("name")) }, listId.takeIf { it > 0 }?.toString(), b, onClose = { sheet = null }) { sheet = null; listId = it.toIntOrNull() ?: 0 }
                if (sheet == "assignee") ChoiceSheet("Assigned to", o.objs("users").map { Choice(it.optInt("id").toString(), it.optString("name")) }, assignee.takeIf { it > 0 }?.toString(), b, allowNone = true, onClose = { sheet = null }) { sheet = null; assignee = it.toIntOrNull() ?: 0 }
            }
        }
    }
}
