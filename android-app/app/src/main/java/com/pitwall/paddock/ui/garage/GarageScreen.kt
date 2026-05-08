package com.pitwall.paddock.ui.garage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.ColorCharcoal
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.OrbitronFamily
import com.pitwall.paddock.ui.theme.RajdhaniFamily

data class GarageTile(val title: String, val icon: ImageVector, val route: String)

val garageTiles = listOf(
    GarageTile("PRE-BRIEF", Icons.Default.Assignment, "briefing"),
    GarageTile("PIT STALL", Icons.Default.Sensors, "pit_stall"),
    GarageTile("ANALYSIS", Icons.Default.BarChart, "analysis_hub"),
    GarageTile("TRAINER CARD", Icons.Default.Person, "trainer_card"),
    GarageTile("QUEST LOG", Icons.Default.List, "quest_log"),
    GarageTile("COACH SELECT", Icons.Default.SmartToy, "coach_select"),
    GarageTile("SETTINGS", Icons.Default.Settings, "settings"),
    GarageTile("CAR SETUP", Icons.Default.Build, "car_setup")
)

@Composable
fun GarageScreen(
    onNavigate: (String) -> Unit
) {
    var isGridView by remember { mutableStateOf(false) }
    var cursorIndex by remember { mutableStateOf(1) } // Pit stall default

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            PitwallTopBar(
                title = "GARAGE",
                onFilterClick = { isGridView = !isGridView }
            )

            if (isGridView) {
                // Grid View
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2), // 2 columns for mobile/portrait, maybe 4 for landscape
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(garageTiles) { index, tile ->
                        GarageTileItem(
                            tile = tile,
                            isFocused = cursorIndex == index,
                            onClick = {
                                cursorIndex = index
                                onNavigate(tile.route)
                            }
                        )
                    }
                }
            } else {
                // Spatial Carousel View
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { /* handle snap if needed */ }
                            ) { change, dragAmount ->
                                change.consume()
                                if (dragAmount.y > 20) {
                                    // Swipe down -> previous
                                    cursorIndex = (cursorIndex - 1 + garageTiles.size) % garageTiles.size
                                } else if (dragAmount.y < -20) {
                                    // Swipe up -> next
                                    cursorIndex = (cursorIndex + 1) % garageTiles.size
                                } else if (dragAmount.x < -30) {
                                    // Swipe left -> select
                                    onNavigate(garageTiles[cursorIndex].route)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    garageTiles.forEachIndexed { index, tile ->
                        val diff = (index - cursorIndex + garageTiles.size) % garageTiles.size
                        
                        // Determine position classes like the PWA
                        val (targetTransY, targetRotX, targetAlpha, targetScale) = when (diff) {
                            0 -> listOf(0f, 0f, 1f, 1.1f) // focused
                            1 -> listOf(200f, -15f, 0.5f, 0.9f) // next
                            2 -> listOf(400f, -30f, 0.15f, 0.7f) // next-2
                            garageTiles.size - 1 -> listOf(-200f, 15f, 0.5f, 0.9f) // prev
                            garageTiles.size - 2 -> listOf(-400f, 30f, 0.15f, 0.7f) // prev-2
                            else -> listOf(0f, 0f, 0f, 0f) // hidden
                        }

                        val animTransY by animateFloatAsState(targetTransY, tween(200))
                        val animRotX by animateFloatAsState(targetRotX, tween(200))
                        val animAlpha by animateFloatAsState(targetAlpha, tween(200))
                        val animScale by animateFloatAsState(targetScale, tween(200))

                        if (targetAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(120.dp)
                                    .graphicsLayer {
                                        translationY = animTransY
                                        rotationX = animRotX
                                        alpha = animAlpha
                                        scaleX = animScale
                                        scaleY = animScale
                                    }
                            ) {
                                GarageTileItem(
                                    tile = tile,
                                    isFocused = diff == 0,
                                    onClick = {
                                        if (diff == 0) onNavigate(tile.route)
                                        else cursorIndex = index
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GarageTileItem(
    tile: GarageTile,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    PitwallFrame(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        accentColor = if (isFocused) ColorUiGood else Color(0xFF6E7686)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(ColorCharcoal.copy(alpha = if (isFocused) 0.6f else 0.3f))
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = tile.title,
                tint = if (isFocused) ColorUiGood else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(24.dp))
            Text(
                text = tile.title,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                fontFamily = OrbitronFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 2.sp
            )
        }
    }
}
