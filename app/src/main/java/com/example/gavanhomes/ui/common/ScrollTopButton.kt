package com.example.gavanhomes.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** The round "back to top" button, like the website's: it fades in at the bottom-right once the page has been scrolled down a screen or so. */
@Composable
private fun BoxScope.TopButton(visible: Boolean, lift: androidx.compose.ui.unit.Dp = 0.dp, onClick: () -> Unit) {
    AnimatedVisibility(
        visible, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp + lift),
        enter = fadeIn() + scaleIn(initialScale = .7f), exit = fadeOut() + scaleOut(targetScale = .7f),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(46.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(KitInk).clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val k = size.width / 24f
                val st = Stroke(width = 2.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawLine(Color.White, Offset(12f * k, 20f * k), Offset(12f * k, 5f * k), st.width, StrokeCap.Round)
                drawPath(Path().apply { moveTo(5.5f * k, 11.5f * k); lineTo(12f * k, 5f * k); lineTo(18.5f * k, 11.5f * k) }, Color.White, style = st)
            }
        }
    }
}

/** For a list (LazyColumn): shows once the first screenful has scrolled away. */
@Composable
fun BoxScope.ScrollTopButton(state: LazyListState, lift: androidx.compose.ui.unit.Dp = 0.dp) {
    val scope = rememberCoroutineScope()
    val show by remember { derivedStateOf { state.firstVisibleItemIndex > 2 || (state.firstVisibleItemIndex > 0 && state.firstVisibleItemScrollOffset > 0 && state.firstVisibleItemIndex > 1) } }
    TopButton(show, lift) { scope.launch { state.animateScrollToItem(0) } }
}

/** For a scrolling page (verticalScroll): shows once it is scrolled about a screen down; [lift] raises it above a floating button. */
@Composable
fun BoxScope.ScrollTopButton(state: ScrollState, lift: androidx.compose.ui.unit.Dp = 0.dp) {
    val scope = rememberCoroutineScope()
    val show by remember { derivedStateOf { state.value > 1400 } }
    TopButton(show, lift) { scope.launch { state.animateScrollTo(0) } }
}
