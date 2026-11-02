package com.example.gavanhomes.ui.discussion

import com.example.gavanhomes.ui.common.softShadow
import com.example.gavanhomes.ui.common.appear
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.ChatCenter
import com.example.gavanhomes.ui.common.FilterBar
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Violet = Color(0xFF6A4FB3)
private val Blue = Color(0xFF1E5FAE)
private val Orange = Color(0xFFD9822B)

private fun tagTint(tag: String?): Color = when (tag) { "PROJECT" -> KitGood; "GROUP" -> Violet; "CUSTOMER" -> Blue; "DIRECT", "STAFF", "", null -> KitMuted; else -> Orange }

/**
 * Chats from the Me hub, as one inbox: every chat you are in, across all your projects, newest message first — like WhatsApp. Each row shows
 * who (or which group), the project it belongs to, the last message and how long ago, and a red count of unread messages. Search by name,
 * project or message, or show only Unread / Groups / Direct. Tap a chat to open it. Refreshes every few seconds.
 * Data: GET /discussion/chats.
 */
@Composable
fun MyChatsScreen(api: ApiClient, b: Branding, onBack: () -> Unit, onOpenChat: (ChatLink) -> Unit) {
    var chats by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }

    LaunchedEffect(attempt) {
        error = null
        while (true) {
            try { chats = withContext(Dispatchers.IO) { api.get("/discussion/chats") }.objs("chats") }
            catch (e: Exception) { if (chats == null) { error = e.message ?: "Could not load your chats"; break } }
            delay(8000)
        }
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, "Chats", onBack, compact = true)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                val list = chats
                if (list == null) {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base)
                        else com.example.gavanhomes.ui.common.EstateError(error, color = b.base, modifier = Modifier.padding(24.dp), onRetry = { attempt++ })
                    }
                    return@Column
                }
                val passes = { c: JSONObject, f: String -> when (f) { "unread" -> c.optInt("unread") > 0; "groups" -> c.optBoolean("group"); "direct" -> !c.optBoolean("group"); else -> true } }
                FilterBar(
                    search, { search = it }, "Search chats, projects or messages…",
                    groups = listOf(FilterGroup("show", "Show", listOf("unread" to "Unread", "groups" to "Groups", "direct" to "Direct").map { (k, l) -> FilterOption(k, l, list.count { c -> passes(c, k) }) })),
                    selected = if (filter == "all") emptyMap() else mapOf("show" to setOf(filter)),
                    onApply = { m -> filter = m["show"]?.firstOrNull() ?: "all" },
                    countWith = { m -> list.count { c -> passes(c, m["show"]?.firstOrNull() ?: "all") } }, single = setOf("show"),
                )
                Spacer(Modifier.height(6.dp))
                val shown = list.filter { c ->
                    passes(c, filter) && (search.isBlank() || c.optString("name").contains(search, true) || c.optString("project_name").contains(search, true) || c.optString("preview").contains(search, true))
                }
                if (shown.isEmpty()) Text(if (list.isEmpty()) "You're not in any chat yet." else "No chats match.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
                shown.forEach { c -> ChatInboxRow(c) { ChatCenter.chats = ChatCenter.chats.filter { it.optString("conv") != c.optString("conv") }; onOpenChat(ChatLink(c.optString("project"), c.optString("conv"), c.optString("name"))) } }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ChatInboxRow(c: JSONObject, onClick: () -> Unit) {
    val unread = c.optInt("unread")
    val tag = c.optString("tag")
    Row(
        Modifier.appear().padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().softShadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(KitCard)
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Initials(c.optString("name"), size = 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.optString("name"), color = KitInk, fontWeight = if (unread > 0) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (tag.isNotBlank() && tag != "DIRECT") Text(tag, color = tagTint(tag), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(tagTint(tag).copy(alpha = .14f)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Text("▣ " + c.optString("project_name"), color = Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (c.str("last_sender")?.let { "$it: " } ?: "") + c.optString("preview"), color = if (unread > 0) KitInk else KitMuted, fontSize = 13.sp,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            c.str("time")?.let { Text(it, color = if (unread > 0) KitDanger else KitMuted, fontSize = 11.sp, fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal) }
            if (unread > 0) Box(Modifier.size(width = if (unread > 9) 28.dp else 22.dp, height = 22.dp).clip(CircleShape).background(KitDanger), contentAlignment = Alignment.Center) {
                Text(if (unread > 99) "99+" else unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
