package com.example.gavanhomes.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiException
import com.example.gavanhomes.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Gate {
    data object Checking : Gate                       // looking at the saved token
    data object SignedOut : Gate                      // native login (POST /login)
    data class Website(val url: String) : Gate        // website, opened with a single-use signed link
    data class Failed(val message: String) : Gate     // server unreachable
}

/**
 * Signed out → the native login (API). Signed in → the website in a WebView, opened already signed in
 * through POST /web-session. If the website ever lands on its own login page (logged out / expired),
 * the token is cleared and the native login comes back.
 */
@Composable
fun AppGate() {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    var token by remember { mutableStateOf(store.token) }
    val api = remember { ApiClient { token } }
    var gate by remember { mutableStateOf<Gate>(if (token == null) Gate.SignedOut else Gate.Checking) }
    var attempt by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun openWebsite() {
        gate = Gate.Checking
        gate = try {
            Gate.Website(withContext(Dispatchers.IO) { api.webSession() })
        } catch (e: ApiException) {
            if (e.code == 401) { store.clear(); token = null; Gate.SignedOut } else Gate.Failed(e.message ?: "Server error")
        } catch (e: Exception) {
            Gate.Failed("Can't reach the server. Check that it is running and the phone is connected.")
        }
    }

    // saved token → straight into the website
    LaunchedEffect(attempt) { if (token != null) openWebsite() }

    fun signOut() { store.clear(); token = null; gate = Gate.SignedOut }

    when (val g = gate) {
        Gate.SignedOut -> GavanLoginScreen(api) { newToken, _ ->
            store.token = newToken; token = newToken
            scope.launch { openWebsite() }
        }
        Gate.Checking -> Box(Modifier.fillMaxSize().background(Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Gate.Failed -> Box(Modifier.fillMaxSize().background(Color(0xFFF3F2F0)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(g.message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { attempt++ }) { Text("Retry") }
            }
        }
        is Gate.Website -> WebsiteScreen(g.url, onLoggedOut = ::signOut)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebsiteScreen(startUrl: String, onLoggedOut: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? android.app.Activity

    BackHandler(enabled = true) {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else activity?.finish()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    private fun isLogin(url: String?) = Uri.parse(url ?: "").path?.trimEnd('/') == "/admin/login"

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (isLogin(request.url.toString())) { onLoggedOut(); return true }
                        return false
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        canGoBack = view.canGoBack()
                        if (isLogin(url)) onLoggedOut()
                    }
                }
                loadUrl(startUrl)
                webView = this
            }
        },
    )
}
