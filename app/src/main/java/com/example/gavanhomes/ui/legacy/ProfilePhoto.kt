package com.example.gavanhomes.ui.legacy

import com.example.gavanhomes.ui.common.AppToast

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.FormPart
import com.example.gavanhomes.ui.discussion.served
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Your profile picture, round, with "Change photo" (pick a picture from the phone) and "Remove". Data: GET /profile (photo) · POST /profile/photo · DELETE /profile/photo. */
@Composable
fun ProfilePhotoRow(api: ApiClient, name: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap = MyPhoto.bitmap
    val hasPhoto = bitmap != null
    var busy by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(name) }

    LaunchedEffect(Unit) { MyPhoto.load(api, force = true); if (title.isBlank()) runCatching { withContext(Dispatchers.IO) { api.get("/me").getJSONObject("data") }.optString("name") }.getOrNull()?.let { title = it } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    val raw = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    val bmp = decode(raw, 1024) ?: throw IllegalStateException("That file is not a picture")
                    val out = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }.toByteArray()
                    api.sendForm("/profile/photo", listOf(FormPart("photo", fileName = "profile.jpg", mime = "image/jpeg", bytes = out)))
                }
                AppToast.show(r.optString("message", "Profile photo updated."))
                MyPhoto.set(r.optJSONObject("data")?.optString("photo")?.takeIf { it.isNotBlank() && it != "null" })
            } catch (e: Exception) { AppToast.show(e.message ?: "Could not upload the photo") }
            busy = false
        }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Blue.copy(alpha = .14f)).clickable(enabled = !busy) { pick.launch("image/*") }, contentAlignment = Alignment.Center) {
            val b = bitmap
            if (b != null) Image(b.asImageBitmap(), "Profile photo", Modifier.size(84.dp), contentScale = ContentScale.Crop)
            else Text(title.trim().take(1).uppercase().ifBlank { "?" }, color = Blue, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (busy) "Uploading…" else if (hasPhoto) "Change photo" else "Add a photo", color = Blue, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable(enabled = !busy) { pick.launch("image/*") }.padding(vertical = 4.dp))
            if (hasPhoto && !busy) Text("Remove", color = Color(0xFFD64545), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable {
                    busy = true
                    scope.launch {
                        try { withContext(Dispatchers.IO) { api.delete("/profile/photo") }; MyPhoto.set(null) } catch (e: Exception) { AppToast.show(e.message ?: "Could not remove") }
                        busy = false
                    }
                }.padding(vertical = 4.dp))
            Text("JPG or PNG, up to 10 MB", color = Color(0xFF8A94A6), fontSize = 12.sp)
        }
    }
}

/** A picture from bytes, scaled so its longer side is at most [maxSide]. */
private fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val longest = maxOf(bmp.width, bmp.height)
    return if (longest <= maxSide) bmp else Bitmap.createScaledBitmap(bmp, bmp.width * maxSide / longest, bmp.height * maxSide / longest, true)
}

/** The signed-in person's picture, loaded once and shared by every place that shows an avatar. */
object MyPhoto {
    var bitmap by mutableStateOf<Bitmap?>(null)
    private var loaded = false

    fun clear() { bitmap = null; loaded = false }

    fun set(url: String?) {
        loaded = true
        if (url.isNullOrBlank()) { bitmap = null; return }
        Thread { bitmap = runCatching { decode(java.net.URL(served(url) + "?v=" + System.currentTimeMillis()).openStream().use { it.readBytes() }, 400) }.getOrNull() ?: bitmap }.start()
    }

    suspend fun load(api: ApiClient, force: Boolean = false) {
        if (loaded && !force) return
        runCatching { withContext(Dispatchers.IO) { api.get("/me").getJSONObject("data") } }.onSuccess { p -> set(p.optString("photo").takeIf { it.isNotBlank() && it != "null" }) }
    }
}

/** A round avatar: your picture when you have one, otherwise your initial. [modifier] gives the size, shape and background. */
@Composable
fun MyAvatar(name: String, modifier: Modifier, fg: Color = Color.White, fontSize: Float = 18f) {
    val b = MyPhoto.bitmap
    Box(Modifier.clip(CircleShape).then(modifier), contentAlignment = Alignment.Center) {
        if (b != null) Image(b.asImageBitmap(), "Profile photo", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = fg, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
    }
}

private val Blue = androidx.compose.ui.graphics.Color(0xFF2F6FE4)
