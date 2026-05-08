package com.pitwall.paddock.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.SaveSlot
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.CyberButton
import com.pitwall.paddock.ui.components.CyberButtonVariant
import com.pitwall.paddock.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: (SaveSlot) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    var driverName by remember { mutableStateOf("") }
    var driverLevel by remember { mutableStateOf("Beginner") }
    var preferredCoach by remember { mutableStateOf("trod") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEW DRIVER PROFILE",
                    color = Color.White,
                    fontFamily = OrbitronFamily,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "STEP ${pagerState.currentPage + 1} / 3",
                    color = ColorUiGood,
                    fontFamily = RajdhaniFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> NameStep(
                        name = driverName,
                        onNameChange = { driverName = it },
                        onNext = { scope.launch { pagerState.animateScrollToPage(1) } }
                    )
                    1 -> LevelStep(
                        level = driverLevel,
                        onLevelChange = { driverLevel = it },
                        onNext = { scope.launch { pagerState.animateScrollToPage(2) } }
                    )
                    2 -> CoachStep(
                        coach = preferredCoach,
                        onCoachChange = { preferredCoach = it },
                        onComplete = {
                            onComplete(SaveSlot(driverName, driverLevel, preferredCoach))
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ENTER CALLSIGN",
            color = Color.White,
            fontFamily = OrbitronFamily,
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontFamily = RajdhaniFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ColorUiGood,
                unfocusedBorderColor = ColorSlate,
                cursorColor = ColorUiGood,
                focusedContainerColor = ColorCharcoal.copy(alpha = 0.3f),
                unfocusedContainerColor = ColorCharcoal.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth(0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        CyberButton(
            text = "NEXT",
            onClick = onNext,
            variant = if (name.isNotBlank()) CyberButtonVariant.Primary else CyberButtonVariant.Outlined
        )
    }
}

@Composable
private fun LevelStep(
    level: String,
    onLevelChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SELECT EXPERIENCE",
            color = Color.White,
            fontFamily = OrbitronFamily,
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        val levels = listOf("Beginner", "Intermediate", "Advanced")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            levels.forEach { l ->
                val selected = level == l
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) ColorUiGood else ColorSlate
                        )
                        .background(if (selected) ColorUiGood.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onLevelChange(l) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = l.uppercase(),
                        color = if (selected) ColorUiGood else Color.White,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        CyberButton(
            text = "NEXT",
            onClick = onNext,
            variant = CyberButtonVariant.Primary
        )
    }
}

@Composable
private fun CoachStep(
    coach: String,
    onCoachChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "CHOOSE YOUR COACH",
            color = Color.White,
            fontFamily = OrbitronFamily,
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        val coaches = listOf(
            Triple("trod", "T.R.O.D.", "Strict, data-driven, focus on fundamentals."),
            Triple("aria", "A.R.I.A.", "Adaptive, encouraging, focuses on flow."),
            Triple("rex", "R.E.X.", "Aggressive, pushes for maximum pace.")
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            coaches.forEach { (id, name, desc) ->
                val selected = coach == id
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .padding(horizontal = 8.dp)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) ColorUiGood else ColorSlate
                        )
                        .background(if (selected) ColorUiGood.copy(alpha = 0.2f) else ColorCharcoal.copy(alpha = 0.5f))
                        .clickable { onCoachChange(id) }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(ColorSlate.copy(alpha = 0.3f))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Placeholder for coach avatar
                        Text(id.take(1).uppercase(), color = Color.White, fontSize = 24.sp, fontFamily = OrbitronFamily)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = name,
                        color = if (selected) ColorUiGood else Color.White,
                        fontFamily = OrbitronFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = desc,
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = RajdhaniFamily,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        CyberButton(
            text = "COMPLETE",
            onClick = onComplete,
            variant = CyberButtonVariant.Primary
        )
    }
}
