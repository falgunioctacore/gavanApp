package com.example.gavanhomes.ui.hr

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.professionals.ProCard
import com.example.gavanhomes.ui.professionals.ProDoc
import com.example.gavanhomes.ui.professionals.ProFact
import com.example.gavanhomes.ui.professionals.ProLoading
import com.example.gavanhomes.ui.professionals.ProTile
import com.example.gavanhomes.ui.professionals.prettyDate
import com.example.gavanhomes.ui.projects.form.Input
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One record of a standard HR module, all native — GET /hr/{module}/{id}. The details shown are the module's own form fields
 * (so a field added on the website appears here), with the module's workflow actions: approve / reject a request,
 * check a visitor in or out. Edit and delete (with confirmation) for those allowed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HrRecordScreen(api: ApiClient, b: Branding, slug: String, id: Int, onBack: () -> Unit, onEdit: (String, Int) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var reject by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/hr/$slug/$id") }
            r.optJSONObject("schema")?.let { HrSchemas.cache[slug] = parseSchema(it) }
            root = r
        } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    val view: (String) -> Unit = { path ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${ApiConfig.BASE_URL}/storage/$path"))) }
            .onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }
    fun act(path: String, body: JSONObject = JSONObject(), message: String) {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.send("POST", path, body) }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); attempt++; onChanged()
            } catch (e: Exception) { Toast.makeText(context, e.message ?: "Something went wrong", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    val schema = HrSchemas.cache[slug]
    val d = root?.optJSONObject("data")
    if (d == null || schema == null) { ProLoading(b, "Details", error, onBack) { attempt++ }; return }

    val can = root?.optJSONObject("can")
    val title = schema.titleOf(d)
    val badge = d.str(schema.badge)
    val badgeText = badge?.let { b2 -> schema.field(schema.badge)?.let { schema.display(it, b2) } ?: hrStatusLabel(b2) }
    val related = d.optJSONObject("related")

    Column(Modifier.fillMaxSize().background(KitPage)) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).fillMaxSize().verticalScroll(rememberScrollState())) {
            HeroBar(b, schema.singular, onBack, actions = { if (can?.optBoolean("edit") == true) HeroButton("✎") { onEdit(slug, id) } }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Initials(title, 60.dp)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text(schema.subOf(d).ifEmpty { d.str("reference").orEmpty() }, color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(badgeText, d.str("reference") ?: d.str("employee_code")).toTypedArray())
                }
            }

            if (related != null) Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProTile(related.optInt("payrolls").toString(), "Payrolls", Modifier.weight(1f))
                ProTile(related.optInt("reviews").toString(), "Reviews", Modifier.weight(1f))
                ProTile(related.optInt("documents").toString(), "Documents", Modifier.weight(1f))
                ProTile(related.optInt("exits").toString(), "Exits", Modifier.weight(1f))
            }

            // workflow actions
            if (can?.optBoolean("edit") == true) {
                if (slug == "requests" && badge == "pending") Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reject", color = KitDanger, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(KitDanger.copy(alpha = .1f)).clickable(enabled = !busy) { reject = true }.padding(vertical = 15.dp))
                    Text("Approve", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(KitGood).clickable(enabled = !busy) { act("/hr/requests/$id/approve", message = "Request approved") }.padding(vertical = 15.dp))
                }
                if (slug == "visitors" && badge == "expected") Text("Check in", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitGood).clickable(enabled = !busy) { act("/hr/visitors/$id/check-in", message = "Visitor checked in") }.padding(vertical = 15.dp))
                if (slug == "visitors" && badge == "checked_in") Text("Check out", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0F172A)).clickable(enabled = !busy) { act("/hr/visitors/$id/check-out", message = "Visitor checked out") }.padding(vertical = 15.dp))
            }

            ProCard("Details") {
                schema.fields.forEach { f ->
                    when (f.type) {
                        "file" -> ProDoc(f.label, d.str("file"), view)
                        "textarea" -> d.str(f.name)?.let { text -> Column { Text(f.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp); Text(text, color = KitInk, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 2.dp)) } }
                        else -> {
                            val raw = d.str(f.name)
                            // "employee_id" → show the person's name
                            val shown = if (f.name.endsWith("_id")) d.str(f.name.removeSuffix("_id").let { k -> if (k == "employee") "employee_name" else k + "_name" }) ?: schema.display(f, raw) else schema.display(f, raw)
                            ProFact(f.label, shown)
                        }
                    }
                }
                ProFact("Added", prettyDate(d.str("created_at")))
            }

            if (can?.optBoolean("delete") == true) Text(
                "Delete ${schema.singular.lowercase()}", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitDanger.copy(alpha = .1f)).clickable(enabled = !busy) { confirm = true }.padding(vertical = 16.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirm) SwalConfirm(
        title = "Delete this ${schema.singular.lowercase()}?", text = "$title can't be restored.", confirmText = "Yes, delete", onCancel = { confirm = false },
        onConfirm = {
            confirm = false; busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { api.delete("/hr/$slug/$id") }
                    Toast.makeText(context, "${schema.singular} deleted", Toast.LENGTH_SHORT).show(); onChanged(); onBack()
                } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show(); busy = false }
            }
        },
    )

    if (reject) {
        var remarks by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { reject = false }, containerColor = Color.White) {
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reject request", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Input("Remarks for the employee (optional)", remarks, { remarks = it }, singleLine = false)
                Text("Reject", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitDanger).clickable { reject = false; act("/hr/requests/$id/reject", JSONObject().put("remarks", remarks.trim()), "Request rejected") }.padding(vertical = 15.dp))
            }
        }
    }
}
