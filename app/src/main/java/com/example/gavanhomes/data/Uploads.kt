package com.example.gavanhomes.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Files on their way to the server. [ApiClient.sendForm] reports every upload that carries a file here, so the app can show a progress bar
 * ([com.example.gavanhomes.ui.UploadBar]) while it goes: how many are running, the name of the file and how much of it has been sent.
 */
object Uploads {
    var active by mutableIntStateOf(0)
        private set
    var fraction by mutableFloatStateOf(0f)
        private set
    var name by mutableStateOf<String?>(null)
        private set
    private var total = 0L
    private var sent = 0L

    @Synchronized fun begin(size: Int, fileName: String?) { if (active == 0) { total = 0; sent = 0; fraction = 0f }; active++; total += size; name = fileName ?: name }
    @Synchronized fun advance(n: Int) { sent += n; fraction = if (total > 0) (sent.toFloat() / total).coerceIn(0f, 1f) else 0f }
    @Synchronized fun end() { active = (active - 1).coerceAtLeast(0); if (active == 0) { fraction = 0f; name = null } }
}

/**
 * Every successful change (POST / PUT / DELETE) tells the app here what the server said ("Task assigned.", "File deleted." …), so that a toast alert
 * follows ANY action even when its screen forgot to show one: [listener] shows the server's message unless the screen showed its own just now.
 * Chat messages, sign-in and read-markers are left out — they are not "actions" to announce.
 */
object WriteNotice {
    @Volatile var listener: ((String) -> Unit)? = null
    private val quiet = Regex("login|logout|web-session|/thread|/chats|/messages|/read|/discussion|app-info|alerts|/notifications|/typing|/me$|/token|/pin")
    @Volatile var invalidListener: ((String) -> Unit)? = null
    fun invalid(message: String?) { val m = message?.trim().orEmpty(); if (m.isNotEmpty()) invalidListener?.invoke(m) }
    fun ok(path: String, message: String?) {
        val text = message?.trim().orEmpty()
        if (text.isEmpty() || text == "null" || quiet.containsMatchIn(path)) return
        listener?.invoke(text)
    }
}
