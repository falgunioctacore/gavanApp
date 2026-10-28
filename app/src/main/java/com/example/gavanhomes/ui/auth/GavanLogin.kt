package com.example.gavanhomes.ui.auth

import com.example.gavanhomes.ui.common.*

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.ApiConfig
import com.example.gavanhomes.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Calendar

/**
 * Native copy of the website's mobile login (resources/views/content/admin_mobile/auth/login.blade.php):
 * brand-gradient hero with logo, a white card that overlaps it, icon fields, gradient button.
 * Signs in with POST /api/mobile/admin/login.
 */
@Composable
fun GavanLoginScreen(api: ApiClient, onSignedIn: (String, User) -> Unit) {
    var branding by remember { mutableStateOf(Branding()) }
    LaunchedEffect(Unit) { branding = loadBranding(api) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || email.isBlank() || password.isBlank()) return
        busy = true; error = null
        scope.launch {
            try {
                val (token, user) = withContext(Dispatchers.IO) { api.login(email.trim(), password, android.os.Build.MODEL) }
                onSignedIn(token, user)
            } catch (e: Exception) {
                error = e.message ?: "Could not sign in"
                busy = false
            }
        }
    }

    val pageBg = Color(0xFFF3F2F0)
    val ink = Color(0xFF1F2A44)
    val muted = Color(0xFF8A8F9C)

    Column(Modifier.fillMaxSize().background(pageBg).verticalScroll(rememberScrollState())) {
        // ── hero ──
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.linearGradient(listOf(branding.deep, branding.base), start = Offset(0f, 0f), end = Offset(900f, 1700f))),
        ) {
            Box(Modifier.size(240.dp).offset(x = 200.dp, y = (-70).dp).clip(CircleShape).background(Color.White.copy(alpha = .08f)))
            Box(Modifier.size(140.dp).offset(x = (-50).dp, y = 130.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)))
            Column(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 76.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(84.dp).shadow(14.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    branding.logo?.let { Image(it, contentDescription = branding.name, modifier = Modifier.size(66.dp), contentScale = ContentScale.Fit) }
                }
                Spacer(Modifier.height(18.dp))
                Text("Welcome back", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Sign in to the ${branding.name} admin app.", color = Color.White.copy(alpha = .85f), fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }

        // ── card (overlaps the hero) ──
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-48).dp), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth()
                    .shadow(20.dp, RoundedCornerShape(26.dp), ambientColor = branding.base, spotColor = branding.base)
                    .clip(RoundedCornerShape(26.dp)).background(Color.White)
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 22.dp),
            ) {
                error?.let {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 18.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFDECEC)).padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("⚠", color = Color(0xFFB42323), fontSize = 16.sp)
                        Spacer(Modifier.size(8.dp))
                        Text(it, color = Color(0xFFB42323), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                FieldLabel("EMAIL", muted)
                IconField(
                    value = email, onValueChange = { email = it }, placeholder = "you@company.com", glyph = "✉",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), brand = branding.base, pageBg = pageBg,
                )
                Spacer(Modifier.height(18.dp))
                FieldLabel("PASSWORD", muted)
                IconField(
                    value = password, onValueChange = { password = it }, placeholder = "Enter your password", glyph = "🔒",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = { Text(if (showPassword) "Hide" else "Show", color = muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { showPassword = !showPassword }.padding(12.dp)) },
                    onDone = ::submit, brand = branding.base, pageBg = pageBg,
                )

                Spacer(Modifier.height(26.dp))
                Box(
                    Modifier.fillMaxWidth().height(56.dp)
                        .shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = branding.base, spotColor = branding.base)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(branding.base, branding.deep)))
                        .clickable(enabled = !busy) { submit() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (busy) "Signing in…" else "Sign in  →", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "© ${Calendar.getInstance().get(Calendar.YEAR)} ${branding.name}", color = muted, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp),
        )
    }
}

@Composable
private fun FieldLabel(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, modifier = Modifier.padding(start = 2.dp, bottom = 7.dp))
}

@Composable
private fun IconField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    glyph: String,
    keyboard: KeyboardOptions,
    brand: Color,
    pageBg: Color,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(pageBg)
            .border(1.5.dp, Color(0xFFE6E3DE), RoundedCornerShape(16.dp)).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, fontSize = 17.sp, color = Color(0xFF8A8F9C))
        TextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            placeholder = { Text(placeholder, color = Color(0xFF8A8F9C).copy(alpha = .7f)) },
            visualTransformation = visual, keyboardOptions = keyboard,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = brand,
                focusedTextColor = Color(0xFF1F2A44), unfocusedTextColor = Color(0xFF1F2A44),
            ),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
