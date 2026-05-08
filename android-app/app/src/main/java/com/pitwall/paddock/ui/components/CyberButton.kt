package com.pitwall.paddock.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.ui.theme.ColorCharcoal
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.Dimens
import com.pitwall.paddock.ui.theme.OrbitronFamily

enum class CyberButtonVariant { Primary, Outlined, Dark }

/**
 * Cyber-styled CTA button matching the web app's <CyberButton> component.
 *
 * Features:
 *   • Orbitron Bold label with letter-spacing
 *   • 1.5dp ColorUiGood border
 *   • Press scale animation: 1.0 → 0.97 (web touch-target:active scale 0.98)
 *   • Primary: ColorUiGood/18 fill. Outlined: transparent. Dark: ColorCharcoal.
 */
@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CyberButtonVariant = CyberButtonVariant.Outlined,
    accentColor: Color = ColorUiGood,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label       = "cyber_button_scale",
    )

    val containerColor = when (variant) {
        CyberButtonVariant.Primary  -> accentColor.copy(alpha = 0.18f)
        CyberButtonVariant.Outlined -> Color.Transparent
        CyberButtonVariant.Dark     -> ColorCharcoal
    }

    Button(
        onClick           = onClick,
        modifier          = modifier
            .defaultMinSize(minHeight = 48.dp)
            .scale(scale),
        shape             = RoundedCornerShape(Dimens.CardCornerMd),
        border            = BorderStroke(Dimens.BorderThick, accentColor),
        colors            = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor   = accentColor,
        ),
        contentPadding    = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        interactionSource = interactionSource,
        elevation         = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text          = text,
            fontFamily    = OrbitronFamily,
            fontWeight    = FontWeight.Bold,
            fontSize      = 11.sp,
            letterSpacing = 0.8.sp,
        )
    }
}
