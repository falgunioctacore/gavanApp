package com.example.gavanhomes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.example.gavanhomes.data.SessionStore
import com.example.gavanhomes.ui.common.AppTheme
import com.example.gavanhomes.ui.shell.AppGate
import com.example.gavanhomes.ui.theme.GavanHomesTheme

/**
 * Native login (API) → the app. The native splash (the logo on the brand background, res/values/splash.xml) shows while the
 * app starts; the saved light / dark choice applies from the first frame. See ui/shell/AppGate.kt.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppTheme.mode = SessionStore(this).theme
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
