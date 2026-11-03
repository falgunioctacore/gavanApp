package com.example.gavanhomes.ui.files

import com.example.gavanhomes.ui.common.AppToast

import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.Downloads
import com.example.gavanhomes.ui.common.FabAction
import com.example.gavanhomes.ui.common.FilterBar
import com.example.gavanhomes.ui.common.FilterGroup
import com.example.gavanhomes.ui.common.FilterOption
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.ModulePages
import com.example.gavanhomes.ui.common.ProvideFab
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.Paged
import com.example.gavanhomes.ui.inventory.PeoplePick
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.inventory.enc
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.Choice
import com.example.gavanhomes.ui.leads.ChoiceSheet
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.leads.whenLabel
import com.example.gavanhomes.ui.procurement.ActionButton
import com.example.gavanhomes.ui.professionals.ProKpi
import com.example.gavanhomes.ui.professionals.ProKpiTiles
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickedFile
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.projects.form.readPicked
import com.example.gavanhomes.ui.tasks.Card
import com.example.gavanhomes.ui.tasks.Fact
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun fileSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun extColor(ext: String) = when (ext) {
    "pdf" -> KitDanger; "jpg", "jpeg", "png", "gif", "webp" -> InvPurple; "doc", "docx" -> InvBlue
    "xls", "xlsx", "csv" -> KitGood; "zip", "rar" -> InvAmber; "dwg", "dxf" -> Color(0xFF4A8AAD); else -> KitMuted
}

private fun mimeOf(name: String, ext: String) =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.ifBlank { name.substringAfterLast('.', "") }.lowercase()) ?: "application/octet-stream"

/** The file's little badge: its extension on a tinted square (▤ for a folder). */
@Composable
private fun Badge(text: String, c: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
        Text(text, color = c, fontWeight = FontWeight.ExtraBold, fontSize = if (text.length > 3) 11.sp else 13.sp, maxLines = 1)
    }
}

/** The pages, like the website's File Manager sidebar: the dashboard, the four file views, then requests, audit trail and trash. */
private class Page(val key: String, val label: String, val location: String? = null)
private val Pages = listOf(
    Page("dashboard", "Dashboard"), Page("all", "All files", "all"), Page("mine", "My files", "my_files"), Page("shared", "Shared with me", "shared_with_me"),
    Page("recent", "Recent files", "recent"), Page("requests", "Access requests"), Page("audit", "Audit trail"), Page("trash", "Trash"),
)
private val Types = listOf("pdf" to "PDF", "image" to "Images", "word" to "Word", "sheet" to "Excel / sheets", "video" to "Video", "zip" to "Archives", "text" to "Text")

/**
 * The project's Files module, like the website's File Manager. Dashboard: its numbers, storage against the quota, quick access by
 * department, categories, recent uploads and waiting access requests. All files (folders you drill into, with the path back) /
 * My files / Shared with me / Recent, each searchable and filtered by department, category and type. A file: open (saved to
 * Downloads, counted as a view), details & history, rename, move, share, new version, delete — or ask for access when it is in a
 * locked folder. Access requests (admins approve / deny), the audit trail of who opened what, and the trash.
 * The + button uploads a file into the open folder or makes a new folder. Data: /projects/{uuid}/file-center/…
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFiles(api: ApiClient, b: Branding, uuid: String, version: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    val path = remember { mutableStateListOf<Pair<String, String>>() }        // the open folder and its parents: id, name
    var search by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var local by remember { mutableIntStateOf(0) }
    var static by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<JSONObject?>(null) }
    var folderOptions by remember { mutableStateOf<JSONObject?>(null) }
    var details by remember { mutableStateOf<Int?>(null) }
    var renaming by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }   // file · folder
    var moving by remember { mutableStateOf<JSONObject?>(null) }
    var sharing by remember { mutableStateOf<JSONObject?>(null) }
    var replacing by remember { mutableStateOf<JSONObject?>(null) }
    var asking by remember { mutableStateOf<JSONObject?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }  // file · folder · forever
    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var quota by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    val folderId = path.lastOrNull()?.first
    val v = version + local

    LaunchedEffect(v) {
        error = null
        try { overview = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/file-center/overview") } } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    val current = Pages[page]
    BackHandler(enabled = current.key == "all" && path.isNotEmpty()) { path.removeAt(path.lastIndex) }

    val pickUpload = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { readPicked(context, it) }?.let { picked = it } }
    val pickVersion = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val f = replacing ?: return@rememberLauncherForActivityResult
        val file = uri?.let { readPicked(context, it) } ?: run { replacing = null; return@rememberLauncherForActivityResult }
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.sendForm("/projects/$uuid/file-center/files/${f.optString("id")}/replace", listOf(FormPart("file", fileName = file.name, mime = file.mime, bytes = file.bytes))) }
                AppToast.show(r.str("message") ?: "New version uploaded"); local++
            } catch (e: Exception) { AppToast.show(e.message ?: "Could not upload") }
            replacing = null
        }
    }
    val can = overview?.optJSONObject("can") ?: JSONObject()
    val inFiles = current.location != null
    ProvideFab(if (can.optBoolean("create") && !(current.key == "all" && static)) listOf(FabAction("upload", "Upload file", b.base, "⇪"), FabAction("folder", "New folder", InvAmber, "▤")) else emptyList()) {
        if (it == "upload") pickUpload.launch("*/*") else newFolder = true
    }
    ModulePages("Documents", Pages.map { it.label }, page) { page = it; if (Pages[it].key != "all") path.clear() }

    fun open(f: JSONObject) {
        val id = f.optString("id"); val name = f.optString("name"); val ext = f.optString("ext")
        busy = id
        scope.launch {
            try {
                val (served, bytes) = withContext(Dispatchers.IO) { api.download("/projects/$uuid/file-center/files/$id/download") }
                runCatching { withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/files/$id/view", JSONObject()) } }
                val file = if (served != "export") served else if (ext.isNotBlank() && !name.endsWith(".$ext", true)) "$name.$ext" else name
                val mime = mimeOf(file, ext)
                val uri = withContext(Dispatchers.IO) { Downloads.save(context, file, mime, bytes) }
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(view) }.onFailure { AppToast.show("Saved to Downloads/GavanHomes") }
            } catch (e: Exception) { AppToast.show(e.message ?: "Could not open the file") }
            busy = null
        }
    }
    fun act(done: String, call: suspend () -> Unit) {
        scope.launch {
            try { withContext(Dispatchers.IO) { call() }; AppToast.show(done); local++ }
            catch (e: Exception) { AppToast.show(e.message ?: "Could not do that") }
        }
    }

    when {
        current.key == "dashboard" -> {
            com.example.gavanhomes.ui.common.ModuleTrend(api, uuid, "files", version)
            val o = overview
            if (o == null) Loading(b, error) { local++ } else Dashboard(o, b, can, onPage = { k, dept -> page = Pages.indexOfFirst { it.key == k }; if (dept != null) sel = mapOf("department_id" to setOf(dept)) }, onQuota = { quota = true }, onOpen = { details = it })
        }
        inFiles -> {
            val o = overview
            val groups = listOfNotNull(
                o?.objs("departments")?.takeIf { it.isNotEmpty() }?.let { d -> FilterGroup("department_id", "Department", d.map { FilterOption(it.optString("id"), it.optString("name"), it.optInt("files")) }) },
                o?.objs("categories")?.takeIf { it.isNotEmpty() }?.let { c -> FilterGroup("category_id", "Category", c.map { FilterOption(it.optString("id"), it.optString("name"), it.optInt("files")) }) },
                FilterGroup("type", "Type", Types.map { FilterOption(it.first, it.second) }),
            )
            var total by remember { mutableIntStateOf(-1) }
            FilterBar(search, { search = it }, "Search files and folders…", groups = groups, selected = sel,
                onApply = { m -> sel = m.mapValues { (_, s) -> s.take(1).toSet() }.filterValues { it.isNotEmpty() } }, countWith = { m -> if (m == sel) total else -1 })
            val filtered = search.isNotBlank() || sel.isNotEmpty() || current.location != "all"
            if (current.key == "all" && !filtered) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("All files", color = if (path.isEmpty()) KitInk else b.base, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { path.clear() })
                path.forEachIndexed { i, (_, n) ->
                    Text("  ›  ", color = KitMuted, fontSize = 14.sp)
                    Text(n, color = if (i == path.lastIndex) KitInk else b.base, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { while (path.size > i + 1) path.removeAt(path.lastIndex) })
                }
            }
            if (current.key == "all" && static && !filtered) Text("🔒 A locked folder — ask for access to open a file.", color = InvAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            val query = buildString {
                if (current.location != "all") append("&location=").append(current.location)
                if (current.key == "all" && !filtered) folderId?.let { append("&folder_id=").append(it) }
                if (search.isNotBlank()) append("&search=").append(enc(search.trim()))
                sel.forEach { (k, s) -> s.firstOrNull()?.let { append("&$k=").append(enc(it)) } }
            }
            Paged(api, b, "/projects/$uuid/file-center/table", query, v,
                when (current.key) { "mine" -> "You haven't uploaded anything here yet."; "shared" -> "Nothing has been shared with you."; "recent" -> "Nothing uploaded in the last 7 days."; else -> if (filtered) "Nothing matches." else "This folder is empty." },
                "item", onMeta = { m -> total = m.optInt("total"); static = m.optBoolean("static") }) { r ->
                if (r.optBoolean("is_folder")) FolderRow(r, can.optBoolean("edit") || can.optBoolean("delete"), onMenu = { folderOptions = r }) {
                    if (filtered || current.key != "all") { search = ""; sel = emptyMap(); page = 1; path.clear() }
                    path.add(r.optString("id") to r.optString("name"))
                } else FileRow(r, busy == r.optString("id")) { options = r }
            }
        }
        current.key == "requests" -> Requests(api, b, uuid, v) { local++ }
        current.key == "audit" -> Paged(api, b, "/projects/$uuid/file-center/audit", "", v, "No one has opened or downloaded a file yet.", "access") { a ->
            RowCard(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(a.optString("ext").uppercase().ifBlank { "FILE" }.take(4), extColor(a.optString("ext")))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(a.optString("file_name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(a.str("accessed_by"), a.str("user_role")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                    }
                    Chip(a.optString("action").replaceFirstChar { it.uppercase() }, if (a.optString("action") == "downloaded") InvBlue else KitGood)
                }
                Text(listOfNotNull(a.str("accessed_on"), a.str("device"), a.str("browser"), a.str("ip_address")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        else -> Paged(api, b, "/projects/$uuid/file-center/trash", "", v, "The trash is empty.", "deleted file") { f ->
            RowCard(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(f.optString("ext").uppercase().ifBlank { "FILE" }.take(4), extColor(f.optString("ext")))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(f.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(fileSize(f.optLong("size")), f.str("owner"), whenLabel(f.str("deleted"))?.let { "deleted $it" }).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                    }
                }
                if (can.optBoolean("delete")) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("Restore", KitGood) { act("File restored") { api.send("POST", "/projects/$uuid/file-center/trash/${f.optInt("id")}/restore", JSONObject()) } }
                    SmallAction("Delete for good", KitDanger) { deleting = "forever" to f }
                }
            }
        }
    }

    options?.let { f ->
        val shut = f.optBoolean("locked") && !f.optBoolean("approved")
        val locked = f.optBoolean("locked")
        ModalBottomSheet(onDismissRequest = { options = null }, containerColor = com.example.gavanhomes.ui.common.KitCard) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(f.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text(listOfNotNull(fileSize(f.optLong("size")), f.str("sub_label"), f.str("uploaded_by")?.takeIf { it != "—" }).joinToString(" · "), color = KitMuted, fontSize = 13.sp)
                if (shut) ActionButton("Request access", InvAmber) { options = null; asking = f }
                else ActionButton("Open", b.base) { options = null; open(f) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("Details & history", KitInk) { options = null; details = f.optInt("id") }
                    if (can.optBoolean("edit") && !locked) {
                        SmallAction("Rename", InvBlue) { options = null; renaming = "file" to f }
                        SmallAction("Move", InvPurple) { options = null; moving = f }
                        SmallAction("Share", KitGood) { options = null; sharing = f }
                        SmallAction("New version", InvAmber) { options = null; replacing = f; pickVersion.launch("*/*") }
                    }
                    if (can.optBoolean("delete") && !locked) SmallAction("Delete", KitDanger) { options = null; deleting = "file" to f }
                }
            }
        }
    }
    folderOptions?.let { f ->
        ModalBottomSheet(onDismissRequest = { folderOptions = null }, containerColor = com.example.gavanhomes.ui.common.KitCard) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(f.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text(f.optString("sub_label"), color = KitMuted, fontSize = 13.sp)
                ActionButton("Open", b.base) { folderOptions = null; search = ""; sel = emptyMap(); page = 1; path.add(f.optString("id") to f.optString("name")) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (can.optBoolean("edit")) SmallAction("Rename folder", InvBlue) { folderOptions = null; renaming = "folder" to f }
                    if (can.optBoolean("delete")) SmallAction("Delete folder", KitDanger) { folderOptions = null; deleting = "folder" to f }
                }
            }
        }
    }
    details?.let { id -> FileDetails(api, b, uuid, id, onClose = { details = null }) }
    renaming?.let { (kind, f) ->
        TextSheet(if (kind == "folder") "Rename folder" else "Rename file", "Name", f.optString("name"), "Save", b, onClose = { renaming = null }) { name ->
            withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/${if (kind == "folder") "folders" else "files"}/${f.optString("id")}/rename", JSONObject().put("name", name)) }
            AppToast.show("Renamed"); renaming = null; local++
        }
    }
    moving?.let { f -> MoveSheet(api, b, uuid, f, onClose = { moving = null }) { moving = null; local++ } }
    sharing?.let { f -> ShareSheet(api, b, uuid, f, onClose = { sharing = null }) { sharing = null; local++ } }
    asking?.let { f ->
        TextSheet("Request access", "Why do you need “${f.optString("name")}”?", "", "Send request", b, multiline = true, onClose = { asking = null }) { reason ->
            withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/request-access", JSONObject().put("file_id", f.optString("id")).put("reason", reason)) }
            AppToast.show("Access requested — you'll be told when it's reviewed"); asking = null; local++
        }
    }
    if (newFolder) TextSheet("New folder", "Folder name", "", "Create folder", b, note = path.lastOrNull()?.let { "Inside ${it.second}" }, onClose = { newFolder = false }) { name ->
        withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/folders", JSONObject().put("name", name).put("parent_id", folderId ?: JSONObject.NULL)) }
        AppToast.show("Folder created"); newFolder = false; local++
    }
    picked?.let { p -> UploadSheet(api, b, uuid, p, path.lastOrNull(), onClose = { picked = null }) { picked = null; local++ } }
    if (quota) TextSheet("Storage quota", "Allowance for this project (GB)", overview?.optJSONObject("storage")?.optInt("quota_gb")?.toString() ?: "50", "Save", b, number = true, onClose = { quota = false }) { gb ->
        withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/quota", JSONObject().put("quota_gb", gb.toIntOrNull() ?: 0)) }
        AppToast.show("Storage quota updated"); quota = false; local++
    }
    deleting?.let { (kind, f) ->
        SwalConfirm(
            title = when (kind) { "folder" -> "Delete folder ${f.optString("name")}?"; "forever" -> "Delete ${f.optString("name")} for good?"; else -> "Delete ${f.optString("name")}?" },
            text = when (kind) { "folder" -> "Only an empty folder can be deleted."; "forever" -> "It can't be brought back."; else -> "It goes to the trash, where it can be restored." },
            confirmText = "Yes, delete",
            onConfirm = {
                deleting = null
                act(if (kind == "folder") "Folder deleted" else if (kind == "forever") "Deleted for good" else "Moved to the trash") {
                    when (kind) {
                        "folder" -> api.delete("/projects/$uuid/file-center/folders/${f.optString("id")}")
                        "forever" -> api.delete("/projects/$uuid/file-center/trash/${f.optInt("id")}")
                        else -> api.delete("/projects/$uuid/file-center/files/${f.optString("id")}")
                    }
                }
            },
            onCancel = { deleting = null },
        )
    }
}

@Composable
private fun Loading(b: Branding, error: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base)
        else com.example.gavanhomes.ui.common.EstateError(error, color = b.base, modifier = Modifier.padding(24.dp), onRetry = onRetry)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dashboard(o: JSONObject, b: Branding, can: JSONObject, onPage: (String, String?) -> Unit, onQuota: () -> Unit, onOpen: (Int) -> Unit) {
    val s = o.optJSONObject("stats") ?: JSONObject()
    Box(Modifier.padding(top = 4.dp)) {
        ProKpiTiles(listOf(
            ProKpi(s.optInt("files").toString(), "Files", InvBlue), ProKpi(s.optInt("folders").toString(), "Folders", InvAmber), ProKpi(fileSize(s.optLong("size")), "Total size", KitInk),
            ProKpi(s.optInt("recent").toString(), "New this week", KitGood), ProKpi(s.optInt("shared").toString(), "Shared", InvPurple), ProKpi(s.optInt("downloads").toString(), "Downloads", InvBlue),
        ), columns = 3)
    }
    Text("${s.optInt("views")} views (opens) in all", color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
    o.optJSONObject("storage")?.let { st ->
        val used = st.optLong("used"); val q = st.optLong("quota").coerceAtLeast(1)
        val pct = (used.toDouble() / q).coerceIn(0.0, 1.0)
        Card("Storage", "${fileSize(used)} of ${st.optInt("quota_gb")} GB used") {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(com.example.gavanhomes.ui.common.KitLine)) {
                Box(Modifier.fillMaxWidth(pct.toFloat().coerceAtLeast(.01f)).fillMaxHeight().background(if (pct > .9) KitDanger else if (pct > .7) InvAmber else KitGood))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${"%.1f".format(pct * 100)}% used · ${fileSize((q - used).coerceAtLeast(0))} free", color = KitMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (can.optBoolean("edit")) SmallAction("Change quota", b.base) { onQuota() }
            }
        }
    }
    val req = o.optJSONObject("requests") ?: JSONObject()
    if (req.optInt("total") > 0) Card("Access requests", if (can.optBoolean("review")) "Waiting for a decision" else "Your requests for locked files") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${req.optInt("pending")} pending · ${req.optInt("total")} in all", color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            SmallAction("Open", InvAmber) { onPage("requests", null) }
        }
    }
    val depts = o.objs("departments")
    if (depts.isNotEmpty()) Card("Quick access", "By department") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            depts.forEachIndexed { i, d ->
                val c = listOf(InvBlue, InvAmber, KitGood, InvPurple, KitDanger)[i % 5]
                Column(Modifier.widthIn(min = 96.dp).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = .08f)).clickable { onPage("all", d.optString("id")) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("▤  " + d.optString("name"), color = c, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
                    Text("${d.optInt("files")} file${if (d.optInt("files") == 1) "" else "s"}", color = KitMuted, fontSize = 11.sp)
                }
            }
        }
    }
    val cats = o.objs("categories").filter { it.optInt("files") > 0 }
    if (cats.isNotEmpty()) Card("Categories") { cats.forEach { c -> Fact(c.optString("name"), "${c.optInt("files")} files") } }
    val recent = o.objs("recent")
    Card("Latest uploads", if (recent.isEmpty()) "Nothing uploaded yet" else null) {
        recent.forEach { f ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(com.example.gavanhomes.ui.common.KitSoft).clickable { onOpen(f.optInt("id")) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(f.optString("ext").uppercase().ifBlank { "FILE" }.take(4), extColor(f.optString("ext")))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(f.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(fileSize(f.optLong("size")), f.str("folder"), f.str("by"), whenLabel(f.str("at"))).joinToString(" · "), color = KitMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (recent.isNotEmpty()) Text("All files  ›", color = InvBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { onPage("all", null) }.padding(vertical = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderRow(r: JSONObject, menu: Boolean, onMenu: () -> Unit, onOpen: () -> Unit) {
    RowCard(onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge("▤", InvAmber)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(r.str("sub_label"), fileSize(r.optLong("size")), r.str("modified_date")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1)
            }
            if (menu) Text("⋮", color = KitMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onMenu() }.padding(horizontal = 10.dp, vertical = 2.dp))
            else Text("›", color = KitMuted, fontSize = 20.sp)
        }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.str("department")?.let { Chip(it, InvBlue) }
            Chip(r.optString("access_label"), KitMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileRow(r: JSONObject, busy: Boolean, onClick: () -> Unit) {
    val shut = r.optBoolean("locked") && !r.optBoolean("approved")
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(r.optString("ext").uppercase().ifBlank { "FILE" }.take(4), extColor(r.optString("ext")))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(fileSize(r.optLong("size")), r.str("sub_label"), r.str("uploaded_by")?.takeIf { it != "—" }).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when { busy -> CircularProgressIndicator(color = InvBlue, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)); shut -> Text("🔒", fontSize = 16.sp) }
        }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.str("department")?.let { Chip(it, InvBlue) }
            r.str("category")?.let { Chip(it, InvPurple) }
            Chip(r.optString("access_label") + (r.str("access_sub")?.let { " · $it" } ?: ""), if (r.optBoolean("is_shared")) KitGood else KitMuted)
            Chip("⇩ ${r.optInt("downloads")}  ◉ ${r.optInt("views")}", KitMuted)
            r.str("modified_date")?.let { Chip(it, KitMuted) }
        }
    }
}

/** One file: what and where it is, who it is shared with, and everything that happened to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileDetails(api: ApiClient, b: Branding, uuid: String, id: Int, onClose: () -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) { try { d = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/file-center/files/$id") } } catch (e: Exception) { error = e.message ?: "Could not load" } }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val x = d
        if (x == null) { Loading(b, error) {}; Spacer(Modifier.height(40.dp)); return@ModalBottomSheet }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(x.optString("ext").uppercase().ifBlank { "FILE" }.take(4), extColor(x.optString("ext")))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(x.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                    Text(listOfNotNull(fileSize(x.optLong("size")), x.str("folder")).joinToString(" · "), color = KitMuted, fontSize = 13.sp)
                }
            }
            x.str("description")?.let { Text(it, color = KitInk, fontSize = 14.sp) }
            Fact("Uploaded by", x.str("by")); Fact("Uploaded", whenLabel(x.str("created"))); Fact("Last changed", whenLabel(x.str("updated")))
            Fact("Department", x.str("department")); Fact("Category", x.str("category")); Fact("Type", x.str("type"))
            Fact("Downloads", x.optInt("downloads").toString()); Fact("Views", x.optInt("views").toString())
            if (x.optBoolean("locked")) Text("🔒 In a locked folder", color = InvAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val shares = x.objs("shares")
            Text("Shared with", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            Text(if (shares.isEmpty()) "No one — private" else shares.joinToString(", ") { it.optString("name") }, color = KitMuted, fontSize = 13.sp)
            val h = x.objs("history")
            Text("History · ${h.size}", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            if (h.isEmpty()) Text("Nothing recorded yet.", color = KitMuted, fontSize = 13.sp)
            h.forEach { e ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip(e.optString("event").replaceFirstChar { it.uppercase() }, when (e.optString("event")) { "created" -> KitGood; "deleted" -> KitDanger; "downloaded" -> InvBlue; else -> InvPurple })
                        Text("  " + listOfNotNull(e.str("user"), e.str("date"), e.str("time")).joinToString(" · "), color = KitMuted, fontSize = 11.sp)
                    }
                    e.str("reason")?.let { Text(it, color = KitInk, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                    e.objs("changes").forEach { c -> Text("${c.optString("field")}: ${c.optString("old")} → ${c.optString("new")}", color = KitMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

/** Upload: the picked file, its name and a description, and the folder it goes into. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadSheet(api: ApiClient, b: Branding, uuid: String, p: PickedFile, open: Pair<String, String>?, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(p.name.substringBeforeLast('.')) }
    var description by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(open) }
    var pick by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Upload file", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Chip("${p.name} · ${fileSize(p.bytes.size.toLong())}", KitMuted)
            Input("Name", name, { name = it; error = null }, error)
            Input("Description (optional)", description, { description = it }, singleLine = false)
            PickerField("Folder", folder?.second ?: "All files (root)") {
                scope.launch { pick = runCatching { withContext(Dispatchers.IO) { api.get("/projects/$uuid/file-center/folder-list") }.objs("data") }.getOrNull() ?: emptyList() }
            }
            ActionButton(if (saving) "Uploading…" else "Upload", b.base) {
                if (saving) return@ActionButton
                if (name.isBlank()) { error = "Give the file a name."; return@ActionButton }
                saving = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            api.sendForm("/projects/$uuid/file-center/upload", listOfNotNull(
                                FormPart("file", fileName = p.name, mime = p.mime, bytes = p.bytes), FormPart("name", name.trim()),
                                description.takeIf { it.isNotBlank() }?.let { FormPart("description", it.trim()) }, folder?.let { FormPart("folder_id", it.first) },
                            ))
                        }
                        AppToast.show("File uploaded"); onDone()
                    } catch (e: ApiException) { error = e.errors.values.firstOrNull() ?: e.message }
                    catch (e: Exception) { error = e.message ?: "Could not upload" }
                    saving = false
                }
            }
        }
    }
    pick?.let { list ->
        ChoiceSheet("Upload into", list.filter { !it.optBoolean("static") }.map { Choice(it.optString("id"), it.optString("path")) }, folder?.first, b, allowNone = true, noneLabel = "All files (root)", onClose = { pick = null }) { id ->
            folder = list.firstOrNull { it.optString("id") == id }?.let { id to it.optString("name") }; pick = null
        }
    }
}

/** Move a file: every folder of the project by its path, or the root. */
@Composable
private fun MoveSheet(api: ApiClient, b: Branding, uuid: String, f: JSONObject, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(Unit) {
        try { list = withContext(Dispatchers.IO) { api.get("/projects/$uuid/file-center/folder-list") }.objs("data") }
        catch (e: Exception) { AppToast.show(e.message ?: "Could not load folders"); onClose() }
    }
    val l = list ?: return
    ChoiceSheet("Move “${f.optString("name")}” to", l.filter { !it.optBoolean("static") }.map { Choice(it.optString("id"), it.optString("path")) }, null, b, allowNone = true, noneLabel = "All files (root)", onClose = onClose) { id ->
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/files/${f.optString("id")}/move", JSONObject().put("folder_id", id.ifBlank { null } ?: JSONObject.NULL)) }
                AppToast.show(r.str("message") ?: "Moved"); onDone()
            } catch (e: Exception) { AppToast.show(e.message ?: "Could not move"); onClose() }
        }
    }
}

/** Share a file with people of this project (tick them), or take a share back (untick). */
@Composable
private fun ShareSheet(api: ApiClient, b: Branding, uuid: String, f: JSONObject, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<JSONObject>?>(null) }
    var current by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(Unit) {
        try {
            people = withContext(Dispatchers.IO) { api.get("/projects/$uuid/file-center/people") }.objs("data")
            current = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/file-center/files/${f.optString("id")}") }.objs("shares").map { it.optInt("id") }.toSet()
        } catch (e: Exception) { AppToast.show(e.message ?: "Could not load"); onClose() }
    }
    val p = people ?: return
    if (p.isEmpty()) { LaunchedEffect(Unit) { AppToast.show("No one is on this project's team yet"); onClose() }; return }
    PeoplePick(p, current, b, "Share “${f.optString("name")}”", onClose = onClose) { ids ->
        val add = ids.filter { it !in current }; val remove = current.filter { it !in ids }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (add.isNotEmpty()) api.send("POST", "/projects/$uuid/file-center/files/${f.optString("id")}/share", JSONObject().put("user_ids", JSONArray(add)))
                    remove.forEach { api.send("POST", "/projects/$uuid/file-center/files/${f.optString("id")}/share", JSONObject().put("user_id", it)) }
                }
                AppToast.show(if (add.isEmpty() && remove.isEmpty()) "No changes" else "Sharing updated"); onDone()
            } catch (e: Exception) { AppToast.show(e.message ?: "Could not share"); onClose() }
        }
    }
}

/** Access requests for locked files: yours, or — for an admin — everyone's, with Approve / Deny (and a change of mind). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Requests(api: ApiClient, b: Branding, uuid: String, version: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    var review by remember { mutableStateOf(false) }
    var deciding by remember { mutableStateOf<Pair<JSONObject, String>?>(null) }
    var local by remember { mutableIntStateOf(0) }
    var rq by remember { mutableStateOf("") }
    com.example.gavanhomes.ui.common.FilterBar(
        rq, { rq = it }, "Search file, person or reason…",
        groups = listOf(com.example.gavanhomes.ui.common.FilterGroup("status", "Status", listOf("pending" to "Pending", "approved" to "Approved", "denied" to "Denied").map { (k, l) -> com.example.gavanhomes.ui.common.FilterOption(k, l, color = when (k) { "approved" -> KitGood; "denied" -> KitDanger; else -> InvAmber }) })),
        selected = if (status.isBlank()) emptyMap() else mapOf("status" to setOf(status)),
        onApply = { m -> status = m["status"]?.firstOrNull().orEmpty() }, countWith = { -1 }, single = setOf("status"),
    )
    Paged(api, b, "/projects/$uuid/file-center/requests", (if (status.isBlank()) "" else "&status=$status") + (if (rq.isBlank()) "" else "&search=" + java.net.URLEncoder.encode(rq.trim(), "UTF-8")), version + local, "No access requests.", "request", onMeta = { review = it.optBoolean("can_review") }) { r ->
        val st = r.optString("status")
        RowCard(null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.optString("file"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(r.str("folder"), r.str("by"), whenLabel(r.str("at"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                }
                Chip(st.replaceFirstChar { it.uppercase() }, when (st) { "approved" -> KitGood; "denied" -> KitDanger; else -> InvAmber })
            }
            r.str("reason")?.let { Text("“$it”", color = KitInk, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (st != "pending") Text(listOfNotNull(r.str("reviewer")?.let { "By $it" }, whenLabel(r.str("reviewed")), r.str("notes")).joinToString(" · "), color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            if (review) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (st != "approved") SmallAction("Approve", KitGood) { deciding = r to "approved" }
                if (st != "denied") SmallAction("Deny", KitDanger) { deciding = r to "denied" }
            }
        }
    }
    deciding?.let { (r, to) ->
        TextSheet(if (to == "approved") "Approve access" else "Deny access", "Note for ${r.optString("by")} (optional)", "", if (to == "approved") "Approve" else "Deny", b, multiline = true, allowEmpty = true, onClose = { deciding = null }) { note ->
            withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/requests/${r.optInt("id")}", JSONObject().put("status", to).put("reviewer_notes", note.ifBlank { JSONObject.NULL })) }
            AppToast.show(if (to == "approved") "Approved — they can open the file now" else "Denied"); deciding = null; local++; onChanged()
        }
    }
}

/** One text field and a button in a sheet; [onSave] throws to show its error under the field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextSheet(
    title: String, label: String, initial: String, button: String, b: Branding, multiline: Boolean = false, note: String? = null,
    number: Boolean = false, allowEmpty: Boolean = false, onClose: () -> Unit, onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 18.dp).heightIn(min = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            note?.let { Chip(it, KitMuted) }
            Input(label, text, { text = if (number) it.filter(Char::isDigit) else it; error = null }, error, singleLine = !multiline, keyboard = if (number) KeyboardType.Number else KeyboardType.Text)
            ActionButton(if (saving) "Please wait…" else button, b.base) {
                if (saving) return@ActionButton
                if (text.isBlank() && !allowEmpty) { error = "This can't be empty."; return@ActionButton }
                saving = true
                scope.launch {
                    try { onSave(text.trim()) }
                    catch (e: ApiException) { error = e.errors.values.firstOrNull() ?: e.message }
                    catch (e: Exception) { error = e.message ?: "Could not save" }
                    saving = false
                }
            }
        }
    }
}
