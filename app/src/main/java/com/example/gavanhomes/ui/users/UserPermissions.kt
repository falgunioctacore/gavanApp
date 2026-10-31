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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.SearchBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val RoleBlue = Color(0xFF4A8AAD)

/**
 * One person's permissions, module by module: what their role already gives (blue, locked — change those on the role), and the
 * extra actions granted to them alone (green, tap to switch on or off). Data: GET / PUT /users/{id}/permissions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserPermissionsScreen(api: ApiClient, b: Branding, id: Int, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var only by remember { mutableStateOf("all") }          // all · granted · extra
    var saving by remember { mutableStateOf(false) }
    fun JSONObject.list(k: String) = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    LaunchedEffect(attempt) {
        error = null
        try { d = withContext(Dispatchers.IO) { api.detail("/users/$id/permissions") }.also { picked = it.list("permissions").toSet() } }
        catch (e: Exception) { error = e.message ?: "Could not load the permissions" }
    }
    fun save() {
        saving = true
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.send("PUT", "/users/$id/permissions", JSONObject().put("permissions", JSONArray(picked.sorted()))) }
                Toast.makeText(context, r.optString("message", "Permissions saved"), Toast.LENGTH_SHORT).show(); onSaved()
            } catch (e: Exception) { Toast.makeText(context, e.message ?: "Could not save", Toast.LENGTH_LONG).show() }
            saving = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(KitPage)) {
            val x = d
            HeroBar(b, "Permissions", onClose, compact = true, showBell = false)
            when {
                x == null && error != null -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
                }
                x == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
                else -> {
                    val all = x.list("all")
                    val fromRole = x.list("role_permissions").toSet()
                    val full = x.optBoolean("full_access")
                    val editable = x.optBoolean("can_edit") && !full
                    val has = { p: String -> full || p in fromRole || p in picked }
                    val groups = all.map { it to splitPermission(it) }.groupBy({ it.second.first }, { it.first to it.second.second }).toSortedMap()
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(x.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
                            Text("Role: ${x.optString("role").ifBlank { "none" }.headline()}", color = KitMuted, fontSize = 13.sp)
                            Text(
                                when {
                                    full -> "This role has every permission, so there is nothing to add."
                                    !x.optBoolean("can_edit") -> "Only a super admin can change permissions — this is a view of what they can do."
                                    else -> "Blue comes from the role (change it on the role). Tap grey to give this person an extra action; green ones are theirs alone."
                                }, color = KitMuted, fontSize = 12.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Legend("From role · ${fromRole.size}", RoleBlue); Legend("Extra · ${picked.size}", KitGood); Legend("Can do · ${all.count(has)} of ${all.size}", KitInk)
                            }
                        }
                        if (!full) {
                            SearchBox(query, { query = it }, "Search modules or actions…")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("all" to "Everything", "granted" to "What they can do", "extra" to "Extras only").forEach { (k, l) ->
                                    com.example.gavanhomes.ui.tasks.Pick(l, only == k, b.base) { only = k }
                                }
                            }
                            if (editable && picked.isNotEmpty()) Text("Remove all extras", color = KitMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picked = emptySet() }.padding(6.dp))
                        }
                        groups.forEach { (group, perms) ->
                            val shown = perms.filter { (p, _) ->
                                (query.isBlank() || group.contains(query.trim(), true) || p.contains(query.trim(), true)) &&
                                    when (only) { "granted" -> has(p); "extra" -> p in picked; else -> true }
                            }
                            if (shown.isEmpty() || full) return@forEach
                            val open = perms.map { it.first }.filter { it !in fromRole }
                            val allOn = open.isNotEmpty() && open.all { it in picked }
                            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(group.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Text("${perms.count { has(it.first) }}/${perms.size}", color = KitMuted, fontSize = 12.sp)
                                    if (editable && open.isNotEmpty()) Text(
                                        if (allOn) "None" else "All", color = if (allOn) KitMuted else b.base, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).clickable { picked = if (allOn) picked - open.toSet() else picked + open }.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    shown.forEach { (perm, verb) ->
                                        val role = perm in fromRole
                                        val extra = perm in picked
                                        val bg = when { role -> RoleBlue; extra -> KitGood; else -> Color(0xFFF1EFEB) }
                                        Text(
                                            (if (role) "🔒 " else "") + verb, color = if (role || extra) Color.White else KitInk, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(bg)
                                                .then(if (role || extra) Modifier else Modifier.border(1.dp, Color(0xFFE0DDD6), RoundedCornerShape(14.dp)))
                                                .clickable(enabled = editable && !role) { picked = if (extra) picked - perm else picked + perm }.padding(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (editable) Box(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        val changed = picked != x.list("permissions").toSet()
                        Text(
                            if (saving) "Saving…" else if (changed) "Save permissions" else "No changes", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (saving || !changed) KitMuted else b.base).clickable(enabled = !saving && changed) { save() }.padding(vertical = 15.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(label: String, c: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(c))
        Text(" $label", color = KitMuted, fontSize = 11.sp)
    }
}
