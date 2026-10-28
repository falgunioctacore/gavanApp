package com.example.gavanhomes.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Where the Laravel app lives. 127.0.0.1 works over USB with `adb reverse tcp:8000 tcp:8000`. */
object ApiConfig {
    const val BASE_URL = "http://127.0.0.1:8000"
    const val PREFIX = "/api/mobile/admin"
}

class ApiException(val code: Int, message: String) : IOException(message)

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String?,
    val roles: List<String>,
    val permissions: List<String>,
) {
    fun can(permission: String) = permissions.contains(permission)
}

/** One page of a list endpoint (`data`, `current_page`, `last_page` — see routes/api.php). */
data class Page(val items: List<JSONObject>, val page: Int, val lastPage: Int, val total: Int)

/** Client for every route in routes/api.php (Bearer-token auth). Call from a background thread. */
class ApiClient(private val tokenProvider: () -> String?) {

    // ---- session -----------------------------------------------------------------------------
    fun login(email: String, password: String, device: String): Pair<String, User> {
        val body = JSONObject().put("email", email).put("password", password).put("device_name", device)
        val json = request("POST", "/login", body, auth = false)
        return json.getString("token") to parseUser(json.getJSONObject("user"))
    }

    /** Public branding for the login screen (GET /app-info): name, logo path, theme colours. */
    fun appInfo(): JSONObject = request("GET", "/app-info", auth = false).getJSONObject("data")

    /** POST /web-session: a single-use signed URL that signs the website in for this token's user. */
    fun webSession(): String = request("POST", "/web-session").getString("url")

    fun me(): User = parseUser(request("GET", "/me").getJSONObject("data"))

    fun logout() {
        request("POST", "/logout")
    }

    fun updateProfile(fields: Map<String, String>): User =
        parseUser(request("PUT", "/profile", JSONObject(fields)).getJSONObject("data"))

    fun changePassword(current: String, new: String) {
        request("PUT", "/profile/password", JSONObject()
            .put("current_password", current).put("password", new).put("password_confirmation", new))
    }

    // ---- data --------------------------------------------------------------------------------
    fun dashboard(): JSONObject = request("GET", "/dashboard")

    /** Any list route: projects, tasks, leads, users, roles, notifications. */
    fun list(resource: String, page: Int = 1, search: String = "", extra: Map<String, String> = emptyMap()): Page {
        val q = StringBuilder("/$resource?page=$page")
        if (search.isNotBlank()) q.append("&search=").append(URLEncoder.encode(search, "UTF-8"))
        extra.forEach { (k, v) -> q.append("&").append(k).append("=").append(URLEncoder.encode(v, "UTF-8")) }
        val j = request("GET", q.toString())
        val arr: JSONArray = j.optJSONArray("data") ?: JSONArray()
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
        return Page(items, j.optInt("current_page", page), j.optInt("last_page", 1), j.optInt("total", items.size))
    }

    fun detail(path: String): JSONObject = request("GET", path).getJSONObject("data")

    /** Modules the signed-in user may open (GET /catalog) — the More menu is built from this. */
    fun catalog(): List<JSONObject> {
        val arr = request("GET", "/catalog").optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    fun markNotificationRead(id: String) {
        request("POST", "/notifications/$id/read")
    }

    fun markAllNotificationsRead() {
        request("POST", "/notifications/read-all")
    }

    // ---- plumbing ----------------------------------------------------------------------------
    /** A user saved by [User.toJson], or null if it can't be read. */
    fun cachedUser(json: String): User? = runCatching { parseUser(JSONObject(json)) }.getOrNull()

    private fun parseUser(u: JSONObject): User {
        fun list(key: String) = u.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        return User(
            id = u.optLong("id"),
            name = u.optString("name"),
            email = u.optString("email"),
            phone = u.optString("phone_number").takeIf { it.isNotEmpty() && it != "null" },
            roles = list("roles"),
            permissions = list("permissions"),
        )
    }

    private fun request(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject {
        val conn = URL(ApiConfig.BASE_URL + ApiConfig.PREFIX + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            if (auth) tokenProvider()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (code !in 200..299) {
                val message = json.optString("message").ifEmpty { json.optJSONObject("errors")?.let { firstError(it) } ?: "Request failed ($code)" }
                throw ApiException(code, message)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun firstError(errors: JSONObject): String? {
        val key = errors.keys().asSequence().firstOrNull() ?: return null
        return errors.optJSONArray(key)?.optString(0)
    }
}

/** `optString` returns "null" for JSON null — treat that (and blanks) as missing. */
fun JSONObject.str(key: String): String? = optString(key).takeIf { it.isNotEmpty() && it != "null" }

fun User.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("email", email).put("phone_number", phone ?: JSONObject.NULL)
    .put("roles", JSONArray(roles)).put("permissions", JSONArray(permissions))
