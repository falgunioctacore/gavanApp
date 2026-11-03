package com.example.gavanhomes.ui.discussion

import com.example.gavanhomes.ui.common.AppToast

import com.example.gavanhomes.ui.common.softShadow
import com.example.gavanhomes.ui.common.bounceClick
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

private val thumbs = object : android.util.LruCache<String, Bitmap>(60) {}

/** A photo from the server scaled down to a grid cell, kept in a small memory cache while the gallery is used. */
@Composable
private fun Thumb(url: String, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(thumbs.get(url), url) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = URL(url).openStream().use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
                var sample = 1; while (bounds.outWidth / (sample * 2) >= 360) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.also { thumbs.put(url, it) }
            }.getOrNull()
        }
    }
    val b = bmp
    if (b == null) Box(modifier.background(Color(0x14000000)), contentAlignment = Alignment.Center) { Text("🖼", fontSize = 20.sp) }
    else Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = modifier)
}

private fun sizeText(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun iconFor(name: String): Pair<String, Color> = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "PDF" to Color(0xFFD64545)
    "doc", "docx" -> "DOC" to Color(0xFF2F6FE4)
    "xls", "xlsx", "csv" -> "XLS" to Color(0xFF1F9D55)
    "ppt", "pptx" -> "PPT" to Color(0xFFD9822B)
    "zip", "rar" -> "ZIP" to Color(0xFF8A8F9C)
    else -> name.substringAfterLast('.', "FILE").take(4).uppercase() to Color(0xFF6A4FB3)
}

/** The month groups of a gallery section — {"November 2026": [..]} — as (month, items) pairs, newest first. */
private fun JSONObject.groups(key: String): List<Pair<String, List<JSONObject>>> {
    val o = optJSONObject(key) ?: return emptyList()
    return o.keys().asSequence().map { m -> m to o.getJSONArray(m).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } }.toList()
}

/**
 * Everything shared in a chat, like WhatsApp's "Media, links and docs": a Media tab (photos and videos in a grid, by month), a Docs tab
 * (every file with its type, size and date) and a Links tab. Tap a photo or file to see it inside the app. Data: GET /chats/{id}/media.
 */
@Composable
fun ChatGallery(api: ApiClient, b: Branding, base: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(attempt) {
        error = null
        try { data = withContext(Dispatchers.IO) { api.get("$base/media") } } catch (e: Exception) { error = e.message ?: "Could not load" }
    }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, "Media, docs & links", onBack, compact = true)
        val d = data
        if (d == null) { Text(error ?: "Loading…", color = KitMuted, modifier = Modifier.padding(32.dp).clickable { if (error != null) attempt++ }); return@Column }
        val media = d.groups("media"); val docs = d.groups("docs"); val links = d.groups("links")
        val counts = listOf(media.sumOf { it.second.size }, docs.sumOf { it.second.size }, links.sumOf { it.second.size })
        Row(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Media", "Docs", "Links").forEachIndexed { i, label ->
                Text(
                    "$label · ${counts[i]}", color = if (tab == i) Color.White else KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (tab == i) b.base else Color.Transparent).clickable { tab = i }.padding(vertical = 10.dp),
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when (tab) {
                0 -> if (media.isEmpty()) Text("No photos or videos shared yet.", color = KitMuted, modifier = Modifier.padding(vertical = 32.dp)) else media.forEach { (month, items) ->
                    Text(month, color = KitMuted, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    items.chunked(3).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowItems.forEach { m ->
                                val video = m.optString("type") == "video"
                                val name = m.str("name") ?: if (video) "video" else "photo"
                                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { preview = served(m.optString("url")) to name }, contentAlignment = Alignment.Center) {
                                    if (video) Box(Modifier.fillMaxSize().background(Color(0xFF1E232A)), contentAlignment = Alignment.Center) { Text("▶", color = Color.White, fontSize = 26.sp) }
                                    else Thumb(served(m.optString("url")), Modifier.fillMaxSize())
                                }
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                1 -> if (docs.isEmpty()) Text("No documents shared yet.", color = KitMuted, modifier = Modifier.padding(vertical = 32.dp)) else docs.forEach { (month, items) ->
                    Text(month, color = KitMuted, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    items.forEach { m ->
                        val name = m.str("name") ?: "File"
                        val (tag, tint) = iconFor(name)
                        Row(
                            Modifier.padding(bottom = 8.dp).fillMaxWidth().softShadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(com.example.gavanhomes.ui.common.KitCard).bounceClick { preview = served(m.optString("url")) to name }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(tag, color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp) }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(name, color = KitInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(if (m.has("size") && !m.isNull("size")) sizeText(m.optLong("size")) else null, m.str("date")).joinToString(" · "), color = KitMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                else -> if (links.isEmpty()) Text("No links shared yet.", color = KitMuted, modifier = Modifier.padding(vertical = 32.dp)) else links.forEach { (month, items) ->
                    Text(month, color = KitMuted, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    items.forEach { m ->
                        Column(
                            Modifier.padding(bottom = 8.dp).fillMaxWidth().softShadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(com.example.gavanhomes.ui.common.KitCard)
                                .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.optString("url")))) }.onFailure { AppToast.show("No app can open this link") } }.padding(14.dp),
                        ) {
                            Text(m.optString("url"), color = Color(0xFF2874F0), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(m.str("date") ?: "", color = KitMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    preview?.let { (url, name) -> FilePreview(url, name) { preview = null } }
}
