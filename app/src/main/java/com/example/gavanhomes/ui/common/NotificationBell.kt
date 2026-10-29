package com.example.gavanhomes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
