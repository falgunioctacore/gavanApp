package com.example.gavanhomes.ui.hr

import android.app.DatePickerDialog
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiConfig
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.professionals.proDate
import com.example.gavanhomes.ui.projects.form.Choice
import com.example.gavanhomes.ui.projects.form.ChoiceSheet
import com.example.gavanhomes.ui.projects.form.FileField
import com.example.gavanhomes.ui.projects.form.FileSlot
import com.example.gavanhomes.ui.projects.form.FormCard
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.projects.form.readPicked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Shown = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * Create / edit a record of a standard HR module (id == null → create). The form is built from the module's own field list
 * (GET /hr/{module}?schema=1), so it always matches the website: text, email, number, date, choice, long text and file inputs.
 * Saved with POST /hr/{module} · PUT /hr/{module}/{id} (multipart when the module takes a file).
 */
@Composable
fun HrFormScreen(api: ApiClient, b: Branding, slug: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = id != null
    val text = remember { mutableStateMapOf<String, String>() }
    val file = remember { FileSlot() }
    var schema by remember { mutableStateOf(HrSchemas.cache[slug]) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<List<String>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<HrField?>(null) }
    var initial by remember { mutableStateOf<Map<String, String>?>(null) }   // the values as loaded, to tell whether anything was changed

    var target by remember { mutableStateOf<FileSlot?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = target; target = null
        if (uri != null && slot != null) readPicked(context, uri)?.let { slot.picked = it }
    }
    val view: (String) -> Unit = { path ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${ApiConfig.BASE_URL}/storage/$path"))) }
            .onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(attempt) {
        loading = true; loadError = null
        try {
            if (schema == null) {
                schema = parseSchema(withContext(Dispatchers.IO) { api.get("/hr/$slug?schema=1") }.getJSONObject("schema")).also { HrSchemas.cache[slug] = it }
            }
            if (id != null) {
                val d = withContext(Dispatchers.IO) { api.detail("/hr/$slug/$id") }
                schema!!.fields.forEach { f -> text[f.name] = d.str(f.name).orEmpty().let { v -> if (f.type == "date") v.take(10) else v } }
                file.existing = d.str("file")
            } else schema!!.fields.firstOrNull { it.type == "select" && it.required && it.options?.isNotEmpty() == true }?.let { f ->
                // a new record starts on the first status / stage, as the website's form does
                if (f.name.contains("status") || f.name == "stage") text[f.name] = f.options!!.first().first
            }
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
        initial = text.toMap()
        loading = false
    }

    fun save() {
        val s = schema ?: return
        banner = emptyList(); errors = emptyMap()
        val problems = s.fields.filter { it.required && it.type != "file" && text[it.name].isNullOrBlank() }.associate { it.name to "${it.label} is required" }
        if (problems.isNotEmpty()) { errors = problems; banner = problems.values.toList(); return }

        saving = true
        scope.launch {
            try {
                val dataFields = s.fields.filter { it.type != "file" }
                if (s.file) {
                    val parts = mutableListOf<FormPart>()
                    if (editing) parts += FormPart("_method", "PUT")
                    dataFields.forEach { parts += FormPart(it.name, text[it.name].orEmpty()) }
                    file.picked?.let { p -> parts += FormPart(s.fields.first { it.type == "file" }.name, fileName = p.name, mime = p.mime, bytes = p.bytes) }
                    withContext(Dispatchers.IO) { api.sendForm(if (editing) "/hr/$slug/$id" else "/hr/$slug", parts) }
                } else {
                    val body = JSONObject()
                    dataFields.forEach { body.put(it.name, text[it.name]?.trim().orEmpty().ifEmpty { JSONObject.NULL }) }
                    withContext(Dispatchers.IO) { if (editing) api.send("PUT", "/hr/$slug/$id", body) else api.send("POST", "/hr/$slug", body) }
                }
                Toast.makeText(context, "${s.singular} ${if (editing) "saved" else "created"}", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: ApiException) {
                errors = e.errors
                banner = if (e.errors.isEmpty()) listOf(e.message ?: "Could not save") else e.errors.values.filter { it.isNotBlank() }.distinct().take(4)
            } catch (e: Exception) { banner = listOf(e.message ?: "Could not save") }
            saving = false
        }
    }

    val s = schema
    val back = leaveGuard(initial != null && (text.toMap() != initial || file.picked != null), onBack)
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, if (s == null) "Form" else (if (editing) "Edit " else "New ") + s.singular.lowercase(), back, compact = true)

        when {
            loading || s == null && loadError == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            loadError != null || s == null -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(loadError ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
            else -> {
                if (banner.isNotEmpty()) Column(
                    Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).padding(12.dp),
                ) { banner.forEach { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        FormCard(if (editing) "Details" else "New ${s.singular.lowercase()}") {
                            s.fields.forEach { f ->
                                val label = f.label + if (f.required) " *" else ""
                                val error = errors[f.name]
                                when (f.type) {
                                    "select" -> PickerField(label, f.options?.firstOrNull { it.first == text[f.name] }?.second, error) { picker = f }
                                    "date" -> {
                                        val current = proDate(text[f.name])
                                        PickerField(label, current?.format(Shown), error) {
                                            val d = current ?: LocalDate.now()
                                            DatePickerDialog(context, { _, y, m, day -> text[f.name] = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                                        }
                                    }
                                    "file" -> FileField(f.label, file, error, { slot -> target = slot; launcher.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }, view)
                                    "textarea" -> Input(label, text[f.name].orEmpty(), { text[f.name] = it }, error, singleLine = false)
                                    "number" -> Input(label, text[f.name].orEmpty(), { text[f.name] = it }, error, keyboard = KeyboardType.Decimal)
                                    "email" -> Input(label, text[f.name].orEmpty(), { text[f.name] = it }, error, keyboard = KeyboardType.Email)
                                    else -> Input(label, text[f.name].orEmpty(), { text[f.name] = it }, error)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(b.base, b.deep))).clickable(enabled = !saving) { save() },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (saving) "Saving…" else if (editing) "Save changes" else "Create ${s.singular.lowercase()}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
        }
    }

    picker?.let { f ->
        val options = f.options.orEmpty()
        val choices = options.mapIndexed { i, o -> Choice(i, o.second) }
        ChoiceSheet(
            f.label, choices, setOfNotNull(options.indexOfFirst { it.first == text[f.name] }.takeIf { it >= 0 }), multi = false, allowNone = !f.required, onDismiss = { picker = null },
        ) { ids -> text[f.name] = ids.firstOrNull()?.let { options[it].first }.orEmpty() }
    }
}
