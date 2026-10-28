package com.example.gavanhomes.ui.projects.form

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                people.filter { it.id in form.memberIds }.forEach { Pill(it.label, b.base.copy(alpha = .16f), b.dark) }
            }
        }

        FormCard("Location & contact") {
            Field(form, "address", "Address")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(form, "city", "City") }
                Box(Modifier.weight(1f)) { Field(form, "state", "State") }
            }
            Field(form, "pincode", "Pincode", KeyboardType.Number)
            Field(form, "phone", "Phone", KeyboardType.Phone)
            Field(form, "email", "Email", KeyboardType.Email)
        }

        FormCard("Schedule") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { DateField(form, "start_date", "Start date", context) }
                Box(Modifier.weight(1f)) { DateField(form, "end_date", "End date", context) }
            }
        }

        // ── developer details ──
        FormCard("Developer details", "Who is building the project, with their documents.") {
            Field(form, "developer_name", "Developer name")
            Field(form, "developer_adhar", "Aadhaar number", KeyboardType.Number)
            FileField("Aadhaar card", form.files.getValue("developer_adhar_proof"), err["developer_adhar_proof"], pick, view)
            Field(form, "developer_pan", "PAN number")
            FileField("PAN card", form.files.getValue("developer_pan_proof"), err["developer_pan_proof"], pick, view)
            Field(form, "developer_gst", "GST number")
            FileField("GST certificate", form.files.getValue("developer_gst_proof"), err["developer_gst_proof"], pick, view)
            Field(form, "developer_address", "Address", singleLine = false)
            FileField("Proof document", form.files.getValue("developer_proof"), err["developer_proof"], pick, view)
            SwitchRow("Has partner(s)?", "Add each partner with their documents.", form.developerPartner) {
                form.developerPartner = it
                if (it && form.partners.isEmpty()) form.partners.add(PartnerState())
            }
        }

        if (form.developerPartner) {
            form.partners.forEachIndexed { i, p ->
                FormCard("Partner #${i + 1}") {
                    Input("Name", p.name, { p.name = it }, err["partners.$i.name"])
                    Input("Aadhaar", p.aadhar, { p.aadhar = it }, err["partners.$i.aadhar"], keyboard = KeyboardType.Number)
                    Input("PAN", p.pan, { p.pan = it }, err["partners.$i.pan"])
                    Input("Mobile", p.mobile, { p.mobile = it }, err["partners.$i.mobile"], keyboard = KeyboardType.Phone)
                    Input("Email", p.email, { p.email = it }, err["partners.$i.email"], keyboard = KeyboardType.Email)
                    FileField("Photo", p.photo, err["partners.$i.photo"], pick, view)
                    FileField("E-signature", p.esign, err["partners.$i.esign"], pick, view)
                    Text(
                        "Remove partner", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { form.partners.removeAt(i) }.padding(vertical = 6.dp, horizontal = 2.dp),
                    )
                }
            }
            Text(
                "+ Add partner", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { form.partners.add(PartnerState()) }.padding(16.dp),
            )
        }

        // ── RERA ──
        FormCard("RERA registration") {
            SwitchRow("RERA registered?", null, form.reraRegistered) { form.reraRegistered = it }
            if (form.reraRegistered) {
                FileField("RERA certificate", form.files.getValue("rera_certificate"), err["rera_certificate"], pick, view)
                Field(form, "rera_number", "RERA number")
                DateField(form, "rera_completion_date", "RERA completion date", context)
                SubHeading("RERA agent")
                Field(form, "rera_agent_aadhaar", "Aadhaar", KeyboardType.Number)
                Field(form, "rera_agent_pan", "PAN")
                Field(form, "rera_agent_email", "Email", KeyboardType.Email)
                Field(form, "rera_agent_mobile", "Mobile", KeyboardType.Phone)
                Field(form, "rera_agent_estg", "ESTG no.")
                Field(form, "rera_agent_address", "Address", singleLine = false)
                FileField("Agent photo", form.files.getValue("rera_agent_photo"), err["rera_agent_photo"], pick, view)
                FileField("Agent RERA certificate", form.files.getValue("rera_agent_certificate"), err["rera_agent_certificate"], pick, view)
            }
        }

        // ── pricing ──
        FormCard("Pricing & cost breakdown", "Amounts in ₹. The total is the sum of these fields.") {
            PriceFields.forEach { (key, label) -> NumberField(form, key, label) }
            val total = form.total()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base.copy(alpha = .14f)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total consideration", fontWeight = FontWeight.SemiBold, color = KitInk, modifier = Modifier.weight(1f))
                Text(total?.let { "₹" + String.format(java.util.Locale.US, "%,.2f", it) } ?: "—", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = b.dark)
            }
        }

        // ── payment schedule ──
        FormCard("Construction-linked payment schedule", "Percent of the sale price due at each milestone. Should add up to 100%.") {
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
