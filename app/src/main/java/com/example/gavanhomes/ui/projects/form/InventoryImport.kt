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
    val skipped: List<Pair<Int, List<String>>>,   // line number → what is wrong
    val headerError: String?,
    val fileName: String,
)

/** The sample file: one header row with every column key of the type (documents are uploaded in the app, not by CSV). */
fun sampleCsv(cols: List<InvColumn>) = cols.joinToString(",") { it.key } + "\r\n"

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

fun parseInventoryCsv(text: String, cols: List<InvColumn>, fileName: String): CsvResult {
    val lines = text.removePrefix("﻿").replace("\r\n", "\n").replace("\r", "\n").split("\n").filter { it.isNotBlank() }
    if (lines.size < 2) return CsvResult(emptyList(), emptyList(), "File is empty or has no data rows.", fileName)

    val headers = splitCsvLine(lines[0]).map(::normalizeHeader)
    val missing = cols.filter { it.required && it.key !in headers }
    if (missing.isNotEmpty()) return CsvResult(emptyList(), emptyList(), "Missing required columns: ${missing.joinToString(", ") { it.key }}", fileName)

    val valid = mutableListOf<Map<String, String>>()
    val skipped = mutableListOf<Pair<Int, List<String>>>()
    lines.drop(1).forEachIndexed { i, line ->
        val values = splitCsvLine(line)
        val row = linkedMapOf<String, String>()
        val errors = mutableListOf<String>()
        cols.filterNot { it.isFile }.forEach { col ->
            val idx = headers.indexOf(col.key)
            val raw = if (idx >= 0) values.getOrNull(idx).orEmpty().trim() else ""
            row[col.key] = raw
            if (col.required && raw.isEmpty()) errors += "\"${col.key}\" is required"
            if (col.type == "select" && raw.isNotEmpty()) {
                if (raw.lowercase() !in col.options) { errors += "\"${col.key}\" must be one of: ${col.options.joinToString(", ")}"; row[col.key] = "" }
                else row[col.key] = raw.lowercase()
            }
            if (col.type == "number" && raw.isNotEmpty() && raw.toDoubleOrNull() == null) { errors += "\"${col.key}\" must be a number"; row[col.key] = "" }
        }
        if (errors.isEmpty()) valid += row else skipped += (i + 2) to errors
    }
    return CsvResult(valid, skipped, null, fileName)
}

/** Shows what a chosen CSV contains and imports its valid rows as new units (existing units are kept). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportSheet(
    type: String, cols: List<InvColumn>, result: CsvResult?, onChoose: () -> Unit, onSample: () -> Unit,
    onImport: () -> Unit, onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Text("Import CSV", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Text(type, fontSize = 12.sp, color = KitMuted)

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("EXPECTED COLUMNS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cols.filterNot { it.isFile }.forEach { c ->
                        Text(
                            c.key + if (c.required) " *" else "", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (c.required) KitDanger else KitInk,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (c.required) KitDanger.copy(alpha = .1f) else Color(0xFFF1EFEB)).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Text("Header names must match these keys. “status” must be available, booked, sold or reserved. Documents are added per unit in the app.", fontSize = 12.sp, color = KitMuted)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "↓ Sample CSV", fontWeight = FontWeight.Bold, color = KitInk, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1EFEB)).clickable { onSample() }.padding(vertical = 14.dp),
                    )
                    Text(
                        if (result == null) "Choose CSV file" else "Choose another", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F172A)).clickable { onChoose() }.padding(vertical = 14.dp),
                    )
                }

                if (result != null) {
                    Text("📄 ${result.fileName}", fontSize = 13.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (result.headerError != null) {
                        Text("⚠ ${result.headerError}", color = KitDanger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stat("${result.valid.size} valid", KitGood)
                            if (result.skipped.isNotEmpty()) Stat("${result.skipped.size} skipped", KitDanger)
                            Stat("Total ${result.valid.size + result.skipped.size}", KitMuted)
                        }
                        if (result.skipped.isNotEmpty()) Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KitDanger.copy(alpha = .08f)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            result.skipped.take(15).forEach { (line, errs) -> Text("Row $line: ${errs.joinToString(" · ")}", fontSize = 12.sp, color = KitDanger) }
                            if (result.skipped.size > 15) Text("…and ${result.skipped.size - 15} more", fontSize = 12.sp, color = KitMuted)
                        }
                        if (result.valid.isNotEmpty()) {
                            Text("PREVIEW (first 5)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                            result.valid.take(5).forEach { r ->
                                Text(
                                    r.values.filter { it.isNotBlank() }.take(5).joinToString(" · "), fontSize = 12.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F5F1)).padding(horizontal = 12.dp, vertical = 9.dp),
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
