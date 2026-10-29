package com.example.gavanhomes.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Where the Laravel app lives — set per build type (app/build.gradle.kts): the debug build talks to the local server
 * (127.0.0.1 over USB with `adb reverse tcp:8000 tcp:8000`), the release build to https://gavan.co.in.
 */
object ApiConfig {
    val BASE_URL: String = com.example.gavanhomes.BuildConfig.BASE_URL
    const val PREFIX = "/api/mobile/admin"
}

/** [errors] holds Laravel's per-field validation messages (field name → first message) on a 422. */
class ApiException(val code: Int, message: String, val errors: Map<String, String> = emptyMap()) : IOException(message)

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
data class Page(val items: List<JSONObject>, val page: Int, val lastPage: Int, val total: Int, val raw: JSONObject = JSONObject())

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
        return Page(items, j.optInt("current_page", page), j.optInt("last_page", 1), j.optInt("total", items.size), j)
    }

    fun detail(path: String): JSONObject = request("GET", path).getJSONObject("data")

    /** A GET that returns the whole response (`data` plus extras such as `can`). */
    fun get(path: String): JSONObject = request("GET", path)

    fun delete(path: String): JSONObject = request("DELETE", path)

    /** Any write: POST / PUT with a JSON body; returns the response JSON. */
    fun send(method: String, path: String, body: JSONObject): JSONObject = request(method, path, body)

    /** Pickers for the project form: statuses, project types, people. */
    fun projectOptions(): JSONObject = request("GET", "/projects/options").getJSONObject("data")

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

    private fun request(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject =
        execute(method, path, auth, if (body != null) "application/json" else null, body?.let { b -> { out -> out.write(b.toString().toByteArray()) } })

    /** POST multipart/form-data (the website's own field names; files as parts). Used for forms that upload documents. */
    fun sendForm(path: String, parts: List<FormPart>): JSONObject {
        val boundary = "----gavan" + System.currentTimeMillis()
        val buffer = java.io.ByteArrayOutputStream()
        fun w(s: String) = buffer.write(s.toByteArray(Charsets.UTF_8))
        fun q(s: String) = s.replace("\"", "%22").replace("\r", "").replace("\n", "")
        for (p in parts) {
            w("--$boundary\r\n")
            if (p.bytes != null) {
                w("Content-Disposition: form-data; name=\"${q(p.name)}\"; filename=\"${q(p.fileName ?: "file")}\"\r\nContent-Type: ${p.mime ?: "application/octet-stream"}\r\n\r\n")
                buffer.write(p.bytes)
            } else {
                w("Content-Disposition: form-data; name=\"${q(p.name)}\"\r\n\r\n")
                w(p.value.orEmpty())
            }
            w("\r\n")
        }
        w("--$boundary--\r\n")
        val bytes = buffer.toByteArray()
        return execute("POST", path, true, "multipart/form-data; boundary=$boundary", { out -> out.write(bytes) }, readTimeoutMs = 120_000)
    }

    private fun execute(
        method: String, path: String, auth: Boolean = true, contentType: String? = null,
        write: ((java.io.OutputStream) -> Unit)? = null, readTimeoutMs: Int = 20_000,
    ): JSONObject {
        val conn = URL(ApiConfig.BASE_URL + ApiConfig.PREFIX + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Accept", "application/json")
            if (auth) tokenProvider()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (write != null) {
                conn.doOutput = true
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { write(it) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (code !in 200..299) {
                val errs = json.optJSONObject("errors")
                val message = json.optString("message").ifEmpty { errs?.let { firstError(it) } ?: "Request failed ($code)" }
                val fields = errs?.keys()?.asSequence()?.associateWith { k -> errs.optJSONArray(k)?.optString(0).orEmpty() } ?: emptyMap()
                throw ApiException(code, message, fields)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }
    /** A file the server builds on request (an export): its bytes and the name the server gave it. */
    fun download(path: String): Pair<String, ByteArray> {
        val conn = URL(ApiConfig.BASE_URL + ApiConfig.PREFIX + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 120_000
            tokenProvider()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            val code = conn.responseCode
            if (code !in 200..299) {
                val text = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw ApiException(code, runCatching { JSONObject(text).optString("message") }.getOrNull()?.ifEmpty { null } ?: "Could not create the file ($code)")
            }
            val name = Regex("filename=\"?([^\";]+)\"?").find(conn.getHeaderField("Content-Disposition").orEmpty())?.groupValues?.get(1) ?: "export"
            return name to conn.inputStream.use { it.readBytes() }
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

/** One part of a multipart form: a text field, or a file when [bytes] is set. */
class FormPart(val name: String, val value: String? = null, val fileName: String? = null, val mime: String? = null, val bytes: ByteArray? = null)
