package com.example.gavanhomes.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Toast alerts inside the app: a rounded card that slides down from the top right — green with a tick for success, red with a cross for an error,
 * dark for plain information — and goes away by itself (tap to dismiss). Every "Saved", "Deleted", "Could not …" in the app goes through
 * [show]; the kind is worked out from the words, or given ([success] / [error]). Draw it once, on top of everything: [AppToastHost].
 */
object AppToast {
    enum class Kind { Success, Error, Info }

    class Item(val id: Long, val text: String, val kind: Kind)

    var current by mutableStateOf<Item?>(null)
        private set
    private var counter = 0L

    fun show(message: CharSequence?, kind: Kind? = null) {
        val text = message?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        current = Item(++counter, text, kind ?: guess(text))
    }


    /** Shows [message] after a short moment — but only if no other toast appeared in between (the screen's own toast wins, so nothing shows twice). */
    fun fallback(message: String) {
        val seen = counter
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (counter == seen) show(message, Kind.Success) }, 700)
    }

    fun success(message: CharSequence?) = show(message, Kind.Success)
    fun error(message: CharSequence?) = show(message, Kind.Error)
    fun dismiss() { current = null }

    private val bad = Regex("could not|couldn't|can't|cannot|unable|failed|failure|error|invalid|wrong|denied|not allowed|not available|no internet|required|must |expired|incorrect|unauthor|forbidden|too large|too big|try again", RegexOption.IGNORE_CASE)
    private fun guess(text: String): Kind = if (bad.containsMatchIn(text)) Kind.Error else Kind.Success
}

@Composable
fun AppToastHost(modifier: Modifier = Modifier) {
    val item = AppToast.current
    var shown by androidx.compose.runtime.remember { mutableStateOf<AppToast.Item?>(null) }      // kept while the toast slides away
    if (item != null) shown = item
    LaunchedEffect(item?.id) {
        if (item != null) {
            kotlinx.coroutines.delay((2200 + item.text.length * 35L).coerceAtMost(6000L))
            if (AppToast.current?.id == item.id) AppToast.dismiss()
        }
    }
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp, end = 12.dp, start = 56.dp), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(
            visible = item != null,
            enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(160)),
        ) {
            val last = shown ?: return@AnimatedVisibility
            val tint = when (last.kind) { AppToast.Kind.Success -> Color(0xFF0E8A50); AppToast.Kind.Error -> Color(0xFFD03B3B); AppToast.Kind.Info -> Color(0xFF1F2A44) }
            Row(
                Modifier.widthIn(max = 520.dp).shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = tint, spotColor = tint)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B1E26)).clickable { AppToast.dismiss() }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                    Text(if (last.kind == AppToast.Kind.Error) "✕" else "✓", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
                Text(last.text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

