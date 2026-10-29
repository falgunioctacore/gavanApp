package com.example.gavanhomes.ui.settings

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.common.loadBranding
import com.example.gavanhomes.ui.projects.form.FormCard
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickedFile
import com.example.gavanhomes.ui.projects.form.readPicked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MaxLogoBytes = 2 * 1024 * 1024     // the website's limit for the logo
private val Swatches = listOf("#3670F7", "#CFA251", "#0E8A7E", "#1BAF7A", "#8B5CF6", "#EC4899", "#EB6834", "#D03B3B", "#475569", "#0F172A")

private fun hexOrNull(s: String): Color? = runCatching { Color(android.graphics.Color.parseColor(if (s.startsWith("#")) s else "#$s")) }.getOrNull()
private fun validHex(s: String) = Regex("^#[0-9a-fA-F]{6}$").matches(s)

/**
 * App settings (super admins) — only what belongs to the app: its logo, name and theme colour. The website's own settings
 * (sidebar / navbar colours, favicon) stay on the website; the website needs them in every save, so the stored values are
 * read here and sent back unchanged. Saving goes through the website's own save (GET / POST /app-settings, multipart), then the
 * whole app reloads its branding so the new logo, name and colour show straight away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(api: ApiClient, b: Branding, onBack: () -> Unit, onSaved: (Branding) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = remember { mutableStateMapOf<String, String>() }       // app_name, theme_color — and the website-only colours, kept as they are
    var logo by remember { mutableStateOf<PickedFile?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        loading = true; loadError = null
        try {
            val d = withContext(Dispatchers.IO) { api.detail("/app-settings") }
            listOf("app_name", "theme_color", "sidebar_bg_color", "navbar_bg_color").forEach { text[it] = d.str(it).orEmpty() }
            initial = listOf(text["app_name"], text["theme_color"]).toString()
        } catch (e: Exception) { loadError = e.message ?: "Could not load the settings" }
        loading = false
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val p = readPicked(context, uri) ?: return@rememberLauncherForActivityResult
        if (p.bytes.size > MaxLogoBytes) { Toast.makeText(context, "The logo must be 2 MB or smaller", Toast.LENGTH_LONG).show(); return@rememberLauncherForActivityResult }
        logo = p
    }

    val back = leaveGuard(initial != null && (listOf(text["app_name"], text["theme_color"]).toString() != initial || logo != null), onBack)

    fun save() {
        val problems = mutableMapOf<String, String>()
        if (text["app_name"].isNullOrBlank()) problems["app_name"] = "Enter the app name"
        if (!validHex(text["theme_color"].orEmpty())) problems["theme_color"] = "Use a colour like #3670F7"
        errors = problems
        if (problems.isNotEmpty()) return
        saving = true
        scope.launch {
            try {
                val parts = mutableListOf(
                    FormPart("app_name", text["app_name"]!!.trim()), FormPart("theme_color", text["theme_color"]!!.uppercase()),
                    // not shown in the app, but the website's save needs them: sent back exactly as stored
                    FormPart("sidebar_bg_color", text["sidebar_bg_color"].orEmpty()), FormPart("navbar_bg_color", text["navbar_bg_color"].orEmpty()),
                )
                logo?.let { parts += FormPart("app_logo", fileName = it.name, mime = it.mime, bytes = it.bytes) }
                withContext(Dispatchers.IO) { api.sendForm("/app-settings", parts) }
                val fresh = loadBranding(api, context)           // the new logo, name and colour go straight into the whole app
                Toast.makeText(context, "App settings saved", Toast.LENGTH_SHORT).show()
                onSaved(fresh)
            } catch (e: ApiException) { errors = e.errors.ifEmpty { mapOf("app_name" to (e.message ?: "Could not save")) } }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, "App settings", back, compact = true)
        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            loadError != null -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(loadError ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        FormCard("App logo", "Shown on the sign-in screen and in the menus. PNG, JPG or WebP, up to 2 MB.") {
                            val picked = logo?.let { runCatching { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)?.asImageBitmap() }.getOrNull() }
                            Box(
                                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFF7F5F1)).border(1.dp, Color(0xFFE4E1DB), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val shown = picked ?: b.logo
                                if (shown != null) Image(shown, contentDescription = "Logo", modifier = Modifier.size(110.dp), contentScale = ContentScale.Fit)
                                else Text(logo?.let { "✓ ${it.name}" } ?: "No logo", color = KitMuted, fontSize = 14.sp)
                            }
                            logo?.let { Text("New logo: ${it.name} · ${it.bytes.size / 1024} KB — saved when you press Save", color = KitMuted, fontSize = 12.sp) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    if (logo == null) "Upload logo" else "Choose another", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F172A))
                                        .clickable { launcher.launch(arrayOf("image/png", "image/jpeg", "image/webp", "image/svg+xml")) }.padding(vertical = 14.dp),
                                )
                                if (logo != null) Text(
                                    "Remove", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).clickable { logo = null }.padding(horizontal = 20.dp, vertical = 14.dp),
                                )
                            }
                            errors["app_logo"]?.let { Text(it, color = KitDanger, fontSize = 12.sp) }
                        }

                        FormCard("App name") {
                            Input("Name *", text["app_name"].orEmpty(), { text["app_name"] = it }, errors["app_name"])
                        }

                        FormCard("Theme colour", "The colour of the headers, buttons and highlights across the app.") {
                            val v = text["theme_color"].orEmpty()
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(hexOrNull(v) ?: Color(0xFFEDEBE7)).border(1.dp, Color(0xFFD9D6D0), RoundedCornerShape(14.dp)))
                                Box(Modifier.weight(1f)) { Input("Colour", v, { text["theme_color"] = it.take(7) }, errors["theme_color"]) }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Swatches.forEach { hex ->
                                    val on = v.equals(hex, true)
                                    Box(
                                        Modifier.size(34.dp).clip(CircleShape).background(hexOrNull(hex)!!)
                                            .border(if (on) 3.dp else 1.dp, if (on) KitInk else Color(0xFFD9D6D0), CircleShape).clickable { text["theme_color"] = hex },
                                    )
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
                    ) { Text(if (saving) "Saving…" else "Save settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
        }
    }
}
