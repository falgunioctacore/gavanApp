package com.example.gavanhomes.ui.shell

import com.example.gavanhomes.ui.auth.GavanLoginScreen
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.SwalConfirm
import com.example.gavanhomes.ui.common.cachedBranding
import com.example.gavanhomes.ui.common.loadBranding
import com.example.gavanhomes.ui.common.swipeBack
import com.example.gavanhomes.ui.dashboard.DashboardScreen
import com.example.gavanhomes.ui.projects.ProjectDetailScreen
import com.example.gavanhomes.ui.projects.ProjectFormScreen
import com.example.gavanhomes.ui.projects.ProjectsScreen
import com.example.gavanhomes.ui.users.UserDetailScreen
import com.example.gavanhomes.ui.users.UserFormScreen
import com.example.gavanhomes.ui.audit.AuditScreen
import com.example.gavanhomes.ui.common.LocalOpenNotifications
import com.example.gavanhomes.ui.common.NotificationCenter
import com.example.gavanhomes.ui.hod.HodScreen
import com.example.gavanhomes.ui.notifications.NotificationsScreen
import com.example.gavanhomes.ui.settings.AppSettingsScreen
import com.example.gavanhomes.ui.hr.HrFormScreen
import com.example.gavanhomes.ui.hr.HrRecordScreen
import com.example.gavanhomes.ui.hr.HrScreen
import com.example.gavanhomes.ui.professionals.ProfessionalDetailScreen
import com.example.gavanhomes.ui.professionals.ProfessionalFormScreen
import com.example.gavanhomes.ui.professionals.ProfessionalsScreen
import com.example.gavanhomes.ui.professionals.ReviewDetailScreen
import com.example.gavanhomes.ui.users.UsersScreen
import com.example.gavanhomes.ui.account.AccountScreen

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.Toast
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiConfig
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.SessionStore
import com.example.gavanhomes.data.User
import com.example.gavanhomes.data.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Gate {
    data object Checking : Gate                                         // looking at the saved token
    data object SignedOut : Gate                                        // native login (POST /login)
    data class Ready(val user: User) : Gate                             // signed in: native dashboard + website
    data class Failed(val message: String) : Gate                       // server unreachable
}

/**
 * Signed out → native login (API).  Signed in → native dashboard (GET /dashboard); its tiles open the
 * matching website page in a WebView that was signed in through POST /web-session. The website's own
 * "Dashboards" tab (or Back from its first page) returns to the native dashboard; its login page means
 * the session ended, so the token is cleared and the native login comes back.
 */
@Composable
fun AppGate() {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    var token by remember { mutableStateOf(store.token) }
    val api = remember { ApiClient { token } }
    var gate by remember { mutableStateOf<Gate>(if (token == null) Gate.SignedOut else Gate.Checking) }
    var branding by remember { mutableStateOf(cachedBranding(context)) }       // instant: the last branding seen
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { branding = loadBranding(api, context) }

    /** Straight in with the saved user (the dashboard call validates the token); only ask the server when nothing is saved. */
    suspend fun enter() {
        store.userJson?.let { json -> api.cachedUser(json) }?.let { gate = Gate.Ready(it); return }
        gate = Gate.Checking
        gate = try {
            withContext(Dispatchers.IO) { api.me() }.let { me -> store.userJson = me.toJson().toString(); Gate.Ready(me) }
        } catch (e: ApiException) {
            if (e.code == 401) { store.clear(); token = null; Gate.SignedOut } else Gate.Failed(e.message ?: "Server error")
        } catch (e: Exception) {
            Gate.Failed("Can't reach the server. Check that it is running and the phone is connected.")
        }
    }

    fun signOut() {
        store.clear(); token = null
        CookieManager.getInstance().removeAllCookies(null)
        gate = Gate.SignedOut
    }

    LaunchedEffect(attempt) { if (token != null) enter() }

    when (val g = gate) {
        Gate.SignedOut -> GavanLoginScreen(api) { newToken, user ->
            // the login response already carries the user: go straight to the dashboard, no extra calls
            store.token = newToken; store.userJson = user.toJson().toString(); token = newToken
            gate = Gate.Ready(user)
        }
        Gate.Checking -> Box(Modifier.fillMaxSize().background(Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = branding.base)
        }
        is Gate.Failed -> Box(Modifier.fillMaxSize().background(Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(g.message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { attempt++ }) { Text("Retry") }
            }
        }
        is Gate.Ready -> Home(api, g, branding, store, onSignedOut = ::signOut, onBranding = { branding = it })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Home(api: ApiClient, ready: Gate.Ready, branding: Branding, store: SessionStore, onSignedOut: () -> Unit, onBranding: (Branding) -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf(ready.user) }
    val modules = remember(user) { modulesFor(user) }
    var screen by remember { mutableStateOf("dashboard") }     // a module key, or "web" for a website page
    var returnTo by remember { mutableStateOf("dashboard") }   // where Back from a website page goes
    var homeVersion by remember { mutableIntStateOf(0) }       // bumped so the dashboard reloads when you return to it
    var projectsVersion by remember { mutableIntStateOf(0) }   // bumped after a project is saved so the list reloads
    var formUuid by remember { mutableStateOf<String?>(null) } // project being edited (null = new)
    var detailUuid by remember { mutableStateOf<String?>(null) } // project whose details are open
    var usersVersion by remember { mutableIntStateOf(0) }      // bumped after a user is saved / deleted so the list reloads
    var userId by remember { mutableStateOf<Int?>(null) }      // user whose details are open, or being edited (null on the form = new)
    var lastBack by remember { mutableStateOf(0L) }            // when Back was last pressed on the home dashboard (double-back to exit)
    var proSection by remember { mutableIntStateOf(0) }        // the Professionals sidebar section that is open
    var proVersion by remember { mutableIntStateOf(0) }        // bumped after a professional / review changes so the module reloads
    var proUuid by remember { mutableStateOf<String?>(null) }  // professional whose details are open, or being edited (null on the form = new)
    var reviewId by remember { mutableStateOf<Int?>(null) }    // submission being reviewed
    var auditSection by remember { mutableIntStateOf(0) }      // the Audit logs sidebar section that is open
    var hodSection by remember { mutableIntStateOf(0) }        // the HOD Sales sidebar section that is open
    var hrSection by remember { mutableIntStateOf(0) }         // the HR & Admin sidebar section that is open
    var hrVersion by remember { mutableIntStateOf(0) }         // bumped after an HR record changes so the module reloads
    var hrSlug by remember { mutableStateOf("employees") }     // the HR module whose record is open / being edited
    var hrId by remember { mutableStateOf<Int?>(null) }        // that record (null on the form = new)

    fun goHome() { screen = "dashboard"; homeVersion++ }

    var notifFrom by remember { mutableStateOf("dashboard") }   // the screen the bell was pressed on, so Back returns there
    val openNotifications: () -> Unit = { if (screen != "notifications") notifFrom = screen; screen = "notifications" }

    // the unread badge: starts from the last count, then is fetched on start, whenever the app comes back to the front, and every minute;
    // after a failed fetch (connection down) it tries again in a few seconds instead of waiting a minute
    remember(store) { NotificationCenter.unread = store.unread }
    var badgeTick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(context) {
        val owner = context as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) badgeTick++ }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(badgeTick) {
        while (true) {
            val ok = runCatching {
                val n = withContext(Dispatchers.IO) { api.get("/notifications") }.optInt("unread")
                NotificationCenter.unread = n; store.unread = n
            }.isSuccess
            kotlinx.coroutines.delay(if (ok) 60_000 else 8_000)
        }
    }

    var confirmLogout by remember { mutableStateOf(false) }
    val logout: () -> Unit = { confirmLogout = true }          // asks first; the real sign-out is below

    if (confirmLogout) {
        SwalConfirm(
            title = "Log out?",
            text = "You will need to sign in again to use the app.",
            confirmText = "Yes, log out",
            onCancel = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                // revoke the API token first (it is still the active one), then clear it locally
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { api.logout() } }
                    onSignedOut()
                }
            },
        )
    }

    // ── the website, created only when a website page is first opened ──────────────────────────────
    // The WebView is signed in through POST /web-session; nothing of this runs at login, so the dashboard
    // appears as soon as its own data is there. It is attached to the screen only while a page is showing.
    var webView by remember { mutableStateOf<WebView?>(null) }
    var opening by remember { mutableStateOf(false) }          // session link requested / first page still loading
    val holder = remember { arrayOfNulls<WebView>(1) }
    DisposableEffect(Unit) { onDispose { holder[0]?.destroy() } }

    fun buildWebView(sessionUrl: String, firstPath: String): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // locked down: the page can read no phone files, load no http content inside an https page, and Safe Browsing is on
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
        webChromeClient = WebChromeClient()
        var established = false                                  // the session link has been followed
        webViewClient = object : WebViewClient() {
            private fun path(url: String?) = Uri.parse(url ?: "").path?.trimEnd('/').orEmpty()

            private fun route(view: WebView, url: String?): Boolean = when (path(url)) {
                // the signed link redirects to the dashboard once the session cookie is set: go to the page we came for
                "/admin/dashboard" -> {
                    view.stopLoading()
                    if (!established) { established = true; view.loadUrl(ApiConfig.BASE_URL + firstPath) } else goHome()
                    true
                }
                "/admin/login" -> { view.stopLoading(); onSignedOut(); true }   // the session ended
                else -> false
            }

            private val ownHost = Uri.parse(ApiConfig.BASE_URL).host

            // the website stays inside the app; a link to anywhere else opens in the phone's browser instead
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host != ownHost) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    return true
                }
                return route(view, request.url.toString())
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (established) opening = false
                if (view.tag == "clear") { view.clearHistory(); view.tag = null }   // a fresh visit starts with no history
            }
        }
        loadUrl(sessionUrl)
    }

    fun openWeb(path: String) {
        if (screen != "web") returnTo = screen
        screen = "web"
        val existing = webView
        if (existing != null) {
            existing.tag = "clear"
            existing.loadUrl(ApiConfig.BASE_URL + path)
            return
        }
        opening = true
        scope.launch {
            try {
                val url = withContext(Dispatchers.IO) { api.webSession() }
                val wv = buildWebView(url, path)
                wv.tag = "clear"
                holder[0] = wv
                webView = wv
            } catch (e: Exception) {
                opening = false
                screen = returnTo
                Toast.makeText(context, e.message ?: "Could not open the page", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Native screen when the module has one, otherwise the website page. */
    fun openModule(m: Module) { if (m.native) screen = m.key else openWeb(m.path) }

    /** A website path from a tile: its native module if there is one, else the website page. */
    fun openPath(path: String) {
        val native = modules.firstOrNull { it.native && it.key != "dashboard" && path == it.path }
        if (native != null) screen = native.key else openWeb(path)
    }

    BackHandler(enabled = true) {
        val w = webView
        when {
            screen == "web" && w != null && w.canGoBack() -> w.goBack()
            screen == "web" -> { opening = false; screen = returnTo }
            screen == "projectForm" || screen == "projectDetail" -> screen = "projects"
            screen == "userForm" || screen == "userDetail" -> screen = "users"
            screen == "professionalForm" || screen == "professionalDetail" || screen == "reviewDetail" -> screen = "professionals"
            screen == "hrForm" || screen == "hrRecord" -> screen = "hr"
            screen == "appSettings" -> screen = "account"
            screen == "notifications" -> screen = notifFrom
            screen == "professionals" && proSection != 0 -> proSection = 0    // back from a section → that module's dashboard first
            screen == "hr" && hrSection != 0 -> hrSection = 0
            screen == "hod" && hodSection != 0 -> hodSection = 0
            screen == "audit" && auditSection != 0 -> auditSection = 0
            screen != "dashboard" -> goHome()
            System.currentTimeMillis() - lastBack < 2000 -> activity?.finish()
            else -> { lastBack = System.currentTimeMillis(); Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show() }
        }
    }

    // the list / module a details or form screen belongs to
    val base = when (screen) {
        "projectDetail", "projectForm" -> "projects"
        "userDetail", "userForm" -> "users"
        "professionalDetail", "professionalForm", "reviewDetail" -> "professionals"
        "hrRecord", "hrForm" -> "hr"
        "appSettings" -> "account"
        "notifications" -> when (notifFrom) { "projectDetail", "projectForm" -> "projects"; "userDetail", "userForm" -> "users"; "professionalDetail", "professionalForm", "reviewDetail" -> "professionals"; "hrRecord", "hrForm" -> "hr"; "appSettings" -> "account"; "notifications" -> "dashboard"; else -> notifFrom }
        else -> screen
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalOpenNotifications provides openNotifications) {
    if (screen == "web") {
        Box(Modifier.fillMaxSize().background(Color(0xFFF3F2F0))) {
            webView?.let { wv ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { _ ->
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        wv
                    },
                )
            }
            if (opening) Box(Modifier.fillMaxSize().background(Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = branding.base)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().swipeBack(enabled = screen != "dashboard" && (screen != base || (base != "hr" && base != "professionals" && base != "hod" && base != "audit")))) {   // the two sidebar modules keep left-to-right for their drawer
            Box(Modifier.weight(1f)) {
                // the list under a details / form screen stays composed (the overlay hides it), so Back returns to exactly
                // where you were: scroll position, filters, search and loaded data
                when (base) {
                    "projects" -> androidx.compose.runtime.key(projectsVersion) {
                        ProjectsScreen(
                            api, branding, onBack = ::goHome,
                            onOpenDetails = { uuid -> detailUuid = uuid; screen = "projectDetail" },
                            onOpenForm = { uuid -> formUuid = uuid; screen = "projectForm" },
                        )
                    }
                    "users" -> androidx.compose.runtime.key(usersVersion) {
                        UsersScreen(
                            api, branding, onBack = ::goHome,
                            onOpenUser = { id -> userId = id; screen = "userDetail" },
                            onAdd = { userId = null; screen = "userForm" },
                            onEditUser = { id -> userId = id; screen = "userForm" },
                        )
                    }
                    "professionals" -> androidx.compose.runtime.key(proVersion) {
                        ProfessionalsScreen(
                            api, branding, section = proSection, onSection = { proSection = it }, onBack = ::goHome,
                            onOpenPro = { uuid -> proUuid = uuid; screen = "professionalDetail" },
                            onAddPro = { proUuid = null; screen = "professionalForm" },
                            onEditPro = { uuid -> proUuid = uuid; screen = "professionalForm" },
                            onOpenReview = { id -> reviewId = id; screen = "reviewDetail" },
                        )
                    }
                    "hr" -> androidx.compose.runtime.key(hrVersion) {
                        HrScreen(
                            api, branding, section = hrSection, onSection = { hrSection = it }, onBack = ::goHome,
                            onOpen = { slug, id -> hrSlug = slug; hrId = id; screen = "hrRecord" },
                            onAdd = { slug -> hrSlug = slug; hrId = null; screen = "hrForm" },
                            onEdit = { slug, id -> hrSlug = slug; hrId = id; screen = "hrForm" },
                        )
                    }
                    "audit" -> AuditScreen(api, branding, section = auditSection, onSection = { auditSection = it }, onBack = ::goHome)
                    "hod" -> HodScreen(api, branding, section = hodSection, onSection = { hodSection = it }, onBack = ::goHome)
                    "account" -> AccountScreen(api, user, branding, onUser = { user = it; store.userJson = it.toJson().toString() }, onBack = ::goHome, onLogout = logout,
                        onAppSettings = if (user.roles.any { it.equals("superAdmin", true) }) ({ screen = "appSettings" }) else null)
                    else -> androidx.compose.runtime.key(homeVersion) {
                        DashboardScreen(
                            api, user, branding, modules, onOpenModule = ::openModule, onOpenPath = ::openPath, onLogout = logout,
                            cachedJson = store.dashboardJson, onCache = { store.dashboardJson = it }, onUnauthorized = onSignedOut,
                        )
                    }
                }

                // details / form screens sit on top of their list
                if (screen != base) Box(
                    Modifier.fillMaxSize().background(Color(0xFFF3F2F0)).pointerInput(Unit) { detectTapGestures { } },   // taps never reach the list below
                ) {
                    when (screen) {
                        "notifications" -> NotificationsScreen(api, branding, onBack = { screen = notifFrom })
                        "projectDetail" -> ProjectDetailScreen(
                            api, branding, uuid = detailUuid.orEmpty(), onBack = { screen = "projects" },
                            onEdit = { uuid -> formUuid = uuid; screen = "projectForm" },
                        )
                        "projectForm" -> ProjectFormScreen(
                            api, branding, uuid = formUuid, onBack = { screen = "projects" },
                            onSaved = { projectsVersion++; screen = "projects" },
                        )
                        "userDetail" -> androidx.compose.runtime.key(usersVersion) {
                            UserDetailScreen(
                                api, branding, id = userId ?: 0, onBack = { screen = "users" },
                                onEdit = { id -> userId = id; screen = "userForm" },
                                onDeleted = { usersVersion++; screen = "users" },
                            )
                        }
                        "userForm" -> UserFormScreen(
                            api, branding, id = userId, onBack = { screen = "users" },
                            onSaved = { usersVersion++; screen = "users" },
                        )
                        "professionalDetail" -> ProfessionalDetailScreen(
                            api, branding, uuid = proUuid.orEmpty(), onBack = { screen = "professionals" },
                            onEdit = { uuid -> proUuid = uuid; screen = "professionalForm" },
                            onDeleted = { proVersion++; screen = "professionals" },
                        )
                        "professionalForm" -> ProfessionalFormScreen(
                            api, branding, uuid = proUuid, onBack = { screen = "professionals" },
                            onSaved = { proVersion++; proSection = 1; screen = "professionals" },
                        )
                        "reviewDetail" -> ReviewDetailScreen(
                            api, branding, id = reviewId ?: 0, onBack = { screen = "professionals" },
                            onDone = { proVersion++; screen = "professionals" },
                        )
                        "hrRecord" -> HrRecordScreen(
                            api, branding, slug = hrSlug, id = hrId ?: 0, onBack = { screen = "hr" },
                            onEdit = { slug, id -> hrSlug = slug; hrId = id; screen = "hrForm" },
                            onChanged = { hrVersion++ },
                        )
                        "appSettings" -> AppSettingsScreen(api, branding, onBack = { screen = "account" }, onSaved = { onBranding(it); screen = "account" })
                        "hrForm" -> HrFormScreen(
                            api, branding, slug = hrSlug, id = hrId, onBack = { screen = "hr" },
                            onSaved = { hrVersion++; screen = "hr" },
                        )
                    }
                }
            }
            if (screen != "projectForm" && screen != "userForm" && screen != "professionalForm" && screen != "hrForm" && screen != "appSettings") {   // the forms have their own Save bar
                ModuleBar(modules, base, accent = branding.base, onLogout = logout, onSelect = ::openModule)
            }
        }
    }
    }
}
