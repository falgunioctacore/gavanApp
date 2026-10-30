package com.example.gavanhomes.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

private val Amber = Color(0xFFC47A1A)
private val Blue = Color(0xFF1E5FAE)

/** Status colours by meaning, like the dashboard's charts. */
private fun statusColor(s: String): Color = when (s.lowercase()) {
    "completed", "achieved", "approved", "active", "accepted by supplier", "awarded", "present", "available", "delivered", "cleared" -> KitGood
    "in progress", "open", "cold", "confirmed", "project", "whatsapp" -> Blue
    "pending", "warm", "draft", "booked", "in storage", "personal", "group" -> Amber
    "overdue", "missed", "hot", "rejected", "closed", "cancelled", "absent", "inactive" -> KitDanger
    else -> KitMuted
}

private fun JSONObject.rows(key: String): List<JSONObject> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()

/**
 * One of the project's modules (Tasks, Targets, Bids, Leads, Inventory, Procurements, Customers, Attendance, Assets, Accounting, Communications,
 * Files, Discussion, General, Reports) as a native list: optional tabs, a search box, the status mix, then cards, 20 at a time.
 * Data: GET /projects/{uuid}/modules/{key}?tab=&search=&page=.
 */
@Composable
fun ProjectModule(api: ApiClient, b: Branding, uuid: String, module: String) {
    var tab by remember(module) { mutableStateOf("") }
    var tabs by remember(module) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var search by remember(module) { mutableStateOf("") }
    var stats by remember(module) { mutableStateOf<List<JSONObject>>(emptyList()) }
    val rows = remember(module) { mutableStateListOf<JSONObject>() }
    var page by remember(module) { mutableIntStateOf(1) }
    var lastPage by remember(module) { mutableIntStateOf(1) }
    var total by remember(module) { mutableIntStateOf(0) }
    var loading by remember(module) { mutableStateOf(true) }
    var error by remember(module) { mutableStateOf<String?>(null) }
    var attempt by remember(module) { mutableIntStateOf(0) }

    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val q = "?page=$p&tab=${URLEncoder.encode(tab, "UTF-8")}&search=${URLEncoder.encode(search.trim(), "UTF-8")}"
            val r = withContext(Dispatchers.IO) { api.get("/projects/$uuid/modules/$module$q") }
            if (p == 1) rows.clear()
            rows.addAll(r.rows("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1); total = r.optInt("total")
            tabs = r.rows("tabs"); stats = r.rows("stats")
            if (tab.isEmpty()) tab = r.optString("tab")
        } catch (e: Exception) { error = e.message ?: "Could not load" }
        loading = false
    }
    LaunchedEffect(module, tab, attempt) { load(1) }
    LaunchedEffect(search) { if (search.isNotEmpty() || rows.isNotEmpty()) { delay(350); load(1) } }   // search as you type, after a short pause

    if (tabs.size > 1) com.example.gavanhomes.ui.common.ModulePages(module.replaceFirstChar { it.uppercase() }, tabs.map { it.optString("label") }, tabs.indexOfFirst { it.optString("key") == tab }.coerceAtLeast(0)) { tab = tabs[it].optString("key"); search = "" }

    if (module != "reports") Column(Modifier.padding(top = 10.dp)) { SearchBox(search, { search = it }, "Search…") }

    if (stats.isNotEmpty()) Row(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { s ->
            val c = statusColor(s.optString("label"))
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = .1f)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.optInt("value").toString(), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = c)
                Text(s.optString("label"), fontSize = 10.sp, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
    if (total > 0 && module != "reports") Text("$total in total", color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp))

    rows.forEach { ModuleCard(it) }

    when {
        loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
        error != null && rows.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
        }
        rows.isEmpty() -> Text("Nothing here yet.", color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
        loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base, modifier = Modifier.padding(8.dp)) }
    }
    // next page: shown as a button so a long list never loads more than the user asks for
    if (rows.isNotEmpty() && page < lastPage && !loading) {
        var more by remember(page, module) { mutableStateOf(false) }
        LaunchedEffect(more) { if (more) load(page + 1) }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { OutlinedButton(onClick = { more = true }) { Text("Load more") } }
    }
}

@Composable
private fun ModuleCard(r: JSONObject) {
    val status = r.str("status")
    val accent = r.str("color")?.takeIf { it.startsWith("#") }?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    val lines = r.optJSONArray("lines")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.optString("title"), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KitInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            r.str("subtitle")?.let { Text(it, color = KitMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (lines.isNotEmpty()) Text(lines.joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
            r.str("value")?.let { Text(it, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = accent ?: KitInk) }
            if (status != null) {
                val c = statusColor(status)
                Spacer(Modifier.height(4.dp)); Pill(status, c.copy(alpha = .14f), c)
            }
        }
    }
}
