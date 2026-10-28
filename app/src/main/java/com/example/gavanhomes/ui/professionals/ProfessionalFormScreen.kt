package com.example.gavanhomes.ui.professionals

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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TextKeys = listOf(
    "name", "company_name", "email", "phone", "address", "city", "state", "pincode", "license_number", "registration_number",
    "gst_number", "pan_number", "license_expiry", "total_year_of_experience", "expertise_area", "status", "password", "password_confirmation",
)
private val Shown = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * Create / edit a professional (uuid == null → create) — the website's form: profile, contact, licence & business, documents, login.
 * Data: GET /professionals/options, GET /professionals/{uuid}, POST /professionals · POST + _method=PUT /professionals/{uuid} (multipart).
 */
@Composable
fun ProfessionalFormScreen(api: ApiClient, b: Branding, uuid: String?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = uuid != null
    val text = remember { mutableStateMapOf<String, String>() }
    val photo = remember { FileSlot() }
    val certification = remember { FileSlot() }
    var typeId by remember { mutableStateOf<Int?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<List<String>>(emptyList()) }

    var types by remember { mutableStateOf<List<Choice>>(emptyList()) }
    var statuses by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<String?>(null) }
    var initial by remember { mutableStateOf<String?>(null) }   // the values as loaded, to tell whether anything was changed

    // the form types another person's password — keep the phone's password manager from offering to save it
    val host = LocalView.current
    DisposableEffect(host) {
        val before = host.importantForAutofill
        host.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose { host.importantForAutofill = before }
    }

    var target by remember { mutableStateOf<FileSlot?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = target; target = null
        if (uri != null && slot != null) readPicked(context, uri)?.let { slot.picked = it }
    }
    val pickPhoto: (FileSlot) -> Unit = { target = it; launcher.launch(arrayOf("image/jpeg", "image/png")) }
    val pickDoc: (FileSlot) -> Unit = { target = it; launcher.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }
    val view: (String) -> Unit = { path ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${ApiConfig.BASE_URL}/storage/$path"))) }
            .onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(attempt) {
        loading = true; loadError = null
        try {
            val o = withContext(Dispatchers.IO) { api.detail("/professionals/options") }
            types = o.optJSONArray("types")?.let { a -> (0 until a.length()).map { Choice(a.getJSONObject(it).getInt("id"), a.getJSONObject(it).getString("name")) } } ?: emptyList()
            statuses = o.optJSONArray("statuses")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            if (uuid != null) {
                val d = withContext(Dispatchers.IO) { api.detail("/professionals/$uuid") }
                TextKeys.forEach { text[it] = d.str(it).orEmpty() }
                text["total_year_of_experience"] = d.str("experience").orEmpty()
                text["license_expiry"] = d.str("license_expiry").orEmpty()
                typeId = d.optInt("type_id").takeIf { it != 0 }
                photo.existing = d.str("photo"); certification.existing = d.str("certification")
            } else if (text["status"].isNullOrBlank()) text["status"] = "active"
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
        initial = text.toMap().toString() + typeId
        loading = false
    }

    fun field(key: String) = text[key].orEmpty()

    fun save() {
        banner = emptyList(); errors = emptyMap()
        val problems = mutableMapOf<String, String>()
        if (typeId == null) problems["professional_type_id"] = "Choose a type"
        if (field("name").isBlank()) problems["name"] = "Enter a name"
        if (!editing && field("password").length < 6) problems["password"] = "At least 6 characters"
        if (field("password").isNotEmpty() && field("password") != field("password_confirmation")) problems["password_confirmation"] = "Passwords don't match"
        if (problems.isNotEmpty()) { errors = problems; banner = problems.values.toList(); return }

        val parts = mutableListOf<FormPart>()
        if (editing) parts += FormPart("_method", "PUT")
        parts += FormPart("professional_type_id", typeId.toString())
        TextKeys.filter { it != "password" && it != "password_confirmation" }.forEach { parts += FormPart(it, field(it)) }
        if (field("password").isNotEmpty()) { parts += FormPart("password", field("password")); parts += FormPart("password_confirmation", field("password_confirmation")) }
        photo.picked?.let { parts += FormPart("profile_photo_url", fileName = it.name, mime = it.mime, bytes = it.bytes) }
        certification.picked?.let { parts += FormPart("certification_url", fileName = it.name, mime = it.mime, bytes = it.bytes) }

        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.sendForm(if (uuid == null) "/professionals" else "/professionals/$uuid", parts) }
                Toast.makeText(context, if (editing) "Professional saved" else "Professional created", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: ApiException) {
                errors = e.errors
                banner = if (e.errors.isEmpty()) listOf(e.message ?: "Could not save") else e.errors.values.filter { it.isNotBlank() }.distinct().take(4)
            } catch (e: Exception) { banner = listOf(e.message ?: "Could not save") }
            saving = false
        }
    }

    @Composable fun f(key: String, label: String, kb: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, password: Boolean = false) =
        Input(label, field(key), { text[key] = it }, errors[key], singleLine = singleLine, keyboard = kb, password = password)

    val back = leaveGuard(initial != null && (text.toMap().toString() + typeId != initial || photo.picked != null || certification.picked != null), onBack)
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, if (editing) "Edit professional" else "New professional", back, compact = true)

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            loadError != null -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(loadError ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
            else -> {
                if (banner.isNotEmpty()) Column(
                    Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).padding(12.dp),
                ) { banner.forEach { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        FormCard("Profile") {
                            PickerField("Type *", types.firstOrNull { it.id == typeId }?.label, errors["professional_type_id"]) { picker = "type" }
                            f("name", "Name *")
                            f("company_name", "Company")
                            PickerField("Status", statusLabel(field("status").ifBlank { "active" }), errors["status"]) { picker = "status" }
                        }
                        FormCard("Contact") {
                            f("email", "Email", KeyboardType.Email)
                            f("phone", "Phone", KeyboardType.Phone)
                            f("address", "Address", singleLine = false)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) { f("city", "City") }
                                Box(Modifier.weight(1f)) { f("state", "State") }
                            }
                            f("pincode", "Pincode", KeyboardType.Number)
                        }
                        FormCard("Licence & business") {
                            f("license_number", "Licence number")
                            val expiry = proDate(field("license_expiry"))
                            PickerField("Licence expiry", expiry?.format(Shown), errors["license_expiry"]) {
                                val d = expiry ?: LocalDate.now()
                                DatePickerDialog(context, { _, y, m, day -> text["license_expiry"] = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                            }
                            f("registration_number", "Registration number")
                            f("gst_number", "GST number")
                            f("pan_number", "PAN number")
                            f("total_year_of_experience", "Years of experience", KeyboardType.Number)
                            f("expertise_area", "Expertise area", singleLine = false)
                        }
                        FormCard("Documents", "Photo up to 2 MB (JPG / PNG). Certification up to 4 MB (PDF / JPG / PNG).") {
                            FileField("Profile photo", photo, errors["profile_photo_url"], pickPhoto, view)
                            FileField("Certification", certification, errors["certification_url"], pickDoc, view)
                        }
                        FormCard("Login", if (editing) "Leave blank to keep the current password." else "They sign in to the professional portal with this.") {
                            f("password", if (editing) "New password" else "Password *", password = true)
                            f("password_confirmation", "Confirm password", password = true)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(b.base, b.deep))).clickable(enabled = !saving) { save() },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (saving) "Saving…" else if (editing) "Save changes" else "Create professional", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
        }
    }

    when (picker) {
        "type" -> ChoiceSheet("Type", types, setOf(typeId ?: -1), multi = false, onDismiss = { picker = null }) { typeId = it.firstOrNull() }
        "status" -> {
            val choices = statuses.mapIndexed { i, s -> Choice(i, statusLabel(s)) }
            ChoiceSheet("Status", choices, setOfNotNull(choices.firstOrNull { statuses[it.id] == field("status") }?.id), multi = false, onDismiss = { picker = null }) { ids ->
                ids.firstOrNull()?.let { text["status"] = statuses[it] }
            }
        }
    }
}
