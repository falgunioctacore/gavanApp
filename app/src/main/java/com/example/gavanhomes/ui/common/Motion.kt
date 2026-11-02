package com.example.gavanhomes.ui.common

import android.os.SystemClock
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin

// ───────────────────────────── motion: a real-estate feel — things rise like floors going up, towers light their windows ─────────────────────────────

/** Items composed together arrive one after another, like floors going up (the counter restarts after a short pause). */
private object Rise {
    private var last = 0L
    private var n = 0
    fun next(): Int { val now = SystemClock.uptimeMillis(); if (now - last > 250) n = 0; last = now; return n++ }
}

/**
 * Fades in and rises a little the first time it is shown; neighbours follow in sequence. Only the first ten items of a burst animate — a long list
 * scrolled fast just appears, so scrolling never feels slow.
 */
fun Modifier.appear(): Modifier = composed {
    val order = remember { Rise.next() }
    val a = remember { Animatable(if (order < 10) 0f else 1f) }
    LaunchedEffect(Unit) { if (a.value < 1f) { delay(order.coerceAtMost(8) * 55L); a.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) } }
    graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 34.dp.toPx() }
}

/** A click that presses in: the item shrinks a touch while held and springs back. */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) .96f else 1f, spring(dampingRatio = .5f, stiffness = 520f), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = src, indication = null, enabled = enabled) { onClick() }
}

/** "12" counts up from 0, "33%" too; anything else is shown as it is. */
@Composable
fun countUp(value: String): String {
    val m = remember(value) { Regex("^(\\D*)(\\d+)(\\D*)$").matchEntire(value) }
    val target = m?.groupValues?.get(2)?.toIntOrNull() ?: 0
    var go by remember(value) { mutableStateOf(false) }
    LaunchedEffect(value) { go = true }
    val n by animateIntAsState(if (go) target else 0, tween(900, easing = FastOutSlowInEasing), label = "count")
    return if (m == null) value else m.groupValues[1] + n + m.groupValues[3]
}

/** A bar that fills from empty to [fraction] — like a progress report on site. */
@Composable
fun animatedFraction(fraction: Float): Float {
    var go by remember(fraction) { mutableStateOf(false) }
    LaunchedEffect(fraction) { go = true }
    val f by animateFloatAsState(if (go) fraction else 0f, tween(900, easing = FastOutSlowInEasing), label = "fill")
    return f
}

/** A slow back-and-forth 0..1 for drifting shapes. */
@Composable
fun rememberDrift(): State<Float> {
    val t = rememberInfiniteTransition(label = "drift")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
}

/** A gentle 1.0 ↔ 1.18 heartbeat for badges. */
@Composable
fun rememberPulse(): State<Float> {
    val t = rememberInfiniteTransition(label = "pulse")
    return t.animateFloat(1f, 1.18f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
}

private val Towers = listOf(.42f to 3, .78f to 4, .58f to 3, 1f to 5, .66f to 3, .48f to 4, .9f to 4, .38f to 3)   // height · window columns

/** A faint city skyline whose windows light up and dim slowly — the hero's watermark. */
@Composable
fun Skyline(modifier: Modifier = Modifier, tint: Color = Color.White, alpha: Float = .12f) {
    val t = rememberInfiniteTransition(label = "sky")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (Towers.size - 1)) / Towers.size
        Towers.forEachIndexed { i, (hf, cols) ->
            val h = size.height * hf
            val x = i * (w + gap)
            val top = size.height - h
            drawRect(tint.copy(alpha = alpha), Offset(x, top), Size(w, h))
            val cell = w / (cols * 2f + 1)
            val rows = (h / (cell * 2.2f)).toInt() - 1
            for (r in 0 until rows) for (c in 0 until cols) {
                val on = (sin(phase + i * 1.7f + r * .9f + c * 2.3f) + 1f) / 2f
                drawRect(tint.copy(alpha = alpha * (.5f + on * 2.2f)), Offset(x + cell * (1 + c * 2), top + cell * 1.2f + r * cell * 2.2f), Size(cell, cell * 1.3f))
            }
        }
    }
}

/** Opening a screen slides it in from the right over a fade; closing slides it back out. */
fun <S> AnimatedContentTransitionScope<S>.overlayTransition(closing: Boolean): ContentTransform =
    if (closing) (fadeIn(tween(1)) togetherWith (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(220)))).apply { targetContentZIndex = -1f }
    else (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(280))) togetherWith fadeOut(tween(180))

/** Switching tabs fades one page into the next. */
fun <S> AnimatedContentTransitionScope<S>.tabTransition(): ContentTransform = fadeIn(tween(260)) togetherWith fadeOut(tween(120))

/**
 * The opening scene: on the brand colours seven towers rise one after another, their windows light up, the name fades in — then the whole scene
 * fades away to the app. Touches are blocked while it shows.
 */
@Composable
fun IntroOverlay(deep: Color, base: Color, title: String, holdMs: Long = 1700) {
    var show by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(if (show) 1f else 0f, tween(500), label = "intro")
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1200, easing = LinearEasing)) }
    LaunchedEffect(Unit) { delay(holdMs); show = false }
    if (alpha > 0f) Box(
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }.background(Brush.linearGradient(listOf(deep, base), start = Offset.Zero, end = Offset(1000f, 1900f))).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(168.dp, 124.dp)) {
                val n = 7
                val gap = 6.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                val hs = listOf(.5f, .78f, .62f, 1f, .72f, .56f, .84f)
                for (i in 0 until n) {
                    val rise = FastOutSlowInEasing.transform(((p.value - i * .09f) / .45f).coerceIn(0f, 1f))
                    val h = size.height * hs[i] * rise
                    val x = i * (w + gap)
                    drawRoundRect(Color.White.copy(alpha = .94f), Offset(x, size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
                    val rows = (h / (w * .95f)).toInt() - 1
                    for (r in 0 until rows) drawRect(base.copy(alpha = .85f * rise), Offset(x + w * .28f, size.height - h + w * .35f + r * w * .95f), Size(w * .44f, w * .42f))
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.graphicsLayer { val k = ((p.value - .55f) / .4f).coerceIn(0f, 1f); this.alpha = k; translationY = (1f - k) * 16.dp.toPx() })
        }
    }
}
