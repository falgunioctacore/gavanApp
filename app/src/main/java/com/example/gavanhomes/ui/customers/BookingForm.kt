package com.example.gavanhomes.ui.customers

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.DeleteButton
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.inventory.Banner
import com.example.gavanhomes.ui.inventory.DatePick
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.SaveBar
import com.example.gavanhomes.ui.inventory.Waiting
import com.example.gavanhomes.ui.inventory.rupees
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.whenLabel
import com.example.gavanhomes.ui.procurement.ActionButton
import com.example.gavanhomes.ui.procurement.MiniRow
import com.example.gavanhomes.ui.procurement.plain
import com.example.gavanhomes.ui.professionals.ProBar
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.Label
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject


// ───────────────────────────── booking form ─────────────────────────────

private val CostLines = listOf(
    "cost_bsp" to "Basic sale price (BSP)", "cost_floor_rise" to "Floor rise", "cost_plc" to "Preferred location (PLC)", "cost_parking" to "Parking", "cost_clubhouse" to "Clubhouse / amenities",
    "cost_maintenance_deposit" to "Maintenance deposit", "cost_legal_docs" to "Legal documents", "cost_gst" to "GST", "cost_stamp_duty" to "Stamp duty", "cost_registration" to "Registration",
    "cost_electricity" to "Electricity connection", "cost_gas" to "Gas connection", "cost_society_formation" to "Society formation", "cost_city_survey" to "City survey",
    "cost_municipal" to "Municipal charges", "cost_infrastructure" to "Infrastructure", "cost_fabrication" to "Fabrication / extra work",
)
private val Stages = listOf(
    "booking" to "On booking", "agreement" to "Within 30 days / agreement", "plinth" to "Plinth completion", "slab1" to "1st slab", "slab3" to "3rd slab", "slab5" to "5th slab", "slab7" to "7th slab",
    "slab9" to "9th slab", "slab11" to "11th slab", "slab12" to "12th slab (top)", "brickwork" to "Brickwork completion", "plastering" to "Plastering completion", "flooring" to "Flooring completion",
    "possession" to "Possession / OC",
)
private val DocChecks = listOf("doc_pan" to "PAN card", "doc_aadhaar" to "Aadhaar card", "doc_photos" to "Photographs", "doc_address" to "Address proof", "doc_cheque" to "Booking cheque", "doc_income" to "Income proof",
    "doc_co_applicant" to "Co-applicant documents", "doc_booking" to "Signed booking form")

private fun num(s: String?) = s?.replace(",", "")?.toDoubleOrNull() ?: 0.0
private fun money2(v: Double) = if (v == 0.0) "" else (Math.round(v * 100) / 100.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }

/** 39600 → "Thirty-Nine Thousand Six Hundred Only" (Indian grouping), like the website's form. */
private fun inWords(value: Double): String {
    var n = Math.round(value)
    if (n <= 0) return ""
    val ones = listOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
    val tens = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
    fun two(x: Int) = if (x < 20) ones[x] else tens[x / 10] + if (x % 10 != 0) "-" + ones[x % 10] else ""
    fun three(x: Int): String = listOfNotNull(if (x / 100 > 0) ones[x / 100] + " Hundred" else null, if (x % 100 > 0) two(x % 100) else null).joinToString(" ")
    val crore = (n / 10000000).toInt(); n %= 10000000
    val lakh = (n / 100000).toInt(); n %= 100000
    val thousand = (n / 1000).toInt(); n %= 1000
    return listOfNotNull(if (crore > 0) three(crore) + " Crore" else null, if (lakh > 0) two(lakh) + " Lakh" else null, if (thousand > 0) two(thousand) + " Thousand" else null, if (n > 0) three(n.toInt()) else null).joinToString(" ") + " Only"
}

/**
 * New booking / edit booking — the website's booking form on the phone: the unit, applicant and co-applicant, property and rate, the cost sheet
 * (BSP = carpet area × rate; GST 5 %, stamp duty 4.9 %, registration 1 % and municipal 1 % of BSP; the total is the sum), the booking payment,
 * the stage-wise payment schedule (the project's percentages), home loan, document checklist and office use. A booking can be kept as a draft,
 * which holds no unit; a draft can be submitted later. [lead] starts it from a lead. Data: GET …/customer-center/booking-options · POST …/bookings · PUT …/bookings/{id}.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookingFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, lead: Int?, onBack: () -> Unit, onSaved: () -> Unit, unit: Int? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = remember { mutableStateMapOf<String, String>() }
    val touched = remember { mutableStateListOf<String>() }               // schedule rows the person typed — never overwritten
    var o by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var initial by remember { mutableStateOf<Map<String, String>?>(null) }
    var errors by com.example.gavanhomes.ui.common.rememberErrorsState()
    var banner by com.example.gavanhomes.ui.common.rememberBannerState()
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var wasDraft by remember { mutableStateOf(false) }
    val picked = remember { mutableStateMapOf<String, com.example.gavanhomes.ui.projects.form.PickedFile>() }       // documents chosen on this screen, by form field
    val onFile = remember { mutableStateMapOf<String, JSONObject>() }                                             // documents already on the booking
    var pickFor by remember { mutableStateOf("") }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { com.example.gavanhomes.ui.projects.form.readPicked(context, it) }?.let { picked[pickFor] = it }
    }

    LaunchedEffect(attempt) {
        loadError = null
        try {
            val opts = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/customer-center/booking-options" + (if (id != null) "?booking_id=$id" else if (lead != null) "?lead_id=$lead" else "")) }
            if (id != null) {
                val x = withContext(Dispatchers.IO) { api.detail("/customer-center/bookings/$id") }
                wasDraft = x.optBoolean("draft")
                x.optJSONObject("files")?.let { fl -> fl.keys().forEach { k -> fl.optJSONObject(k)?.let { onFile[if (k == "signature_path") "signature" else k] = it } } }
                x.optJSONObject("raw")?.let { r -> r.keys().forEach { k -> if (!r.isNull(k)) f[k] = r.get(k).toString().let { v -> if (v.endsWith(".00")) v.dropLast(3) else v } } }
                Stages.forEach { (k, _) -> if (num(f["schedule_${k}_amount"]) > 0) touched.add(k) }
            } else {
                opts.optJSONObject("lead")?.let { l -> f["applicant_name"] = l.str("name").orEmpty(); f["applicant_mobile"] = l.str("mobile").orEmpty().filter(Char::isDigit).takeLast(10); f["applicant_email"] = l.str("email").orEmpty(); f["lead_id"] = l.optInt("id").toString() }
                f["booking_date"] = java.time.LocalDate.now().toString(); f["payment_plan"] = "clpp"; f["payment_mode"] = "Cheque"
                f["booking_percent"] = money2(opts.optJSONObject("schedule_percent")?.optDouble("booking", 0.0) ?: 0.0)
                f["office_sales_exec"] = opts.str("sales_exec").orEmpty()
                opts.optJSONObject("charges")?.let { c -> c.keys().forEach { k -> if (!c.isNull(k)) f[k] = money2(c.optDouble(k)) } }
            }
            o = opts
            initial = f.toMap()
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
    }
    val title = if (id != null) (if (wasDraft) "Edit draft booking" else "Edit booking") else "New booking"
    val back = leaveGuard(initial != null && f.toMap() != initial, onBack)
    val opts = o ?: return Waiting(b, title, loadError, onBack) { attempt++ }
    val units = opts.objs("units")
    val pct = opts.optJSONObject("schedule_percent") ?: JSONObject()
    val auto = opts.optJSONObject("auto_percent") ?: JSONObject()

    fun bsp() = num(f["cost_bsp"])
    fun total() = CostLines.sumOf { num(f[it.first]) }
    fun base() = if (bsp() > 0) bsp() else total()
    // BSP = carpet × rate, then the auto-percentage charges, booking amount and the stage amounts (unless typed)
    fun recalc() {
        val area = num(f["carpet_area"]); val rate = num(f["rate_per_sqft"])
        if (area > 0 && rate > 0) {
            f["cost_bsp"] = money2(area * rate)
            auto.keys().forEach { k -> f[k] = money2(area * rate * auto.optDouble(k) / 100) }
        }
        val p = num(f["booking_percent"]); val base = base()
        if (p > 0 && base > 0) f["booking_amount"] = money2(base * p / 100)
        if (base > 0) {
            Stages.forEach { (k, _) -> val sp = pct.optDouble(k, 0.0); if (k !in touched && sp > 0) f["schedule_${k}_amount"] = if (k == "booking" && num(f["booking_amount"]) > 0) f["booking_amount"].orEmpty() else money2(base * sp / 100) }
        }
        f["total_consideration"] = money2(total()); f["amount_in_words"] = inWords(total())
        f["schedule_total"] = money2(Stages.sumOf { (k, _) -> num(f["schedule_${k}_amount"]) })
    }
    fun pickUnit(u: JSONObject) {
        f["inv_id"] = u.optInt("id").toString(); f["flat_no"] = u.str("number").orEmpty(); f["tower_wing"] = u.str("wing").orEmpty(); f["floor"] = u.opt("floor")?.toString()?.takeIf { it != "null" }.orEmpty()
        f["facing"] = u.str("facing").orEmpty(); f["unit_type"] = u.str("type").orEmpty(); f["carpet_area"] = money2(u.optDouble("carpet", 0.0)); f["balcony_area"] = money2(u.optDouble("balcony", 0.0))
        f["super_builtup"] = money2(u.optDouble("builtup", 0.0))
        val price = u.optDouble("price", 0.0); val carpet = u.optDouble("carpet", 0.0)
        if (price > 0 && carpet > 0) f["rate_per_sqft"] = money2(price / carpet)
        recalc()
    }
    fun body(draft: Boolean): JSONObject {
        val j = JSONObject()
        f.forEach { (k, v) -> val t = v.trim(); if (t.isNotEmpty() && k !in DocChecks.map { it.first } && k != "loan_required" && k != "existing_loan") j.put(k, t) }
        DocChecks.forEach { (k, _) -> j.put(k, if (f[k] == "1") 1 else 0) }
        j.put("loan_required", if (f["loan_required"] == "1") 1 else 0); j.put("existing_loan", if (f["existing_loan"] == "1") 1 else 0)
        if (draft) j.put("save_as_draft", 1)
        return j
    }
    fun parts(draft: Boolean): List<com.example.gavanhomes.data.FormPart> {
        val j = body(draft)
        val out = mutableListOf<com.example.gavanhomes.data.FormPart>()
        j.keys().forEach { k -> out.add(com.example.gavanhomes.data.FormPart(k, value = j.get(k).toString())) }
        picked.forEach { (k, p) -> out.add(com.example.gavanhomes.data.FormPart(k, fileName = p.name, mime = p.mime, bytes = p.bytes)) }
        return out
    }
    fun save(draft: Boolean) {
        val e = mutableMapOf<String, String>()
        if (f["inv_id"].isNullOrBlank()) e["inv_id"] = "Select the unit."
        if (f["applicant_name"].isNullOrBlank()) e["applicant_name"] = "Enter the applicant's full name."
        if (!Regex("^\\d{10}$").matches(f["applicant_mobile"].orEmpty())) e["applicant_mobile"] = "Enter a 10-digit mobile number."
        if (f["booking_date"].isNullOrBlank()) e["booking_date"] = "Pick the booking date."
        f["applicant_alt_mobile"]?.takeIf { it.isNotBlank() && !Regex("^\\d{10}$").matches(it) }?.let { e["applicant_alt_mobile"] = "Must be a 10-digit number." }
        f["co_mobile"]?.takeIf { it.isNotBlank() && !Regex("^\\d{10}$").matches(it) }?.let { e["co_mobile"] = "Must be a 10-digit number." }
        listOf("applicant_pan", "co_pan").forEach { k -> f[k]?.takeIf { it.isNotBlank() && !Regex("^[A-Z]{5}[0-9]{4}[A-Z]$").matches(it.uppercase()) }?.let { e[k] = "Format ABCDE1234F." } }
        listOf("applicant_aadhaar", "co_aadhaar").forEach { k -> f[k]?.takeIf { it.isNotBlank() && it.length != 12 }?.let { e[k] = "Must be 12 digits." } }
        if (e.isNotEmpty()) { errors = e; banner = e.values.first(); return }
        recalc()
        errors = emptyMap(); banner = null
        bookingSubmit(scope, api, id?.let { "/customer-center/bookings/$it" }, "/projects/$uuid/customer-center/bookings", body(draft), if (picked.isEmpty()) null else parts(draft),
            if (draft) "Draft saved" else if (id != null && !wasDraft) "Booking saved" else "Booking created", { saving = it }, { er, m -> errors = er; banner = m }, onSaved)
    }
    val unitLabel = units.firstOrNull { it.optInt("id").toString() == f["inv_id"] }?.optString("label")
    // started from a unit (its sheet's Book button): the unit is already chosen and its details filled in
    LaunchedEffect(unit) { if (id == null && unit != null && f["inv_id"].isNullOrBlank()) units.firstOrNull { it.optInt("id") == unit }?.let { pickUnit(it); initial = f.toMap() } }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, title, back, compact = true)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 8.dp)) {
                Banner(banner)
                @Composable fun T(k: String, l: String, kb: KeyboardType = KeyboardType.Text, mod: (String) -> String = { it }) { Input(l, f[k].orEmpty(), { f[k] = mod(it) }, errors[k], keyboard = kb) }
                @Composable fun Dg(k: String, l: String, max: Int) { Input(l, f[k].orEmpty(), { f[k] = it.filter(Char::isDigit).take(max) }, errors[k], keyboard = KeyboardType.Number) }
                @Composable fun Am(k: String, l: String, after: () -> Unit = {}) { Input(l, f[k].orEmpty(), { f[k] = it.filter { c -> c.isDigit() || c == '.' }; after() }, errors[k], keyboard = KeyboardType.Decimal) }
                @Composable fun Dt(k: String, l: String) { DatePick(l, f[k].orEmpty(), errors[k]) { f[k] = it } }

                Card("Unit & booking") {
                    PickerField("Unit", unitLabel, errors["inv_id"]) { sheet = "unit" }
                    Dt("booking_date", "Booking date")
                    T("booking_ref", "Booking reference (blank = automatic)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { T("baf_no", "BAF no.") }; Box(Modifier.weight(1f)) { T("inq_no", "Enquiry no.") } }
                }
                Card("Applicant") {
                    T("applicant_name", "Full name")
                    Dg("applicant_mobile", "Mobile (10 digits)", 10)
                    Dg("applicant_alt_mobile", "Alternate mobile", 10)
                    T("applicant_email", "Email", KeyboardType.Email) { it }
                    Dt("applicant_dob", "Date of birth")
                    Dg("applicant_age", "Age", 3)
                    Label("Gender")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("male", "female", "other").forEach { g -> Pick(pretty(g), f["gender"] == g, InvBlue) { f["gender"] = if (f["gender"] == g) "" else g } } }
                    T("applicant_nationality", "Nationality"); T("occupation", "Occupation"); T("company_name", "Company")
                    T("applicant_pan", "PAN", KeyboardType.Text) { it.uppercase().take(10) }
                    Dg("applicant_aadhaar", "Aadhaar (12 digits)", 12)
                    Input("Permanent address", f["permanent_address"].orEmpty(), { f["permanent_address"] = it }, errors["permanent_address"], singleLine = false)
                    T("city_state_pin", "City / state / PIN")
                }
                Card("Co-applicant", "Optional") {
                    T("co_name", "Name"); T("co_relationship", "Relationship"); Dt("co_dob", "Date of birth"); T("co_occupation", "Occupation")
                    Dg("co_mobile", "Mobile (10 digits)", 10); T("co_email", "Email", KeyboardType.Email) { it }
                    T("co_pan", "PAN", KeyboardType.Text) { it.uppercase().take(10) }; Dg("co_aadhaar", "Aadhaar (12 digits)", 12)
                }
                Card("Property & rate", "Choosing the unit fills these in") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { T("unit_type", "Type") }; Box(Modifier.weight(1f)) { T("tower_wing", "Wing / tower") } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { T("flat_no", "Flat no.") }; Box(Modifier.weight(1f)) { T("floor", "Floor") } }
                    T("facing", "Facing")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { Am("carpet_area", "Carpet area") { recalc() } }; Box(Modifier.weight(1f)) { Am("balcony_area", "Balcony area") {} } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { Am("utility_area", "Utility area") {} }; Box(Modifier.weight(1f)) { Am("total_carpet_area", "Total carpet") {} } }
                    Am("super_builtup", "Super built-up") {}
                    Am("rate_per_sqft", "Rate per sq ft (₹)") { recalc() }
                }
                Card("Cost sheet", "BSP = carpet area × rate · GST, stamp duty, registration and municipal are worked out from it") {
                    CostLines.forEach { (k, l) ->
                        val auto1 = k == "cost_bsp" || auto.has(k)
                        Input(l + if (auto1) "  (auto)" else "", f[k].orEmpty(), { f[k] = it.filter { c -> c.isDigit() || c == '.' }; recalc() }, errors[k], keyboard = KeyboardType.Decimal)
                    }
                    Fact("Total consideration", rupees(total()))
                    if (inWords(total()).isNotBlank()) Text(inWords(total()), color = KitMuted, fontSize = 12.sp)
                }
                Card("Booking payment") {
                    Am("booking_percent", "Booking % (of BSP)") { recalc() }
                    Am("booking_amount", "Booking amount (₹)") { }
                    PickerField("Payment mode", f["payment_mode"]?.ifBlank { null }) { sheet = "mode" }
                    T("bank_name", "Bank"); T("bank_branch", "Branch"); T("transaction_no", "Cheque / transaction no."); Dt("payment_date", "Payment date")
                }
                Card("Payment plan & schedule", "Amounts follow the project's percentages — change any you need") {
                    Label("Plan")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        opts.objs("plans").forEach { p -> Pick(p.optString("label"), f["payment_plan"] == p.optString("key"), InvBlue) { f["payment_plan"] = p.optString("key") } }
                    }
                    Stages.forEach { (k, l) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.weight(1.1f)) { Input("$l (₹)", f["schedule_${k}_amount"].orEmpty(), { f["schedule_${k}_amount"] = it.filter { c -> c.isDigit() || c == '.' }; if (k !in touched) touched.add(k); f["schedule_total"] = money2(Stages.sumOf { (kk, _) -> num(f["schedule_${kk}_amount"]) }) }, errors["schedule_${k}_amount"], keyboard = KeyboardType.Decimal) }
                            Box(Modifier.weight(.9f)) { DatePick("Due", f["schedule_${k}_due"].orEmpty(), errors["schedule_${k}_due"]) { f["schedule_${k}_due"] = it } }
                        }
                    }
                    Fact("Schedule total", rupees(num(f["schedule_total"])))
                    Dt("possession_date", "Possession date")
                }
                Card("Home loan") {
                    val loan = f["loan_required"] == "1"
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { f["loan_required"] = if (loan) "0" else "1" }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(loan, { f["loan_required"] = if (it) "1" else "0" }); Text("Home loan required", color = KitInk, fontSize = 14.sp)
                    }
                    if (loan) {
                        T("loan_bank", "Loan bank"); Am("loan_amount", "Loan amount (₹)") {}
                        Label("Employment")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("salaried", "self_employed", "business").forEach { g -> Pick(pretty(g), f["employment_type"] == g, InvBlue) { f["employment_type"] = if (f["employment_type"] == g) "" else g } } }
                        Am("monthly_income", "Monthly income (₹)") {}
                        T("income_docs", "Income documents provided")
                        val ex = f["existing_loan"] == "1"
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { f["existing_loan"] = if (ex) "0" else "1" }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(ex, { f["existing_loan"] = if (it) "1" else "0" }); Text("Has an existing loan", color = KitInk, fontSize = 14.sp)
                        }
                        if (ex) Am("current_emi", "Current EMI (₹)") {}
                    }
                }
                Card("Documents received") {
                    DocChecks.forEach { (k, l) ->
                        val on = f[k] == "1"
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { f[k] = if (on) "0" else "1" }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { f[k] = if (it) "1" else "0" }); Text(l, color = KitInk, fontSize = 14.sp)
                        }
                    }
                }
                Card("Documents to upload", "Photo, PAN, Aadhaar (PDF or picture, up to 4 MB) and signature (picture, up to 2 MB)") {
                    listOf("applicant_photo" to "Applicant photo", "applicant_pan_doc" to "Applicant PAN card", "applicant_aadhaar_doc" to "Applicant Aadhaar card", "co_pan_doc" to "Co-applicant PAN card", "co_aadhaar_doc" to "Co-applicant Aadhaar card", "signature" to "Applicant signature").forEach { (k, l) ->
                        val shown = picked[k]?.name ?: if (onFile.containsKey(k)) "Uploaded — tap to replace" else null
                        PickerField(l, shown, errors[k]) { pickFor = k; picker.launch(if (k == "applicant_photo" || k == "signature") "image/*" else "*/*") }
                        if (picked.containsKey(k)) Text("Remove chosen file", color = KitMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { picked.remove(k) }.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
                Card("Signatures & witnesses") {
                    T("sig_applicant_name", "Applicant signs as"); Dt("sig_applicant_date", "Signed on")
                    T("sig_co_name", "Co-applicant signs as"); Dt("sig_co_date", "Co-applicant signed on")
                    T("sig_office_name", "Office signatory"); T("sig_office_designation", "Designation")
                    T("witness1_name", "Witness 1 name"); T("witness1_address", "Witness 1 address")
                    T("witness2_name", "Witness 2 name"); T("witness2_address", "Witness 2 address")
                }
                Card("Office use") {
                    T("office_booking_id", "Booking ID"); T("office_unit_code", "Unit code"); T("office_status", "Office status")
                    T("office_sales_exec", "Sales executive"); T("office_sales_mgr", "Sales manager"); T("office_broker", "Broker"); T("office_rera_agent", "RERA agent reg. no."); Am("office_commission", "Commission (₹)") {}
                    T("office_remarks", "Office remarks")
                    Input("Remarks", f["remarks"].orEmpty(), { f["remarks"] = it }, errors["remarks"], singleLine = false)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().background(com.example.gavanhomes.ui.common.KitCard).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (id == null || wasDraft) Text("Save draft", color = KitInk, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(com.example.gavanhomes.ui.common.KitSoft).clickable(enabled = !saving) { save(true) }.padding(vertical = 15.dp))
            Text(if (saving) "Saving…" else if (id != null && !wasDraft) "Save booking" else "Book the unit", color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.4f).clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { save(false) }.padding(vertical = 15.dp))
        }
    }

    if (sheet == "unit") {
        var q by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the unit", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.padding(horizontal = 20.dp))
                com.example.gavanhomes.ui.common.SearchBox(q, { q = it }, "Search wing, flat, floor, type…")
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shown = units.filter { q.isBlank() || it.optString("label").contains(q, true) }
                    if (shown.isEmpty()) Text("No free units match.", color = KitMuted, fontSize = 13.sp)
                    shown.take(200).forEach { u ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).clickable { pickUnit(u); sheet = null }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.optString("label"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(listOfNotNull(u.optDouble("carpet", 0.0).takeIf { it > 0 }?.let { "${money2(it)} sq ft carpet" }, u.str("facing")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                            }
                            if (u.optDouble("price", 0.0) > 0) Text(rupees(u.optDouble("price", 0.0)), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
    if (sheet == "mode") ChoiceSheet("Payment mode", opts.optJSONArray("payment_modes")?.let { a -> (0 until a.length()).map { Choice(a.getString(it), a.getString(it)) } }.orEmpty(), f["payment_mode"], b, allowNone = true, onClose = { sheet = null }) { f["payment_mode"] = it; sheet = null }
}

private fun bookingSubmit(scope: kotlinx.coroutines.CoroutineScope, api: ApiClient, update: String?, create: String, body: JSONObject, files: List<com.example.gavanhomes.data.FormPart>?, ok: String,
                          setSaving: (Boolean) -> Unit, setErrors: (Map<String, String>, String?) -> Unit, done: () -> Unit) {
    setSaving(true)
    scope.launch {
        try {
            withContext(Dispatchers.IO) {
                if (files != null) api.sendForm(if (update != null) "$update/update" else create, files)          // documents go as multipart (a POST either way)
                else if (update != null) api.send("PUT", update, body) else api.send("POST", create, body)
            }
            AppToast.show(ok); done()
        }
        catch (e: ApiException) { setErrors(e.errors, e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message) }
        catch (e: Exception) { setErrors(emptyMap(), e.message ?: "Could not save") }
        setSaving(false)
    }
}
