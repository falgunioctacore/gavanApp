package com.example.gavanhomes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The app's typeface: Inter — clean, a little wide, very readable on a phone. */
val AppFont = FontFamily(
    Font(com.example.gavanhomes.R.font.inter_regular, FontWeight.Normal),
    Font(com.example.gavanhomes.R.font.inter_medium, FontWeight.Medium),
    Font(com.example.gavanhomes.R.font.inter_semibold, FontWeight.SemiBold),
    Font(com.example.gavanhomes.R.font.inter_bold, FontWeight.Bold),
    Font(com.example.gavanhomes.R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** Every text inherits Inter, with a relaxed line height and no extra letter spacing. */
val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
)
