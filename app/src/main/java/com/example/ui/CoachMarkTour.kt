package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CoachAccent = Color(0xFF00F5D4)
private val CoachGreen = Color(0xFF118270)

/**
 * A single step of the interactive on-screen manual.
 *
 * @param targetKey Key of a registered on-screen element to spotlight. `null` dims the
 *   whole screen (used for intro/outro and the file-naming explanation).
 * @param tab       Tab that must be open for [targetKey] to exist, or `null` to stay put.
 */
private data class CoachStep(
    val targetKey: String?,
    val tab: Int?,
    val titleKey: String,
    val bodyKey: String,
    val example: List<String> = emptyList()
)

private fun coachSteps(): List<CoachStep> = listOf(
    CoachStep(null, null, "tour_s1_title", "tour_s1_body"),
    CoachStep("search_input", 0, "tour_s2_title", "tour_s2_body"),
    CoachStep("scan_button", 0, "tour_s3_title", "tour_s3_body"),
    CoachStep("play_in_order", 0, "tour_s4_title", "tour_s4_body"),
    CoachStep("play_shuffle", 0, "tour_s5_title", "tour_s5_body"),
    CoachStep(
        null, 0, "tour_s6_title", "tour_s6_body",
        example = listOf(
            "001_Imagine.mp3",
            "002_Yesterday.mp3",
            "010_Queen_Bohemian Rhapsody.mp3"
        )
    ),
    CoachStep("nav_bar", null, "tour_s8_title", "tour_s8_body"),
    CoachStep("playlists_add", 1, "tour_s9_title", "tour_s9_body"),
    CoachStep("settings_howto", 2, "tour_s10_title", "tour_s10_body"),
    CoachStep(null, null, "tour_s11_title", "tour_s11_body")
)

/**
 * Full-screen "spotlight" manual: dims the whole screen except the element currently
 * being explained, which is punched out (kept bright) and outlined, with an explanatory
 * card shown above or below it.
 *
 * @param targets     Live map of element keys to their bounds in root coordinates.
 * @param onSelectTab Switches the app's active tab so a step's target becomes visible.
 */
@Composable
fun CoachMarkTour(
    targets: Map<String, Rect>,
    language: String,
    isDark: Boolean,
    onSelectTab: (Int) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = remember { coachSteps() }
    var index by remember { mutableStateOf(0) }
    val step = steps[index]

    // Bring the required tab forward before drawing the step's highlight.
    LaunchedEffect(index) {
        step.tab?.let { onSelectTab(it) }
    }

    val density = LocalDensity.current
    val contentColor = if (isDark) Color.White else Color(0xFF111115)
    val secondaryColor = if (isDark) Color(0xFFB8B8C0) else Color(0xFF4A4A52)
    val panelColor = if (isDark) Color(0xFF1C1D22) else Color.White
    val panelBorder = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("coach_tour")
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val padPx = with(density) { 6.dp.toPx() }

        val hole = step.targetKey?.let { targets[it] }
        val highlight = hole?.let {
            Rect(
                left = (it.left - padPx).coerceIn(0f, screenWidthPx),
                top = (it.top - padPx).coerceIn(0f, screenHeightPx),
                right = (it.right + padPx).coerceIn(0f, screenWidthPx),
                bottom = (it.bottom + padPx).coerceIn(0f, screenHeightPx)
            )
        }

        val panelAlignment = when {
            highlight == null -> Alignment.Center
            highlight.center.y > screenHeightPx * 0.55f -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }

        // Dim layer with a transparent "hole" over the spotlighted element.
        // Offscreen compositing keeps BlendMode.Clear from erasing the app behind it.
        val interaction = remember { MutableInteractionSource() }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { /* block taps while the manual is open */ }
                )
        ) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            if (highlight != null) {
                val radius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(highlight.left, highlight.top),
                    size = Size(highlight.width, highlight.height),
                    cornerRadius = radius,
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    color = CoachAccent,
                    topLeft = Offset(highlight.left, highlight.top),
                    size = Size(highlight.width, highlight.height),
                    cornerRadius = radius,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        CoachPanel(
            step = step,
            language = language,
            index = index,
            total = steps.size,
            isDark = isDark,
            contentColor = contentColor,
            secondaryColor = secondaryColor,
            panelColor = panelColor,
            panelBorder = panelBorder,
            onBack = { if (index > 0) index-- },
            onNext = { if (index < steps.lastIndex) index++ else onFinish() },
            onSkip = onFinish,
            modifier = Modifier
                .align(panelAlignment)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        )
    }
}

@Composable
private fun CoachPanel(
    step: CoachStep,
    language: String,
    index: Int,
    total: Int,
    isDark: Boolean,
    contentColor: Color,
    secondaryColor: Color,
    panelColor: Color,
    panelBorder: Color,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLast = index == total - 1
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = panelColor),
        border = BorderStroke(1.dp, panelBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Strings.get("onb_step", language)
                        .replace("%1", (index + 1).toString())
                        .replace("%2", total.toString()),
                    color = CoachAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("coach_skip")
                ) {
                    Text(
                        text = Strings.get("onb_skip", language),
                        color = secondaryColor,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Strings.get(step.titleKey, language),
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = Strings.get(step.bodyKey, language),
                color = secondaryColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            if (step.example.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = Strings.get("tour_example_label", language),
                    color = secondaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CoachGreen.copy(alpha = if (isDark) 0.18f else 0.08f), RoundedCornerShape(10.dp))
                        .border(1.dp, CoachAccent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    step.example.forEach { line ->
                        Text(
                            text = line,
                            color = contentColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(Strings.get("onb_back", language), color = contentColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("coach_next"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoachGreen, contentColor = Color.White)
                ) {
                    Text(
                        text = if (isLast) Strings.get("onb_finish", language) else Strings.get("onb_next", language),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
