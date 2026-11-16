package com.example.gavanhomes.ui.leads

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitSoft
import com.example.gavanhomes.ui.projects.form.displayName
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One lead read from a file or the phone's contacts, before it is sent. */
private class ImportRow(val fields: Map<String, String>) {
    val name get() = fields["client_name"].orEmpty()
    val phone get() = fields["phone_number"].orEmpty()
}

private val ImportKeys = listOf("client_name", "phone_number", "alternate_phone_number", "email", "company_name", "lead_source", "interested_in", "notes")

/** Header spellings people use for each field (matched after lower-casing and turning anything but letters / digits into "_"). */
private val Aliases = mapOf(
    "client_name" to listOf("client_name", "name", "full_name", "client", "customer", "customer_name"),
    "phone_number" to listOf("phone_number", "mobile", "mobile_number", "mobile_no", "phone", "contact", "contact_no", "whatsapp"),
    "alternate_phone_number" to listOf("alternate_phone_number", "alternate_no", "alternate_number", "alternate", "alt_phone", "alt_mobile", "phone_2"),
    "email" to listOf("email", "email_id", "e_mail"),
    "company_name" to listOf("company_name", "company", "organisation", "organization"),
    "lead_source" to listOf("lead_source", "source"),
    "interested_in" to listOf("interested_in", "project", "plot", "interest", "interested"),
    "notes" to listOf("notes", "note", "remarks", "remark", "comment"),
)

/** The sample file: the header row and two example leads. */
private fun sampleLeadsCsv() = "client_name,phone_number,alternate_phone_number,email,company_name,lead_source,interested_in,notes\r\n" +
    "Ramesh Patel,9876543210,9876500000,ramesh@example.com,Patel Traders,Instagram,Plot 12,Wants a corner plot\r\n" +
    "Sunita Shah,9123456780,,,,Walk-in,Plot 5,Call after 6 pm\r\n"

private fun splitLine(line: String): List<String> {
    val out = mutableListOf<String>(); val cur = StringBuilder(); var q = false; var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' -> if (q && i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ } else q = !q
            ch == ',' && !q -> { out.add(cur.toString()); cur.clear() }
            else -> cur.append(ch)
        }
        i++
    }
    out.add(cur.toString())
    return out.map { it.trim() }
}

private fun norm(h: String) = h.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

/** Reads a leads CSV: rows with their fields, or a message when the header is not usable. */
private fun parseLeadsCsv(text: String): Pair<List<ImportRow>, String?> {
    val lines = text.removePrefix("﻿").replace("\r\n", "\n").replace("\r", "\n").split("\n").filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList<ImportRow>() to "The file is empty or has no lead rows."
    val headers = splitLine(lines[0]).map(::norm)
    val index = ImportKeys.associateWith { key -> Aliases.getValue(key).firstNotNullOfOrNull { a -> headers.indexOf(a).takeIf { it >= 0 } } }
    if (index["client_name"] == null || index["phone_number"] == null) return emptyList<ImportRow>() to "The first row must name the columns — at least client_name (or name) and phone_number (or mobile)."
    val rows = lines.drop(1).map { l ->
        val v = splitLine(l)
        ImportRow(ImportKeys.associateWith { k -> index[k]?.let { v.getOrNull(it) }.orEmpty() })
    }
    return rows to null
}

/** Every contact with a phone number, once per number (last 10 digits). */
private fun readContacts(context: android.content.Context): List<ImportRow> {
    val out = linkedMapOf<String, ImportRow>()
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, "display_name COLLATE NOCASE ASC",
    )?.use { c ->
        while (c.moveToNext()) {
            val digits = c.getString(1).orEmpty().filter(Char::isDigit).takeLast(10)
            if (digits.length == 10 && digits !in out) out[digits] = ImportRow(mapOf("client_name" to c.getString(0).orEmpty().trim().ifEmpty { digits }, "phone_number" to digits))
        }
    }
    return out.values.toList()
}

/**
 * Import leads in bulk — from a CSV file or the phone's contacts. Pick the list and the source, review, import; a mobile number that is
 * already a lead (or repeats) is never added twice, and wrong rows are reported with their row number and the problem.
 * Data: POST /projects/{uuid}/lead-center/import.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LeadImportSheet(api: ApiClient, b: Branding, uuid: String, options: JSONObject?, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val lists = options?.objs("lists").orEmpty()
    val sources = options?.optJSONArray("sources")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().filter { it != "Import" }
    var listId by remember { mutableStateOf(lists.singleOrNull()?.optInt("id") ?: 0) }
    var source by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ImportRow>>(emptyList()) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }            // the contact chooser is open
    val contacts = remember { mutableStateListOf<ImportRow>() }
    val chosen = remember { mutableStateListOf<String>() }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<JSONObject?>(null) }

    val chooseCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri)
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            if (!name.lowercase().endsWith(".csv")) problem = "Please choose a .csv file."
            else if (text == null) problem = "Could not read that file."
            else { val (r, err) = parseLeadsCsv(text); rows = r; problem = err; fileName = name; result = null }
        }
    }
    val saveSample = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(sampleLeadsCsv().toByteArray()) } }.isSuccess
            AppToast.show(if (ok) "Sample CSV saved" else "Could not save the file")
        }
    }
    fun openContacts() {
        contacts.clear(); contacts.addAll(readContacts(context)); chosen.clear(); query = ""; picking = true
        if (contacts.isEmpty()) AppToast.show("No contacts with a phone number found")
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openContacts() else problem = "Allow access to contacts to import from them."
    }

    fun send(list: List<ImportRow>) {
        if (listId <= 0) { problem = "Choose the lead list these leads go into."; return }
        busy = true; problem = null
        scope.launch {
            try {
                val body = JSONObject().put("list_id", listId).put("lead_source", source.ifBlank { "Import" })
                    .put("rows", JSONArray(list.map { r -> JSONObject().also { o -> r.fields.forEach { (k, v) -> o.put(k, v) } } }))
                val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/lead-center/import", body) }
                result = r; AppToast.show(r.optString("message")); if (r.optJSONObject("data")?.optInt("added", 0) ?: 0 > 0) onDone()
            } catch (e: com.example.gavanhomes.data.ApiException) {
                problem = e.message
            } catch (e: Exception) { problem = e.message ?: "Could not import" }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = KitCard) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            if (picking) {
                // ── choose contacts ──
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Phone contacts", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                    Text("Back", color = KitMuted, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = false }.padding(8.dp))
                }
                val shown = contacts.filter { query.isBlank() || it.name.contains(query.trim(), true) || it.phone.contains(query.trim()) }
                com.example.gavanhomes.ui.projects.form.Input("Search contacts", query, { query = it })
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${chosen.size} of ${contacts.size} selected", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("Select all shown", color = b.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { shown.forEach { if (it.phone !in chosen) chosen.add(it.phone) } }.padding(8.dp))
                    Text("Clear", color = KitMuted, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { chosen.clear() }.padding(8.dp))
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.phone }) { c ->
                        val on = c.phone in chosen
                        Row(Modifier.fillMaxWidth().clickable { if (on) chosen.remove(c.phone) else chosen.add(c.phone) }.padding(vertical = 9.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(if (on) "☑" else "☐", fontSize = 20.sp, color = if (on) KitGood else KitMuted, modifier = Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) { Text(c.name, color = KitInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(c.phone, color = KitMuted, fontSize = 12.sp) }
                        }
                    }
                }
                Text(
                    if (chosen.isEmpty()) "Choose contacts" else "Use ${chosen.size} contact${if (chosen.size == 1) "" else "s"}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (chosen.isEmpty()) Color(0xFFC9C7C2) else Color(0xFF0F172A))
                        .clickable(enabled = chosen.isNotEmpty()) { rows = contacts.filter { it.phone in chosen }; fileName = "Phone contacts"; result = null; picking = false }.padding(vertical = 16.dp),
                )
            } else {
                Text("Import leads", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text("From a CSV file or your phone's contacts. A mobile number that is already a lead is never added twice.", fontSize = 12.sp, color = KitMuted)

                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("LEAD LIST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                    if (lists.isEmpty()) Text("This project has no lead list yet — create one under Lists first.", color = KitDanger, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lists.forEach { l -> Chip(l.optString("name"), listId == l.optInt("id")) { listId = l.optInt("id") } }
                    }
                    Text("LEAD SOURCE (for rows that have none)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sources.forEach { s -> Chip(s, source == s) { source = if (source == s) "" else s } }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action("↓ Sample CSV", Modifier.weight(1f), KitSoft, KitInk) { saveSample.launch("sample_leads.csv") }
                        Action("↑ Choose CSV", Modifier.weight(1f), Color(0xFF0F172A), Color.White) { chooseCsv.launch(arrayOf("*/*")) }
                    }
                    Action("☎  From phone contacts", Modifier.fillMaxWidth(), KitSoft, KitInk) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) openContacts() else askContacts.launch(Manifest.permission.READ_CONTACTS)
                    }
                    Text("CSV columns: client_name, phone_number, alternate_phone_number, email, company_name, lead_source, interested_in, notes — the first row is the heading; only name and mobile are required.", fontSize = 12.sp, color = KitMuted)

                    problem?.let { Text("⚠ $it", color = KitDanger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }

                    if (fileName != null && problem == null && result == null) {
                        Text("📄 $fileName · ${rows.size} lead${if (rows.size == 1) "" else "s"}", fontSize = 13.sp, color = KitInk, fontWeight = FontWeight.SemiBold)
                        rows.take(5).forEach { r ->
                            Text("${r.name} · ${r.phone}", fontSize = 12.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(KitSoft).padding(horizontal = 12.dp, vertical = 9.dp))
                        }
                        if (rows.size > 5) Text("…and ${rows.size - 5} more", fontSize = 12.sp, color = KitMuted)
                    }

                    result?.optJSONObject("data")?.let { d ->
                        val dups = d.optJSONArray("duplicates") ?: JSONArray(); val errs = d.optJSONArray("errors") ?: JSONArray()
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KitGood.copy(alpha = .12f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${d.optInt("added")} imported", color = KitGood, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            if (dups.length() > 0) Text("${dups.length()} skipped — the mobile number is already a lead", color = KitMuted, fontSize = 12.sp)
                            for (i in 0 until minOf(dups.length(), 6)) dups.getJSONObject(i).let { Text("Row ${it.optInt("row")}: ${it.optString("name")} · ${it.optString("phone")}", fontSize = 12.sp, color = KitMuted) }
                        }
                        if (errs.length() > 0) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KitDanger.copy(alpha = .08f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${errs.length()} row${if (errs.length() == 1) "" else "s"} left out — fix them in the file and import again:", color = KitDanger, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            for (i in 0 until minOf(errs.length(), 12)) errs.getJSONObject(i).let { Text("Row ${it.optInt("row")}: ${it.optString("message")}", fontSize = 12.sp, color = KitDanger) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                val n = rows.size
                val done = result != null
                Text(
                    if (busy) "Importing…" else if (done) "Done" else if (n > 0) "Import $n lead${if (n == 1) "" else "s"}" else "Import leads",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (busy || (n == 0 && !done)) Color(0xFFC9C7C2) else Color(0xFF0F172A))
                        .clickable(enabled = !busy && (n > 0 || done)) { if (done) onDismiss() else send(rows) }.padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF0F172A) else KitSoft).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp))
}

@Composable
private fun Action(text: String, modifier: Modifier, bg: Color, fg: Color, onClick: () -> Unit) {
    Text(text, fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center, modifier = modifier.clip(RoundedCornerShape(14.dp)).background(bg).clickable { onClick() }.padding(vertical = 14.dp))
}
