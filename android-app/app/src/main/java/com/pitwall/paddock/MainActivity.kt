package com.pitwall.paddock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pitwall.paddock.data.StartSessionRequest
import com.pitwall.paddock.ui.analysis.*
import com.pitwall.paddock.ui.briefing.BriefingScreen
import com.pitwall.paddock.ui.briefing.WebBriefScreen
import com.pitwall.paddock.ui.carstatus.CarSetupScreen
import com.pitwall.paddock.ui.coach.CoachSelectScreen
import com.pitwall.paddock.ui.components.MainTab
import com.pitwall.paddock.ui.components.PitwallNavBar
import com.pitwall.paddock.ui.feedback.HistoricalTrackMapScreen
import com.pitwall.paddock.ui.feedback.PostSessionAnalysisScreen
import com.pitwall.paddock.ui.garage.GarageScreen
import com.pitwall.paddock.ui.garage.PitStallScreen
import com.pitwall.paddock.ui.marker.MarkerDetailScreen
import com.pitwall.paddock.ui.onboarding.OnboardingScreen
import com.pitwall.paddock.ui.ontrack.OnTrackHudScreen
import com.pitwall.paddock.ui.quest.QuestLogScreen
import com.pitwall.paddock.ui.ranking.RankingScreen
import com.pitwall.paddock.ui.save.SaveSelectScreen
import com.pitwall.paddock.ui.stageclear.StageClearScreen
import com.pitwall.paddock.ui.theme.PaddockTheme
import com.pitwall.paddock.ui.title.TitleScreen
import com.pitwall.paddock.ui.track.TrackScreen
import com.pitwall.paddock.ui.trainer.TrainerCardScreen
import com.pitwall.paddock.service.TermuxLauncher
import com.pitwall.paddock.service.TermuxLifecycleService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Start the Lifecycle Service to catch app swipe-aways
        startService(Intent(this, TermuxLifecycleService::class.java))

        // 2. Fire the Intent to Termux to boot the Python backend
        TermuxLauncher.bootServer(this)

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
    val vm: PaddockViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val subRoute = backStack?.destination?.route.orEmpty()

    // Boot track outline and save slot
    LaunchedEffect(Unit) { 
        vm.loadTrackOutline(context)
        vm.loadSaveSlot(context)
    }

    val tabs = enumValues<MainTab>().toList()
    var selectedIndex by remember { mutableIntStateOf(0) }

    // Hide bottom bar on entry flow and sub-screens
    val showNavBar = subRoute == "root"

    // Wait until state is loaded to determine start destination
    // Simple state flow to title if no slot, otherwise jump to root if they had a slot loaded.
    // For simplicity, we just start at title and user flows through Save Select.

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showNavBar) {
                Box(Modifier.navigationBarsPadding()) {
                    PitwallNavBar(
                        selectedRoute = tabs[selectedIndex].route,
                        onTabSelected = { route ->
                            val idx = tabs.indexOfFirst { it.route == route }
                            if (idx >= 0) selectedIndex = idx
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "title",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // ── Phase 2: Entry Flow ───────────────────────────────────────────
            composable("title") {
                TitleScreen(onNavigateNext = { nav.navigate("save_select") })
            }
            composable("save_select") {
                SaveSelectScreen(
                    activeSlot = state.activeSlot,
                    onSelectSave = { nav.navigate("root") { popUpTo("title") { inclusive = true } } },
                    onNewGame = { nav.navigate("onboarding") }
                )
            }
            composable("onboarding") {
                OnboardingScreen(
                    onComplete = { slot ->
                        vm.createSaveSlot(context, slot)
                        nav.navigate("root") { popUpTo("title") { inclusive = true } }
                    }
                )
            }

            // ── Main Tabs (Root) ──────────────────────────────────────────────
            composable("root") {
                Box(Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { index, tab ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (index == selectedIndex) Modifier
                                    else Modifier.then(Modifier.fillMaxSize())
                                ),
                        ) {
                            val visited = remember(index) { mutableStateOf(false) }
                            if (index == selectedIndex) visited.value = true

                            if (visited.value) {
                                AnimatedVisibility(
                                    visible = index == selectedIndex,
                                    enter = fadeIn(animationSpec = tween(150)),
                                    exit  = fadeOut(animationSpec = tween(100)),
                                ) {
                                    when (tab) {
                                        MainTab.Track -> TrackScreen(
                                            state = state,
                                            markers = vm.markers,
                                            onOpenMarkerDetail = { m -> nav.navigate("marker_detail/${m.id}") },
                                            onSelectedCountClick = { selectedIndex = 1 },
                                        )
                                        MainTab.Briefing -> BriefingScreen(
                                            briefResponse = state.briefResponse,
                                            isLoading = state.briefLoading,
                                            onFetchBrief = { vm.fetchBrief(driver = state.activeSlot?.driverName ?: "UNKNOWN") },
                                            onCommence = { nav.navigate("pit_stall") },
                                            onOpenWebBrief = { nav.navigate("briefing_web") }
                                        )
                                        MainTab.Ranking -> RankingScreen(
                                            onOpenPostSessionCatalog = { nav.navigate("analysis_hub") },
                                        )
                                        MainTab.Garage -> GarageScreen(
                                            onNavigate = { route -> nav.navigate(route) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Phase 3/5/9: Garage Sub-Screens ───────────────────────────────
            composable("pit_stall") {
                PitStallScreen(
                    bridgeLine = state.bridgeLine,
                    isBridgeOnline = state.bridgeOnline,
                    activeSlot = state.activeSlot,
                    onRefreshBridge = { vm.refreshBridgeHealth() },
                    onStartSession = { driver, level, track, car ->
                        vm.startSession(StartSessionRequest(driver, level, track, car)) {
                            state.activeSessionId?.let { sid ->
                                nav.navigate("on_track_hud/$sid")
                            }
                        }
                    }
                )
            }
            composable("trainer_card") { TrainerCardScreen(state.activeSlot) }
            composable("quest_log") { QuestLogScreen() }
            composable("coach_select") { CoachSelectScreen() }
            composable("car_setup") { CarSetupScreen() }
            
            // ── Phase 7: Analysis Hub & Screens ───────────────────────────────
            composable("analysis_hub") {
                AnalysisHubScreen(onNavigate = { route -> nav.navigate(route) })
            }
            composable("lap_times") {
                // If we had a specific session id, we'd pass it. For now, use current/last session.
                LaunchedEffect(Unit) { state.activeSessionId?.let { vm.fetchLapTimeTable(it) } }
                LapTimesScreen(lapTimeTable = state.currentLapTimeTable, isLoading = state.currentLapTimeTable == null)
            }
            composable("corner_mastery") {
                LaunchedEffect(Unit) { state.activeSessionId?.let { vm.fetchScorecard(it) } }
                CornerMasteryScreen(scorecard = state.currentScorecard, isLoading = state.currentScorecard == null, useMph = state.useMph)
            }
            composable("pedal_profile") {
                LaunchedEffect(Unit) { state.activeSessionId?.let { vm.fetchPedalBehavior(it) } }
                PedalProfileScreen(pedalBehavior = state.currentPedalBehavior, isLoading = state.currentPedalBehavior == null)
            }
            composable("comparison") { ComparisonScreen() }
            composable("driver_evolution") { DriverEvolutionScreen(sessions = state.sessions) }
            composable("session_history") { 
                com.pitwall.paddock.ui.history.SessionHistoryScreen(
                    onBack = { nav.popBackStack() }
                ) 
            }
            
            // ── Phase 6: On-Track HUD ─────────────────────────────────────────
            composable(
                route = "on_track_hud/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { entry ->
                val sid = entry.arguments?.getString("sessionId") ?: return@composable
                OnTrackHudScreen(
                    sessionId = sid,
                    telemetryFrame = state.telemetryFrame,
                    activeCue = state.activeCue,
                    trackOutline = state.trackOutline,
                    useMph = state.useMph,
                    onOpenStreams = { vm.openStreams(it) },
                    onCloseStreams = { vm.closeStreams() },
                    onEndSession = {
                        vm.endSession {
                            nav.navigate("stage_clear/$sid") {
                                popUpTo("root") // Return back to root after stage clear
                            }
                        }
                    }
                )
            }

            // ── Phase 8: Stage Clear ──────────────────────────────────────────
            composable(
                route = "stage_clear/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) {
                // In a real app we'd fetch the session detail using the ID
                StageClearScreen(
                    sessionDetail = null, // Passing null shows placeholder "Analyzing" or mock data
                    onBackToGarage = { nav.navigate("root") { popUpTo("root") { inclusive = true } } }
                )
            }

            // ── Existing Sub-screens ──────────────────────────────────────────
            composable("briefing_web") {
                WebBriefScreen(
                    webBriefBaseUrl  = BuildConfig.WEB_BRIEF_BASE_URL,
                    focusMarkerIds   = state.selectedMarkerIds,
                    onBack           = { nav.popBackStack() },
                )
            }
            composable("historical_track_map") {
                HistoricalTrackMapScreen(
                    trackOutline = state.trackOutline,
                    useMph       = state.useMph,
                )
            }
            composable(
                route     = "marker_detail/{markerId}",
                arguments = listOf(navArgument("markerId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("markerId") ?: return@composable
                val marker = vm.markers.find { it.id == id } ?: return@composable
                val inFocus   = id in state.selectedMarkerIds
                val canAdd    = state.selectedMarkerIds.size < 3 || inFocus
                MarkerDetailScreen(
                    marker        = marker,
                    isInFocus     = inFocus,
                    canAddToFocus = canAdd,
                    onBack        = { nav.popBackStack() },
                    onTackle      = { vm.toggleFocus(id) },
                )
            }
            
            // Re-route legacy 'settings' to the placeholder
            composable("settings") {
                // Not fully implemented in this sprint, use placeholder
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    androidx.compose.material3.Text("Settings screen", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}
