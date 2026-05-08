package com.pitwall.paddock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import com.pitwall.paddock.ui.theme.ColorCurbRed
import com.pitwall.paddock.ui.theme.ColorCurbWhite
import com.pitwall.paddock.ui.theme.Dimens

/**
 * Horizontal kerb-stripe accent — alternating red/white repeating segments.
 * Matches the web .kerb-stripe and .heading-rule CSS classes.
 *
 * Used as:
 *   • Bottom accent on TopBar
 *   • Section separator between major content blocks
 */
@Composable
fun KerbStripe(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = Dimens.KerbStripeHeight,
    segmentWidth: androidx.compose.ui.unit.Dp = Dimens.KerbSegmentWidth,
) {
    val red   = ColorCurbRed
    val white = ColorCurbWhite

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                brush = Brush.horizontalGradient(
                    // Repeating red/white pair — we approximate with a tile using tileMode
                    // by composing a gradient with many stops to simulate repeat.
                    // Each segment occupies 1/20th of the bar for visual density.
                    colorStops = buildKerbStops(red, white, segments = 20),
                ),
            ),
    )
}

private fun buildKerbStops(red: Color, white: Color, segments: Int): Array<Pair<Float, Color>> {
    val stops = mutableListOf<Pair<Float, Color>>()
    val step = 1f / segments
    for (i in 0 until segments) {
        val start = i * step
        val mid   = start + step * 0.5f
        stops += start to if (i % 2 == 0) red else white
        stops += mid   to if (i % 2 == 0) red else white
        stops += mid   to if (i % 2 == 0) white else red
    }
    stops += 1f to if (segments % 2 == 0) red else white
    return stops.toTypedArray()
}
