package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Accent = Color(0xFF00F5D4)
private val PrimaryGreen = Color(0xFF118270)

/**
 * One slide of the first-launch onboarding wizard.
 *
 * @param icon       Glyph shown in the round badge at the top of the slide.
 * @param titleKey   [Strings] key of the slide headline.
 * @param descKey    [Strings] key of the slide subtitle.
 * @param bulletKeys [Strings] keys of the bullet points (may be empty).
 */
private data class OnboardingPage(
    val icon: ImageVector,
    val titleKey: String,
    val descKey: String,
    val bulletKeys: List<String>
)

private fun onboardingPages(): List<OnboardingPage> = listOf(
    OnboardingPage(
        icon = Icons.Default.MusicNote,
        titleKey = "onb_welcome_title",
        descKey = "onb_welcome_desc",
        bulletKeys = emptyList()
    ),
    OnboardingPage(
        icon = Icons.Default.FolderOpen,
        titleKey = "onb_access_title",
        descKey = "onb_access_desc",
        bulletKeys = listOf("onb_access_b1", "onb_access_b2", "onb_access_b3", "onb_access_b4")
    ),
    OnboardingPage(
        icon = Icons.Default.AudioFile,
        titleKey = "onb_files_title",
        descKey = "onb_files_desc",
        bulletKeys = listOf(
            "onb_files_b1", "onb_files_b2", "onb_files_b3", "onb_files_b4",
            "onb_files_b5", "onb_files_b6", "onb_files_b7"
        )
    ),
    OnboardingPage(
        icon = Icons.Default.PlayCircle,
        titleKey = "onb_player_title",
        descKey = "onb_player_desc",
        bulletKeys = listOf(
            "onb_player_b1", "onb_player_b2", "onb_player_b3",
            "onb_player_b4", "onb_player_b5", "onb_player_b6"
        )
    ),
    OnboardingPage(
        icon = Icons.Default.QueueMusic,
        titleKey = "onb_playlists_title",
        descKey = "onb_playlists_desc",
        bulletKeys = listOf(
            "onb_playlists_b1", "onb_playlists_b2", "onb_playlists_b3",
            "onb_playlists_b4", "onb_playlists_b5"
        )
    ),
    OnboardingPage(
        icon = Icons.Default.CloudDownload,
        titleKey = "onb_server_title",
        descKey = "onb_server_desc",
        bulletKeys = listOf("onb_server_b1", "onb_server_b2", "onb_server_b3", "onb_server_b4")
    ),
    OnboardingPage(
        icon = Icons.Default.Settings,
        titleKey = "onb_settings_title",
        descKey = "onb_settings_desc",
        bulletKeys = listOf(
            "onb_settings_b1", "onb_settings_b2", "onb_settings_b3",
            "onb_settings_b4", "onb_settings_b5", "onb_settings_b6"
        )
    )
)

/**
 * Full-screen, swipeable onboarding wizard explaining what each screen does and how
 * the app works with track files. Shown on first launch and re-openable from Settings.
 */
@Composable
fun OnboardingScreen(
    language: String,
    isDark: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = remember { onboardingPages() }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    val contentColor = if (isDark) Color.White else Color(0xFF111115)
    val secondaryColor = if (isDark) Color(0xFFB8B8C0) else Color(0xFF4A4A52)
    val cardColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFFF2F4F7)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

    val backgroundBrush = remember(isDark) {
        val colors = if (isDark) {
            listOf(Color(0xFF171329), Color(0xFF0B2B28))
        } else {
            listOf(Color.White, Color(0xFFF2F4F7))
        }
        Brush.linearGradient(colors)
    }

    val currentPage = pagerState.currentPage
    val isLast = currentPage == pages.lastIndex

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .testTag("onboarding_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top bar: progress hint + "Skip"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Strings.get("onb_step", language)
                        .replace("%1", (currentPage + 1).toString())
                        .replace("%2", pages.size.toString()),
                    color = secondaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.testTag("onboarding_skip")
                ) {
                    Text(
                        text = Strings.get("onb_skip", language),
                        color = secondaryColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                OnboardingPageContent(
                    page = pages[pageIndex],
                    language = language,
                    contentColor = contentColor,
                    secondaryColor = secondaryColor,
                    cardColor = cardColor,
                    cardBorder = cardBorder
                )
            }

            // Step indicator dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { index ->
                    val selected = index == currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(if (selected) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) Accent
                                else if (isDark) Color.White.copy(alpha = 0.25f)
                                else Color.Black.copy(alpha = 0.15f)
                            )
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(currentPage - 1) } },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = Strings.get("onb_back", language),
                            color = contentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Button(
                    onClick = {
                        if (isLast) {
                            onFinish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("onboarding_next"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isLast) Strings.get("onb_finish", language) else Strings.get("onb_next", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    language: String,
    contentColor: Color,
    secondaryColor: Color,
    cardColor: Color,
    cardBorder: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.12f))
                .border(1.dp, Accent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(46.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = Strings.get(page.titleKey, language),
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Strings.get(page.descKey, language),
            color = secondaryColor,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )
        if (page.bulletKeys.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    page.bulletKeys.forEach { key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Accent)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = Strings.get(key, language),
                                color = contentColor.copy(alpha = 0.92f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
