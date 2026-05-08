package com.pitwall.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Typography aligned with PWA utilities (`text-title`, `text-body`, …). */
val PitwallTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = PitwallFontTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                lineHeight = 58.sp,
                letterSpacing = 3.sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = PitwallFontTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                lineHeight = 44.sp,
                letterSpacing = 2.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = PitwallFontUi,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 1.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = PitwallFontUi,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                letterSpacing = 1.2.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = PitwallFontUi,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.8.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = PitwallFontUi,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = PitwallFontUi,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = PitwallFontMono,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = PitwallFontUi,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                letterSpacing = 2.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = PitwallFontMono,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.6.sp,
            ),
    )
