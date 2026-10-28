package com.example.gavanhomes.ui.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.User
import com.example.gavanhomes.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Blue = Color(0xFF2F6FE4)
private val Navy = Color(0xFF1F3F78)
private val Ink = Color(0xFF0F172A)
private val Muted = Color(0xFF8A94A6)

private fun errorText(e: Throwable) = e.message ?: "Something went wrong"

// ─────────────────────────────────────────── shared bits ───────────────────────────────────────────

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue) }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = Muted)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun Pill(text: String, bg: Color = Color(0xFFE8F0FF), fg: Color = Blue) {
    Text(
        text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Hero(height: Int = 120, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(Navy, Blue)))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(height.dp),
    ) { content() }
}

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(Navy).statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Text("←", color = Color.White, fontSize = 22.sp) }
        } else Spacer(Modifier.width(12.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

// ─────────────────────────────────────────── login ───────────────────────────────────────────

@Composable
fun LoginScreen(api: ApiClient, onSignedIn: (String, User) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        busy = true; error = null
        scope.launch {
            try {
                val (token, user) = withContext(Dispatchers.IO) { api.login(email.trim(), password, android.os.Build.MODEL) }
                onSignedIn(token, user)
            } catch (e: Exception) {
                error = errorText(e)
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("Welcome back", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Text("Sign in to the Gavan Homes admin app.", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color(0xFFD03B3B), modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = ::submit, enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue),
        ) { Text(if (busy) "Signing in…" else "Sign in", fontWeight = FontWeight.SemiBold) }
    }
}

// ─────────────────────────────────────────── home (GET /dashboard) ───────────────────────────────────────────

@Composable
fun HomeScreen(api: ApiClient, user: User) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        error = null
        try { data = withContext(Dispatchers.IO) { api.dashboard() } } catch (e: Exception) { error = errorText(e) }
    }

    val d = data
    when {
        error != null -> ErrorBox(error!!) { reload++ }
        d == null -> Loading()
        else -> {
            val stats = d.optJSONObject("data") ?: JSONObject()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                item {
                    Hero(height = 190) {
                        Column {
                            Text("Welcome back,", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                            Text(user.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            val (label, value) = when {
                                stats.has("leads") -> "Total leads" to stats.optInt("leads")
                                stats.has("projects") -> "Total projects" to stats.optInt("projects")
                                else -> "Notifications" to d.optInt("unread_notifications")
                            }
                            Text(value.toString(), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                            Text(label.lowercase(), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                        }
                    }
                }
                item {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("projects" to "Projects", "tasks" to "Tasks", "users" to "Users").filter { stats.has(it.first) }.forEach { (key, label) ->
                            StatCard(label, stats.optInt(key).toString(), Modifier.weight(1f))
                        }
                    }
                }
                if (d.has("tasks_overdue")) item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Overdue", d.optInt("tasks_overdue").toString(), Modifier.weight(1f), Color(0xFFD03B3B))
                        StatCard("Completion", "${d.optInt("task_completion")}%", Modifier.weight(1f), Color(0xFF0E8A50))
                        if (d.has("leads_hot")) StatCard("Hot leads", d.optInt("leads_hot").toString(), Modifier.weight(1f), Color(0xFFF59E0B))
                    }
                }
                d.optJSONObject("task_status")?.let { s -> item { Distribution("Task status", s) } }
                d.optJSONObject("lead_status")?.let { s -> item { Distribution("Lead pipeline", s) } }
                d.optJSONArray("recent_tasks")?.let { arr ->
                    item { SectionCard("Upcoming tasks") {
                        for (i in 0 until arr.length()) {
                            val t = arr.getJSONObject(i)
                            RowLine(t.str("name") ?: "Task", listOfNotNull(t.str("project"), t.str("due_date")?.take(10)).joinToString(" · "), t.str("status"))
                        }
                    } }
                }
                d.optJSONArray("recent_leads")?.let { arr ->
                    item { SectionCard("Recent leads") {
                        for (i in 0 until arr.length()) {
                            val l = arr.getJSONObject(i)
                            RowLine(l.str("client_name") ?: "Lead", l.str("phone_number") ?: "", l.str("status"))
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Blue) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = accent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun Distribution(title: String, counts: JSONObject) {
    val keys = counts.keys().asSequence().toList()
    val max = (keys.maxOfOrNull { counts.optInt(it) } ?: 1).coerceAtLeast(1)
    SectionCard(title) {
        keys.forEach { k ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, fontSize = 13.sp); Text(counts.optInt(k).toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            LinearProgressIndicator(
                progress = { counts.optInt(k).toFloat() / max }, color = Blue, trackColor = Color(0xFFE8F0FF),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            )
        }
    }
}

@Composable
private fun RowLine(title: String, subtitle: String, badge: String?, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        if (!badge.isNullOrBlank()) { Spacer(Modifier.width(8.dp)); Pill(badge) }
    }
}

// ─────────────────────────────────────────── generic list over GET /{resource} ───────────────────────────────────────────

class ListSpec(
    val title: String,
    val resource: String,
    val searchable: Boolean = true,
    val detailPath: ((JSONObject) -> String?)? = null,           // GET path of a record's detail, if it has one
    val row: (JSONObject) -> Triple<String, String, String?>,   // title, subtitle, badge
)

object Screens {
    val Projects = ListSpec("Projects", "projects") {
        Triple(it.str("name") ?: "Project", listOfNotNull(it.str("code"), "${it.optInt("tasks_count")} tasks", "${it.optInt("members_count")} members").joinToString(" · "),
            it.str("status") ?: if (it.optBoolean("active")) "Active" else "Inactive")
    }
    val Tasks = ListSpec("Tasks", "tasks") {
        Triple(it.str("name") ?: "Task", listOfNotNull(it.optJSONObject("project")?.str("name"), it.str("due_date")?.take(10)).joinToString(" · "), it.str("status"))
    }
    val Leads = ListSpec("Leads", "leads") {
        Triple(it.str("client_name") ?: "Lead", listOfNotNull(it.str("phone_number"), it.str("assignee")).joinToString(" · "), it.str("status"))
    }
    val Users = ListSpec("Users", "users") {
        val roles = it.optJSONArray("roles")
        Triple(it.str("name") ?: "User", it.str("email") ?: "", if (roles != null && roles.length() > 0) roles.getString(0) else null)
    }
    val Roles = ListSpec("Roles", "roles", searchable = false) {
        Triple(it.str("name") ?: "Role", "${it.optInt("users_count")} users · ${it.optInt("permissions_count")} permissions", if (it.optBoolean("protected")) "System" else null)
    }
    /** Any module served by GET /catalog/{key}: rows arrive already shaped as title / subtitle / badge. */
    fun catalog(key: String, label: String) = ListSpec(label, "catalog/$key", detailPath = { "catalog/$key/${it.optInt("id")}" }) {
        Triple(it.str("title") ?: "Record", it.str("subtitle") ?: "", it.str("badge"))
    }
    val Notifications = ListSpec("Notifications", "notifications", searchable = false) {
        Triple(it.str("title") ?: "Notification", it.str("created_at")?.take(16)?.replace('T', ' ') ?: "", if (!it.optBoolean("read")) "New" else null)
    }
}

@Composable
fun ResourceList(api: ApiClient, spec: ListSpec, onBack: (() -> Unit)? = null) {
    val items = remember(spec) { mutableStateListOf<JSONObject>() }
    var page by remember(spec) { mutableStateOf(0) }
    var lastPage by remember(spec) { mutableStateOf(1) }
    var total by remember(spec) { mutableStateOf(0) }
    var search by remember(spec) { mutableStateOf("") }
    var busy by remember(spec) { mutableStateOf(false) }
    var error by remember(spec) { mutableStateOf<String?>(null) }
    var detail by remember(spec) { mutableStateOf<JSONObject?>(null) }
    var detailError by remember(spec) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun openDetail(obj: JSONObject) {
        val path = spec.detailPath?.invoke(obj) ?: return
        scope.launch {
            try { detail = withContext(Dispatchers.IO) { api.detail(path) } } catch (e: Exception) { detailError = errorText(e) }
        }
    }

    fun load(reset: Boolean) {
        if (busy) return
        busy = true; error = null
        val next = if (reset) 1 else page + 1
        scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { api.list(spec.resource, next, search) }
                if (reset) items.clear()
                items.addAll(p.items); page = p.page; lastPage = p.lastPage; total = p.total
            } catch (e: Exception) {
                error = errorText(e)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(spec) { load(true) }
    // debounce typing into the server-side `search` filter
    LaunchedEffect(search) {
        if (page > 0 || items.isNotEmpty()) { kotlinx.coroutines.delay(350); load(true) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(spec.title, onBack)
        if (spec.searchable) {
            OutlinedTextField(
                value = search, onValueChange = { search = it }, singleLine = true, placeholder = { Text("Search ${spec.title.lowercase()}…") },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        detail?.let { DetailDialog(it) { detail = null } }
        detailError?.let { msg ->
            androidx.compose.material3.AlertDialog(onDismissRequest = { detailError = null }, confirmButton = { androidx.compose.material3.TextButton(onClick = { detailError = null }) { Text("OK") } }, text = { Text(msg) })
        }
        when {
            error != null && items.isEmpty() -> ErrorBox(error!!) { load(true) }
            busy && items.isEmpty() -> Loading()
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing here yet", color = Muted) }
            else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { obj ->
                    val (t, s, b) = spec.row(obj)
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            RowLine(t, s, b, onClick = when {
                                spec.resource == "notifications" && !obj.optBoolean("read") -> ({
                                    scope.launch { runCatching { withContext(Dispatchers.IO) { api.markNotificationRead(obj.getString("id")) } }; load(true) }
                                })
                                spec.detailPath != null -> ({ openDetail(obj) })
                                else -> null
                            })
                        }
                    }
                }
                if (page < lastPage) item {
                    OutlinedButton(onClick = { load(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Loading…" else "Load more (${items.size} of $total)")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────── more / profile ───────────────────────────────────────────

@Composable
fun MoreScreen(api: ApiClient, user: User, onOpen: (String) -> Unit, onSignOut: () -> Unit) {
    var modules by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { modules = withContext(Dispatchers.IO) { api.catalog() } } catch (e: Exception) { error = errorText(e) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("More", null)
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { MoreItem("◉", "Profile", user.email) { onOpen("profile") } }
            if (user.can("view users")) {
                item { MoreItem("☺", "Users", "People who can sign in") { onOpen("users") } }
                item { MoreItem("⚿", "Roles & permissions", "Access control") { onOpen("roles") } }
            }
            item { MoreItem("♪", "Notifications", "Latest alerts") { onOpen("notifications") } }

            // every other module comes from GET /catalog, so only what the API serves (and you may open) is listed
            modules?.groupBy { it.optString("group") }?.forEach { (group, items) ->
                item { Text(group.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
                items(items) { m ->
                    MoreItem("▸", m.optString("label"), "${m.optInt("count")} records") { onOpen("catalog:${m.optString("key")}:${m.optString("label")}") }
                }
            }
            if (modules == null && error == null) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue) } }
            error?.let { e -> item { Text(e, color = Color(0xFFD03B3B)) } }
            item { OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Log out", color = Color(0xFFD03B3B)) } }
        }
    }
}

/** Record detail from GET /catalog/{key}/{id}: a title and label / value rows. */
@Composable
private fun DetailDialog(detail: JSONObject, onClose: () -> Unit) {
    val fields = detail.optJSONArray("fields")
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Close") } },
        title = { Text(detail.str("title") ?: "Details", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                if (fields != null) items((0 until fields.length()).map { fields.getJSONObject(it) }) { f ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(f.optString("label"), color = Muted, fontSize = 11.sp)
                        Text(f.optString("value"), fontSize = 14.sp)
                    }
                }
            }
        },
    )
}

@Composable
private fun MoreItem(glyph: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F0FF)), contentAlignment = Alignment.Center) {
                Text(glyph, color = Blue, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            Text("›", color = Muted, fontSize = 22.sp)
        }
    }
}

@Composable
fun ProfileScreen(api: ApiClient, user: User, onUser: (User) -> Unit, onBack: () -> Unit, onSignOut: () -> Unit) {
    val words = user.name.trim().split(" ")
    var first by remember { mutableStateOf(words.firstOrNull().orEmpty()) }
    var last by remember { mutableStateOf(words.drop(1).joinToString(" ")) }
    var email by remember { mutableStateOf(user.email) }
    var phone by remember { mutableStateOf(user.phone.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopBar("Profile", onBack)
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(user.roles.joinToString(" · ").ifBlank { "Admin" }, color = Muted)
            }
            item { OutlinedTextField(first, { first = it }, label = { Text("First name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(last, { last = it }, label = { Text("Last name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            message?.let { m -> item { Text(m, color = if (m.startsWith("Saved")) Color(0xFF0E8A50) else Color(0xFFD03B3B)) } }
            item {
                Button(
                    onClick = {
                        busy = true; message = null
                        scope.launch {
                            try {
                                val updated = withContext(Dispatchers.IO) {
                                    api.updateProfile(mapOf("first_name" to first, "last_name" to last, "email" to email, "phone_number" to phone))
                                }
                                onUser(updated); message = "Saved"
                            } catch (e: Exception) { message = errorText(e) } finally { busy = false }
                        }
                    },
                    enabled = !busy && first.isNotBlank() && last.isNotBlank() && email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                ) { Text(if (busy) "Saving…" else "Save changes") }
            }
            item { OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Log out", color = Color(0xFFD03B3B)) } }
        }
    }
}
