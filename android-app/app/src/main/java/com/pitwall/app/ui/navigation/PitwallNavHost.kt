package com.pitwall.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pitwall.app.ui.analysis.AnalysisBundleScreen
import com.pitwall.app.ui.analysis.AnalysisHubScreen
import com.pitwall.app.ui.analysis.ComparisonScreen
import com.pitwall.app.ui.analysis.GhostManagerScreen
import com.pitwall.app.ui.analysis.SqlConsoleScreen
import com.pitwall.app.ui.analysis.TelemetryReplayScreen
import com.pitwall.app.ui.analytics.BrakeAccelerationScreen
import com.pitwall.app.ui.analytics.CornerClassificationScreen
import com.pitwall.app.ui.analytics.CornerMasteryScreen
import com.pitwall.app.ui.analytics.DriverEvolutionScreen
import com.pitwall.app.ui.analytics.InsightsScreen
import com.pitwall.app.ui.analytics.LapDistributionScreen
import com.pitwall.app.ui.analytics.LapTimesScreen
import com.pitwall.app.ui.analytics.PedalProfileScreen
import com.pitwall.app.ui.analytics.SessionClipsScreen
import com.pitwall.app.ui.analytics.SectorTimesScreen
import com.pitwall.app.ui.analytics.StraightsAndSpeedScreen
import com.pitwall.app.ui.analytics.ThrottleCornerBoxScreen
import com.pitwall.app.ui.analytics.TrackAtlasScreen
import com.pitwall.app.ui.analytics.TrackMapScreen
import com.pitwall.app.ui.analytics.TrackReferenceScreen
import com.pitwall.app.ui.calibration.CalibrationScreen
import com.pitwall.app.ui.coach.CoachAskScreen
import com.pitwall.app.ui.coach.CoachBiosScreen
import com.pitwall.app.ui.coach.CoachConceptsScreen
import com.pitwall.app.ui.coach.CoachSelectScreen
import com.pitwall.app.ui.coach.PreBriefScreen
import com.pitwall.app.ui.endofday.EndOfDayScreen
import com.pitwall.app.ui.garage.CarSetupScreen
import com.pitwall.app.ui.garage.GarageHubScreen
import com.pitwall.app.ui.garage.TrainerCardScreen
import com.pitwall.app.ui.hardware.HardwareDetailScreen
import com.pitwall.app.ui.leaderboard.GlobalLeaderboardScreen
import com.pitwall.app.ui.hud.HudScreen
import com.pitwall.app.ui.notifications.NotificationsScreen
import com.pitwall.app.ui.pitstall.LivePitWallScreen
import com.pitwall.app.ui.pitstall.PitStallScreen
import com.pitwall.app.ui.onboarding.OnboardingScreen
import com.pitwall.app.ui.quests.QuestLogScreen
import com.pitwall.app.ui.quests.SponsorContractsScreen
import com.pitwall.app.ui.save.SaveSelectScreen
import com.pitwall.app.ui.settings.AppSettingsScreen
import com.pitwall.app.ui.sessions.SessionDetailScreen
import com.pitwall.app.ui.sessions.SessionsListScreen
import com.pitwall.app.ui.stage.StageClearScreen
import com.pitwall.app.ui.title.TitleScreen

@Composable
fun PitwallNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.TITLE,
        modifier = modifier,
    ) {
        composable(Routes.TITLE) { TitleScreen(navController) }

        composable(Routes.SAVE) {
            SaveSelectScreen(navController)
        }

        composable(Routes.BRIDGE_SESSIONS) {
            SessionsListScreen(navController, screenTitle = "Bridge sessions")
        }

        composable(
            route = Routes.SESSION_DETAIL,
            arguments = listOf(navArgument("sid") { type = NavType.StringType }),
        ) { entry ->
            SessionDetailScreen(
                sessionId = entry.arguments?.getString("sid").orEmpty(),
                navController = navController,
            )
        }

        composable(
            route = Routes.ONBOARDING,
            arguments =
                listOf(
                    navArgument("step") {
                        type = NavType.StringType
                        defaultValue = "0"
                    },
                ),
        ) { entry ->
            val step = entry.arguments?.getString("step") ?: ""
            OnboardingScreen(navController = navController, step = step)
        }

        composable(Routes.GARAGE) {
            GarageHubScreen(navController)
        }
        composable(Routes.CAR_SETUP) {
            CarSetupScreen(navController)
        }
        composable(Routes.TRAINER_CARD) {
            TrainerCardScreen(navController)
        }
        composable(Routes.COACH_SELECT) {
            CoachSelectScreen(navController)
        }
        composable(Routes.COACH_BIOS) {
            CoachBiosScreen(navController)
        }
        composable(Routes.PIT_STALL) {
            PitStallScreen(navController)
        }
        composable(Routes.HARDWARE) {
            HardwareDetailScreen(navController)
        }
        composable(Routes.PIT_STALL_LIVE) {
            LivePitWallScreen(navController)
        }
        composable(Routes.QUESTS) {
            QuestLogScreen(navController)
        }
        composable(Routes.SPONSORS) {
            SponsorContractsScreen(navController)
        }
        composable(Routes.ANALYSIS) {
            AnalysisHubScreen(navController)
        }
        composable(Routes.ANALYSIS_BUNDLE) {
            AnalysisBundleScreen(navController)
        }
        composable(Routes.INSIGHTS) {
            InsightsScreen(navController)
        }
        composable(Routes.LAP_DISTRIBUTION) {
            LapDistributionScreen(navController)
        }
        composable(Routes.LAP_TIMES) {
            LapTimesScreen(navController)
        }
        composable(Routes.SECTOR_TIMES) {
            SectorTimesScreen(navController)
        }
        composable(Routes.SESSION_CLIPS) {
            SessionClipsScreen(navController)
        }
        composable(Routes.BRAKE_ACCEL) {
            BrakeAccelerationScreen(navController)
        }
        composable(Routes.THROTTLE_CORNER_BOX) {
            ThrottleCornerBoxScreen(navController)
        }
        composable(Routes.CORNER_CLASSIFICATION) {
            CornerClassificationScreen(navController)
        }
        composable(Routes.COMPARE) {
            ComparisonScreen(navController)
        }
        composable(Routes.CORNERS) {
            CornerMasteryScreen(navController)
        }
        composable(Routes.STRAIGHTS) {
            StraightsAndSpeedScreen(navController)
        }
        composable(Routes.TRACK_WALK) {
            TrackMapScreen(navController)
        }
        composable(Routes.TRACK_REFERENCE) {
            TrackReferenceScreen(navController)
        }
        composable(Routes.ATLAS) {
            TrackAtlasScreen(navController)
        }
        composable(Routes.EVOLUTION) {
            DriverEvolutionScreen(navController)
        }
        composable(Routes.PEDALS) {
            PedalProfileScreen(navController)
        }
        composable(Routes.GHOSTS) {
            GhostManagerScreen(navController)
        }
        composable(Routes.REPLAY) {
            TelemetryReplayScreen(navController)
        }
        composable(Routes.SQL) {
            SqlConsoleScreen(navController)
        }
        composable(Routes.COACH_ASK) {
            CoachAskScreen(navController)
        }
        composable(Routes.COACH_CONCEPTS) {
            CoachConceptsScreen(navController)
        }
        composable(Routes.BRIEFING) {
            PreBriefScreen(navController)
        }
        composable(Routes.HUD) {
            HudScreen(navController)
        }
        composable(Routes.STAGE_CLEAR) {
            StageClearScreen(navController)
        }
        composable(Routes.CALIBRATION) {
            CalibrationScreen(navController)
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(navController)
        }
        composable(Routes.SETTINGS) {
            AppSettingsScreen(navController)
        }
        composable(Routes.END_OF_DAY) {
            EndOfDayScreen(navController)
        }
        composable(Routes.LEADERBOARD) {
            GlobalLeaderboardScreen(navController)
        }
    }
}
