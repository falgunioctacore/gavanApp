package com.example.gavanhomes.ui.users

import com.example.gavanhomes.ui.common.AppToast

import com.example.gavanhomes.ui.common.softShadow
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
internal fun splitPermission(name: String): Pair<String, String> {
    val first = name.substringBefore(' ')
    return if (first in Verbs && name.contains(' ')) name.substringAfter(' ') to first.replaceFirstChar { it.uppercase() } else name to "Access"
}

private class Action(val label: String, val perms: List<String>)
private class Module(val label: String, val actions: List<Action>)
private class Group(val name: String, val modules: List<Module>)
private class Template(val key: String, val name: String, val note: String, val scope: String, val permissions: Set<String>)

private fun JSONArray.strings() = (0 until length()).map { getString(it) }

/**
 * Add a role or edit one. Permissions are grouped (Sales, Finance, HR, Construction, Assets, Settings), each module has a clear ON / OFF switch per
 * action, search, and "All / None" per module. Duplicate modules are shown once (one switch sets both). A role also has a scope (Own / Team / All
 * records), an amount limit and an optional project list, and can start from a ready template (the management's role matrix).
 * Built-in roles keep every permission and can't be renamed or deleted.
 * Data: GET /roles/catalog · GET /roles/{id} · POST /roles · PUT /roles/{id} · DELETE /roles/{id}.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleEditor(api: ApiClient, b: Branding, id: Int?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val editing = id != null
    var groups by remember { mutableStateOf<List<Group>?>(null) }
    var templates by remember { mutableStateOf<List<Template>>(emptyList()) }
    var scopes by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var projects by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dataScope by remember { mutableStateOf("own") }
    var limit by remember { mutableStateOf("") }
    var projectIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var system by remember { mutableStateOf(false) }
    var users by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("All") }
    var saving by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var askTemplate by remember { mutableStateOf<Template?>(null) }

    LaunchedEffect(attempt) {
        error = null
        try {
            val c = withContext(Dispatchers.IO) { api.detail("/roles/catalog") }
            groups = (0 until c.getJSONArray("groups").length()).map { gi ->
                val g = c.getJSONArray("groups").getJSONObject(gi)
                Group(g.getString("group"), (0 until g.getJSONArray("modules").length()).map { mi ->
                    val m = g.getJSONArray("modules").getJSONObject(mi)
                    Module(m.getString("label"), (0 until m.getJSONArray("actions").length()).map { ai ->
                        val a = m.getJSONArray("actions").getJSONObject(ai); Action(a.getString("label"), a.getJSONArray("perms").strings())
                    })
                })
            }
            templates = (0 until c.getJSONArray("templates").length()).map { ti ->
                val t = c.getJSONArray("templates").getJSONObject(ti)
                Template(t.getString("key"), t.getString("name"), t.optString("note"), t.optString("scope", "own"), t.getJSONArray("permissions").strings().toSet())
            }
            scopes = (0 until c.getJSONArray("scopes").length()).map { si -> c.getJSONArray("scopes").getJSONObject(si).let { Triple(it.getString("key"), it.getString("label"), it.optString("help")) } }
            projects = (0 until c.getJSONArray("projects").length()).map { pi -> c.getJSONArray("projects").getJSONObject(pi).let { it.getInt("id") to (it.getString("name")) } }
            if (id != null) {
                val d = withContext(Dispatchers.IO) { api.detail("/roles/$id") }
                name = d.optString("name"); system = d.optBoolean("system"); users = d.optInt("users_count")
                picked = d.optJSONArray("permissions")?.strings()?.toSet() ?: emptySet()
                d.optJSONObject("scope")?.let { s ->
                    dataScope = s.optString("data_scope", "own").ifBlank { "own" }
                    limit = if (s.isNull("amount_limit")) "" else s.optDouble("amount_limit").let { v -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString() }
                    projectIds = s.optJSONArray("project_ids")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
                }
            }
        } catch (e: Exception) { error = e.message ?: "Could not load the role" }
    }

    fun save() {
        if (name.isBlank()) { nameError = "Enter a role name"; return }
        saving = true; nameError = null
        val body = JSONObject().put("name", name.trim()).put("permissions", JSONArray(picked.sorted())).put("data_scope", dataScope)
            .put("amount_limit", limit.replace(",", "").toDoubleOrNull() ?: JSONObject.NULL).put("project_ids", JSONArray(projectIds.toList()))
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (id != null) api.send("PUT", "/roles/$id", body) else api.send("POST", "/roles", body) }
                AppToast.show(if (editing) "Role saved" else "Role created")
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
            val list = groups
            when {
                error != null -> com.example.gavanhomes.ui.common.EstateError(error, modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), onRetry = { attempt++ })
                list == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { com.example.gavanhomes.ui.common.EstateLoader(b.base) }
                else -> {
                    val allPerms = list.flatMap { g -> g.modules.flatMap { m -> m.actions.flatMap { it.perms } } }.toSet()
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RoleCard {
                                Input("Role name", name, { name = it; nameError = null }, nameError, readOnly = system)
                                if (system) Text("This is a built-in role: it keeps every permission and can't be renamed or deleted.", color = KitMuted, fontSize = 12.sp)
                                else if (editing && users > 0) Text("$users ${if (users == 1) "person has" else "people have"} this role, so it can't be deleted.", color = KitMuted, fontSize = 12.sp)
                            }

                            if (!system) {
                                RoleCard {
                                    Text("START FROM A TEMPLATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                                    Text("Ready-made roles from the management's role matrix. Pick one, then change anything you need.", fontSize = 12.sp, color = KitMuted)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        templates.filter { it.key != "director" }.forEach { t ->
                                            Text(t.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = KitInk, maxLines = 1,
                                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(com.example.gavanhomes.ui.common.KitSoft).clickable { askTemplate = t }.padding(horizontal = 12.dp, vertical = 9.dp))
                                        }
                                    }
                                }
                                RoleCard {
                                    Text("WHICH RECORDS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        scopes.forEach { (k, label, _) ->
                                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (dataScope == k) Color.White else KitInk, textAlign = TextAlign.Center,
                                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (dataScope == k) b.base else com.example.gavanhomes.ui.common.KitSoft).clickable { dataScope = k }.padding(vertical = 11.dp))
                                        }
                                    }
                                    Text(scopes.firstOrNull { it.first == dataScope }?.third.orEmpty(), fontSize = 12.sp, color = KitMuted)
                                    Input("Amount limit in ₹ (blank = no limit)", limit, { limit = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal)
                                    Text("PROJECTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KitMuted, letterSpacing = 0.6.sp)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        RoleChip("All projects", projectIds.isEmpty(), b.base) { projectIds = emptySet() }
                                        projects.forEach { (pid, pname) -> RoleChip(pname, pid in projectIds, b.base) { projectIds = if (pid in projectIds) projectIds - pid else projectIds + pid } }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${picked.count { it in allPerms }} of ${allPerms.size} permissions on", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text("View only", color = b.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picked = allPerms.filter { it.startsWith("view ") }.toSet() }.padding(8.dp))
                                    Text("All on", color = b.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picked = allPerms }.padding(8.dp))
                                    Text("All off", color = KitMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picked = emptySet() }.padding(8.dp))
                                }
                            }
                            SearchBox(query, { query = it }, "Search permissions…")
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (listOf("All") + list.map { it.name }).forEach { g -> RoleChip(g, tab == g, b.base) { tab = g } }
                            }

                            list.filter { tab == "All" || it.name == tab }.forEach { g ->
                                val shownModules = g.modules.filter { query.isBlank() || it.label.contains(query.trim(), true) || g.name.contains(query.trim(), true) }
                                if (shownModules.isEmpty()) return@forEach
                                Text(g.name.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = b.base, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                                shownModules.forEach { m ->
                                    val names = m.actions.flatMap { it.perms }
                                    val allOn = names.all { it in picked || system }
                                    RoleCard {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(m.label, fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                            if (!system) Text(
                                                if (allOn) "All off" else "All on", color = if (allOn) KitMuted else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picked = if (allOn) picked - names.toSet() else picked + names }.padding(horizontal = 10.dp, vertical = 6.dp),
                                            )
                                        }
                                        m.actions.forEach { a ->
                                            val on = system || a.perms.all { it in picked }
                                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = !system) { picked = if (on) picked - a.perms.toSet() else picked + a.perms }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(a.label, color = if (on) KitInk else KitMuted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                                Text(if (on) "ON" else "OFF", color = if (on) KitGood else KitMuted, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
                                                androidx.compose.material3.Switch(
                                                    checked = on, onCheckedChange = { picked = if (it) picked + a.perms else picked - a.perms.toSet() }, enabled = !system,
                                                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = KitGood, checkedThumbColor = Color.White),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().background(com.example.gavanhomes.ui.common.KitCard).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            if (saving) "Saving…" else if (editing) "Save role" else "Create role", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving) KitMuted else b.base).clickable(enabled = !saving) { save() }.padding(vertical = 15.dp),
                        )
                    }
                }
            }
        }
        askTemplate?.let { t ->
            SwalConfirm(
                title = "Use the “${t.name}” template?", text = t.note + "\n\nThis replaces the permissions and scope below (the name stays). You can still change anything.", confirmText = "Use template",
                confirmColor = b.base,
                onConfirm = { picked = t.permissions; dataScope = t.scope; if (name.isBlank()) name = t.name; askTemplate = null },
                onCancel = { askTemplate = null },
            )
        }
        if (askDelete) SwalConfirm(
            title = "Delete this role?", text = "\"$name\" will be removed. People are not affected — nobody has this role.", confirmText = "Yes, delete it",
            onConfirm = {
                askDelete = false
                scope.launch {
                    try { withContext(Dispatchers.IO) { api.delete("/roles/$id") }; AppToast.show("Role deleted"); onSaved() }
                    catch (e: Exception) { AppToast.show(e.message ?: "Could not delete") }
                }
            },
            onCancel = { askDelete = false },
        )
    }
}

@Composable
private fun RoleCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().softShadow(3.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun RoleChip(text: String, on: Boolean, tint: Color, onClick: () -> Unit) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else KitInk, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) tint else com.example.gavanhomes.ui.common.KitSoft).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp))
}
