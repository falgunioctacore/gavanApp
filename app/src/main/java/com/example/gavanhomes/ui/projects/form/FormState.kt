package com.example.gavanhomes.ui.projects.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.data.str
import org.json.JSONObject

/** The website's project form, field for field (names match routes/api.php → Admin\ProjectController). */

/** A document the user picked on the phone, ready to upload. */
class PickedFile(val name: String, val mime: String, val bytes: ByteArray)

/** One document field: what is already stored on the server, and/or a newly picked file that will replace it. */
class FileSlot(existing: String? = null) {
    var existing by mutableStateOf(existing)
    var picked by mutableStateOf<PickedFile?>(null)
    val hasFile get() = picked != null || existing != null
}

class PartnerState(src: JSONObject? = null) {
    var name by mutableStateOf(src?.str("name").orEmpty())
    var aadhar by mutableStateOf(src?.str("aadhar").orEmpty())
    var pan by mutableStateOf(src?.str("pan").orEmpty())
    var mobile by mutableStateOf(src?.str("mobile").orEmpty())
    var email by mutableStateOf(src?.str("email").orEmpty())
    val photo = FileSlot(src?.str("photo"))
    val esign = FileSlot(src?.str("esign"))
}

/** One inventory column of a property type, as sent by GET /projects/options. */
class InvColumn(val key: String, val label: String, val type: String, val options: List<String>, val required: Boolean) {
    val isFile get() = type == "file"
}

val InventoryFileKeys = listOf("unit_plan", "vastu_report", "statement", "plan_copy")

private var nextUnitId = 1L

/** One row of the unit inventory. Plain columns live in [values]; the four documents in [files]. */
class UnitState(src: JSONObject? = null) {
    val id = nextUnitId++
    val values = mutableStateMapOf<String, String>().also { m ->
        src?.keys()?.forEach { k -> if (k !in InventoryFileKeys) src.str(k)?.let { m[k] = it } }
    }
    val files: Map<String, FileSlot> = InventoryFileKeys.associateWith { FileSlot(src?.str(it)) }

    /** A new unit with the same plain values (documents are not copied). */
    fun duplicate() = UnitState().also { copy -> copy.values.putAll(values) }
}

/** Plain-value columns (one text box each), grouped the way the website groups them. */
val ProjectKeys = listOf("name", "code", "status_id", "leader_id", "description", "address", "city", "state", "pincode", "phone", "email", "start_date", "end_date")
val DeveloperKeys = listOf("developer_name", "developer_adhar", "developer_pan", "developer_gst", "developer_address")
val ReraKeys = listOf(
    "rera_number", "rera_completion_date", "rera_agent_aadhaar", "rera_agent_pan", "rera_agent_email",
    "rera_agent_mobile", "rera_agent_estg", "rera_agent_address",
)
val PriceFields = listOf(
    "floor_rise_charges" to "Floor Rise Charges", "preferential_location_charges" to "PLC (Preferential Location)",
    "car_parking_charges" to "Car Parking Charges", "clubhouse_amenity_charges" to "Clubhouse / Amenity Charges",
    "maintenance_deposit" to "Maintenance Deposit", "legal_documentation_charges" to "Legal / Documentation Charges",
    "gst" to "GST (applicable rate)", "stamp_duty" to "Stamp Duty", "registration_charges" to "Registration Charges",
    "electricity_connection_charges" to "Electricity (GEB) Charges", "gas_connection_charges" to "Gas (GSPC) Charges",
    "society_formation_charges" to "Society Formation Charges", "city_survey_charges" to "City Survey Charges",
    "municipal_authority_charges" to "Municipal / Local Authority", "infrastructure_development_charges" to "Infrastructure Development",
    "fabrication_additional_work_charges" to "Fabrication / Additional Work",
)
/** key, label, the website's default for a new project */
val PayFields = listOf(
    Triple("payment_on_booking", "On Booking (%)", "8"), Triple("within_30_days_agreement", "Within 30 days (%)", "12"),
    Triple("payment_on_plinth_completion", "On Plinth (%)", "9"), Triple("payment_on_1st_slab", "1st Slab (%)", "7"),
    Triple("payment_on_3rd_slab", "3rd Slab (%)", "7"), Triple("payment_on_5th_slab", "5th Slab (%)", "7"),
    Triple("payment_on_7th_slab", "7th Slab (%)", "7"), Triple("payment_on_9th_slab", "9th Slab (%)", "7"),
    Triple("payment_on_11th_slab", "11th Slab (%)", "7"), Triple("payment_on_12th_slab", "12th Slab (%)", "5"),
    Triple("on_brickwork_completion", "Brickwork (%)", "5"), Triple("on_plastering_completion", "Plastering (%)", "5"),
    Triple("on_flooring_completion", "Flooring (%)", "5"), Triple("on_possession_oc", "Possession / OC (%)", "9"),
)
val ProjectFileKeys = listOf(
    "developer_adhar_proof", "developer_pan_proof", "developer_gst_proof", "developer_proof",
    "rera_certificate", "rera_agent_photo", "rera_agent_certificate",
)

private val AllScalarKeys = ProjectKeys + DeveloperKeys + ReraKeys + PriceFields.map { it.first } + PayFields.map { it.first }

fun slugOf(type: String) = type.lowercase().replace(Regex("[^a-z0-9]+"), "_")

class ProjectForm {
    val text = mutableStateMapOf<String, String>()
    var active by mutableStateOf(true)
    var reraRegistered by mutableStateOf(false)
    var developerPartner by mutableStateOf(false)
    var types by mutableStateOf<Set<String>>(emptySet())
    var memberIds by mutableStateOf<Set<Int>>(emptySet())
    val files: Map<String, FileSlot> = ProjectFileKeys.associateWith { FileSlot() }
    val partners = mutableStateListOf<PartnerState>()
    val inventory = mutableStateMapOf<String, SnapshotStateList<UnitState>>()

    /** Field → message from the last rejected save (keys as the server names them, e.g. "email", "partners.0.email"). */
    var errors by mutableStateOf<Map<String, String>>(emptyMap())

    fun get(key: String) = text[key].orEmpty()
    fun set(key: String, value: String) { text[key] = value }

    init {
        // a new project starts with the website's payment-schedule defaults
        PayFields.forEach { (k, _, default) -> text[k] = default }
    }

    fun unitsOf(type: String): SnapshotStateList<UnitState> = inventory.getOrPut(type) { mutableStateListOf() }
    val unitCount get() = types.sumOf { inventory[it]?.size ?: 0 }

    /** Sum of the cost-breakdown fields — what the website shows as "Total Consideration". */
    fun total(): Double? {
        val values = PriceFields.mapNotNull { get(it.first).toDoubleOrNull() }
        return if (values.isEmpty()) null else values.sum()
    }

    /** Sum of the payment-schedule percentages (should be 100). */
    fun payTotal(): Double = PayFields.sumOf { get(it.first).toDoubleOrNull() ?: 0.0 }

    /** Fill every field from `form` of GET /projects/{uuid}. */
    fun load(f: JSONObject) {
        AllScalarKeys.forEach { k -> text[k] = f.str(k) ?: if (k in PayFields.map { it.first }) PayFields.first { p -> p.first == k }.third else "" }
        active = f.optBoolean("active", true)
        reraRegistered = f.optBoolean("rera_registered")
        developerPartner = f.optBoolean("developer_partner")
        types = f.optJSONArray("type")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        memberIds = f.optJSONArray("member_ids")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()

        val stored = f.optJSONObject("files")
        ProjectFileKeys.forEach { k -> files.getValue(k).existing = stored?.str(k) }

        partners.clear()
        f.optJSONArray("partners")?.let { a -> for (i in 0 until a.length()) partners.add(PartnerState(a.getJSONObject(i))) }

        inventory.clear()
        f.optJSONObject("inventory")?.let { inv ->
            inv.keys().forEach { type ->
                val rows = inv.getJSONArray(type)
                inventory[type] = mutableStateListOf<UnitState>().also { list -> for (i in 0 until rows.length()) list.add(UnitState(rows.getJSONObject(i))) }
            }
        }
    }

    /**
     * The multipart body of the website's form. Blank project fields are sent blank (so they can be cleared);
     * blank unit columns are left out (the inventory is rewritten as a whole, and this keeps big lists small).
     * Only the types currently selected are sent, as on the website.
     */
    fun parts(editing: Boolean, columns: Map<String, List<InvColumn>>): List<FormPart> {
        val out = mutableListOf<FormPart>()
        fun add(name: String, value: String) { out += FormPart(name, value) }
        fun file(name: String, p: PickedFile) { out += FormPart(name, fileName = p.name, mime = p.mime, bytes = p.bytes) }

        if (editing) add("_method", "PUT")
        AllScalarKeys.forEach { add(it, get(it)) }
        add("total_consideration", total()?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "")
        if (active) add("active", "1")
        add("rera_registered", if (reraRegistered) "1" else "0")
        add("developer_partner", if (developerPartner) "1" else "0")
        types.sorted().forEach { add("type[]", it) }
        memberIds.sorted().forEach { add("member_ids[]", it.toString()) }

        files.forEach { (k, slot) -> slot.picked?.let { file(k, it) } }

        if (developerPartner) partners.forEachIndexed { i, p ->
            add("partners[$i][name]", p.name); add("partners[$i][aadhar]", p.aadhar); add("partners[$i][pan]", p.pan)
            add("partners[$i][mobile]", p.mobile); add("partners[$i][email]", p.email)
            p.photo.picked?.let { file("partners[$i][photo]", it) } ?: p.photo.existing?.let { add("partners[$i][existing_photo]", it) }
            p.esign.picked?.let { file("partners[$i][esign]", it) } ?: p.esign.existing?.let { add("partners[$i][existing_esign]", it) }
        }

        types.sorted().forEach { type ->
            val slug = slugOf(type)
            val cols = columns[type].orEmpty().filterNot { it.isFile }
            unitsOf(type).forEachIndexed { i, u ->
                cols.forEach { c -> u.values[c.key]?.takeIf { it.isNotBlank() }?.let { add("inventory[$slug][$i][${c.key}]", it) } }
                InventoryFileKeys.forEach { fk ->
                    val slot = u.files.getValue(fk)
                    val p = slot.picked
                    if (p != null) file("inventory_files[$slug][$i][$fk]", p) else slot.existing?.let { add("inventory[$slug][$i][$fk]", it) }
                }
            }
        }
        return out
    }
}
