package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.isActive

@Composable
fun RotatingVinyl(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // The angle is kept across pause/resume so the record never snaps back to 0.
    // It is driven from frame time (withFrameNanos) so the rotation is perfectly
    // synced with the display refresh rate and stops instantly on pause.
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            val deltaSeconds = (nowNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = nowNanos
            // One full revolution per 4 seconds (90 deg/sec), same as before.
            rotationAngle = (rotationAngle + deltaSeconds * 90f) % 360f
        }
    }

    Box(
        modifier = modifier
            .size(200.dp)
            .shadow(16.dp, shape = CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Draw Authentic Vinyl Record with concentric tracks
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotationAngle }
        ) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)
            val radius = size.minDimension / 2f

            // 1. Vinyl solid black plastic plate
            drawCircle(
                color = Color(0xFF16161C),
                radius = radius,
                center = center
            )

            // 2. Reflective high-gloss grooved circles
            val grooveDensity = 10
            for (i in 1..grooveDensity) {
                val grooveRadius = radius * (0.35f + (0.55f * i / grooveDensity))
                drawCircle(
                    color = Color(0xFF0C0C10),
                    radius = grooveRadius,
                    center = center,
                    style = Stroke(width = 1.2f)
                )
            }

            // 3. Stylized inner grooves accent lines
            drawCircle(
                color = Color(0xFF2E2E38),
                radius = radius - 8f,
                center = center,
                style = Stroke(width = 1f)
            )
        }

        // Center Album Logo Artwork
        Image(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = "Vinyl Logo Artwork",
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer { rotationZ = rotationAngle }
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        // Draw Ambient Vinyl Highlights (does not rotate, simulates static studio reflections)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw reflections / shine arcs
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = 220f,
                sweepAngle = 35f,
                useCenter = true,
                size = size
            )
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = 40f,
                sweepAngle = 35f,
                useCenter = true,
                size = size
            )
        }
    }
}
