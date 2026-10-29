package com.example.gavanhomes.ui.projects

import com.example.gavanhomes.ui.common.ScrollTopButton
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.input.pointer.pointerInput
import com.example.gavanhomes.ui.tasks.ProjectTasks
import com.example.gavanhomes.ui.tasks.TaskDetailScreen
import com.example.gavanhomes.ui.tasks.TaskFormScreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.example.gavanhomes.data.ApiConfig
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.common.SegTabs
import com.example.gavanhomes.ui.projects.form.PayFields
import com.example.gavanhomes.ui.projects.form.PriceFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Amber = Color(0xFFC47A1A)
private val UnitColors = mapOf("available" to KitGood, "booked" to Amber, "sold" to KitDanger, "reserved" to Color(0xFF8B5CF6))

/** Labels of the unit columns, in the order the unit sheet lists them. */
private val UnitLabels = listOf(
    "unit_no" to "Unit #", "flat_no" to "Flat #", "shop_no" to "Shop #", "house_no" to "House #", "plot_area" to "Plot (SQM)",
    "wing" to "Wing", "block" to "Block / Tower", "floor" to "Floor", "area_sqm" to "Area (SQM)", "bedrooms" to "Bedrooms",
    "bathrooms" to "Bathrooms", "facing" to "Facing", "price" to "Price", "bhk_type" to "BHK type", "extra_cost" to "Extra cost",
    "carpet_area" to "Carpet area", "build_up_area" to "Build-up area", "balcony_area" to "Balcony area", "extra_area" to "Extra area",
    "land_area" to "Land area", "undivided_share" to "Undivided share", "east" to "East", "west" to "West", "north" to "North", "south" to "South",
)
private val UnitDocs = listOf("unit_plan" to "Unit plan", "vastu_report" to "Vastu report", "statement" to "Statement", "plan_copy" to "Plan copy")
/** A page of the project drawer; [key] is the module it lists (null for the two built-in pages). */
private class Section(val label: String, val blurb: String, val glyph: String, val key: String? = null)
private val Sections = listOf(
    Section("Dashboard", "Numbers and charts at a glance", "▦"),
    Section("Details", "Overview, units, developer and pricing", "ⓘ"),
)
/** The website's project tabs (commonTabs.blade.php), in its order — each one a native list (ProjectModule). */
private val ModuleSections = listOf(
    Section("Tasks", "Work items and their progress", "✔", "tasks"), Section("Targets", "Goals and how far they are", "◎", "targets"),
    Section("Bids", "Bids and their status", "▤", "bids"), Section("Leads", "Leads and follow-ups", "⚑", "leads"),
    Section("Inventory", "Materials and stock", "▣", "inventory"), Section("Procurements", "Purchase orders, indents, suppliers", "⛟", "procurements"),
    Section("Customers", "Bookings and customers", "◍", "customers"), Section("Attendance", "Workers and today's attendance", "◷", "attendance"),
    Section("Assets", "Equipment and its value", "◈", "assets"), Section("Accounting", "Payments and expenses", "₹", "accounting"),
    Section("Communications", "WhatsApp and email templates", "✉", "communications"), Section("Files", "Project documents", "▥", "files"),
    Section("Discussion", "Project chats", "☰", "discussion"), Section("General", "Contractors, workers, engineers, parties", "⚙", "general"),
    Section("Reports", "Headline numbers per area", "▮", "reports"),
)
private val Tabs = listOf("Overview", "Inventory", "Developer", "Pricing")

private fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
private fun JSONObject.objects(key: String): List<JSONObject> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()

private val Rupees: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }
private fun money(v: String?): String? = v?.toDoubleOrNull()?.let { "₹" + Rupees.format(it) }
private fun money(v: Double): String = "₹" + Rupees.format(v)
private val Pretty = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun date(s: String?): LocalDate? = runCatching { LocalDate.parse(s!!.take(10)) }.getOrNull()
private fun prettyDate(s: String?): String? = date(s)?.format(Pretty)

/** A unit's headline number: its flat / shop / house / unit number. */
private fun unitTitle(u: JSONObject) = listOf("flat_no", "shop_no", "house_no", "unit_no").firstNotNullOfOrNull { u.str(it) } ?: "Unit"

/**
 * Project details, all native — GET /projects/{uuid} (`detail`): overview, inventory, developer / partners / RERA, pricing.
 * Documents open in whatever app handles them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(api: ApiClient, b: Branding, uuid: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val context = LocalContext.current
    var root by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var section by remember { mutableIntStateOf(0) }                    // which of Sections is open
    var tab by remember { mutableIntStateOf(0) }                        // the tab inside Details
    // Tasks: task pages open over the project, newest on top ("detail" id · "form" id, 0 = a new task)
    val taskStack = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, Int>>() }
    val targetStack = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, Int>>() }
    var targetsVersion by remember { mutableIntStateOf(0) }
    val leadStack = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, Int>>() }
    var leadsVersion by remember { mutableIntStateOf(0) }
    var tasksVersion by remember { mutableIntStateOf(0) }               // bumped after a change, so lists reload
    var unit by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }

    var dash by remember { mutableStateOf<JSONObject?>(null) }
    var dashError by remember { mutableStateOf<String?>(null) }
    var dashAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        try { root = withContext(Dispatchers.IO) { api.detail("/projects/$uuid") } }
        catch (e: Exception) { error = e.message ?: "Could not load the project" }
    }
    LaunchedEffect(dashAttempt) {
        dashError = null
        try { dash = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/dashboard") } }
        catch (e: Exception) { dashError = e.message ?: "Could not load the dashboard" }
    }

    val view: (String) -> Unit = { path ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${ApiConfig.BASE_URL}/storage/$path"))) }
            .onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }
    fun launch(intent: Intent) { runCatching { context.startActivity(intent) } }

    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    // Dashboard, Details, then every module this user may open (the dashboard response says which)
    val allowed = dash?.optJSONArray("tabs")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    val sections = Sections + ModuleSections.filter { it.key in allowed }
    val current = sections.getOrElse(section) { sections[0] }
    androidx.activity.compose.BackHandler(enabled = drawer.isOpen || section != 0) { if (drawer.isOpen) scope.launch { drawer.close() } else section = 0 }

    // all the project's pages live in a side drawer (opened with ☰) instead of rows of tabs
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,                       // opened with ☰ — a left-edge swipe means "go back"
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White, windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(b.deep, b.base), start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset(700f, 700f)))
                            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        Column {
                            Text(root?.optJSONObject("detail")?.str("name") ?: "Project", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(root?.optJSONObject("detail")?.str("code") ?: "Project pages", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp)) {
                    sections.forEachIndexed { i, s ->
                        val on = i == section
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) b.base.copy(alpha = .16f) else Color.Transparent)
                                .clickable { section = i; scope.launch { drawer.close() } }.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (on) b.base else Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) {
                                Text(s.glyph, color = if (on) Color.White else KitInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(s.label, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = KitInk, fontSize = 15.sp)
                                Text(s.blurb, color = KitMuted, fontSize = 12.sp)
                            }
                        }
                    }
                    }
                }
            }
        },
    ) {
    Column(Modifier.fillMaxSize().background(KitPage)) {
        val d = root?.optJSONObject("detail")
        if (d == null) {
            HeroBar(b, "Project", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = b.base)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
                }
            }
            return@Column
        }

        val inventory = d.optJSONObject("inventory")
        val units = inventory?.keys()?.asSequence()?.toList().orEmpty().sorted().flatMap { t -> d.optJSONObject("inventory")!!.objects(t).map { t to it } }
        val counts = UnitColors.keys.associateWith { s -> units.count { it.second.str("status") == s } }

        val pageScroll = rememberScrollState()
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize().verticalScroll(pageScroll)) {
            HeroBar(
                b, current.label, onBack,
                actions = {
                    if (root?.optBoolean("can_edit") == true) { HeroButton("✎") { onEdit(uuid) }; Spacer(Modifier.size(8.dp)) }
                    HeroButton("☰") { scope.launch { drawer.open() } }
                },
            ) {
                Column {
                    Text(d.str("name") ?: "Project", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(d.str("code") ?: "No code", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*(listOfNotNull(d.str("status"), if (d.optBoolean("active")) "Active" else "Inactive") + d.strings("type")).toTypedArray())
                }
            }

            if (current.key == "tasks") ProjectTasks(api, b, uuid, tasksVersion, onOpen = { taskStack.add("detail" to it) }, onNew = { taskStack.add("form" to 0) },
                onEdit = { taskStack.add("form" to it) }, onOpenAt = { id, tabKey -> taskStack.add("detail:$tabKey" to id) })
            else if (current.key == "leads") com.example.gavanhomes.ui.leads.ProjectLeads(api, b, uuid, leadsVersion, onOpen = { id, tabKey -> leadStack.add("detail:$tabKey" to id) }, onNew = { leadStack.add("form" to 0) }, onEdit = { leadStack.add("form" to it) })
            else if (current.key == "targets") com.example.gavanhomes.ui.targets.ProjectTargets(api, b, uuid, targetsVersion, onOpen = { targetStack.add("detail" to it) }, onNew = { targetStack.add("form" to 0) },
                onEdit = { targetStack.add("form" to it) }, onOpenAt = { id, tabKey -> targetStack.add("detail:$tabKey" to id) })
            else if (current.key != null) androidx.compose.runtime.key(current.key) { ProjectModule(api, b, uuid, current.key) }
            else if (section == 0) ProjectDashboard(dash, dashError, b) { dashAttempt++ }
            else {
                // ── numbers at a glance ──
                Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(units.size.toString(), "Units", Modifier.weight(1f))
                    Tile((counts["available"] ?: 0).toString(), "Available", Modifier.weight(1f), KitGood)
                    Tile(((counts["sold"] ?: 0) + (counts["booked"] ?: 0)).toString(), "Sold / booked", Modifier.weight(1f), KitDanger)
                    Tile(d.optJSONArray("members")?.length()?.toString() ?: "0", "Members", Modifier.weight(1f))
                }

                SegTabs(Tabs, tab) { tab = it }

                when (tab) {
                    0 -> Overview(d, b, ::launch)
                    1 -> Inventory(d, units, counts) { t, u -> unit = t to u }
                    2 -> Developer(d, view)
                    else -> Pricing(d, b)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        ScrollTopButton(pageScroll)
        }
    }
    }

    // a task / target form has its own Save bar, so the footer tabs step aside while one is open
    val formOpen = taskStack.lastOrNull()?.first == "form" || targetStack.lastOrNull()?.first == "form" || leadStack.lastOrNull()?.first == "form"
    androidx.compose.runtime.LaunchedEffect(formOpen) { com.example.gavanhomes.ui.common.Chrome.hideBar = formOpen }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { com.example.gavanhomes.ui.common.Chrome.hideBar = false } }

    leadStack.lastOrNull()?.let { (kind, lid) ->
        fun pop() { if (leadStack.isNotEmpty()) leadStack.removeAt(leadStack.lastIndex) }
        Box(Modifier.fillMaxSize().background(KitPage).pointerInput(Unit) { detectTapGestures { } }) {
            if (kind.startsWith("detail")) androidx.compose.runtime.key(leadStack.size, lid) {
                com.example.gavanhomes.ui.leads.LeadDetailScreen(
                    api, b, lid, leadsVersion, initialTab = when (kind.substringAfter(':', "")) { "followups" -> 1; "tasks" -> 2; "activity" -> 3; else -> 0 }, onBack = ::pop, onEdit = { leadStack.add("form" to it) },
                    onOpenTask = { taskStack.add("detail" to it) }, onChanged = { leadsVersion++ },
                )
            } else com.example.gavanhomes.ui.leads.LeadFormScreen(api, b, uuid, lid.takeIf { it > 0 }, onBack = ::pop, onSaved = { leadsVersion++; pop() })
        }
    }

    taskStack.lastOrNull()?.let { (kind, tid) ->
        fun pop() { if (taskStack.isNotEmpty()) taskStack.removeAt(taskStack.lastIndex) }
        Box(Modifier.fillMaxSize().background(KitPage).pointerInput(Unit) { detectTapGestures { } }) {
            if (kind.startsWith("detail")) androidx.compose.runtime.key(taskStack.size, tid) {
                TaskDetailScreen(
                    api, b, tid, tasksVersion, initialTab = when (kind.substringAfter(':', "")) { "files" -> 1; "discussion" -> 2; "activity" -> 3; "assign" -> 4; else -> 0 }, onBack = ::pop, onEdit = { taskStack.add("form" to it) }, onOpenTask = { taskStack.add("detail" to it) },
                    onChanged = { tasksVersion++ },
                )
            } else TaskFormScreen(api, b, uuid, tid.takeIf { it > 0 }, onBack = ::pop, onSaved = { tasksVersion++; pop() })
        }
    }

    targetStack.lastOrNull()?.let { (kind, tid) ->
        fun pop() { if (targetStack.isNotEmpty()) targetStack.removeAt(targetStack.lastIndex) }
        Box(Modifier.fillMaxSize().background(KitPage).pointerInput(Unit) { detectTapGestures { } }) {
            if (kind.startsWith("detail")) androidx.compose.runtime.key(targetStack.size, tid) {
                com.example.gavanhomes.ui.targets.TargetDetailScreen(
                    api, b, tid, targetsVersion, initialTab = when (kind.substringAfter(':', "")) { "files" -> 1; "discussion" -> 2; "activity" -> 3; "assign" -> 4; else -> 0 }, onBack = ::pop, onEdit = { targetStack.add("form" to it) },
                    onChanged = { targetsVersion++ },
                )
            } else com.example.gavanhomes.ui.targets.TargetFormScreen(api, b, uuid, tid.takeIf { it > 0 }, onBack = ::pop, onSaved = { targetsVersion++; pop() })
        }
    }

    unit?.let { (type, u) ->
        val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { unit = null }, sheetState = sheet, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(unitTitle(u), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
                        Text(type, color = KitMuted, fontSize = 13.sp)
                    }
                    StatusPill(u.str("status"))
                }
                UnitLabels.forEach { (k, label) -> Fact(label, if (k == "price") money(u.str(k)) else u.str(k)) }
                val docs = UnitDocs.filter { !u.str(it.first).isNullOrBlank() }
                if (docs.isNotEmpty()) {
                    Text("DOCUMENTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                    docs.forEach { (k, label) -> Doc(label, u.str(k), view) }
                }
            }
        }
    }
}

// ─────────────────────────────── tabs ───────────────────────────────

@Composable
private fun Overview(d: JSONObject, b: Branding, launch: (Intent) -> Unit) {
    val start = date(d.str("start_date")); val end = date(d.str("end_date"))
    if (start != null || end != null) Card("Timeline") {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Text("START", fontSize = 11.sp, color = KitMuted, fontWeight = FontWeight.Bold); Text(prettyDate(d.str("start_date")) ?: "—", fontWeight = FontWeight.Bold, color = KitInk) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { Text("END", fontSize = 11.sp, color = KitMuted, fontWeight = FontWeight.Bold); Text(prettyDate(d.str("end_date")) ?: "—", fontWeight = FontWeight.Bold, color = KitInk) }
        }
        if (start != null && end != null && end.isAfter(start)) {
            val frac = (ChronoUnit.DAYS.between(start, LocalDate.now()).toFloat() / ChronoUnit.DAYS.between(start, end)).coerceIn(0f, 1f)
            Bar(frac, b.base)
            val left = ChronoUnit.DAYS.between(LocalDate.now(), end)
            Text(
                if (left >= 0) "$left days left · ${(frac * 100).toInt()}% of the time used" else "Ended ${-left} days ago",
                color = if (left < 0) KitDanger else KitMuted, fontSize = 13.sp,
            )
        }
    }

    d.str("description")?.let { Card("About") { Text(it, color = KitInk, fontSize = 14.sp, lineHeight = 21.sp) } }

    val address = listOfNotNull(d.str("address"), d.str("city"), d.str("state"), d.str("pincode")).joinToString(", ")
    if (address.isNotEmpty() || d.str("phone") != null || d.str("email") != null) Card("Location & contact") {
        Fact("Address", address.ifEmpty { null }) { launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))) }
        Fact("Phone", d.str("phone")) { launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${d.str("phone")}"))) }
        Fact("Email", d.str("email")) { launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${d.str("email")}"))) }
    }

    val members = d.objects("members")
    if (d.str("leader") != null || members.isNotEmpty()) Card("Team") {
        d.str("leader")?.let { Person(it, "Team leader") }
        members.filter { it.str("name") != d.str("leader") }.forEach { Person(it.str("name") ?: "Member", "Member") }
    }
}

@Composable
private fun Inventory(d: JSONObject, units: List<Pair<String, JSONObject>>, counts: Map<String, Int>, onOpen: (String, JSONObject) -> Unit) {
    if (units.isEmpty()) {
        Card("Inventory") { Text("No units have been added to this project yet.", color = KitMuted, fontSize = 14.sp) }
        return
    }
    Card("Availability", "${units.size} unit${if (units.size == 1) "" else "s"}") {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFEDEBE7))) {
            UnitColors.forEach { (s, c) -> val n = counts[s] ?: 0; if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxSize().background(c)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UnitColors.forEach { (s, c) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = .1f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((counts[s] ?: 0).toString(), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = c)
                    Text(s.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = c, maxLines = 1)
                }
            }
        }
    }
    units.groupBy { it.first }.toSortedMap().forEach { (type, rows) ->
        Text(
            "$type · ${rows.size}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 2.dp),
        )
        rows.forEach { (_, u) -> UnitRow(u) { onOpen(type, u) } }
    }
}

@Composable
private fun Developer(d: JSONObject, view: (String) -> Unit) {
    val files = d.optJSONObject("files")
    fun file(k: String) = files?.str(k)

    Card("Developer") {
        if (listOf("developer_name", "developer_adhar", "developer_pan", "developer_gst", "developer_address").all { d.str(it) == null }) {
            Text("No developer details added.", color = KitMuted, fontSize = 14.sp)
        }
        Fact("Name", d.str("developer_name"))
        Fact("Aadhaar", d.str("developer_adhar")); Doc("Aadhaar card", file("developer_adhar_proof"), view)
        Fact("PAN", d.str("developer_pan")); Doc("PAN card", file("developer_pan_proof"), view)
        Fact("GST", d.str("developer_gst")); Doc("GST certificate", file("developer_gst_proof"), view)
        Fact("Address", d.str("developer_address"))
        Doc("Proof document", file("developer_proof"), view)
    }

    d.objects("partners").forEachIndexed { i, p ->
        Card("Partner ${i + 1}", p.str("name")) {
            Fact("Mobile", p.str("mobile")); Fact("Email", p.str("email"))
            Fact("Aadhaar", p.str("aadhar")); Fact("PAN", p.str("pan"))
            Doc("Photo", p.str("photo"), view); Doc("E-signature", p.str("esign"), view)
        }
    }

    if (d.optBoolean("rera_registered")) Card("RERA registration") {
        Fact("RERA number", d.str("rera_number")); Fact("Completion date", prettyDate(d.str("rera_completion_date")))
        Doc("RERA certificate", file("rera_certificate"), view)
        if (listOf("rera_agent_aadhaar", "rera_agent_pan", "rera_agent_email", "rera_agent_mobile", "rera_agent_estg", "rera_agent_address").any { d.str(it) != null }) {
            Text("RERA AGENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
            Fact("Aadhaar", d.str("rera_agent_aadhaar")); Fact("PAN", d.str("rera_agent_pan"))
            Fact("Email", d.str("rera_agent_email")); Fact("Mobile", d.str("rera_agent_mobile"))
            Fact("ESTG no.", d.str("rera_agent_estg")); Fact("Address", d.str("rera_agent_address"))
        }
        Doc("Agent photo", file("rera_agent_photo"), view); Doc("Agent RERA certificate", file("rera_agent_certificate"), view)
    } else Card("RERA registration") { Text("Not RERA registered.", color = KitMuted, fontSize = 14.sp) }
}

@Composable
private fun Pricing(d: JSONObject, b: Branding) {
    val parts = PriceFields.mapNotNull { (k, label) -> d.str(k)?.toDoubleOrNull()?.let { label to it } }
    val total = d.str("total_consideration")?.toDoubleOrNull() ?: parts.takeIf { it.isNotEmpty() }?.sumOf { it.second }

    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(b.deep, b.base))).padding(20.dp),
    ) {
        Text("TOTAL CONSIDERATION", color = Color.White.copy(alpha = .8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Text(total?.let(::money) ?: "—", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
    }

    Card("Cost breakdown") {
        if (parts.isEmpty()) Text("No pricing added.", color = KitMuted, fontSize = 14.sp)
        parts.forEach { (label, v) -> Fact(label, money(v)) }
    }

    val pay = PayFields.mapNotNull { (k, label, _) -> d.str(k)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { label.removeSuffix(" (%)") to it } }
    Card("Payment schedule", "Share of the sale price due at each milestone") {
        if (pay.isEmpty()) Text("No payment schedule added.", color = KitMuted, fontSize = 14.sp)
        else {
            val palette = listOf(b.base, b.deep, Color(0xFF2F6FE4), Color(0xFF1BAF7A), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899))
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                pay.forEachIndexed { i, (_, v) -> Box(Modifier.weight(v.toFloat()).fillMaxSize().background(palette[i % palette.size])) }
            }
            pay.forEachIndexed { i, (label, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(palette[i % palette.size]))
                    Text(label, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Text("${Rupees.format(v)}%", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp)
                }
            }
            val sum = pay.sumOf { it.second }
            val ok = kotlin.math.abs(sum - 100.0) < 0.005
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background((if (ok) KitGood else KitDanger).copy(alpha = .1f)).padding(12.dp)) {
                Text("Total", fontWeight = FontWeight.SemiBold, color = KitInk, modifier = Modifier.weight(1f))
                Text("${Rupees.format(sum)}%" + if (ok) "  ✓" else "  (not 100%)", fontWeight = FontWeight.ExtraBold, color = if (ok) KitGood else KitDanger)
            }
        }
    }
}

// ─────────────────────────────── pieces ───────────────────────────────

@Composable
private fun Card(title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk)
            if (sub != null) Text(sub, color = KitMuted, fontSize = 12.sp)
        }
        content()
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier, tint: Color = KitInk) {
    Column(
        modifier.shadow(5.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = tint)
        Text(label, fontSize = 11.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A label / value line; it is left out when there is no value, and tappable when [onClick] is given. */
@Composable
private fun Fact(label: String, value: String?, onClick: (() -> Unit)? = null) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(.8f))
        Text(value, color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun Doc(label: String, path: String?, view: (String) -> Unit) {
    if (path.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            "View", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KitInk,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1EFEB)).clickable { view(path) }.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEDEBE7))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxSize().background(color))
    }
}

@Composable
private fun Person(name: String, role: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Initials(name, size = 40.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(name, fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp)
            Text(role, color = KitMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusPill(status: String?) {
    val c = UnitColors[status] ?: KitMuted
    Pill((status ?: "—").replaceFirstChar { it.uppercase() }, c.copy(alpha = .14f), c)
}

@Composable
private fun UnitRow(u: JSONObject, onClick: () -> Unit) {
    val sub = listOfNotNull(
        u.str("wing")?.let { "Wing $it" }, u.str("floor")?.let { "Floor $it" }, u.str("bhk_type"),
        u.str("area_sqm")?.let { "$it sqm" }, u.str("plot_area")?.let { "$it sqm plot" },
    ).joinToString(" · ")
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)).background(Color.White).clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(unitTitle(u), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk)
            if (sub.isNotEmpty()) Text(sub, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(u.str("price")) ?: "—", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            StatusPill(u.str("status"))
        }
    }
}
