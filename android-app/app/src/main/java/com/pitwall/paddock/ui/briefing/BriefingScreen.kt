package com.pitwall.paddock.ui.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.BriefingCardData
import com.pitwall.paddock.data.BriefingCardStyle
import com.pitwall.paddock.data.DemoContent
import com.pitwall.paddock.data.toAccentColor
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.CyberButton
import com.pitwall.paddock.ui.components.CyberButtonVariant
import com.pitwall.paddock.ui.components.KerbStripe
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.ColorBiosGreen
import com.pitwall.paddock.ui.theme.ColorCharcoal
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorSlate
import com.pitwall.paddock.ui.theme.ColorSilver
import com.pitwall.paddock.ui.theme.ColorUiBad
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.ColorUiInfo
import com.pitwall.paddock.ui.theme.ColorUiWarn
import com.pitwall.paddock.ui.theme.Dimens
import com.pitwall.paddock.ui.theme.OrbitronFamily
import com.pitwall.paddock.ui.theme.RajdhaniFamily

@Composable
fun BriefingScreen(
    onCommence: () -> Unit,
    onOpenWebBrief: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ColorInk),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PitwallTopBar()

            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(Dimens.SpaceMd))

                // ── Section meta label ────────────────────────────────────────
                Text(
                    text          = "SESSION PREP",
                    color         = ColorUiInfo,
                    fontFamily    = RajdhaniFamily,
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                )

                // ── Screen heading ────────────────────────────────────────────
                Text(
                    text          = "PRE-BRIEFING SUMMARY",
                    color         = ColorSilver,
                    fontFamily    = OrbitronFamily,
                    fontSize      = 20.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier      = Modifier.padding(top = 6.dp),
                )

                // ── Kerb stripe rule under heading ────────────────────────────
                KerbStripe(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))

                // ── Body copy ─────────────────────────────────────────────────
                Text(
                    text       = "Review targeted sectors and telemetry markers prior to session initiation. Data derived from previous stint anomalies.",
                    color      = ColorSlate,
                    fontFamily = RajdhaniFamily,
                    fontSize   = 14.sp,
                    lineHeight = 20.sp,
                    modifier   = Modifier.padding(top = 12.dp),
                )

                Spacer(Modifier.height(Dimens.SpaceLg))

                // ── Briefing cards ────────────────────────────────────────────
                DemoContent.briefingCards.forEach { card ->
                    BriefingSectorCard(data = card)
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(Dimens.SpaceSm))

                // ── Web pre-brief CTA (outlined) ──────────────────────────────
                CyberButton(
                    text      = "OPEN WEB PRE-BRIEF",
                    onClick   = onOpenWebBrief,
                    variant   = CyberButtonVariant.Outlined,
                    modifier  = Modifier.fillMaxWidth().height(52.dp),
                )

                Spacer(Modifier.height(Dimens.SpaceSm))

                // ── Commence session CTA (primary) ────────────────────────────
                CyberButton(
                    text      = "🚀  COMMENCE TRACK SESSION",
                    onClick   = onCommence,
                    variant   = CyberButtonVariant.Primary,
                    modifier  = Modifier.fillMaxWidth().height(56.dp),
                )

                Spacer(Modifier.height(Dimens.SpaceXl))
            }
        }

        // CRT overlay — on top of all content, non-interactive
        CrtOverlay()
    }
}

// ── Briefing sector card ──────────────────────────────────────────────────────

@Composable
private fun BriefingSectorCard(data: BriefingCardData) {
    val accent = when (data.style) {
        BriefingCardStyle.Green -> ColorBiosGreen
        BriefingCardStyle.Peach -> ColorUiWarn
        BriefingCardStyle.Cyan  -> ColorUiGood
    }
    val icon = when (data.style) {
        BriefingCardStyle.Green -> Icons.Filled.WarningAmber
        BriefingCardStyle.Peach -> Icons.Filled.Bolt
        BriefingCardStyle.Cyan  -> Icons.Filled.Star
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(ColorCharcoal, RoundedCornerShape(Dimens.CardCornerMd))
            .height(IntrinsicSize.Min),
    ) {
        // Left accent bar
        Box(
            Modifier
                .width(Dimens.AccentBarWidth)
                .fillMaxHeight()
                .background(
                    accent,
                    RoundedCornerShape(topStart = Dimens.CardCornerMd, bottomStart = Dimens.CardCornerMd),
                ),
        )

        Row(
            Modifier
                .weight(1f)
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Icon container
            Box(
                modifier          = Modifier
                    .size(44.dp)
                    .background(ColorInk, RoundedCornerShape(Dimens.CardCornerSm))
                    .then(
                        Modifier.then(
                            Modifier.background(
                                color = accent.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(Dimens.CardCornerSm),
                            )
                        )
                    ),
                contentAlignment  = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }

            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                // Marker code
                Text(
                    text          = data.mkrCode,
                    color         = ColorSlate.copy(alpha = 0.7f),
                    fontFamily    = RajdhaniFamily,
                    fontSize      = 10.sp,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
                // Title
                Text(
                    text          = data.title,
                    color         = ColorSilver,
                    fontFamily    = OrbitronFamily,
                    fontSize      = 14.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    modifier      = Modifier.padding(top = 2.dp),
                )
                // Body
                Text(
                    text       = data.body,
                    color      = ColorSlate,
                    fontFamily = RajdhaniFamily,
                    fontSize   = 13.sp,
                    lineHeight = 18.sp,
                    modifier   = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
