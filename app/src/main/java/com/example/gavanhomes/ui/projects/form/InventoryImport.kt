package com.example.gavanhomes.ui.projects.form

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted

/** The result of reading an inventory CSV — same rules as the website's "Import CSV" dialog. */
class CsvResult(
    val valid: List<Map<String, String>>,
    val skipped: List<Pair<Int, List<String>>>,   // line number → what is wrong (row and column)
    val headerError: String?,
    val fileName: String,
    val merged: Int = 0,                          // rows that repeated a number already in the file (only one unit is kept)
)

private const val Q = "\""

private fun cell(v: String) = if (v.contains(',') || v.contains(Q)) Q + v.replace(Q, Q + Q) + Q else v

/**
 * The sample file: one header row (the plot template's own names for PLOT: plot_no, plot_area_sqft …) and example rows
 * that show the format. Documents are uploaded in the app, not by CSV.
 */
fun sampleCsv(type: String, cols: List<InvColumn>): String {
    val use = cols.filterNot { it.isFile }
    val examples: List<Map<String, String>> = if (type.contains("plot", ignoreCase = true)) listOf(
        mapOf("unit_no" to "1", "block" to "A", "east" to "12 m road", "west" to "Plot 2", "north" to "Plot 10", "south" to "Open land", "facing" to "east", "plot_area" to "5000", "carpet_area" to "4500", "rate_per_sqft" to "1200", "price" to "6000000", "extra_cost" to "0", "status" to "available"),
        mapOf("unit_no" to "2", "block" to "A", "east" to "Plot 1", "west" to "Plot 3", "north" to "Plot 11", "south" to "Open land", "facing" to "south", "plot_area" to "5000", "carpet_area" to "4500", "rate_per_sqft" to "1200", "price" to "6000000", "extra_cost" to "0", "status" to "booked"),
        mapOf("unit_no" to "3", "block" to "A", "east" to "Plot 2", "west" to "18 m road", "north" to "Plot 12", "south" to "Open land", "facing" to "west", "plot_area" to "6500", "carpet_area" to "5800", "rate_per_sqft" to "1250", "price" to "", "extra_cost" to "150000", "status" to "reserved"),
    ) else listOf(use.associate { c ->
        c.key to when { c.type == "select" -> c.options.firstOrNull().orEmpty(); c.type == "number" -> "0"; c.key.endsWith("_no") -> "101"; else -> "" }
    })
    val header = use.joinToString(",") { it.csvKey }
    return (listOf(header) + examples.map { ex -> use.joinToString(",") { c -> cell(ex[c.key].orEmpty()) } }).joinToString("\r\n") + "\r\n"
}

/** What a column accepts, for the import screen: fixed lists and required fields. */
fun columnRules(cols: List<InvColumn>): List<String> = cols.filterNot { it.isFile }.mapNotNull { c ->
    when {
        c.type == "select" -> "${c.csvKey}: ${c.options.joinToString(" / ")}" + (if (c.required) " (required)" else "") + " — any capital letters"
        c.type == "number" -> "${c.csvKey}: a number" + (if (c.required) " (required)" else "")
        c.required -> "${c.csvKey}: required"
        else -> null
    }
}

private fun splitCsvLine(line: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var inQuote = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' -> if (inQuote && i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ } else inQuote = !inQuote
            ch == ',' && !inQuote -> { out.add(cur.toString()); cur.clear() }
            else -> cur.append(ch)
        }
        i++
    }
    out.add(cur.toString())
    return out.map { it.trim() }
}

/** Header cells are matched to column keys case-insensitively, with anything other than letters / digits / _ turned into _. */
private fun normalizeHeader(h: String) = h.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_").replace(Regex("__+"), "_").trim('_')

/** "North East", "north_east", "NORTH-EAST" → north-east — and the same for any other list value. */
private fun normalizeChoice(raw: String, options: List<String>): String? {
    val squash = { s: String -> s.lowercase().replace(Regex("[\\s_-]+"), "") }
    return options.firstOrNull { squash(it) == squash(raw) }
}

/** True when this line is the header row again (a file whose heading was pasted twice) — never a unit. */
private fun looksLikeHeader(values: List<String>, headers: List<String>): Boolean =
    values.count { normalizeHeader(it) in headers } >= maxOf(2, values.size / 2)

fun parseInventoryCsv(text: String, cols: List<InvColumn>, fileName: String): CsvResult {
    val lines = text.removePrefix("﻿").replace("\r\n", "\n").replace("\r", "\n").split("\n").withIndex().filter { it.value.isNotBlank() }
    if (lines.size < 2) return CsvResult(emptyList(), emptyList(), "File is empty or has no data rows.", fileName)

    val headers = splitCsvLine(lines[0].value).map(::normalizeHeader)
    // a column is found by the CSV name (plot_no) or by its own key (unit_no)
    fun indexOf(c: InvColumn) = headers.indexOf(c.csvKey).takeIf { it >= 0 } ?: headers.indexOf(c.key)
    val missing = cols.filter { it.required && !it.isFile && indexOf(it) < 0 }
    if (missing.isNotEmpty()) return CsvResult(emptyList(), emptyList(), "Missing required column${if (missing.size == 1) "" else "s"}: ${missing.joinToString(", ") { it.csvKey }}", fileName)

    val identity = listOf("unit_no", "flat_no", "shop_no", "house_no")
    val byNumber = linkedMapOf<String, Map<String, String>>()
    val skipped = mutableListOf<Pair<Int, List<String>>>()
    var repeats = 0
    lines.drop(1).forEach { (lineIdx, line) ->
        val values = splitCsvLine(line)
        if (looksLikeHeader(values, headers)) return@forEach                       // heading repeated inside the file
        val row = linkedMapOf<String, String>()
        val errors = mutableListOf<String>()
        cols.filterNot { it.isFile }.forEach { col ->
            val idx = indexOf(col)
            val raw = if (idx >= 0) values.getOrNull(idx).orEmpty().trim() else ""
            row[col.key] = raw
            if (col.required && raw.isEmpty()) errors += "${col.csvKey} is empty"
            if (col.type == "select" && raw.isNotEmpty()) {
                val ok = normalizeChoice(raw, col.options)
                if (ok == null) { errors += "${col.csvKey} “$raw” is not allowed (use ${col.options.joinToString(", ")})"; row[col.key] = "" } else row[col.key] = ok
            }
            if (col.type == "number" && raw.isNotEmpty() && raw.replace(",", "").toDoubleOrNull() == null) { errors += "${col.csvKey} “$raw” is not a number"; row[col.key] = "" }
            else if (col.type == "number" && raw.isNotEmpty()) row[col.key] = raw.replace(",", "")
        }
        val key = unitKeyOf(row.filterKeys { it in identity })
        if (key == null && errors.isEmpty()) errors += "no ${cols.firstOrNull { it.key in identity }?.csvKey ?: "unit number"}"
        if (errors.isNotEmpty() || key == null) { skipped += (lineIdx + 1) to errors; return@forEach }
        if (byNumber.containsKey(key)) repeats++
        byNumber[key] = row                                                        // one unit per number: the later row wins
    }
    return CsvResult(byNumber.values.toList(), skipped, null, fileName, repeats)
}

/** How an import treats the units already in the project. */
enum class ImportMode(val label: String, val help: String) {
    Merge("Update existing + add new", "A plot number that is already there is updated; new numbers are added. Nothing is duplicated."),
    AddNew("Add new only", "Only plot numbers that are not in the project yet are added; the ones already there are left exactly as they are."),
    Replace("Replace all", "Existing units are removed first (booked and sold ones are always kept), then the file is added."),
}

/** Shows what a chosen CSV contains and imports its valid rows (existing units are updated by number, never duplicated). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportSheet(
    type: String, cols: List<InvColumn>, existing: Int, result: CsvResult?, mode: ImportMode, onMode: (ImportMode) -> Unit,
    onChoose: () -> Unit, onSample: () -> Unit, onImport: () -> Unit, onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = com.example.gavanhomes.ui.common.KitCard) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Text("Import CSV", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Text(type + if (existing > 0) " · $existing unit${if (existing == 1) "" else "s"} now" else "", fontSize = 12.sp, color = KitMuted)

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("EXPECTED COLUMNS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cols.filterNot { it.isFile }.forEach { c ->
                        Text(
                            c.csvKey + if (c.required) " *" else "", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (c.required) KitDanger else KitInk,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (c.required) KitDanger.copy(alpha = .1f) else com.example.gavanhomes.ui.common.KitSoft).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("ALLOWED VALUES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                    columnRules(cols).forEach { Text(it, fontSize = 12.sp, color = KitInk) }
                    Text("The first row must be the column names. Each plot / unit number can appear only once. Documents are added per unit in the app.", fontSize = 12.sp, color = KitMuted, modifier = Modifier.padding(top = 4.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "↓ Sample CSV", fontWeight = FontWeight.Bold, color = KitInk, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).clickable { onSample() }.padding(vertical = 14.dp),
                    )
                    Text(
                        if (result == null) "Choose CSV file" else "Choose another", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F172A)).clickable { onChoose() }.padding(vertical = 14.dp),
                    )
                }

                if (existing > 0) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("WHAT TO DO WITH THE $existing EXISTING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImportMode.entries.forEach { m ->
                            val on = m == mode
                            Text(
                                m.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else KitInk, textAlign = TextAlign.Center,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Color(0xFF0F172A) else com.example.gavanhomes.ui.common.KitSoft).clickable { onMode(m) }.padding(horizontal = 12.dp, vertical = 11.dp),
                            )
                        }
                    }
                    Text(mode.help, fontSize = 12.sp, color = KitMuted)
                }

                if (result != null) {
                    Text("📄 ${result.fileName}", fontSize = 13.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (result.headerError != null) {
                        Text("⚠ ${result.headerError}", color = KitDanger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stat("${result.valid.size} valid", KitGood)
                            if (result.skipped.isNotEmpty()) Stat("${result.skipped.size} with errors", KitDanger)
                        }
                        if (result.merged > 0) Text("${result.merged} row${if (result.merged == 1) "" else "s"} repeated a number already in the file — only one unit is kept for each.", fontSize = 12.sp, color = KitMuted)
                        if (result.skipped.isNotEmpty()) Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KitDanger.copy(alpha = .08f)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("These rows are left out — fix them in the file and import again:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = KitDanger)
                            result.skipped.take(15).forEach { (line, errs) -> Text("Row $line: ${errs.joinToString(" · ")}", fontSize = 12.sp, color = KitDanger) }
                            if (result.skipped.size > 15) Text("…and ${result.skipped.size - 15} more", fontSize = 12.sp, color = KitMuted)
                        }
                        if (result.valid.isNotEmpty()) {
                            Text("PREVIEW (first 5)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                            result.valid.take(5).forEach { r ->
                                Text(
                                    r.values.filter { it.isNotBlank() }.take(5).joinToString(" · "), fontSize = 12.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(horizontal = 12.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            val n = result?.valid?.size ?: 0
            Text(
                if (n > 0) "Import $n row${if (n == 1) "" else "s"}" else "Import rows",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (n > 0) Color(0xFF0F172A) else Color(0xFFC9C7C2)).clickable(enabled = n > 0) { onImport() }.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun Stat(text: String, color: Color) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** The file name of a document the user chose. */
fun displayName(context: android.content.Context, uri: android.net.Uri): String {
    var name = ""
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) { val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (i >= 0) name = c.getString(i).orEmpty() }
    }
    return name.ifEmpty { uri.lastPathSegment.orEmpty() }
}
