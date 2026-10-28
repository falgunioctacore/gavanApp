package com.example.gavanhomes.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** SweetAlert2's default palette. */
private val SwalWarning = Color(0xFFF8BB86)
private val SwalTitle = Color(0xFF595959)
private val SwalCancel = Color(0xFF6E7881)
private val SwalDanger = Color(0xFFDC3741)

/**
 * A SweetAlert2-style confirmation: card that pops in, warning ring with "!", title, text, and a confirm /
 * cancel pair (the website uses the same look through SweetAlert, so the app feels the same).
 */
@Composable
fun SwalConfirm(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    cancelText: String = "Cancel",
    confirmColor: Color = SwalDanger,
) {
    val pop = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }

    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().scale(pop.value).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // warning icon: a ring with "!"
            Box(Modifier.size(80.dp).border(4.dp, SwalWarning.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
                Text("!", color = SwalWarning, fontSize = 46.sp, fontWeight = FontWeight.Light)
            }
            Spacer(Modifier.height(18.dp))
            Text(title, color = SwalTitle, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(text, color = Color(0xFF545454), fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                SwalButton(confirmText, confirmColor, onConfirm)
                SwalButton(cancelText, SwalCancel, onCancel)
            }
        }
    }
}

@Composable
private fun SwalButton(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(color).clickable { onClick() }.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}
