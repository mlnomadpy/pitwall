package com.pitwall.paddock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorSlate
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.Dimens
import com.pitwall.paddock.ui.theme.OrbitronFamily

/**
 * Pitwall top app bar — redesigned to match web PageShell header.
 *
 * Changes from original:
 *   • Background → ColorInk
 *   • Title → Orbitron Bold, ColorUiGood, letter-spacing 1.5sp
 *   • Avatar circle → ColorSlate/40 fill + ColorSlate/30 ring
 *   • KerbStripe accent at bottom
 *   • Filter icon → ColorUiGood
 */
@Composable
fun PitwallTopBar(
    title: String = "SONOMA RACEWAY",
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    onFilterClick: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(ColorInk),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            // ── Avatar / Profile ─────────────────────────────────────────────
            IconButton(
                onClick  = onProfileClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(Dimens.TouchTarget),
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(ColorSlate.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, ColorSlate.copy(alpha = 0.35f), CircleShape),
                )
            }

            // ── Title ────────────────────────────────────────────────────────
            Text(
                text          = title,
                color         = ColorUiGood,
                fontFamily    = OrbitronFamily,
                fontSize      = 13.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                textAlign     = TextAlign.Center,
                modifier      = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp),
            )

            // ── Filter / Tune ────────────────────────────────────────────────
            IconButton(
                onClick  = onFilterClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(Dimens.TouchTarget),
            ) {
                Icon(
                    imageVector    = Icons.Filled.Tune,
                    contentDescription = "Filter",
                    tint           = ColorUiGood,
                )
            }
        }

        // ── Kerb stripe accent ───────────────────────────────────────────────
        KerbStripe()
    }
}
