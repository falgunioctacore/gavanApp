package com.example.gavanhomes.ui.users

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.HeroButton
import com.example.gavanhomes.ui.common.HeroChips
import com.example.gavanhomes.ui.common.Initials
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitGood
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.SegTabs
import com.example.gavanhomes.ui.common.SwalConfirm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Tabs = listOf("Profile", "Work & pay", "Access")
private val Rupees: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }
private val Pretty = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
private fun JSONObject.ints(key: String): List<Int> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList()
private fun JSONObject.objects(key: String): List<JSONObject> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
private fun prettyDate(s: String?): String? = runCatching { LocalDate.parse(s!!.take(10)).format(Pretty) }.getOrNull()

/**
 * A person, all native — GET /users/{id}: profile, work & pay, access (role + direct permissions).
 * Super admins can edit or delete (with a confirmation).
 */
@Composable
fun UserDetailScreen(api: ApiClient, b: Branding, id: Int, onBack: () -> Unit, onEdit: (Int) -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        error = null
        try { root = withContext(Dispatchers.IO) { api.get("/users/$id") } }
        catch (e: Exception) { error = e.message ?: "Could not load the user" }
    }
    fun launch(intent: Intent) { runCatching { context.startActivity(intent) } }

    Column(Modifier.fillMaxSize().background(KitPage)) {
        val d = root?.optJSONObject("data")
        if (d == null) {
            HeroBar(b, "User", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = b.base)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(error ?: "", color = KitMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
                }
            }
            return@Column
        }

        val can = root?.optJSONObject("can")
        val name = d.str("name") ?: "User"
        val role = d.strings("roles").firstOrNull()
        val directCount = d.strings("permissions").size
        val roleCount = d.strings("role_permissions").size

        Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).fillMaxSize().verticalScroll(rememberScrollState())) {
            HeroBar(
                b, "User details", onBack,
                actions = {
                    if (can?.optBoolean("edit") == true) HeroButton("✎") { onEdit(id) }
                    if (can?.optBoolean("delete") == true && !deleting) { Spacer(Modifier.size(8.dp)); com.example.gavanhomes.ui.common.DeleteButton { confirmDelete = true } }
                },
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Initials(name, 64.dp)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text(d.str("email") ?: "", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HeroChips(*listOfNotNull(role?.headline(), d.str("status")?.headline(), d.str("department")).toTypedArray())
                }
            }

            Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(d.ints("site_ids").size.toString(), "Sites", Modifier.weight(1f))
                Tile(roleCount.toString(), "Role permissions", Modifier.weight(1f))
                Tile(directCount.toString(), "Direct permissions", Modifier.weight(1f))
            }

            SegTabs(Tabs, tab) { tab = it }

            when (tab) {
                0 -> Profile(d) { launch(it) }
                1 -> Work(d)
                else -> Access(d, role)
            }

            Spacer(Modifier.height(20.dp))
        }

        if (confirmDelete) SwalConfirm(
            title = "Delete this user?", text = "$name will lose access to the app. This can't be undone.", confirmText = "Yes, delete",
            onCancel = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false; deleting = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.delete("/users/$id") }
                        Toast.makeText(context, "User deleted", Toast.LENGTH_SHORT).show()
                        onDeleted()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Could not delete the user", Toast.LENGTH_LONG).show()
                        deleting = false
                    }
                }
            },
        )
    }
}

// ─────────────────────────────── tabs ───────────────────────────────

@Composable
private fun Profile(d: JSONObject, launch: (Intent) -> Unit) {
    Card("Contact") {
        Fact("Email", d.str("email")) { launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${d.str("email")}"))) }
        Fact("Phone", d.str("phone_number")) { launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${d.str("phone_number")}"))) }
        Fact("Username", d.str("username"))
        Fact("Country", d.str("country"))
    }
    Card("Account") {
        val status = d.str("status")
        Fact("Status", status?.headline())
        Fact("Member since", prettyDate(d.str("created_at")))
        Fact("Created by", d.str("created_by"))
        Fact("Last updated", prettyDate(d.str("updated_at")))
        if (status == "inactive") Text("This account is switched off — the person can't sign in.", color = KitDanger, fontSize = 13.sp)
    }
}

@Composable
private fun Work(d: JSONObject) {
    Card("Assignment") {
        Fact("Role", d.strings("roles").firstOrNull()?.headline())
        Fact("Department", d.str("department"))
    }

    val sites = d.objects("sites")
    Card("Sites", if (sites.isEmpty()) null else "${sites.size} assigned") {
        if (sites.isEmpty()) Text("No sites assigned.", color = KitMuted, fontSize = 14.sp)
        sites.forEach { s -> Text(s.str("name") ?: "Site", color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
    }

    Card("Attendance & pay") {
        Fact("Monthly salary", d.str("salary")?.toDoubleOrNull()?.let { "₹" + Rupees.format(it) } ?: "—")
        Fact("Clock in", d.str("scheduled_check_in") ?: "Company default")
        Fact("Clock out", d.str("scheduled_check_out") ?: "Company default")
        Fact("Grace period", d.str("grace_minutes")?.let { "$it min" } ?: "Company default")
        Fact("Overtime rate", d.str("overtime_rate")?.toDoubleOrNull()?.let { "₹" + Rupees.format(it) + "/hr" } ?: "Auto from salary")
        Fact(
            if (d.optBoolean("custom_weekend")) "Weekend (custom)" else "Weekend (company)",
            d.ints("weekend_days").sorted().joinToString(", ") { WeekDays.getOrElse(it) { "?" } }.ifEmpty { "None" },
        )
    }
}

@Composable
private fun Access(d: JSONObject, role: String?) {
    if (role in FullAccessRoles) {
        Card("Full access") { Text("The ${role?.headline()} role has every permission, so none are listed individually.", color = KitMuted, fontSize = 14.sp) }
        return
    }
    val fromRole = d.strings("role_permissions")
    val direct = d.strings("permissions")
    Card("From role", "${fromRole.size} permission${if (fromRole.size == 1) "" else "s"} · ${role?.headline() ?: "no role"}") {
        if (fromRole.isEmpty()) Text("The role grants no permissions.", color = KitMuted, fontSize = 14.sp)
        PermList(fromRole)
    }
    Card("Direct permissions", "Granted on top of the role") {
        if (direct.isEmpty()) Text("None.", color = KitMuted, fontSize = 14.sp)
        PermList(direct)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermList(names: List<String>) {
    permGroups(names).forEach { g ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(g.name.headline(), fontWeight = FontWeight.Bold, color = KitInk, fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.actions.forEach { (action, _) ->
                    Text(
                        action.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = KitGood,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(KitGood.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────── pieces ───────────────────────────────

@Composable
private fun Card(title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KitInk)
            if (sub != null) Text(sub, color = KitMuted, fontSize = 12.sp)
        }
        content()
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.shadow(5.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KitInk)
        Text(label, fontSize = 11.sp, color = KitMuted, maxLines = 1)
    }
}

/** A label / value line; left out when there is no value, tappable when [onClick] is given. */
@Composable
private fun Fact(label: String, value: String?, onClick: (() -> Unit)? = null) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(.8f))
        Text(value, color = KitInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}
