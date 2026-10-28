package com.example.gavanhomes.ui.projects

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiConfig
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.SegTabs
import com.example.gavanhomes.ui.projects.form.BasicInfoStep
import com.example.gavanhomes.ui.projects.form.Choice
import com.example.gavanhomes.ui.projects.form.ChoiceSheet
import com.example.gavanhomes.ui.projects.form.FileSlot
import com.example.gavanhomes.ui.projects.form.InvColumn
import com.example.gavanhomes.ui.projects.form.InventoryStep
import com.example.gavanhomes.ui.projects.form.ProjectForm
import com.example.gavanhomes.ui.projects.form.UnitState
import com.example.gavanhomes.ui.projects.form.readPicked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Create / edit a project (uuid == null → create) — the website's whole form, in its two steps:
 *   1. Basic information: project, team, location, developer & partners, RERA, pricing, payment schedule
 *   2. Inventory: the units of each selected property type, with their documents
 * Documents are picked on the phone and uploaded with the save (multipart, like the website).
 * Data: GET /projects/options (pickers, unit columns), GET /projects/{uuid} (current values), POST /projects[/{uuid}] (save).
 */
@Composable
fun ProjectFormScreen(api: ApiClient, b: Branding, uuid: String?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = uuid != null
    val form = remember { ProjectForm() }

    var statuses by remember { mutableStateOf<List<Choice>>(emptyList()) }
    var typeNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var people by remember { mutableStateOf<List<Choice>>(emptyList()) }
    var columns by remember { mutableStateOf<Map<String, List<InvColumn>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    var step by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<List<String>>(emptyList()) }
    var picker by remember { mutableStateOf<String?>(null) }   // "status" | "types" | "leader" | "members"

    // ── document picker: one launcher; each button says which slot it fills ──
    var target by remember { mutableStateOf<FileSlot?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = target; target = null
        if (uri != null && slot != null) readPicked(context, uri)?.let { slot.picked = it }
    }
    val pick: (FileSlot) -> Unit = { target = it; launcher.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }
    val view: (String) -> Unit = { path ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${ApiConfig.BASE_URL}/storage/$path"))) }
            .onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(attempt) {
        loading = true; loadError = null
        try {
            val opts = withContext(Dispatchers.IO) { api.projectOptions() }
            statuses = opts.getJSONArray("statuses").let { a -> (0 until a.length()).map { Choice(a.getJSONObject(it).optInt("id"), a.getJSONObject(it).optString("name")) } }
            typeNames = opts.getJSONArray("types").let { a -> (0 until a.length()).map { a.getString(it) } }
            people = opts.getJSONArray("users").let { a -> (0 until a.length()).map { Choice(a.getJSONObject(it).optInt("id"), a.getJSONObject(it).optString("name")) } }
            columns = opts.getJSONObject("inventory_columns").let { o ->
                o.keys().asSequence().associateWith { t ->
                    val arr = o.getJSONArray(t)
                    (0 until arr.length()).map { i ->
                        val c = arr.getJSONObject(i)
                        val ops = c.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                        InvColumn(c.getString("key"), c.getString("label"), c.getString("type"), ops, c.optBoolean("required"))
                    }
                }
            }

            if (uuid != null) {
                val d = withContext(Dispatchers.IO) { api.detail("/projects/$uuid") }
                form.load(d.getJSONObject("form"))
            } else if (form.get("status_id").isBlank()) {
                form.set("status_id", (statuses.firstOrNull { it.label.equals("Pending", true) } ?: statuses.firstOrNull())?.id?.toString().orEmpty())
            }
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
        loading = false
    }

    fun save() {
        banner = emptyList(); form.errors = emptyMap()
        if (form.get("name").isBlank()) { form.errors = mapOf("name" to "Enter a project name"); step = 0; banner = listOf("Please enter the project name."); return }
        if (form.get("status_id").isBlank()) { form.errors = mapOf("status_id" to "Choose a status"); step = 0; banner = listOf("Please choose a status."); return }
        saving = true
        scope.launch {
            try {
                val parts = form.parts(editing, columns)
                withContext(Dispatchers.IO) { api.sendForm(if (uuid == null) "/projects" else "/projects/$uuid", parts) }
                Toast.makeText(context, if (editing) "Project saved" else "Project created", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: ApiException) {
                form.errors = e.errors
                banner = if (e.errors.isEmpty()) listOf(e.message ?: "Could not save") else e.errors.values.filter { it.isNotBlank() }.distinct().take(4)
                step = if (e.errors.keys.any { !it.startsWith("inventory") } || e.errors.isEmpty()) 0 else 1
            } catch (e: Exception) { banner = listOf(e.message ?: "Could not save") }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        // compact: the form is long, so the header stays slim (the project's name is in the first field)
        HeroBar(b, if (editing) "Edit project" else "New project", onBack, compact = true)

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            loadError != null -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(loadError ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
            else -> {
                SegTabs(listOf("Basic information", "Inventory · ${form.unitCount}"), step) { step = it }

                if (banner.isNotEmpty()) Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).padding(12.dp),
                ) { banner.forEach { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    if (step == 0) Column(
                        Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                    ) {
                        BasicInfoStep(form, statuses, people, b, context, pick, view) { picker = it }
                        Spacer(Modifier.height(8.dp))
                    } else Box(Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding()) {
                        InventoryStep(form, columns, b, pick, view)
                    }
                }

                // bottom bar: switch step + save
                Row(
                    Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (step == 0) "Inventory →" else "← Details", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF1EFEB)).clickable { step = 1 - step }.padding(top = 17.dp),
                    )
                    Box(
                        Modifier.weight(2f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(b.base, b.deep))).clickable(enabled = !saving) { save() },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (saving) "Saving…" else if (editing) "Save changes" else "Create project", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
        }
    }

    // ── option pickers ──
    when (picker) {
        "status" -> ChoiceSheet("Status", statuses, setOf(form.get("status_id").toIntOrNull() ?: -1), multi = false, onDismiss = { picker = null }) { form.set("status_id", it.firstOrNull()?.toString().orEmpty()) }
        "leader" -> ChoiceSheet("Team leader", people, setOf(form.get("leader_id").toIntOrNull() ?: -1), multi = false, allowNone = true, onDismiss = { picker = null }) { form.set("leader_id", it.firstOrNull()?.toString().orEmpty()) }
        "members" -> ChoiceSheet("Members", people, form.memberIds, multi = true, onDismiss = { picker = null }) { form.memberIds = it }
        "types" -> {
            val choices = typeNames.mapIndexed { i, t -> Choice(i, t) }
            ChoiceSheet("Project types", choices, choices.filter { it.label in form.types }.map { it.id }.toSet(), multi = true, onDismiss = { picker = null }) { ids ->
                form.types = choices.filter { it.id in ids }.map { it.label }.toSet()
            }
        }
    }
}
