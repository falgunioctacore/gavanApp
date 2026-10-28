package com.example.gavanhomes.ui.common

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
/** Colours + name from GET /app-info, so the app follows the website's theme. Falls back to the gold defaults. */
class Branding(
    val name: String = "GavanHomes",
    val base: Color = Color(0xFFCFA251),
    val deep: Color = Color(0xFF5D4924),
    val dark: Color = Color(0xFF785E2F),
    val logo: ImageBitmap? = null,
)

internal fun parseHex(hex: String?, fallback: Color) = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

suspend fun loadBranding(api: ApiClient): Branding = withContext(Dispatchers.IO) {
    runCatching {
        val info = api.appInfo()
        val theme = info.optJSONObject("theme")
        val logo = runCatching {
            URL(ApiConfig.BASE_URL + "/" + info.optString("logo_path")).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
        Branding(
            name = info.optString("name").ifBlank { "GavanHomes" },
            base = parseHex(theme?.optString("base"), Color(0xFFCFA251)),
            deep = parseHex(theme?.optString("darker"), Color(0xFF5D4924)),
            dark = parseHex(theme?.optString("dark"), Color(0xFF785E2F)),
            logo = logo,
        )
    }.getOrDefault(Branding())
}

