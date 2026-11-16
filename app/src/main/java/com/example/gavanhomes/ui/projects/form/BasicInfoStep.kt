package com.example.gavanhomes.ui.projects.form

import com.example.gavanhomes.ui.common.bounceClick
import android.content.Context
import com.example.gavanhomes.ui.common.accentText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.Pill

/** Step 1 of the website's form: Basic Information — project, developer & partners, RERA, pricing, payment schedule. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BasicInfoStep(
    form: ProjectForm, statuses: List<Choice>, people: List<Choice>, b: Branding, context: Context,
    pick: (FileSlot) -> Unit, view: (String) -> Unit, openPicker: (String) -> Unit,
    api: com.example.gavanhomes.data.ApiClient? = null,
) {
    val err = form.errors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // ── project details ──
        FormCard("Project details") {
            Field(form, "name", "Project name *")
            Field(form, "code", "Project code (auto-generated if blank)")
            PickerField("Status *", statuses.firstOrNull { it.id.toString() == form.get("status_id") }?.label, err["status_id"]) { openPicker("status") }
            PickerField("Project types", form.types.sorted().joinToString(", ").ifEmpty { null }, err["type"]) { openPicker("types") }
            Field(form, "description", "Description", singleLine = false)
            SwitchRow("Active", "Inactive projects are hidden from new work.", form.active) { form.active = it }
        }

        FormCard("Team") {
            val leaderId = form.get("leader_id").toIntOrNull()
            PickerField("Team leader", people.firstOrNull { it.id == leaderId }?.label, err["leader_id"]) { openPicker("leader") }
            PickerField("Members", if (form.memberIds.isEmpty()) null else "${form.memberIds.size} selected", err["member_ids"]) { openPicker("members") }
            if (form.memberIds.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                people.filter { it.id in form.memberIds }.forEach { Pill(it.label, b.base.copy(alpha = .16f), b.accentText) }
            }
        }

        FormCard("Location & contact") {
            Field(form, "address", "Address")
            LocationFields(form)
            Field(form, "phone", "Phone", KeyboardType.Phone)
            Field(form, "email", "Email", KeyboardType.Email)
        }

        FormCard("Schedule") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { DateField(form, "start_date", "Start date", context) }
                Box(Modifier.weight(1f)) { DateField(form, "end_date", "End date", context) }
            }
        }

        // ── developers & partners ──
        DevelopersAndPartners(form, api, pick, view)

        // ── RERA ──
        var reraSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        FormCard("RERA registration") {
            SwitchRow("RERA registered?", null, form.reraRegistered) { form.reraRegistered = it }
            if (form.reraRegistered) {
                val proj = listOfNotNull(form.get("rera_number").takeIf { it.isNotBlank() }, if (form.files.getValue("rera_certificate").hasFile) "certificate added" else null).joinToString(" · ")
                val agent = listOfNotNull(form.get("rera_agent_mobile").takeIf { it.isNotBlank() }, form.get("rera_agent_email").takeIf { it.isNotBlank() }).joinToString(" · ")
                listOf(Triple("project", "Project RERA", proj), Triple("agent", "RERA agent", agent)).forEach { (key, title, sub) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).clickable { reraSheet = key }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KitInk)
                            Text(sub.ifBlank { "Tap to fill in" }, fontSize = 12.sp, color = KitMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Text("›", fontSize = 22.sp, color = KitMuted)
                    }
                }
            }
        }
        reraSheet?.let { kind -> ReraSheet(kind, form, context, pick, view) { reraSheet = null } }

        // ── pricing ──
        FormCard("Pricing & cost breakdown", "Amounts in ₹. The total is the sum of these fields.") {
            PriceFields.forEach { (key, label) ->
                val pct = form.chargeMode(key) == "percent"
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        Input(label.replace(" (applicable rate)", "") + if (pct) " (%)" else " (₹)", form.get(key), { form.set(key, it) }, form.errors[key], keyboard = KeyboardType.Decimal)
                    }
                    // % / ₹ toggle: each charge is either a share of the deal value or a fixed amount
                    Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(com.example.gavanhomes.ui.common.KitSoft)) {
                        listOf("percent" to "%", "amount" to "₹").forEach { (m, t) ->
                            val on = (if (pct) "percent" else "amount") == m
                            Text(
                                t, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (on) Color.White else KitMuted,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) KitInk else Color.Transparent).clickable { if (m == (if (key in PercentCharges) "percent" else "amount")) form.chargeModes.remove(key) else form.chargeModes[key] = m }.padding(horizontal = 14.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
                if (key == "gst") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Rate:", fontSize = 12.sp, color = KitMuted, modifier = Modifier.padding(top = 7.dp))
                    listOf("1", "5", "12", "18").forEach { r ->
                        Text("$r%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (form.get("gst") == r) Color.White else KitInk,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (form.get("gst") == r) KitInk else com.example.gavanhomes.ui.common.KitSoft).clickable { form.set("gst", r) }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    Text("confirm with your CA", fontSize = 11.sp, color = KitMuted, modifier = Modifier.padding(top = 8.dp))
                }
            }
            val total = form.total()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base.copy(alpha = .14f)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Fixed charges (₹ items)", fontWeight = FontWeight.SemiBold, color = KitInk, modifier = Modifier.weight(1f))
                Text(total?.let { "₹" + String.format(java.util.Locale.US, "%,.2f", it) } ?: "—", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = b.accentText)
            }
        }

        CostSheetPreview(form, b)

        // ── payment schedule ──
        if (form.hasPlot) PlotScheduleCard(form, context, b)
        if (!form.plotOnly) FormCard("Construction-linked payment schedule", "Percent of the sale price due at each milestone. Should add up to 100%.") {
            PayFields.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (key, label, _) -> Box(Modifier.weight(1f)) { NumberField(form, key, label) } }
                    if (pair.size == 1) Box(Modifier.weight(1f)) {}
                }
            }
            val sum = form.payTotal()
            val ok = kotlin.math.abs(sum - 100.0) < 0.005
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background((if (ok) KitGood else KitDanger).copy(alpha = .12f)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", fontWeight = FontWeight.SemiBold, color = KitInk, modifier = Modifier.weight(1f))
                Text(
                    String.format(java.util.Locale.US, "%.2f%%", sum) + if (ok) "  ✓" else "  (not 100%)",
                    fontWeight = FontWeight.ExtraBold, color = if (ok) KitGood else KitDanger,
                )
            }
        }
    }
}

/** What a deal of a given value costs under these charges: base price + each % charge (value × %) + each fixed charge = total. */
@Composable
private fun CostSheetPreview(form: ProjectForm, b: Branding) {
    var deal by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val base = deal.replace(",", "").toDoubleOrNull()
    fun money(v: Double) = "₹" + String.format(java.util.Locale.US, "%,.0f", v)
    FormCard("Cost sheet check", "Type a deal value to see the amount each percentage charge comes to. It is only a preview — nothing is saved from it.") {
        Input("Deal value / base price (₹)", deal, { deal = it }, keyboard = KeyboardType.Decimal)
        if (base != null && base > 0) {
            val lines = PriceFields.mapNotNull { (key, label) ->
                val v = form.get(key).toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val pct = form.chargeMode(key) == "percent"
                Triple(label.replace(" (applicable rate)", "") + if (pct) " · " + String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.') + "%" else "", if (pct) base * v / 100.0 else v, key)
            }
            val total = base + lines.sumOf { it.second }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { Text("Base price", color = KitInk, modifier = Modifier.weight(1f)); Text(money(base), color = KitInk, fontWeight = FontWeight.SemiBold) }
                lines.forEach { (title, amount, _) ->
                    Row {
                        Text(title, color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(money(amount), color = KitInk, fontSize = 13.sp)
                    }
                }
                Row(Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(b.base.copy(alpha = .14f)).padding(12.dp)) {
                    Text("Total", fontWeight = FontWeight.ExtraBold, color = KitInk, modifier = Modifier.weight(1f))
                    Text(money(total), fontWeight = FontWeight.ExtraBold, color = b.accentText)
                }
            }
        }
    }
}

/**
 * A plot project's payment schedule: milestones with a name, an amount (% of the plot price or fixed ₹) and a due rule
 * (a fixed date, X days / months after booking, or monthly EMIs). Ready templates fill it in; it can be edited per customer at booking.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlotScheduleCard(form: ProjectForm, context: Context, b: Branding) {
    val err = form.errors["payment_milestones"]
    FormCard("Payment schedule (plots)", "Plots have no construction stages. Add milestones below — the percentages must add up to 100%. The schedule can be changed for each customer at booking.") {
        Text("TEMPLATES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MilestoneTemplates.forEach { (title, rows) ->
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = KitInk,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(com.example.gavanhomes.ui.common.KitSoft).clickable { form.milestones.clear(); rows.forEach { init -> form.milestones.add(Milestone().also(init)) } }.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
        form.milestones.forEachIndexed { i, m ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Milestone ${i + 1}", fontWeight = FontWeight.ExtraBold, color = KitInk, modifier = Modifier.weight(1f))
                    Text("Remove", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { form.milestones.removeAt(i) }.padding(6.dp))
                }
                Input("Name (e.g. Booking, Agreement, Registry)", m.name, { m.name = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) { Input(if (m.mode == "percent") "Amount (%)" else "Amount (₹)", m.value, { m.value = it }, keyboard = KeyboardType.Decimal) }
                    Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(com.example.gavanhomes.ui.common.KitCard)) {
                        listOf("percent" to "%", "amount" to "₹").forEach { (mode, t) ->
                            Text(t, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (m.mode == mode) Color.White else KitMuted,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (m.mode == mode) KitInk else Color.Transparent).clickable { m.mode = mode }.padding(horizontal = 14.dp, vertical = 14.dp))
                        }
                    }
                }
                Text("DUE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("date" to "Fixed date", "days" to "Days after booking", "months" to "Months after booking", "emi" to "Monthly EMIs").forEach { (k, t) ->
                        Text(t, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (m.dueType == k) Color.White else KitInk,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (m.dueType == k) KitInk else com.example.gavanhomes.ui.common.KitCard).clickable { if (m.dueType != k) { m.dueType = k; m.dueValue = "" } }.padding(horizontal = 11.dp, vertical = 8.dp))
                    }
                }
                if (m.dueType == "date") {
                    val current = m.dueValue.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() }
                    PickerField("Due date", current?.toString()) {
                        val d = current ?: java.time.LocalDate.now()
                        android.app.DatePickerDialog(context, { _, y, mo, day -> m.dueValue = java.time.LocalDate.of(y, mo + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }
                } else Input(when (m.dueType) { "days" -> "Days after booking (0 = on booking)"; "months" -> "Months after booking"; else -> "Number of monthly EMIs" }, m.dueValue, { m.dueValue = it.filter { c -> c.isDigit() } }, keyboard = KeyboardType.Number)
            }
        }
        Text("+ Add milestone", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).bounceClick { form.milestones.add(Milestone(null).also { it.name = if (form.milestones.isEmpty()) "Booking" else "" }) }.padding(14.dp))

        val pctRows = form.milestones.filter { it.mode == "percent" }
        val sum = pctRows.sumOf { it.value.toDoubleOrNull() ?: 0.0 }
        val allPct = form.milestones.isNotEmpty() && pctRows.size == form.milestones.size
        if (form.milestones.isNotEmpty()) {
            val ok = !allPct || kotlin.math.abs(sum - 100.0) < 0.005
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background((if (ok) KitGood else KitDanger).copy(alpha = .12f)).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Total", fontWeight = FontWeight.SemiBold, color = KitInk, modifier = Modifier.weight(1f))
                Text(if (allPct) String.format(java.util.Locale.US, "%.2f%%", sum) + if (ok) "  ✓" else "  (not 100%)" else "mixed % and ₹", fontWeight = FontWeight.ExtraBold, color = if (ok) KitGood else KitDanger)
            }
        }
        if (err != null) Text(err, color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * State first, then City (the state's cities — or type another), then Pincode: looked up from the city, one tap to fill when the
 * city has several, and still an ordinary text box that can be edited.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationFields(form: ProjectForm) {
    var sheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var customCity by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var pins by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<IndiaPlaces.Pin>>(emptyList()) }
    var looking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val state = form.get("state"); val city = form.get("city")
    val cities = IndiaPlaces.citiesOf(state)

    androidx.compose.runtime.LaunchedEffect(city, state) {
        pins = emptyList()
        if (city.isBlank()) return@LaunchedEffect
        looking = true
        pins = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IndiaPlaces.pincodes(city, state.ifBlank { null }) }
        looking = false
        if (pins.size == 1 && form.get("pincode").isBlank()) form.set("pincode", pins[0].code)   // a single pincode fills itself in
    }

    // typing a full pincode fills in the state and city
    val typedPin = form.get("pincode")
    var pinNote by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(typedPin) {
        pinNote = null
        if (typedPin.length != 6 || !typedPin.all(Char::isDigit)) return@LaunchedEffect
        pinNote = "Looking up the pincode…"
        val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IndiaPlaces.place(typedPin) }
        if (form.get("pincode") != typedPin) return@LaunchedEffect
        if (found == null) { pinNote = "No place found for $typedPin — check the pincode, or choose the State and City."; return@LaunchedEffect }
        pinNote = "✓ ${found.second}, ${found.first}"
        if (!found.first.equals(form.get("state"), true)) form.set("state", found.first)
        if (form.get("city").isBlank()) form.set("city", found.second.replaceFirstChar { it.uppercase() })
    }
    PickerField("State", state.ifBlank { null }, form.errors["state"]) { sheet = "state" }
    PickerField("City", city.ifBlank { null }, form.errors["city"]) { if (state.isBlank()) com.example.gavanhomes.ui.common.AppToast.show("Choose the state first") else sheet = "city" }
    if (customCity || cities.isEmpty() && state.isNotBlank() || (city.isNotBlank() && cities.none { it.equals(city, true) } && state.isNotBlank())) {
        Field(form, "city", "City (type it)")
    }
    Field(form, "pincode", "Pincode", KeyboardType.Number)
    pinNote?.let { Text(it, fontSize = 12.sp, color = if (it.startsWith("No place")) KitDanger else KitMuted) }
    if (looking) Text("Looking up pincodes…", fontSize = 12.sp, color = KitMuted)
    else if (pins.isNotEmpty()) {
        Text(if (pins.size == 1) "Pincode of $city" else "$city has ${pins.size} pincodes — tap one", fontSize = 12.sp, color = KitMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            pins.take(16).forEach { p ->
                val on = form.get("pincode") == p.code
                Text(p.code + " · " + p.area, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) KitInk else com.example.gavanhomes.ui.common.KitSoft).clickable { form.set("pincode", p.code) }.padding(horizontal = 11.dp, vertical = 8.dp))
            }
        }
    }

    when (sheet) {
        "state" -> ChoiceSheet("State", IndiaPlaces.states.mapIndexed { i, n -> Choice(i, n) }, setOf(IndiaPlaces.states.indexOfFirst { it.equals(state, true) }), multi = false, onDismiss = { sheet = null }) { ids ->
            val picked = ids.firstOrNull()?.let { IndiaPlaces.states.getOrNull(it) }
            if (picked != null && !picked.equals(state, true)) { form.set("state", picked); form.set("city", ""); form.set("pincode", ""); customCity = false }
        }
        "city" -> {
            val list = cities + "Other…"
            ChoiceSheet("City in $state", list.mapIndexed { i, n -> Choice(i, n) }, setOf(list.indexOfFirst { it.equals(city, true) }), multi = false, onDismiss = { sheet = null }) { ids ->
                val picked = ids.firstOrNull()?.let { list.getOrNull(it) }
                if (picked == "Other…") { customCity = true; form.set("city", ""); form.set("pincode", "") }
                else if (picked != null && !picked.equals(city, true)) { customCity = false; form.set("city", picked); form.set("pincode", "") }
            }
        }
    }
}

/** The project's RERA details, or the RERA agent's, in a popup. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReraSheet(kind: String, form: ProjectForm, context: Context, pick: (FileSlot) -> Unit, view: (String) -> Unit, onClose: () -> Unit) {
    val err = form.errors
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = com.example.gavanhomes.ui.common.KitPage) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (kind == "agent") "RERA agent" else "Project RERA", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitInk).clickable { onClose() }.padding(horizontal = 18.dp, vertical = 10.dp))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (kind == "agent") {
                    Field(form, "rera_agent_aadhaar", "Aadhaar", KeyboardType.Number)
                    Field(form, "rera_agent_pan", "PAN")
                    Field(form, "rera_agent_email", "Email", KeyboardType.Email)
                    Field(form, "rera_agent_mobile", "Mobile", KeyboardType.Phone)
                    Field(form, "rera_agent_estg", "ESTG no.")
                    Field(form, "rera_agent_address", "Address", singleLine = false)
                    FileField("Agent photo", form.files.getValue("rera_agent_photo"), err["rera_agent_photo"], pick, view)
                    FileField("Agent RERA certificate", form.files.getValue("rera_agent_certificate"), err["rera_agent_certificate"], pick, view)
                } else {
                    FileField("RERA certificate", form.files.getValue("rera_certificate"), err["rera_certificate"], pick, view)
                    Field(form, "rera_number", "RERA number")
                    DateField(form, "rera_completion_date", "RERA completion date", context)
                }
            }
        }
    }
}
