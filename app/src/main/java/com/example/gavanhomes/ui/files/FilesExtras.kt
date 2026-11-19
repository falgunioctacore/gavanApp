package com.example.gavanhomes.ui.files

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.Downloads
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.LoadMoreOnScroll
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.inventory.InvAmber
import com.example.gavanhomes.ui.inventory.InvBlue
import com.example.gavanhomes.ui.leads.Chip
import com.example.gavanhomes.ui.leads.SmallAction
import com.example.gavanhomes.ui.leads.whenLabel
import com.example.gavanhomes.ui.procurement.ActionButton
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.tasks.objs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

// ───────────────────────────── what can be looked at inside the app ─────────────────────────────

internal fun isImageExt(ext: String) = ext.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp")
internal fun isPreviewable(ext: String) = isImageExt(ext) || ext.lowercase() == "pdf"

/** Decoded thumbnails, kept for the session (about 8 MB). */
private object ThumbCache {
    val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }
}

private fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** A picture's thumbnail, fetched with the user's sign-in (the server only serves it to someone who may open the file); [fallback] while it loads or when it can't. */
@Composable
internal fun Thumb(api: ApiClient, uuid: String, id: String, modifier: Modifier, fallback: @Composable () -> Unit) {
    val key = "$uuid/$id"
    var bmp by remember(key) { mutableStateOf(ThumbCache.cache.get(key)) }
    LaunchedEffect(key) {
        if (bmp != null) return@LaunchedEffect
        bmp = withContext(Dispatchers.IO) {
            runCatching { decode(api.download("/projects/$uuid/file-center/files/$id/thumb").second, 360) }.getOrNull()?.also { ThumbCache.cache.put(key, it) }
        }
    }
    val b = bmp
    Box(modifier.clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else fallback()
    }
}

// ───────────────────────────── a paged list that can also be a grid ─────────────────────────────

/** Like the other paged lists, but [columns] cards per row (1 = list, 2 = grid), and a row can be changed in place (a star) without reloading. */
@Composable
internal fun FilesPaged(
    api: ApiClient, b: Branding, path: String, query: String, version: Int, empty: String, noun: String, columns: Int = 1,
    onMeta: (JSONObject) -> Unit = {}, item: @Composable (JSONObject, (JSONObject) -> Unit) -> Unit,
) {
    val rows = remember { mutableStateListOf<JSONObject>() }
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(1) }
    var lastPage by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    suspend fun load(p: Int) {
        loading = true; error = null
        try {
            val r = withContext(Dispatchers.IO) { api.get("$path?page=$p$query") }
            if (p == 1) { rows.clear(); onMeta(r) }
            rows.addAll(r.objs("data")); page = r.optInt("current_page", p); lastPage = r.optInt("last_page", 1); total = r.optInt("total")
        } catch (e: Exception) { error = e.message ?: "Could not load" }
        loading = false
    }
    LaunchedEffect(path, query, version, attempt) { load(1) }

    Text("$total $noun${if (total == 1) "" else "s"}", color = KitMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 4.dp))
    rows.chunked(columns).forEach { chunk ->
        if (columns == 1) item(chunk[0]) { n -> rows[rows.indexOf(chunk[0])] = n }
        else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            chunk.forEach { r -> Box(Modifier.weight(1f)) { item(r) { n -> rows[rows.indexOf(r)] = n } } }
            repeat(columns - chunk.size) { Box(Modifier.weight(1f)) {} }
        }
    }
    when {
        loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) }
        error != null && rows.isEmpty() -> com.example.gavanhomes.ui.common.EstateError(error, modifier = Modifier.fillMaxWidth().padding(24.dp), onRetry = { attempt++ })
        rows.isEmpty() -> Text(empty, color = KitMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
        loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) }
    }
    if (rows.isNotEmpty() && page < lastPage && !loading) LoadMoreOnScroll(page) { scope.launch { load(page + 1) } }
}

// ───────────────────────────── share out, save ─────────────────────────────

/** Hands one or more files to any app (WhatsApp, mail, Drive …) through the system share sheet. */
internal fun shareOut(context: Context, files: List<Triple<String, String, ByteArray>>) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val uris = files.map { (name, _, bytes) -> FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "-")).also { it.writeBytes(bytes) }) }
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).setType(files[0].second).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { AppToast.show("Nothing to share with") }
}

/** A file's name with its extension, from what the server called it or the extension we know. */
internal fun nameWithExt(served: String, name: String, ext: String): String =
    if (served != "export") served else if (ext.isNotBlank() && !name.endsWith(".$ext", true)) "$name.$ext" else name

// ───────────────────────────── preview ─────────────────────────────

/** A picture (pinch to zoom) or a PDF (every page) shown inside the app, with Download and Share. */
@Composable
internal fun FilePreview(api: ApiClient, b: Branding, uuid: String, f: JSONObject, onClose: () -> Unit, onDownload: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    val ext = f.optString("ext").lowercase()
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(f.optString("id")) {
        try {
            bytes = withContext(Dispatchers.IO) { api.download("/projects/$uuid/file-center/files/${f.optString("id")}/thumb").second }
            runCatching { withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/files/${f.optString("id")}/view", JSONObject()) } }
        } catch (e: Exception) { error = e.message ?: "Could not open the file" }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0F14)).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✕", color = Color.White, fontSize = 22.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClose() }.padding(horizontal = 10.dp, vertical = 4.dp))
                Text(f.optString("name"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                Text("⇩", color = Color.White, fontSize = 22.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onDownload() }.padding(horizontal = 10.dp, vertical = 4.dp))
                Text("⇪", color = Color.White, fontSize = 22.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onShare() }.padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val data = bytes
                when {
                    error != null -> Text(error ?: "", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                    data == null -> com.example.gavanhomes.ui.common.EstateLoader(Color.White)
                    isImageExt(ext) -> ZoomImage(data)
                    else -> PdfPages(context, data)
                }
            }
        }
    }
}

@Composable
private fun ZoomImage(data: ByteArray) {
    val bmp = remember(data) { decode(data, 2048) }
    var scale by remember { mutableStateOf(1f) }
    var dx by remember { mutableStateOf(0f) }
    var dy by remember { mutableStateOf(0f) }
    if (bmp == null) { Text("This picture can't be shown.", color = Color.White); return }
    Image(
        bmp.asImageBitmap(), null, contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                if (scale == 1f) { dx = 0f; dy = 0f } else { dx += pan.x; dy += pan.y }
            }
        }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = dx, translationY = dy),
    )
}

@Composable
private fun PdfPages(context: Context, data: ByteArray) {
    val state = remember(data) {
        runCatching {
            val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "preview.pdf").also { it.writeBytes(data) }
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(fd)
        }.getOrNull()
    }
    androidx.compose.runtime.DisposableEffect(state) { onDispose { runCatching { state?.close() } } }
    if (state == null) { Text("This PDF can't be shown — use Download.", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp)); return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        itemsIndexed(List(state.pageCount) { it }) { _, i ->
            val page by remember(state, i) {
                mutableStateOf(synchronized(state) {
                    runCatching {
                        state.openPage(i).use { p ->
                            val w = 1080; val h = (w.toFloat() / p.width * p.height).toInt()
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.WHITE); p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                        }
                    }.getOrNull()
                })
            }
            page?.let { Image(it.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
        }
    }
}

// ───────────────────────────── versions ─────────────────────────────

/** Every version of a file, newest first — download an old one, or make it the current file again. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VersionsSheet(api: ApiClient, b: Branding, uuid: String, f: JSONObject, canEdit: Boolean, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = f.optString("id")
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var restoring by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(attempt) { list = runCatching { withContext(Dispatchers.IO) { api.get("/projects/$uuid/file-center/files/$id/versions") }.objs("data") }.getOrNull() ?: emptyList() }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Versions", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Text(f.optString("name"), color = KitMuted, fontSize = 13.sp)
            val l = list
            if (l == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) }
            else if (l.size <= 1) Text("Only one version so far. Uploading a “New version” keeps the old one here.", color = KitMuted, fontSize = 13.sp)
            l?.forEach { v ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Version ${v.optInt("version")}", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        if (v.optBoolean("current")) Box(Modifier.padding(start = 8.dp)) { Chip("Current", KitGood) }
                    }
                    Text(listOfNotNull(fileSize(v.optLong("size")), v.str("by"), whenLabel(v.str("at"))).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                    if (!v.optBoolean("current")) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("Download", InvBlue) {
                            scope.launch {
                                try {
                                    val (served, bytes) = withContext(Dispatchers.IO) { api.download("/projects/$uuid/file-center/files/$id/versions/${v.optInt("id")}/download") }
                                    withContext(Dispatchers.IO) { Downloads.save(context, served, android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(served.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream", bytes) }
                                    AppToast.show("Saved to Downloads/GavanHomes")
                                } catch (e: Exception) { AppToast.show(e.message ?: "Could not download") }
                            }
                        }
                        if (canEdit) SmallAction("Make current", InvAmber) { restoring = v }
                    }
                }
            }
        }
    }
    restoring?.let { v ->
        SwalConfirm(
            title = "Make version ${v.optInt("version")} the current file?", text = "The file now in use stays here as an earlier version — nothing is lost.", confirmText = "Yes, restore",
            onConfirm = {
                restoring = null
                scope.launch {
                    try { val r = withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/files/$id/versions/${v.optInt("id")}/restore", JSONObject()) }; AppToast.show(r.str("message") ?: "Restored"); onDone() }
                    catch (e: Exception) { AppToast.show(e.message ?: "Could not restore") }
                }
            },
            onCancel = { restoring = null },
        )
    }
}

// ───────────────────────────── description + tags ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InfoSheet(api: ApiClient, b: Branding, uuid: String, f: JSONObject, onClose: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf(f.optString("description").takeIf { it != "null" }.orEmpty()) }
    var tags by remember { mutableStateOf(f.optString("tags").takeIf { it != "null" }.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = com.example.gavanhomes.ui.common.KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Description & tags", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = KitInk)
            Text(f.optString("name"), color = KitMuted, fontSize = 13.sp)
            Input("Description", description, { description = it }, singleLine = false)
            Input("Tags (comma separated)", tags, { tags = it })
            Text("Tags and the description are found by the search box.", color = KitMuted, fontSize = 12.sp)
            ActionButton(if (saving) "Saving…" else "Save", b.base) {
                if (saving) return@ActionButton
                saving = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.send("POST", "/projects/$uuid/file-center/files/${f.optString("id")}/meta", JSONObject().put("description", description.trim()).put("tags", tags.trim())) }
                        AppToast.show("Saved"); onDone()
                    } catch (e: Exception) { AppToast.show(e.message ?: "Could not save") }
                    saving = false
                }
            }
        }
    }
}

/** The file's tags as small chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagChips(tags: String?) {
    val list = tags?.takeIf { it.isNotBlank() && it != "null" }?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { list.forEach { Chip("#$it", InvBlue) } }
}
