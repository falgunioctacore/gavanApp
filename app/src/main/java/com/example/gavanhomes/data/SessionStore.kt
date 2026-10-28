package com.example.gavanhomes.data

import android.content.Context

/** Keeps the API token on the device so the app stays signed in. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("gavan_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().apply { if (value == null) remove("token") else putString("token", value) }.apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
