package com.example.gavanhomes.ui.users

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.str
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.HeroBar
import com.example.gavanhomes.ui.common.KitDanger
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitPage
import com.example.gavanhomes.ui.common.leaveGuard
import com.example.gavanhomes.ui.common.Pill
import com.example.gavanhomes.ui.common.SegTabs
import com.example.gavanhomes.ui.projects.form.Choice
import com.example.gavanhomes.ui.projects.form.ChoiceSheet
import com.example.gavanhomes.ui.projects.form.FormCard
import com.example.gavanhomes.ui.projects.form.Input
import com.example.gavanhomes.ui.projects.form.PickerField
import com.example.gavanhomes.ui.projects.form.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Every plain value of the website's user form, in its field names. */
private class UserForm {
    val text = mutableStateMapOf<String, String>()
    var customWeekend by mutableStateOf(false)
    var weekend by mutableStateOf<Set<Int>>(emptySet())
    var sites by mutableStateOf<Set<Int>>(emptySet())
    var perms by mutableStateOf<Set<String>>(emptySet())
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
    fun get(k: String) = text[k].orEmpty()
}

private val DetailsKeys = setOf("first_name", "last_name", "email", "username", "password", "phone_number", "status", "country", "employee_id")

/**
 * Create / edit a user (id == null → create) — the website's form in three sections: Details, Work & pay, Permissions.
 * Data: GET /users/options (pickers), GET /users/{id} (current values), POST /users · PUT /users/{id} (save, same rules as the website).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserFormScreen(api: ApiClient, b: Branding, id: Int?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = id != null
    val form = remember { UserForm() }

    // this form types another person's password — keep the phone's password manager from offering to save it
    val host = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(host) {
        val before = host.importantForAutofill
        host.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose { host.importantForAutofill = before }
    }

    var roles by remember { mutableStateOf<List<Choice>>(emptyList()) }
    var roleNames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var departments by remember { mutableStateOf<List<Choice>>(emptyList()) }
    var projects by remember { mutableStateOf<List<Choice>>(emptyList()) }
    var allPerms by remember { mutableStateOf<List<String>>(emptyList()) }
    var employees by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var countries by remember { mutableStateOf<List<String>>(emptyList()) }
    var statuses by remember { mutableStateOf<List<String>>(emptyList()) }
    var companyWeekend by remember { mutableStateOf<List<Int>>(listOf(0)) }

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<List<String>>(emptyList()) }
    var picker by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var initial by remember { mutableStateOf<List<Any>?>(null) }   // the values as loaded, to tell whether anything was changed

    LaunchedEffect(attempt) {
        loading = true; loadError = null
        try {
            val o = withContext(Dispatchers.IO) { api.detail("/users/options") }
            fun objs(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
            roleNames = objs("roles").associate { it.getInt("id") to it.getString("name") }
            roles = objs("roles").map { Choice(it.getInt("id"), it.getString("name").headline()) }
            departments = objs("departments").map { Choice(it.getInt("id"), it.getString("name")) }
            projects = objs("projects").map { Choice(it.getInt("id"), it.getString("name")) }
            allPerms = o.optJSONArray("permissions")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            employees = objs("employees")
            countries = o.optJSONArray("countries")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: listOf("India")
            statuses = o.optJSONArray("statuses")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: listOf("active", "pending", "inactive")
            val defaults = o.getJSONObject("defaults")
            companyWeekend = defaults.optJSONArray("weekend_days")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: listOf(0)

            if (id != null) {
                val d = withContext(Dispatchers.IO) { api.detail("/users/$id") }
                listOf("first_name", "last_name", "email", "username", "phone_number", "status", "country", "salary", "grace_minutes", "overtime_rate", "role_id", "department_id")
                    .forEach { form.text[it] = d.str(it).orEmpty() }
                form.text["scheduled_check_in"] = d.str("scheduled_check_in") ?: defaults.str("check_in").orEmpty()
                form.text["scheduled_check_out"] = d.str("scheduled_check_out") ?: defaults.str("check_out").orEmpty()
                form.customWeekend = d.optBoolean("custom_weekend")
                form.weekend = d.optJSONArray("weekend_days")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: companyWeekend.toSet()
                form.sites = d.optJSONArray("site_ids")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
                form.perms = d.optJSONArray("permissions")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
            } else if (form.get("status").isBlank()) {
                form.text["status"] = "active"; form.text["country"] = "India"
                form.text["scheduled_check_in"] = defaults.str("check_in").orEmpty()
                form.text["scheduled_check_out"] = defaults.str("check_out").orEmpty()
                form.weekend = companyWeekend.toSet()
            }
        } catch (e: Exception) { loadError = e.message ?: "Could not load the form" }
        initial = listOf(form.text.toMap(), form.sites, form.perms, form.weekend, form.customWeekend)
        loading = false
    }

    val roleId = form.get("role_id").toIntOrNull()
    val fullAccess = roleNames[roleId] in FullAccessRoles

    fun tabOf(key: String) = when {
        key in DetailsKeys -> 0
        key.startsWith("permissions") -> 2
        else -> 1
    }

    fun save() {
        banner = emptyList(); form.errors = emptyMap()
        val problems = mutableMapOf<String, String>()
        if (form.get("first_name").isBlank()) problems["first_name"] = "Enter a first name"
        if (form.get("email").isBlank()) problems["email"] = "Enter an email"
        if (!editing && form.get("password").length < 8) problems["password"] = "At least 8 characters"
        if (editing && form.get("password").isNotEmpty() && form.get("password").length < 8) problems["password"] = "At least 8 characters"
        if (roleId == null) problems["role_id"] = "Choose a role"
        if (problems.isNotEmpty()) {
            form.errors = problems; tab = tabOf(problems.keys.first()); banner = problems.values.toList(); return
        }

        val body = JSONObject()
        fun opt(k: String) = form.get(k).trim().ifEmpty { null } ?: JSONObject.NULL
        listOf("first_name", "last_name", "email", "username", "phone_number", "status", "country", "salary", "overtime_rate", "scheduled_check_in", "scheduled_check_out")
            .forEach { body.put(it, opt(it)) }
        if (form.get("password").isNotEmpty()) body.put("password", form.get("password"))
        body.put("role_id", roleId)
        body.put("department_id", form.get("department_id").toIntOrNull() ?: JSONObject.NULL)
        body.put("grace_minutes", form.get("grace_minutes").toIntOrNull() ?: JSONObject.NULL)
        body.put("custom_weekend", form.customWeekend)
        body.put("weekend_days", JSONArray(form.weekend.sorted()))
        body.put("site_ids", JSONArray(form.sites.sorted()))
        body.put("permissions", JSONArray(form.perms.sorted()))
        if (!editing) form.get("employee_id").toIntOrNull()?.let { body.put("employee_id", it) }

        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (editing) api.send("PUT", "/users/$id", body) else api.send("POST", "/users", body) }
                Toast.makeText(context, if (editing) "User saved" else "User created", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: ApiException) {
                form.errors = e.errors
                banner = if (e.errors.isEmpty()) listOf(e.message ?: "Could not save") else e.errors.values.filter { it.isNotBlank() }.distinct().take(4)
                e.errors.keys.firstOrNull()?.let { tab = tabOf(it) }
            } catch (e: Exception) { banner = listOf(e.message ?: "Could not save") }
            saving = false
        }
    }

    val back = leaveGuard(initial != null && listOf(form.text.toMap(), form.sites, form.perms, form.weekend, form.customWeekend) != initial, onBack)
    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, if (editing) "Edit user" else "New user", back, compact = true)

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = b.base) }
            loadError != null -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(loadError ?: "", color = KitMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
            }
            else -> {
                val tabs = listOf("Details", "Work & pay") + if (fullAccess) emptyList() else listOf("Permissions · ${form.perms.size}")
                SegTabs(tabs, tab.coerceAtMost(tabs.size - 1)) { tab = it }

                if (banner.isNotEmpty()) Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KitDanger.copy(alpha = .1f)).padding(12.dp),
                ) { banner.forEach { Text("• $it", color = KitDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        val err = form.errors
                        @Composable fun f(key: String, label: String, kb: KeyboardType = KeyboardType.Text, password: Boolean = false) {
                            Input(label, form.get(key), { form.text[key] = it }, err[key], keyboard = kb, password = password)
                        }

                        when (tab.coerceAtMost(tabs.size - 1)) {
                            0 -> {
                                if (!editing) FormCard("From HR record", "Fills in name, email, phone and department — still editable.") {
                                    val emp = employees.firstOrNull { it.optInt("id").toString() == form.get("employee_id") }
                                    PickerField("Employee", emp?.str("name")) { picker = "employee" }
                                }
                                FormCard("Profile") {
                                    f("first_name", "First name *")
                                    f("last_name", "Last name")
                                    f("email", "Email *", KeyboardType.Email)
                                    f("username", "Username")
                                    f("password", if (editing) "Password (leave blank to keep)" else "Password *", password = true)
                                    f("phone_number", "Phone number", KeyboardType.Phone)
                                }
                                FormCard("Account") {
                                    PickerField("Status", form.get("status").headline(), err["status"]) { picker = "status" }
                                    PickerField("Country", form.get("country").ifBlank { null }, err["country"]) { picker = "country" }
                                }
                            }
                            1 -> {
                                FormCard("Assignment") {
                                    PickerField("Role *", roles.firstOrNull { it.id == roleId }?.label, err["role_id"]) { picker = "role" }
                                    PickerField("Department", departments.firstOrNull { it.id.toString() == form.get("department_id") }?.label, err["department_id"]) { picker = "department" }
                                    PickerField("Assigned sites", if (form.sites.isEmpty()) null else "${form.sites.size} selected", err["site_ids"]) { picker = "sites" }
                                    if (form.sites.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        projects.filter { it.id in form.sites }.forEach { Pill(it.label, b.base.copy(alpha = .16f), b.dark) }
                                    }
                                }
                                FormCard("Attendance & pay") {
                                    f("salary", "Monthly salary (₹)", KeyboardType.Decimal)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(Modifier.weight(1f)) { TimeField("Clock in", form, "scheduled_check_in", context) }
                                        Box(Modifier.weight(1f)) { TimeField("Clock out", form, "scheduled_check_out", context) }
                                    }
                                    f("grace_minutes", "Grace period (min)", KeyboardType.Number)
                                    f("overtime_rate", "Overtime rate (₹/hr, blank = auto)", KeyboardType.Decimal)
                                }
                                FormCard("Weekend", "Off, the company default applies (${companyWeekend.sorted().joinToString(", ") { WeekDays[it] }}).") {
                                    SwitchRow("Custom weekend for this person", null, form.customWeekend) {
                                        form.customWeekend = it
                                        if (!it) form.weekend = companyWeekend.toSet()
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        WeekDays.forEachIndexed { i, label ->
                                            val on = i in form.weekend
                                            Text(
                                                label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else KitInk.copy(alpha = if (form.customWeekend) 1f else .4f),
                                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Color(0xFF0F172A).copy(alpha = if (form.customWeekend) 1f else .45f) else Color(0xFFF1EFEB))
                                                    .clickable(enabled = form.customWeekend) { form.weekend = if (on) form.weekend - i else form.weekend + i }.padding(horizontal = 14.dp, vertical = 10.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                FormCard("Direct permissions", "Granted in addition to the role's permissions. Tap a group to toggle all of it.") {
                                    Input("Search permissions", query, { query = it })
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${form.perms.size} selected", color = KitMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        if (form.perms.isNotEmpty()) Text("Clear all", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { form.perms = emptySet() }.padding(8.dp))
                                    }
                                }
                                permGroups(allPerms).filter { g -> query.isBlank() || g.name.contains(query, true) || g.actions.any { it.second.contains(query, true) } }.forEach { g ->
                                    val names = g.actions.map { it.second }
                                    val all = names.all { it in form.perms }
                                    Column(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { form.perms = if (all) form.perms - names.toSet() else form.perms + names },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(g.name.headline(), fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                            Text(if (all) "All ✓" else "${names.count { it in form.perms }}/${names.size}", color = if (all) b.dark else KitMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            g.actions.forEach { (action, full) ->
                                                val on = full in form.perms
                                                Text(
                                                    action.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else KitInk,
                                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Color(0xFF0F172A) else Color(0xFFF1EFEB))
                                                        .clickable { form.perms = if (on) form.perms - full else form.perms + full }.padding(horizontal = 12.dp, vertical = 8.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(b.base, b.deep))).clickable(enabled = !saving) { save() },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (saving) "Saving…" else if (editing) "Save changes" else "Create user", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
        }
    }

    // ── option pickers ──
    when (picker) {
        "employee" -> ChoiceSheet(
            "HR employee", employees.map { Choice(it.optInt("id"), listOfNotNull(it.str("name"), it.str("designation")).joinToString(" — ")) },
            setOf(form.get("employee_id").toIntOrNull() ?: -1), multi = false, allowNone = true, onDismiss = { picker = null },
        ) { ids ->
            val emp = employees.firstOrNull { it.optInt("id") == ids.firstOrNull() }
            form.text["employee_id"] = emp?.optInt("id")?.toString().orEmpty()
            if (emp != null) {
                val full = emp.str("name").orEmpty().trim()
                form.text["first_name"] = full.substringBefore(' ')
                form.text["last_name"] = if (' ' in full) full.substringAfter(' ') else ""
                emp.str("email")?.let { form.text["email"] = it }
                emp.str("phone")?.let { form.text["phone_number"] = it }
                emp.str("department")?.let { dn -> departments.firstOrNull { it.label.equals(dn.trim(), true) }?.let { form.text["department_id"] = it.id.toString() } }
            }
        }
        "status" -> {
            val choices = statuses.mapIndexed { i, s -> Choice(i, s.headline()) }
            ChoiceSheet("Status", choices, setOfNotNull(choices.firstOrNull { statuses[it.id] == form.get("status") }?.id), multi = false, onDismiss = { picker = null }) { ids ->
                ids.firstOrNull()?.let { form.text["status"] = statuses[it] }
            }
        }
        "country" -> {
            val choices = countries.mapIndexed { i, c -> Choice(i, c) }
            ChoiceSheet("Country", choices, setOfNotNull(choices.firstOrNull { it.label == form.get("country") }?.id), multi = false, onDismiss = { picker = null }) { ids ->
                ids.firstOrNull()?.let { form.text["country"] = countries[it] }
            }
        }
        "role" -> ChoiceSheet("Role", roles, setOf(roleId ?: -1), multi = false, onDismiss = { picker = null }) { form.text["role_id"] = it.firstOrNull()?.toString().orEmpty() }
        "department" -> ChoiceSheet("Department", departments, setOf(form.get("department_id").toIntOrNull() ?: -1), multi = false, allowNone = true, onDismiss = { picker = null }) {
            form.text["department_id"] = it.firstOrNull()?.toString().orEmpty()
        }
        "sites" -> ChoiceSheet("Assigned sites", projects, form.sites, multi = true, onDismiss = { picker = null }) { form.sites = it }
    }
}

/** A time stored as HH:mm; a tap opens the clock dialog. */
@Composable
private fun TimeField(label: String, form: UserForm, key: String, context: android.content.Context) {
    val v = form.get(key)
    PickerField(label, v.ifBlank { null }, form.errors[key]) {
        val h = v.substringBefore(':', "9").toIntOrNull() ?: 9
        val m = v.substringAfter(':', "0").take(2).toIntOrNull() ?: 0
        TimePickerDialog(context, { _, hh, mm -> form.text[key] = "%02d:%02d".format(hh, mm) }, h, m, true).show()
    }
}
