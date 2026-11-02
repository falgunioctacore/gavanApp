package com.example.gavanhomes.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.gavanhomes.ui.discussion.ChatAlerts
import com.example.gavanhomes.ui.discussion.ChatLink
import org.json.JSONObject

/**
 * The chats that have messages you have not read — one list for the whole app, so every top bar's chat button shows the same badge and
 * the picker can jump straight to a chat. Each chat is {project (uuid), project_name, conv, name, unread}; the shell keeps the list fresh.
 */
object ChatCenter {
    var chats by mutableStateOf<List<JSONObject>>(emptyList())
    var picker by mutableStateOf(false)

    val total: Int get() = chats.sumOf { it.optInt("unread") }

    /** Opens [c] in its own chat screen, and takes it off the unread list right away. */
    fun open(c: JSONObject) {
        picker = false
        ChatAlerts.pending = ChatLink(c.optString("project"), c.optString("conv"), c.optString("name"))
        chats = chats.filter { it.optString("conv") != c.optString("conv") }
    }
}
