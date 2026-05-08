package com.pitwall.app.ui.title

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pitwall.app.ui.navigation.Routes
import com.pitwall.app.ui.shell.CrtScanlinesOverlay
import com.pitwall.app.ui.shell.CyberLandscapeBackground
import com.pitwall.app.ui.shell.CyberStartButton
import com.pitwall.app.ui.theme.PitwallFontTitle
import com.pitwall.app.ui.theme.PitwallFontUi
import com.pitwall.app.ui.theme.PitwallPalette
import kotlinx.coroutines.delay

/**
 * Native mirror of `TitleScreen.vue`: landscape shell, CRT overlay, START → save slots.
 */
@Composable
fun TitleScreen(navController: NavController) {
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(380)
        navController.navigate(Routes.SAVE) {
            launchSingleTop = true
        }
        pressed = false
    }

    fun handleStart() {
        if (pressed) return
        pressed = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { handleStart() },
            ),
    ) {
        CyberLandscapeBackground(Modifier.fillMaxSize())

        CrtScanlinesOverlay(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BoxWithConstraints {
                val titleSp = (maxWidth.value / 9f).coerceIn(28f, 76f).sp
                Text(
                    text = "PITWALL",
                    style =
                        TextStyle(
                            fontFamily = PitwallFontTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = titleSp,
                            lineHeight = titleSp,
                            letterSpacing = titleSp.value * 0.06f,
                            color = Color(0xFFf8f8f0),
                            shadow =
                                Shadow(
                                    color = PitwallPalette.TitleGlowWarm.copy(alpha = 0.55f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 24f,
                                ),
                        ),
                    textAlign = TextAlign.Center,
                )
            }

            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 12.dp)
                    .fillMaxWidth(0.72f)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                PitwallPalette.TitleGlowWarm.copy(alpha = 0.55f),
                                PitwallPalette.UiGood.copy(alpha = 0.78f),
                                PitwallPalette.TitleGlowWarm.copy(alpha = 0.55f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .border(1.dp, PitwallPalette.UiGood.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        .background(PitwallPalette.Surface.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(PitwallPalette.UiGood, CircleShape),
                )
                Text(
                    text = "AI RACING COACH",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontFamily = PitwallFontUi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 3.sp,
                    color = PitwallPalette.UiGood,
                )
                Box(
                    Modifier
                        .size(6.dp)
                        .background(PitwallPalette.UiGood, CircleShape),
                )
            }

            Spacer(Modifier.height(40.dp))

            CyberStartButton(
                label = "START",
                onClick = { handleStart() },
            )
        }

        Text(
            text = "SONOMA RACEWAY · 2026 EDITION",
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontFamily = PitwallFontUi,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = PitwallPalette.AccentSlate,
        )

        if (pressed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
    }
}
