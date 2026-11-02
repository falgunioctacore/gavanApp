package com.example.gavanhomes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The signed-in user's unread notification count — one value for the whole app, so every top bar's bell shows the same badge. */
object NotificationCenter {
    var unread by mutableIntStateOf(0)
}

/** Opens the notifications screen; provided once by the app shell, used by every top bar's bell. */
val LocalOpenNotifications = compositionLocalOf<(() -> Unit)?> { null }

/** The top-bar bell: a round glass button (like the other header buttons) with a red unread badge. Draws nothing outside the app shell. */
@Composable
fun NotificationBell() {
    val open = LocalOpenNotifications.current ?: return
    val n = NotificationCenter.unread
    Box {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)).clickable { open() },
            contentAlignment = Alignment.Center,
        ) { Text("🔔", fontSize = 17.sp) }
        if (n > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-3).dp).size(width = if (n > 9) 22.dp else 18.dp, height = 18.dp)
                .clip(CircleShape).background(KitDanger).border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(if (n > 99) "99+" else n.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

/** The top-bar chat button (next to the bell): a speech bubble with the number of unread chat messages; tapping it opens the chat picker. */
@Composable
fun ChatBell() {
    if (LocalOpenNotifications.current == null) return
    val n = ChatCenter.total
    Box {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)).clickable { ChatCenter.picker = true },
            contentAlignment = Alignment.Center,
        ) { Text("💬", fontSize = 17.sp) }
        if (n > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-3).dp).size(width = if (n > 9) 22.dp else 18.dp, height = 18.dp)
                .clip(CircleShape).background(KitDanger).border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(if (n > 99) "99+" else n.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

/** The sheet the chat button opens: the chats with unread messages (tap one to open it) and a way to all chats. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatPickerSheet(b: Branding, onAll: () -> Unit) {
    if (!ChatCenter.picker) return
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = { ChatCenter.picker = false }, containerColor = KitCard) {
        androidx.compose.foundation.layout.Column(
            Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Text("Chats", color = KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            val chats = ChatCenter.chats
            if (chats.isEmpty()) Text("No unread messages.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            chats.forEach { c ->
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(KitSoft).clickable { ChatCenter.open(c) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(c.optString("name"), color = KitInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        Text(c.optString("project_name"), color = KitMuted, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(c.optInt("unread").toString(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.clip(CircleShape).background(KitDanger).padding(horizontal = 9.dp, vertical = 4.dp))
                }
            }
            Text(
                "All chats", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(b.base).clickable { ChatCenter.picker = false; onAll() }.padding(vertical = 13.dp),
            )
        }
    }
}

/** The top-bar log-out button: icon only — a round glass button with a drawn power symbol. The screen decides what happens on tap (it asks first). */
@Composable
fun LogoutIconButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
            val w = 2.2.dp.toPx()
            val inset = w / 2
            // the open ring (gap at the top)…
            drawArc(
                Color.White, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset + 1.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w - 1.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            // …and the stem through the gap
            drawLine(Color.White, androidx.compose.ui.geometry.Offset(size.width / 2, 0f), androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.5f), strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
