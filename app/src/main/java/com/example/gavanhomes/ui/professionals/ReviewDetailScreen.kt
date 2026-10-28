package com.example.gavanhomes.ui.professionals

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiConfig
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.projects.form.Input
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One submission from a professional — GET /professional-reviews/{id}. A reviewer can approve it or send it back for rework
 * (with instructions, required) — POST /professional-reviews/{id}/review, which also notifies the professional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetailScreen(api: ApiClient, b: Branding, id: Int, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var approve by remember { mutableStateOf(false) }
    var reworkOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        error = null
        try { root = withContext(Dispatchers.IO) { api.get("/professional-reviews/$id") } }
        catch (e: Exception) { error = e.message ?: "Could not load the submission" }
    }
    val view: (String) -> Unit = { path ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${ApiConfig.BASE_URL}/storage/$path"))) }
            .onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }

    fun decide(decision: String, comments: String?, onFail: (String) -> Unit = {}) {
        busy = true
        scope.launch {
            try {
                val body = JSONObject().put("decision", decision)
                if (comments != null) body.put("review_comments", comments)
                withContext(Dispatchers.IO) { api.send("POST", "/professional-reviews/$id/review", body) }
                Toast.makeText(context, if (decision == "approved") "Submission approved" else "Sent back for rework", Toast.LENGTH_SHORT).show()
                onDone()
            } catch (e: ApiException) { onFail(e.errors["review_comments"] ?: e.message ?: "Could not save") }
            catch (e: Exception) { onFail(e.message ?: "Could not save") }
            busy = false
        }
    }

    val d = root?.optJSONObject("data")
    if (d == null) { ProLoading(b, "Submission", error, onBack) { attempt++ }; return }
    val canReview = root?.optJSONObject("can")?.optBoolean("review") == true
    val status = d.str("status")

    Column(Modifier.fillMaxSize().background(KitPage)) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            HeroBar(b, "Submission", onBack) {
                Column {
                    Text(d.str("task") ?: "Task", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(d.str("project") ?: "No project", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(statusLabel(status), if (d.optInt("rework_count") > 0) "${d.optInt("rework_count")}× rework" else null, d.str("due_date")?.let { "Due ${prettyDate(it)}" }).toTypedArray())
                }
            }

            ProCard("Professional") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Initials(d.str("professional") ?: "?", 44.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(d.str("professional") ?: "—", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 16.sp)
                        Text(d.str("professional_code") ?: "", color = KitMuted, fontSize = 12.sp)
                    }
                }
            }

            ProCard("Timeline") {
                ProFact("Assigned", prettyDate(d.str("assigned_at")))
                ProFact("Submitted", prettyDate(d.str("submitted_at")))
                ProFact("Due", prettyDate(d.str("due_date")))
                ProFact("Reviewed", prettyDate(d.str("reviewed_at")))
            }

            d.str("task_description")?.let { desc -> ProCard("Task") { Text(desc, color = KitInk, fontSize = 14.sp, lineHeight = 21.sp) } }

            val files = d.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
            ProCard("Submitted files", "${files.size} file${if (files.size == 1) "" else "s"}") {
                if (files.isEmpty()) Text("No files were uploaded with this submission.", color = KitMuted, fontSize = 14.sp)
                files.forEach { f -> ProDoc(f.str("name") ?: "File", f.str("path"), view) }
            }

            d.str("review_comments")?.let { c -> ProCard("Review comments") { Text(c, color = KitInk, fontSize = 14.sp, lineHeight = 21.sp) } }
            Spacer(Modifier.height(16.dp))
        }

        if (canReview) Row(
            Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Send back", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(KitDanger.copy(alpha = .1f)).clickable(enabled = !busy) { reworkOpen = true }.padding(top = 17.dp),
            )
            Text(
                if (busy) "Saving…" else "Approve", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.4f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(KitGood).clickable(enabled = !busy) { approve = true }.padding(top = 17.dp),
            )
        }
    }

    if (approve) SwalConfirm(
        title = "Approve this submission?", text = "The professional is notified that the work was approved.", confirmText = "Yes, approve", confirmColor = KitGood,
        onCancel = { approve = false },
        onConfirm = { approve = false; decide("approved", null) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } },
    )

    if (reworkOpen) {
        var comments by remember { mutableStateOf("") }
        var problem by remember { mutableStateOf<String?>(null) }
        ModalBottomSheet(onDismissRequest = { reworkOpen = false }, containerColor = Color.White) {
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Send back for rework", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                Text("Tell the professional what to fix. This is required.", color = KitMuted, fontSize = 13.sp)
                Input("Instructions *", comments, { comments = it; problem = null }, problem, singleLine = false)
                Text(
                    if (busy) "Saving…" else "Send back", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(KitDanger).clickable(enabled = !busy) {
                        if (comments.isBlank()) { problem = "Enter instructions for the professional"; return@clickable }
                        decide("rework", comments.trim()) { problem = it }
                    }.padding(top = 15.dp),
                )
            }
        }
    }
}
