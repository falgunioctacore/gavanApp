package com.example.gavanhomes.ui.professionals

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiConfig
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.common.SwalConfirm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A professional, all native — GET /professionals/{uuid}: contact, business & licence, expertise, documents, tasks. */
@Composable
fun ProfessionalDetailScreen(api: ApiClient, b: Branding, uuid: String, onBack: () -> Unit, onEdit: (String) -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        error = null
        try { root = withContext(Dispatchers.IO) { api.get("/professionals/$uuid") } }
        catch (e: Exception) { error = e.message ?: "Could not load the professional" }
    }
    val view: (String) -> Unit = { path ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${ApiConfig.BASE_URL}/storage/$path"))) }
            .onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }
    fun launch(i: Intent) { runCatching { context.startActivity(i) } }

    val d = root?.optJSONObject("data")
    if (d == null) { ProLoading(b, "Professional", error, onBack) { attempt++ }; return }

    val name = d.str("name") ?: "Professional"
    val counts = d.optJSONObject("task_counts")
    fun n(k: String) = counts?.optInt(k) ?: 0
    val total = counts?.keys()?.asSequence()?.sumOf { counts.optInt(it) } ?: 0
    val licence = licenceTone(d.str("license_expiry"))
    val place = listOfNotNull(d.str("address"), d.str("city"), d.str("state"), d.str("pincode")).joinToString(", ")

    Column(Modifier.fillMaxSize().background(KitPage)) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).fillMaxSize().verticalScroll(rememberScrollState())) {
            HeroBar(b, "Professional", onBack, actions = { HeroButton("✎") { onEdit(uuid) } }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Initials(name, 64.dp)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text(listOfNotNull(d.str("code"), d.str("company")).joinToString(" · "), color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(statusLabel(d.str("status")), d.str("type"), d.str("experience")?.let { "$it yrs experience" }).toTypedArray())
                }
            }

            Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProTile(total.toString(), "Tasks", Modifier.weight(1f))
                ProTile((n("assigned") + n("in_progress") + n("rework")).toString(), "Open", Modifier.weight(1f), ProBlue)
                ProTile(n("submitted").toString(), "Submitted", Modifier.weight(1f), ProAmber)
                ProTile(n("approved").toString(), "Approved", Modifier.weight(1f), KitGood)
            }

            ProCard("Contact") {
                ProFact("Phone", d.str("phone")) { launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${d.str("phone")}"))) }
                ProFact("Email", d.str("email")) { launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${d.str("email")}"))) }
                ProFact("Address", place.ifEmpty { null }) { launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}"))) }
                if (d.str("phone") == null && d.str("email") == null && place.isEmpty()) Text("No contact details.", color = KitMuted, fontSize = 14.sp)
            }

            ProCard("Business & licence") {
                ProFact("Company", d.str("company_name"))
                ProFact("Licence number", d.str("license_number"))
                ProFact("Licence expiry", prettyDate(d.str("license_expiry")))
                licence?.let { (t, c) -> Row { Pill(t, c.copy(alpha = .14f), c) } }
                ProFact("Registration number", d.str("registration_number"))
                ProFact("GST", d.str("gst_number"))
                ProFact("PAN", d.str("pan_number"))
            }

            if (d.str("expertise_area") != null || d.str("experience") != null) ProCard("Expertise") {
                ProFact("Experience", d.str("experience")?.let { "$it years" })
                d.str("expertise_area")?.let { Text(it, color = KitInk, fontSize = 14.sp, lineHeight = 21.sp) }
            }

            if (d.str("photo") != null || d.str("certification") != null) ProCard("Documents") {
                ProDoc("Profile photo", d.str("photo"), view)
                ProDoc("Certification", d.str("certification"), view)
            }

            val tasks = d.objects("tasks")
            ProCard("Tasks", if (tasks.isEmpty()) null else "Latest ${tasks.size}") {
                if (tasks.isEmpty()) Text("No tasks assigned yet.", color = KitMuted, fontSize = 14.sp)
                tasks.forEach { t ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.str("name") ?: "Task", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(t.str("project"), prettyDate(t.str("due_date"))?.let { "due $it" }).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                        }
                        Pill(statusLabel(t.str("status")), statusColor(t.str("status")).copy(alpha = .14f), statusColor(t.str("status")))
                    }
                }
            }

            Text(
                if (deleting) "Deleting…" else "Delete professional", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(KitDanger.copy(alpha = .1f)).clickable(enabled = !deleting) { confirm = true }.padding(vertical = 16.dp),
            )
            Spacer(Modifier.height(20.dp))
        }

        if (confirm) SwalConfirm(
            title = "Delete $name?", text = "They will lose access and the record can't be restored.", confirmText = "Yes, delete",
            onCancel = { confirm = false },
            onConfirm = {
                confirm = false; deleting = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.delete("/professionals/$uuid") }
                        Toast.makeText(context, "Professional deleted", Toast.LENGTH_SHORT).show(); onDeleted()
                    } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show(); deleting = false }
                }
            },
        )
    }
}
