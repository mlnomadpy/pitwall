package com.pitwall.paddock.ui.carstatus

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.service.UsbProxyService
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.ColorCharcoal
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorSlate
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.ColorUiBad
import com.pitwall.paddock.ui.theme.OrbitronFamily
import com.pitwall.paddock.ui.theme.RajdhaniFamily

@Composable
fun CarSetupScreen(isBridgeOnline: Boolean) {
    val context = LocalContext.current
    var isProxyRunning by remember { mutableStateOf(UsbProxyService.isRunning) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            PitwallTopBar(title = "CAR SETUP")
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Bridge Status
                Row(
                    modifier = Modifier
                        .background(ColorCharcoal.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.pitwall.paddock.ui.components.StatusPip(
                        active = isBridgeOnline,
                        color = if (isBridgeOnline) ColorUiGood else ColorUiBad
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isBridgeOnline) "TERMUX BRIDGE ONLINE" else "TERMUX BRIDGE OFFLINE",
                        color = if (isBridgeOnline) ColorUiGood else ColorUiBad,
                        fontFamily = OrbitronFamily,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(Modifier.height(32.dp))

                Text(
                    text = "USB-to-TCP Serial Proxy",
                    color = Color.White,
                    fontFamily = OrbitronFamily,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Routes a physical USB CAN adapter to localhost:9000\\nfor Termux integration without root.",
                    color = ColorSlate,
                    fontFamily = RajdhaniFamily,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Button(
                    onClick = {
                        if (isProxyRunning) {
                            context.stopService(Intent(context, UsbProxyService::class.java))
                            isProxyRunning = false
                        } else {
                            context.startService(Intent(context, UsbProxyService::class.java))
                            isProxyRunning = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isProxyRunning) ColorUiBad else ColorUiGood
                    )
                ) {
                    Text(
                        text = if (isProxyRunning) "STOP USB PROXY" else "START USB PROXY",
                        fontFamily = OrbitronFamily,
                        color = Color.White
                    )
                }
            }
        }
    }
}
