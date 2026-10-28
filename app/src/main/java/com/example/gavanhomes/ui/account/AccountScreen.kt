package com.example.gavanhomes.ui.account

import com.example.gavanhomes.ui.common.*

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native copy of the website's Account page: profile card, edit form (PUT /profile), change password
 * (PUT /profile/password) and log out. Roles are listed read-only.
 */
@Composable
fun AccountScreen(api: ApiClient, user: User, b: Branding, onUser: (User) -> Unit, onBack: () -> Unit, onLogout: () -> Unit, onAppSettings: (() -> Unit)? = null) {
    val words = user.name.trim().split(Regex("\\s+"))
    var first by remember { mutableStateOf(words.firstOrNull().orEmpty()) }
    var last by remember { mutableStateOf(words.drop(1).joinToString(" ")) }
    var email by remember { mutableStateOf(user.email) }
    var phone by remember { mutableStateOf(user.phone.orEmpty()) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }

    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var passMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(KitPage)) {
        HeroBar(b, "Account", onBack) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(user.name, 64.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(user.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(user.email, color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                    if (user.roles.isNotEmpty()) Text(user.roles.joinToString(" · "), color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                }
            }
        }

        Column(
            Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Profile") {
                Field(first, { first = it }, "First name")
                Field(last, { last = it }, "Last name")
                Field(email, { email = it }, "Email")
                Field(phone, { phone = it }, "Phone")
                message?.let { (ok, text) -> Text(text, color = if (ok) KitGood else KitDanger, fontSize = 13.sp) }
                PrimaryButton(if (busy) "Saving…" else "Save changes", b, enabled = !busy && first.isNotBlank() && last.isNotBlank() && email.isNotBlank()) {
                    busy = true; message = null
                    scope.launch {
                        try {
                            val updated = withContext(Dispatchers.IO) {
                                api.updateProfile(mapOf("first_name" to first.trim(), "last_name" to last.trim(), "email" to email.trim(), "phone_number" to phone.trim()))
                            }
                            onUser(updated); message = true to "Saved"
                        } catch (e: Exception) { message = false to (e.message ?: "Could not save") } finally { busy = false }
                    }
                }
            }

            Section("Change password") {
                Field(current, { current = it }, "Current password", password = true)
                Field(newPass, { newPass = it }, "New password (min 8)", password = true)
                passMessage?.let { (ok, text) -> Text(text, color = if (ok) KitGood else KitDanger, fontSize = 13.sp) }
                PrimaryButton("Update password", b, enabled = current.isNotBlank() && newPass.length >= 8) {
                    passMessage = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { api.changePassword(current, newPass) }
                            passMessage = true to "Password updated"; current = ""; newPass = ""
                        } catch (e: Exception) { passMessage = false to (e.message ?: "Could not update the password") }
                    }
                }
            }

            if (onAppSettings != null) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable { onAppSettings() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.example.gavanhomes.ui.common.BrandMark(b, 44.dp, Modifier.border(1.dp, Color(0xFFE4E1DB), RoundedCornerShape(13.dp)))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("App settings", fontWeight = FontWeight.ExtraBold, color = KitInk, fontSize = 15.sp)
                    Text("Logo, name and theme colour", color = KitMuted, fontSize = 12.sp)
                }
                Text("›", color = KitMuted, fontSize = 22.sp)
            }

            Text(
                "Log out", color = KitDanger, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFFEF2F2)).clickable { onLogout() }.padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KitInk)
        content()
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(14.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = KitInk, unfocusedTextColor = KitInk, cursorColor = KitInk,
            focusedLabelColor = KitMuted, unfocusedLabelColor = KitMuted,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrimaryButton(text: String, b: Branding, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(if (enabled) b.base else Color(0xFFCFCFCF), if (enabled) b.deep else Color(0xFFB5B5B5))))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}
