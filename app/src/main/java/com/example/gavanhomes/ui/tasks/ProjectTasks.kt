package com.example.gavanhomes.ui.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.roundToInt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.common.SearchBox
import com.example.gavanhomes.ui.common.SegTabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Amber = Color(0xFFC47A1A)
private val Blue = Color(0xFF1E5FAE)
private val Orange = Color(0xFFE0701B)

/** The colour of a task status, by its name. */
internal fun statusTint(name: String?): Color = when (name?.lowercase()) {
    "completed" -> KitGood
    "in progress" -> Blue
    "pending" -> Amber
    "overdue" -> KitDanger
    "cancelled" -> KitMuted
    else -> KitMuted
}

internal fun priorityTint(p: String?): Color = when (p?.lowercase()) {
    "urgent" -> KitDanger
    "high" -> Orange
    "medium" -> Blue
    else -> KitMuted
}

private val Shown = DateTimeFormatter.ofPattern("d MMM yyyy")
internal fun prettyDay(s: String?): String? = runCatching { LocalDate.parse(s!!.take(10)).format(Shown) }.getOrNull()

internal fun JSONObject.objs(key: String): List<JSONObject> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
internal fun JSONObject.names(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

private class Quick(val key: String, val label: String)
private val QuickFilters = listOf(Quick("today", "Today"), Quick("tomorrow", "Tomorrow"), Quick("this_week", "This week"), Quick("overdue", "Overdue"), Quick("mine", "My tasks"), Quick("high", "High priority"))

/**
 * The project's Tasks module, like the website's Tasks tab: a Dashboard (status and priority numbers, quick filters, due today),
 * the task List (search, quick filters, status filter) and the Board (a column per status).
 * Data: GET /projects/{uuid}/task-center/overview · list · board.
 */
@Composable
fun ProjectTasks(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit, onNew: () -> Unit, onEdit: (Int) -> Unit = {}, onOpenAt: (Int, String) -> Unit = { id, _ -> onOpen(id) }) {
    var view by remember { mutableIntStateOf(0) }                    // 0 Dashboard · 1 List · 2 Board
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    // the list's filter, kept here so it survives a switch to the Board and back; a "Quick look" tap on the dashboard sets it too
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }

    var pickers by remember { mutableStateOf<JSONObject?>(null) }       // departments, types, people — for the filter sheet
    LaunchedEffect(version, attempt) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/task-center/overview") } }
        catch (e: Exception) { error = e.message ?: "Could not load the tasks" }
    }
    LaunchedEffect(uuid) { pickers = runCatching { withContext(Dispatchers.IO) { api.detail("/projects/$uuid/task-center/options") } }.getOrNull() }

    SegTabs(listOf("Dashboard", "List", "Board", "History"), view) { view = it }

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
        0 -> TaskDashboard(o, canCreate, onNew, onOpen) { q -> sel = quickSelection(q); query = ""; view = 1 }
        1 -> TaskList(api, b, uuid, version, o, pickers, sel, { sel = it }, query, { query = it }, sort, { sort = it }, onOpen, canCreate, onNew, onChanged = { attempt++ }, onEdit = onEdit, onOpenAt = onOpenAt)
        2 -> TaskBoard(api, b, uuid, version, o.optJSONObject("can")?.optBoolean("edit") == true, onChanged = { attempt++ }, onOpen = onOpen)
        else -> TaskHistory(api, b, uuid, version, onOpen)
    }
}

// ───────────────────────────── dashboard ─────────────────────────────

@Composable
private fun TaskDashboard(o: JSONObject, canCreate: Boolean, onNew: () -> Unit, onOpen: (Int) -> Unit, onQuick: (String) -> Unit) {
    if (canCreate) Text(
        "+  New task", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitInk).clickable { onNew() }.padding(vertical = 14.dp),
    )

    // status numbers
    val tiles = listOf(Triple("Total", o.optInt("total"), KitInk)) + o.objs("statuses").map { Triple(it.optString("name"), it.optInt("count"), statusTint(it.optString("name"))) }
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, n, c) ->
                    Column(
                        Modifier.weight(1f).shadow(5.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(n.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = c)
                        Text(label, fontSize = 11.sp, color = KitMuted, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    // overall completion
    val rate = o.optInt("completion_rate")
    Card("Completion", "${o.optInt("pending_high")} high-priority tasks still open") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rate%", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = KitGood)
            Text("of all tasks completed", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }
        Meter(rate / 100f, KitGood)
    }

    // priorities
    Card("By priority", "Tasks per priority and how many are done") {
        o.objs("priorities").chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    val c = priorityTint(p.optString("priority"))
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = .09f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(p.optString("priority"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c)
                        Text(p.optInt("total").toString(), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = KitInk)
                        Meter(p.optInt("rate") / 100f, c)
                        Text("${p.optInt("completed")} done · ${p.optInt("overdue")} overdue", fontSize = 11.sp, color = KitMuted)
                    }
                }
            }
        }
    }

    // quick filters
    val q = o.optJSONObject("quick")
    Card("Quick look", "Tap one to see those tasks") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickFilters.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { f ->
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onQuick(f.key) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(f.label, color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text((q?.optInt(f.key) ?: 0).toString(), color = if (f.key == "overdue") KitDanger else KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }

    val today = o.objs("due_today")
    if (today.isNotEmpty()) Card("Due today", "${today.size} task${if (today.size == 1) "" else "s"} to finish") {
        today.forEach { TaskCard(it, flat = true) { onOpen(it.optInt("id")) } }
    }
}

// ───────────────────────────── list ─────────────────────────────

private val Sorts = listOf("" to "Due soonest", "due_desc" to "Due latest", "newest" to "Newest", "oldest" to "Oldest", "priority" to "Priority", "name" to "Name A–Z")

/** What a tap on the dashboard's "Quick look" selects in the filter. */
internal fun quickSelection(key: String): Map<String, Set<String>> = when (key) {
    "mine" -> mapOf("more" to setOf("mine"))
    "high" -> mapOf("priority" to setOf("High", "Urgent"))
    else -> mapOf("due" to setOf(key))
}

/** The server query for the filter the user has built (the website's filter bar: status, priority, due, department, type, people, mine, created by me). */
private fun queryOf(sel: Map<String, Set<String>>, sort: Int): String = buildString {
    fun add(param: String, key: String) { sel[key]?.takeIf { it.isNotEmpty() }?.let { append("&").append(param).append("=").append(it.joinToString(",")) } }
    add("status_id", "status"); add("priority", "priority"); add("department_id", "department"); add("type", "type"); add("assignee_id", "people")
    sel["due"]?.firstOrNull()?.let { append("&due=").append(it) }
    if ("mine" in sel["more"].orEmpty()) append("&mine=1")
    if ("created" in sel["more"].orEmpty()) append("&created_by_me=1")
    Sorts.getOrNull(sort)?.first?.takeIf { it.isNotEmpty() }?.let { append("&sort=").append(it) }
}

/** The filter page's categories, with counts where the server told us them. */
private fun filterGroups(o: JSONObject, pickers: JSONObject?): List<FilterGroup> {
    val q = o.optJSONObject("quick")
    fun dueOpt(v: String, label: String, count: Boolean = true, color: Color? = null) = FilterOption(v, label, if (count) q?.optInt(v) else null, color)
    return listOfNotNull(
        FilterGroup("due", "Due", listOf(dueOpt("today", "Today"), dueOpt("tomorrow", "Tomorrow"), dueOpt("this_week", "This week"), dueOpt("overdue", "Overdue", color = KitDanger),
            dueOpt("yesterday", "Yesterday", false), dueOpt("next_week", "Next week", false), dueOpt("this_month", "This month", false), dueOpt("next_month", "Next month", false), dueOpt("last_month", "Last month", false))),
        FilterGroup("status", "Status", o.objs("statuses").map { FilterOption(it.optInt("id").toString(), it.optString("name"), it.optInt("count"), statusTint(it.optString("name"))) }),
        FilterGroup("priority", "Priority", listOf("Urgent", "High", "Medium", "Low").map { FilterOption(it, it, null, priorityTint(it)) }),
        pickers?.objs("departments")?.takeIf { it.isNotEmpty() }?.let { d -> FilterGroup("department", "Department", d.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
        pickers?.objs("types")?.takeIf { it.isNotEmpty() }?.let { t -> FilterGroup("type", "Type", t.map { FilterOption(it.optString("key"), it.optString("label")) }) },
        pickers?.objs("people")?.takeIf { it.isNotEmpty() }?.let { p -> FilterGroup("people", "Assigned to", p.map { FilterOption(it.optInt("id").toString(), it.optString("name")) }) },
        FilterGroup("more", "More", listOf(FilterOption("mine", "My tasks", q?.optInt("mine")), FilterOption("created", "Created by me"))),
    )
}

/**
 * The task list: the app's usual search + Sort | Filter bar (a full filter page with a category rail), then the cards, 20 at a time.
 * Press and hold a task to select several and set their status or priority, or delete them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskList(
    api: ApiClient, b: Branding, uuid: String, version: Int, o: JSONObject, pickers: JSONObject?,
    sel: Map<String, Set<String>>, setSel: (Map<String, Set<String>>) -> Unit, query: String, setQuery: (String) -> Unit, sort: Int, setSort: (Int) -> Unit,
    onOpen: (Int) -> Unit, canCreate: Boolean, onNew: () -> Unit, onChanged: () -> Unit, onEdit: (Int) -> Unit, onOpenAt: (Int, String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val rows = remember { mutableStateListOf<JSONObject>() }
    val selected = remember { mutableStateListOf<Int>() }
    var page by remember { mutableIntStateOf(1) }
    var lastPage by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<String?>(null) }           // "export" · "bulk-status" · "bulk-priority"
    var askDelete by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    // the ⋮ menu of a task row: View details · Mark as complete · Edit · Add notes · Add attachment · View history · Assign · Delete
    var rowMenu by remember { mutableStateOf<JSONObject?>(null) }
    var noteFor by remember { mutableStateOf<JSONObject?>(null) }
    var noteText by remember { mutableStateOf("") }
    var deleteRow by remember { mutableStateOf<JSONObject?>(null) }
    var attachTo by remember { mutableStateOf<Int?>(null) }
    val attachPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        val target = attachTo; attachTo = null
        val f = uri?.let { com.example.gavanhomes.ui.projects.form.readPicked(context, it) }
        if (target != null && f != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { api.sendForm("/task-center/$target/documents", listOf(com.example.gavanhomes.data.FormPart("document", fileName = f.name, mime = f.mime, bytes = f.bytes))) }
                android.widget.Toast.makeText(context, "File attached", android.widget.Toast.LENGTH_SHORT).show(); onChanged(); attempt++
            } catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "Could not attach the file", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    fun rowAction(success: String, call: () -> Unit) {
        scope.launch {
            try { withContext(Dispatchers.IO) { call() }; android.widget.Toast.makeText(context, success, android.widget.Toast.LENGTH_SHORT).show(); onChanged(); attempt++ }
            catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "Could not do that", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    val can = o.optJSONObject("can")
    val canEdit = can?.optBoolean("edit") == true
    val canDelete = can?.optBoolean("delete") == true
    val filterQuery = queryOf(sel, sort)

    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/task-center/list?page=$p&search=${URLEncoder.encode(query.trim(), "UTF-8")}$filterQuery") }
            if (p == 1) rows.clear()
            rows.addAll(r.objs("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1); total = r.optInt("total")
        } catch (e: Exception) { error = e.message ?: "Could not load" }
        loading = false
    }
    LaunchedEffect(filterQuery, version, attempt) { selected.clear(); load(1) }
    LaunchedEffect(query) { delay(350); load(1) }

    // Android 13+ asks once before an app may show notifications
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    /** Builds the file on the server for the current filters, saves it to Downloads/GavanHomes and announces it in the notification bar (tap to open, or Share). */
    fun export(format: String) {
        if (!com.example.gavanhomes.ui.common.Downloads.canNotify(context)) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        exporting = true
        val kind = if (format == "pdf") "PDF" else "Excel"
        android.widget.Toast.makeText(context, "Preparing your $kind export…", android.widget.Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val (name, bytes) = withContext(Dispatchers.IO) {
                    api.download("/projects/$uuid/task-center/export?format=$format&search=${URLEncoder.encode(query.trim(), "UTF-8")}$filterQuery")
                }
                val mime = if (format == "pdf") "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                // saved to the phone's Downloads folder (Downloads/GavanHomes), and announced in the notification bar — tap it to open the file
                val uri = withContext(Dispatchers.IO) { com.example.gavanhomes.ui.common.Downloads.save(context, name, mime, bytes) }
                com.example.gavanhomes.ui.common.Downloads.notifyReady(context, "$kind export ready", name, mime, uri)
                android.widget.Toast.makeText(context, "$kind export saved to Downloads/GavanHomes — $name", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "Could not export", android.widget.Toast.LENGTH_LONG).show() }
            exporting = false
        }
    }

    fun bulk(action: String, value: Any?) {
        val ids = selected.toList()
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/task-center/bulk", JSONObject().put("ids", JSONArray(ids)).put("action", action).put("value", value ?: JSONObject.NULL)) }
                android.widget.Toast.makeText(context, "${ids.size} task${if (ids.size == 1) "" else "s"} updated", android.widget.Toast.LENGTH_SHORT).show()
                selected.clear(); onChanged(); attempt++
            } catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "Could not update", android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    FilterBar(
        query, setQuery, "Search tasks…",
        groups = filterGroups(o, pickers), selected = sel,
        onApply = { m -> setSel(m.mapValues { (k, v) -> if (k == "due") v.take(1).toSet() else v }.filterValues { it.isNotEmpty() }) },
        countWith = { m -> if (m == sel) total else -1 },
        sortOptions = Sorts.map { it.second }, sortIndex = sort, onSort = setSort,
    )

    Row(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$total task${if (total == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        ActionPill(if (exporting) "Exporting…" else "Export", ActionIcon.Download, filled = false, accent = b.base, enabled = !exporting) { sheet = "export" }
        if (canCreate) { Spacer(Modifier.size(8.dp)); ActionPill("New task", ActionIcon.Plus, filled = true, accent = b.base) { onNew() } }
    }

    // bulk bar
    if (selected.isNotEmpty()) Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KitInk).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.size} selected", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("Select all", color = Color.White.copy(alpha = .8f), fontSize = 12.sp, modifier = Modifier.clickable { selected.clear(); selected.addAll(rows.map { it.optInt("id") }) }.padding(6.dp))
            Text("Cancel", color = Color.White.copy(alpha = .8f), fontSize = 12.sp, modifier = Modifier.clickable { selected.clear() }.padding(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canEdit) { BulkButton("Status") { sheet = "bulk-status" }; BulkButton("Priority") { sheet = "bulk-priority" } }
            if (canDelete) BulkButton("Delete", KitDanger) { askDelete = true }
        }
    }

    rows.forEach { r ->
        val id = r.optInt("id")
        TaskCard(
            r, selected = if (selected.isEmpty()) null else id in selected,
            onLongClick = if (canEdit || canDelete) ({ if (id !in selected) selected.add(id) }) else null,
            onMore = if (selected.isEmpty()) ({ rowMenu = r }) else null,
        ) { if (selected.isNotEmpty()) { if (id in selected) selected.remove(id) else selected.add(id) } else onOpen(id) }
    }
    if (rows.isNotEmpty() && selected.isEmpty() && (canEdit || canDelete)) Text("Press and hold a task to select several", color = KitMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

    when {
        loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
        error != null && rows.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
        }
        rows.isEmpty() -> Text("No tasks match.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
        loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
    }
    if (rows.isNotEmpty() && page < lastPage && !loading) {
        var more by remember(page) { mutableStateOf(false) }
        LaunchedEffect(more) { if (more) load(page + 1) }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { OutlinedButton(onClick = { more = true }) { Text("Load more") } }
    }

    // ── the ⋮ menu of a task ──
    rowMenu?.let { t ->
        val tid = t.optInt("id")
        val done = t.optString("status") == "Completed"
        ModalBottomSheet(onDismissRequest = { rowMenu = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = KitInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Choose what to do with this task", color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                MenuRow("◉", "View task details") { rowMenu = null; onOpen(tid) }
                if (canEdit && !done) MenuRow("✓", "Mark as complete", KitGood) {
                    rowMenu = null
                    val completed = o.objs("statuses").firstOrNull { it.optString("name") == "Completed" }?.optInt("id") ?: 0
                    rowAction("Task marked complete") { api.send("POST", "/task-center/$tid/status", JSONObject().put("status_id", completed)) }
                }
                if (canEdit) MenuRow("✎", "Edit task") { rowMenu = null; onEdit(tid) }
                if (canEdit) MenuRow("☰", "Add notes") { rowMenu = null; noteText = ""; noteFor = t }
                if (canEdit) MenuRow("＋", "Add attachment") { rowMenu = null; attachTo = tid; attachPicker.launch("*/*") }
                MenuRow("◷", "View history") { rowMenu = null; onOpenAt(tid, "activity") }
                if (canEdit) MenuRow("⇄", "Assign") { rowMenu = null; onOpenAt(tid, "assign") }
                if (canDelete) { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEFECE6))); MenuRow("✕", "Delete", KitDanger) { rowMenu = null; deleteRow = t } }
            }
        }
    }
    noteFor?.let { t ->
        ModalBottomSheet(onDismissRequest = { noteFor = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add a note", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text(t.optString("name"), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                com.example.gavanhomes.ui.projects.form.Input("Note", noteText, { noteText = it }, singleLine = false)
                Text("Save note", color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (noteText.isBlank()) KitMuted else KitInk).clickable(enabled = noteText.isNotBlank()) {
                        val tid = t.optInt("id"); val text = noteText.trim(); noteFor = null
                        rowAction("Note added") { api.send("POST", "/task-center/$tid/comments", JSONObject().put("text", text)) }
                    }.padding(vertical = 14.dp))
            }
        }
    }
    deleteRow?.let { t -> com.example.gavanhomes.ui.common.SwalConfirm(
        title = "Delete this task?", text = t.optString("name"), confirmText = "Yes, delete it",
        onConfirm = { deleteRow = null; rowAction("Task deleted") { api.delete("/task-center/${t.optInt("id")}") } }, onCancel = { deleteRow = null },
    ) }

    when (sheet) {
        "export" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Export tasks", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
                Text("Exports the $total task${if (total == 1) "" else "s"} that match your search and filters.", color = KitMuted, fontSize = 13.sp)
                // what the file will be limited to, so it is clear the filters apply
                val applied = filterGroups(o, pickers).flatMap { g -> sel[g.key].orEmpty().mapNotNull { v -> g.options.firstOrNull { it.value == v }?.let { "${g.title}: ${it.label}" } } } +
                    listOfNotNull(query.trim().takeIf { it.isNotEmpty() }?.let { "Search: “$it”" })
                if (applied.isEmpty()) Text("No filters — every task will be exported.", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    applied.forEach { Text(it, color = KitInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1EFEB)).padding(horizontal = 12.dp, vertical = 7.dp)) }
                }
                SheetRow("Excel sheet (.xlsx)", KitGood) { sheet = null; export("excel") }
                SheetRow("PDF report", KitDanger) { sheet = null; export("pdf") }
            }
        }
        "bulk-status" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set status of ${selected.size}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
                o.objs("statuses").forEach { s -> SheetRow(s.optString("name"), statusTint(s.optString("name"))) { sheet = null; bulk("status", s.optInt("id")) } }
            }
        }
        "bulk-priority" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set priority of ${selected.size}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
                listOf("Urgent", "High", "Medium", "Low").forEach { p -> SheetRow(p, priorityTint(p)) { sheet = null; bulk("priority", p) } }
            }
        }
    }
    if (askDelete) com.example.gavanhomes.ui.common.SwalConfirm(
        title = "Delete ${selected.size} task${if (selected.size == 1) "" else "s"}?", text = "They will be removed from the project.", confirmText = "Yes, delete",
        onConfirm = { askDelete = false; bulk("delete", null) }, onCancel = { askDelete = false },
    )
}

private enum class ActionIcon { Plus, Download }

/**
 * The list's two actions: **New task** as a solid brand-colour pill, **Export** as a white pill with a brand outline — each with a small drawn icon
 * (a plus, a download arrow) so they read at a glance.
 */
@Composable
private fun ActionPill(label: String, icon: ActionIcon, filled: Boolean, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val fg = if (filled) Color.White else accent
    Row(
        Modifier.then(if (filled) Modifier.shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = accent, spotColor = accent) else Modifier)
            .clip(RoundedCornerShape(16.dp)).background(if (filled) accent else Color.White)
            .then(if (filled) Modifier else Modifier.border(1.5.dp, accent.copy(alpha = .55f), RoundedCornerShape(16.dp)))
            .clickable(enabled = enabled) { onClick() }.padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(18.dp)) {
            val k = size.width / 24f
            val st = Stroke(width = 2.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (icon) {
                ActionIcon.Plus -> { drawLine(fg, Offset(12f * k, 5f * k), Offset(12f * k, 19f * k), st.width, StrokeCap.Round); drawLine(fg, Offset(5f * k, 12f * k), Offset(19f * k, 12f * k), st.width, StrokeCap.Round) }
                ActionIcon.Download -> {
                    drawLine(fg, Offset(12f * k, 3f * k), Offset(12f * k, 15f * k), st.width, StrokeCap.Round)
                    drawPath(Path().apply { moveTo(6.5f * k, 10f * k); lineTo(12f * k, 15.5f * k); lineTo(17.5f * k, 10f * k) }, fg, style = st)
                    drawPath(Path().apply { moveTo(4f * k, 17f * k); lineTo(4f * k, 20f * k); lineTo(20f * k, 20f * k); lineTo(20f * k, 17f * k) }, fg, style = st)
                }
            }
        }
        Text(label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun MenuRow(glyph: String, label: String, tint: Color = KitInk, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = if (tint == KitInk) .08f else .12f)), contentAlignment = Alignment.Center) { Text(glyph, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun BulkButton(text: String, tint: Color = Color.White, onClick: () -> Unit) {
    Text(text, color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .14f)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp))
}

@Composable
private fun SheetRow(label: String, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onClick() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(tint))
        Text(label, color = KitInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
    }
}

// ───────────────────────────── history ─────────────────────────────

/** Everything that happened to the project's tasks, newest first (GET …/task-center/activity): tap an entry to open that task. */
@Composable
private fun TaskHistory(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit) {
    val rows = remember { mutableStateListOf<JSONObject>() }
    var page by remember { mutableIntStateOf(1) }
    var lastPage by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/task-center/activity?page=$p") }
            if (p == 1) rows.clear()
            rows.addAll(r.objs("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1); total = r.optInt("total")
        } catch (e: Exception) { error = e.message ?: "Could not load the history" }
        loading = false
    }
    LaunchedEffect(version, attempt) { load(1) }

    Text("$total event${if (total == 1) "" else "s"} across all tasks", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    Card("History", "Who did what to the project's tasks, and when") {
        rows.forEachIndexed { i, h ->
            val tint = when (h.optString("dot")) { "ts-dot-create" -> KitGood; "ts-dot-delete" -> KitDanger; else -> Amber }
            Row(Modifier.height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).clickable(enabled = h.optInt("task_id") > 0) { onOpen(h.optInt("task_id")) }) {
                Column(Modifier.width(18.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 5.dp).size(12.dp).clip(CircleShape).background(tint))
                    if (i < rows.lastIndex) Box(Modifier.weight(1f).width(2.dp).background(Color(0xFFE6E3DD)))
                }
                Column(Modifier.padding(start = 10.dp, bottom = if (i < rows.lastIndex) 16.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(h.optString("task"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(h.optString("label"), color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 4.dp))
                    Text("${h.optString("user")} · ${h.optString("at")}", color = KitMuted, fontSize = 12.sp)
                    h.objs("changes").forEach { c ->
                        val old = c.str("old"); val new = c.str("new")
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF7F5F1)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text(c.optString("field"), color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (old != null && new != null) {
                                Text(old, color = KitMuted, fontSize = 12.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, lineHeight = 16.sp)
                                Text("→ $new", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
                            } else Text(new ?: old ?: "—", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }
        when {
            loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            error != null && rows.isEmpty() -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
            rows.isEmpty() -> Text("Nothing has happened yet.", color = KitMuted, fontSize = 14.sp)
        }
        if (rows.isNotEmpty() && page < lastPage) {
            var more by remember(page) { mutableStateOf(false) }
            LaunchedEffect(more) { if (more) load(page + 1) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(color = b.base) else OutlinedButton(onClick = { more = true }) { Text("Load more") }
            }
        }
    }
}

// ───────────────────────────── board ─────────────────────────────

/** The task with its status changed to the column it was dropped on. */
private fun withStatus(task: JSONObject, col: JSONObject) = JSONObject(task.toString())
    .put("status", col.optString("name")).put("status_id", col.optInt("id")).put("overdue", col.optString("name") == "Overdue")

/** The board after [task] moved to column [to]: out of the column it was in, on top of the new one, both counts adjusted. */
private fun moved(cols: List<JSONObject>, task: JSONObject, to: JSONObject): List<JSONObject> {
    val id = task.optInt("id")
    return cols.map { c ->
        val tasks = c.objs("tasks")
        val had = tasks.any { it.optInt("id") == id }
        when {
            c.optInt("id") == to.optInt("id") -> JSONObject(c.toString()).put("tasks", JSONArray(listOf(withStatus(task, to)) + tasks.filter { it.optInt("id") != id })).put("total", c.optInt("total") + if (had) 0 else 1)
            had -> JSONObject(c.toString()).put("tasks", JSONArray(tasks.filter { it.optInt("id") != id })).put("total", c.optInt("total") - 1)
            else -> c
        }
    }
}

/**
 * The board: a column per status. With edit rights a card can be dragged to another column — press and hold it, move it over the column
 * and let go; the status changes at once (and goes back if the server refuses). Dragging near the screen edge scrolls sideways.
 */
@Composable
private fun TaskBoard(api: ApiClient, b: Branding, uuid: String, version: Int, canEdit: Boolean, onChanged: () -> Unit, onOpen: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var cols by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(version, attempt) {
        error = null
        try { cols = withContext(Dispatchers.IO) { api.get("/projects/$uuid/task-center/board").objs("data") } }
        catch (e: Exception) { error = e.message ?: "Could not load the board" }
    }

    // drag state: the card being held, where the finger is (window coordinates) and the column under it
    var dragging by remember { mutableStateOf<JSONObject?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableIntStateOf(-1) }
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val colCoords = remember { mutableMapOf<Int, LayoutCoordinates>() }
    val sideways = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenW = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }

    fun columnAt(p: Offset): Int = colCoords.entries.firstOrNull { (_, c) -> c.isAttached && c.boundsInRoot().contains(p) }?.key ?: -1

    fun drop() {
        val task = dragging; val c = cols
        val target = c?.firstOrNull { it.optInt("id") == hover }
        dragging = null; hover = -1
        if (task == null || c == null || target == null || task.optInt("status_id") == target.optInt("id")) return
        val before = c
        cols = moved(c, task, target)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.send("POST", "/task-center/${task.optInt("id")}/status", JSONObject().put("status_id", target.optInt("id"))) }
                android.widget.Toast.makeText(context, "Moved to ${target.optString("name")}", android.widget.Toast.LENGTH_SHORT).show()
                onChanged()
            } catch (e: Exception) {
                cols = before
                android.widget.Toast.makeText(context, e.message ?: "Could not move the task", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // near a screen edge while dragging: scroll the board that way
    LaunchedEffect(dragging != null) {
        while (dragging != null) {
            val edge = screenW * 0.14f
            if (finger.x < edge) sideways.scrollBy(-22f) else if (finger.x > screenW - edge) sideways.scrollBy(22f)
            hover = columnAt(finger)
            delay(16)
        }
    }

    val c = cols
    if (c == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(error ?: "", color = KitMuted); OutlinedButton(onClick = { attempt++ }) { Text("Retry") } }
        }
        return
    }
    Text(
        if (canEdit) "Press and hold a task, then drag it to another column" else "Swipe sideways to see every status",
        color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    Box(Modifier.onGloballyPositioned { rootCoords = it }) {
        Row(
            Modifier.horizontalScroll(sideways, enabled = dragging == null).padding(horizontal = 12.dp).height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            c.forEach { col ->
                val id = col.optInt("id")
                val tint = statusTint(col.optString("name"))
                val tasks = col.objs("tasks")
                val over = dragging != null && hover == id
                Column(
                    Modifier.width(290.dp).fillMaxHeight().onGloballyPositioned { colCoords[id] = it }
                        .clip(RoundedCornerShape(22.dp)).background(if (over) tint.copy(alpha = .16f) else Color(0xFFEFEDE8))
                        .then(if (over) Modifier.border(2.dp, tint, RoundedCornerShape(22.dp)) else Modifier).padding(vertical = 10.dp),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(tint))
                        Text(col.optString("name"), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text(col.optInt("total").toString(), color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                    if (tasks.isEmpty()) Text(if (over) "Drop here" else "Nothing here", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                    tasks.forEach { t ->
                        var coords by remember(t.optInt("id")) { mutableStateOf<LayoutCoordinates?>(null) }
                        val drag = if (canEdit) Modifier.pointerInput(t.optInt("id")) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { at -> coords?.let { finger = it.localToRoot(at) }; dragging = t },
                                onDrag = { change, amount -> change.consume(); finger += amount },
                                onDragEnd = { drop() },
                                onDragCancel = { dragging = null; hover = -1 },
                            )
                        } else Modifier
                        TaskCard(t, narrow = true, dim = dragging?.optInt("id") == t.optInt("id"), modifier = Modifier.onGloballyPositioned { coords = it }.then(drag)) { onOpen(t.optInt("id")) }
                    }
                    if (col.optInt("total") > tasks.size) Text("+ ${col.optInt("total") - tasks.size} more — use the List", color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }

        // the card in the hand, following the finger
        dragging?.let { t ->
            Box(
                Modifier.width(270.dp).offset {
                    val o = rootCoords?.positionInRoot() ?: Offset.Zero
                    IntOffset((finger.x - o.x - 135.dp.toPx()).roundToInt(), (finger.y - o.y - 40.dp.toPx()).roundToInt())
                }.shadow(16.dp, RoundedCornerShape(18.dp)).graphicsLayer { rotationZ = 2f; alpha = .96f },
            ) { TaskCard(t, narrow = true) { } }
        }
    }
}

// ───────────────────────────── pieces ─────────────────────────────

/** One task as a card: name, who it is with, due date, subtasks; status and priority on the right. [flat] drops the shadow (inside another card). */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun TaskCard(
    r: JSONObject, flat: Boolean = false, narrow: Boolean = false, dim: Boolean = false, modifier: Modifier = Modifier,
    selected: Boolean? = null, onLongClick: (() -> Unit)? = null, onMore: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val status = r.str("status"); val priority = r.str("priority")
    val overdue = r.optBoolean("overdue")
    val people = r.names("assignees")
    val due = prettyDay(r.str("due_date"))
    val box = modifier.alpha(if (dim) .35f else 1f).padding(horizontal = if (flat) 0.dp else if (narrow) 10.dp else 16.dp, vertical = 5.dp).fillMaxWidth()
        .then(if (flat) Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFFF7F5F1)) else Modifier.shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White))
    Column(
        box.then(if (selected == true) Modifier.border(2.dp, KitInk, RoundedCornerShape(18.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (selected != null) Box(
                Modifier.padding(end = 10.dp, top = 1.dp).size(22.dp).clip(CircleShape).background(if (selected) KitInk else Color(0xFFE6E3DD)),
                contentAlignment = Alignment.Center,
            ) { if (selected) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
            Text(r.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KitInk, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (!narrow && status != null) { Spacer(Modifier.width(8.dp)); val c = statusTint(status); Pill(status, c.copy(alpha = .14f), c) }
            if (onMore != null) Text("⋮", color = KitInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp).size(30.dp).clip(CircleShape).background(Color(0xFFF3F2F0)).clickable { onMore() }.wrapContentSize(Alignment.Center))
        }
        Text(
            if (people.isEmpty()) "Unassigned" else people.take(2).joinToString(", ") + if (people.size > 2) " +${people.size - 2}" else "",
            color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (priority != null) { val c = priorityTint(priority); Pill(priority, c.copy(alpha = .14f), c) }
            if (narrow && status != null) { val c = statusTint(status); Pill(status, c.copy(alpha = .14f), c) }
            if (due != null) Text("Due $due", color = if (overdue) KitDanger else KitMuted, fontSize = 12.sp, fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal)
            if (r.optInt("subtasks") > 0) Text("${r.optInt("subtasks")} subtasks", color = KitMuted, fontSize = 12.sp)
        }
        if (r.optInt("progress") > 0) Meter(r.optInt("progress") / 100f, statusTint(status))
    }
}

@Composable
internal fun Meter(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFE6E3DD))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color))
    }
}

@Composable
private fun FilterChip(label: String, count: Int?, on: Boolean, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) tint else Color.White).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (on) Color.White else KitInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        if (count != null) Text(count.toString(), color = if (on) Color.White.copy(alpha = .85f) else tint, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
internal fun Card(title: String, sub: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk)
            if (sub != null) Text(sub, color = KitMuted, fontSize = 12.sp)
        }
        content()
    }
}
