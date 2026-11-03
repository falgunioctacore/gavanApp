package com.example.gavanhomes.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.ui.discussion.served
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Everyone's profile picture, for the avatars beside names in the people pickers (assignees, owners …). Loaded once after sign-in
 * ([load] — GET /people/photos); a picture itself is fetched the first time that person's avatar is drawn and kept.
 */
object PeoplePhotos {
    private val urls = mutableStateMapOf<Int, String>()
    private val bitmaps = mutableStateMapOf<Int, Bitmap>()
    private var loaded = false

    fun clear() { urls.clear(); bitmaps.clear(); loaded = false }

    suspend fun load(api: ApiClient) {
        if (loaded) return
        runCatching { withContext(Dispatchers.IO) { api.get("/people/photos").getJSONObject("data") } }.onSuccess { d ->
            loaded = true
            d.keys().forEach { k -> k.toIntOrNull()?.let { urls[it] = d.optString(k) } }
        }
    }

    internal fun urlOf(id: Int): String? = urls[id]
    internal fun bitmapOf(id: Int): Bitmap? = bitmaps[id]
    internal suspend fun fetch(id: Int) {
        val url = urls[id] ?: return
        if (bitmaps[id] != null) return
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = URL(served(url)).openStream().use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
                var sample = 1; while (bounds.outWidth / (sample * 2) >= 200) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
        if (bmp != null) bitmaps[id] = bmp
    }
}

/** A person's avatar: their profile picture when they have one, otherwise the coloured initials. [id] is the user's id (0 = not a user, initials only). */
@Composable
fun PersonAvatar(id: Int, name: String, size: Dp = 36.dp) {
    val has = id > 0 && PeoplePhotos.urlOf(id) != null
    LaunchedEffect(id, has) { if (has) PeoplePhotos.fetch(id) }
    val bmp = if (id > 0) PeoplePhotos.bitmapOf(id) else null
    if (bmp != null) Image(bmp.asImageBitmap(), name, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    else Initials(name, size = size)
}
