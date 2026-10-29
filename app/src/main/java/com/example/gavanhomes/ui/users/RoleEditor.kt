package com.example.gavanhomes.ui.users

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.DeleteButton
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.SearchBox
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.projects.form.Input
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Verbs = setOf("view", "create", "edit", "delete", "approve", "export", "import", "manage", "assign", "update", "download", "upload", "print", "reject", "send")

/** "create account settings" → ("account settings", "Create"); a permission with no verb in front keeps its whole name and the verb "Access". */
private fun splitPermission(name: String): Pair<String, String> {
    val first = name.substringBefore(' ')
    return if (first in Verbs && name.contains(' ')) name.substringAfter(' ') to first.replaceFirstChar { it.uppercase() } else name to "Access"
}

/**
 * Add a role or edit one — the website's Roles page: the role's name, and which permissions it holds, grouped by what they are about
 * (View / Create / Edit / Delete for users, projects, tasks…). Built-in roles keep every permission and can't be renamed or deleted.
 * Data: GET /roles/permissions · GET /roles/{id} · POST /roles · PUT /roles/{id} · DELETE /roles/{id}.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleEditor(api: ApiClient, b: Branding, id: Int?, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = id != null
    var all by remember { mutableStateOf<List<String>?>(null) }
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var system by remember { mutableStateOf(false) }
    var users by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        error = null
        try {
            if (id != null) {
                val d = withContext(Dispatchers.IO) { api.detail("/roles/$id") }
                name = d.optString("name"); system = d.optBoolean("system"); users = d.optInt("users_count")
                picked = d.optJSONArray("permissions")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
                all = d.optJSONArray("all")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            } else {
                all = withContext(Dispatchers.IO) { api.get("/roles/permissions") }.optJSONArray("data")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            }
        } catch (e: Exception) { error = e.message ?: "Could not load the role" }
    }

    fun save() {
        if (name.isBlank()) { nameError = "Enter a role name"; return }
        saving = true; nameError = null
        val body = JSONObject().put("name", name.trim()).put("permissions", JSONArray(picked.sorted()))
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (id != null) api.send("PUT", "/roles/$id", body) else api.send("POST", "/roles", body) }
                Toast.makeText(context, if (editing) "Role saved" else "Role created", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: ApiException) { nameError = e.errors["name"] ?: e.message }
            catch (e: Exception) { nameError = e.message ?: "Could not save" }
            saving = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(KitPage)) {
            HeroBar(
                b, if (editing) "Edit role" else "New role", onClose, compact = true, showBell = false,
                actions = { if (editing && !system && users == 0) DeleteButton { askDelete = true } },
            )
            val list = all
            when {
                error != null -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
                }
                list == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
                else -> {
                    val groups = list.map { it to splitPermission(it) }.groupBy({ it.second.first }, { it.first to it.second.second }).toSortedMap()
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Input("Role name", name, { name = it; nameError = null }, nameError, readOnly = system)
                                if (system) Text("This is a built-in role: it keeps every permission and can't be renamed or deleted.", color = KitMuted, fontSize = 12.sp)
                                else if (editing && users > 0) Text("$users ${if (users == 1) "person has" else "people have"} this role, so it can't be deleted.", color = KitMuted, fontSize = 12.sp)
                            }
                            if (!system) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${picked.size} of ${list.size} permissions", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text("Select all", color = b.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picked = list.toSet() }.padding(8.dp))
                                    Text("Clear", color = KitMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picked = emptySet() }.padding(8.dp))
                                }
                                SearchBox(query, { query = it }, "Search permissions…")
                            }
                            groups.forEach { (group, perms) ->
                                val shown = perms.filter { query.isBlank() || group.contains(query.trim(), true) || it.first.contains(query.trim(), true) }
                                if (shown.isEmpty()) return@forEach
                                val names = perms.map { it.first }
                                val allOn = names.all { it in picked || system }
                                Column(Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(group.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                        if (!system) Text(
                                            if (allOn) "None" else "All", color = if (allOn) KitMuted else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picked = if (allOn) picked - names.toSet() else picked + names }.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        shown.forEach { (perm, verb) ->
                                            val on = system || perm in picked
                                            Text(
                                                verb, color = if (on) Color.White else KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) (if (system) KitMuted else KitGood) else Color(0xFFF1EFEB))
                                                    .then(if (on) Modifier else Modifier.border(1.dp, Color(0xFFE0DDD6), RoundedCornerShape(14.dp)))
                                                    .clickable(enabled = !system) { picked = if (perm in picked) picked - perm else picked + perm }.padding(horizontal = 14.dp, vertical = 8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            if (saving) "Saving…" else if (editing) "Save role" else "Create role", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { save() }.padding(vertical = 15.dp),
                        )
                    }
                }
            }
        }
        if (askDelete) SwalConfirm(
            title = "Delete this role?", text = "\"$name\" will be removed. People are not affected — nobody has this role.", confirmText = "Yes, delete it",
            onConfirm = {
                askDelete = false
                scope.launch {
                    try { withContext(Dispatchers.IO) { api.delete("/roles/$id") }; Toast.makeText(context, "Role deleted", Toast.LENGTH_SHORT).show(); onSaved() }
                    catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not delete", Toast.LENGTH_LONG).show() }
                }
            },
            onCancel = { askDelete = false },
        )
    }
}
