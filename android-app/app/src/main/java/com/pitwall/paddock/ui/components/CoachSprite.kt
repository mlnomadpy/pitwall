package com.pitwall.paddock.ui.components

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.pitwall.paddock.BuildConfig

@Composable
fun CoachSprite(
    coachId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ImageLoader.Builder(context)
        .components {
            if (SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

    // Assuming the python bridge serves the sprites from /static/
    val spriteUrl = "${BuildConfig.PITWALL_API_BASE_URL}/static/${coachId.lowercase()}.gif"

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(spriteUrl)
            .crossfade(true)
            .build(),
        imageLoader = imageLoader
    )

    Box(modifier = modifier) {
        Image(
            painter = painter,
            contentDescription = "Coach $coachId",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
