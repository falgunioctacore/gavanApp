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
    val aadharFront = FileSlot(src?.str("aadhar_front"))
    val aadharBack = FileSlot(src?.str("aadhar_back"))
    val panDoc = FileSlot(src?.str("pan_doc"))
    /** The number that passed the OTP check (blank = none); the tag shows only while the mobile still matches it. */
    var verifiedMobile by mutableStateOf(if (src?.optBoolean("mobile_verified") == true) src.str("mobile").orEmpty() else "")
    var mobileToken by mutableStateOf("")                     // proof from a fresh OTP check, sent with the save
    var developer by mutableStateOf<Int?>(if (src != null && src.has("developer") && !src.isNull("developer")) src.optInt("developer") else null)

    private fun last10(s: String) = s.filter(Char::isDigit).takeLast(10)
    val mobileVerified get() = verifiedMobile.isNotBlank() && last10(mobile) == last10(verifiedMobile)

    /** Why this partner cannot be saved, per field (empty when fine): Aadhaar is 12 digits, PAN is ABCDE1234F. */
    fun problems(): Map<String, String> = buildMap {
        if (aadhar.isNotBlank() && !Regex("^\\d{4}\\s?\\d{4}\\s?\\d{4}$").matches(aadhar.trim())) put("aadhar", "Aadhaar must be 12 digits")
        if (pan.isNotBlank() && !Regex("^[A-Za-z]{5}\\d{4}[A-Za-z]$").matches(pan.trim())) put("pan", "PAN must look like ABCDE1234F")
    }
}

/** One developer of the project — an entity (company, LLP, firm), so no Aadhaar: entity type, CIN / LLPIN, certificate, RERA, signatory. */
class DeveloperState(src: JSONObject? = null) {
    val key = nextUnitId++
    var id by mutableStateOf(src?.optInt("id", 0) ?: 0)
    var name by mutableStateOf(src?.str("name").orEmpty())
    var isLead by mutableStateOf(src?.optBoolean("is_lead") == true)
    var entityType by mutableStateOf(src?.str("entity_type").orEmpty())      // pvt_ltd | llp | partnership | proprietorship
    var cin by mutableStateOf(src?.str("cin_llpin").orEmpty())
    var reraNumber by mutableStateOf(src?.str("rera_number").orEmpty())
    var signatoryName by mutableStateOf(src?.str("signatory_name").orEmpty())
    var signatoryMobile by mutableStateOf(src?.str("signatory_mobile").orEmpty())
    var pan by mutableStateOf(src?.str("pan").orEmpty())
    var gst by mutableStateOf(src?.str("gst").orEmpty())
    var address by mutableStateOf(src?.str("address").orEmpty())
    val logo = FileSlot(src?.str("logo"))
    val incorporationDoc = FileSlot(src?.str("incorporation_doc"))
    val reraDoc = FileSlot(src?.str("rera_doc"))
    val panDoc = FileSlot(src?.str("pan_doc"))
    val gstDoc = FileSlot(src?.str("gst_doc"))
    val proofDoc = FileSlot(src?.str("proof_doc"))

    fun problems(): Map<String, String> = buildMap {
        if (pan.isNotBlank() && !Regex("^[A-Za-z]{5}\\d{4}[A-Za-z]$").matches(pan.trim())) put("pan", "PAN must look like ABCDE1234F")
    }
}

val EntityTypes = listOf("pvt_ltd" to "Pvt Ltd", "llp" to "LLP", "partnership" to "Partnership", "proprietorship" to "Proprietorship")

/** One milestone of a plot project's own payment schedule. */
class Milestone(src: JSONObject? = null) {
    var name by mutableStateOf(src?.str("name").orEmpty())
    var mode by mutableStateOf(src?.str("mode") ?: "percent")          // percent | amount
    var value by mutableStateOf(src?.str("value").orEmpty())
    var dueType by mutableStateOf(src?.str("due_type") ?: "days")      // date | days | months | emi
    var dueValue by mutableStateOf(src?.str("due_value").orEmpty())    // a date (yyyy-MM-dd), a number of days / months, or the number of EMIs

    fun json() = JSONObject().put("name", name.trim()).put("mode", mode).put("value", value.toDoubleOrNull() ?: JSONObject.NULL)
        .put("due_type", dueType).put("due_value", if (dueType == "date") dueValue else (dueValue.toDoubleOrNull() ?: 0))
}

/** Ready-made plot schedules: name → milestones (percent of the plot price). */
val MilestoneTemplates: List<Pair<String, List<Milestone.() -> Unit>>> = listOf(
    "Full payment in 3 months" to listOf<Milestone.() -> Unit>({ name = "Full payment"; value = "100"; dueType = "months"; dueValue = "3" }),
    "10% booking + 90% in 6 EMIs" to listOf<Milestone.() -> Unit>(
        { name = "Booking"; value = "10"; dueType = "days"; dueValue = "0" },
        { name = "Balance in EMIs"; value = "90"; dueType = "emi"; dueValue = "6" },
    ),
    "20% booking, 30% in 30 days, 50% at registry" to listOf<Milestone.() -> Unit>(
        { name = "Booking"; value = "20"; dueType = "days"; dueValue = "0" },
        { name = "Agreement"; value = "30"; dueType = "days"; dueValue = "30" },
        { name = "Registry"; value = "50"; dueType = "months"; dueValue = "3" },
    ),
)

/** Charges that are a percentage of the deal by default; every charge can be switched to a fixed ₹ amount or back. */
val PercentCharges = setOf("gst", "stamp_duty", "registration_charges")

/** One inventory column of a property type, as sent by GET /projects/options. */
class InvColumn(val key: String, val label: String, val type: String, val options: List<String>, val required: Boolean, val csv: String? = null) {
    val isFile get() = type == "file"
    /** The header of this column in a CSV file (the plot template calls the unit number "plot_no"). */
    val csvKey get() = csv ?: key
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
    fun duplicate() = UnitState().also { copy -> copy.values.putAll(values); copy.values.remove("id") }

    /** The unit's number as the server sees it — "73", "Unit 73" and "unit73" are the same plot. Null when it has none. */
    val key: String? get() = unitKeyOf(values)
    val savedId: Int? get() = values["id"]?.toIntOrNull()
}

/** One unit number from its values: the first of unit / flat / shop / house no. that is filled, minus a "Unit " style prefix. */
fun unitKeyOf(v: Map<String, String>): String? {
    for (c in listOf("unit_no", "flat_no", "shop_no", "house_no")) {
        val n = v[c].orEmpty().trim().lowercase().replace(Regex("^(unit|flat|shop|house|plot)\\s*(no\\.?|number|#)?\\s*[-:#.]*\\s*"), "").replace(Regex("\\s+"), "")
        if (n.isNotEmpty()) return n
    }
    return null
}

/** Plain-value columns (one text box each), grouped the way the website groups them. */
val ProjectKeys = listOf("name", "code", "status_id", "leader_id", "description", "address", "city", "state", "pincode", "phone", "email", "start_date", "end_date")
val DeveloperKeys = listOf("developer_name", "developer_pan", "developer_gst", "developer_address")   // the lead developer, kept in step with the list below by the server
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
    val developers = mutableStateListOf<DeveloperState>()
    val inventory = mutableStateMapOf<String, SnapshotStateList<UnitState>>()
    val milestones = mutableStateListOf<Milestone>()
    val chargeModes = mutableStateMapOf<String, String>()   // charge column → "percent" | "amount" (only the ones changed from the default)

    private fun isPlot(t: String) = t.contains("plot", ignoreCase = true)
    /** Plot projects have no construction stages: their schedule is the milestone list. */
    val plotOnly get() = types.isNotEmpty() && types.all(::isPlot)
    val hasPlot get() = types.any(::isPlot)

    fun chargeMode(key: String) = chargeModes[key] ?: if (key in PercentCharges) "percent" else "amount"

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
        // only fixed-₹ charges add up here — a percentage has no amount until there is a deal value
        val values = PriceFields.filter { chargeMode(it.first) == "amount" }.mapNotNull { get(it.first).toDoubleOrNull() }
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

        developers.clear()
        f.optJSONArray("developers")?.let { a -> for (i in 0 until a.length()) developers.add(DeveloperState(a.getJSONObject(i))) }
        // a project saved before developers became a list: its one developer is the first entry
        if (developers.isEmpty() && f.str("developer_name") != null) developers.add(DeveloperState().also { d ->
            d.name = f.str("developer_name").orEmpty(); d.pan = f.str("developer_pan").orEmpty(); d.gst = f.str("developer_gst").orEmpty(); d.address = f.str("developer_address").orEmpty(); d.isLead = true
            d.panDoc.existing = f.optJSONObject("files")?.str("developer_pan_proof"); d.gstDoc.existing = f.optJSONObject("files")?.str("developer_gst_proof"); d.proofDoc.existing = f.optJSONObject("files")?.str("developer_proof")
        })
        partners.clear()
        f.optJSONArray("partners")?.let { a -> for (i in 0 until a.length()) partners.add(PartnerState(a.getJSONObject(i))) }

        milestones.clear()
        f.optJSONArray("payment_milestones")?.let { a -> for (i in 0 until a.length()) milestones.add(Milestone(a.getJSONObject(i))) }
        chargeModes.clear()
        f.optJSONObject("charge_modes")?.let { o -> o.keys().forEach { k -> chargeModes[k] = o.optString(k) } }

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
        val payKeys = PayFields.map { it.first }.toSet()
        // a plot-only project has no construction stages: they are sent blank so old values do not linger
        AllScalarKeys.forEach { add(it, if (plotOnly && it in payKeys) "" else get(it)) }
        add("payment_milestones", if (hasPlot && milestones.isNotEmpty()) org.json.JSONArray(milestones.map { it.json() }).toString() else "")
        add("charge_modes", if (chargeModes.isEmpty()) "" else JSONObject(chargeModes.toMap<String, Any>()).toString())
        add("total_consideration", total()?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "")
        if (active) add("active", "1")
        add("rera_registered", if (reraRegistered) "1" else "0")
        add("developer_partner", if (developerPartner) "1" else "0")
        types.sorted().forEach { add("type[]", it) }
        memberIds.sorted().forEach { add("member_ids[]", it.toString()) }

        files.forEach { (k, slot) -> slot.picked?.let { file(k, it) } }

        // developers: always sent as a whole list (the first marked lead, else the first, is the lead)
        val leadAt = developers.indexOfFirst { it.isLead }.takeIf { it >= 0 } ?: 0
        developers.forEachIndexed { i, d ->
            if (d.id > 0) add("developers[$i][id]", d.id.toString())
            add("developers[$i][name]", d.name); add("developers[$i][is_lead]", if (i == leadAt) "1" else "0"); add("developers[$i][entity_type]", d.entityType)
            add("developers[$i][cin_llpin]", d.cin); add("developers[$i][rera_number]", d.reraNumber); add("developers[$i][signatory_name]", d.signatoryName)
            add("developers[$i][signatory_mobile]", d.signatoryMobile); add("developers[$i][pan]", d.pan); add("developers[$i][gst]", d.gst); add("developers[$i][address]", d.address)
            listOf("logo" to d.logo, "incorporation_doc" to d.incorporationDoc, "rera_doc" to d.reraDoc, "pan_doc" to d.panDoc, "gst_doc" to d.gstDoc, "proof_doc" to d.proofDoc).forEach { (k, slot) ->
                slot.picked?.let { file("developers[$i][$k]", it) } ?: slot.existing?.let { add("developers[$i][existing_$k]", it) }
            }
        }
        if (developers.isEmpty()) add("developers", "")

        if (developerPartner) partners.forEachIndexed { i, p ->
            add("partners[$i][name]", p.name); add("partners[$i][aadhar]", p.aadhar); add("partners[$i][pan]", p.pan)
            add("partners[$i][mobile]", p.mobile); add("partners[$i][email]", p.email)
            p.developer?.takeIf { it in developers.indices }?.let { add("partners[$i][developer]", it.toString()) }
            if (p.mobileToken.isNotBlank() && p.mobileVerified) add("partners[$i][mobile_token]", p.mobileToken)
            listOf("photo" to p.photo, "esign" to p.esign, "aadhar_front" to p.aadharFront, "aadhar_back" to p.aadharBack, "pan_doc" to p.panDoc).forEach { (k, slot) ->
                slot.picked?.let { file("partners[$i][$k]", it) } ?: slot.existing?.let { add("partners[$i][existing_$k]", it) }
            }
        }

        types.sorted().forEach { type ->
            val slug = slugOf(type)
            val cols = columns[type].orEmpty().filterNot { it.isFile }
            unitsOf(type).forEachIndexed { i, u ->
                u.savedId?.let { add("inventory[$slug][$i][id]", it.toString()) }
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
