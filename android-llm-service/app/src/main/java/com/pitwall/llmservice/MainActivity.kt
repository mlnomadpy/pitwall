package com.pitwall.llmservice

import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

import java.io.File

class MainActivity : ComponentActivity() {
    private val modelPath = "/sdcard/Pitwall/models/gemma-4-E2B-it.task"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val context = LocalContext.current
            var hasNotificationPermission by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= 33) {
                        ContextCompat.checkSelfPermission(
                            context,
                            "android.permission.POST_NOTIFICATIONS"
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true
                )
            }

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted ->
                    hasNotificationPermission = isGranted
                    if (isGranted) {
                        startLlmService()
                    }
                }
            )

            LaunchedEffect(Unit) {
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= 33) {
                    launcher.launch("android.permission.POST_NOTIFICATIONS")
                } else {
                    startLlmService()
                }
            }

            val modelExists = remember { File(modelPath).exists() }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Pitwall LLM Service",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (!modelExists) {
                            Text(
                                text = "Model not found at:\n$modelPath",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Text(
                            text = if (hasNotificationPermission) 
                                "Service is running on port 8080" 
                            else 
                                "Notification permission required",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        if (hasNotificationPermission) {
                            CircularProgressIndicator()
                        } else {
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    launcher.launch("android.permission.POST_NOTIFICATIONS")
                                }
                            }) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startLlmService() {
        val serviceIntent = Intent(this, LlmServerService::class.java)
        startForegroundService(serviceIntent)
    }
}
