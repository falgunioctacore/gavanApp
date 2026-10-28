package com.example.gavanhomes.ui.projects.form

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.common.SearchBox
import com.example.gavanhomes.ui.common.SwalConfirm

private val Amber = Color(0xFFC47A1A)
private val Blue = Color(0xFF2F6FE4)

private fun statusColor(s: String?): Color = when (s) { "available" -> KitGood; "booked" -> Amber; "sold" -> KitDanger; "reserved" -> Blue; else -> KitMuted }
private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

/** Step 2 of the website's form: the unit inventory, one list per selected property type. */
@Composable
fun InventoryStep(
    form: ProjectForm, columns: Map<String, List<InvColumn>>, b: Branding,
    pick: (FileSlot) -> Unit, view: (String) -> Unit,
) {
    val types = form.types.sorted()
    if (types.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No property type chosen", fontWeight = FontWeight.Bold, color = KitInk)
            Text("Pick the project types in Basic information, then add their units here.", color = KitMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        return
    }

    var current by remember { mutableStateOf(types.first()) }
    if (current !in types) current = types.first()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<UnitState?>(null) }

    val cols = columns[current].orEmpty()
    val idKey = cols.firstOrNull()?.key ?: "unit_no"
    val units = form.unitsOf(current)
    val shown = units.withIndex().filter { (_, u) ->
        val q = query.trim().lowercase()
        q.isEmpty() || u.values.values.any { it.lowercase().contains(q) }
    }

    // ── CSV import (same flow as the website: sample file, choose CSV, review, import) ──
    val context = LocalContext.current
    var importing by remember { mutableStateOf(false) }
    var csv by remember { mutableStateOf<CsvResult?>(null) }
    val chooseCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri)
            if (!name.lowercase().endsWith(".csv")) Toast.makeText(context, "Please select a .csv file.", Toast.LENGTH_LONG).show()
            else {
                val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                if (text == null) Toast.makeText(context, "Could not read that file", Toast.LENGTH_LONG).show() else csv = parseInventoryCsv(text, cols, name)
            }
        }
    }
    val saveSample = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(sampleCsv(cols).toByteArray()) } }.isSuccess
            Toast.makeText(context, if (ok) "Sample CSV saved" else "Could not save the file", Toast.LENGTH_SHORT).show()
        }
    }
    val sampleName = "sample_${slugOf(current)}_inventory.csv"

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
        // type chips
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { t ->
                    val on = t == current
                    Text(
                        "$t · ${form.unitsOf(t).size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1,
                        modifier = Modifier.shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF0F172A) else Color.White)
                            .clickable { current = t; query = "" }.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        if (units.size > 6) { item { SearchBox(query, { query = it }, "Search units…") }; item { Spacer(Modifier.height(8.dp)) } }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${units.size} unit${if (units.size == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    "+ Add unit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F172A))
                        .clickable { editing = UnitState().also { it.values["status"] = "available"; units.add(it) } }.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "↓ Sample CSV", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KitInk, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { saveSample.launch(sampleName) }.padding(vertical = 12.dp),
                )
                Text(
                    "↑ Import CSV", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KitInk, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { csv = null; importing = true }.padding(vertical = 12.dp),
                )
            }
        }

        if (units.isEmpty()) item {
            Text("No units yet. Tap “Add unit”.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
        }
        itemsIndexed(shown, key = { _, v -> v.value.id }) { _, (index, u) ->
            UnitCard(u, index, idKey, form.errors.keys.any { it.startsWith("inventory.${slugOf(current)}.$index.") }) { editing = u }
        }
    }

    if (importing) ImportSheet(
        type = current, cols = cols, result = csv,
        onChoose = { chooseCsv.launch(arrayOf("*/*")) },
        onSample = { saveSample.launch(sampleName) },
        onImport = {
            val rows = csv?.valid.orEmpty()
            rows.forEach { r -> units.add(UnitState().also { u -> r.forEach { (k, v) -> if (v.isNotBlank()) u.values[k] = v } }) }
            Toast.makeText(context, "Imported ${rows.size} unit${if (rows.size == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
            importing = false; csv = null
        },
        onDismiss = { importing = false; csv = null },
    )

    editing?.let { u ->
        UnitEditor(
            u, cols, form, current, units.indexOf(u), pick, view,
            onDismiss = { editing = null },
            onDuplicate = { units.add(units.indexOf(u) + 1, u.duplicate()); editing = null },
            onDelete = { units.remove(u); editing = null },
        )
    }
}

@Composable
private fun UnitCard(u: UnitState, index: Int, idKey: String, hasError: Boolean, onClick: () -> Unit) {
    val title = u.values[idKey]?.takeIf { it.isNotBlank() } ?: "Unit ${index + 1}"
    val status = u.values["status"]
    val facts = listOfNotNull(
        u.values["wing"]?.takeIf { it.isNotBlank() }?.let { "Wing $it" },
        u.values["floor"]?.takeIf { it.isNotBlank() }?.let { "Floor $it" },
        u.values["bhk_type"]?.takeIf { it.isNotBlank() },
        u.values["bedrooms"]?.takeIf { it.isNotBlank() }?.let { "$it bed" },
        u.values["price"]?.toDoubleOrNull()?.let { "₹" + String.format(java.util.Locale.US, "%,.0f", it) },
    )
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(if (hasError) KitDanger.copy(alpha = .08f) else Color.White).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontSize = 12.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (status != null) Pill(cap(status), statusColor(status).copy(alpha = .14f), statusColor(status))
        Text("›", fontSize = 22.sp, color = KitMuted, modifier = Modifier.padding(start = 10.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UnitEditor(
    u: UnitState, cols: List<InvColumn>, form: ProjectForm, type: String, index: Int,
    pick: (FileSlot) -> Unit, view: (String) -> Unit,
    onDismiss: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val prefix = "inventory.${slugOf(type)}.$index."

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Unit ${index + 1}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                    Text(type, fontSize = 12.sp, color = KitMuted)
                }
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0F172A)).clickable { onDismiss() }.padding(horizontal = 18.dp, vertical = 10.dp))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                cols.forEach { c ->
                    val error = form.errors[prefix + c.key]
                    when {
                        c.isFile -> FileField(c.label, u.files.getValue(c.key), error, pick, view)
                        c.type == "select" -> Column {
                            Text(c.label + if (c.required) " *" else "", fontSize = 12.sp, color = KitMuted, modifier = Modifier.padding(bottom = 6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!c.required) OptionChip("None", u.values[c.key].isNullOrBlank()) { u.values.remove(c.key) }
                                c.options.forEach { o -> OptionChip(cap(o), u.values[c.key] == o) { u.values[c.key] = o } }
                            }
                            if (error != null) Text(error, color = KitDanger, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        else -> Input(
                            c.label + if (c.required) " *" else "", u.values[c.key].orEmpty(),
                            { v -> if (v.isBlank()) u.values.remove(c.key) else u.values[c.key] = v }, error,
                            keyboard = if (c.type == "number") KeyboardType.Decimal else KeyboardType.Text,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Duplicate", fontWeight = FontWeight.Bold, color = KitInk, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1EFEB)).clickable { onDuplicate() }.padding(vertical = 14.dp),
                    )
                    Text(
                        "Delete unit", fontWeight = FontWeight.Bold, color = KitDanger, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).clickable { confirmDelete = true }.padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }

    if (confirmDelete) SwalConfirm(
        title = "Delete this unit?", text = "It is removed when you save the project.", confirmText = "Yes, delete",
        onCancel = { confirmDelete = false }, onConfirm = { confirmDelete = false; onDelete() },
    )
}

@Composable
private fun OptionChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF0F172A) else Color(0xFFF1EFEB)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
