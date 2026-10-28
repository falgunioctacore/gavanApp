package com.example.gavanhomes.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The app logo on a white rounded tile; draws nothing when there is no logo (or it can't be decoded, e.g. an SVG). */
@Composable
fun BrandMark(b: Branding, size: Dp = 46.dp, modifier: Modifier = Modifier) {
    val logo = b.logo ?: return
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color.White), contentAlignment = Alignment.Center) {
        Image(logo, contentDescription = b.name, modifier = Modifier.size(size * 0.76f), contentScale = ContentScale.Fit)
    }
}
