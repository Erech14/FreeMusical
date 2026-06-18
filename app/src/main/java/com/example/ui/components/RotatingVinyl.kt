package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun RotatingVinyl(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotation")
    
    // Smooth angle progression
    val rotationAngle by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(200.dp)
            .shadow(16.dp, shape = CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Draw Authentic Vinyl Record with concentric tracks and reflective shine overlays
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotationAngle)
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

            // 4. Colorful central paper release sticker
            drawCircle(
                color = Color(0xFFFFA000), // PrimaryAmber
                radius = radius * 0.35f,
                center = center
            )

            // 5. Secondary inner ring for vintage label styling
            drawCircle(
                color = Color(0xFFFF5722), // AccentOrange
                radius = radius * 0.25f,
                center = center
            )

            // 6. Album spindle hub
            drawCircle(
                color = Color(0xFF0F0F13), // CharcoalBlack
                radius = radius * 0.04f,
                center = center
            )
        }

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
