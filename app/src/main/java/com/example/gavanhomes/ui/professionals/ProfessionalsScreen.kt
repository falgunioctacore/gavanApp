package com.example.gavanhomes.ui.professionals

import com.example.gavanhomes.ui.common.HeroSummary
import com.example.gavanhomes.ui.common.ScrollTopButton
import com.example.gavanhomes.ui.common.FabAction
import com.example.gavanhomes.ui.common.FabLift
import com.example.gavanhomes.ui.common.QuickFab
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.CardAction
import com.example.gavanhomes.ui.common.CardStat
import com.example.gavanhomes.ui.common.EntityCard
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.common.FilterBar
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.SearchBox
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.SwitchRow
import com.example.gavanhomes.ui.projects.form.readPicked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private class Section(val label: String, val glyph: String, val blurb: String)

private val Sections = listOf(
    Section("Dashboard", "⌂", "Overview and alerts"),
    Section("Professionals", "✦", "The directory"),
    Section("Professional types", "▦", "Categories"),
    Section("Review queue", "✓", "Submitted work"),
    Section("Performance", "↗", "Per-professional metrics"),
)
private val SortNames = listOf("Newest", "Name A–Z", "Licence expiry")
private val StatusOrder = listOf("active", "onhold", "inactive", "blacklisted")

private class Pro(val json: JSONObject) {
    val uuid = json.str("uuid").orEmpty()
    val id = json.optInt("id")
    val name = json.str("name") ?: "Professional"
    val code = json.str("code")
    val company = json.str("company")
    val type = json.str("type")
    val email = json.str("email")
    val phone = json.str("phone")
    val city = json.str("city")
    val status = json.str("status")
    val expiry = json.str("license_expiry")
    val banner = ProBanners[(id % ProBanners.size + ProBanners.size) % ProBanners.size]
    fun matches(q: String) = q.isEmpty() || listOfNotNull(name, code, company, type, email, phone, city).any { it.lowercase().contains(q) }
}

/**
 * Professionals, all native. The sections (dashboard, directory, types, review queue, performance) live in a sidebar that
 * the ☰ button slides open. Data: /professionals (and its sub-routes), /professional-types, /professional-reviews, /professional-performance.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfessionalsScreen(
    api: ApiClient, b: Branding, section: Int, onSection: (Int) -> Unit, onBack: () -> Unit,
    onOpenPro: (String) -> Unit, onAddPro: () -> Unit, onEditPro: (String) -> Unit, onOpenReview: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)

    var dash by remember { mutableStateOf<JSONObject?>(null) }
    var pros by remember { mutableStateOf<List<Pro>?>(null) }
    var types by remember { mutableStateOf<List<JSONObject>?>(null) }
    var reviews by remember { mutableStateOf<JSONObject?>(null) }
    var perf by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val loadedAt = remember { mutableStateMapOf<Int, Int>() }
    var refreshing by remember { mutableStateOf(false) }                      // pull-down refresh in progress
    LaunchedEffect(loadedAt[section]) { refreshing = false }                         // fresh data arrived
    LaunchedEffect(refreshing) { if (refreshing) { kotlinx.coroutines.delay(10_000); refreshing = false } }   // never spin forever

    // directory controls
    var query by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }   // chosen filters: group → values
    var sort by remember { mutableIntStateOf(0) }
    var compact by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Pro?>(null) }
    var confirmDelete by remember { mutableStateOf<Pro?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    var typeSheet by remember { mutableStateOf<JSONObject?>(null) }   // an existing type, or an empty object for "new"
    var typeOpen by remember { mutableStateOf(false) }

    LaunchedEffect(section, reload) {
        if (loadedAt[section] == reload) return@LaunchedEffect
        error = null
        try {
            when (section) {
                0 -> dash = withContext(Dispatchers.IO) { api.detail("/professionals/dashboard") }
                1 -> pros = withContext(Dispatchers.IO) { api.get("/professionals") }.optJSONArray("data")?.let { a -> (0 until a.length()).map { Pro(a.getJSONObject(it)) } } ?: emptyList()
                2 -> types = withContext(Dispatchers.IO) { api.get("/professional-types") }.optJSONArray("data")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
                3 -> reviews = withContext(Dispatchers.IO) { api.get("/professional-reviews") }
                else -> perf = withContext(Dispatchers.IO) { api.get("/professional-performance") }.optJSONArray("data")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
            }
            loadedAt[section] = reload
        } catch (e: Exception) { error = e.message ?: "Could not load" }
    }

    fun refresh() { loadedAt.clear(); reload++ }
    fun fail(e: Exception) = Toast.makeText(context, e.message ?: "Something went wrong", Toast.LENGTH_LONG).show()

    val sidebarCounts = mapOf(
        1 to (pros?.size ?: dash?.optJSONObject("stats")?.optInt("total")),
        2 to (types?.size ?: dash?.optJSONObject("stats")?.optInt("types")),
        3 to (reviews?.optJSONArray("data")?.length() ?: dash?.optJSONObject("stats")?.optInt("submitted")),
    )

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White, windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(b.deep, b.base), start = Offset.Zero, end = Offset(700f, 700f)))
                            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (b.logo != null) { com.example.gavanhomes.ui.common.BrandMark(b, 52.dp); Spacer(Modifier.size(14.dp)) }
                        Column {
                            Text("Professionals", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Directory, reviews and performance", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Sections.forEachIndexed { i, s ->
                        val on = i == section
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (on) b.base.copy(alpha = .16f) else Color.Transparent)
                                .clickable { onSection(i); scope.launch { drawer.close() } }.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) b.base else Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) {
                                Text(s.glyph, color = if (on) Color.White else KitInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(s.label, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = KitInk, fontSize = 15.sp)
                                Text(s.blurb, color = KitMuted, fontSize = 12.sp)
                            }
                            sidebarCounts[i]?.let { n ->
                                Text(n.toString(), color = if (on) b.dark else KitMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Color.White else Color(0xFFF3F2F0)).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(KitPage)) {
            val toggle: @Composable () -> Unit = { HeroButton("☰") { scope.launch { drawer.open() } } }
            val title = Sections[section].label
            val ready = when (section) { 0 -> dash != null; 1 -> pros != null; 2 -> types != null; 3 -> reviews != null; else -> perf != null }

            if (!ready) {
                HeroBar(b, title, onBack, leading = toggle)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (error == null) CircularProgressIndicator(color = b.base)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp)); androidx.compose.material3.OutlinedButton(onClick = { refresh() }) { Text("Retry") }
                    }
                }
                return@Column
            }

            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; loadedAt.clear(); reload++ }, modifier = Modifier.fillMaxSize()) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                item {
                    HeroBar(
                        b, title, onBack, leading = toggle,
                        actions = {
                        },
                    ) { HeroCounters(section, dash, pros, types, reviews, perf) }
                }

                when (section) {
                    0 -> dashboard(dash!!, b, onOpenPro, onSection)
                    1 -> {
                        val all = pros.orEmpty()
                        val passes = { p: Pro, m: Map<String, Set<String>> -> m["status"].orEmpty().let { s -> s.isEmpty() || p.status in s } && m["type"].orEmpty().let { t -> t.isEmpty() || p.type in t } }
                        val shown = all
                            .filter { p -> passes(p, sel) && p.matches(query.trim().lowercase()) }
                            .let { l ->
                                when (sort) {
                                    1 -> l.sortedBy { it.name.lowercase() }
                                    2 -> l.sortedBy { it.expiry ?: "9999" }
                                    else -> l.sortedByDescending { it.id }
                                }
                            }
                        item { Spacer(Modifier.height(14.dp)) }
                        item {
                            FilterBar(
                                query, { query = it }, "Search professionals…",
                                groups = listOf(
                                    FilterGroup("status", "Status", StatusOrder.map { st -> FilterOption(st, statusLabel(st), all.count { p -> p.status == st }, statusColor(st)) }),
                                    FilterGroup("type", "Type", all.mapNotNull { p -> p.type }.distinct().sorted().map { t -> FilterOption(t, t, all.count { p -> p.type == t }) }),
                                ),
                                selected = sel, onApply = { sel = it },
                                                                countWith = { m -> all.count { p -> passes(p, m) && p.matches(query.trim().lowercase()) } },
                                sortOptions = SortNames, sortIndex = sort, onSort = { sort = it },
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${shown.size} ${if (shown.size == 1) "professional" else "professionals"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("⇪ Import", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = KitInk,
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { importOpen = true }.padding(horizontal = 12.dp, vertical = 8.dp))
                                Spacer(Modifier.size(8.dp))
                                com.example.gavanhomes.ui.common.ViewToggle(compact) { compact = it }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                        if (shown.isEmpty()) item {
                            Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No professionals match", fontWeight = FontWeight.Bold, color = KitInk)
                                Text("Try another search or clear the filters.", color = KitMuted, fontSize = 13.sp)
                            }
                        }
                        items(shown, key = { it.uuid }) { p ->
                            if (compact) ProCompact(p, b, { onOpenPro(p.uuid) }, { sheet = p })
                            else ProCard(
                                p, b, { onOpenPro(p.uuid) }, { sheet = p },
                                { p.phone?.let { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) } },
                                { p.email?.let { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$it"))) } },
                            )
                        }
                    }
                    2 -> types(types!!, b) { typeSheet = it; typeOpen = true }
                    3 -> reviews(reviews!!, b, onOpenReview)
                    else -> performance(perf!!, b, onOpenPro)
                }
            }
            val fab = when (section) { 1 -> listOf(FabAction("pro", "New professional")); 2 -> listOf(FabAction("type", "New type")); else -> emptyList() }
            ScrollTopButton(listState, lift = if (fab.isNotEmpty()) FabLift else 0.dp)
            QuickFab(b, fab) { if (it == "pro") onAddPro() else { typeSheet = JSONObject(); typeOpen = true } }
            }
        }
    }

    // ── ⋯ actions on a professional ──
    sheet?.let { p ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(p.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text(listOfNotNull(p.code, p.type).joinToString(" · "), color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                ProSheetAction("◉", "Open details") { sheet = null; onOpenPro(p.uuid) }
                ProSheetAction("✎", "Edit professional") { sheet = null; onEditPro(p.uuid) }
                p.phone?.let { n -> ProSheetAction("✆", "Call $n") { sheet = null; context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) } }
                p.email?.let { m -> ProSheetAction("✉", m) { sheet = null; context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$m"))) } }
                ProSheetAction("✕", "Delete", danger = true) { confirmDelete = p; sheet = null }
            }
        }
    }
    confirmDelete?.let { p ->
        SwalConfirm(
            title = "Delete ${p.name}?", text = "They will lose access and the record can't be restored.", confirmText = "Yes, delete",
            onCancel = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.delete("/professionals/${p.uuid}") }
                        Toast.makeText(context, "Professional deleted", Toast.LENGTH_SHORT).show(); refresh()
                    } catch (e: Exception) { fail(e) }
                }
            },
        )
    }

    // ── CSV import ──
    if (importOpen) ImportSheet(api, onDone = { importOpen = false; if (it) refresh() })

    // ── type form ──
    if (typeOpen) typeSheet?.let { t ->
        TypeSheet(api, t, onDismiss = { typeOpen = false }, onChanged = { typeOpen = false; refresh() })
    }
}

// ─────────────────────────────── hero counters ───────────────────────────────

@Composable
private fun HeroCounters(section: Int, dash: JSONObject?, pros: List<Pro>?, types: List<JSONObject>?, reviews: JSONObject?, perf: List<JSONObject>?) {
    val stats = dash?.optJSONObject("stats")
    val items: List<Pair<String, String>> = when (section) {
        0 -> listOf((stats?.optInt("total") ?: 0).toString() to "Professionals", (stats?.optInt("active") ?: 0).toString() to "Active",
            (stats?.optInt("submitted") ?: 0).toString() to "To review", ((stats?.optInt("expiring") ?: 0) + (stats?.optInt("expired") ?: 0)).toString() to "Alerts")
        1 -> { val a = pros.orEmpty(); listOf(a.size.toString() to "Total", a.count { it.status == "active" }.toString() to "Active", a.count { it.status == "onhold" }.toString() to "On hold",
            a.count { it.status == "inactive" || it.status == "blacklisted" }.toString() to "Inactive") }
        2 -> { val a = types.orEmpty(); listOf(a.size.toString() to "Types", a.count { it.optBoolean("active") }.toString() to "Active", a.count { it.optBoolean("system") }.toString() to "System",
            a.count { it.optInt("professionals") == 0 }.toString() to "Unused") }
        3 -> listOf((reviews?.optJSONArray("data")?.length() ?: 0).toString() to "Pending", (reviews?.optInt("reviewed_count") ?: 0).toString() to "Reviewed")
        else -> { val a = perf.orEmpty(); listOf(a.size.toString() to "Tracked", a.sumOf { it.optInt("total_tasks") }.toString() to "Tasks", a.sumOf { it.optInt("approved") }.toString() to "Approved",
            a.sumOf { it.optInt("rework_count") }.toString() to "Reworks") }
    }
    HeroSummary(items)
}

// ─────────────────────────────── dashboard ───────────────────────────────

private fun LazyListScope.dashboard(d: JSONObject, b: Branding, onOpenPro: (String) -> Unit, onSection: (Int) -> Unit) {
    val s = d.optJSONObject("stats") ?: JSONObject()
    item { Spacer(Modifier.height(14.dp)) }
    item {
        ProKpiGrid(listOf(
            Triple(s.optInt("open").toString(), "Open tasks", ProBlue), Triple(s.optInt("submitted").toString(), "Awaiting review", ProAmber),
            Triple(s.optInt("approved").toString(), "Approved", KitGood), Triple(s.optInt("types").toString(), "Types", KitInk),
            Triple(s.optInt("expiring").toString(), "Licences expiring", ProAmber), Triple(s.optInt("expired").toString(), "Licences expired", KitDanger),
        ))
    }


    val byType = d.objects("by_type")
    item {
        val palette = listOf(b.base, ProBlue, Color(0xFF8B5CF6), KitGood, ProAmber, Color(0xFFEC4899))
        ProDonutCard("Professionals by type", "Tap to open the directory", byType.mapIndexed { i, t -> Triple(t.optString("name"), t.optInt("count"), palette[i % palette.size]) }, "No professionals yet.") { onSection(1) }
    }

    val byStatus = d.optJSONObject("by_status")
    item {
        ProDonutCard("Task status", "Work assigned to professionals", byStatus?.keys()?.asSequence()?.toList().orEmpty().map { k -> Triple(statusLabel(k), byStatus!!.optInt(k), statusColor(k)) }, "No tasks assigned to professionals yet.")
    }

    val alerts = d.objects("licence_alerts")
    item {
        ProCard("Licence alerts", "Expired or lapsing within 60 days") {
            if (alerts.isEmpty()) Text("Every licence is in date.", color = KitMuted, fontSize = 14.sp)
            alerts.forEach { a ->
                val days = a.optInt("days")
                val tone = if (days < 0) KitDanger else ProAmber
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenPro(a.optString("uuid")) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.optString("name"), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp)
                        Text(prettyDate(a.str("license_expiry")) ?: "", color = KitMuted, fontSize = 12.sp)
                    }
                    Pill(if (days < 0) "Expired ${-days}d ago" else "In ${days}d", tone.copy(alpha = .14f), tone)
                }
            }
        }
    }

    val recent = d.objects("recent_assignments")
    item {
        ProCard("Recent assignments") {
            if (recent.isEmpty()) Text("Nothing assigned yet.", color = KitMuted, fontSize = 14.sp)
            recent.forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.str("task") ?: "Task", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.str("professional") ?: "", color = KitMuted, fontSize = 12.sp)
                    }
                    Pill(statusLabel(r.str("status")), statusColor(r.str("status")).copy(alpha = .14f), statusColor(r.str("status")))
                }
            }
        }
    }
}

// ─────────────────────────────── cards ───────────────────────────────

@Composable
private fun ProCard(p: Pro, b: Branding, onClick: () -> Unit, onMore: () -> Unit, onCall: () -> Unit, onMail: () -> Unit) {
    val licence = licenceTone(p.expiry)
    EntityCard(
        title = p.name, subtitle = listOfNotNull(p.code, p.company).joinToString(" · ").ifEmpty { "No code" }, accent = p.banner, onClick = onClick, onMore = onMore,
        chips = {
            Pill(statusLabel(p.status), statusColor(p.status).copy(alpha = .14f), statusColor(p.status))
            p.type?.let { Pill(it, ProBlue.copy(alpha = .14f), ProBlue) }
            p.city?.let { Pill(it, b.base.copy(alpha = .16f), b.dark) }
            licence?.let { (t, c) -> Pill(t, c.copy(alpha = .14f), c) }
        },
        footerLeft = { Text(p.phone ?: p.email ?: "No contact", fontSize = 13.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        footerRight = {
            if (p.phone != null) CardAction("✆", b.base, onCall)
            if (p.email != null) CardAction("✉", b.base, onMail)
        },
    )
}

@Composable
private fun ProCompact(p: Pro, b: Branding, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(Color.White).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Initials(p.name, 46.dp, statusColor(p.status))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(p.code, p.city).joinToString(" · "), fontSize = 12.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (p.type != null) Pill(p.type, b.base.copy(alpha = .16f), b.dark)
        Text("⋯", fontSize = 22.sp, color = KitMuted, modifier = Modifier.clip(CircleShape).clickable { onMore() }.padding(horizontal = 10.dp))
    }
}

// ─────────────────────────────── types ───────────────────────────────

private fun LazyListScope.types(types: List<JSONObject>, b: Branding, onOpen: (JSONObject) -> Unit) {
    item { Spacer(Modifier.height(14.dp)) }
    if (types.isEmpty()) item { Text("No types yet — tap + to add one.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp)) }
    items(types, key = { it.optInt("id") }) { t ->
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .background(Color.White).clickable { onOpen(t) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(b.base.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Text(t.optString("name").take(1).uppercase(), color = b.dark, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(t.optString("name"), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.str("description") ?: "${t.optInt("professionals")} professional${if (t.optInt("professionals") == 1) "" else "s"}", color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${t.optInt("professionals")}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp)
                if (t.optBoolean("system")) Pill("System", Color(0xFFEDEBE7), KitMuted)
                else Pill(if (t.optBoolean("active")) "Active" else "Inactive", (if (t.optBoolean("active")) KitGood else KitMuted).copy(alpha = .14f), if (t.optBoolean("active")) KitGood else KitMuted)
            }
        }
    }
}

/** Add (empty [type]) or edit a professional type; system types are read-only; unused types can be deleted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSheet(api: ApiClient, type: JSONObject, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = type.optInt("id", 0)
    val system = type.optBoolean("system")
    var name by remember { mutableStateOf(type.optString("name")) }
    var description by remember { mutableStateOf(type.str("description").orEmpty()) }
    var active by remember { mutableStateOf(if (id == 0) true else type.optBoolean("active")) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (id == 0) "New type" else if (system) type.optString("name") else "Edit type", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            if (system) {
                Text("System types can't be edited or deleted.", color = KitMuted, fontSize = 14.sp)
            } else {
                Input("Name *", name, { name = it }, error)
                Input("Description", description, { description = it }, singleLine = false)
                SwitchRow("Active", "Inactive types are hidden when adding a professional.", active) { active = it }
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0F172A)).clickable(enabled = !saving) {
                        if (name.isBlank()) { error = "Enter a name"; return@clickable }
                        saving = true; error = null
                        scope.launch {
                            try {
                                val body = JSONObject().put("name", name.trim()).put("description", description.trim().ifEmpty { JSONObject.NULL }).put("status", if (active) 1 else 0)
                                withContext(Dispatchers.IO) { if (id == 0) api.send("POST", "/professional-types", body) else api.send("PUT", "/professional-types/$id", body) }
                                Toast.makeText(context, if (id == 0) "Type created" else "Type saved", Toast.LENGTH_SHORT).show(); onChanged()
                            } catch (e: ApiException) { error = e.errors["name"] ?: e.message }
                            catch (e: Exception) { error = e.message }
                            saving = false
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Text(if (saving) "Saving…" else "Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                if (id != 0) ProSheetAction("✕", "Delete type", danger = true) { confirm = true }
            }
        }
    }
    if (confirm) SwalConfirm(
        title = "Delete this type?", text = "A type in use by professionals can't be deleted.", confirmText = "Yes, delete",
        onCancel = { confirm = false },
        onConfirm = {
            confirm = false
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { api.delete("/professional-types/$id") }
                    Toast.makeText(context, "Type deleted", Toast.LENGTH_SHORT).show(); onChanged()
                } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
            }
        },
    )
}

// ─────────────────────────────── reviews ───────────────────────────────

private fun LazyListScope.reviews(r: JSONObject, b: Branding, onOpen: (Int) -> Unit) {
    val rows = r.objects("data")
    item { Spacer(Modifier.height(14.dp)) }
    if (rows.isEmpty()) item {
        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing to review", fontWeight = FontWeight.Bold, color = KitInk)
            Text("Submitted work from professionals shows up here.", color = KitMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
    items(rows, key = { it.optInt("id") }) { a ->
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
                .background(Color.White).clickable { onOpen(a.optInt("id")) }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.str("task") ?: "Task", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(a.str("project") ?: "No project", color = KitMuted, fontSize = 12.sp)
                }
                Pill("Submitted", ProAmber.copy(alpha = .14f), ProAmber)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(a.str("professional") ?: "?", 34.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(a.str("professional") ?: "—", fontWeight = FontWeight.SemiBold, color = KitInk, fontSize = 13.sp)
                    Text(listOfNotNull(a.str("professional_code"), prettyDate(a.str("submitted_at"))?.let { "submitted $it" }).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                }
                if (a.optInt("rework_count") > 0) Pill("${a.optInt("rework_count")}× rework", KitDanger.copy(alpha = .12f), KitDanger)
            }
        }
    }
}

// ─────────────────────────────── performance ───────────────────────────────

private fun LazyListScope.performance(rows: List<JSONObject>, b: Branding, onOpenPro: (String) -> Unit) {
    item { Spacer(Modifier.height(14.dp)) }
    if (rows.isEmpty()) item {
        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No performance data yet", fontWeight = FontWeight.Bold, color = KitInk)
            Text("Professionals appear once they have been assigned tasks.", color = KitMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
    items(rows, key = { it.optString("uuid") }) { r ->
        fun pct(k: String) = if (r.isNull(k)) "—" else "${r.optInt(k)}%"
        fun hrs(k: String) = if (r.isNull(k)) "—" else "${r.optDouble(k)}h"
        val onTime = if (r.isNull("on_time_pct")) null else r.optInt("on_time_pct")
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
                .background(Color.White).clickable { onOpenPro(r.optString("uuid")) }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(r.str("name") ?: "?", 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(r.str("name") ?: "—", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp)
                    Text(listOfNotNull(r.str("code"), r.str("type")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                }
                Text("${r.optInt("total_tasks")} task${if (r.optInt("total_tasks") == 1) "" else "s"}", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 13.sp)
            }
            if (onTime != null) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { Text("On time", color = KitMuted, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("$onTime%", fontWeight = FontWeight.Bold, color = KitInk, fontSize = 12.sp) }
                ProBar(onTime / 100f, if (onTime >= 80) KitGood else if (onTime >= 50) ProAmber else KitDanger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("First-time OK", pct("first_time_approval_rate"), Modifier.weight(1f))
                Metric("Delayed", pct("delayed_pct"), Modifier.weight(1f))
                Metric("Reworks", r.optInt("rework_count").toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Avg approval", hrs("avg_approval_hours"), Modifier.weight(1f))
                Metric("Avg response", hrs("avg_response_hours"), Modifier.weight(1f))
                Metric("Approved", r.optInt("approved").toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KitInk)
        Text(label, fontSize = 10.sp, color = KitMuted, maxLines = 1)
    }
}

// ─────────────────────────────── import ───────────────────────────────

/** Upload a CSV / XLSX of professionals (name, type, email, phone, city, state, license_number) — the website's importer. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ImportSheet(api: ApiClient, onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = readPicked(context, uri) ?: return@rememberLauncherForActivityResult
        fileName = picked.name; busy = true; result = null; failed = false
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.sendForm("/professionals/import", listOf(FormPart("file", fileName = picked.name, mime = picked.mime, bytes = picked.bytes))) }
                result = r.optString("message", "Imported")
            } catch (e: Exception) { failed = true; result = (e as? ApiException)?.errors?.values?.firstOrNull() ?: e.message ?: "Import failed" }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = { onDone(result != null && !failed) }, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Import professionals", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            Text("A CSV or Excel file with these columns. A type that doesn't exist yet is created; rows with a duplicate email are skipped; each account gets a random password.", color = KitMuted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("name *", "type *", "email", "phone", "city", "state", "license_number").forEach {
                    Text(it, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (it.endsWith("*")) KitDanger else KitInk,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (it.endsWith("*")) KitDanger.copy(alpha = .1f) else Color(0xFFF1EFEB)).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0F172A)).clickable(enabled = !busy) {
                    launcher.launch(arrayOf("text/csv", "text/plain", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"))
                },
                contentAlignment = Alignment.Center,
            ) { Text(if (busy) "Importing…" else if (fileName == null) "Choose file" else "Choose another file", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            result?.let {
                Text(
                    (if (failed) "⚠ " else "✓ ") + it, color = if (failed) KitDanger else KitGood, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background((if (failed) KitDanger else KitGood).copy(alpha = .1f)).padding(12.dp),
                )
            }
        }
    }
}
