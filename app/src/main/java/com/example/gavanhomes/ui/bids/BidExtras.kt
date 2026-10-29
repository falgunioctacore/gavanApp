package com.example.gavanhomes.ui.bids

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.FilterBar
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.SendButton
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

private val Blue = Color(0xFF1E5FAE)
private val Amber = Color(0xFFC47A1A)

private fun taskBidTint(status: String?): Color = if (status == "no_bid") KitMuted else bidTint(status)
private fun taskBidLabel(status: String?): String = if (status == "no_bid") "No bid" else (status ?: "").replaceFirstChar { it.uppercase() }

// ───────────────────────────── task bids ─────────────────────────────

/**
 * The website's Task Bids tab: every task that asks for materials or services, and the bid made for it (or **Create bid**, which opens the bid form with the
 * task's items filled in). Data: GET /projects/{uuid}/bid-center/task-bids.
 */
@Composable
internal fun TaskBidList(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit, onCreate: (Int) -> Unit) {
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var counts by remember { mutableStateOf<JSONObject?>(null) }
    var canCreate by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }

    val statusQuery = sel["status"]?.takeIf { it.isNotEmpty() }?.let { "&status=" + URLEncoder.encode(it.joinToString(","), "UTF-8") }.orEmpty()
    suspend fun load() {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/bid-center/task-bids?search=${URLEncoder.encode(query.trim(), "UTF-8")}$statusQuery") }
            rows = r.objs("data"); counts = r.optJSONObject("counts"); canCreate = r.optJSONObject("can")?.optBoolean("create") == true
        } catch (e: Exception) { error = e.message ?: "Could not load the task bids" }
        loading = false
    }
    LaunchedEffect(statusQuery, version, attempt) { load() }
    LaunchedEffect(query) { delay(350); load() }

    val c = counts
    if (c != null) Box(Modifier.padding(top = 12.dp)) {
        ProKpiTiles(
            listOf(
                ProKpi(c.optInt("total").toString(), "Tasks", KitInk), ProKpi(c.optInt("no_bid").toString(), "No bid", KitMuted), ProKpi(c.optInt("open").toString(), "Open", Blue),
                ProKpi(c.optInt("submitted").toString(), "Submitted", Amber), ProKpi(c.optInt("awarded").toString(), "Awarded", KitGood), ProKpi(c.optInt("closed").toString(), "Closed", KitDanger),
            ),
            columns = 3,
        )
    }
    FilterBar(
        query, { query = it }, "Search task or bid…",
        groups = listOf(FilterGroup("status", "Status", listOf("no_bid", "draft", "open", "submitted", "awarded", "closed").map { FilterOption(it, taskBidLabel(it), c?.optInt(it), taskBidTint(it)) })),
        selected = sel, onApply = { sel = it.filterValues { v -> v.isNotEmpty() } }, countWith = { m -> if (m == sel) rows.size else -1 },
    )
    Text("${rows.size} task${if (rows.size == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))

    rows.forEach { r ->
        val st = r.optString("status")
        val bid = r.optJSONObject("bid")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.optString("task"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${r.optInt("items_count")} item${if (r.optInt("items_count") == 1) "" else "s"} to buy", color = KitMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.size(8.dp))
                Chip(taskBidLabel(st), taskBidTint(st))
            }
            if (bid != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onOpen(bid.optInt("id")) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(bid.optString("title"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("◷ " + listOfNotNull(bidDay(bid.str("start_date")), bidDay(bid.str("end_date"))).joinToString(" → "), color = KitMuted, fontSize = 12.sp)
                    val sup = bid.optJSONArray("suppliers")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                    if (sup.isNotEmpty()) Text(sup.take(3).joinToString(", ") + if (sup.size > 3) " +${sup.size - 3}" else "", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${bid.optInt("submissions")} of ${bid.optInt("supplier_count")} submitted" + if (bid.optInt("pending") > 0) " · ${bid.optInt("pending")} to review" else "", color = KitMuted, fontSize = 12.sp)
                }
            } else if (canCreate) {
                Text("+  Create bid", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(b.base).clickable { onCreate(r.optInt("task_id")) }.padding(vertical = 12.dp))
            }
        }
    }
    when {
        loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
        error != null && rows.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
        }
        rows.isEmpty() -> Text("No tasks are waiting on a bid.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
    }
}

// ───────────────────────────── settings ─────────────────────────────

/**
 * The website's Bid Settings: the terms & conditions that can be attached to a bid — add, edit (title, text, active) and delete.
 * Data: GET /projects/{uuid}/bid-center/terms · POST …/terms · DELETE /bid-center/terms/{id}.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BidSettings(api: ApiClient, b: Branding, uuid: String, version: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var canManage by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }      // an empty object adds
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var openId by remember { mutableIntStateOf(0) }

    LaunchedEffect(version, attempt) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/bid-center/terms") }
            rows = r.objs("data"); canManage = r.optJSONObject("can")?.optBoolean("manage") == true
        } catch (e: Exception) { error = e.message ?: "Could not load the terms" }
        loading = false
    }
    fun act(success: String, call: () -> Unit) {
        scope.launch {
            try { withContext(Dispatchers.IO) { call() }; android.widget.Toast.makeText(context, success, android.widget.Toast.LENGTH_SHORT).show(); attempt++; onChanged() }
            catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "Could not save", android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Terms & conditions", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text("Attached to the bids you send to suppliers", color = KitMuted, fontSize = 12.sp)
        }
        if (canManage) Text("+  New terms", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(b.base).clickable { editing = JSONObject() }.padding(horizontal = 16.dp, vertical = 9.dp))
    }

    rows.forEach { t ->
        val id = t.optInt("id"); val active = t.optBoolean("active"); val open = openId == id
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(5.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White)
                .clickable { openId = if (open) 0 else id }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.optString("title"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Chip(if (active) "Active" else "Inactive", if (active) KitGood else KitMuted)
            }
            if (open) com.example.gavanhomes.ui.common.HtmlText(t.optString("html").ifBlank { t.optString("content") }, color = KitInk, fontSize = 14.sp)
            else Text(t.optString("content"), color = KitMuted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bidDay(t.str("created_at")) ?: "", color = KitMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (canManage) {
                    Text("✎", color = KitInk, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).background(Color(0xFFF1EFEA)).clickable { editing = t }.padding(horizontal = 11.dp, vertical = 6.dp))
                    Text("🗑", fontSize = 15.sp, modifier = Modifier.clip(CircleShape).background(KitDanger.copy(alpha = .1f)).clickable { deleting = t }.padding(horizontal = 9.dp, vertical = 6.dp))
                }
            }
        }
    }
    when {
        loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
        error != null && rows.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
        }
        rows.isEmpty() -> Text("No bid terms yet.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
    }

    editing?.let { t -> TermSheet(t, onClose = { editing = null }) { title, content, active ->
        editing = null
        act(if (t.has("id")) "Terms saved" else "Terms added") {
            api.send("POST", "/projects/$uuid/bid-center/terms", JSONObject().put("id", if (t.has("id")) t.optInt("id") else JSONObject.NULL).put("title", title).put("content", content).put("active", active))
        }
    } }
    deleting?.let { t -> SwalConfirm(
        title = "Delete these terms?", text = t.optString("title"), confirmText = "Yes, delete it",
        onConfirm = { deleting = null; act("Terms deleted") { api.delete("/bid-center/terms/${t.optInt("id")}") } }, onCancel = { deleting = null },
    ) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TermSheet(t: JSONObject, onClose: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf(t.optString("title")) }
    var content by remember { mutableStateOf(t.optString("content")) }
    var active by remember { mutableStateOf(if (t.has("active")) t.optBoolean("active") else true) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (t.has("id")) "Edit terms" else "New terms", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Input("Title", title, { title = it })
            Input("Terms & conditions", content, { content = it }, singleLine = false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pick("Active", active, KitGood) { active = true }; Pick("Inactive", !active, KitMuted) { active = false } }
            SendButton(if (t.has("id")) "Save terms" else "Add terms", title.isNotBlank() && content.isNotBlank()) { onSave(title.trim(), content.trim(), active) }
        }
    }
}
