package com.example.gavanhomes.ui.leads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import com.example.gavanhomes.ui.professionals.ProDonutCard
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.tasks.CallChip
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Pick
import com.example.gavanhomes.ui.tasks.objs
import com.example.gavanhomes.ui.tasks.prettyDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private val Blue = Color(0xFF1E5FAE)
private val Amber = Color(0xFFC47A1A)

/** "#rrggbb" from the server as a Color (grey when missing or malformed). */
internal fun tint(hex: String?, fallback: Color = KitMuted): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

/** "2026-11-01 10:00" → "1 Nov 2026 · 10:00". */
internal fun whenLabel(s: String?): String? = prettyDay(s)?.let { d -> d + (s?.drop(11)?.take(5)?.takeIf { it.length == 5 && it != "00:00" }?.let { " · $it" } ?: "") }

internal fun statusTintOf(options: JSONObject?, name: String?): Color =
    tint(options?.objs("statuses")?.firstOrNull { it.optString("name") == name }?.str("color"), if (name.isNullOrBlank()) Blue else KitMuted)

internal fun labelTintOf(options: JSONObject?, name: String?): Color = when (name?.lowercase()) {
    "hot" -> KitDanger
    "warm" -> Amber
    "cold" -> Color(0xFF6B7A90)
    else -> tint(options?.objs("labels")?.firstOrNull { it.optString("name") == name }?.str("color"), Blue)
}

private class Quick(val key: String, val label: String)
private val QuickViews = listOf(
    Quick("my", "My leads"), Quick("today", "Follow-up today"), Quick("missed", "Missed follow-up"), Quick("site_visit", "Site visit"), Quick("hot", "Hot"),
    Quick("converted", "Converted"), Quick("lost", "Lost"), Quick("unassigned", "Unassigned"), Quick("untouched", "Untouched"), Quick("no_task", "No task"), Quick("duplicates", "Duplicates"),
)
private val Sorts = listOf("newest" to "Newest first", "oldest" to "Oldest first", "name" to "Name A–Z")

/**
 * The project's Leads module, like the website's Leads tab: a Dashboard (numbers, charts, quick views, follow-ups due), the lead List (search, filters,
 * call / WhatsApp, bulk actions) and the Follow-ups of every lead.
 * Data: GET /projects/{uuid}/lead-center/overview · options · list · followups.
 */
@Composable
fun ProjectLeads(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int, String) -> Unit, onNew: () -> Unit, onEdit: (Int) -> Unit, settingsOpen: Boolean = false, onCloseSettings: () -> Unit = {}) {
    var view by remember { mutableIntStateOf(0) }                    // 0 Dashboard · 1 Leads · 2 Follow-ups
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }

    LaunchedEffect(version, attempt) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/lead-center/overview") } }
        catch (e: Exception) { error = e.message ?: "Could not load the leads" }
    }
    LaunchedEffect(uuid, version) { options = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/lead-center/options") } }.getOrNull() }

    if (settingsOpen) { SettingsEntry(true, "Lead settings", b.base, onCloseSettings); LeadSettings(api, b, uuid, version) { attempt++ }; return }
    SegTabs(listOf("Dashboard", "Leads", "Follow-ups", "Lists", "Export", "Pipeline"), view) { view = it }

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
        0 -> LeadDashboard(o, options, canCreate, onNew, onOpen) { key -> sel = mapOf("quick" to setOf(key)); query = ""; view = 1 }
        1 -> LeadList(api, b, uuid, version, o, options, sel, { sel = it }, query, { query = it }, sort, { sort = it }, canCreate, onNew, onOpen, onChanged = { attempt++ })
        2 -> FollowupList(api, b, uuid, version, options, onOpen)
        3 -> LeadLists(api, b, uuid, version, onOpenList = { id -> sel = mapOf("list_id" to setOf(id.toString())); query = ""; view = 1 }, onChanged = { attempt++ })
        4 -> ExportRequests(api, b, uuid, version, options)
        else -> PipelineReport(api, b, uuid, version, options)
    }
}

// ───────────────────────────── dashboard ─────────────────────────────

@Composable
private fun LeadDashboard(o: JSONObject, options: JSONObject?, canCreate: Boolean, onNew: () -> Unit, onOpen: (Int, String) -> Unit, onQuick: (String) -> Unit) {
    if (canCreate) Text(
        "+  New lead", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitInk).clickable { onNew() }.padding(vertical = 14.dp),
    )
    val k = o.optJSONObject("kpis") ?: JSONObject()
    Box(Modifier.padding(top = 14.dp)) {
        ProKpiTiles(
            listOf(
                ProKpi(k.optInt("total").toString(), "Total leads", KitInk), ProKpi(k.optInt("new").toString(), "New", Blue), ProKpi(k.optInt("working").toString(), "Working", KitGood),
                ProKpi(k.optInt("due_today").toString(), "Due today", Amber), ProKpi(k.optInt("visit_plan").toString(), "Visit planned", Color(0xFF6A4FB3)), ProKpi(k.optInt("visit_done").toString(), "Visit done", KitGood),
                ProKpi(k.optInt("hot").toString(), "Hot", KitDanger), ProKpi(k.optInt("converted").toString(), "Converted", KitGood), ProKpi(k.optInt("lost").toString(), "Lost", KitMuted),
            ),
            columns = 3,
        )
    }

    val byStatus = o.objs("by_status")
    ProDonutCard("By status", "Working leads per status", byStatus.map { Triple(it.optString("name"), it.optInt("count"), statusTintOf(options, it.optString("name"))) })
    val byLabel = o.objs("by_label")
    ProDonutCard("By label", "Hot, warm and cold", byLabel.map { Triple(it.optString("name"), it.optInt("count"), labelTintOf(options, it.optString("name"))) })
    val palette = listOf(Blue, KitGood, Amber, Color(0xFF6A4FB3), KitDanger, Color(0xFF1B8A8F), Color(0xFFB8527A), Color(0xFF6B7A90))
    ProDonutCard("By source", "Where the leads come from", o.objs("by_source").mapIndexed { i, s -> Triple(s.optString("name"), s.optInt("count"), palette[i % palette.size]) })

    val q = o.optJSONObject("quick")
    Card("Quick views", "Tap one to see those leads") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickViews.forEach { v -> Pick("${v.label} · ${q?.optInt(v.key, 0) ?: 0}", false, Blue) { onQuick(v.key) } }
        }
    }

    val due = o.objs("due")
    if (due.isNotEmpty()) Card("Follow-ups due", "Pending, due today or earlier") { due.forEach { LeadCard(it, options, flat = true, onMore = null) { onOpen(it.optInt("id"), "followups") } } }
}

// ───────────────────────────── list ─────────────────────────────

private fun groupsOf(o: JSONObject, options: JSONObject?): List<FilterGroup> {
    val q = o.optJSONObject("quick")
    return listOfNotNull(
        FilterGroup("quick", "Quick view", QuickViews.map { FilterOption(it.key, it.label, q?.optInt(it.key)) }),
        options?.objs("statuses")?.takeIf { it.isNotEmpty() }?.let { s -> FilterGroup("status", "Status", s.map { FilterOption(it.optString("name"), it.optString("name"), null, tint(it.str("color"))) }) },
        options?.objs("labels")?.takeIf { it.isNotEmpty() }?.let { l -> FilterGroup("label", "Label", l.map { FilterOption(it.optString("name"), it.optString("name"), null, labelTintOf(options, it.optString("name"))) }) },
        options?.objs("users")?.takeIf { it.isNotEmpty() }?.let { u -> FilterGroup("assigned_to", "Assigned to", u.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
        options?.objs("lists")?.takeIf { it.isNotEmpty() }?.let { l -> FilterGroup("list_id", "Lead list", l.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
        options?.optJSONArray("sources")?.let { a -> (0 until a.length()).map { a.getString(it) } }?.takeIf { it.isNotEmpty() }?.let { s -> FilterGroup("source", "Source", s.map { FilterOption(it, it) }) },
    )
}

private fun queryOf(sel: Map<String, Set<String>>, sort: Int): String = buildString {
    sel.forEach { (k, v) ->
        if (v.isEmpty()) return@forEach
        if (k == "quick") append("&quick=").append(v.first()) else append("&$k=").append(URLEncoder.encode(v.joinToString(","), "UTF-8"))
    }
    append("&sort=").append(Sorts[sort.coerceIn(0, Sorts.lastIndex)].first)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LeadList(
    api: ApiClient, b: Branding, uuid: String, version: Int, o: JSONObject, options: JSONObject?,
    sel: Map<String, Set<String>>, setSel: (Map<String, Set<String>>) -> Unit, query: String, setQuery: (String) -> Unit, sort: Int, setSort: (Int) -> Unit,
    canCreate: Boolean, onNew: () -> Unit, onOpen: (Int, String) -> Unit, onChanged: () -> Unit,
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
    var sheet by remember { mutableStateOf<String?>(null) }           // bulk-status · bulk-label · bulk-assign · bulk-list
    var askDelete by remember { mutableStateOf(false) }
    val can = o.optJSONObject("can")
    val canEdit = can?.optBoolean("edit") == true
    val canDelete = can?.optBoolean("delete") == true
    val filterQuery = queryOf(sel, sort)

    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/lead-center/list?page=$p&search=${URLEncoder.encode(query.trim(), "UTF-8")}$filterQuery") }
            if (p == 1) rows.clear()
            rows.addAll(r.objs("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1); total = r.optInt("total")
        } catch (e: Exception) { error = e.message ?: "Could not load" }
        loading = false
    }
    LaunchedEffect(filterQuery, version, attempt) { selected.clear(); load(1) }
    LaunchedEffect(query) { delay(350); load(1) }

    fun bulk(action: String, extra: String?) {
        val ids = selected.toList()
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/lead-center/bulk", JSONObject().put("ids", JSONArray(ids)).put("action", action).put("extra", extra ?: JSONObject.NULL)) }
                android.widget.Toast.makeText(context, "${ids.size} lead${if (ids.size == 1) "" else "s"} updated", android.widget.Toast.LENGTH_SHORT).show()
                selected.clear(); onChanged(); attempt++
            } catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "Could not update", android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    FilterBar(
        query, setQuery, "Search name, phone or email…",
        groups = groupsOf(o, options), selected = sel,
        onApply = { m -> setSel(m.mapValues { (k, v) -> if (k == "quick") v.take(1).toSet() else v }.filterValues { it.isNotEmpty() }) },
        countWith = { m -> if (m == sel) total else -1 },
        sortOptions = Sorts.map { it.second }, sortIndex = sort, onSort = setSort,
    )
    Row(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$total lead${if (total == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (canCreate) Text("+  New lead", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(b.base).clickable { onNew() }.padding(horizontal = 16.dp, vertical = 9.dp))
    }

    if (selected.isNotEmpty()) Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitInk).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.size} selected", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("Select all", color = Color.White.copy(alpha = .8f), fontSize = 12.sp, modifier = Modifier.clickable { selected.clear(); selected.addAll(rows.map { it.optInt("id") }) }.padding(6.dp))
            Text("Cancel", color = Color.White.copy(alpha = .8f), fontSize = 12.sp, modifier = Modifier.clickable { selected.clear() }.padding(6.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canEdit) { BulkButton("Status") { sheet = "bulk-status" }; BulkButton("Label") { sheet = "bulk-label" }; BulkButton("Assign") { sheet = "bulk-assign" }; BulkButton("Move to list") { sheet = "bulk-list" } }
            if (canDelete) BulkButton("Delete", KitDanger) { askDelete = true }
        }
    }

    rows.forEach { r ->
        val id = r.optInt("id")
        LeadCard(r, options, selected = if (selected.isEmpty()) null else id in selected, onLongClick = if (canEdit || canDelete) ({ if (id !in selected) selected.add(id) }) else null, onMore = null) {
            if (selected.isNotEmpty()) { if (id in selected) selected.remove(id) else selected.add(id) } else onOpen(id, "details")
        }
    }
    if (rows.isNotEmpty() && selected.isEmpty() && (canEdit || canDelete)) Text("Press and hold a lead to select several", color = KitMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

    when {
        loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
        error != null && rows.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
        }
        rows.isEmpty() -> Text("No leads match.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
        loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
    }
    if (rows.isNotEmpty() && page < lastPage && !loading) {
        var more by remember(page) { mutableStateOf(false) }
        LaunchedEffect(more) { if (more) load(page + 1) }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { OutlinedButton(onClick = { more = true }) { Text("Load more") } }
    }

    when (sheet) {
        "bulk-status" -> ChoiceSheet("Set status", options?.objs("statuses").orEmpty().map { Choice(it.optString("name"), it.optString("name"), tint(it.str("color"))) }, null, b, onClose = { sheet = null }) { sheet = null; bulk("change_status", it) }
        "bulk-label" -> ChoiceSheet("Set label", options?.objs("labels").orEmpty().map { Choice(it.optString("name"), it.optString("name"), labelTintOf(options, it.optString("name"))) }, null, b, onClose = { sheet = null }) { sheet = null; bulk("change_label", it) }
        "bulk-assign" -> ChoiceSheet("Assign to", options?.objs("users").orEmpty().map { Choice(it.optInt("id").toString(), it.optString("name")) }, null, b, onClose = { sheet = null }) { sheet = null; bulk("assign", it) }
        "bulk-list" -> ChoiceSheet("Move to list", options?.objs("lists").orEmpty().map { Choice(it.optInt("id").toString(), it.optString("name")) }, null, b, onClose = { sheet = null }) { sheet = null; bulk("move_list", it) }
    }
    if (askDelete) SwalConfirm(
        title = "Delete ${selected.size} lead${if (selected.size == 1) "" else "s"}?", text = "They will be removed from the project.", confirmText = "Yes, delete",
        onConfirm = { askDelete = false; bulk("delete", null) }, onCancel = { askDelete = false },
    )
}

@Composable
private fun BulkButton(text: String, tint: Color = Color.White, onClick: () -> Unit) {
    Text(text, color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .14f)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp))
}

/** One lead as a card: avatar, name and number, the label; status / source / list chips; the next follow-up; who has it and Call / WhatsApp buttons. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun LeadCard(r: JSONObject, options: JSONObject?, flat: Boolean = false, selected: Boolean? = null, onLongClick: (() -> Unit)? = null, onMore: (() -> Unit)?, onClick: () -> Unit) {
    val context = LocalContext.current
    val status = r.str("status") ?: "New"
    val sc = statusTintOf(options, r.str("status"))
    val lc = r.str("label")?.let { labelTintOf(options, it) }
    val phone = r.str("phone")
    Column(
        Modifier.padding(horizontal = if (flat) 0.dp else 16.dp, vertical = if (flat) 4.dp else 7.dp).fillMaxWidth()
            .then(if (flat) Modifier else Modifier.shadow(6.dp, RoundedCornerShape(22.dp)))
            .clip(RoundedCornerShape(22.dp)).background(if (selected == true) b0(sc) else if (flat) Color(0xFFF7F5F1) else Color.White)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) Box(Modifier.padding(end = 10.dp).size(24.dp).clip(CircleShape).background(if (selected) KitInk else Color(0xFFE6E3DD)), contentAlignment = Alignment.Center) { if (selected) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
            Box(Modifier.size(46.dp).clip(CircleShape).background(sc.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Text(r.optString("name").trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }, color = sc, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(phone ?: r.str("email") ?: "No contact", color = KitMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (lc != null) Chip(r.optString("label"), lc)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(status, sc)
            if (r.optBoolean("converted")) Chip("Converted", KitGood)
            r.str("source")?.let { Chip(it, KitMuted) }
            r.str("list")?.let { Chip(it, Blue) }
        }
        whenLabel(r.str("next_followup"))?.let {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Amber.copy(alpha = .1f)).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("◷", color = Amber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${r.str("next_type") ?: "Follow-up"} · $it", color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val owner = r.str("assignee")
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (owner != null) { com.example.gavanhomes.ui.common.Initials(owner, size = 26.dp); Text(owner, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp)) }
                else Text("Unassigned", color = KitMuted, fontSize = 12.sp)
            }
            if (phone != null) {
                Text("Call", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitGood)
                    .clickable { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phone.filter { c -> c.isDigit() || c == '+' }))) } }.padding(horizontal = 16.dp, vertical = 8.dp))
                Spacer(Modifier.size(8.dp))
                Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF25A244)).clickable {
                    val d = phone.filter { c -> c.isDigit() }.trimStart('0'); val n = if (d.length == 10) "91$d" else d
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$n"))) }
                }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

private fun b0(c: Color) = c.copy(alpha = .14f)

@Composable
internal fun Chip(text: String, c: Color) {
    Text(text, color = c, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 4.dp))
}

// ───────────────────────────── follow-ups ─────────────────────────────

private val FollowupSorts = listOf("newest" to "Newest first", "oldest" to "Oldest first", "next" to "Next follow-up")

private fun followupGroups(options: JSONObject?): List<FilterGroup> = listOfNotNull(
    FilterGroup("view", "View", listOf(FilterOption("today", "Today"), FilterOption("scheduled", "Scheduled"))),
    options?.objs("followup_statuses")?.takeIf { it.isNotEmpty() }?.let { s -> FilterGroup("status", "Status", s.map { FilterOption(it.optString("slug"), it.optString("name"), null, tint(it.str("color"))) }) },
    options?.objs("followup_types")?.takeIf { it.isNotEmpty() }?.let { t -> FilterGroup("type", "Type", t.map { FilterOption(it.optString("name"), it.optString("name")) }) },
    options?.objs("users")?.takeIf { it.isNotEmpty() }?.let { u -> FilterGroup("staff", "Staff", u.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
    FilterGroup("more", "More", listOf(FilterOption("mine", "Mine only"))),
)

private fun followupQuery(sel: Map<String, Set<String>>, sort: Int): String = buildString {
    sel["view"]?.firstOrNull()?.let { append("&view=").append(it) }
    listOf("status", "type", "staff").forEach { k -> sel[k]?.takeIf { it.isNotEmpty() }?.let { append("&$k=").append(URLEncoder.encode(it.joinToString(","), "UTF-8")) } }
    if ("mine" in sel["more"].orEmpty()) append("&my_only=1")
    append("&sort=").append(FollowupSorts[sort.coerceIn(0, FollowupSorts.lastIndex)].first)
}

/** Type → the colour and glyph of its round marker on the timeline. */
private fun typeStyle(type: String?): Pair<Color, String> = when (type?.lowercase()) {
    "call" -> KitGood to "✆"
    "site visit" -> Amber to "⌂"
    "meeting" -> Blue to "☻"
    "whatsapp" -> Color(0xFF25A244) to "✉"
    "email" -> Blue to "@"
    else -> Color(0xFF6A4FB3) to "◷"
}

/** One dropdown-style filter pill, like the website's follow-up filters. */
@Composable
private fun FilterPill(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        label + " ▾", color = if (active) Color.White else KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.shadow(if (active) 0.dp else 2.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(if (active) accent else Color.White).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowupList(api: ApiClient, b: Branding, uuid: String, version: Int, options: JSONObject?, onOpen: (Int, String) -> Unit) {
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    val rows = remember { mutableStateListOf<JSONObject>() }
    var page by remember { mutableIntStateOf(1) }
    var lastPage by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var editF by remember { mutableStateOf<JSONObject?>(null) }
    var statusF by remember { mutableStateOf<JSONObject?>(null) }
    var deleteF by remember { mutableStateOf<JSONObject?>(null) }
    var pickLead by remember { mutableStateOf(false) }
    var scheduleFor by remember { mutableStateOf<JSONObject?>(null) }
    fun act(success: String, call: () -> Unit) {
        scope.launch {
            try { withContext(Dispatchers.IO) { call() }; android.widget.Toast.makeText(context, success, android.widget.Toast.LENGTH_SHORT).show(); attempt++ }
            catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "Could not save", android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    val fq = followupQuery(sel, sort)
    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) {
                api.get("/projects/$uuid/lead-center/followups?page=$p&search=${URLEncoder.encode(query.trim(), "UTF-8")}$fq")
            }
            if (p == 1) rows.clear()
            rows.addAll(r.objs("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1); total = r.optInt("total")
        } catch (e: Exception) { error = e.message ?: "Could not load" }
        loading = false
    }
    LaunchedEffect(fq, version, attempt) { load(1) }
    LaunchedEffect(query) { delay(350); load(1) }

    FilterBar(
        query, { query = it }, "Search lead, phone or remarks…",
        groups = followupGroups(options), selected = sel,
        onApply = { m -> sel = m.mapValues { (k, v) -> if (k == "view") v.take(1).toSet() else v }.filterValues { it.isNotEmpty() } },
        countWith = { m -> if (m == sel) total else -1 },
        sortOptions = FollowupSorts.map { it.second }, sortIndex = sort, onSort = { sort = it },
    )
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$total follow-up${if (total == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
        Text("+  Schedule follow-up", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(b.base).clickable { pickLead = true }.padding(horizontal = 16.dp, vertical = 9.dp))
    }

    rows.forEach { f ->
        FollowupCard(f, options, showLead = true, onClick = { detail = f }, onStatus = if (f.optBoolean("can_status")) ({ statusF = f }) else null,
            onEdit = if (f.optBoolean("can_edit")) ({ editF = f }) else null, onDelete = if (f.optBoolean("can_edit")) ({ deleteF = f }) else null)
    }

    when {
        loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
        error != null && rows.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
        }
        rows.isEmpty() -> Text("No follow-ups here.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
    }
    if (rows.isNotEmpty() && page < lastPage && !loading) {
        var more by remember(page) { mutableStateOf(false) }
        LaunchedEffect(more) { if (more) load(page + 1) }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { OutlinedButton(onClick = { more = true }) { Text("Load more") } }
    }

    if (pickLead) LeadPickSheet(api, uuid, onClose = { pickLead = false }) { lead -> pickLead = false; scheduleFor = lead }
    scheduleFor?.let { lead -> FollowupSheet(b, options, JSONObject(), onClose = { scheduleFor = null }) { body -> scheduleFor = null; act("Follow-up scheduled") { api.send("POST", "/lead-center/${lead.optInt("id")}/followups", body) } } }
    detail?.let { f -> FollowupDetailSheet(b, options, f, onClose = { detail = null }, onOpenLead = { detail = null; onOpen(f.optInt("lead_id"), "followups") }, onStatus = { detail = null; statusF = f }, onEdit = { detail = null; editF = f }, onDelete = { detail = null; deleteF = f }) }
    editF?.let { f -> FollowupSheet(b, options, f, onClose = { editF = null }) { body -> editF = null; act("Follow-up saved") { api.send("PUT", "/lead-center/${f.optInt("lead_id")}/followups/${f.optInt("id")}", body) } } }
    statusF?.let { f -> StatusSheet(b, options, f, onClose = { statusF = null }) { st, note -> statusF = null; act("Follow-up marked $st") { api.send("POST", "/lead-center/${f.optInt("lead_id")}/followups/${f.optInt("id")}/status", JSONObject().put("status", st).put("notes", note)) } } }
    deleteF?.let { f -> SwalConfirm(title = "Delete this follow-up?", text = "Its reminder task goes too, unless it was already completed.", confirmText = "Yes, delete it", onConfirm = { deleteF = null; act("Follow-up deleted") { api.delete("/lead-center/${f.optInt("lead_id")}/followups/${f.optInt("id")}") } }, onCancel = { deleteF = null }) }
}

/**
 * A follow-up like the website's timeline entry: a round marker for its type, then a card with the TYPE pill and the lead, who logged it and when,
 * the remarks, a green "Next:" chip, and — right on the card — the status pill (tap to change), edit and delete.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FollowupCard(
    f: JSONObject, options: JSONObject?, showLead: Boolean = false, onClick: (() -> Unit)? = null,
    onStatus: (() -> Unit)? = null, onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    val st = f.optString("status")
    val sc = tint(options?.objs("followup_statuses")?.firstOrNull { it.optString("slug") == st }?.str("color"), when (st) { "completed" -> KitGood; "missed" -> KitDanger; "rescheduled" -> Amber; else -> Blue })
    val (tc, glyph) = typeStyle(f.str("type"))
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Box(Modifier.padding(top = 4.dp).size(40.dp).clip(CircleShape).background(tc.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(glyph, color = tc, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
        Column(
            Modifier.padding(start = 10.dp).weight(1f).shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text((f.str("type") ?: "Follow-up").uppercase(), color = tc, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.5.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(tc.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 5.dp))
                if (showLead) Text("👤 " + (f.str("lead_name") ?: "Lead"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp))
            }
            Text(listOfNotNull(whenLabel(f.str("created_at")), f.str("created_by")?.uppercase()).joinToString(" · "), color = KitMuted, fontSize = 11.sp)
            f.str("remarks")?.let { Text(it, color = KitInk, fontSize = 14.sp, lineHeight = 20.sp) }
            f.str("status_remarks")?.let { Text(it, color = KitMuted, fontSize = 12.sp, lineHeight = 17.sp) }
            whenLabel(f.str("at"))?.let {
                Text("⏰ Next: $it", color = KitGood, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(KitGood.copy(alpha = .1f)).padding(horizontal = 10.dp, vertical = 6.dp))
            }
            f.str("assignee")?.let { Text("Assigned to $it", color = KitMuted, fontSize = 12.sp) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    st.uppercase() + if (onStatus != null) " ▾" else "", color = sc, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(sc.copy(alpha = .12f)).then(if (onStatus != null) Modifier.clickable { onStatus() } else Modifier).padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (onEdit != null) Text("✎", color = KitInk, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).background(Color(0xFFF1EFEA)).clickable { onEdit() }.padding(horizontal = 11.dp, vertical = 6.dp))
                if (onDelete != null) Text("🗑", fontSize = 15.sp, modifier = Modifier.clip(CircleShape).background(KitDanger.copy(alpha = .1f)).clickable { onDelete() }.padding(horizontal = 9.dp, vertical = 6.dp))
            }
            trailing?.invoke()
        }
    }
}

// ───────────────────────────── shared pieces ─────────────────────────────

internal class Choice(val value: String, val label: String, val tint: Color? = null)

/** A bottom sheet with one choice per row (✓ on the current one); [allowNone] adds a "None" row that returns "". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoiceSheet(title: String, choices: List<Choice>, current: String?, b: Branding, allowNone: Boolean = false, noneLabel: String = "None", onClose: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            (if (allowNone) listOf(Choice("", noneLabel)) else emptyList<Choice>()).plus(choices).forEach { c ->
                val on = current == c.value
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else Color(0xFFF7F5F1)).clickable { onPick(c.value) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    c.tint?.let { Box(Modifier.size(10.dp).clip(CircleShape).background(it)); Spacer(Modifier.size(12.dp)) }
                    Text(c.label, color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (on) Text("✓", color = b.base, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/** A follow-up opened from the Follow-ups tab: who it is for (call / WhatsApp), when, status, notes and its actions — without leaving the list. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FollowupDetailSheet(b: Branding, options: JSONObject?, f: JSONObject, onClose: () -> Unit, onOpenLead: () -> Unit, onStatus: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val st = f.optString("status")
    val sc = tint(options?.objs("followup_statuses")?.firstOrNull { it.optString("slug") == st }?.str("color"), when (st) { "completed" -> KitGood; "missed" -> KitDanger; "rescheduled" -> Amber; else -> Blue })
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.str("lead_name") ?: "Lead", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
                    Text(listOfNotNull(f.str("type"), whenLabel(f.str("at"))).joinToString(" · "), color = KitMuted, fontSize = 13.sp)
                }
                Chip(st.replaceFirstChar { it.uppercase() }, sc)
            }
            f.str("lead_phone")?.let { com.example.gavanhomes.ui.tasks.PhoneActions(it) }
            f.str("assignee")?.let { Text("👤 Assigned to $it", color = KitMuted, fontSize = 13.sp) }
            f.str("remarks")?.let { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp)) { Text("Remarks", color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(it, color = KitInk, fontSize = 14.sp, lineHeight = 20.sp) } }
            f.str("status_remarks")?.let { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp)) { Text("Status notes", color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(it, color = KitInk, fontSize = 13.sp, lineHeight = 19.sp) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (f.optBoolean("can_status")) com.example.gavanhomes.ui.leads.SmallAction("Change status", b.base, onStatus)
                if (f.optBoolean("can_edit")) com.example.gavanhomes.ui.leads.SmallAction("Edit", KitInk, onEdit)
                com.example.gavanhomes.ui.leads.SmallAction("Open lead", KitInk, onOpenLead)
                if (f.optBoolean("can_edit")) com.example.gavanhomes.ui.leads.SmallAction("Delete", KitDanger, onDelete)
            }
        }
    }
}

/** The ⚙ Settings pill that sits above a module's tabs (instead of a Settings tab); [back] true shows a "‹ Back" pill in its place. */
@Composable
internal fun SettingsEntry(open: Boolean, title: String, accent: Color, onToggle: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (open) Text(title, color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        Text(
            if (open) "‹  Back" else "⚙  Settings", color = if (open) Color.White else KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.shadow(if (open) 0.dp else 2.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(if (open) accent else Color.White).clickable { onToggle() }.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

