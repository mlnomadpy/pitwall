package com.pitwall.paddock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pitwall.paddock.ui.briefing.BriefingScreen
import com.pitwall.paddock.ui.briefing.WebBriefScreen
import com.pitwall.paddock.ui.feedback.HistoricalTrackMapScreen
import com.pitwall.paddock.ui.feedback.PostSessionAnalysisScreen
import com.pitwall.paddock.ui.components.MainTab
import com.pitwall.paddock.ui.components.PitwallNavBar
import com.pitwall.paddock.ui.garage.GarageScreen
import com.pitwall.paddock.ui.marker.MarkerDetailScreen
import com.pitwall.paddock.ui.ranking.RankingScreen
import com.pitwall.paddock.ui.theme.PaddockTheme
import com.pitwall.paddock.ui.track.TrackScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaddockTheme {
                PaddockAppContent()
            }
        }
    }
}

@Composable
fun PaddockAppContent() {
    val nav = rememberNavController()
    val vm: PaddockViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val showBottomBar = !route.startsWith("marker_detail") && route !in setOf(
        "post_session",
        "briefing_web",
        "historical_track_map",
    )
    val selectedTab = when {
        route.startsWith("marker_detail") -> MainTab.Track.route
        route == "post_session" || route == "historical_track_map" -> MainTab.Garage.route
        route == "briefing_web" -> MainTab.Briefing.route
        else -> route.ifEmpty { MainTab.Track.route }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                Box(Modifier.navigationBarsPadding()) {
                    PitwallNavBar(
                        selectedRoute = selectedTab,
                        onTabSelected = { r ->
                            nav.navigate(r) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = MainTab.Track.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(MainTab.Track.route) {
                TrackScreen(
                    state = state,
                    markers = vm.markers,
                    onOpenMarkerDetail = { m -> nav.navigate("marker_detail/${m.id}") },
                    onSelectedCountClick = {
                        nav.navigate(MainTab.Briefing.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(MainTab.Briefing.route) {
                BriefingScreen(
                    onCommence = {
                        nav.navigate(MainTab.Track.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenWebBrief = { nav.navigate("briefing_web") },
                )
            }
            composable("briefing_web") {
                WebBriefScreen(
                    webBriefBaseUrl = BuildConfig.WEB_BRIEF_BASE_URL,
                    focusMarkerIds = state.selectedMarkerIds,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(MainTab.Ranking.route) {
                RankingScreen(
                    onOpenPostSessionCatalog = { nav.navigate("post_session") },
                )
            }
            composable(MainTab.Garage.route) {
                GarageScreen(
                    state = state,
                    baseUrl = BuildConfig.PITWALL_API_BASE_URL,
                    markers = vm.markers,
                    onRefreshBridge = { vm.refreshBridgeHealth() },
                    onOpenPostSessionCatalog = { nav.navigate("post_session") },
                )
            }
            composable("post_session") {
                PostSessionAnalysisScreen(
                    onBack = { nav.popBackStack() },
                    onOpenHistoricalMap = { nav.navigate("historical_track_map") },
                )
            }
            composable("historical_track_map") {
                HistoricalTrackMapScreen(onBack = { nav.popBackStack() })
            }
            composable(
                route = "marker_detail/{markerId}",
                arguments = listOf(navArgument("markerId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("markerId") ?: return@composable
                val marker = vm.markers.find { it.id == id } ?: return@composable
                val inFocus = id in state.selectedMarkerIds
                val canAdd = state.selectedMarkerIds.size < 3 || inFocus
                MarkerDetailScreen(
                    marker = marker,
                    isInFocus = inFocus,
                    canAddToFocus = canAdd,
                    onBack = { nav.popBackStack() },
                    onTackle = { vm.toggleFocus(id) },
                )
            }
        }
    }
}
