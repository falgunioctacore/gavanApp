package com.example.gavanhomes.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.AppToast
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitSoft
import com.example.gavanhomes.ui.common.softShadow
import com.example.gavanhomes.ui.professionals.ReviewDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val TodayTint = Color(0xFF1E5FAE)
private val WaitTint = Color(0xFFC47A1A)

/**
 * "My day": my tasks today, overdue, and what is waiting for my decision — the same three lists as the website.
 * Data: GET /projects/{uuid}/task-center/my-day.
 */
@Composable
internal fun MyDayPage(api: ApiClient, b: Branding, uuid: String, version: Int, onOpen: (Int) -> Unit, onChanged: () -> Unit, onAllMine: () -> Unit) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableIntStateOf(0) }                                   // the task whose status is being saved
    var askDone by remember { mutableStateOf<JSONObject?>(null) }
    var review by remember { mutableIntStateOf(0) }                                 // a professional's submission opened for a decision

    LaunchedEffect(uuid, version, attempt) {
        error = null
        try { data = withContext(Dispatchers.IO) { api.detail("/projects/$uuid/task-center/my-day") } }
        catch (e: Exception) { error = e.message ?: "Could not load your tasks" }
    }

    fun setStatus(id: Int, statusId: Int, done: String) {
        busy = id
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.send("POST", "/task-center/$id/status", JSONObject().put("status_id", statusId)) }
                AppToast.show(done); attempt++; onChanged()
            } catch (e: Exception) { AppToast.show(e.message ?: "Could not update the task") }
            busy = 0
        }
    }

    val d = data
    if (d == null) {
        Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) {
            if (error == null) com.example.gavanhomes.ui.common.EstateLoader(b.base)
            else com.example.gavanhomes.ui.common.EstateError(error!!, color = b.base, modifier = Modifier.padding(24.dp), onRetry = { attempt++ })
        }
        return
    }
    val canUpdate = d.optBoolean("can_update")
    val inProgress = d.optInt("in_progress_id")
    val completed = d.optInt("completed_id")

    @Composable
    fun TaskItem(t: JSONObject) {
        val id = t.optInt("id")
        val late = if (t.isNull("days_late")) null else t.optInt("days_late")
        MyDayCard(onClick = { onOpen(id) }) {
            Text(t.optString("name"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KitInk)
            Text(
                listOfNotNull(t.str("parent")?.let { "Part of $it" }, t.str("priority"), t.str("status"), prettyDay(t.str("due_date"))?.let { "due $it" }).joinToString(" · "),
                fontSize = 12.sp, color = KitMuted, modifier = Modifier.padding(top = 2.dp),
            )
            if (late != null) Text(if (late > 0) "$late day${if (late == 1) "" else "s"} late" else "Late today", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KitDanger)
            if (canUpdate) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t.optInt("status_id") != inProgress) MyDayButton("▶  Start", b.base, filled = false, enabled = busy == 0) { setStatus(id, inProgress, "Task started") }
                MyDayButton(if (busy == id) "Saving…" else "✓  Done", KitGood, filled = true, enabled = busy == 0) { askDone = t }
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        val today = d.objs("today")
        val overdue = d.objs("overdue")
        val waiting = d.objs("waiting")

        MyDayHead("My tasks today", today.size, TodayTint, "Due today, and the ones you have started.")
        if (today.isEmpty()) MyDayEmpty("Nothing due today.") else today.forEach { TaskItem(it) }

        MyDayHead("Overdue", overdue.size, KitDanger, "Past the due date. Oldest first.")
        if (overdue.isEmpty()) MyDayEmpty("Nothing is overdue.") else overdue.forEach { TaskItem(it) }

        MyDayHead("Waiting for me", waiting.size, WaitTint, "Work handed in that needs your decision.")
        if (waiting.isEmpty()) MyDayEmpty("Nothing is waiting for you.") else waiting.forEach { w ->
            val professional = w.optString("kind") == "professional"
            MyDayCard(onClick = { onOpen(w.optInt("task_id")) }) {
                Text(w.optString("name"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KitInk)
                Text("${w.str("who") ?: "Someone"} · ${w.optString("what")}", fontSize = 12.sp, color = KitMuted, modifier = Modifier.padding(top = 2.dp))
                if (professional) Row(Modifier.padding(top = 10.dp)) { MyDayButton("Check now", b.base, filled = true, enabled = true) { review = w.optInt("id") } }
                else Text("Check the photo in Contractors → To verify.", fontSize = 12.sp, color = KitMuted, modifier = Modifier.padding(top = 6.dp))
            }
        }

        val other = d.optInt("other_open")
        if (other > 0) Text(
            "$other more open task${if (other == 1) " of yours is" else "s of yours are"} due later — see all my tasks",
            color = b.base, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { onAllMine() }.padding(16.dp),
        )
    }

    askDone?.let { t ->
        AlertDialog(
            onDismissRequest = { askDone = null },
            title = { Text("Mark as done?") },
            text = { Text(t.optString("name")) },
            confirmButton = { TextButton({ askDone = null; setStatus(t.optInt("id"), completed, "Task marked complete") }) { Text("Done") } },
            dismissButton = { TextButton({ askDone = null }) { Text("Cancel") } },
        )
    }
    if (review > 0) Dialog(onDismissRequest = { review = 0 }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(KitSoft)) {
            ReviewDetailScreen(api, b, review, onBack = { review = 0 }, onDone = { review = 0; attempt++; onChanged() })
        }
    }
}

@Composable
private fun MyDayHead(title: String, count: Int, tint: Color, hint: String) {
    Column(Modifier.padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = tint, modifier = Modifier.weight(1f))
            Text(count.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KitInk)
        }
        Text(hint, fontSize = 12.sp, color = KitMuted)
    }
}

@Composable
private fun MyDayCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 14.dp, vertical = 5.dp).fillMaxWidth().softShadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).background(KitCard).clickable { onClick() }.padding(14.dp),
    ) { content() }
}

@Composable
private fun MyDayButton(label: String, tint: Color, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (filled) tint.copy(alpha = if (enabled) 1f else .5f) else tint.copy(alpha = .12f))
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 18.dp, vertical = 9.dp),
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.White else tint) }
}

@Composable
private fun MyDayEmpty(text: String) {
    Text(text, fontSize = 13.sp, color = KitMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
}
