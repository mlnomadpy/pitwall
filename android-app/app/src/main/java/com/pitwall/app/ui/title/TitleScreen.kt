package com.pitwall.app.ui.title

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.ui.bridge.BridgeStatusViewModel
import com.pitwall.app.ui.navigation.Routes
import com.pitwall.app.ui.session.SessionActionsViewModel
import com.pitwall.app.ui.theme.PitwallPalette

@Composable
fun TitleScreen(
    navController: NavController,
    sessionVm: SessionActionsViewModel = viewModel(),
) {
    val vm: BridgeStatusViewModel =
        viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val health by vm.health.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sessionBanner by sessionVm.banner.collectAsStateWithLifecycle()

    val pulseTransition = rememberInfiniteTransition(label = "title_cta")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse",
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            PitwallPalette.InkDeep,
                            PitwallPalette.Surface.copy(alpha = 0.92f),
                        ),
                    ),
                )
                .drawBehind {
                    val step = 3.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = PitwallPalette.Silver.copy(alpha = 0.035f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f,
                        )
                        y += step
                    }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "PITWALL",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier =
                    Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    PitwallPalette.CyanPrimary.copy(alpha = 0.85f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(PitwallPalette.UiGood, CircleShape),
                )
                Text(
                    "AI RACING COACH",
                    style = MaterialTheme.typography.labelLarge,
                    color = PitwallPalette.UiGood,
                    letterSpacing = 3.sp,
                )
                Box(
                    Modifier
                        .size(6.dp)
                        .background(PitwallPalette.UiGood, CircleShape),
                )
            }
            Text(
                text = "Flask bridge · coach stack",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Bridge",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(
                        onClick = { vm.refreshHealth() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (loading) "Checking…" else "Ping /health")
                    }
                    when {
                        error != null ->
                            Text(
                                text = error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        health != null ->
                            Column {
                                Text("status · ${health!!.status}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "engine · ${health!!.engine} · v${health!!.version}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = { sessionVm.startNewSession() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start new session")
            }

            sessionBanner?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (it.startsWith("Session started")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    modifier = Modifier.padding(top = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { navController.navigate(Routes.SAVE) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .alpha(pulseAlpha),
            ) {
                Text("Continue to save slots")
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            FilledTonalButton(
                onClick = {
                    if (SaveStore.activeSlot() != null) {
                        navController.navigate(Routes.GARAGE)
                    } else {
                        navController.navigate(Routes.SAVE)
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                Text(
                    if (SaveStore.activeSlot() != null) {
                        "Enter garage"
                    } else {
                        "Garage (pick save first)"
                    },
                )
            }

            Text(
                "Arcade shell cues match the web title — audio/sprites stay on PWA.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
