package com.pitwall.app.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    step: String,
    vm: OnboardingViewModel =
        viewModel(
            viewModelStoreOwner = LocalContext.current as ComponentActivity,
        ),
) {
    val ctx = LocalContext.current
    val stepNum = step.toIntOrNull()?.coerceIn(1, 4) ?: 1
    val name by vm.driverName.collectAsStateWithLifecycle()
    val skill by vm.skillLevel.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New driver · step $stepNum of 4") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (stepNum <= 1) {
                                SaveStore.setActiveSlot(ctx, null)
                                navController.navigate(Routes.SAVE) {
                                    popUpTo(Routes.TITLE) { inclusive = false }
                                }
                            } else {
                                navController.navigateUp()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (stepNum) {
                1 -> {
                    Text(
                        "Welcome to Pitwall — same onboarding arc as the web client.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "You picked an empty slot. Next: name, skill, then we drop you in the garage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { navController.navigate("onboarding/2") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Next")
                    }
                }
                2 -> {
                    OutlinedTextField(
                        value = name,
                        onValueChange = vm::setDriverName,
                        label = { Text("Driver name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { navController.navigate("onboarding/3") },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Next")
                    }
                }
                3 -> {
                    Text("Skill level", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("beginner", "intermediate", "pro").forEach { opt ->
                            FilterChip(
                                selected = skill == opt,
                                onClick = { vm.setSkillLevel(opt) },
                                label = { Text(opt.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                    Button(
                        onClick = { navController.navigate("onboarding/4") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Next")
                    }
                }
                else -> {
                    Text(
                        "Ready to create ${name.ifBlank { "your driver" }} ($skill).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            val slotId = SaveStore.activeSlotId
                            if (slotId != null) {
                                SaveStore.newSlotFromOnboarding(
                                    context = ctx,
                                    slotId = slotId,
                                    driverName = name.ifBlank { "DRIVER" },
                                    skillLevel = skill,
                                )
                                navController.navigate(Routes.GARAGE) {
                                    popUpTo(Routes.TITLE) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Enter garage")
                    }
                }
            }
        }
    }
}
