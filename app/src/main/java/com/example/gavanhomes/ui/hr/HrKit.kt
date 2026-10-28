package com.example.gavanhomes.ui.hr

import androidx.compose.ui.graphics.Color
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.professionals.ProAmber
import com.example.gavanhomes.ui.professionals.ProBlue
import com.example.gavanhomes.ui.professionals.headline
import com.example.gavanhomes.ui.professionals.objects
import com.example.gavanhomes.ui.professionals.prettyDate
import org.json.JSONObject

/** One input of a module's form, as the website defines it (sent by GET /hr/{slug}). */
class HrField(val name: String, val label: String, val type: String, val required: Boolean, val options: List<Pair<String, String>>?)

/** How a module looks: its form fields, and which columns head a list card (title, sub-lines, status badge). */
class HrSchema(
    val slug: String, val label: String, val singular: String, val title: String, val sub: List<String>, val badge: String,
    val file: Boolean, val fields: List<HrField>,
) {
    /** The shown text of a stored value: a select's label ("on_leave" → "On Leave"), a date, or the raw text. */
    fun display(field: HrField, raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        field.options?.firstOrNull { it.first == raw }?.let { return it.second }
        return when (field.type) {
            "date" -> prettyDate(raw) ?: raw
            else -> raw
        }
    }

    fun field(name: String) = fields.firstOrNull { it.name == name }
}

fun parseSchema(j: JSONObject): HrSchema = HrSchema(
    slug = j.optString("slug"), label = j.optString("label"), singular = j.optString("singular"), title = j.optString("title"),
    sub = j.optJSONArray("sub")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(), badge = j.optString("badge"),
    file = j.optBoolean("file"),
    fields = j.objects("fields").map { f ->
        HrField(
            f.optString("name"), f.optString("label"), f.optString("type", "text"), f.optBoolean("required"),
            f.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> o.optString("value") to o.optString("label") } } },
        )
    },
)

/** The schemas the lists already fetched, so a form opens without asking the server again. */
object HrSchemas { val cache = mutableMapOf<String, HrSchema>() }

/** The line a record is known by in lists: its title column, else the first sub-line, else its reference. */
fun HrSchema.titleOf(row: JSONObject): String =
    row.str(title) ?: sub.firstNotNullOfOrNull { row.str(it) } ?: row.str("reference") ?: singular

fun HrSchema.subOf(row: JSONObject): String =
    sub.mapNotNull { key -> row.str(key)?.let { v -> field(key)?.let { display(it, v) } ?: if (key.endsWith("_date") || key.endsWith("_day")) prettyDate(v) ?: v else v.headlineIfCode() } }.joinToString(" · ")

private fun String.headlineIfCode(): String = if (all { it.isLowerCase() || it == '_' } && contains('_')) headline() else this

/** Colours for the statuses the HR modules use (requests, visitors, leave, candidates, F&F …). */
fun hrStatusColor(s: String?): Color = when (s?.lowercase()) {
    "active", "approved", "completed", "paid", "settled", "hired", "checked_out", "available", "done", "closed", "generated", "reviewed" -> KitGood
    "pending", "expected", "scheduled", "in_progress", "interview", "on_leave", "in_service", "draft", "under_review", "screening", "due" -> ProAmber
    "rejected", "terminated", "cancelled", "overdue", "inactive", "blacklisted", "absent", "lost", "damaged", "expired" -> KitDanger
    "checked_in", "applied", "open", "assigned", "offer", "submitted" -> ProBlue
    else -> Color(0xFF9AA5B8)
}

fun hrStatusLabel(s: String?): String = s?.headline() ?: "—"
