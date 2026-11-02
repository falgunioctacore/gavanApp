package com.example.gavanhomes.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.Net
import kotlinx.coroutines.delay

/** True when an error message means "the request never got an answer" (no internet, unreachable, too slow). */
fun isConnectionError(message: String?): Boolean {
    val m = message.orEmpty().lowercase()
    return m.startsWith("no internet") || m.startsWith("can't reach") || m.startsWith("the server is taking too long") || "unable to resolve host" in m
}

/** A wifi symbol with a slash through it. */
@Composable
fun WifiOffIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(width = w * .07f, cap = StrokeCap.Round)
        for (i in 1..3) {
            val r = w * (.14f + i * .13f)
            drawArc(color, 225f, 90f, false, Offset(w / 2 - r, w * .72f - r), Size(r * 2, r * 2), style = stroke)
        }
        drawCircle(color, w * .055f, Offset(w / 2, w * .72f))
        drawLine(color, Offset(w * .16f, w * .14f), Offset(w * .84f, w * .86f), w * .08f, StrokeCap.Round)
    }
}

/**
 * The full "No internet connection" screen: a big wifi-off sign, what to do, and **Try again** (which shows a spinner while it tries).
 * When the connection returns the caller retries by itself, so the screen usually just goes away.
 */
@Composable
fun OfflineView(message: String?, color: Color, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (busy) { delay(1400); busy = false } }
    val slow = message.orEmpty().lowercase().let { it.startsWith("the server is taking") }
    val unreachable = message.orEmpty().lowercase().startsWith("can't reach")
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp).widthIn(max = 380.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(116.dp).clip(CircleShape).background(color.copy(alpha = .09f)))
            Box(Modifier.size(84.dp).clip(CircleShape).background(color.copy(alpha = .12f)))
            WifiOffIcon(color, Modifier.size(46.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(if (slow) "This is taking too long" else if (unreachable) "Can't reach the server" else "No internet connection", color = Color(0xFF1F2A44), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (slow) "The server is busy right now. Give it a moment, then try again." else if (unreachable) "Check your connection, or that the server is running." else "Check your Wi-Fi or mobile data. We'll try again by ourselves the moment you're back online.",
            color = Color(0xFF8A8F9C), fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.clip(RoundedCornerShape(18.dp)).background(color).clickable(enabled = !busy) { busy = true; onRetry() }.padding(horizontal = 30.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Text(if (busy) "Trying…" else "Try again", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

/** A slim bar at the top while the phone is offline ("No internet connection"), then a green "Back online" for a moment. */
@Composable
fun ConnectionBanner(modifier: Modifier = Modifier) {
    var back by remember { mutableStateOf(false) }
    var wasOffline by remember { mutableStateOf(false) }
    val online = Net.online
    LaunchedEffect(online) {
        if (!online) { wasOffline = true; back = false }
        else if (wasOffline) { back = true; delay(2200); back = false; wasOffline = false }
    }
    AnimatedVisibility(
        visible = !online || back, modifier = modifier,
        enter = expandVertically(tween(260)) + fadeIn(tween(260)), exit = shrinkVertically(tween(260)) + fadeOut(tween(200)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(if (online) Color(0xFF0E8A50) else Color(0xFF3B3F4A)).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            if (!online) { WifiOffIcon(Color.White, Modifier.size(16.dp)); Spacer(Modifier.size(8.dp)) }
            Text(if (online) "Back online" else "No internet connection", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
