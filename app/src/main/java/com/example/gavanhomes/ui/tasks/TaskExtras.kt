package com.example.gavanhomes.ui.tasks

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiConfig
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickedFile
import com.example.gavanhomes.ui.projects.form.readPicked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

private val Blue = Color(0xFF2874F0)

/** The server builds links with its own idea of the address; the app always goes through the address it signed in with. */
internal fun localUrl(u: String): String {
    val p = Uri.parse(u)
    return ApiConfig.BASE_URL + (p.encodedPath ?: "") + (p.encodedQuery?.let { "?$it" } ?: "")
}

internal fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(localUrl(url)))) }
        .onFailure { Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show() }
}

/** A picture from the server, loaded in the background and scaled down. */
@Composable
internal fun RemoteImage(url: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = URL(localUrl(url)).readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1000) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, modifier, contentScale = contentScale)
    else Box(modifier.background(Color(0xFFE6E3DD)), contentAlignment = Alignment.Center) { Text("🖼", fontSize = 20.sp) }
}

// ───────────────────────────── discussion ─────────────────────────────

/**
 * The task's private discussion — the website's Discussion tab: a chat between the creator and the assignees, with attachments,
 * edit / delete for your own messages (within an hour), read ticks and a Gallery of what was shared.
 * Data: GET/POST /task-center/{id}/thread · …/thread/media · …/thread/{cid}/edit · …/delete. Refreshes every few seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDiscussion(api: ApiClient, b: Branding, id: Int, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var header by remember { mutableStateOf<JSONObject?>(null) }
    val messages = remember { mutableStateListOf<JSONObject>() }
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var sending by remember { mutableStateOf(false) }
    var galleryOpen by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf<JSONObject?>(null) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var askDelete by remember { mutableStateOf<JSONObject?>(null) }
    val scroll = rememberScrollState()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { readPicked(context, it) }?.let { picked = it } }

    suspend fun load() {
        try {
            val r = withContext(Dispatchers.IO) { api.get("/task-center/$id/thread") }
            header = r.optJSONObject("header"); allowed = true; error = null
            val list = r.objs("messages")
            // only touch the list when something changed, so the scroll position does not jump
            val sig = { l: List<JSONObject> -> l.map { it.optInt("id").toString() + it.optString("body") + it.optBoolean("deleted") + it.optString("receipt") } }
            if (sig(list) != sig(messages.toList())) { val grew = list.size > messages.size; messages.clear(); messages.addAll(list); if (grew) scope.launch { delay(60); scroll.animateScrollTo(scroll.maxValue) } }
        } catch (e: ApiException) {
            if (e.code == 403) allowed = false else error = e.message
        } catch (e: Exception) { error = e.message ?: "Could not load the discussion" }
    }
    LaunchedEffect(id) {
        load(); delay(100); scroll.scrollTo(scroll.maxValue)
        while (true) { delay(6000); if (!sending) load() }
    }

    fun send() {
        if (sending || (text.isBlank() && picked == null)) return
        sending = true
        val body = text.trim(); val file = picked
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (file != null) api.sendForm("/task-center/$id/thread", listOfNotNull(
                        body.takeIf { it.isNotEmpty() }?.let { FormPart("content", value = it) }, FormPart("attachment", fileName = file.name, mime = file.mime, bytes = file.bytes)))
                    else api.send("POST", "/task-center/$id/thread", JSONObject().put("content", body))
                }
                text = ""; picked = null
                Toast.makeText(context, if (file != null) "File sent" else "Message sent", Toast.LENGTH_SHORT).show()
                load(); onChanged(); delay(60); scroll.animateScrollTo(scroll.maxValue)
            } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not send", Toast.LENGTH_LONG).show() }
            sending = false
        }
    }

    fun changeMessage(call: () -> Unit, done: String) = scope.launch {
        try { withContext(Dispatchers.IO) { call() }; Toast.makeText(context, done, Toast.LENGTH_SHORT).show(); load(); onChanged() }
        catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
    }

    when (allowed) {
        null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = b.base) else Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
        }
        false -> Card("Discussion", "Private") { Text("Only the task's creator and the people it is assigned to can see this discussion.", color = KitMuted, fontSize = 14.sp) }
        else -> Column(
            Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, Color(0xFFE8E5DE), RoundedCornerShape(22.dp)),
        ) {
            // head
            Row(Modifier.fillMaxWidth().background(Color(0xFFFAF9F7)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(b.base.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text("💬", fontSize = 18.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(header?.optString("title").orEmpty(), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(header?.optString("subtitle").orEmpty(), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("Gallery", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp)).clickable { galleryOpen = true }.padding(horizontal = 12.dp, vertical = 8.dp))
            }

            // messages
            Column(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 440.dp).verticalScroll(scroll).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (messages.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 30.sp); Text("No messages yet. Start the conversation!", color = KitMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                var lastDate = ""
                messages.forEach { m ->
                    val date = m.optString("date_label")
                    if (date != lastDate) {
                        lastDate = date
                        Text(date.uppercase(), color = KitMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF2F0EB)).padding(horizontal = 14.dp, vertical = 4.dp))
                    }
                    Bubble(m, b, onOpen = { openLink(context, it) }, onLong = { if (m.optBoolean("can_edit") || m.optBoolean("can_delete")) actions = m })
                }
            }

            // composer
            Column(Modifier.fillMaxWidth().background(Color(0xFFFAF9F7)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                picked?.let { f ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF2F0EB)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📎 ${f.name}", color = KitInk, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("✕", color = KitMuted, modifier = Modifier.clickable { picked = null }.padding(4.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).border(1.5.dp, b.base, CircleShape).clickable { pick.launch("*/*") }, contentAlignment = Alignment.Center) { Text("+", color = b.base, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    Box(Modifier.weight(1f)) { Input("Type a message…", text, { text = it }) }
                    Text(
                        if (sending) "…" else "Send", color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (text.isBlank() && picked == null) KitMuted else b.base).clickable(enabled = !sending && (text.isNotBlank() || picked != null)) { send() }.padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }

    // message actions
    actions?.let { m ->
        ModalBottomSheet(onDismissRequest = { actions = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (m.optBoolean("can_edit")) SheetAction("Edit message", KitInk) { editing = m; actions = null }
                if (m.optBoolean("can_delete")) SheetAction("Delete message", KitDanger) { askDelete = m; actions = null }
            }
        }
    }
    editing?.let { m ->
        var draft by remember(m.optInt("id")) { mutableStateOf(m.optString("body")) }
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Edit message", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Input("Message", draft, { draft = it }, singleLine = false)
                Text("Save", color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (draft.isBlank()) KitMuted else b.base).clickable(enabled = draft.isNotBlank()) {
                        editing = null; changeMessage({ api.send("POST", "/task-center/$id/thread/${m.optInt("id")}/edit", JSONObject().put("content", draft.trim())) }, "Message edited")
                    }.padding(vertical = 14.dp))
            }
        }
    }
    askDelete?.let { m -> SwalConfirm(
        title = "Delete this message?", text = "It will show as deleted for everyone.", confirmText = "Yes, delete",
        onConfirm = { askDelete = null; changeMessage({ api.send("POST", "/task-center/$id/thread/${m.optInt("id")}/delete", JSONObject()) }, "Message deleted") }, onCancel = { askDelete = null },
    ) }
    if (galleryOpen) Gallery(api, id, onClose = { galleryOpen = false })
}

@Composable
private fun SheetAction(label: String, tint: Color, onClick: () -> Unit) {
    Text(label, color = tint, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F5F1)).clickable { onClick() }.padding(16.dp))
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: JSONObject, b: Branding, onOpen: (String) -> Unit, onLong: () -> Unit) {
    val self = m.optBoolean("self"); val deleted = m.optBoolean("deleted")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (self) Alignment.End else Alignment.Start) {
        Column(
            Modifier.widthIn(max = 270.dp).clip(RoundedCornerShape(14.dp)).background(if (self) b.base.copy(alpha = .16f) else Color(0xFFF2F0EB))
                .combinedClickable(onClick = {}, onLongClick = onLong).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (!self) Text(m.optString("sender"), color = b.base, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            when {
                deleted -> Text("This message was deleted", color = KitMuted, fontSize = 13.sp, fontStyle = FontStyle.Italic)
                m.optBoolean("is_image") && m.str("attachment_url") != null -> RemoteImage(m.optString("attachment_url"), Modifier.size(width = 220.dp, height = 180.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(m.optString("attachment_url")) })
                m.str("attachment_url") != null -> Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .7f)).clickable { onOpen(m.optString("attachment_url")) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (m.optBoolean("is_video")) "🎬" else "📄", fontSize = 18.sp)
                    Text(m.str("attachment_name") ?: "File", color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(start = 8.dp))
                }
                m.optString("msg_kind") == "contact" -> m.optJSONObject("meta")?.let { c -> Text("👤 ${c.optString("name")}\n${c.optString("phone")}", color = KitInk, fontSize = 13.sp) }
            }
            if (!deleted) m.str("body")?.takeIf { it.isNotBlank() }?.let { Text(it, color = KitInk, fontSize = 14.sp, lineHeight = 20.sp) }
        }
        Row(Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(m.optString("time") + if (m.optBoolean("edited")) " · edited" else "", color = KitMuted, fontSize = 10.sp)
            m.optJSONObject("receipt")?.let { r ->
                val st = r.optString("status")
                Text(if (st == "sent") " ✓" else " ✓✓", color = if (st == "seen") Blue else KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Everything shared in the discussion: pictures and videos, documents and links, grouped by month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Gallery(api: ApiClient, id: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(id) { data = runCatching { withContext(Dispatchers.IO) { api.get("/task-center/$id/thread/media") } }.getOrNull() ?: JSONObject() }

    fun groups(key: String): List<Pair<String, List<JSONObject>>> {
        val o = data?.optJSONObject(key) ?: return emptyList()
        return o.keys().asSequence().map { month -> month to (o.optJSONArray(month)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()) }.toList()
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Shared content", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            com.example.gavanhomes.ui.common.SegTabs(listOf("Media", "Docs", "Links"), tab) { tab = it }
            Column(Modifier.heightIn(min = 200.dp, max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data == null) Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else {
                    val g = groups(listOf("media", "docs", "links")[tab])
                    if (g.isEmpty()) Text("Nothing shared yet.", color = KitMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(30.dp))
                    g.forEach { (month, items) ->
                        Text(month.uppercase(), color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                        when (tab) {
                            0 -> items.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { it2 ->
                                        Box(Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(8.dp)).clickable { openLink(context, it2.optString("url")) }) {
                                            if (it2.optString("type") == "image") RemoteImage(it2.optString("url"), Modifier.fillMaxWidth().height(100.dp))
                                            else Box(Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF222222)), contentAlignment = Alignment.Center) { Text("▶", color = Color.White, fontSize = 24.sp) }
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            1 -> items.forEach { d -> GalleryRow("📄", d.optString("name"), d.optString("date")) { openLink(context, d.optString("url")) } }
                            else -> items.forEach { d -> GalleryRow("🔗", d.optString("url"), d.optString("date")) { openLink(context, d.optString("url")) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryRow(icon: String, title: String, date: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F5F1)).clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 18.sp)
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = KitInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(date, color = KitMuted, fontSize = 11.sp)
        }
    }
}

// ───────────────────────────── assign ─────────────────────────────

/**
 * The website's Assign tab: who the task is with now, pick people (staff, suppliers, professionals), say why (remarks are required),
 * and the append-only assignment history. Data: GET/POST /task-center/{id}/assign.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskAssignTab(api: ApiClient, b: Branding, id: Int, taskName: String, canEdit: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Set<String>?>(null) }
    var reason by remember { mutableStateOf("") }
    var reasonError by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(id, attempt) {
        try {
            val d = withContext(Dispatchers.IO) { api.detail("/task-center/$id/assign") }
            info = d; chosen = d.objs("assigned").map { it.optString("key") }.toSet()
        } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    val d = info
    if (d == null) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { if (error == null) CircularProgressIndicator(color = b.base) else Text(error ?: "", color = KitMuted) }
        return
    }
    val everyone = listOf("Staff" to d.objs("users"), "Suppliers" to d.objs("suppliers"), "Professionals" to d.objs("professionals"))
    val names = everyone.flatMap { it.second }.associate { it.optString("key") to it.optString("name") }
    val sel = chosen ?: emptySet()

    Card("Assign task", taskName) {
        Text("CURRENT ASSIGNEES", color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        val current = d.objs("assigned")
        if (current.isEmpty()) Text("No assignees yet.", color = KitMuted, fontSize = 14.sp)
        current.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(a.optString("name"), size = 36.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(a.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("${a.optString("type")} · ${a.optString("at")}", color = KitMuted, fontSize = 11.sp)
                }
            }
        }
    }

    if (canEdit) Card("Add / update assignees") {
        com.example.gavanhomes.ui.projects.form.PickerField("Assignees", sel.takeIf { it.isNotEmpty() }?.map { names[it] ?: it }?.let { if (it.size <= 2) it.joinToString(", ") else "${it.take(2).joinToString(", ")} +${it.size - 2}" }) { picker = true }
        Input("Remarks (why?)", reason, { reason = it; reasonError = null }, reasonError, singleLine = false)
        Text(
            if (saving) "Assigning…" else "Assign task", color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) {
                if (reason.isBlank()) { reasonError = "Remarks are required when assigning a task"; return@clickable }
                saving = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.send("POST", "/task-center/$id/assign", JSONObject().put("assignees", JSONArray(sel.toList())).put("reason", reason.trim())) }
                        Toast.makeText(context, "Task assigned", Toast.LENGTH_SHORT).show(); reason = ""; onChanged(); attempt++
                    } catch (e: ApiException) { reasonError = e.errors["reason"] ?: e.message }
                    catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not assign", Toast.LENGTH_LONG).show() }
                    saving = false
                }
            }.padding(vertical = 14.dp),
        )
    }

    val history = d.objs("history")
    Card("Assignment history", "Every assignment, with who made it and why") {
        if (history.isEmpty()) Text("No assignment history yet.", color = KitMuted, fontSize = 14.sp)
        history.forEach { h ->
            Row {
                Initials(h.optString("name"), size = 34.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(h.optString("name"), color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Assigned by ${h.optString("by")} · ${h.optString("at")}", color = KitMuted, fontSize = 11.sp)
                    h.str("reason")?.let { Text("Reason: $it", color = KitInk, fontSize = 12.sp, lineHeight = 16.sp) }
                }
            }
        }
    }

    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = Color.White, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Assign to", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk, modifier = Modifier.weight(1f))
                Text("Done", color = b.base, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picker = false }.padding(8.dp))
            }
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                everyone.forEach { (group, rows) ->
                    if (rows.isEmpty()) return@forEach
                    Text(group.uppercase(), color = KitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 8.dp))
                    rows.forEach { p ->
                        val key = p.optString("key"); val on = key in sel
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else Color(0xFFF7F5F1))
                                .clickable { chosen = if (on) sel - key else sel + key }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Initials(p.optString("name"), size = 32.dp)
                            Text(p.optString("name"), color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (on) b.base else Color.Transparent).border(2.dp, if (on) b.base else Color(0xFF9E9E9E), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                if (on) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
