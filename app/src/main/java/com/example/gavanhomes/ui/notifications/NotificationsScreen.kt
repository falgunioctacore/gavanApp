package com.example.gavanhomes.ui.notifications

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.NotificationCenter
import com.example.gavanhomes.ui.professionals.ProFilterChip
import com.example.gavanhomes.ui.professionals.objects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** "5 min ago", "Yesterday, 4:12 PM", "12 Oct, 4:12 PM" — from Laravel's ISO timestamp. */
private fun whenText(iso: String?): String {
    val t = runCatching { Instant.parse(iso!!) }.getOrNull() ?: return iso.orEmpty()
    val zone = ZoneId.systemDefault()
    val local = t.atZone(zone)
    val mins = ChronoUnit.MINUTES.between(t, Instant.now())
    val clock = local.format(DateTimeFormatter.ofPattern("h:mm a"))
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "$mins min ago"
        local.toLocalDate() == LocalDate.now(zone) -> "Today, $clock"
        local.toLocalDate() == LocalDate.now(zone).minusDays(1) -> "Yesterday, $clock"
        else -> local.format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
    }
}

/**
 * Notifications, native: the signed-in user's notifications, newest first (GET /notifications), All / Unread, tap one to mark it read,
 * ✓✓ marks everything read. The unread count here is the same one every top bar's bell shows.
 */
@Composable
fun NotificationsScreen(api: ApiClient, b: Branding, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<JSONObject>() }
    var page by remember { mutableIntStateOf(0) }
    var lastPage by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("/notifications?page=$p") }
            if (p == 1) rows.clear()
            rows.addAll(r.objects("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1)
            NotificationCenter.unread = r.optInt("unread", NotificationCenter.unread)
        } catch (e: Exception) { error = e.message ?: "Could not load notifications" }
        loading = false
    }
    LaunchedEffect(reload) { load(1) }

    fun markRead(index: Int, n: JSONObject) {
        if (n.optBoolean("read")) return
        rows[index] = JSONObject(n.toString()).put("read", true)
        NotificationCenter.unread = (NotificationCenter.unread - 1).coerceAtLeast(0)
        scope.launch { runCatching { withContext(Dispatchers.IO) { api.markNotificationRead(n.optString("id")) } } }
    }

    val shown = rows.withIndex().filter { !unreadOnly || !it.value.optBoolean("read") }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                HeroBar(
                    b, "Notifications", onBack,
                    actions = {
                        if (NotificationCenter.unread > 0) {
                            HeroButton("✓✓") {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { api.markAllNotificationsRead() }
                                        for (i in rows.indices) rows[i] = JSONObject(rows[i].toString()).put("read", true)
                                        NotificationCenter.unread = 0
                                        Toast.makeText(context, "All marked as read", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not update", Toast.LENGTH_LONG).show() }
                                }
                            }
                        }
                    },
                    showBell = false,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Counter(NotificationCenter.unread.toString(), "Unread", Modifier.weight(1f))
                        Counter(rows.size.toString() + if (page < lastPage) "+" else "", "Loaded", Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProFilterChip("All", null, null, !unreadOnly) { unreadOnly = false }
                    ProFilterChip("Unread", NotificationCenter.unread.takeIf { it > 0 }, Color(0xFFD03B3B), unreadOnly) { unreadOnly = true }
                }
            }

            if (error != null && rows.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { reload++ }) { Text("Retry") }
                }
            } else if (loading && rows.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) } }
            else if (shown.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔔", fontSize = 34.sp)
                    Text(if (unreadOnly) "You're all caught up" else "No notifications yet", fontWeight = FontWeight.Bold, color = KitInk, modifier = Modifier.padding(top = 8.dp))
                    Text(if (unreadOnly) "Nothing unread." else "Updates about your work will show up here.", color = KitMuted, fontSize = 13.sp)
                }
            }

            itemsIndexed(shown, key = { _, it -> it.value.optString("id") }) { _, (index, n) ->
                val unread = !n.optBoolean("read")
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().shadow(if (unread) 4.dp else 1.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                        .background(if (unread) Color.White else Color(0xFFFAF9F7)).clickable { markRead(index, n) }.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(if (unread) b.base else Color.Transparent))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(n.str("title") ?: "Notification", fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.SemiBold, color = KitInk, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        n.str("message")?.takeIf { it != n.str("title") }?.let { Text(it, color = KitMuted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
                        Text(whenText(n.str("created_at")), color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            if (rows.isNotEmpty() && page < lastPage) item {
                Text(
                    if (loading) "Loading…" else "Load older", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable(enabled = !loading) { scope.launch { load(page + 1) } }.padding(vertical = 15.dp),
                )
            }
        }
    }
}

@Composable
private fun Counter(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .22f)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = .85f), fontSize = 11.sp)
    }
}
