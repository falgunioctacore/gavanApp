package com.example.gavanhomes.ui.discussion

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiConfig
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.FabAction
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.ProvideFab
import com.example.gavanhomes.ui.common.SearchBox
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.procurement.ActionButton
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.readPicked
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

private fun tagColor(t: String?) = when (t) { "PROJECT" -> KitGood; "GROUP" -> InvPurple; "CUSTOMER" -> InvBlue; null, "", "DIRECT", "STAFF" -> KitMuted; else -> Color(0xFFD9822B) }

/** A file link the server wrote with its own host name: the same path on the server this app talks to. */
internal fun served(url: String): String = runCatching { Uri.parse(url) }.getOrNull()?.let { u -> ApiConfig.BASE_URL.trimEnd('/') + (u.encodedPath ?: "") } ?: url

/**
 * The project's Discussion module, like the website's Discussion hub: the project's chats (the project group, groups and
 * one-to-one chats with staff, customers, suppliers, contractors …) with the latest message and unread counts, refreshed every
 * few seconds. The + button starts a chat or a group. Data: /projects/{uuid}/discussion-center/…  [onOpen] opens a chat.
 */
@Composable
fun ProjectDiscussion(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (String, String) -> Unit) {
    var chats by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var starting by remember { mutableStateOf<String?>(null) }                    // chat · group
    LaunchedEffect(version, attempt) {
        error = null
        while (true) {
            try { chats = withContext(Dispatchers.IO) { api.get("/projects/$uuid/discussion-center/chats") }.objs("conversations") }
            catch (e: Exception) { if (chats == null) { error = e.message ?: "Could not load"; break } }
            delay(8000)
        }
    }
    ProvideFab(listOf(FabAction("chat", "New chat", b.base, "✉"), FabAction("group", "New group", InvPurple, "☷"))) { starting = it }
    val list = chats
    if (list == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") } }
        }
    } else {
        SearchBox(search, { search = it }, "Search chats…")
        val unread = list.count { it.optInt("unread") > 0 }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All", "unread" to "Unread${if (unread > 0) " $unread" else ""}", "groups" to "Groups", "direct" to "Direct").forEach { (k, l) ->
                com.example.gavanhomes.ui.tasks.Pick(l, filter == k, b.base) { filter = k }
            }
        }
        val shown = list.filter { c ->
            (search.isBlank() || c.optString("name").contains(search, true) || c.optString("preview").contains(search, true)) && when (filter) {
                "unread" -> c.optInt("unread") > 0
                "groups" -> c.optString("type") != "direct"
                "direct" -> c.optString("type") == "direct"
                else -> true
            }
        }
        if (shown.isEmpty()) Text(if (list.isEmpty()) "No chats yet — start one with +." else "No chats match.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
        shown.forEach { c -> ChatRow(c, b) { onOpen(c.optString("uuid"), c.optString("name")) } }
    }
    starting?.let { kind -> NewChatSheet(api, b, uuid, kind == "group", onClose = { starting = null }) { conv, name -> starting = null; attempt++; onOpen(conv, name) } }
}

@Composable
private fun ChatRow(c: JSONObject, b: Branding, onClick: () -> Unit) {
    val unread = c.optInt("unread")
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Initials(c.optString("name"), size = 46.dp, dot = if (c.optBoolean("online")) KitGood else null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.optBoolean("pinned")) Text("📌 ", fontSize = 12.sp)
                    Text(c.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    c.str("tag")?.let { Spacer(Modifier.size(6.dp)); Chip(it.lowercase().replaceFirstChar { ch -> ch.uppercase() }, tagColor(it)) }
                }
                val preview = listOfNotNull(c.str("last_sender")?.let { "$it:" }, c.str("preview")).joinToString(" ")
                Text(preview.ifBlank { "No messages yet" }, color = if (unread > 0) KitInk else KitMuted, fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(c.optString("time"), color = if (unread > 0) b.base else KitMuted, fontSize = 11.sp)
                if (unread > 0) Box(Modifier.padding(top = 4.dp).size(22.dp).clip(CircleShape).background(b.base), contentAlignment = Alignment.Center) {
                    Text(if (unread > 99) "99+" else unread.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/** Pick a person to chat with, or a name and several people for a new group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(api: ApiClient, b: Branding, uuid: String, group: Boolean, onClose: () -> Unit, onStarted: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<JSONObject>?>(null) }
    var q by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try { people = withContext(Dispatchers.IO) { api.get("/projects/$uuid/discussion-center/directory") }.objs("people") }
        catch (e: Exception) { error = e.message ?: "Could not load people" }
    }
    fun start(key: String, who: String) {
        busy = true
        scope.launch {
            try { val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/discussion-center/start", JSONObject().put("member", key)) }; onStarted(r.optString("uuid"), who) }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not start the chat", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }
    fun makeGroup() {
        if (name.isBlank()) { error = "Give the group a name."; return }
        if (picked.isEmpty()) { error = "Add at least one member."; return }
        busy = true; error = null
        scope.launch {
            try { val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/discussion-center/group", JSONObject().put("name", name.trim()).put("members", JSONArray(picked.toList()))) }; onStarted(r.optString("uuid"), name.trim()) }
            catch (e: ApiException) { error = e.errors.values.firstOrNull() ?: e.message }
            catch (e: Exception) { error = e.message ?: "Could not create the group" }
            busy = false
        }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (group) "New group" else "New chat", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk, modifier = Modifier.weight(1f))
                if (group) Text(if (busy) "Creating…" else "Create${if (picked.isNotEmpty()) " (${picked.size})" else ""}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(b.base).clickable { if (!busy) makeGroup() }.padding(horizontal = 16.dp, vertical = 8.dp))
                else if (busy) CircularProgressIndicator(color = b.base, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
            if (group) Box(Modifier.padding(horizontal = 20.dp)) { Input("Group name", name, { name = it; error = null }) }
            error?.let { Text(it, color = KitDanger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
            SearchBox(q, { q = it }, "Search people…")
            val list = people
            if (list == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { if (error == null) CircularProgressIndicator(color = b.base) }
            else Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = list.filter { q.isBlank() || it.optString("name").contains(q, true) || it.optString("sub").contains(q, true) }
                if (shown.isEmpty()) Text("No one matches.", color = KitMuted, modifier = Modifier.padding(12.dp))
                shown.forEach { p ->
                    val key = p.optString("key"); val on = key in picked
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else Color(0xFFF7F5F1))
                        .clickable { if (group) { if (on) picked.remove(key) else picked.add(key) } else if (!busy) start(key, p.optString("name")) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Initials(p.optString("name"), size = 34.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.optString("name"), color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 14.sp)
                            p.str("sub")?.let { Text(it, color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        p.str("tag")?.let { Chip(it.lowercase().replaceFirstChar { c -> c.uppercase() }, tagColor(it)) }
                        if (on) Text("  ✓", color = b.base, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── a chat ─────────────────────────────

/**
 * One chat: its messages (new ones arrive every few seconds), ticks on your own (sent · delivered · seen), send a message or a
 * file, and — within an hour of sending — edit or delete your own (press and hold). The 👥 button lists the members.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(api: ApiClient, b: Branding, uuid: String, conv: String, title: String, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val base = "/projects/$uuid/discussion-center/chats/$conv"
    val messages = remember { mutableStateListOf<JSONObject>() }
    var header by remember { mutableStateOf<JSONObject?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var askDelete by remember { mutableStateOf<JSONObject?>(null) }
    var members by remember { mutableStateOf<JSONObject?>(null) }
    val listState = rememberLazyListState()

    fun merge(m: JSONObject) {
        val i = messages.indexOfFirst { it.optInt("id") == m.optInt("id") }
        if (i >= 0) messages[i] = m else messages.add(m)
    }
    fun receipts(r: JSONObject) {
        val a = r.optJSONArray("receipts") ?: return
        for (k in 0 until a.length()) {
            val x = a.getJSONObject(k); val i = messages.indexOfFirst { it.optInt("id") == x.optInt("id") }
            if (i >= 0 && messages[i].optJSONObject("receipt")?.optString("status") != x.optString("status")) messages[i] = JSONObject(messages[i].toString()).put("receipt", x)
        }
    }
    LaunchedEffect(attempt) {
        error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("$base/messages") }
            messages.clear(); messages.addAll(r.objs("messages")); header = r.optJSONObject("header"); loaded = true; onChanged()
        } catch (e: Exception) { error = e.message ?: "Could not load the chat"; return@LaunchedEffect }
        while (true) {
            delay(4000)
            runCatching { withContext(Dispatchers.IO) { api.get("$base/messages?after_id=${messages.lastOrNull()?.optInt("id") ?: 0}") } }.getOrNull()?.let { r ->
                r.objs("messages").forEach(::merge); header = r.optJSONObject("header") ?: header; receipts(r)
            }
        }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    fun send() {
        val body = text.trim(); if (body.isEmpty() || sending) return
        sending = true
        scope.launch {
            try {
                val edit = editing
                val r = withContext(Dispatchers.IO) {
                    if (edit != null) api.send("POST", "$base/messages/${edit.optInt("id")}/edit", JSONObject().put("body", body))
                    else api.send("POST", "$base/send", JSONObject().put("body", body))
                }
                r.optJSONObject("message")?.let(::merge); text = ""; editing = null
            } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not send", Toast.LENGTH_LONG).show() }
            sending = false
        }
    }
    val attach = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val file = uri?.let { readPicked(context, it) } ?: return@rememberLauncherForActivityResult
        sending = true
        scope.launch {
            try { withContext(Dispatchers.IO) { api.sendForm("$base/send", listOf(FormPart("attachment", fileName = file.name, mime = file.mime, bytes = file.bytes))) }.optJSONObject("message")?.let(::merge) }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not send the file", Toast.LENGTH_LONG).show() }
            sending = false
        }
    }

    Column(Modifier.fillMaxSize().background(KitPage).imePadding()) {
        HeroBar(b, header?.str("name") ?: title, onBack, compact = true, actions = {
            HeroButton("👥") { scope.launch { members = runCatching { withContext(Dispatchers.IO) { api.get("$base/members") } }.getOrNull() } }
        })
        header?.str("status")?.let { Text(it, color = if (header?.optBoolean("online") == true) KitGood else KitMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !loaded && error == null -> CircularProgressIndicator(color = b.base, modifier = Modifier.align(Alignment.Center))
                !loaded -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") } }
                messages.isEmpty() -> Text("No messages yet — say hello.", color = KitMuted, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
                    items(messages.size, key = { messages[it].optInt("id") }) { i ->
                        val m = messages[i]
                        if (i == 0 || messages[i - 1].optString("date") != m.optString("date")) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(m.optString("date_label"), color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        val showSender = !m.optBoolean("self") && (i == 0 || messages[i - 1].optString("sender") != m.optString("sender") || messages[i - 1].optBoolean("self"))
                        Bubble(m, b, showSender, onLong = { if (m.optBoolean("can_edit") || m.optBoolean("can_delete")) options = m }) {
                            m.str("attachment_url")?.let { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(served(url)))) }.onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() } }
                        }
                    }
                }
            }
        }
        editing?.let {
            Row(Modifier.fillMaxWidth().background(b.base.copy(alpha = .1f)).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Editing message", color = b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Cancel", color = KitMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable { editing = null; text = "" })
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editing == null) Box(Modifier.size(42.dp).clip(CircleShape).clickable(enabled = !sending) { attach.launch("*/*") }, contentAlignment = Alignment.Center) { Text("📎", fontSize = 20.sp) }
            TextField(
                value = text, onValueChange = { text = it }, placeholder = { Text("Message", color = KitMuted) }, maxLines = 5,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF3F2F0), unfocusedContainerColor = Color(0xFFF3F2F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            )
            Spacer(Modifier.size(6.dp))
            Box(Modifier.size(46.dp).clip(CircleShape).background(if (text.isBlank()) b.base.copy(alpha = .4f) else b.base).clickable { send() }, contentAlignment = Alignment.Center) {
                if (sending) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Text(if (editing != null) "✓" else "➤", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    options?.let { m ->
        ModalBottomSheet(onDismissRequest = { options = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(m.str("body") ?: m.str("attachment_name") ?: "Message", color = KitInk, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (m.optBoolean("can_edit")) ActionButton("Edit", b.base) { options = null; editing = m; text = m.optString("body") }
                if (m.optBoolean("can_delete")) SmallAction("Delete for everyone", KitDanger) { options = null; askDelete = m }
            }
        }
    }
    askDelete?.let { m ->
        SwalConfirm(title = "Delete this message?", text = "It is removed for everyone in the chat.", confirmText = "Yes, delete",
            onConfirm = { askDelete = null; scope.launch {
                try { withContext(Dispatchers.IO) { api.delete("$base/messages/${m.optInt("id")}") }.optJSONObject("message")?.let(::merge) }
                catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
            } }, onCancel = { askDelete = null })
    }
    members?.let { r ->
        ModalBottomSheet(onDismissRequest = { members = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = r.objs("members")
                Text("Members · ${list.size}", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
                list.forEach { p ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Initials(p.optString("name"), size = 34.dp, dot = if (p.optBoolean("online")) KitGood else null)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.optString("name") + if (p.optBoolean("self")) " (you)" else "", color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            p.str("sub")?.let { Text(it, color = KitMuted, fontSize = 12.sp) }
                        }
                        p.str("tag")?.let { Chip(it.lowercase().replaceFirstChar { c -> c.uppercase() }, tagColor(it)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: JSONObject, b: Branding, showSender: Boolean, onLong: () -> Unit, onOpenFile: () -> Unit) {
    val self = m.optBoolean("self")
    val ink = if (self) Color.White else KitInk
    Box(Modifier.fillMaxWidth(), contentAlignment = if (self) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (self) 18.dp else 4.dp, bottomEnd = if (self) 4.dp else 18.dp))
                .background(if (self) b.base else Color.White).combinedClickable(onClick = { if (m.str("attachment_url") != null) onOpenFile() }, onLongClick = onLong).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (showSender) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.optString("sender"), color = b.base, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                m.str("tag")?.takeIf { it != "STAFF" }?.let { Text("  ${it.lowercase()}", color = KitMuted, fontSize = 10.sp) }
            }
            when {
                m.optBoolean("deleted") -> Text("This message was deleted", color = ink.copy(alpha = .7f), fontStyle = FontStyle.Italic, fontSize = 14.sp)
                else -> {
                    m.str("attachment_url")?.let { url ->
                        if (m.optBoolean("is_image")) RemoteImage(served(url))
                        else Row(Modifier.padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp)).background(if (self) Color.White.copy(alpha = .18f) else Color(0xFFF3F2F0)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📎", fontSize = 18.sp)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(m.optString("attachment_name", "File"), color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (!m.isNull("attachment_size")) Text(com.example.gavanhomes.ui.files.fileSize(m.optLong("attachment_size")), color = ink.copy(alpha = .7f), fontSize = 11.sp)
                            }
                        }
                    }
                    m.str("body")?.takeIf { it.isNotBlank() }?.let { Text(it, color = ink, fontSize = 15.sp) }
                }
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (m.optBoolean("edited") && !m.optBoolean("deleted")) Text("edited · ", color = ink.copy(alpha = .65f), fontSize = 10.sp)
                Text(m.optString("time"), color = ink.copy(alpha = .65f), fontSize = 10.sp)
                m.optJSONObject("receipt")?.optString("status")?.let { s ->
                    Text(if (s == "sent") " ✓" else " ✓✓", color = if (s == "seen") Color(0xFF8FE3FF) else ink.copy(alpha = .65f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** A photo from the server, scaled down to fit a chat bubble. */
@Composable
private fun RemoteImage(url: String) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = URL(url).openStream().use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
                var sample = 1; while (bounds.outWidth / (sample * 2) >= 600) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val img = bitmap
    if (img == null) Box(Modifier.size(220.dp, 160.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x22000000)), contentAlignment = Alignment.Center) { Text("🖼", fontSize = 28.sp) }
    else Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 300.dp).clip(RoundedCornerShape(12.dp)))
}
