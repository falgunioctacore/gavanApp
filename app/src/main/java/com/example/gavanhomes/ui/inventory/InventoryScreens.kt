package com.example.gavanhomes.ui.inventory

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.gavanhomes.ui.common.ScrollTopButton
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.whenLabel
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.Label
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** The hero and a spinner, or an error with Retry, while a screen's data is on its way. */
@Composable
internal fun Waiting(b: Branding, title: String, error: String?, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, title, onBack, compact = true)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(error, color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
internal fun SaveBar(label: String, saving: Boolean, b: Branding, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)) {
        Text(
            if (saving) "Saving…" else label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { onClick() }.padding(vertical = 15.dp),
        )
    }
}

@Composable
internal fun Banner(text: String?) {
    text?.let { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).padding(12.dp)) }
}

// ───────────────────────────── material details ─────────────────────────────

/** The ledger's movement types as the website names them. */
private fun txnLabel(t: String): Pair<String, Color> = when (t) {
    "IN" -> "Received" to KitGood
    "OUT" -> "Issued" to InvPurple
    "ADJ_IN" -> "Added" to KitGood
    "ADJ_OUT" -> "Used" to InvAmber
    "TRF" -> "Transfer" to InvBlue
    "EST" -> "Estimate" to KitMuted
    else -> t to KitMuted
}

/**
 * A material: what is on hand, its value and alert level, its details, the Stock Adjustment (add / use stock) and its stock ledger
 * for this project. Data: GET /inventory-center/materials/{id} · POST …/materials/{id}/adjust · DELETE /inventory-center/materials/{id}.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialDetailScreen(api: ApiClient, b: Branding, uuid: String, id: Int, version: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var m by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var adjust by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(id, version, attempt) {
        error = null
        try { m = withContext(Dispatchers.IO) { api.detail("/inventory-center/materials/$id?project=$uuid") } } catch (e: Exception) { error = e.message ?: "Could not load the material" }
    }
    LaunchedEffect(uuid) { stores = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/inventory-center/options").objs("stores") } }.getOrDefault(emptyList()) }

    val x = m ?: return Waiting(b, "Material", error, onBack) { attempt++ }
    val can = x.optJSONObject("can") ?: JSONObject()
    val own = x.optInt("project_id") > 0
    val (label, c) = stockStatus(x.str("status"))
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize().background(KitPage)) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize().verticalScroll(scroll)) {
            HeroBar(b, "Material", onBack, actions = {
                if (can.optBoolean("edit") && own) { HeroButton("✎") { onEdit(id) }; Spacer(Modifier.size(8.dp)) }
                if (can.optBoolean("delete") && own) DeleteButton { askDelete = true }
            }) {
                Column {
                    Text(x.optString("name"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOfNotNull(x.str("code"), x.str("category")).joinToString(" · ").ifEmpty { "No code" }, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(label, if (own) null else "Catalogue material", if (x.optInt("active", 1) == 0) "Inactive" else null).toTypedArray())
                }
            }

            Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("${qty(x.optDouble("on_hand", 0.0))} ${x.optString("unit")}", "On hand", Modifier.weight(1f), c)
                Tile(shortRupees(x.optDouble("value", 0.0)), "Value", Modifier.weight(1f), KitGood)
                Tile(qty(x.optDouble("min_stock", 0.0)), "Min stock", Modifier.weight(1f), InvAmber)
            }

            if (can.optBoolean("edit")) Text(
                "±  Adjust stock", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitInk).clickable { adjust = true }.padding(vertical = 14.dp),
            )

            val history = x.objs("history")
            com.example.gavanhomes.ui.common.PillTabs(listOf(com.example.gavanhomes.ui.common.PillTab("Details"), com.example.gavanhomes.ui.common.PillTab("Stock ledger", history.size)), tab, b.base) { tab = it }

            if (tab == 0) Card("Details") {
                Fact("Unit", listOfNotNull(x.str("unit_name"), x.str("unit")?.let { "($it)" }).joinToString(" ").ifBlank { null })
                Fact("Category", x.str("category")); Fact("Brand", x.str("brand_name")); Fact("HSN code", x.str("hsn_code")); Fact("Tax rate", x.str("tax_rate"))
                x.optDouble("rate", 0.0).takeIf { it > 0 }?.let { Fact("Sale rate", rupees(it)) }
                x.optDouble("purchase_price", 0.0).takeIf { it > 0 }?.let { Fact("Purchase price", rupees(it)) }
                x.optDouble("opening_stock", 0.0).takeIf { it > 0 }?.let { Fact("Opening stock", qty(it) + (prettyDay(x.str("stock_as_of_date"))?.let { d -> " on $d" } ?: "")) }
                x.optDouble("expected", 0.0).takeIf { it > 0 }?.let { Fact("Estimated need", qty(it)) }
                Fact("Location", x.str("location")); Fact("Specifications", x.str("specifications"))
            }

            if (tab == 1) Card("Stock ledger", "Every movement of this material here, newest first") {
                if (history.isEmpty()) Text("No movements yet.", color = KitMuted, fontSize = 13.sp)
                history.forEach { h ->
                    val (tl, tc) = txnLabel(h.optString("type"))
                    val inQ = h.optDouble("in", 0.0); val outQ = h.optDouble("out", 0.0)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Chip(tl, tc); h.str("store")?.let { Chip(it, KitMuted) } }
                            Text(listOfNotNull(whenLabel(h.str("date")), h.str("by")).joinToString(" · "), color = KitMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            h.str("remarks")?.let { Text(it, color = KitInk, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            when {
                                inQ > 0 -> Text("+" + qty(inQ), color = KitGood, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                outQ > 0 -> Text("−" + qty(outQ), color = KitDanger, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                h.optDouble("estimate", 0.0) > 0 -> Text("~" + qty(h.optDouble("estimate", 0.0)), color = KitMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text("Bal ${qty(h.optDouble("balance", 0.0))}", color = KitMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        ScrollTopButton(scroll)
    }

    if (adjust) AdjustSheet(api, b, uuid, x, stores, onClose = { adjust = false }) { adjust = false; attempt++; onChanged() }
    if (askDelete) SwalConfirm(
        title = "Delete this material?", text = "Only a material with no stock and no history can be deleted.", confirmText = "Yes, delete it",
        onConfirm = {
            askDelete = false
            scope.launch {
                try { withContext(Dispatchers.IO) { api.delete("/inventory-center/materials/$id") }; Toast.makeText(context, "Material deleted", Toast.LENGTH_SHORT).show(); onChanged(); onBack() }
                catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
            }
        },
        onCancel = { askDelete = false },
    )
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier, tint: Color) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Text(value, color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = KitMuted, fontSize = 12.sp)
    }
}

/** Stock Adjustment: use or add stock, the quantity, which store, and why (website: materials/{id}/update-stock). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdjustSheet(api: ApiClient, b: Branding, uuid: String, m: JSONObject, stores: List<JSONObject>, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf("used") }
    var amount by remember { mutableStateOf("") }
    var store by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val onHand = m.optDouble("on_hand", 0.0)
    fun save() {
        val q = amount.toDoubleOrNull()
        error = when {
            q == null || q <= 0 -> "Enter a quantity greater than 0."
            action == "used" && q > onHand + 0.0001 -> "Only ${qty(onHand)} ${m.optString("unit")} on hand."
            else -> null
        }
        if (error != null) return
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.send("POST", "/projects/$uuid/inventory-center/materials/${m.optInt("id")}/adjust", JSONObject().put("stock_action", action).put("qty", q)
                        .put("comment", comment.trim().ifBlank { JSONObject.NULL }).put("store_id", if (store > 0) store else JSONObject.NULL))
                }
                Toast.makeText(context, "Stock updated", Toast.LENGTH_SHORT).show(); onDone()
            } catch (e: ApiException) { error = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message } catch (e: Exception) { error = e.message ?: "Could not update" }
            saving = false
        }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Adjust stock", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Text("${m.optString("name")} · ${qty(onHand)} ${m.optString("unit")} on hand", color = KitMuted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pick("−  Use stock", action == "used", InvAmber) { action = "used" }
                Pick("+  Add stock", action == "add", KitGood) { action = "add" }
            }
            Input("Quantity (${m.optString("unit")})", amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal)
            if (stores.isNotEmpty()) {
                Label("Store")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pick("None", store == 0, KitMuted) { store = 0 }
                    stores.forEach { s -> Pick(s.optString("name"), store == s.optInt("id"), b.base) { store = s.optInt("id") } }
                }
            }
            Input("Why is it being adjusted?", comment, { comment = it }, singleLine = false)
            Banner(error)
            Text(
                if (saving) "Saving…" else if (action == "used") "Use stock" else "Add stock", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { save() }.padding(vertical = 15.dp),
            )
        }
    }
}

// ───────────────────────────── material form ─────────────────────────────

/**
 * New material / edit material — the website's Item Master form: the basics (name, code, unit, category), stock (opening stock, as-of
 * date, its price, minimum), pricing & tax (sale rate, purchase price, tax slab, HSN) and more (brand, location, specifications).
 * Data: GET …/inventory-center/options · GET /inventory-center/materials/{id} · POST …/inventory-center/materials · PUT /inventory-center/materials/{id}.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialFormScreen(api: ApiClient, b: Branding, uuid: String, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val f = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var initial by remember { mutableStateOf<Map<String, String>?>(null) }
    val fields = listOf("name", "code", "uom_id", "category_id", "opening_stock", "stock_as_of_date", "stock_at_price", "min_stock", "rate", "purchase_price", "tax_rate", "hsn_code", "brand_name", "location", "specifications")
    fun num(v: Any?): String = (v as? Number)?.toDouble()?.takeIf { it != 0.0 }?.let { qty(it).replace(",", "") } ?: v?.toString()?.takeIf { it != "null" && it.toDoubleOrNull() != 0.0 }.orEmpty()

    LaunchedEffect(attempt) {
        loadError = null
        try {
            val o = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/inventory-center/options") }
            if (id != null) {
                val x = withContext(Dispatchers.IO) { api.detail("/inventory-center/materials/$id?project=$uuid") }
                fields.forEach { k ->
                    f[k] = when (k) {
                        "opening_stock", "stock_at_price", "min_stock", "rate", "purchase_price" -> num(x.opt(k))
                        "uom_id", "category_id" -> x.optInt(k).takeIf { it > 0 }?.toString().orEmpty()
                        else -> x.str(k).orEmpty()
                    }
                }
            } else f["stock_as_of_date"] = LocalDate.now().toString()
            options = o
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
        initial = f.toMap()
    }

    fun save() {
        val problems = mutableMapOf<String, String>()
        if (f["name"].isNullOrBlank()) problems["name"] = "Enter the material's name."
        if (f["uom_id"].isNullOrBlank()) problems["uom_id"] = "Choose the unit it is counted in."
        listOf("opening_stock", "stock_at_price", "min_stock", "rate", "purchase_price").forEach { k -> f[k]?.takeIf { it.isNotBlank() }?.let { if (it.toDoubleOrNull() == null || it.toDouble() < 0) problems[k] = "Enter a number (0 or more)." } }
        if (problems.isNotEmpty()) { errors = problems; banner = problems.values.first(); return }
        val body = JSONObject()
        fields.forEach { k ->
            val v = f[k].orEmpty().trim()
            body.put(k, when {
                v.isEmpty() -> JSONObject.NULL
                k in setOf("uom_id", "category_id") -> v.toInt()
                k in setOf("opening_stock", "stock_at_price", "min_stock", "rate", "purchase_price") -> v.toDouble()
                else -> v
            })
        }
        body.put("project", uuid)
        errors = emptyMap(); banner = null; saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (id != null) api.send("PUT", "/inventory-center/materials/$id", body) else api.send("POST", "/projects/$uuid/inventory-center/materials", body) }
                Toast.makeText(context, if (id != null) "Material saved" else "Material created", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: ApiException) { errors = e.errors; banner = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message }
            catch (e: Exception) { banner = e.message ?: "Could not save" }
            saving = false
        }
    }

    val back = leaveGuard(initial != null && f.toMap() != initial, onBack)
    val o = options ?: return Waiting(b, if (id != null) "Edit material" else "New material", loadError, onBack) { attempt++ }
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, if (id != null) "Edit material" else "New material", back, compact = true)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 8.dp)) {
                Banner(banner)
                Card("The material", "What it is and how it is counted") {
                    Input("Material name", f["name"].orEmpty(), { f["name"] = it }, errors["name"])
                    Input("Code", f["code"].orEmpty(), { f["code"] = it }, errors["code"])
                    PickerField("Unit", o.objs("uoms").firstOrNull { it.optInt("id").toString() == f["uom_id"] }?.let { "${it.optString("name")} (${it.optString("code")})" }, errors["uom_id"]) { sheet = "uom" }
                    PickerField("Category", o.objs("categories").firstOrNull { it.optInt("id").toString() == f["category_id"] }?.optString("name"), errors["category_id"]) { sheet = "category" }
                }
                Card("Stock", if (id == null) "What you have today and when to warn" else "The opening entry and when to warn") {
                    Input("Opening stock", f["opening_stock"].orEmpty(), { f["opening_stock"] = it.filter { c -> c.isDigit() || c == '.' } }, errors["opening_stock"], keyboard = KeyboardType.Decimal)
                    DatePick("As of date", f["stock_as_of_date"].orEmpty(), errors["stock_as_of_date"]) { f["stock_as_of_date"] = it }
                    Input("Stock at price (₹)", f["stock_at_price"].orEmpty(), { f["stock_at_price"] = it.filter { c -> c.isDigit() || c == '.' } }, errors["stock_at_price"], keyboard = KeyboardType.Decimal)
                    Input("Minimum stock (alert below)", f["min_stock"].orEmpty(), { f["min_stock"] = it.filter { c -> c.isDigit() || c == '.' } }, errors["min_stock"], keyboard = KeyboardType.Decimal)
                }
                Card("Pricing & tax") {
                    Input("Sale rate (₹)", f["rate"].orEmpty(), { f["rate"] = it.filter { c -> c.isDigit() || c == '.' } }, errors["rate"], keyboard = KeyboardType.Decimal)
                    Input("Purchase price (₹)", f["purchase_price"].orEmpty(), { f["purchase_price"] = it.filter { c -> c.isDigit() || c == '.' } }, errors["purchase_price"], keyboard = KeyboardType.Decimal)
                    Label("Tax rate")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        o.optJSONArray("tax_rates")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().forEach { t -> Pick(t, f["tax_rate"] == t, InvBlue) { f["tax_rate"] = if (f["tax_rate"] == t) "" else t } }
                    }
                    Input("HSN code", f["hsn_code"].orEmpty(), { f["hsn_code"] = it }, errors["hsn_code"])
                }
                Card("More") {
                    Input("Brand", f["brand_name"].orEmpty(), { f["brand_name"] = it }, errors["brand_name"])
                    Input("Location", f["location"].orEmpty(), { f["location"] = it }, errors["location"])
                    Input("Specifications", f["specifications"].orEmpty(), { f["specifications"] = it }, errors["specifications"], singleLine = false)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        SaveBar(if (id != null) "Save material" else "Create material", saving, b, ::save)
    }
    when (sheet) {
        "uom" -> ChoiceSheet("Unit", o.objs("uoms").map { Choice(it.optInt("id").toString(), "${it.optString("name")} (${it.optString("code")})") }, f["uom_id"], b, onClose = { sheet = null }) { f["uom_id"] = it; sheet = null }
        "category" -> ChoiceSheet("Category", o.objs("categories").map { Choice(it.optInt("id").toString(), it.optString("name")) }, f["category_id"], b, allowNone = true, onClose = { sheet = null }) { f["category_id"] = it; sheet = null }
    }
}

// ───────────────────────────── issue / return form ─────────────────────────────

private class Line(val material: JSONObject, amount: String) { var amount by mutableStateOf(amount) }

/**
 * Issue material / return material — the website's forms: the date, who it was issued to (issue) or who returned it to whom (return),
 * remarks, and one line per material with its quantity (an issue cannot take more than is on hand).
 * Data: GET …/inventory-center/options · POST …/inventory-center/issues | returns.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocFormScreen(api: ApiClient, b: Branding, uuid: String, kind: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val issue = kind == "issue"
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val people = remember { mutableStateListOf<Int>() }
    var by by remember { mutableIntStateOf(0) }
    var to by remember { mutableIntStateOf(0) }
    var remarks by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<Line>() }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        loadError = null
        try { options = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/inventory-center/options") } } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
    }

    fun save() {
        val problems = mutableMapOf<String, String>()
        if (!issue && by <= 0) problems["returned_by"] = "Choose who returned it."
        if (!issue && to <= 0) problems["return_to"] = "Choose who it was returned to."
        val filled = lines.filter { (it.amount.toDoubleOrNull() ?: 0.0) > 0 }
        if (filled.isEmpty()) problems["materials"] = "Add at least one material with a quantity."
        if (issue) filled.firstOrNull { it.amount.toDouble() > it.material.optDouble("on_hand", 0.0) + 0.0001 }?.let {
            problems["materials"] = "${it.material.optString("name")}: only ${qty(it.material.optDouble("on_hand", 0.0))} ${it.material.optString("unit")} on hand."
        }
        if (problems.isNotEmpty()) { errors = problems; banner = problems.values.first(); return }
        val body = JSONObject().put(if (issue) "issue_date" else "return_date", date).put("remarks", remarks.trim().ifBlank { JSONObject.NULL })
            .put("lines", JSONArray(filled.map { JSONObject().put("material_id", it.material.optInt("id")).put("qty", it.amount.toDouble()) }))
        if (issue) body.put("issued_by", JSONArray(people.toList())) else body.put("returned_by", by).put("return_to", to)
        errors = emptyMap(); banner = null; saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/inventory-center/${if (issue) "issues" else "returns"}", body) }
                Toast.makeText(context, if (issue) "Material issued" else "Material returned", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: ApiException) { errors = e.errors; banner = e.errors.values.firstOrNull { it.isNotBlank() } ?: e.message }
            catch (e: Exception) { banner = e.message ?: "Could not save" }
            saving = false
        }
    }

    val title = if (issue) "Issue material" else "Return material"
    val back = leaveGuard(lines.isNotEmpty() || remarks.isNotBlank(), onBack)
    val o = options ?: return Waiting(b, title, loadError, onBack) { attempt++ }
    val users = o.objs("users")
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, title, back, compact = true)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 8.dp)) {
                Banner(banner)
                Card(if (issue) "Issue" else "Return", "The number is given when it is saved") {
                    DatePick(if (issue) "Issue date" else "Return date", date, errors[if (issue) "issue_date" else "return_date"]) { date = it }
                    if (issue) {
                        PickerField("Issued to", people.mapNotNull { p -> users.firstOrNull { it.optInt("id") == p }?.optString("name") }.joinToString(", ").ifBlank { null }, errors["issued_by"]) { sheet = "people" }
                    } else {
                        PickerField("Returned by", users.firstOrNull { it.optInt("id") == by }?.optString("name"), errors["returned_by"]) { sheet = "by" }
                        PickerField("Returned to", users.firstOrNull { it.optInt("id") == to }?.optString("name"), errors["return_to"]) { sheet = "to" }
                    }
                    Input("Remarks", remarks, { remarks = it }, singleLine = false)
                }
                Card("Materials", if (issue) "What goes out, and how much" else "What comes back, and how much") {
                    errors["materials"]?.let { Text(it, color = KitDanger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    lines.forEachIndexed { i, l ->
                        val have = l.material.optDouble("on_hand", 0.0)
                        val over = issue && (l.amount.toDoubleOrNull() ?: 0.0) > have + 0.0001
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.material.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${qty(have)} ${l.material.optString("unit")} on hand", color = if (over) KitDanger else KitMuted, fontSize = 12.sp)
                                }
                                Text("✕", color = KitDanger, fontSize = 15.sp, modifier = Modifier.clip(CircleShape).background(KitDanger.copy(alpha = .1f)).clickable { lines.removeAt(i) }.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                            Input("Quantity (${l.material.optString("unit")})", l.amount, { l.amount = it.filter { c -> c.isDigit() || c == '.' } }, if (over) "More than is on hand" else null, keyboard = KeyboardType.Decimal)
                        }
                    }
                    Text(
                        "+  Add material", color = b.base, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base.copy(alpha = .12f)).clickable { sheet = "material" }.padding(vertical = 13.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        SaveBar(if (issue) "Issue material" else "Return material", saving, b, ::save)
    }
    when (sheet) {
        "material" -> MaterialPickSheet(o.objs("materials"), b, exclude = lines.map { it.material.optInt("id") }.toSet(), onClose = { sheet = null }) { m -> lines.add(Line(m, "")); sheet = null }
        "people" -> PeoplePick(users, people.toSet(), b, "Issued to", onClose = { sheet = null }) { picked -> people.clear(); people.addAll(picked); sheet = null }
        "by" -> ChoiceSheet("Returned by", users.map { Choice(it.optInt("id").toString(), it.optString("name")) }, by.takeIf { it > 0 }?.toString(), b, onClose = { sheet = null }) { by = it.toIntOrNull() ?: 0; sheet = null }
        "to" -> ChoiceSheet("Returned to", users.map { Choice(it.optInt("id").toString(), it.optString("name")) }, to.takeIf { it > 0 }?.toString(), b, onClose = { sheet = null }) { to = it.toIntOrNull() ?: 0; sheet = null }
    }
}

/** Pick several people (✓ marks the chosen ones), then Done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeoplePick(users: List<JSONObject>, current: Set<Int>, b: Branding, title: String, onClose: () -> Unit, onDone: (List<Int>) -> Unit) {
    val picked = remember { mutableStateListOf<Int>().apply { addAll(current) } }
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(b.base).clickable { onDone(picked.toList()) }.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            com.example.gavanhomes.ui.common.SearchBox(q, { q = it }, "Search people…")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                users.filter { q.isBlank() || it.optString("name").contains(q, true) }.forEach { u ->
                    val id = u.optInt("id"); val on = id in picked
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else Color(0xFFF7F5F1)).clickable { if (on) picked.remove(id) else picked.add(id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.example.gavanhomes.ui.common.Initials(u.optString("name"), size = 30.dp)
                        Text(u.optString("name"), color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        if (on) Text("✓", color = b.base, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── issue / return details ─────────────────────────────

/** An issue or return: number, date, people, remarks and its lines; Delete takes its stock effect back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocDetailScreen(api: ApiClient, b: Branding, kind: String, id: Int, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val issue = kind == "issue"
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var askDelete by remember { mutableStateOf(false) }
    val base = "/inventory-center/${if (issue) "issues" else "returns"}/$id"
    LaunchedEffect(id, attempt) { error = null; try { d = withContext(Dispatchers.IO) { api.detail(base) } } catch (e: Exception) { error = e.message ?: "Could not load" } }

    val title = if (issue) "Material issue" else "Material return"
    val x = d ?: return Waiting(b, title, error, onBack) { attempt++ }
    val can = x.optJSONObject("can") ?: JSONObject()
    Column(Modifier.fillMaxSize().background(KitPage).verticalScroll(rememberScrollState())) {
        HeroBar(b, title, onBack, actions = { if (can.optBoolean("delete")) DeleteButton { askDelete = true } }) {
            Column {
                Text(x.optString("number"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(prettyDay(x.str("date")) ?: "—", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                HeroChips("${x.optInt("items")} item${if (x.optInt("items") == 1) "" else "s"}", "Qty ${qty(x.optDouble("qty", 0.0))}")
            }
        }
        Card("Details") {
            if (issue) Fact("Issued to", x.optJSONArray("people")?.let { a -> (0 until a.length()).joinToString(", ") { a.getString(it) } }?.ifBlank { null })
            else { Fact("Returned by", x.str("returned_by")); Fact("Returned to", x.str("returned_to")) }
            Fact("Recorded by", x.str("by")); Fact("Recorded", whenLabel(x.str("created_at"))); Fact("Remarks", x.str("remarks"))
        }
        Card("Materials") {
            x.objs("lines").forEach { l ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        l.str("code")?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
                    }
                    Text("${qty(l.optDouble("qty", 0.0))} ${l.optString("unit")}", color = if (issue) InvPurple else InvBlue, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.width(1.dp).height(28.dp))
    }
    if (askDelete) SwalConfirm(
        title = "Delete ${x.optString("number")}?", text = if (issue) "The issued stock goes back on hand." else "The returned stock is taken back out.", confirmText = "Yes, delete it",
        onConfirm = {
            askDelete = false
            scope.launch {
                try { withContext(Dispatchers.IO) { api.delete(base) }; Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show(); onChanged(); onBack() }
                catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
            }
        },
        onCancel = { askDelete = false },
    )
}
