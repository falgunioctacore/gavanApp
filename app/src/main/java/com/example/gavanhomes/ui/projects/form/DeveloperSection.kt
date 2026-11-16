package com.example.gavanhomes.ui.projects.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitSoft
import com.example.gavanhomes.ui.common.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The project's developers and their partners.
 * Developers are entities (Pvt Ltd / LLP / Partnership / Proprietorship): logo, CIN / LLPIN, certificate or deed, RERA, authorised signatory,
 * PAN, GST — and no Aadhaar. Add as many as the project has and mark one as the lead. Partners are persons: Aadhaar (front + back) and PAN
 * cards, a mobile verified by OTP, and — with several developers — the developer each belongs to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevelopersAndPartners(form: ProjectForm, api: ApiClient?, pick: (FileSlot) -> Unit, view: (String) -> Unit) {
    val err = form.errors
    if (form.developers.isEmpty()) form.developers.add(DeveloperState().also { it.isLead = true })

    var open by remember { mutableStateOf<Int?>(null) }
    FormCard("Developers", "Tap a developer to see or change its details.") {
        form.developers.forEachIndexed { i, d ->
            val lead = if (form.developers.none { it.isLead }) i == 0 else d.isLead
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitSoft).clickable { open = i }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoThumb(d.logo, d.name)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(d.name.ifBlank { "Developer ${i + 1} — tap to fill" }, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = if (d.name.isBlank()) KitMuted else KitInk, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (lead && form.developers.size > 1) Text("Lead developer", fontSize = 12.sp, color = KitGood, fontWeight = FontWeight.SemiBold)
                    else if (d.entityType.isNotBlank()) Text(EntityTypes.firstOrNull { it.first == d.entityType }?.second.orEmpty(), fontSize = 12.sp, color = KitMuted)
                }
                Text("›", fontSize = 22.sp, color = KitMuted)
            }
        }
        Text(
            "+ Add developer", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitSoft).bounceClick { form.developers.add(DeveloperState()); open = form.developers.lastIndex }.padding(14.dp),
        )
    }
    open?.let { i -> if (i in form.developers.indices) DeveloperSheet(form, i, pick, view) { open = null } else open = null }

    FormCard("Partners") {
        SwitchRow("Has partner(s)?", "Each partner with their documents — and, with several developers, the developer they belong to.", form.developerPartner) {
            form.developerPartner = it
            if (it && form.partners.isEmpty()) form.partners.add(PartnerState())
        }
    }
    var openPartner by remember { mutableStateOf<Int?>(null) }
    if (form.developerPartner) {
        FormCard("Partner list", "Tap a partner to see or change the details.") {
            form.partners.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitSoft).clickable { openPartner = i }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LogoThumb(p.photo, p.name)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(p.name.ifBlank { "Partner ${i + 1} — tap to fill" }, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = if (p.name.isBlank()) KitMuted else KitInk, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        val sub = listOfNotNull(
                            form.developers.getOrNull(p.developer ?: -1)?.name?.takeIf { it.isNotBlank() },
                            p.mobile.takeIf { it.isNotBlank() }?.let { it + if (p.mobileVerified) " ✓" else "" },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = if (p.mobileVerified) KitGood else KitMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Text("›", fontSize = 22.sp, color = KitMuted)
                }
            }
            Text(
                "+ Add partner", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitSoft).bounceClick { form.partners.add(PartnerState()); openPartner = form.partners.lastIndex }.padding(14.dp),
            )
        }
        openPartner?.let { i -> if (i in form.partners.indices) PartnerSheet(form, i, api, pick, view) { openPartner = null } else openPartner = null }
    }
}

/** One partner's full details in a popup, like a developer's. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PartnerSheet(form: ProjectForm, index: Int, api: ApiClient?, pick: (FileSlot) -> Unit, view: (String) -> Unit, onClose: () -> Unit) {
    val err = form.errors
    val i = index
    val p = form.partners[index]
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = com.example.gavanhomes.ui.common.KitPage) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Partner ${index + 1}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitInk).clickable { onClose() }.padding(horizontal = 18.dp, vertical = 10.dp))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Input("Name", p.name, { p.name = it }, err["partners.$i.name"])
                if (form.developers.size > 1) {
                    Text("DEVELOPER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        form.developers.forEachIndexed { di, d ->
                            Text(d.name.ifBlank { "Developer ${di + 1}" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (p.developer == di) Color.White else KitInk, maxLines = 1,
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (p.developer == di) KitInk else KitSoft).clickable { p.developer = if (p.developer == di) null else di }.padding(horizontal = 14.dp, vertical = 9.dp))
                        }
                    }
                }
                Input("Aadhaar number (12 digits)", p.aadhar, { p.aadhar = it.filter { c -> c.isDigit() || c == ' ' }.take(14) }, err["partners.$i.aadhar"] ?: p.problems()["aadhar"].takeIf { p.aadhar.isNotBlank() }, keyboard = KeyboardType.Number)
                FileField("Aadhaar card — front", p.aadharFront, err["partners.$i.aadhar_front"], pick, view)
                FileField("Aadhaar card — back", p.aadharBack, err["partners.$i.aadhar_back"], pick, view)
                Input("PAN (ABCDE1234F)", p.pan, { p.pan = it.uppercase().take(10) }, err["partners.$i.pan"] ?: p.problems()["pan"].takeIf { p.pan.length == 10 })
                FileField("PAN card", p.panDoc, err["partners.$i.pan_doc"], pick, view)
                MobileField(p, api, err["partners.$i.mobile"])
                Input("Email", p.email, { p.email = it }, err["partners.$i.email"], keyboard = KeyboardType.Email)
                FileField("Photo", p.photo, err["partners.$i.photo"], pick, view)
                FileField("E-signature", p.esign, err["partners.$i.esign"], pick, view)
                Text("Aadhaar and PAN documents are visible to admins only.", fontSize = 11.sp, color = KitMuted)
                var askRemovePartner by remember { mutableStateOf(false) }
                Text(
                    "Remove partner", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { askRemovePartner = true }.padding(vertical = 6.dp, horizontal = 2.dp),
                )
                if (askRemovePartner) com.example.gavanhomes.ui.common.SwalConfirm(
                    title = "Remove " + p.name.ifBlank { "this partner" } + "?", text = "Their details and documents are removed when you save the project.", confirmText = "Yes, remove",
                    onCancel = { askRemovePartner = false }, onConfirm = { askRemovePartner = false; form.partners.removeAt(i); onClose() },
                )

            }
        }
    }
}

/** The partner's mobile with OTP verification: a code is sent by SMS, entering it shows a Verified tag; changing the number needs a new code. */
@Composable
private fun MobileField(p: PartnerState, api: ApiClient?, error: String?) {
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }       // the code box is open
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val digits = p.mobile.filter(Char::isDigit).takeLast(10)

    Input("Mobile", p.mobile, { v -> p.mobile = v.filter(Char::isDigit).take(10); if (p.mobile.length != 10) asking = false }, error, keyboard = KeyboardType.Phone)
    if (p.mobileVerified) {
        Text("✓ Verified", color = KitGood, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(KitGood.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 6.dp))
    } else if (digits.length == 10 && api != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("sms" to "OTP by SMS", "whatsapp" to "OTP by WhatsApp").forEach { (channel, label) ->
                Text(
                    if (busy) "Sending…" else if (asking) "Resend · " + label.removePrefix("OTP by ") else label, color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitSoft).clickable(enabled = !busy) {
                        busy = true
                        scope.launch {
                            try {
                                val r = withContext(Dispatchers.IO) { api.send("POST", "/partner-otp/send", JSONObject().put("mobile", digits).put("channel", channel)) }
                                asking = true; code = ""
                                AppToast.show(r.optString("message", "Code sent"))
                            } catch (e: Exception) { AppToast.show(e.message ?: "Could not send the code") }
                            busy = false
                        }
                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        if (asking) {
            Input("6-digit code", code, { code = it.filter(Char::isDigit).take(6) }, keyboard = KeyboardType.Number)
            Text(
                if (busy) "Checking…" else "Verify", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (code.length == 6) KitInk else Color(0xFFC9C7C2)).clickable(enabled = code.length == 6 && !busy) {
                    busy = true
                    scope.launch {
                        try {
                            val r = withContext(Dispatchers.IO) { api.send("POST", "/partner-otp/verify", JSONObject().put("mobile", digits).put("code", code)) }
                            p.verifiedMobile = digits; p.mobileToken = r.optJSONObject("data")?.optString("token").orEmpty(); asking = false
                            AppToast.show("Mobile verified")
                        } catch (e: Exception) { AppToast.show(e.message ?: "That code is not correct") }
                        busy = false
                    }
                }.padding(horizontal = 20.dp, vertical = 11.dp),
            )
        }
    }
}

/** The developer's full details in a popup: opens from the list, edits live, closes with Done (or when the developer is removed). */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperSheet(form: ProjectForm, index: Int, pick: (FileSlot) -> Unit, view: (String) -> Unit, onClose: () -> Unit) {
    val err = form.errors
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = com.example.gavanhomes.ui.common.KitPage) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Developer ${index + 1}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitInk).clickable { onClose() }.padding(horizontal = 18.dp, vertical = 10.dp))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    run {
        val d = form.developers[index]; val i = index
        val lead = if (form.developers.none { it.isLead }) i == 0 else d.isLead
        FormCard(if (form.developers.size > 1) "Developer #${i + 1}" + if (lead) " · Lead" else "" else "Developer details", "An entity — company, LLP or firm — with its documents.") {
            Input("Developer name", d.name, { d.name = it }, err["developers.$i.name"])
            FileField("Logo", d.logo, err["developers.$i.logo"], pick, view)
            if (form.developers.size > 1) SwitchRow("Lead developer", "Decides the logo order and the billing entity.", lead) { on ->
                if (on) form.developers.forEach { it.isLead = false }
                d.isLead = on
            }
            Text("ENTITY TYPE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EntityTypes.forEach { (k, label) ->
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (d.entityType == k) Color.White else KitInk,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (d.entityType == k) KitInk else KitSoft).clickable { d.entityType = if (d.entityType == k) "" else k }.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
            Input(if (d.entityType == "llp") "LLPIN" else "CIN / LLPIN", d.cin, { d.cin = it.uppercase() }, err["developers.$i.cin_llpin"])
            FileField(if (d.entityType == "partnership" || d.entityType == "proprietorship") "Partnership deed / registration" else "Incorporation certificate", d.incorporationDoc, err["developers.$i.incorporation_doc"], pick, view)
            Input("RERA number", d.reraNumber, { d.reraNumber = it }, err["developers.$i.rera_number"])
            FileField("RERA certificate", d.reraDoc, err["developers.$i.rera_doc"], pick, view)
            SubHeading("Authorised signatory")
            Input("Signatory name", d.signatoryName, { d.signatoryName = it }, err["developers.$i.signatory_name"])
            Input("Signatory mobile", d.signatoryMobile, { d.signatoryMobile = it.filter(Char::isDigit).take(10) }, err["developers.$i.signatory_mobile"], keyboard = KeyboardType.Phone)
            Input("PAN number", d.pan, { d.pan = it.uppercase().take(10) }, err["developers.$i.pan"])
            FileField("PAN card", d.panDoc, err["developers.$i.pan_doc"], pick, view)
            Input("GST number", d.gst, { d.gst = it.uppercase() }, err["developers.$i.gst"])
            FileField("GST certificate", d.gstDoc, err["developers.$i.gst_doc"], pick, view)
            Input("Address", d.address, { d.address = it }, err["developers.$i.address"], singleLine = false)
            FileField("Proof document", d.proofDoc, err["developers.$i.proof_doc"], pick, view)
            var askRemove by remember { mutableStateOf(false) }
            if (form.developers.size > 1) Text(
                "Remove developer", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { askRemove = true }.padding(vertical = 6.dp, horizontal = 2.dp),
            )
            if (askRemove) com.example.gavanhomes.ui.common.SwalConfirm(
                title = "Remove " + d.name.ifBlank { "this developer" } + "?", text = "Its details and documents are removed when you save the project. Its partners stay, with no developer chosen.", confirmText = "Yes, remove",
                onCancel = { askRemove = false },
                onConfirm = {
                    askRemove = false
                    val gone = form.developers.removeAt(i)
                    // partners of that developer lose the link; those after it move up one place
                    form.partners.forEach { p -> p.developer = when { p.developer == null -> null; p.developer == i -> null; p.developer!! > i -> p.developer!! - 1; else -> p.developer } }
                    if (gone.isLead) form.developers.firstOrNull()?.isLead = true
                    onClose()
                },
            )
        }
    }

            }
        }
    }
}

/** A small round logo: the picked image, the stored one (downloaded), or the developer's first letter. */
@Composable
private fun LogoThumb(slot: FileSlot, name: String) {
    val picked = slot.picked
    val stored = slot.existing
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, picked, stored) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    picked != null -> android.graphics.BitmapFactory.decodeByteArray(picked.bytes, 0, picked.bytes.size)
                    stored != null -> java.net.URL(com.example.gavanhomes.data.ApiConfig.BASE_URL + "/storage/" + stored).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                    else -> null
                }
            }.getOrNull()
        }
    }
    androidx.compose.foundation.layout.Box(Modifier.size(46.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.size(46.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Text(name.trim().take(1).uppercase().ifEmpty { "+" }, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitMuted)
    }
}
