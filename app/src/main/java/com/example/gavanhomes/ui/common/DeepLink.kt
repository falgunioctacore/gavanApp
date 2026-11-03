package com.example.gavanhomes.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A page the person asked to open from a notification — a tapped alert in the notification bar, or a row of the notifications list: a task or a
 * target (kind "task" / "target") of a project (its uuid). The shell opens the project and the project opens the page, then clears [pending].
 */
object DeepLink {
    class Open(val kind: String, val id: Int, val project: String)

    var pending by mutableStateOf<Open?>(null)

    /** The page a notification row leads to, or null when it has none (the server adds open / open_id / open_project to task and target ones). */
    fun from(n: org.json.JSONObject): Open? {
        val kind = n.optString("open"); val id = n.optInt("open_id"); val project = n.optString("open_project")
        return if (kind.isNotBlank() && id > 0 && project.isNotBlank()) Open(kind, id, project) else null
    }
}
