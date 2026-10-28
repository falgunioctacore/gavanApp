package com.example.gavanhomes.data

import android.content.Context

/** Keeps the API token, the signed-in user and the last dashboard on the device, so the app opens instantly. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("gavan_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = put("token", value)

    /** The signed-in user as JSON (see ApiClient.cachedUser). */
    var userJson: String?
        get() = prefs.getString("user", null)
        set(value) = put("user", value)

    /** Last dashboard response — shown straight away while a fresh one loads. */
    var dashboardJson: String?
        get() = prefs.getString("dashboard", null)
        set(value) = put("dashboard", value)

    private fun put(key: String, value: String?) =
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
