package com.example.gavanhomes.ui.users

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.Initials
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
import com.example.gavanhomes.ui.common.SegTabs
import com.example.gavanhomes.ui.common.listAllMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Banners = listOf(
    Color(0xFF5B9BFF) to Color(0xFF2F6FE4), Color(0xFF1BAF7A) to Color(0xFF0E8A7E), Color(0xFFF59E0B) to Color(0xFFC2410C),
    Color(0xFF8B5CF6) to Color(0xFF4A3AA7), Color(0xFFEC4899) to Color(0xFF9D174D), Color(0xFF475569) to Color(0xFF1E293B),
)
private val Amber = Color(0xFFF59E0B)
private val SortNames = listOf("Newest", "Name A–Z", "Role")

private fun statusColor(s: String?): Color = when (s) { "active" -> KitGood; "pending" -> Amber; else -> Color(0xFF9AA5B8) }

/** One person from GET /users, with the bits the cards need. */
private class U(val json: JSONObject) {
    val id = json.optInt("id")
    val name = json.str("name") ?: "User"
    val email = json.str("email")
    val phone = json.str("phone")
    val status = json.str("status")
    val department = json.str("department")
    val role = json.optJSONArray("roles")?.let { if (it.length() > 0) it.getString(0) else null }
    val sites = json.optInt("sites_count")
    val admin = role == "admin" || role == "superAdmin"
    val banner = Banners[(id % Banners.size + Banners.size) % Banners.size]
    fun matches(q: String) = q.isEmpty() || listOfNotNull(name, email, department, role, phone).any { it.lowercase().contains(q) }
}

/**
 * Users: header counters, search, status + role filters, sort and a cards / compact switch (like Projects), plus a Roles tab.
 * Data: GET /api/mobile/admin/users and /roles. A person opens their native details; "+" (super admins) adds one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(api: ApiClient, b: Branding, onBack: () -> Unit, onOpenUser: (Int) -> Unit, onAdd: () -> Unit, onEditUser: (Int) -> Unit) {
    val context = LocalContext.current
    var users by remember { mutableStateOf<List<U>?>(null) }
    var roles by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var canCreate by remember { mutableStateOf(false) }
    var canEdit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }   // chosen filters: group → values
    var sort by remember { mutableIntStateOf(0) }
    var compact by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<U?>(null) }
    var refreshing by remember { mutableStateOf(false) }                      // pull-down refresh in progress
    LaunchedEffect(users) { refreshing = false }                         // fresh data arrived
    LaunchedEffect(refreshing) { if (refreshing) { kotlinx.coroutines.delay(10_000); refreshing = false } }   // never spin forever

    LaunchedEffect(reload) {
        error = null
        try {
            val (items, meta) = api.listAllMeta("users")
            users = items.map(::U)
            canCreate = meta.optJSONObject("can")?.optBoolean("create") == true
            canEdit = meta.optJSONObject("can")?.optBoolean("edit") == true
            roles = withContext(Dispatchers.IO) { api.list("roles").items }
        } catch (e: Exception) { error = e.message ?: "Could not load users" }
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        val all = users
        if (all == null) {
            HeroBar(b, "Users", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = b.base)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { reload++ }) { Text("Retry") }
                }
            }
            return@Column
        }

        val active = all.count { it.status == "active" }
        val pending = all.count { it.status == "pending" }
        val inactive = all.size - active - pending
        val passes = { u: U, m: Map<String, Set<String>> -> m["status"].orEmpty().let { s -> s.isEmpty() || u.status in s } && m["role"].orEmpty().let { r -> r.isEmpty() || u.role in r } }
        val shown = all
            .filter { u -> passes(u, sel) && u.matches(query.trim().lowercase()) }
            .let { list ->
                when (sort) {
                    1 -> list.sortedBy { it.name.lowercase() }
                    2 -> list.sortedWith(compareBy({ it.role.orEmpty() }, { it.name.lowercase() }))
                    else -> list.sortedByDescending { it.id }
                }
            }
        val maxPerms = (roles.maxOfOrNull { it.optInt("permissions_count") } ?: 1).coerceAtLeast(1)

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // ── header: title, add / refresh, and live counters ──
            item {
                HeroBar(
                    b, "Users", onBack,
                    actions = {
                        if (canCreate) HeroButton("+") { onAdd() }
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Counter(all.size.toString(), "People", Modifier.weight(1.2f), big = true)
                        Counter(active.toString(), "Active", Modifier.weight(1f))
                        Counter(pending.toString(), "Pending", Modifier.weight(1f))
                        Counter(inactive.toString(), "Inactive", Modifier.weight(1f))
                    }
                }
            }
            item { SegTabs(listOf("People ${all.size}", "Roles ${roles.size}"), tab) { tab = it } }

            if (tab == 0) {
                item {
                    FilterBar(
                        query, { query = it }, "Search people…",
                        groups = listOf(
                            FilterGroup("status", "Status", listOf("active", "pending", "inactive").map { st -> FilterOption(st, st.headline(), all.count { u -> u.status == st }, statusColor(st)) }),
                            FilterGroup("role", "Role", all.mapNotNull { u -> u.role }.distinct().sorted().map { r -> FilterOption(r, r.headline(), all.count { u -> u.role == r }) }),
                        ),
                        selected = sel, onApply = { sel = it },
                                                countWith = { m -> all.count { u -> passes(u, m) && u.matches(query.trim().lowercase()) } },
                        sortOptions = SortNames, sortIndex = sort, onSort = { sort = it },
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${shown.size} ${if (shown.size == 1) "person" else "people"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        com.example.gavanhomes.ui.common.ViewToggle(compact) { compact = it }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }

                if (shown.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No one matches", fontWeight = FontWeight.Bold, color = KitInk)
                        Text("Try another search or clear the filters.", color = KitMuted, fontSize = 13.sp)
                    }
                }
                items(shown, key = { it.id }) { u ->
                    if (compact) CompactRow(u, b, onClick = { onOpenUser(u.id) }, onMore = { sheet = u })
                    else UserCard(
                        u, b, onClick = { onOpenUser(u.id) }, onMore = { sheet = u },
                        onCall = { u.phone?.let { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) } },
                        onMail = { u.email?.let { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$it"))) } },
                    )
                }
            } else {
                items(roles.chunked(2)) { pair ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { r -> RoleTile(r, maxPerms, b, Modifier.weight(1f).height(150.dp)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        }
    }

    // ── ⋯ actions ──
    sheet?.let { u ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(u.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text(u.role?.headline() ?: "No role", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                SheetAction("◉", "Open details") { sheet = null; onOpenUser(u.id) }
                if (canEdit) SheetAction("✎", "Edit user") { sheet = null; onEditUser(u.id) }
                u.phone?.let { n -> SheetAction("✆", "Call $n") { sheet = null; context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) } }
                u.email?.let { m -> SheetAction("✉", m) { sheet = null; context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$m"))) } }
            }
        }
    }
}

// ─────────────────────────────── pieces ───────────────────────────────

@Composable
private fun Counter(value: String, label: String, modifier: Modifier, big: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = if (big) .26f else .18f)).padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontSize = if (big) 28.sp else 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = .85f), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun FilterChip(label: String, count: Int, dot: Color?, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF0F172A) else Color.White)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.size(7.dp)) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1)
        Text(" $count", fontSize = 13.sp, color = if (on) Color.White.copy(alpha = .7f) else KitMuted)
    }
}

@Composable
private fun UserCard(u: U, b: Branding, onClick: () -> Unit, onMore: () -> Unit, onCall: () -> Unit, onMail: () -> Unit) {
    EntityCard(
        title = u.name, subtitle = u.email ?: "No email", accent = u.banner, onClick = onClick, onMore = onMore,
        chips = {
            if (u.role != null) { val c = if (u.admin) Color(0xFFC2410C) else Color(0xFF2F6FE4); Pill(u.role.headline(), c.copy(alpha = .14f), c) }
            Pill((u.status ?: "—").headline(), statusColor(u.status).copy(alpha = .14f), statusColor(u.status))
            if (u.department != null) Pill(u.department, b.base.copy(alpha = .16f), b.dark)
        },
        footerLeft = { CardStat("${u.sites}", if (u.sites == 1) "site" else "sites") },
        footerRight = {
            if (u.phone != null) CardAction("✆", b.base, onCall)
            if (u.email != null) CardAction("✉", b.base, onMail)
        },
    )
}

@Composable
private fun CompactRow(u: U, b: Branding, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(Color.White).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Initials(u.name, 46.dp, statusColor(u.status))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(u.department ?: u.email ?: "", fontSize = 12.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (u.role != null) Pill(u.role.headline(), if (u.admin) Color(0xFFFFF1E0) else b.base.copy(alpha = .16f), if (u.admin) Color(0xFFC2410C) else b.dark)
        Text("⋯", fontSize = 22.sp, color = KitMuted, modifier = Modifier.clip(CircleShape).clickable { onMore() }.padding(horizontal = 10.dp))
    }
}

@Composable
private fun RoundAction(glyph: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = .15f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SheetAction(glyph: String, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) { Text(glyph, fontSize = 17.sp, color = KitInk) }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = KitInk, modifier = Modifier.padding(start = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoleTile(r: JSONObject, maxPerms: Int, b: Branding, modifier: Modifier) {
    val frac = r.optInt("permissions_count").toFloat() / maxPerms
    Column(
        modifier.shadow(6.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(64.dp).drawBehind {
                val stroke = 7.dp.toPx(); val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(b.base.copy(alpha = .18f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                drawArc(b.base, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            },
            contentAlignment = Alignment.Center,
        ) { Text(r.optInt("users_count").toString(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk) }
        Spacer(Modifier.height(8.dp))
        Text(r.optString("name").headline(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${r.optInt("permissions_count")} perms" + if (r.optBoolean("protected")) " · system" else "", fontSize = 11.sp, color = KitMuted)
    }
}
