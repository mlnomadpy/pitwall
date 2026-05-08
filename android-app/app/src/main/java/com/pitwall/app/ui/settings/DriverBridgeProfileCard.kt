package com.pitwall.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitwall.app.ui.components.pitwall.MiniSparkline
import com.pitwall.app.ui.components.pitwall.PitwallHorizontalBar
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Summarizes `GET /driver/{id}/profile` for the Driver settings tab.
 */
@Composable
fun DriverBridgeProfileCard(profile: JsonObject?) {
    if (profile == null) return
    val err = profile["error"]?.jsonPrimitive?.content
    if (!err.isNullOrBlank()) {
        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        return
    }

    val weakest = profile["weakest_recent_corner"]?.jsonPrimitive?.content
    val weakestScore = profile["weakest_recent_score"]?.jsonPrimitive?.doubleOrNull
    val impr =
        when (val el = profile["biggest_improvement"]) {
            is JsonObject -> el
            else -> null
        }
    val imprCorner = impr?.get("corner")?.jsonPrimitive?.content
    val imprDelta = impr?.get("delta_score")?.jsonPrimitive?.doubleOrNull

    Card(
        modifier = Modifier.padding(top = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Bridge profile", style = MaterialTheme.typography.titleSmall)
            if (!weakest.isNullOrBlank() || weakestScore != null) {
                Text(
                    "Weakest corner · ${weakest ?: "—"} (${weakestScore?.let { "%.1f".format(it) } ?: "—"} score)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (weakestScore != null) {
                    PitwallHorizontalBar(
                        label = "Corner strength",
                        fraction = (weakestScore / 100.0).toFloat().coerceIn(0f, 1f),
                        caption = "relative %",
                    )
                }
            }
            if (!imprCorner.isNullOrBlank() && imprDelta != null) {
                Text(
                    "Biggest gain · $imprCorner (+${"%.2f".format(imprDelta)} vs prior)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val pb =
                profile["best_lap_history"]?.jsonArray?.mapNotNull { el ->
                    el.jsonObject["lap_s"]?.jsonPrimitive?.doubleOrNull
                }.orEmpty()
            if (pb.size >= 2) {
                Text(
                    "PB lap history (driver_events)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                MiniSparkline(values = pb, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
