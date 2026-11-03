package com.example.gavanhomes.ui.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Context
import android.webkit.MimeTypeMap
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/** Saves a task / target file to the phone's Downloads/GavanHomes folder and, when notifications are allowed, announces it in the notification bar (tap to open). */
internal fun downloadFile(context: Context, scope: CoroutineScope, url: String, name: String, mime: String?) {
    AppToast.show("Downloading $name…")
    scope.launch {
        try {
            val bytes = withContext(Dispatchers.IO) { URL(localUrl(url)).readBytes() }
            val type = mime?.takeIf { it.isNotBlank() && it != "null" } ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
            val uri = withContext(Dispatchers.IO) { Downloads.save(context, name, type, bytes) }
            if (Downloads.canNotify(context)) Downloads.notifyReady(context, "Download complete", name, type, uri)
            AppToast.show("Saved to Downloads/GavanHomes")
        } catch (e: Exception) { AppToast.show("Could not download the file") }
    }
}

internal fun isImageName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")

/**
 * The pictures among a task's (or target's) files, shown directly as a gallery: a grid of thumbnails. Tapping one opens the full-screen
 * viewer, where you swipe left and right through all of them and pinch to zoom. [onDelete] (when given) adds a ✕ on each thumbnail.
 */
@Composable
internal fun ImageGrid(images: List<JSONObject>, onDelete: ((JSONObject) -> Unit)?) {
    var open by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        images.withIndex().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (i, f) ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                        RemoteImage(f.optString("url"), Modifier.fillMaxSize().clickable { open = i }, ContentScale.Crop)
                        if (onDelete != null) Box(
                            Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)).clickable { onDelete(f) },
                            contentAlignment = Alignment.Center,
                        ) { Text("✕", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    open?.let { GalleryViewer(images, it) { open = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryViewer(images: List<JSONObject>, start: Int, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { images.size }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF111418)).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("←", color = Color.White, fontSize = 22.sp) }
                Text(images.getOrNull(pager.currentPage)?.optString("name").orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                Text("${pager.currentPage + 1} / ${images.size}", color = Color(0xFFB0B6C0), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                Text("⬇ Download", color = Color(0xFF8FB8FF), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    images.getOrNull(pager.currentPage)?.let { f -> downloadFile(context, scope, f.optString("url"), f.optString("name"), f.optString("type")) }
                }.padding(horizontal = 10.dp, vertical = 8.dp))
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { RemoteImage(images[i].optString("url"), Modifier.fillMaxSize().zoomable(), ContentScale.Fit) }
            }
        }
    }
}

/** Pinch to zoom and drag while zoomed — one finger at normal size is left alone, so the pager can still swipe. */
private fun Modifier.zoomable(): Modifier = composed {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val e = awaitPointerEvent()
                val zoom = e.calculateZoom(); val pan = e.calculatePan()
                if (e.changes.size > 1 || scale > 1f) {
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                    e.changes.forEach { it.consume() }
                }
            } while (e.changes.any { it.pressed })
        }
    }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
}

/** The "upload a file" button of the Files card: a dashed drop-zone style tile in the brand colour — an arrow in a circle, a title and what may be uploaded. */
@Composable
internal fun UploadTile(accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = .07f)).clickable { onClick() }
            .drawBehind { drawRoundRect(accent.copy(alpha = .55f), cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text("⬆", color = accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Upload a file", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text("PDF, Office, image, CSV, TXT or ZIP · up to 5 MB", color = Color(0xFF8A8F9C), fontSize = 11.sp)
        }
    }
}
