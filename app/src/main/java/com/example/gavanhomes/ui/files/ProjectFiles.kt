package com.example.gavanhomes.ui.files

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.ModulePages
import com.example.gavanhomes.ui.common.ProvideFab
import com.example.gavanhomes.ui.common.SearchBox
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.inventory.InvPurple
import com.example.gavanhomes.ui.inventory.Paged
import com.example.gavanhomes.ui.inventory.RowCard
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.leads.whenLabel
import com.example.gavanhomes.ui.procurement.ActionButton
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickedFile
import com.example.gavanhomes.ui.projects.form.readPicked
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun fileSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun extColor(ext: String) = when (ext) {
    "pdf" -> KitDanger; "jpg", "jpeg", "png", "gif", "webp" -> InvPurple; "doc", "docx" -> InvBlue
    "xls", "xlsx", "csv" -> KitGood; "zip", "rar" -> InvAmber; else -> KitMuted
}

private fun mimeOf(name: String, ext: String) =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.ifBlank { name.substringAfterLast('.', "") }.lowercase()) ?: "application/octet-stream"

/** The file's little badge: its extension on a tinted square (📁 for a folder). */
@Composable
private fun Badge(text: String, c: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
        Text(text, color = c, fontWeight = FontWeight.ExtraBold, fontSize = if (text.length > 3) 11.sp else 13.sp, maxLines = 1)
    }
}

/**
 * The project's Files module, like the website's file manager: browse folders (with the path back), search, open a file (it is
 * saved to Downloads and opened), rename, delete, ask for access to a locked file, and the Trash (restore / delete for good).
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
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var local by remember { mutableIntStateOf(0) }
    var options by remember { mutableStateOf<JSONObject?>(null) }             // the file whose actions are open
    var folderOptions by remember { mutableStateOf<JSONObject?>(null) }
    var renaming by remember { mutableStateOf<JSONObject?>(null) }
    var asking by remember { mutableStateOf<JSONObject?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }  // file · folder · forever
    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    val folderId = path.lastOrNull()?.first
    val v = version + local

    LaunchedEffect(folderId, v, search) {
        error = null
        if (search.isNotBlank()) kotlinx.coroutines.delay(350)
        val q = buildString { folderId?.let { append("folder_id=$it&") }; if (search.isNotBlank()) append("search=" + java.net.URLEncoder.encode(search.trim(), "UTF-8")) }
        try { data = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/file-center/browse?$q") } } catch (e: Exception) { error = e.message ?: "Could not load" }
    }
    BackHandler(enabled = page == 0 && path.isNotEmpty()) { path.removeAt(path.lastIndex) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { readPicked(context, it) }?.let { picked = it } }
    val can = data?.optJSONObject("can") ?: JSONObject()
    val locked = data?.optBoolean("static") == true
    ProvideFab(if (page == 0 && can.optBoolean("create") && !locked) listOf(FabAction("upload", "Upload file", b.base, "⇪"), FabAction("folder", "New folder", InvAmber, "▤")) else emptyList()) {
        if (it == "upload") pick.launch("*/*") else newFolder = true
    }
    ModulePages("Files", listOf("Files", "Trash"), page) { page = it }

    fun open(f: JSONObject) {
        val id = f.optString("id"); val name = f.optString("name"); val ext = f.optString("ext")
        busy = id
        scope.launch {
            try {
                val (served, bytes) = withContext(Dispatchers.IO) { api.download("/projects/$uuid/file-center/files/$id/download") }
                val file = if (served != "export") served else if (ext.isNotBlank() && !name.endsWith(".$ext", true)) "$name.$ext" else name
                val mime = mimeOf(file, ext)
                val uri = withContext(Dispatchers.IO) { Downloads.save(context, file, mime, bytes) }
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(view) }.onFailure { Toast.makeText(context, "Saved to Downloads/GavanHomes", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not open the file", Toast.LENGTH_LONG).show() }
            busy = null
        }
    }
    fun act(done: String, call: suspend () -> Unit) {
        scope.launch {
            try { withContext(Dispatchers.IO) { call() }; Toast.makeText(context, done, Toast.LENGTH_SHORT).show(); local++ }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not do that", Toast.LENGTH_LONG).show() }
        }
    }

    if (page == 1) {
        Paged(api, b, "/projects/$uuid/file-center/trash", "", v, "The trash is empty.", "deleted file") { f ->
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
    } else {
        SearchBox(search, { search = it }, "Search files and folders…")
        val d = data
        // the path back: All files › … › open folder
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("All files", color = if (path.isEmpty()) KitInk else b.base, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { path.clear() })
            path.forEachIndexed { i, (_, n) ->
                Text("  ›  ", color = KitMuted, fontSize = 14.sp)
                Text(n, color = if (i == path.lastIndex) KitInk else b.base, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { while (path.size > i + 1) path.removeAt(path.lastIndex) })
            }
        }
        if (d == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = b.base)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { local++ }) { Text("Retry") } }
            }
            return
        }
        d.optJSONObject("storage")?.let { s -> Text("${s.optInt("count")} files · ${fileSize(s.optLong("used"))} used in this project", color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (locked) Text("🔒 A locked folder — ask for access to open a file.", color = InvAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        val folders = d.objs("folders"); val files = d.objs("files")
        folders.forEach { f ->
            RowCard({ search = ""; path.add(f.optString("id") to f.optString("name")) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge("▤", InvAmber)
                    Text(f.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    if (can.optBoolean("delete")) Text("⋮", color = KitMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { folderOptions = f }.padding(horizontal = 10.dp, vertical = 2.dp))
                    else Text("›", color = KitMuted, fontSize = 20.sp)
                }
            }
        }
        files.forEach { f ->
            val shut = f.optBoolean("locked") && !f.optBoolean("approved")
            RowCard({ options = f }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(f.optString("ext").uppercase().ifBlank { "FILE" }.take(4), extColor(f.optString("ext")))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(f.optString("name"), color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(fileSize(f.optLong("size")), f.str("folder_name")?.takeIf { path.isEmpty() }, f.str("owner")?.takeIf { it != "—" }, com.example.gavanhomes.ui.tasks.prettyDay(f.str("date"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    when {
                        busy == f.optString("id") -> CircularProgressIndicator(color = b.base, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        shut -> Text("🔒", fontSize = 16.sp)
                    }
                }
            }
        }
        if (folders.isEmpty() && files.isEmpty()) Text(if (search.isNotBlank()) "Nothing matches “$search”." else "This folder is empty.", color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
    }

    options?.let { f ->
        val shut = f.optBoolean("locked") && !f.optBoolean("approved")
        ModalBottomSheet(onDismissRequest = { options = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(f.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text(listOfNotNull(fileSize(f.optLong("size")), f.str("folder_name"), f.str("owner")?.takeIf { it != "—" }).joinToString(" · "), color = KitMuted, fontSize = 13.sp)
                if (shut) ActionButton("Request access", InvAmber) { options = null; asking = f }
                else ActionButton("Open", b.base) { options = null; open(f) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (can.optBoolean("edit") && !f.optBoolean("locked")) SmallAction("Rename", InvBlue) { options = null; renaming = f }
                    if (can.optBoolean("delete") && !f.optBoolean("locked")) SmallAction("Delete", KitDanger) { options = null; deleting = "file" to f }
                }
            }
        }
    }
    folderOptions?.let { f ->
        ModalBottomSheet(onDismissRequest = { folderOptions = null }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(f.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                ActionButton("Open", b.base) { folderOptions = null; search = ""; path.add(f.optString("id") to f.optString("name")) }
                SmallAction("Delete folder", KitDanger) { folderOptions = null; deleting = "folder" to f }
            }
        }
    }
    renaming?.let { f ->
        TextSheet("Rename file", "Name", f.optString("name"), "Save", b, onClose = { renaming = null }) { name ->
            withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/files/${f.optString("id")}/rename", JSONObject().put("name", name)) }
            Toast.makeText(context, "File renamed", Toast.LENGTH_SHORT).show(); renaming = null; local++
        }
    }
    asking?.let { f ->
        TextSheet("Request access", "Why do you need “${f.optString("name")}”?", "", "Send request", b, multiline = true, onClose = { asking = null }) { reason ->
            withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/request-access", JSONObject().put("file_id", f.optString("id")).put("reason", reason)) }
            Toast.makeText(context, "Access requested — you'll be told when it's approved", Toast.LENGTH_LONG).show(); asking = null
        }
    }
    if (newFolder) TextSheet("New folder", "Folder name", "", "Create folder", b, onClose = { newFolder = false }) { name ->
        withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/folders", JSONObject().put("name", name).put("parent_id", folderId ?: JSONObject.NULL)) }
        Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show(); newFolder = false; local++
    }
    picked?.let { p ->
        TextSheet("Upload file", "Name", p.name.substringBeforeLast('.'), "Upload", b, note = "${p.name} · ${fileSize(p.bytes.size.toLong())}" + (path.lastOrNull()?.let { " · into ${it.second}" } ?: ""), onClose = { picked = null }) { name ->
            withContext(Dispatchers.IO) {
                api.sendForm("/projects/$uuid/file-center/upload", listOfNotNull(
                    FormPart("file", fileName = p.name, mime = p.mime, bytes = p.bytes), FormPart("name", name), folderId?.let { FormPart("folder_id", it) },
                ))
            }
            Toast.makeText(context, "File uploaded", Toast.LENGTH_SHORT).show(); picked = null; local++
        }
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

/** One text field and a button in a sheet; [onSave] throws to show its error under the field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextSheet(title: String, label: String, initial: String, button: String, b: Branding, multiline: Boolean = false, note: String? = null, onClose: () -> Unit, onSave: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            note?.let { Chip(it, KitMuted) }
            Input(label, text, { text = it; error = null }, error, singleLine = !multiline)
            ActionButton(if (saving) "Please wait…" else button, b.base) {
                if (saving) return@ActionButton
                if (text.isBlank()) { error = "This can't be empty."; return@ActionButton }
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
