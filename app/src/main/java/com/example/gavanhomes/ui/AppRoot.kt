package com.example.gavanhomes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.SessionStore
import com.example.gavanhomes.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BrandBlue = Color(0xFF2F6FE4)
private val Ink = Color(0xFF0F172A)

private val AppColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = Ink,
    background = Color(0xFFF3F6FC),
    surface = Color.White,
    onSurface = Color(0xFF1F2A44),
)

private enum class Tab(val label: String, val glyph: String) {
    Home("Home", "⌂"),
    Projects("Projects", "▦"),
    Tasks("Tasks", "✓"),
    Leads("Leads", "☎"),
    More("More", "☰"),
}

/** Signed-out → LoginScreen; signed-in → tabs. Token lives in SessionStore; all data comes from the API. */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    var token by remember { mutableStateOf(store.token) }
    val api = remember { ApiClient { token } }
    var user by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()

    // resume a saved session; an expired token sends you back to login
    androidx.compose.runtime.LaunchedEffect(token) {
        if (token != null && user == null) {
            try {
                user = withContext(Dispatchers.IO) { api.me() }
            } catch (e: ApiException) {
                if (e.code == 401) { store.clear(); token = null }
            } catch (_: Exception) { /* offline: keep the token, retry on next launch */ }
        }
    }

    fun signOut() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.logout() } }
            store.clear(); token = null; user = null
        }
    }

    MaterialTheme(colorScheme = AppColors, typography = MaterialTheme.typography) {
        val u = user
        if (token == null) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = 460.dp)) {
                    LoginScreen(api = api) { newToken, newUser ->
                        store.token = newToken; token = newToken; user = newUser
                    }
                }
            }
        } else if (u == null) {
            Box(Modifier.fillMaxSize()) { Loading() }
        } else {
            MainScreens(api = api, user = u, onUser = { user = it }, onSignOut = ::signOut)
        }
    }
}

@Composable
private fun MainScreens(api: ApiClient, user: User, onUser: (User) -> Unit, onSignOut: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var more by rememberSaveable { mutableStateOf<String?>(null) }   // sub-screen opened from "More"

    BackHandler(enabled = more != null) { more = null }
    BackHandler(enabled = more == null && tab != Tab.Home) { tab = Tab.Home }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; more = null },
                        icon = { Text(t.glyph, fontSize = 20.sp) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        // phones: full width; tablets: a centred 720dp column
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            when {
                tab == Tab.Home -> HomeScreen(api, user)
                tab == Tab.Projects -> ResourceList(api, Screens.Projects)
                tab == Tab.Tasks -> ResourceList(api, Screens.Tasks)
                tab == Tab.Leads -> ResourceList(api, Screens.Leads)
                more == null -> MoreScreen(api, user, onOpen = { more = it }, onSignOut = onSignOut)
                more == "profile" -> ProfileScreen(api, user, onUser, onBack = { more = null }, onSignOut = onSignOut)
                more == "users" -> ResourceList(api, Screens.Users, onBack = { more = null })
                more == "roles" -> ResourceList(api, Screens.Roles, onBack = { more = null })
                more == "notifications" -> ResourceList(api, Screens.Notifications, onBack = { more = null })
                more!!.startsWith("catalog:") -> {
                    val (_, key, label) = more!!.split(":", limit = 3)
                    ResourceList(api, Screens.catalog(key, label), onBack = { more = null })
                }
            }
            }
        }
    }
}
