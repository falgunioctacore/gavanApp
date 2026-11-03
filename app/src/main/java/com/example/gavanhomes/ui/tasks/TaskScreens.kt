package com.example.gavanhomes.ui.tasks

import com.example.gavanhomes.ui.common.AppToast

import com.example.gavanhomes.ui.common.bounceClick
import com.example.gavanhomes.ui.common.ScrollTopButton
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.DeleteButton
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.SegTabs
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.projects.form.readPicked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate

private val Blue = Color(0xFF1E5FAE)

// ───────────────────────────── task details ─────────────────────────────

/**
 * One task, like the website's task page, in four tabs: **Overview** (status, priority, due date, department, progress — each changed right here —
 * plus the facts and the people), **Subtasks**, **Files** (open, upload, delete) and **Activity** (comments and the history).
 * Data: GET /task-center/{id} · POST …/status · …/quick · …/subtasks · …/comments · …/documents · DELETE /task-center/{id}.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onOpenTask: (Int) -> Unit, onChanged: () -> Unit, initialTab: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(initialTab) }
    var busy by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var askDeleteSub by remember { mutableStateOf<JSONObject?>(null) }
    var askDeleteFile by remember { mutableStateOf<JSONObject?>(null) }
    var previewFile by remember { mutableStateOf<Pair<String, String>?>(null) }      // a task file shown inside the app: url · name
    var departmentSheet by remember { mutableStateOf(false) }
    var subtask by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    LaunchedEffect(id, version, attempt) {
        error = null
        try {
            val d = withContext(Dispatchers.IO) { api.detail("/task-center/$id") }
            t = d
            if (options == null) options = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/${d.optString("project_uuid")}/task-center/options") } }.getOrNull()
        } catch (e: Exception) { error = e.message ?: "Could not load the task" }
    }
    BackHandler(enabled = true) { onBack() }

    /** Runs a write, toasts the outcome and reloads the task. */
    fun act(success: String?, call: () -> Unit, after: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { call() }
                if (success != null) AppToast.show(success)
                after(); onChanged(); attempt++
            } catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
            busy = false
        }
    }
    fun quick(vararg pairs: Pair<String, Any?>) = act("Saved", { api.send("POST", "/task-center/$id/quick", JSONObject().apply { pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }) })

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val f = uri?.let { readPicked(context, it) } ?: return@rememberLauncherForActivityResult
        act("File uploaded", { api.sendForm("/task-center/$id/documents", listOf(FormPart("document", fileName = f.name, mime = f.mime, bytes = f.bytes))) })
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        val d = t
        if (d == null) {
            HeroBar(b, "Task", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base)
                else com.example.gavanhomes.ui.common.EstateError(error, modifier = Modifier.padding(24.dp), onRetry = { attempt++ })
            }
            return@Column
        }
        val can = d.optJSONObject("can")
        val canEdit = can?.optBoolean("edit") == true
        val canDelete = can?.optBoolean("delete") == true
        val isSub = !d.isNull("parent_id")
        val subs = d.objs("subtasks"); val files = d.objs("files")

        val pageScroll = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize().verticalScroll(pageScroll).imePadding()) {
            HeroBar(
                b, if (isSub) "Subtask" else "Task", onBack,
                actions = {
                    if (canEdit) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                    if (canDelete) DeleteButton { askDelete = true }
                },
            ) {
                Column {
                    Text(d.optString("name"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    d.str("project")?.let { Text(it, color = Color.White.copy(alpha = .8f), fontSize = 13.sp) }
                    Spacer(Modifier.height(12.dp))
                    HeroChips(*listOfNotNull(d.str("status"), d.str("priority")?.let { "$it priority" }, prettyDay(d.str("due_date"))?.let { "Due $it" }, d.str("type_label")).toTypedArray())
                }
            }

            com.example.gavanhomes.ui.common.PillTabs(
                listOf(com.example.gavanhomes.ui.common.PillTab("Details"), com.example.gavanhomes.ui.common.PillTab("Files", files.size), com.example.gavanhomes.ui.common.PillTab("Discussion"),
                    com.example.gavanhomes.ui.common.PillTab("Activity", d.objs("history").size), com.example.gavanhomes.ui.common.PillTab("Assign")),
                tab, b.base,
            ) { tab = it }
            val page = listOf("overview", "files", "discussion", "activity", "assign")[tab]

            // the files — a gallery of the pictures, then the other documents with View / Download — shown on the Details tab and the Files tab
            val filesCard: @Composable () -> Unit = {
Card("Files", if (files.isEmpty()) "Documents for this task" else "${files.size} attached") {
                    if (files.isEmpty()) Text("No files yet.", color = KitMuted, fontSize = 14.sp)
                    val images = files.filter { isImageName(it.optString("name")) }
                    if (images.isNotEmpty()) com.example.gavanhomes.ui.tasks.ImageGrid(images, if (canDelete) ({ f -> askDeleteFile = f }) else null)
                    files.filterNot { isImageName(it.optString("name")) }.forEach { f ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Blue.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                                Text((f.str("name")?.substringAfterLast('.', "") ?: "").take(4).uppercase().ifEmpty { "FILE" }, color = Blue, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(f.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                f.optLong("size").takeIf { it > 0 }?.let { Text("${it / 1024} KB", color = KitMuted, fontSize = 12.sp) }
                            }
                            Text("View", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(com.example.gavanhomes.ui.common.KitCard).bounceClick {
                                previewFile = localUrl(f.optString("url")) to f.optString("name")
                            }.padding(horizontal = 12.dp, vertical = 8.dp))
                            Text("Download", color = b.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(12.dp)).background(b.base.copy(alpha = .12f)).clickable {
                                com.example.gavanhomes.ui.tasks.downloadFile(context, scope, f.optString("url"), f.optString("name"), f.optString("type"))
                            }.padding(horizontal = 12.dp, vertical = 8.dp))
                            if (canDelete) Text("✕", color = KitDanger, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable { askDeleteFile = f }.padding(8.dp))
                        }
                    }
                    if (canEdit) com.example.gavanhomes.ui.tasks.UploadTile(b.base) { pickFile.launch("*/*") }
                }
            }

            // the subtasks checklist, shown under the details (a subtask has none of its own)
            val subtasksCard: @Composable () -> Unit = {
                Card("Subtasks", if (subs.isEmpty()) "Break the work into steps" else "${subs.count { it.optString("status") == "Completed" }} of ${subs.size} done") {
                    if (subs.isNotEmpty()) Meter(subs.count { it.optString("status") == "Completed" } / subs.size.toFloat(), KitGood)
                    subs.forEach { s ->
                        val done = s.optString("status") == "Completed"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(if (done) KitGood else com.example.gavanhomes.ui.common.KitLine)
                                    .clickable(enabled = canEdit) { act(if (done) "Subtask marked pending" else "Subtask completed", { api.send("POST", "/task-center/${s.optInt("id")}/status", JSONObject().put("status_id", statusIdFor(d, if (done) "Pending" else "Completed"))) }) },
                                contentAlignment = Alignment.Center,
                            ) { if (done) Text("✓", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                            Column(Modifier.weight(1f).padding(start = 12.dp).clickable { onOpenTask(s.optInt("id")) }) {
                                Text(s.optString("name"), color = KitInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textDecoration = if (done) TextDecoration.LineThrough else null)
                                Text(listOfNotNull(s.str("status"), s.str("priority"), prettyDay(s.str("due_date"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                            }
                            if (canDelete) Text("✕", color = KitMuted, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable { askDeleteSub = s }.padding(8.dp))
                        }
                    }
                    if (canEdit) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Input("New subtask", subtask, { subtask = it }) }
                        SendButton("Add", subtask.isNotBlank()) { act("Subtask added", { api.send("POST", "/task-center/$id/subtasks", JSONObject().put("title", subtask.trim())) }) { subtask = "" } }
                    }
                }
            }

            when (page) {
                // ───── overview ─────
                "overview" -> {
                    if (canEdit) Card("Status", "Tap to change") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            d.objs("statuses").forEach { s ->
                                val on = s.optInt("id") == d.optInt("status_id")
                                Pick(s.optString("name"), on, statusTint(s.optString("name"))) { if (!on) act("Status changed", { api.send("POST", "/task-center/$id/status", JSONObject().put("status_id", s.optInt("id"))) }) }
                            }
                        }
                    }

                    Card("Priority & due date") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Low", "Medium", "High", "Urgent").forEach { p ->
                                val on = d.optString("priority") == p
                                Pick(p, on, priorityTint(p)) { if (canEdit && !on) quick("priority" to p) }
                            }
                        }
                        val due = prettyDay(d.str("due_date"))
                        PickerField("Due date", due?.let { it + (d.str("due_time")?.let { tm -> " · ${tm.take(5)}" } ?: "") }) {
                            if (!canEdit) return@PickerField
                            val cur = runCatching { LocalDate.parse(d.optString("due_date").take(10)) }.getOrDefault(LocalDate.now())
                            DatePickerDialog(context, { _, y, m, day -> quick("due_date" to LocalDate.of(y, m + 1, day).toString()) }, cur.year, cur.monthValue - 1, cur.dayOfMonth).show()
                        }
                        PickerField("Department", d.str("department")) { if (canEdit) departmentSheet = true }
                    }

                    Card("Progress", "${d.optInt("progress")}% complete") {
                        var p by remember(d.optInt("progress")) { mutableFloatStateOf(d.optInt("progress").toFloat()) }
                        Slider(
                            value = p, onValueChange = { if (canEdit) p = it }, valueRange = 0f..100f, steps = 19, enabled = canEdit,
                            onValueChangeFinished = { if (p.toInt() != d.optInt("progress")) quick("progress" to p.toInt()) },
                            colors = SliderDefaults.colors(thumbColor = b.base, activeTrackColor = b.base),
                        )
                        Meter(p / 100f, statusTint(d.str("status")))
                    }

                    d.str("description")?.let { Card("Description") { Text(it, color = KitInk, fontSize = 14.sp, lineHeight = 21.sp) } }

                    Card("Details") {
                        Fact("Type", d.str("type_label"))
                        if (d.str("lead_phone") != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Fact("Customer / lead", d.str("lead_name") ?: d.str("lead_label"))
                            PhoneActions(d.optString("lead_phone"))
                        } else Fact("Customer / lead", d.str("lead_label"))
                        Fact("Reminder", d.str("reminder_label")?.let { r -> if (d.optString("reminder") == "custom") "$r · ${d.optString("reminder_custom_at")}" else r })
                        Fact("Repeats", d.str("recurrence")?.let { r -> when (r) { "weekly" -> "Every week"; "monthly" -> "Every month"; else -> "Until ${prettyDay(d.str("recurrence_custom_date"))}" } })
                        Fact("Created", listOfNotNull(prettyDay(d.str("created_at")), d.str("created_by")?.let { "by $it" }).joinToString(" "))
                        Fact("Completed", prettyDay(d.str("completed_date")))
                        d.str("overdue_reason")?.let { Fact("Overdue reason", it) }
                    }

                    val people = d.objs("assignees")
                    Card("Assigned to", "${people.size} ${if (people.size == 1) "person" else "people"}") {
                        if (people.isEmpty()) Text("Nobody yet.", color = KitMuted, fontSize = 14.sp)
                        people.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.example.gavanhomes.ui.common.PersonAvatar(p.optInt("id"), p.optString("name"), 38.dp)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(p.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    p.str("phone")?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
                                }
                                p.str("phone")?.let { ph -> CallChip(ph) }
                            }
                        }
                    }
                    // notes added from a task's ⋮ menu (Add notes) or the website
                    val notes = d.objs("comments")
                    if (notes.isNotEmpty()) Card("Notes", "${notes.size}") {
                        notes.forEach { c ->
                            Row {
                                Initials(c.optString("by"), size = 34.dp)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(c.optString("by"), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 13.sp)
                                        Text(c.optString("at"), color = KitMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                                    }
                                    Text(c.optString("text"), color = KitInk, fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                    if (!isSub) subtasksCard()
                    filesCard()
                }

                // ───── files ─────
                "files" -> filesCard()

                "discussion" -> TaskDiscussion(api, b, id, onChanged = { attempt++; onChanged() })              // a new message adds to the activity feed
                "assign" -> TaskAssignTab(api, b, id, d.optString("name"), canEdit, onChanged = { attempt++; onChanged() })   // so the details, people and activity show the change
                // ───── activity ─────
                else -> {
                    val history = d.objs("history")
                    Card("Activity", if (history.isEmpty()) "Nothing has happened yet" else "Who did what, and when") {
                        if (history.isEmpty()) Text("No activity yet.", color = KitMuted, fontSize = 14.sp)
                        HistoryTimeline(history)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        ScrollTopButton(pageScroll)
        }

        if (departmentSheet) ModalBottomSheet(onDismissRequest = { departmentSheet = false }, containerColor = com.example.gavanhomes.ui.common.KitCard) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Department", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                (options?.objs("departments").orEmpty()).forEach { dep ->
                    val on = dep.optInt("id") == d.optInt("department_id")
                    Text(
                        dep.optString("name"), color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else com.example.gavanhomes.ui.common.KitSoft)
                            .clickable { departmentSheet = false; quick("department_id" to dep.optInt("id")) }.padding(14.dp),
                    )
                }
            }
        }

        if (askDelete) SwalConfirm(
            title = "Delete this task?", text = "It will be removed from the project.", confirmText = "Yes, delete it",
            onConfirm = { askDelete = false; act("Task deleted", { api.delete("/task-center/$id") }) { onBack() } }, onCancel = { askDelete = false },
        )
        askDeleteSub?.let { s -> SwalConfirm(
            title = "Delete this subtask?", text = s.optString("name"), confirmText = "Yes, delete it",
            onConfirm = { askDeleteSub = null; act("Subtask deleted", { api.delete("/task-center/${s.optInt("id")}") }) }, onCancel = { askDeleteSub = null },
        ) }
        previewFile?.let { (url, name) -> com.example.gavanhomes.ui.discussion.FilePreview(url, name) { previewFile = null } }
        askDeleteFile?.let { f -> SwalConfirm(
            title = "Delete this file?", text = f.optString("name"), confirmText = "Yes, delete it",
            onConfirm = { askDeleteFile = null; act("File deleted", { api.delete("/task-center/$id/documents/${f.optInt("id")}") }) }, onCancel = { askDeleteFile = null },
        ) }
    }
}

/** A phone number's digits, with India's +91 added for a plain 10-digit number — what WhatsApp's link wants. */
private fun waNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return if (digits.length == 10) "91$digits" else digits
}

private fun dial(context: android.content.Context, phone: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) }
        .onFailure { AppToast.show("This phone cannot make calls") }
}

/** The number, with **Call** (opens the dialer) and **WhatsApp** (opens a chat with them). */
@Composable
internal fun PhoneActions(phone: String) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(phone, color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("Call", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitGood).clickable { dial(context, phone) }.padding(horizontal = 16.dp, vertical = 8.dp))
        Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF25A244)).clickable {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${waNumber(phone)}"))) }.onFailure { AppToast.show("WhatsApp is not available") }
        }.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
internal fun CallChip(phone: String) {
    val context = LocalContext.current
    Text("Call", color = KitGood, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitGood.copy(alpha = .12f)).clickable { dial(context, phone) }.padding(horizontal = 14.dp, vertical = 8.dp))
}

private fun statusIdFor(d: JSONObject, name: String): Int = d.objs("statuses").firstOrNull { it.optString("name") == name }?.optInt("id") ?: 0

@Composable
internal fun SendButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label, color = Color.White, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (enabled) com.example.gavanhomes.ui.common.KitInkFill else KitMuted).clickable(enabled = enabled) { onClick() }.padding(horizontal = 18.dp, vertical = 16.dp),
    )
}

@Composable
internal fun Fact(label: String, value: String?) {
    if (value.isNullOrBlank() || value == "null") return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(.8f))
        Text(value, color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}

/** A selectable pill: filled in its colour when on, tinted when off. */
@Composable
internal fun Pick(label: String, on: Boolean, tint: Color, onClick: () -> Unit) {
    Text(
        label, color = if (on) Color.White else tint, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) tint else tint.copy(alpha = .1f)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

// ───────────────────────────── create / edit ─────────────────────────────

/**
 * New task / edit task — the website's task form in sections: **What** (name, description, type), **When** (due date and time, reminder, repeat),
 * **Who** (people, department), **Link** (a customer / lead) and, when editing, **Progress** (status, progress, overdue reason).
 * The Save bar stays at the bottom. Data: GET …/task-center/options · …/leads · GET /task-center/{id} · POST …/task-center · PUT /task-center/{id}.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = id != null

    var options by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Medium") }
    var due by remember { mutableStateOf("") }
    var dueTime by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf("") }
    var remDate by remember { mutableStateOf("") }
    var remTime by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf("") }
    var recDate by remember { mutableStateOf("") }
    var leadId by remember { mutableIntStateOf(0) }
    var leadLabel by remember { mutableStateOf("") }
    var unitId by remember { mutableIntStateOf(0) }
    var unitLabel by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }                       // "" · bids · leads (the website's task category)
    var attachment by remember { mutableStateOf<com.example.gavanhomes.ui.projects.form.PickedFile?>(null) }   // a file to attach when the task is created
    var unitSheet by remember { mutableStateOf(false) }
    var subs by remember { mutableStateOf<List<SubRow>>(emptyList()) }                           // the subtasks checklist of the form
    var subPeople by remember { mutableStateOf<Int?>(null) }                                     // the subtask whose people are being picked
    var bidItems by remember { mutableStateOf<List<BidRow>>(emptyList()) }                       // the items of a task from Bids (the website's "Bid Items")
    var hadBidItems by remember { mutableStateOf(false) }
    var bidPick by remember { mutableStateOf<Int?>(null) }                                       // the row whose item is being chosen
    var typeDialog by remember { mutableStateOf<Pair<String?, String>?>(null) }        // add (key null) or rename a task type: key · label
    var typeDelete by remember { mutableStateOf<Pair<String, String>?>(null) }          // key · label
    var deptDialog by remember { mutableStateOf(false) }
    var listBusy by remember { mutableStateOf(false) }
    fun refreshOptions(after: () -> Unit = {}) {
        scope.launch { runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/task-center/options") } }.onSuccess { options = it; after() } }
    }
    var statusId by remember { mutableIntStateOf(0) }
    var departmentId by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var overdueReason by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<Set<Int>>(emptySet()) }                              // employees / supervisors (users)
    var otherPeople by remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }            // contractor · supplier · professional → ids
    var assignNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }              // "type:id" → name, for showing who is picked
    var assignTab by remember { mutableStateOf("employee") }                                     // the assignee type tab, like the website's
    var visited by remember { mutableStateOf<Set<String>>(emptySet()) }                          // types looked at — only those are sent, so the others stay as they were
    var errors by com.example.gavanhomes.ui.common.rememberErrorsState()
    var banner by com.example.gavanhomes.ui.common.rememberBannerState()
    var saving by remember { mutableStateOf(false) }
    var leadSheet by remember { mutableStateOf(false) }
    var typeSheet by remember { mutableStateOf(false) }
    var departmentSheet by remember { mutableStateOf(false) }
    var peopleSheet by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<List<Any>?>(null) }
    var nameEdited by remember { mutableStateOf(editing) }          // once the user types a name, we stop suggesting one (an existing task keeps its name)
    fun typeLabel(): String = options?.objs("types")?.firstOrNull { it.optString("key") == type }?.optString("label").orEmpty()
    /** "Call – Rajesh Patel Follow-up": the website's suggested name from the type and the customer / lead. */
    fun refreshName() {
        if (nameEdited) return
        val parts = listOf(typeLabel(), leadLabel.substringBefore(" — ").trim()).filter { it.isNotBlank() }
        name = if (parts.isEmpty()) "" else parts.joinToString(" – ") + " Follow-up"
    }
    fun snapshot(): List<Any> =listOf(name, description, type, priority, due, dueTime, reminder, remDate, remTime, recurrence, recDate, leadId, statusId, departmentId, progress.toInt(), overdueReason, people, otherPeople, unitId, category, attachment?.name.orEmpty(), bidItems.map { listOf(it.kind, it.id, it.qty, it.unit) }, subs.map { listOf(it.id, it.title, it.priority, it.due, it.people) })

    LaunchedEffect(attempt) {
        loadError = null
        try {
            val o = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/task-center/options") }
            if (id != null) {
                val d = withContext(Dispatchers.IO) { api.detail("/task-center/$id") }
                name = d.optString("name"); description = d.str("description").orEmpty(); type = d.str("type").orEmpty(); priority = d.str("priority") ?: "Medium"
                due = d.str("due_date").orEmpty(); dueTime = d.str("due_time")?.take(5).orEmpty(); statusId = d.optInt("status_id"); departmentId = d.optInt("department_id")
                reminder = d.str("reminder").orEmpty(); d.str("reminder_custom_at")?.let { remDate = it.take(10); remTime = it.drop(11).take(5) }
                recurrence = d.str("recurrence").orEmpty(); recDate = d.str("recurrence_custom_date").orEmpty()
                leadId = d.optInt("lead_id"); leadLabel = d.str("lead_label").orEmpty(); unitId = d.optInt("unit_id"); unitLabel = d.str("unit_label").orEmpty(); category = d.str("task_category").orEmpty(); progress = d.optInt("progress").toFloat(); overdueReason = d.str("overdue_reason").orEmpty()
                people = d.optJSONArray("assignee_ids")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
                d.optJSONArray("subtask_rows")?.let { a ->
                    subs = (0 until a.length()).map { i -> a.getJSONObject(i).let { x -> SubRow(x.optInt("id"), x.optString("title"), x.str("priority") ?: "Medium", x.str("due_date").orEmpty(), x.optJSONArray("assignee_ids")?.let { p -> (0 until p.length()).map { k -> p.getInt(k) }.toSet() } ?: emptySet()) } }
                }
                d.optJSONArray("bid_items")?.let { a ->
                    bidItems = (0 until a.length()).map { i -> a.getJSONObject(i).let { x -> BidRow(x.optString("kind"), x.optInt("id"), x.optString("name"), x.optString("quantity").trimEnd('0').trimEnd('.').ifBlank { "" }, x.optString("unit").takeIf { u -> u != "null" }.orEmpty()) } }
                    hadBidItems = bidItems.isNotEmpty()
                }
                d.optJSONArray("assignments")?.let { a ->
                    val m = mutableMapOf<String, MutableSet<Int>>(); val n = mutableMapOf<String, String>()
                    for (i in 0 until a.length()) { val x = a.getJSONObject(i); val t = x.optString("type"); n["$t:${x.optInt("id")}"] = x.optString("name"); if (t != "user") m.getOrPut(t) { mutableSetOf() }.add(x.optInt("id")) }
                    otherPeople = m; visited = m.keys.toSet(); assignNames = n
                }
            }
            options = o
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
        initial = snapshot()
    }

    fun save() {
        val problems = mutableMapOf<String, String>()
        if (name.isBlank()) problems["name"] = "Enter a task name"
        if (description.isBlank()) problems["description"] = "Enter a description"
        if (reminder == "custom" && remDate.isBlank()) problems["reminder_custom_date"] = "Pick the reminder date"
        if (recurrence == "custom" && recDate.isBlank()) problems["recurrence_custom_date"] = "Pick the end date"
        if (category == "bids" && bidItems.any { it.id == 0 || it.qty.toDoubleOrNull() == null }) problems["bid_items"] = "Pick an item and a quantity for every bid item"
        if (problems.isNotEmpty()) { errors = problems; banner = problems.values.first(); return }
        val nul = JSONObject.NULL
        val body = JSONObject().put("name", name.trim()).put("description", description.trim()).put("priority", priority)
            .put("due_date", due.ifBlank { nul }).put("due_time", dueTime.ifBlank { nul }).put("type", type.ifBlank { nul })
            .put("assignee_groups", JSONObject().put("user", JSONArray(people.sorted())).also { g -> listOf("contractor", "supplier", "professional").filter { it in visited || it in otherPeople }.forEach { t -> g.put(t, JSONArray((otherPeople[t] ?: emptySet()).sorted())) } }).put("department_id", if (departmentId > 0) departmentId else nul)
            .put("lead_id", if (leadId > 0) leadId else nul).put("reminder", reminder.ifBlank { nul })
            .put("reminder_custom_date", if (reminder == "custom") remDate.ifBlank { nul } else nul).put("reminder_custom_time", if (reminder == "custom") remTime.ifBlank { nul } else nul)
            .put("unit_id", if (unitId > 0) unitId else nul).put("task_category", category.ifBlank { nul }).put("recurrence", recurrence.ifBlank { nul }).put("recurrence_custom_date", if (recurrence == "custom") recDate.ifBlank { nul } else nul)
        if (editing || subs.isNotEmpty()) body.put("subtasks", JSONArray(subs.filter { it.title.isNotBlank() }.map { r -> JSONObject().apply { if (r.id > 0) put("id", r.id); put("title", r.title.trim()); put("priority", r.priority); put("due_date", r.due.ifBlank { nul }); put("assignee_id", JSONArray(r.people.sorted())) } }))
        if (category == "bids" || bidItems.isNotEmpty() || hadBidItems) body.put("bid_items", JSONArray(bidItems.filter { it.id > 0 }.map { r -> JSONObject().put("kind", r.kind).put("id", r.id).put("quantity", r.qty.toDoubleOrNull() ?: 0.0).put("unit", r.unit.ifBlank { nul }) }))
        if (editing) { if (statusId > 0) body.put("status_id", statusId); body.put("progress", progress.toInt()); body.put("overdue_reason", overdueReason.ifBlank { nul }) }
        errors = emptyMap(); banner = null; saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (id != null) api.send("PUT", "/task-center/$id", body)
                    else {
                        val made = api.send("POST", "/projects/$uuid/task-center", body).optJSONObject("data")?.optInt("id") ?: 0
                        attachment?.let { a -> if (made > 0) api.sendForm("/task-center/$made/documents", listOf(FormPart("document", fileName = a.name, mime = a.mime, bytes = a.bytes))) }
                    }
                }
                AppToast.show(if (editing) "Task saved" else "Task created")
                onSaved()
            } catch (e: ApiException) {
                errors = e.errors; banner = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message
            } catch (e: Exception) { banner = e.message ?: "Could not save" }
            saving = false
        }
    }

    val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { readPicked(context, it) }?.let { attachment = it } }

    fun pickDate(current: String, set: (String) -> Unit) {
        val d = runCatching { LocalDate.parse(current.take(10)) }.getOrDefault(LocalDate.now())
        DatePickerDialog(context, { _, y, m, day -> set(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }
    fun pickTime(current: String, set: (String) -> Unit) {
        val h = current.take(2).toIntOrNull() ?: 9; val m = current.drop(3).take(2).toIntOrNull() ?: 0
        TimePickerDialog(context, { _, hh, mm -> set("%02d:%02d".format(hh, mm)) }, h, m, true).show()
    }

    val back = leaveGuard(initial != null && snapshot() != initial, onBack)
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, if (editing) "Edit task" else "New task", back, compact = true)
        val o = options
        when {
            loadError != null -> com.example.gavanhomes.ui.common.EstateError(loadError, modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), onRetry = { attempt++ })
            o == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) }
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 8.dp)) {
                        banner?.let { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).padding(12.dp)) }

                        // like the website: who it is about and what kind of task come first — they fill in the task name
                        Card("Who is it about?", "Pick a customer / lead and a type — the task name fills itself in") {
                            PickerField("Customer / lead", leadLabel.ifBlank { null }) { leadSheet = true }
                            if (leadId > 0) Text("Remove link", color = KitMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { leadId = 0; leadLabel = ""; refreshName() })
                            PickerField("Type", typeLabel().ifBlank { null }, errors["type"]) { typeSheet = true }
                            PickerField("Unit (flat / shop)", unitLabel.ifBlank { null }, errors["unit_id"]) { unitSheet = true }
                            if (unitId > 0) Text("Remove unit", color = KitMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { unitId = 0; unitLabel = "" })
                            Label("Task category")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pick("None", category.isEmpty(), KitMuted) { category = "" }
                                Pick("Bids", category == "bids", Blue) { category = "bids" }
                                Pick("Leads", category == "leads", Blue) { category = "leads" }
                            }
                        }

                        if (category == "bids" || bidItems.isNotEmpty()) Card("Bid items", if (bidItems.isEmpty()) "What this bid is for" else "${bidItems.size} item${if (bidItems.size == 1) "" else "s"}") {
                            errors["bid_items"]?.let { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                            bidItems.forEachIndexed { i, r ->
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Pick("Material", r.kind == "material", b.base) { if (r.kind != "material") bidItems = bidItems.toMutableList().also { it[i] = BidRow("material", 0, "", r.qty, "") } }
                                        Pick("Service", r.kind == "service", b.base) { if (r.kind != "service") bidItems = bidItems.toMutableList().also { it[i] = BidRow("service", 0, "", r.qty, "") } }
                                        Spacer(Modifier.weight(1f))
                                        Text("✕", color = KitMuted, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable { bidItems = bidItems.toMutableList().also { it.removeAt(i) } }.padding(8.dp))
                                    }
                                    PickerField(if (r.kind == "service") "Service" else "Material", r.name.ifBlank { null }) { bidPick = i }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(Modifier.weight(1f)) { Input("Quantity", r.qty, { v -> bidItems = bidItems.toMutableList().also { it[i] = r.copy(qty = v.filter { c -> c.isDigit() || c == '.' }) } }) }
                                        Box(Modifier.weight(1f)) { Input("Unit", r.unit, { v -> bidItems = bidItems.toMutableList().also { it[i] = r.copy(unit = v) } }) }
                                    }
                                }
                            }
                            Text("＋  Add item", color = b.base, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base.copy(alpha = .12f)).clickable { bidItems = bidItems + BidRow("material", 0, "", "", ""); bidPick = bidItems.size - 1 }.padding(vertical = 13.dp))
                        }

                        Card("What needs doing", "Name it and say what it involves") {
                            Input("Task name", name, { name = it; nameEdited = true }, errors["name"])
                            if (!nameEdited && name.isNotBlank()) Text("Suggested from the type and customer — edit it if you like", color = KitMuted, fontSize = 11.sp)
                            Input("Description", description, { description = it }, errors["description"], singleLine = false)
                            if (!editing) {
                                PickerField("Attachment (PDF, Office, image or ZIP, up to 10 MB)", attachment?.name) { pickAttachment.launch("*/*") }
                                if (attachment != null) Text("Remove file", color = KitMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { attachment = null })
                            }
                            Label("Priority")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Low", "Medium", "High", "Urgent").forEach { p -> Pick(p, priority == p, priorityTint(p)) { priority = p } } }
                        }

                        Card("Subtasks", if (subs.isEmpty()) "Break the work into steps" else "${subs.size} step${if (subs.size == 1) "" else "s"}") {
                            subs.forEachIndexed { i, r ->
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f)) { Input("Subtask name", r.title, { v -> subs = subs.toMutableList().also { it[i] = r.copy(title = v) } }) }
                                        Text("✕", color = KitMuted, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable { subs = subs.toMutableList().also { it.removeAt(i) } }.padding(8.dp))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f)) { PickerField("Due date", prettyDay(r.due)) { pickDate(r.due) { v -> subs = subs.toMutableList().also { it[i] = r.copy(due = v) } } } }
                                        val order = listOf("Low", "Medium", "High", "Urgent")
                                        Pick(r.priority, true, priorityTint(r.priority)) { subs = subs.toMutableList().also { it[i] = r.copy(priority = order[(order.indexOf(r.priority) + 1) % order.size]) } }
                                    }
                                    val names = r.people.map { id -> options?.objs("people")?.firstOrNull { it.optInt("id") == id }?.optString("name") ?: assignNames["user:$id"] ?: "User $id" }
                                    PickerField("Assignee (the task's people if empty)", names.takeIf { it.isNotEmpty() }?.let { if (it.size <= 2) it.joinToString(", ") else "${it.take(2).joinToString(", ")} +${it.size - 2}" }) { subPeople = i }
                                }
                            }
                            Text("＋  Add subtask", color = b.base, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base.copy(alpha = .12f)).clickable { subs = subs + SubRow(0, "", priority, "", emptySet()) }.padding(vertical = 13.dp))
                        }

                        Card("When", "Due date, reminder and repeat") {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1.4f)) { PickerField("Due date", prettyDay(due), errors["due_date"]) { pickDate(due) { due = it } } }
                                Box(Modifier.weight(1f)) { PickerField("Time", dueTime.ifBlank { null }, errors["due_time"]) { pickTime(dueTime) { dueTime = it } } }
                            }
                            if (due.isNotBlank() || dueTime.isNotBlank()) Text("Clear due date", color = KitMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { due = ""; dueTime = "" })

                            Label("Remind me")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pick("No reminder", reminder.isEmpty(), KitMuted) { reminder = "" }
                                o.objs("reminders").forEach { r -> Pick(r.optString("label"), reminder == r.optString("key"), Blue) { reminder = r.optString("key") } }
                            }
                            if (reminder == "custom") Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1.4f)) { PickerField("Reminder date", prettyDay(remDate), errors["reminder_custom_date"]) { pickDate(remDate) { remDate = it } } }
                                Box(Modifier.weight(1f)) { PickerField("Time", remTime.ifBlank { null }) { pickTime(remTime) { remTime = it } } }
                            }

                            Label("Repeat")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pick("Doesn't repeat", recurrence.isEmpty(), KitMuted) { recurrence = "" }
                                o.objs("recurrences").forEach { r -> Pick(r.optString("label"), recurrence == r.optString("key"), Blue) { recurrence = r.optString("key") } }
                            }
                            if (recurrence == "custom") PickerField("Repeat until", prettyDay(recDate), errors["recurrence_custom_date"]) { pickDate(recDate) { recDate = it } }
                        }

                        Card("Who", "${people.size + otherPeople.values.sumOf { it.size }} selected") {
                            val chosen = people.map { id -> o.objs("people").firstOrNull { it.optInt("id") == id }?.optString("name") ?: assignNames["user:$id"] ?: "User $id" } +
                                otherPeople.flatMap { (t, ids) -> ids.sorted().map { assignNames["$t:$it"] ?: "#$it" } }
                            PickerField("Assign to", chosen.takeIf { it.isNotEmpty() }?.let { if (it.size <= 2) it.joinToString(", ") else "${it.take(2).joinToString(", ")} +${it.size - 2}" }) { peopleSheet = true }
                            PickerField("Department", o.objs("departments").firstOrNull { it.optInt("id") == departmentId }?.optString("name")) { departmentSheet = true }
                        }

                        if (editing) Card("Progress", "Where the task stands") {
                            Label("Status")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                o.objs("statuses").forEach { s -> Pick(s.optString("name"), statusId == s.optInt("id"), statusTint(s.optString("name"))) { statusId = s.optInt("id") } }
                            }
                            Label("Progress · ${progress.toInt()}%")
                            Slider(value = progress, onValueChange = { progress = it }, valueRange = 0f..100f, steps = 19, colors = SliderDefaults.colors(thumbColor = b.base, activeTrackColor = b.base))
                            Input("Reason it is overdue (optional)", overdueReason, { overdueReason = it }, singleLine = false)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                // the save bar stays in reach
                Box(Modifier.fillMaxWidth().background(com.example.gavanhomes.ui.common.KitCard).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)) {
                    Text(
                        if (saving) "Saving…" else if (editing) "Save task" else "Create task", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { save() }.padding(vertical = 15.dp),
                    )
                }
            }
        }

        // the task type, as a dropdown list
        if (typeSheet) ModalBottomSheet(onDismissRequest = { typeSheet = false }, containerColor = com.example.gavanhomes.ui.common.KitCard) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Task type", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                    Text("＋ New type", color = b.base, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { typeDialog = null to "" }.padding(8.dp))
                }
                val choices = listOf("" to "None") + (options?.objs("types").orEmpty().map { it.optString("key") to it.optString("label") })
                choices.forEach { (key, label) ->
                    val on = type == key
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else com.example.gavanhomes.ui.common.KitSoft)
                            .clickable { type = key; typeSheet = false; refreshName() }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (on) Text("✓", color = b.base, fontWeight = FontWeight.ExtraBold)
                        if (key.isNotEmpty()) {
                            Text("✎", color = KitMuted, fontSize = 17.sp, modifier = Modifier.clip(CircleShape).clickable { typeDialog = key to label }.padding(horizontal = 10.dp, vertical = 4.dp))
                            Text("🗑", fontSize = 15.sp, modifier = Modifier.clip(CircleShape).clickable { typeDelete = key to label }.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }

        // department: pick one
        if (departmentSheet) ModalBottomSheet(onDismissRequest = { departmentSheet = false }, containerColor = com.example.gavanhomes.ui.common.KitCard) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Department", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                    Text("＋ New department", color = b.base, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { deptDialog = true }.padding(8.dp))
                }
                (listOf(0 to "None") + (options?.objs("departments").orEmpty().map { it.optInt("id") to it.optString("name") })).forEach { (did, label) ->
                    val on = departmentId == did
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else com.example.gavanhomes.ui.common.KitSoft)
                            .clickable { departmentId = did; departmentSheet = false }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (on) Text("✓", color = b.base, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // people: pick a type (employee / supervisor / contractor / supplier / professional) and tick as many as needed — like the website's assignee tabs
        if (peopleSheet) {
            var lists by remember { mutableStateOf<Map<String, List<JSONObject>>>(emptyMap()) }
            var listing by remember { mutableStateOf(false) }
            LaunchedEffect(assignTab) {
                if (assignTab in listOf("contractor", "supplier", "professional")) visited = visited + assignTab
                if (assignTab !in lists) {
                    listing = true
                    val rows = runCatching { withContext(Dispatchers.IO) { api.get("/projects/$uuid/task-center/assignees?type=$assignTab").objs("data") } }.getOrDefault(emptyList())
                    lists = lists + (assignTab to rows); listing = false
                }
            }
            val kind = if (assignTab == "employee" || assignTab == "supervisor") "user" else assignTab
            ModalBottomSheet(onDismissRequest = { peopleSheet = false }, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Assign to", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                        Text("Done", color = b.base, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { peopleSheet = false }.padding(8.dp))
                    }
                    var query by remember { mutableStateOf("") }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("employee" to "Employee", "supervisor" to "Supervisor", "contractor" to "Contractor", "supplier" to "Supplier", "professional" to "Professional").forEach { (key, label) ->
                            val count = if (key == "employee" || key == "supervisor") people.size else otherPeople[key]?.size ?: 0
                            Pick(if (count > 0 && key != "supervisor") "$label · $count" else label, assignTab == key, b.base) { assignTab = key }
                        }
                    }
                    Input("Search by name", query, { query = it })
                    if (assignTab == "supervisor") Text("Supervisors are team members too — what you tick here is the same list as Employee.", color = KitMuted, fontSize = 12.sp)
                    Column(Modifier.height(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val everyone = lists[assignTab].orEmpty().filter { query.isBlank() || it.optString("name").contains(query.trim(), ignoreCase = true) }
                        if (listing) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else if (everyone.isEmpty()) Text(if (assignTab == "employee") "This project has no team members yet." else "Nobody to pick here.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                        everyone.forEach { p ->
                            val pid = p.optInt("id")
                            val on = if (kind == "user") pid in people else pid in (otherPeople[kind] ?: emptySet())
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else com.example.gavanhomes.ui.common.KitSoft)
                                    .clickable {
                                        assignNames = assignNames + ("$kind:$pid" to p.optString("name"))
                                        if (kind == "user") people = if (on) people - pid else people + pid
                                        else otherPeople = otherPeople + (kind to (if (on) (otherPeople[kind] ?: emptySet()) - pid else (otherPeople[kind] ?: emptySet()) + pid))
                                    }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                com.example.gavanhomes.ui.common.PersonAvatar(if (kind == "user") pid else 0, p.optString("name"), 34.dp)
                                Text(p.optString("name"), color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (on) b.base else Color.Transparent).border(2.dp, if (on) b.base else Color(0xFF9E9E9E), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                    if (on) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        typeDialog?.let { (key, label) ->
            NameDialog(if (key == null) "New task type" else "Rename task type", label, "Type name", if (key == null) "Add" else "Save", listBusy, onDismiss = { typeDialog = null }) { text ->
                listBusy = true
                scope.launch {
                    try {
                        val r = withContext(Dispatchers.IO) { if (key == null) api.send("POST", "/task-center/types", JSONObject().put("label", text)) else api.send("PUT", "/task-center/types/$key", JSONObject().put("label", text)) }
                        val newKey = r.optString("id")
                        typeDialog = null
                        refreshOptions { if (key == null && newKey.isNotBlank()) { type = newKey; refreshName() }; AppToast.show(if (key == null) "Task type added" else "Task type renamed") }
                    } catch (e: Exception) { AppToast.show((e as? ApiException)?.errors?.values?.firstOrNull { it.isNotBlank() } ?: e.message ?: "Could not save the task type") }
                    listBusy = false
                }
            }
        }
        typeDelete?.let { (key, label) ->
            SwalConfirm(
                title = "Delete task type?", text = "\"$label\" will be removed from the list. Tasks that already use it keep their type.", confirmText = "Yes, delete",
                onConfirm = {
                    typeDelete = null
                    scope.launch {
                        try { withContext(Dispatchers.IO) { api.delete("/task-center/types/$key") }; if (type == key) { type = ""; refreshName() }; refreshOptions { AppToast.show("Task type deleted") } }
                        catch (e: Exception) { AppToast.show(e.message ?: "Could not delete the task type") }
                    }
                }, onCancel = { typeDelete = null },
            )
        }
        if (deptDialog) NameDialog("New department", "", "Department name", "Add", listBusy, onDismiss = { deptDialog = false }) { text ->
            listBusy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/general-center/lists/departments", JSONObject().put("name", text)) }
                    deptDialog = false
                    refreshOptions { options?.objs("departments")?.lastOrNull { it.optString("name") == text }?.let { departmentId = it.optInt("id") }; AppToast.show("Department added") }
                } catch (e: Exception) { AppToast.show((e as? ApiException)?.errors?.values?.firstOrNull { it.isNotBlank() } ?: e.message ?: "Could not add the department") }
                listBusy = false
            }
        }
        bidPick?.let { i ->
            val row = bidItems.getOrNull(i)
            if (row != null) BidItemSheet(options?.objs(if (row.kind == "service") "services" else "materials").orEmpty(), row.kind, row.id, onPick = { id, name, unit ->
                bidItems = bidItems.toMutableList().also { it[i] = row.copy(id = id, name = name, unit = unit.ifBlank { row.unit }) }; bidPick = null
            }, onClose = { bidPick = null })
        }
        subPeople?.let { i ->
            val row = subs.getOrNull(i)
            if (row != null) SubPeopleSheet(options?.objs("people").orEmpty(), row.people, onChange = { v -> subs = subs.toMutableList().also { it[i] = row.copy(people = v) } }, onClose = { subPeople = null })
        }
        if (unitSheet) UnitSheet(api, uuid, unitId, onPick = { uid, label -> unitId = uid; unitLabel = label; unitSheet = false }, onClose = { unitSheet = false })
        if (leadSheet) LeadSheet(api, uuid, leadId, onPick = { lid, label -> leadId = lid; leadLabel = label; leadSheet = false; refreshName() }, onClose = { leadSheet = false })
    }
}

@Composable
internal fun Label(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 4.dp))
}

/** Search the project's leads and pick one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeadSheet(api: ApiClient, uuid: String, current: Int, onPick: (Int, String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(query) {
        delay(300); loading = true
        rows = runCatching { withContext(Dispatchers.IO) { api.get("/projects/$uuid/task-center/leads?search=${URLEncoder.encode(query.trim(), "UTF-8")}").objs("data") } }.getOrDefault(emptyList())
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Customer / lead", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Input("Search by name or phone", query, { query = it })
            Column(Modifier.height(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (loading) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else if (rows.isEmpty()) Text("No leads match.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                rows.forEach { r ->
                    Text(
                        r.optString("label"), color = KitInk, fontWeight = if (r.optInt("id") == current) FontWeight.ExtraBold else FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (r.optInt("id") == current) Blue.copy(alpha = .12f) else com.example.gavanhomes.ui.common.KitSoft)
                            .clickable { onPick(r.optInt("id"), r.optString("label")) }.padding(14.dp),
                    )
                }
            }
        }
    }
}

/** Search the project's units (flat / shop numbers) and pick one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitSheet(api: ApiClient, uuid: String, current: Int, onPick: (Int, String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(query) {
        delay(300); loading = true
        rows = runCatching { withContext(Dispatchers.IO) { api.get("/projects/$uuid/task-center/units?search=${URLEncoder.encode(query.trim(), "UTF-8")}").objs("data") } }.getOrDefault(emptyList())
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Unit", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Input("Search by flat / unit number", query, { query = it })
            Column(Modifier.height(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (loading) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else if (rows.isEmpty()) Text("No units match.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                rows.forEach { r ->
                    Text(
                        r.optString("label"), color = KitInk, fontWeight = if (r.optInt("id") == current) FontWeight.ExtraBold else FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (r.optInt("id") == current) Blue.copy(alpha = .12f) else com.example.gavanhomes.ui.common.KitSoft)
                            .clickable { onPick(r.optInt("id"), r.optString("label")) }.padding(14.dp),
                    )
                }
            }
        }
    }
}

/** A small box asking for one name (a new task type, a new department, a rename). */
@Composable
private fun NameDialog(title: String, initial: String, label: String, button: String, busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = { Input(label, text, { text = it }) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(text.trim()) }, enabled = !busy && text.isNotBlank()) { Text(if (busy) "Saving…" else button, fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** One row of a bid's items: material or service, which one, how many, in what unit. */
private data class BidRow(val kind: String, val id: Int, val name: String, val qty: String, val unit: String)

/** Pick a material or a service from the catalogue (with a search). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BidItemSheet(all: List<JSONObject>, kind: String, current: Int, onPick: (Int, String, String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val rows = all.filter { query.isBlank() || it.optString("name").contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (kind == "service") "Choose a service" else "Choose a material", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Input("Search", query, { query = it })
            Column(Modifier.height(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rows.isEmpty()) Text(if (all.isEmpty()) "No ${if (kind == "service") "services" else "materials"} in the catalogue yet." else "Nothing matches.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                rows.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (r.optInt("id") == current) Blue.copy(alpha = .12f) else com.example.gavanhomes.ui.common.KitSoft)
                            .clickable { onPick(r.optInt("id"), r.optString("name"), r.optString("unit").takeIf { it != "null" }.orEmpty()) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.optString("name"), color = KitInk, fontWeight = if (r.optInt("id") == current) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        r.optString("unit").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

/** One subtask line of the form (id 0 = new). */
private data class SubRow(val id: Int, val title: String, val priority: String, val due: String, val people: Set<Int>)

/** Tick the people for one subtask. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubPeopleSheet(all: List<JSONObject>, selected: Set<Int>, onChange: (Set<Int>) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Subtask assignee", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                Text("Done", color = Blue, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClose() }.padding(8.dp))
            }
            Column(Modifier.height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (all.isEmpty()) Text("This project has no team members yet.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                all.forEach { p ->
                    val pid = p.optInt("id"); val on = pid in selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) Blue.copy(alpha = .14f) else com.example.gavanhomes.ui.common.KitSoft).clickable { onChange(if (on) selected - pid else selected + pid) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.example.gavanhomes.ui.common.PersonAvatar(pid, p.optString("name"), 34.dp)
                        Text(p.optString("name"), color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        if (on) Text("✓", color = Blue, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/** Who did what and when: a dot per entry joined by a line, each with what changed (old → new). Shared by the task page's Activity tab and the list's "View history". */
@Composable
internal fun HistoryTimeline(history: List<JSONObject>) {
    history.forEachIndexed { i, h ->
        val tint = when (h.optString("dot")) { "ts-dot-create" -> KitGood; "ts-dot-delete" -> KitDanger; else -> Color(0xFFC47A1A) }
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            // the timeline: a dot per entry joined by a line
            Column(Modifier.width(18.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 5.dp).size(12.dp).clip(CircleShape).background(tint))
                if (i < history.lastIndex) Box(Modifier.weight(1f).width(2.dp).background(com.example.gavanhomes.ui.common.KitLine))
            }
            Column(Modifier.padding(start = 10.dp, bottom = if (i < history.lastIndex) 16.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.optString("label"), color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Text("${h.optString("user")} · ${h.optString("at").ifBlank { h.optString("ago") }}", color = KitMuted, fontSize = 12.sp)
                h.objs("changes").forEach { c ->
                    val old = c.str("old"); val new = c.str("new")
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(c.optString("field"), color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (old != null && new != null) {
                            Text(old, color = KitMuted, fontSize = 12.sp, textDecoration = TextDecoration.LineThrough, lineHeight = 16.sp)
                            Text("→ $new", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
                        } else Text(new ?: old ?: "—", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}
