package com.pitwall.paddock.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorSlate
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.Dimens
import com.pitwall.paddock.ui.theme.RajdhaniFamily

enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Track("track", "TRACK", Icons.Filled.Map),
    Briefing("briefing", "BRIEFING", Icons.AutoMirrored.Outlined.Assignment),
    Ranking("ranking", "RANKING", Icons.Filled.BarChart),
    Garage("garage", "GARAGE", Icons.Filled.Settings),
}

/**
 * Pitwall bottom navigation bar — redesigned to match web nav aesthetic.
 *
 * Changes:
 *   • Background → ColorInk
 *   • 1px top divider in ColorSlate/25
 *   • Selected: ColorUiGood icon + label, ColorUiGood/12 indicator
 *   • Unselected: ColorSlate/50
 *   • Labels → Rajdhani SemiBold with letter-spacing
 */
@Composable
fun PitwallNavBar(
    selectedRoute: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        HorizontalDivider(
            thickness = Dimens.BorderNormal,
            color     = ColorSlate.copy(alpha = 0.25f),
        )
        NavigationBar(
            containerColor = ColorInk,
            tonalElevation = 0.dp,
        ) {
            MainTab.entries.forEach { tab ->
                val selected = selectedRoute == tab.route
                NavigationBarItem(
                    selected  = selected,
                    onClick   = { onTabSelected(tab.route) },
                    icon = {
                        Icon(
                            imageVector        = tab.icon,
                            contentDescription = tab.label,
                        )
                    },
                    label = {
                        Text(
                            text          = tab.label,
                            fontFamily    = RajdhaniFamily,
                            fontSize      = 10.sp,
                            fontWeight    = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            letterSpacing = 1.sp,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = ColorUiGood,
                        selectedTextColor   = ColorUiGood,
                        unselectedIconColor = ColorSlate.copy(alpha = 0.5f),
                        unselectedTextColor = ColorSlate.copy(alpha = 0.5f),
                        indicatorColor      = ColorUiGood.copy(alpha = 0.12f),
                    ),
                )
            }
        }
    }
}
