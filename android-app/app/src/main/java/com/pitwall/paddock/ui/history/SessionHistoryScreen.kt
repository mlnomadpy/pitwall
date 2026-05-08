package com.pitwall.paddock.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.local.AppDatabase
import com.pitwall.paddock.data.local.SessionHistoryEntity
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val sessions by db.sessionHistoryDao().getAllSessionsFlow().collectAsState(initial = emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            PitwallTopBar(
                title = "SESSION HISTORY"
            )

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "NO SESSIONS RECORDED",
                        color = ColorSlate,
                        fontFamily = RajdhaniFamily,
                        fontSize = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(sessions) { session ->
                        SessionHistoryCard(session)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: SessionHistoryEntity) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
    val dateStr = dateFormat.format(Date(session.startTime))

    PitwallFrame(
        accentColor = ColorUiGood,
        modifier = Modifier.fillMaxWidth().clickable { /* TODO: Open Stage Clear with detailed json */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.trackName.uppercase(),
                    color = Color.White,
                    fontFamily = OrbitronFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = session.coachGrade.uppercase(),
                    color = if (session.coachGrade in listOf("A", "S")) ColorUiGood else ColorSlate,
                    fontFamily = OrbitronFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "DATE: $dateStr",
                color = ColorSilver,
                fontFamily = RajdhaniFamily,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "DRIVER: ${session.driverName} | LAPS: ${session.lapCount}",
                color = ColorSilver,
                fontFamily = RajdhaniFamily,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "BEST LAP: ${session.bestLapTimeS}s",
                color = ColorUiGood,
                fontFamily = OrbitronFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
