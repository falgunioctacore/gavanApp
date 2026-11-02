package com.example.gavanhomes.ui.discussion

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private enum class Kind { Image, Pdf, Text, Other }

private fun kindOf(name: String): Kind = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> Kind.Image
    "pdf" -> Kind.Pdf
    "txt", "csv", "log", "json", "xml", "md" -> Kind.Text
    else -> Kind.Other
}

/**
 * A chat file shown inside the app: photos (pinch to zoom, drag), PDFs (every page, scroll) and plain-text files are fetched and drawn
 * here — nothing is saved to the phone's storage. Other types (Word, Excel …) show their details and an "Open in another app" button.
 */
@Composable
fun FilePreview(url: String, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val kind = kindOf(name)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF111418)).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("←", color = Color.White, fontSize = 22.sp) }
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                Text("Open with…", color = Color(0xFF8FB8FF), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { openElsewhere(context, url) }.padding(horizontal = 10.dp, vertical = 10.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (kind) {
                    Kind.Image -> ImageView(url)
                    Kind.Pdf -> PdfView(url)
                    Kind.Text -> TextView(url)
                    Kind.Other -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("📄", fontSize = 56.sp)
                        Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
                        Text("This type of file can't be shown here.", color = Color(0xFFB0B6C0), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
                        Text("Open in another app", color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF2874F0)).clickable { openElsewhere(context, url) }.padding(horizontal = 22.dp, vertical = 12.dp))
                    }
                }
            }
        }
    }
}

private fun openElsewhere(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun Busy(error: Boolean, what: String) {
    if (error) Text("Could not load the $what.", color = Color(0xFFFFB4B4), fontSize = 14.sp)
    else CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
}

private fun fetch(url: String): ByteArray = URL(url).openStream().use { it.readBytes() }

@Composable
private fun ImageView(url: String) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = fetch(url)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
                var sample = 1; while (bounds.outWidth / (sample * 2) >= 2048) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    var failed by remember(url) { mutableStateOf(false) }
    val bmp = bitmap
    if (bmp == null) { Busy(failed, "photo"); androidx.compose.runtime.LaunchedEffect(url) { kotlinx.coroutines.delay(15000); failed = true }; return }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        bmp.asImageBitmap(), null, contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    )
}

@Composable
private fun PdfView(url: String) {
    val context = LocalContext.current
    val widthPx = with(LocalDensity.current) { (context.resources.displayMetrics.widthPixels).coerceAtMost(1600) }
    val doc by produceState<Pair<PdfRenderer, ParcelFileDescriptor>?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, "chat_preview.pdf").also { it.writeBytes(fetch(url)) }
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(fd) to fd
            }.getOrNull()
        }
    }
    DisposableEffect(doc) { val open = doc; onDispose { open?.let { (r, fd) -> runCatching { r.close(); fd.close() }; runCatching { File(context.cacheDir, "chat_preview.pdf").delete() } } } }
    var failed by remember(url) { mutableStateOf(false) }
    val d = doc
    if (d == null) { Busy(failed, "document"); androidx.compose.runtime.LaunchedEffect(url) { kotlinx.coroutines.delay(20000); failed = true }; return }
    val renderer = d.first
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        items(renderer.pageCount) { i ->
            val page by produceState<Bitmap?>(null, i) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        synchronized(renderer) {   // a PdfRenderer lets only one page be open at a time
                            renderer.openPage(i).use { p ->
                                val h = (widthPx.toFloat() / p.width * p.height).toInt()
                                Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { b -> b.eraseColor(AColor.WHITE); p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                            }
                        }
                    }.getOrNull()
                }
            }
            val b = page
            if (b == null) Box(Modifier.fillMaxWidth().height(420.dp).background(Color(0xFF1E232A)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) }
            else Image(b.asImageBitmap(), "Page ${i + 1}", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TextView(url: String) {
    val text by produceState<String?>(null, url) { value = withContext(Dispatchers.IO) { runCatching { String(fetch(url).let { if (it.size > 200_000) it.copyOf(200_000) else it }) }.getOrNull() } }
    val t = text
    if (t == null) Busy(false, "file")
    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { Text(t, color = Color(0xFFE6E9EE), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
}
