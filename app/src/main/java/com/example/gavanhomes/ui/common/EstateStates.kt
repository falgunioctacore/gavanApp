package com.example.gavanhomes.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Steps = listOf("Laying the foundation…", "Building the floors…", "Adding the finishing touches…")

/**
 * The app's loader: a small building going up — the ground, then each floor rising with its windows lighting, then the roof —
 * and over again, with a caption for the stage it is at. [caption] = false for a quiet loader (no words).
 */
@Composable
fun EstateLoader(color: Color, modifier: Modifier = Modifier, size: Dp = 64.dp, caption: Boolean = true) {
    val t = rememberInfiniteTransition(label = "estate")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "build")
    Column(modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(size)) { drawBuilding(color, p) }
        if (caption) {
            Spacer(Modifier.height(8.dp))
            Text(Steps[(p * Steps.size).toInt().coerceIn(0, Steps.lastIndex)], color = KitMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Three floors and a roof, built to [p] (0 → 1): the ground at once, floors one by one, the roof last. */
private fun DrawScope.drawBuilding(color: Color, p: Float) {
    val w = size.width; val h = size.height
    val left = w * .2f; val right = w * .8f; val ground = h * .92f
    val floors = 3; val floorH = h * .17f
    val stroke = w * .045f
    // ground
    drawLine(color.copy(alpha = .35f), Offset(w * .06f, ground), Offset(w * .94f, ground), stroke, StrokeCap.Round)
    // floors rise in turn over the first 75% of the loop
    for (i in 0 until floors) {
        val start = i * .25f
        val grow = ((p - start) / .25f).coerceIn(0f, 1f)
        if (grow <= 0f) continue
        val bottom = ground - i * floorH
        val top = bottom - floorH * grow
        drawRoundRect(color.copy(alpha = .16f + .06f * i), Offset(left, top), Size(right - left, bottom - top), CornerRadius(w * .02f))
        drawRoundRect(color, Offset(left, top), Size(right - left, bottom - top), CornerRadius(w * .02f), style = Stroke(stroke * .7f))
        // windows light up once the floor is complete
        if (grow >= 1f) {
            val win = floorH * .38f
            val lit = ((p - start - .25f) / .1f).coerceIn(0f, 1f)
            listOf(.32f, .5f, .68f).forEach { cx ->
                drawRoundRect(
                    if (lit > 0f) Color(0xFFF2B33D).copy(alpha = .4f + .6f * lit) else color.copy(alpha = .3f),
                    Offset(w * cx - win / 2, bottom - floorH * .5f - win / 2), Size(win, win), CornerRadius(win * .2f),
                )
            }
        }
    }
    // the roof, in the last quarter
    val roof = ((p - .75f) / .2f).coerceIn(0f, 1f)
    if (roof > 0f) {
        val base = ground - floors * floorH
        val peak = base - h * .2f * roof
        val path = Path().apply { moveTo(left - w * .06f, base); lineTo(w / 2, peak); lineTo(right + w * .06f, base); close() }
        drawPath(path, color.copy(alpha = .9f))
    }
}

/** What went wrong, read from the message, with its sign and a heading in plain words. */
private class Trouble(val sign: String, val title: String, val hint: String)

private fun troubleFor(message: String?): Trouble {
    val m = message.orEmpty().lowercase()
    return when {
        "too long" in m || "timeout" in m || "timed out" in m -> Trouble("◷", "This is taking too long", "The server is busy. Give it a moment and try again.")
        "reach" in m || "connect" in m || "resolve host" in m || "network" in m || "failed to" in m -> Trouble("⌁", "Can't reach the server", "Check your internet connection, or that the server is running.")
        "403" in m || "permission" in m || "not allowed" in m || "forbidden" in m || "only " in m || "unauthor" in m -> Trouble("🔒", "You don't have access", "Ask an admin to give you permission for this.")
        "404" in m || "not found" in m || "no query results" in m -> Trouble("?", "Not found", "It may have been moved or deleted.")
        else -> Trouble("!", "Something went wrong", "Please try again. If it keeps happening, let the admin know.")
    }
}

/**
 * The app's error screen: a house with a sign for the trouble (no connection, too slow, no access, not found, anything else), a
 * heading in plain words, what the server said, and Try again.
 */
@Composable
fun EstateError(message: String?, color: Color = KitInk, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val tr = troubleFor(message)
    Column(modifier.padding(horizontal = 28.dp, vertical = 24.dp).widthIn(max = 360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(104.dp).clip(CircleShape).background(color.copy(alpha = .08f)))
            Canvas(Modifier.size(64.dp)) { drawHouse(color) }
            Box(Modifier.align(Alignment.BottomEnd).size(38.dp).clip(CircleShape).background(KitCard).padding(3.dp).clip(CircleShape).background(if (tr.sign == "🔒") Color(0xFF8A6535) else KitDanger), contentAlignment = Alignment.Center) {
                Text(tr.sign, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(tr.title, color = KitInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(tr.hint, color = KitMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        message?.takeIf { it.isNotBlank() && !tr.hint.equals(it, true) }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = KitMuted.copy(alpha = .8f), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 3)
        }
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Text("↻  Try again", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(color).clickable { onRetry() }.padding(horizontal = 26.dp, vertical = 12.dp))
        }
    }
}

/** A plain house: roof, walls, a door and a window. */
private fun DrawScope.drawHouse(color: Color) {
    val w = size.width; val h = size.height; val s = w * .06f
    val roof = Path().apply { moveTo(w * .08f, h * .48f); lineTo(w * .5f, h * .1f); lineTo(w * .92f, h * .48f) }
    drawPath(roof, color, style = Stroke(s, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawRoundRect(color, Offset(w * .2f, h * .44f), Size(w * .6f, h * .46f), CornerRadius(w * .04f), style = Stroke(s))
    drawRoundRect(color.copy(alpha = .85f), Offset(w * .42f, h * .62f), Size(w * .16f, h * .28f), CornerRadius(w * .03f))
    drawRoundRect(color.copy(alpha = .4f), Offset(w * .27f, h * .54f), Size(w * .1f, w * .1f), CornerRadius(w * .02f))
}
