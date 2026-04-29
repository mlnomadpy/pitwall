package com.pitwall.paddock.data

import androidx.compose.ui.graphics.Color
import com.pitwall.paddock.ui.theme.AccentGreen
import com.pitwall.paddock.ui.theme.AccentPeach
import com.pitwall.paddock.ui.theme.PitwallCyan

enum class BriefingCardStyle {
    Green,
    Peach,
    Cyan,
}

fun BriefingCardStyle.toAccentColor(): Color = when (this) {
    BriefingCardStyle.Green -> AccentGreen
    BriefingCardStyle.Peach -> AccentPeach
    BriefingCardStyle.Cyan -> PitwallCyan
}

data class BriefingCardData(
    val mkrCode: String,
    val title: String,
    val body: String,
    val style: BriefingCardStyle,
)

data class LeaderboardEntry(
    val pos: String,
    val name: String,
    val team: String,
    val score: String,
    val gap: String,
    val isHighlighted: Boolean = false,
    val badge: String? = null,
)

object DemoContent {
    val briefingCards: List<BriefingCardData> = listOf(
        BriefingCardData(
            mkrCode = "MKR-01",
            title = "T10 LEFT BERM",
            body = "Suspension load exceeding nominal limits on exit kerb.",
            style = BriefingCardStyle.Green,
        ),
        BriefingCardData(
            mkrCode = "MKR-02",
            title = "THE CAROUSEL EXIT",
            body = "Traction loss detected at 85% throttle application.",
            style = BriefingCardStyle.Peach,
        ),
        BriefingCardData(
            mkrCode = "MKR-03",
            title = "TURN 7 APEX",
            body = "Missed geometric apex by 0.4m; understeer phase.",
            style = BriefingCardStyle.Cyan,
        ),
    )

    val leaderboard: List<LeaderboardEntry> = listOf(
        LeaderboardEntry("01", "J. VANE", "SCUDERIA CORSA", "9,842", "—", isHighlighted = true, badge = "ALPHA"),
        LeaderboardEntry("02", "M. SATO", "REDLINE RACING", "9,610", "+232"),
        LeaderboardEntry("03", "E. CHEN", "APEX DYNAMICS", "9,485", "+357"),
        LeaderboardEntry("04", "T. BECKER", "SCUDERIA CORSA", "9,120", "+722"),
    )
}
