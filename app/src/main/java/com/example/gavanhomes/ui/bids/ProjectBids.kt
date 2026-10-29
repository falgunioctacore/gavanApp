package com.example.gavanhomes.ui.bids

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.example.gavanhomes.ui.common.SegTabs
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.professionals.ProDonutCard
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Blue = Color(0xFF1E5FAE)
private val Amber = Color(0xFFC47A1A)

internal fun bidTint(status: String?): Color = when (status) {
    "open" -> Blue
    "submitted" -> Amber
    "awarded" -> KitGood
    "closed" -> KitDanger
    else -> KitMuted
}

/** 1234.5 → "₹1,234.50". */
internal fun rupees(v: Double): String = "₹" + String.format(Locale.US, "%,.2f", v)

/** "2026-07-07 11:58" → "7 Jul 2026" (with the time when [time] is set). */
internal fun bidDay(s: String?, time: Boolean = false): String? = runCatching {
    val d = LocalDate.parse(s!!.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    if (time && s.length >= 16) "$d · ${s.substring(11, 16)}" else d
}.getOrNull()

private val Sorts = listOf("newest" to "Newest first", "oldest" to "Oldest first", "closing" to "Closing soonest", "title" to "Title A–Z")

/**
 * The project's Bids module, like the website's Bids tab: a Dashboard (counts by status, awarded value, bids closing soon) and the bid List
 * (search, status and supplier filters, bulk delete). Tap a bid for its details, items, suppliers and submissions.
 * Data: GET /projects/{uuid}/bid-center/overview · options · list.
 */
@Composable
fun ProjectBids(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit, onNew: () -> Unit, onNewFromTask: (Int) -> Unit, settingsOpen: Boolean = false, onCloseSettings: () -> Unit = {}) {
    var view by remember { mutableIntStateOf(0) }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    val bidsRefresh = 0

    LaunchedEffect(version, attempt) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/bid-center/overview") } }
        catch (e: Exception) { error = e.message ?: "Could not load the bids" }
    }
    LaunchedEffect(uuid, version) { options = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/bid-center/options") } }.getOrNull() }

    if (settingsOpen) { com.example.gavanhomes.ui.leads.SettingsEntry(true, "Bid settings", b.base, onCloseSettings); BidSettings(api, b, uuid, version) { attempt++ }; return }
    SegTabs(listOf("Dashboard", "Bids", "Task bids"), view) { view = it }

    val o = overview
    if (o == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
        }
        return
    }
    val canCreate = o.optJSONObject("can")?.optBoolean("create") == true
    when (view) {
        0 -> BidDashboard(o, canCreate, onNew, onOpen) { key -> sel = mapOf("status" to setOf(key)); query = ""; view = 1 }
        2 -> TaskBidList(api, b, uuid, bidsRefresh + version, onOpen, onNewFromTask)
        else -> BidList(api, b, uuid, version, o, options, sel, { sel = it }, query, { query = it }, sort, { sort = it }, canCreate, onNew, onOpen, onChanged = { attempt++ })
    }
}

// ───────────────────────────── dashboard ─────────────────────────────

@Composable
private fun BidDashboard(o: JSONObject, canCreate: Boolean, onNew: () -> Unit, onOpen: (Int) -> Unit, onStatus: (String) -> Unit) {
    if (canCreate) Text(
        "+  New bid", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitInk).clickable { onNew() }.padding(vertical = 14.dp),
    )
    val c = o.optJSONObject("counts") ?: JSONObject()
    Box(Modifier.padding(top = 14.dp)) {
        ProKpiTiles(
            listOf(
                ProKpi(o.optInt("total").toString(), "Total bids", KitInk), ProKpi(c.optInt("open").toString(), "Open", Blue), ProKpi(c.optInt("awarded").toString(), "Awarded", KitGood),
                ProKpi(c.optInt("draft").toString(), "Draft", KitMuted), ProKpi(o.optInt("submissions").toString(), "Submissions", Amber), ProKpi(o.optInt("pending_review").toString(), "To review", KitDanger),
            ),
            columns = 3,
        )
    }
    val labels = listOf("draft" to "Draft", "open" to "Open", "submitted" to "Submitted", "awarded" to "Awarded", "closed" to "Closed")
    ProDonutCard("By status", "Tap the list below to see them", labels.map { (k, l) -> Triple(l, c.optInt(k), bidTint(k)) })
    Card("Awarded value", "What the accepted submissions add up to") {
        Text(rupees(o.optDouble("awarded_amount")), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = KitGood)
    }
    Card("Open a status", "Tap one to see those bids") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach { (k, l) -> Pick("$l · ${c.optInt(k)}", false, bidTint(k)) { onStatus(k) } }
        }
    }
    val soon = o.objs("closing_soon")
    if (soon.isNotEmpty()) Card("Closing soon", "Ending within 3 days") { soon.forEach { BidCard(it, flat = true, onClick = { onOpen(it.optInt("id")) }) } }
    val recent = o.objs("recent")
    if (recent.isNotEmpty()) Card("Recent bids") { recent.forEach { BidCard(it, flat = true, onClick = { onOpen(it.optInt("id")) }) } }
}

// ───────────────────────────── list ─────────────────────────────

private fun groupsOf(options: JSONObject?): List<FilterGroup> = listOfNotNull(
    FilterGroup("status", "Status", listOf("draft", "open", "submitted", "awarded", "closed").map { FilterOption(it, it.replaceFirstChar { c -> c.uppercase() }, null, bidTint(it)) }),
    options?.objs("suppliers")?.takeIf { it.isNotEmpty() }?.let { s -> FilterGroup("supplier", "Supplier", s.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
)

private fun queryOf(sel: Map<String, Set<String>>, sort: Int): String = buildString {
    sel.forEach { (k, v) -> if (v.isNotEmpty()) append("&$k=").append(URLEncoder.encode(v.joinToString(","), "UTF-8")) }
    append("&sort=").append(Sorts[sort.coerceIn(0, Sorts.lastIndex)].first)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BidList(
    api: ApiClient, b: Branding, uuid: String, version: Int, o: JSONObject, options: JSONObject?,
    sel: Map<String, Set<String>>, setSel: (Map<String, Set<String>>) -> Unit, query: String, setQuery: (String) -> Unit, sort: Int, setSort: (Int) -> Unit,
    canCreate: Boolean, onNew: () -> Unit, onOpen: (Int) -> Unit, onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<JSONObject>() }
    val selected = remember { mutableStateListOf<Int>() }
    var page by remember { mutableIntStateOf(1) }
    var lastPage by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var askDelete by remember { mutableStateOf(false) }
    val canDelete = o.optJSONObject("can")?.optBoolean("delete") == true
    val fq = queryOf(sel, sort)

    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/bid-center/list?page=$p&search=${URLEncoder.encode(query.trim(), "UTF-8")}$fq") }
            if (p == 1) rows.clear()
            rows.addAll(r.objs("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1); total = r.optInt("total")
        } catch (e: Exception) { error = e.message ?: "Could not load" }
        loading = false
    }
    LaunchedEffect(fq, version, attempt) { selected.clear(); load(1) }
    LaunchedEffect(query) { delay(350); load(1) }

    fun bulkDelete() {
        val ids = selected.toList()
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/bid-center/bulk-delete", JSONObject().put("ids", JSONArray(ids))) }
                android.widget.Toast.makeText(context, r.optString("message", "Deleted"), android.widget.Toast.LENGTH_LONG).show()
                selected.clear(); onChanged(); attempt++
            } catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "Could not delete", android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    FilterBar(
        query, setQuery, "Search bids…", groups = groupsOf(options), selected = sel, onApply = { m -> setSel(m.filterValues { it.isNotEmpty() }) },
        countWith = { m -> if (m == sel) total else -1 }, sortOptions = Sorts.map { it.second }, sortIndex = sort, onSort = setSort,
    )
    Row(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$total bid${if (total == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (canCreate) Text("+  New bid", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(b.base).clickable { onNew() }.padding(horizontal = 16.dp, vertical = 9.dp))
    }

    if (selected.isNotEmpty()) Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitInk).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${selected.size} selected", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("Cancel", color = Color.White.copy(alpha = .8f), fontSize = 12.sp, modifier = Modifier.clickable { selected.clear() }.padding(8.dp))
        Text("Delete", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .14f)).clickable { askDelete = true }.padding(horizontal = 16.dp, vertical = 9.dp))
    }

    rows.forEach { r ->
        val id = r.optInt("id")
        BidCard(r, selected = if (selected.isEmpty()) null else id in selected, onLongClick = if (canDelete) ({ if (id !in selected) selected.add(id) }) else null) {
            if (selected.isNotEmpty()) { if (id in selected) selected.remove(id) else selected.add(id) } else onOpen(id)
        }
    }
    if (rows.isNotEmpty() && selected.isEmpty() && canDelete) Text("Press and hold a bid to select several", color = KitMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

    when {
        loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
        error != null && rows.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
        }
        rows.isEmpty() -> Text("No bids match.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
        loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
    }
    if (rows.isNotEmpty() && page < lastPage && !loading) {
        var more by remember(page) { mutableStateOf(false) }
        LaunchedEffect(more) { if (more) load(page + 1) }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { OutlinedButton(onClick = { more = true }) { Text("Load more") } }
    }
    if (askDelete) SwalConfirm(
        title = "Delete ${selected.size} bid${if (selected.size == 1) "" else "s"}?", text = "Bids that suppliers have already answered are kept.", confirmText = "Yes, delete",
        onConfirm = { askDelete = false; bulkDelete() }, onCancel = { askDelete = false },
    )
}

/** One bid: title and status, its dates, the suppliers invited and how many have answered (with a badge for the ones waiting for review). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun BidCard(r: JSONObject, flat: Boolean = false, selected: Boolean? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val key = r.optString("status")
    val sc = bidTint(key)
    Column(
        Modifier.padding(horizontal = if (flat) 0.dp else 16.dp, vertical = if (flat) 4.dp else 7.dp).fillMaxWidth()
            .then(if (flat) Modifier else Modifier.shadow(6.dp, RoundedCornerShape(22.dp)))
            .clip(RoundedCornerShape(22.dp)).background(if (selected == true) sc.copy(alpha = .14f) else if (flat) Color(0xFFF7F5F1) else Color.White)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) Box(Modifier.padding(end = 10.dp).size(24.dp).clip(CircleShape).background(if (selected) KitInk else Color(0xFFE6E3DD)), contentAlignment = Alignment.Center) { if (selected) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
            Text(r.optString("title"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(8.dp))
            Chip(r.optString("status_label"), sc)
        }
        Text("◷ " + listOfNotNull(bidDay(r.str("start_date")), bidDay(r.str("end_date"))).joinToString(" → "), color = KitMuted, fontSize = 12.sp)
        val sup = r.optJSONArray("suppliers")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sup.take(3).forEach { Chip(it, Blue) }
            if (r.optInt("supplier_count") > 3) Chip("+${r.optInt("supplier_count") - 3}", KitMuted)
            if (sup.isEmpty()) Text("No suppliers", color = KitMuted, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${r.optInt("submissions")} of ${r.optInt("supplier_count")} submitted", color = KitMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (r.optInt("pending") > 0) Chip("${r.optInt("pending")} to review", Amber)
            if (r.optBoolean("locked")) Text("🔒", fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
