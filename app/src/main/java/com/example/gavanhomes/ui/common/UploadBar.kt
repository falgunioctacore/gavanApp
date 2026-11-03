package com.example.gavanhomes.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.Uploads

/** A progress card at the bottom of the screen while a file is uploading: its name, the percentage and a bar that fills as it goes. */
@Composable
fun UploadBar(modifier: Modifier = Modifier) {
    val busy = Uploads.active > 0
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 92.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(busy, enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
            val f = Uploads.fraction
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B1E26)).padding(14.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (f >= 1f) "Finishing…" else "Uploading" + (Uploads.name?.let { " · $it" } ?: ""), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${(f * 100).toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = .16f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(4.dp)).background(Color(0xFF4F8CFF)))
                }
            }
        }
    }
}
