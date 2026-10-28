package com.example.gavanhomes.ui.projects.form

import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class Choice(val id: Int, val label: String)

private val Shown = DateTimeFormatter.ofPattern("d MMM yyyy")
private const val MaxFileBytes = 5 * 1024 * 1024        // the website's limit: 5 MB per document

@Composable
fun FormCard(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KitInk)
            if (hint != null) Text(hint, fontSize = 12.sp, color = KitMuted, modifier = Modifier.padding(top = 2.dp))
        }
        content()
    }
}

@Composable
fun SubHeading(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun Input(
    label: String, value: String, onChange: (String) -> Unit, error: String? = null,
    singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text, readOnly: Boolean = false, password: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine, minLines = if (singleLine) 1 else 3, readOnly = readOnly,
        isError = error != null, supportingText = error?.let { { Text(it) } }, shape = RoundedCornerShape(14.dp),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = KitInk, unfocusedTextColor = KitInk, cursorColor = KitInk, focusedLabelColor = KitMuted, unfocusedLabelColor = KitMuted),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** An input bound to one of the form's plain-value fields. */
@Composable
fun Field(form: ProjectForm, key: String, label: String, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) =
    Input(label, form.get(key), { form.set(key, it) }, form.errors[key], singleLine, keyboard)

@Composable
fun NumberField(form: ProjectForm, key: String, label: String) = Field(form, key, label, KeyboardType.Decimal)

/** Looks like a field, opens a picker / dialog when tapped. */
@Composable
fun PickerField(label: String, value: String?, error: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .border(1.dp, if (error != null) KitDanger else Color(0xFF79747E), RoundedCornerShape(14.dp)).clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(label, fontSize = 11.sp, color = KitMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value ?: "Select…", color = if (value == null) KitMuted else KitInk, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("▾", color = KitMuted, fontSize = 14.sp)
            }
        }
        if (error != null) Text(error, color = KitDanger, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 3.dp))
    }
}

/** A date stored as yyyy-MM-dd in the form; shown as "1 Nov 2026"; a tap opens the date dialog. */
@Composable
fun DateField(form: ProjectForm, key: String, label: String, context: Context) {
    val current = form.get(key).takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    PickerField(label, current?.format(Shown), form.errors[key]) {
        val d = current ?: LocalDate.now()
        DatePickerDialog(context, { _, y, m, day -> form.set(key, LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = KitInk)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = KitMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * A document field: shows what is stored / picked, and lets the user choose or replace the file.
 * [pick] asks the screen to open the document picker for this slot; [view] opens a stored file.
 */
@Composable
fun FileField(label: String, slot: FileSlot, error: String? = null, pick: (FileSlot) -> Unit, view: (String) -> Unit) {
    val picked = slot.picked
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .border(1.dp, if (error != null) KitDanger else Color(0xFFD9D6D0), RoundedCornerShape(14.dp)).padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = KitInk)
                Text(
                    when {
                        picked != null -> "${picked.name} · ${picked.bytes.size / 1024} KB"
                        slot.existing != null -> "Uploaded · ${slot.existing!!.substringAfterLast('/')}"
                        else -> "No file"
                    },
                    fontSize = 12.sp, color = if (picked != null) KitInk else KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (picked == null && slot.existing != null) Chip("View") { view(slot.existing!!) }
            if (picked != null) { Chip("Remove") { slot.picked = null }; Spacer(Modifier.size(6.dp)) }
            Spacer(Modifier.size(6.dp))
            Chip(if (slot.hasFile) "Replace" else "Choose", filled = true) { pick(slot) }
        }
        if (error != null) Text(error, color = KitDanger, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 3.dp))
    }
}

@Composable
private fun Chip(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.White else KitInk, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (filled) Color(0xFF0F172A) else Color(0xFFF1EFEB)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Reads a document the user picked; null (with a toast) when it is too big or can't be read. */
fun readPicked(context: Context, uri: Uri): PickedFile? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    var name = "document"
    var size = -1L
    resolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); val s = c.getColumnIndex(OpenableColumns.SIZE)
            if (n >= 0) name = c.getString(n) ?: name
            if (s >= 0) size = c.getLong(s)
        }
    }
    if (size > MaxFileBytes) { Toast.makeText(context, "That file is over 5 MB", Toast.LENGTH_LONG).show(); return null }
    val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    if (bytes == null) { Toast.makeText(context, "Could not read that file", Toast.LENGTH_LONG).show(); return null }
    if (bytes.size > MaxFileBytes) { Toast.makeText(context, "That file is over 5 MB", Toast.LENGTH_LONG).show(); return null }
    val ext = when { mime.contains("pdf") -> "pdf"; mime.contains("png") -> "png"; mime.contains("jp") -> "jpg"; else -> null }
    if (ext != null && !name.lowercase().endsWith(".$ext") && !(ext == "jpg" && name.lowercase().endsWith(".jpeg"))) name = "$name.$ext"
    return PickedFile(name, mime, bytes)
}

/** Searchable single / multi select in a bottom sheet. [onDone] gets the chosen ids when the sheet closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSheet(
    title: String, options: List<Choice>, selected: Set<Int>, multi: Boolean, onDismiss: () -> Unit,
    allowNone: Boolean = false, onDone: (Set<Int>) -> Unit,
) {
    var picked by remember { mutableStateOf(selected.filter { it != -1 }.toSet()) }
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { onDone(picked); onDismiss() }, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                if (multi) Text("Done", color = KitInk, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onDone(picked); onDismiss() }.padding(8.dp))
            }
            if (options.size > 8) OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search…") }, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = KitInk, unfocusedTextColor = KitInk),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (allowNone && query.isBlank()) item { ChoiceRow("None", picked.isEmpty()) { picked = emptySet(); onDone(picked); onDismiss() } }
                items(options.filter { query.isBlank() || it.label.contains(query, true) }, key = { it.id }) { o ->
                    ChoiceRow(o.label, o.id in picked) {
                        if (multi) picked = if (o.id in picked) picked - o.id else picked + o.id
                        else { picked = setOf(o.id); onDone(picked); onDismiss() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (on) Color(0xFF0F172A) else Color.Transparent).border(1.5.dp, if (on) Color(0xFF0F172A) else Color(0xFFB9BCC4), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (on) Text("✓", color = Color.White, fontSize = 13.sp) }
        Text(label, fontSize = 15.sp, color = KitInk, modifier = Modifier.padding(start = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
