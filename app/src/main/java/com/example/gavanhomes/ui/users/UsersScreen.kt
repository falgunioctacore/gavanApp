package com.example.gavanhomes.ui.users

import com.example.gavanhomes.ui.common.*

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private fun JSONObject.roleNames(): List<String> = optJSONArray("roles")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
private fun String.headline(): String = replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

/**
 * Native copy of the website's Users & Roles page: hero, Overview / Users / Roles tabs.
 * Data: GET /api/mobile/admin/users and /roles. Tapping a person opens their website profile.
 */
@Composable
fun UsersScreen(api: ApiClient, b: Branding, onBack: () -> Unit, onOpenWeb: (String) -> Unit) {
    var users by remember { mutableStateOf<List<JSONObject>?>(null) }
    var roles by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        error = null
        try {
            users = api.listAll("users")
            roles = withContext(Dispatchers.IO) { api.list("roles").items }
        } catch (e: Exception) { error = e.message ?: "Could not load users" }
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        val all = users
        if (all == null) {
            HeroBar(b, "Roles & Permissions", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = b.base)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { reload++ }) { Text("Retry") }
                }
            }
            return@Column
        }

        val active = all.count { it.str("status") == "active" }
        val pending = all.count { it.str("status") == "pending" }
        val inactive = all.size - active - pending
        val shown = all.filter { u ->
            val q = query.trim().lowercase()
            (roleFilter == null || u.roleNames().contains(roleFilter)) &&
                (q.isEmpty() || listOfNotNull(u.str("name"), u.str("email"), u.str("department")).any { it.lowercase().contains(q) } || u.roleNames().any { it.lowercase().contains(q) })
        }
        val maxPerms = (roles.maxOfOrNull { it.optInt("permissions_count") } ?: 1).coerceAtLeast(1)

        LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                HeroBar(b, "Roles & Permissions", onBack) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { HeroNumber(all.size.toString(), "team members") }
                        Row {
                            all.take(5).forEachIndexed { i, u -> Box(Modifier.padding(start = if (i == 0) 0.dp else 0.dp)) { Initials(u.str("name") ?: "?", 34.dp) } }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HeroChips("$active active", "${roles.size} roles", "$pending pending")
                }
            }
            item { SegTabs(listOf("Overview", "Users ${all.size}", "Roles ${roles.size}"), tab) { tab = it } }

            when (tab) {
                0 -> {
                    item {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Inactive" to inactive, "Pending" to pending, "Roles" to roles.size, "Total" to all.size).forEach { (l, v) ->
                                Column(
                                    Modifier.weight(1f).shadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(v.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
                                    Text(l, fontSize = 10.sp, color = KitMuted)
                                }
                            }
                        }
                    }
                    item { Text("Roles", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KitInk, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)) }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            roles.forEach { r -> RoleTile(r, maxPerms, b, Modifier.size(width = 112.dp, height = 138.dp)) }
                        }
                    }
                    item { Text("Recently added", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KitInk, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)) }
                    items(all.take(5)) { u -> PersonRow(u, b) { onOpenWeb("/admin/users/${u.optInt("id")}") } }
                }
                1 -> {
                    item { SearchBox(query, { query = it }, "Search people…") }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip("All ${all.size}", roleFilter == null) { roleFilter = null }
                            roles.forEach { r -> FilterChip("${r.optString("name").headline()} ${r.optInt("users_count")}", roleFilter == r.optString("name")) { roleFilter = r.optString("name") } }
                        }
                    }
                    if (shown.isEmpty()) item { Text("No one matches", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp)) }
                    items(shown) { u -> PersonRow(u, b) { onOpenWeb("/admin/users/${u.optInt("id")}") } }
                }
                else -> {
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
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        color = if (on) Color.White else KitInk,
        modifier = Modifier.shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF0F172A) else Color.White)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun PersonRow(u: JSONObject, b: Branding, onClick: () -> Unit) {
    val name = u.str("name") ?: "User"
    val role = u.roleNames().firstOrNull()
    val status = u.str("status")
    val dot = when (status) { "active" -> KitGood; "pending" -> Color(0xFFF59E0B); else -> Color(0xFF9AA5B8) }
    val admin = role.equals("admin", true) || role.equals("superAdmin", true)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(Color.White).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Initials(name, 46.dp, dot)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KitInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(u.str("department") ?: u.str("email") ?: "", fontSize = 12.sp, color = KitMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (role != null) Pill(role.headline(), if (admin) Color(0xFFFFF1E0) else b.base.copy(alpha = .16f), if (admin) Color(0xFFC2410C) else b.dark)
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
