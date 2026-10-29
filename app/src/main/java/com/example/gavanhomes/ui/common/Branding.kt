package com.example.gavanhomes.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
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

/**
 * The last branding seen, kept on the phone so the first frame already has the right colours, name and logo — the server's answer
 * (slow on a bad connection) then only refreshes it. Public data, so it is kept across sign-outs for the login screen too.
 */
object BrandingCache {
    private const val FILE = "brand_logo.bin"
    private fun prefs(c: Context) = c.getSharedPreferences("gavan_branding", Context.MODE_PRIVATE)

    fun load(c: Context): Branding? {
        val p = prefs(c)
        if (!p.contains("base")) return null
        val logo = runCatching { java.io.File(c.filesDir, FILE).takeIf { it.exists() }?.readBytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
        return Branding(
            name = p.getString("name", null) ?: "GavanHomes",
            base = Color(p.getInt("base", 0)), deep = Color(p.getInt("deep", 0)), dark = Color(p.getInt("dark", 0)),
            logo = logo,
        )
    }

    fun save(c: Context, b: Branding, logoBytes: ByteArray?) {
        prefs(c).edit().putString("name", b.name).putInt("base", b.base.toArgb()).putInt("deep", b.deep.toArgb()).putInt("dark", b.dark.toArgb()).apply()
        runCatching { if (logoBytes != null) java.io.File(c.filesDir, FILE).writeBytes(logoBytes) }
    }
}

/** What to show before the server has answered: the cached branding, else the defaults. */
fun cachedBranding(context: Context): Branding = BrandingCache.load(context) ?: Branding()

/** Fetches the branding (GET /app-info + the logo) and, when a [context] is given, remembers it for the next start. */
suspend fun loadBranding(api: ApiClient, context: Context? = null): Branding = withContext(Dispatchers.IO) {
    runCatching {
        val info = api.appInfo()
        val theme = info.optJSONObject("theme")
        val logoBytes = runCatching { URL(ApiConfig.BASE_URL + "/" + info.optString("logo_path")).openStream().use { it.readBytes() } }.getOrNull()
        val logo = logoBytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
        Branding(
            name = info.optString("name").ifBlank { "GavanHomes" },
            base = parseHex(theme?.optString("base"), Color(0xFFCFA251)),
            deep = parseHex(theme?.optString("darker"), Color(0xFF5D4924)),
            dark = parseHex(theme?.optString("dark"), Color(0xFF785E2F)),
            logo = logo,
        ).also { if (context != null) BrandingCache.save(context, it, logoBytes) }
    }.getOrElse { (context?.let { c -> BrandingCache.load(c) }) ?: Branding() }    // offline: keep what we had
}
