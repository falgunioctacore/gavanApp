package com.example.gavanhomes.ui.targets

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.example.gavanhomes.ui.common.PillTab
import com.example.gavanhomes.ui.common.PillTabs
import com.example.gavanhomes.ui.common.ScrollTopButton
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.projects.form.readPicked
import com.example.gavanhomes.ui.tasks.CallChip
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.Label
import com.example.gavanhomes.ui.tasks.Meter
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.TaskAssignTab
import com.example.gavanhomes.ui.tasks.TaskDiscussion
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import com.example.gavanhomes.ui.tasks.priorityTint
import com.example.gavanhomes.ui.tasks.statusTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Blue = Color(0xFF1E5FAE)

// ───────────────────────────── target details ─────────────────────────────

/** One target, like the website's target page: Details / Files / Discussion / Activity / Assign. Data: GET /target-center/{id} · POST …/status · …/quick · …/documents. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TargetDetailScreen(api: ApiClient, b: Branding, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit, initialTab: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(initialTab) }
    var busy by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var askDeleteFile by remember { mutableStateOf<JSONObject?>(null) }
    var departmentSheet by remember { mutableStateOf(false) }

    LaunchedEffect(id, version, attempt) {
        error = null
        try {
            val d = withContext(Dispatchers.IO) { api.detail("/target-center/$id") }
            t = d
            if (options == null) options = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/${d.optString("project_uuid")}/target-center/options") } }.getOrNull()
        } catch (e: Exception) { error = e.message ?: "Could not load the target" }
    }
    BackHandler(enabled = true) { onBack() }

    fun act(success: String?, call: () -> Unit, after: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { call() }
                if (success != null) Toast.makeText(context, success, Toast.LENGTH_SHORT).show()
                after(); onChanged(); attempt++
            } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }
    fun quick(vararg pairs: Pair<String, Any?>) = act("Saved", { api.send("POST", "/target-center/$id/quick", JSONObject().apply { pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }) })

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val f = uri?.let { readPicked(context, it) } ?: return@rememberLauncherForActivityResult
        act("File uploaded", { api.sendForm("/target-center/$id/documents", listOf(FormPart("document", fileName = f.name, mime = f.mime, bytes = f.bytes))) })
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        val d = t
        if (d == null) {
            HeroBar(b, "Target", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = b.base)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
                }
            }
            return@Column
        }
        val can = d.optJSONObject("can")
        val canEdit = can?.optBoolean("edit") == true
        val canDelete = can?.optBoolean("delete") == true
        val files = d.objs("files")

        val pageScroll = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize().verticalScroll(pageScroll).imePadding()) {
                HeroBar(
                    b, "Target", onBack,
                    actions = {
                        if (canEdit) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                        if (canDelete) DeleteButton { askDelete = true }
                    },
                ) {
                    Column {
                        Text(d.optString("name"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(listOfNotNull(d.str("target_no"), d.str("project")).joinToString(" · "), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        HeroChips(*listOfNotNull(d.str("status"), d.str("priority")?.let { "$it priority" }, prettyDay(d.str("due_date"))?.let { "Due $it" }, d.str("target_type")).toTypedArray())
                    }
                }

                PillTabs(listOf(PillTab("Details"), PillTab("Files", files.size), PillTab("Discussion"), PillTab("Activity", d.objs("history").size), PillTab("Assign")), tab, b.base) { tab = it }

                when (listOf("overview", "files", "discussion", "activity", "assign")[tab]) {
                    "overview" -> {
                        if (canEdit) Card("Status", "Tap to change") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                d.objs("statuses").forEach { s ->
                                    val on = s.optInt("id") == d.optInt("status_id")
                                    Pick(s.optString("name"), on, statusTint(s.optString("name"))) { if (!on) act("Status changed", { api.send("POST", "/target-center/$id/status", JSONObject().put("status_id", s.optInt("id"))) }) }
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
                            PickerField("Due date", prettyDay(d.str("due_date"))) {
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
                            Fact("Target no.", d.str("target_no"))
                            Fact("Type", d.str("target_type"))
                            Fact("Target value", d.str("target_value"))
                            Fact("Category", d.str("category"))
                            Fact("Lead employee", d.str("lead_employee"))
                            Fact("Budget code", d.str("budget_code"))
                            Fact("Bonus", d.str("bonus"))
                            Fact("Tower / block", d.str("tower_block"))
                            Fact("Unit / floor", d.str("unit_floor"))
                            Fact("Start date", prettyDay(d.str("start_date")))
                            Fact("Completed", prettyDay(d.str("completed_date")))
                            Fact("GPS verification", if (d.optBoolean("gps_verification_required")) "Required" else null)
                            Fact("Photo uploads", if (d.optBoolean("mandatory_photo_uploads")) "Mandatory" else null)
                            Fact("Four-tier approvals", if (d.optBoolean("four_tier_approvals_required")) "Required" else null)
                            Fact("Created", listOfNotNull(prettyDay(d.str("created_at")), d.str("created_by")?.let { "by $it" }).joinToString(" "))
                        }
                        val people = d.objs("assignees")
                        Card("Assigned to", "${people.size} ${if (people.size == 1) "person" else "people"}") {
                            if (people.isEmpty()) Text("Nobody yet.", color = KitMuted, fontSize = 14.sp)
                            people.forEach { p ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Initials(p.optString("name"), size = 38.dp)
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(p.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        p.str("phone")?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
                                    }
                                    p.str("phone")?.let { CallChip(it) }
                                }
                            }
                        }
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
                    }

                    "files" -> Card("Files", if (files.isEmpty()) "Documents for this target" else "${files.size} attached") {
                        if (files.isEmpty()) Text("No files yet.", color = KitMuted, fontSize = 14.sp)
                        files.forEach { f ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Blue.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                                    Text((f.str("name")?.substringAfterLast('.', "") ?: "").take(4).uppercase().ifEmpty { "FILE" }, color = Blue, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(f.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    f.optLong("size").takeIf { it > 0 }?.let { Text("${it / 1024} KB", color = KitMuted, fontSize = 12.sp) }
                                }
                                Text("Open", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).clickable {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(f.optString("url")))) }.onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
                                }.padding(horizontal = 14.dp, vertical = 8.dp))
                                if (canDelete) Text("✕", color = KitDanger, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable { askDeleteFile = f }.padding(8.dp))
                            }
                        }
                        if (canEdit) Text(
                            "+  Upload a file", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitInk).clickable { pickFile.launch("*/*") }.padding(vertical = 13.dp),
                        )
                        Text("PDF, Office, image, CSV, TXT or ZIP · up to 5 MB", color = KitMuted, fontSize = 11.sp)
                    }

                    "discussion" -> TaskDiscussion(api, b, id, base = "/target-center", onChanged = { attempt++; onChanged() })
                    "assign" -> TaskAssignTab(api, b, id, d.optString("name"), canEdit, base = "/target-center", onChanged = { attempt++; onChanged() })

                    else -> {
                        val history = d.objs("history")
                        Card("Activity", if (history.isEmpty()) "Nothing has happened yet" else "Who did what, and when") {
                            if (history.isEmpty()) Text("No activity yet.", color = KitMuted, fontSize = 14.sp)
                            history.forEachIndexed { i, h ->
                                val tint = when (h.optString("dot")) { "ts-dot-create" -> KitGood; "ts-dot-delete" -> KitDanger; else -> Color(0xFFC47A1A) }
                                Row(Modifier.height(IntrinsicSize.Min)) {
                                    Column(Modifier.width(18.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.padding(top = 5.dp).size(12.dp).clip(CircleShape).background(tint))
                                        if (i < history.lastIndex) Box(Modifier.weight(1f).width(2.dp).background(Color(0xFFE6E3DD)))
                                    }
                                    Column(Modifier.padding(start = 10.dp, bottom = if (i < history.lastIndex) 16.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(h.optString("label"), color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 4.dp))
                                        Text("${h.optString("user")} · ${h.optString("at").ifBlank { h.optString("ago") }}", color = KitMuted, fontSize = 12.sp)
                                        h.objs("changes").forEach { c ->
                                            val old = c.str("old"); val new = c.str("new")
                                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F5F1)).padding(horizontal = 10.dp, vertical = 7.dp)) {
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
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
            ScrollTopButton(pageScroll)
        }

        if (departmentSheet) ModalBottomSheet(onDismissRequest = { departmentSheet = false }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Department", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                (options?.objs("departments").orEmpty()).forEach { dep ->
                    val on = dep.optInt("id") == d.optInt("department_id")
                    Text(
                        dep.optString("name"), color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else Color(0xFFF7F5F1))
                            .clickable { departmentSheet = false; quick("department_id" to dep.optInt("id")) }.padding(14.dp),
                    )
                }
            }
        }

        if (askDelete) SwalConfirm(
            title = "Delete this target?", text = "It will be removed from the project.", confirmText = "Yes, delete it",
            onConfirm = { askDelete = false; act("Target deleted", { api.delete("/target-center/$id") }) { onBack() } }, onCancel = { askDelete = false },
        )
        askDeleteFile?.let { f -> SwalConfirm(
            title = "Delete this file?", text = f.optString("name"), confirmText = "Yes, delete it",
            onConfirm = { askDeleteFile = null; act("File deleted", { api.delete("/target-center/$id/documents/${f.optInt("id")}") }) }, onCancel = { askDeleteFile = null },
        ) }
    }
}

// ───────────────────────────── create / edit ─────────────────────────────

/** New target / edit target. Data: GET …/target-center/options · GET /target-center/{id} · POST …/target-center · PUT /target-center/{id}. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TargetFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = id != null

    var options by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Medium") }
    var start by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var statusId by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var categoryId by remember { mutableIntStateOf(0) }
    var departmentId by remember { mutableIntStateOf(0) }
    var type by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var leadId by remember { mutableIntStateOf(0) }
    var budget by remember { mutableStateOf("") }
    var tower by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var bonus by remember { mutableStateOf("") }
    var gps by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf(false) }
    var tier by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // which dropdown is open: "category" | "department" | "lead" | "people"
    var sheet by remember { mutableStateOf<String?>(null) }
    var initial by remember { mutableStateOf<List<Any>?>(null) }
    fun snapshot(): List<Any> = listOf(name, description, priority, start, due, statusId, progress.toInt(), categoryId, departmentId, type, value, leadId, budget, tower, unit, bonus, gps, photo, tier, people)

    LaunchedEffect(attempt) {
        loadError = null
        try {
            val o = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/target-center/options") }
            if (id != null) {
                val d = withContext(Dispatchers.IO) { api.detail("/target-center/$id") }
                name = d.optString("name"); description = d.str("description").orEmpty(); priority = d.str("priority") ?: "Medium"
                start = d.str("start_date").orEmpty().take(10); due = d.str("due_date").orEmpty().take(10); statusId = d.optInt("status_id"); progress = d.optInt("progress").toFloat()
                categoryId = d.optInt("category_id"); departmentId = d.optInt("department_id"); type = d.str("target_type").orEmpty(); value = d.str("target_value").orEmpty()
                leadId = d.optInt("lead_employee_id"); budget = d.str("budget_code").orEmpty(); tower = d.str("tower_block").orEmpty(); unit = d.str("unit_floor").orEmpty(); bonus = d.str("bonus").orEmpty()
                gps = d.optBoolean("gps_verification_required"); photo = d.optBoolean("mandatory_photo_uploads"); tier = d.optBoolean("four_tier_approvals_required")
                people = d.optJSONArray("assignee_ids")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
            }
            options = o
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
        initial = snapshot()
    }

    fun save() {
        val problems = mutableMapOf<String, String>()
        if (name.isBlank()) problems["name"] = "Enter a target name"
        if (description.isBlank()) problems["description"] = "Enter a description"
        if (start.isNotBlank() && due.isNotBlank() && due < start) problems["due_date"] = "Due date must be on or after the start date"
        if (problems.isNotEmpty()) { errors = problems; banner = problems.values.first(); return }
        val nul = JSONObject.NULL
        val body = JSONObject().put("name", name.trim()).put("description", description.trim()).put("priority", priority)
            .put("start_date", start.ifBlank { nul }).put("due_date", due.ifBlank { nul })
            .put("category_id", if (categoryId > 0) categoryId else nul).put("department_id", if (departmentId > 0) departmentId else nul)
            .put("target_type", type.ifBlank { nul }).put("target_value", value.ifBlank { nul }).put("lead_employee_id", if (leadId > 0) leadId else nul)
            .put("budget_code", budget.ifBlank { nul }).put("tower_block", tower.ifBlank { nul }).put("unit_floor", unit.ifBlank { nul }).put("bonus", bonus.ifBlank { nul })
            .put("gps_verification_required", gps).put("mandatory_photo_uploads", photo).put("four_tier_approvals_required", tier)
            .put("assignee_id", JSONArray(people.sorted()))
        if (editing) { if (statusId > 0) body.put("status_id", statusId); body.put("progress", progress.toInt()) }
        errors = emptyMap(); banner = null; saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (id != null) api.send("PUT", "/target-center/$id", body) else api.send("POST", "/projects/$uuid/target-center", body) }
                Toast.makeText(context, if (editing) "Target saved" else "Target created", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: ApiException) {
                errors = e.errors; banner = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message
            } catch (e: Exception) { banner = e.message ?: "Could not save" }
            saving = false
        }
    }

    fun pickDate(current: String, set: (String) -> Unit) {
        val d = runCatching { LocalDate.parse(current.take(10)) }.getOrDefault(LocalDate.now())
        DatePickerDialog(context, { _, y, m, day -> set(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    val back = leaveGuard(initial != null && snapshot() != initial, onBack)
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, if (editing) "Edit target" else "New target", back, compact = true)
        val o = options
        when {
            loadError != null -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(loadError ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
            o == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            else -> {
                val peopleAll = o.objs("people")
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 8.dp)) {
                        banner?.let { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).padding(12.dp)) }

                        Card("What", "Name the target and describe it") {
                            Input("Target name", name, { name = it }, errors["name"])
                            Input("Description", description, { description = it }, errors["description"], singleLine = false)
                            Label("Priority")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Low", "Medium", "High", "Urgent").forEach { p -> Pick(p, priority == p, priorityTint(p)) { priority = p } } }
                        }

                        Card("When", "Start and due dates") {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) { PickerField("Start date", prettyDay(start), errors["start_date"]) { pickDate(start) { start = it } } }
                                Box(Modifier.weight(1f)) { PickerField("Due date", prettyDay(due), errors["due_date"]) { pickDate(due) { due = it } } }
                            }
                            if (start.isNotBlank() || due.isNotBlank()) Text("Clear dates", color = KitMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { start = ""; due = "" })
                        }

                        Card("Target", "Type, value and where it applies") {
                            Label("Target type")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pick("None", type.isEmpty(), KitMuted) { type = "" }
                                (o.optJSONArray("types")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()).forEach { tp -> Pick(tp, type == tp, Blue) { type = tp } }
                            }
                            Input("Target value", value, { value = it }, errors["target_value"])
                            PickerField("Category", o.objs("categories").firstOrNull { it.optInt("id") == categoryId }?.optString("name")) { sheet = "category" }
                            PickerField("Department", o.objs("departments").firstOrNull { it.optInt("id") == departmentId }?.optString("name")) { sheet = "department" }
                            Input("Budget code", budget, { budget = it })
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) { Input("Tower / block", tower, { tower = it }) }
                                Box(Modifier.weight(1f)) { Input("Unit / floor", unit, { unit = it }) }
                            }
                            Input("Bonus", bonus, { bonus = it }, singleLine = false)
                        }

                        Card("Who", "${people.size} assigned") {
                            PickerField("Lead employee", peopleAll.firstOrNull { it.optInt("id") == leadId }?.optString("name")) { sheet = "lead" }
                            val chosen = peopleAll.filter { it.optInt("id") in people }.map { it.optString("name") }
                            PickerField("Assign to", chosen.takeIf { it.isNotEmpty() }?.let { if (it.size <= 2) it.joinToString(", ") else "${it.take(2).joinToString(", ")} +${it.size - 2}" }) { sheet = "people" }
                        }

                        Card("Requirements") {
                            Flag("GPS verification required", gps, b.base) { gps = it }
                            Flag("Mandatory photo uploads", photo, b.base) { photo = it }
                            Flag("Four-tier approvals required", tier, b.base) { tier = it }
                        }

                        if (editing) Card("Progress", "Where the target stands") {
                            Label("Status")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                o.objs("statuses").forEach { s -> Pick(s.optString("name"), statusId == s.optInt("id"), statusTint(s.optString("name"))) { statusId = s.optInt("id") } }
                            }
                            Label("Progress · ${progress.toInt()}%")
                            Slider(value = progress, onValueChange = { progress = it }, valueRange = 0f..100f, steps = 19, colors = SliderDefaults.colors(thumbColor = b.base, activeTrackColor = b.base))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)) {
                    Text(
                        if (saving) "Saving…" else if (editing) "Save target" else "Create target", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { save() }.padding(vertical = 15.dp),
                    )
                }

                // single-choice dropdowns
                fun single(title: String, current: Int, rows: List<Pair<Int, String>>, set: (Int) -> Unit): @Composable () -> Unit = {
                    ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White) {
                        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                            (listOf(0 to "None") + rows).forEach { (rid, label) ->
                                val on = current == rid
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else Color(0xFFF7F5F1)).clickable { set(rid); sheet = null }.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(label, color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (on) Text("✓", color = b.base, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
                when (sheet) {
                    "category" -> single("Category", categoryId, o.objs("categories").map { it.optInt("id") to it.optString("name") }) { categoryId = it }()
                    "department" -> single("Department", departmentId, o.objs("departments").map { it.optInt("id") to it.optString("name") }) { departmentId = it }()
                    "lead" -> single("Lead employee", leadId, peopleAll.map { it.optInt("id") to it.optString("name") }) { leadId = it }()
                    "people" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Assign to", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                                Text("Done", color = b.base, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { sheet = null }.padding(8.dp))
                            }
                            Column(Modifier.height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (peopleAll.isEmpty()) Text("This project has no team members yet.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                                peopleAll.forEach { p ->
                                    val pid = p.optInt("id"); val on = pid in people
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else Color(0xFFF7F5F1)).clickable { people = if (on) people - pid else people + pid }.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Initials(p.optString("name"), size = 34.dp)
                                        Text(p.optString("name"), color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (on) b.base else Color.Transparent).border(2.dp, if (on) b.base else Color(0xFF9E9E9E), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                            if (on) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun Flag(label: String, on: Boolean, tint: Color, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { set(!on) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (on) tint else Color.Transparent).border(2.dp, if (on) tint else Color(0xFF9E9E9E), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            if (on) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
