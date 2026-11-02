package com.example.gavanhomes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.example.gavanhomes.data.SessionStore
import com.example.gavanhomes.ui.common.AppTheme
import com.example.gavanhomes.ui.discussion.ChatAlerts
import com.example.gavanhomes.ui.discussion.ChatLink
import com.example.gavanhomes.ui.shell.AppGate
import com.example.gavanhomes.ui.theme.GavanHomesTheme

/**
 * Native login (API) → the app. The native splash (the logo on the brand background, res/values/splash.xml) shows while the
 * app starts; the saved light / dark choice applies from the first frame. See ui/shell/AppGate.kt.
 */
class MainActivity : ComponentActivity() {
    /** A tapped chat notification opens that chat; a tapped update notification opens the notifications screen. */
    private fun openChatFrom(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("open_notifications", false)) ChatAlerts.openNotifications = true
        val project = intent.getStringExtra("chat_project")
        val conv = intent.getStringExtra("chat_conv")
        if (!project.isNullOrBlank() && !conv.isNullOrBlank()) ChatAlerts.pending = ChatLink(project, conv, intent.getStringExtra("chat_title") ?: "Chat")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openChatFrom(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppTheme.mode = SessionStore(this).theme
        openChatFrom(intent)
        setContent {
            AppTheme.systemDark = isSystemInDarkTheme()
            val dark = AppTheme.dark
            // the navigation bar icons: light on the dark theme, dark on the light one
            SideEffect { WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !dark }
            GavanHomesTheme(darkTheme = dark, dynamicColor = false) {
                AppGate()
            }
        }
    }
}
