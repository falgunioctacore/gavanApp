package com.example.gavanhomes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.gavanhomes.ui.AppGate
import com.example.gavanhomes.ui.theme.GavanHomesTheme

/**
 * Native login (API) → the website in a WebView, opened already signed in. See ui/AppGate.kt.
 * The other native screens (ui/AppRoot.kt, ui/Screens.kt) are kept in the project but not used.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GavanHomesTheme(dynamicColor = false) {
                AppGate()
            }
        }
    }
}
