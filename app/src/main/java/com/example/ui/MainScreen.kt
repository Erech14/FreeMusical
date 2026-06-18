package com.example.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.Track
import com.example.player.MusicViewModel
import com.example.ui.components.RotatingVinyl
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPermissionGranted by viewModel.isStoragePermissionGranted.collectAsStateWithLifecycle()
    val selectedFolderUri by viewModel.selectedFolderUri.collectAsStateWithLifecycle()
    val tracks by viewModel.tracksState.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanCount by viewModel.scanCount.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val mediaError by viewModel.mediaError.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    // Launcher for directory picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectFolder(uri)
        }
    }

    // Storage permission launcher depending on SDK
    val permissionString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setPermissionGranted(isGranted)
        if (isGranted && selectedFolderUri == null) {
            // Trigger folder selection on first success
            try {
                folderPickerLauncher.launch(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Check permission on startup
    LaunchedEffect(Unit) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(permissionString) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        viewModel.setPermissionGranted(hasPermission)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBlack),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Плеер",
                            tint = PrimaryAmber,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            color = SoftWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    if (isPermissionGranted && selectedFolderUri != null) {
                        IconButton(
                            onClick = { viewModel.changeFolder() },
                            modifier = Modifier.testTag("change_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = stringResource(R.string.change_folder),
                                tint = MutedText
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CharcoalBlack,
                    titleContentColor = SoftWhite
                )
            )
        },
        containerColor = CharcoalBlack
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = CharcoalBlack
        ) {
            when {
                !isPermissionGranted -> {
                    PermissionOnboarding(
                        onGrantClick = { permissionLauncher.launch(permissionString) }
                    )
                }
                selectedFolderUri == null -> {
                    FolderSelectionOnboarding(
                        onSelectFolderClick = { folderPickerLauncher.launch(null) }
                    )
                }
                else -> {
                    // Fully configured music client
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Display error message if any
                        mediaError?.let { error ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(12.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Playback Deck & Turf Component
                        PlaybackTurntableDeck(
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            currentPosition = currentPosition,
                            duration = duration,
                            isShuffleEnabled = isShuffleEnabled,
                            onPlayPauseClick = { viewModel.togglePlayPause() },
                            onNextClick = { viewModel.playNext() },
                            onPrevClick = { viewModel.playPrevious() },
                            onShuffleClick = { viewModel.toggleShuffle() },
                            onSeek = { position -> viewModel.seekTo(position) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Search and Sync Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.search_hint),
                                        color = MutedText,
                                        fontSize = 14.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MutedText
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = MutedText
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("search_input"),
                                shape = RoundedCornerShape(26.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = CharcoalGray,
                                    unfocusedContainerColor = CharcoalGray,
                                    disabledContainerColor = CharcoalGray,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = SoftWhite,
                                    unfocusedTextColor = SoftWhite
                                ),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    val uri = Uri.parse(selectedFolderUri!!)
                                    viewModel.scanDirectory(uri)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(CharcoalGray, CircleShape)
                                    .testTag("scan_button"),
                                enabled = !isScanning
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = PrimaryAmber,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.rescan),
                                        tint = PrimaryAmber
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Audio track library stream list
                        Text(
                            text = if (isScanning) stringResource(R.string.scanning) else stringResource(R.string.all_tracks),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SoftWhite,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (isScanning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = PrimaryAmber)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.scanning),
                                        color = MutedText,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else if (tracks.isEmpty()) {
                            // Library is empty
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicOff,
                                        contentDescription = null,
                                        tint = MutedText,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.empty_library_title),
                                        fontWeight = FontWeight.Bold,
                                        color = SoftWhite,
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.empty_library_desc),
                                        color = MutedText,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Filter tracks
                            val filteredList = remember(tracks, searchQuery) {
                                if (searchQuery.isBlank()) {
                                    tracks
                                } else {
                                    tracks.filter {
                                        it.title.contains(searchQuery, ignoreCase = true) ||
                                                it.artist.contains(searchQuery, ignoreCase = true)
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(filteredList, key = { it.uriString }) { track ->
                                    TrackItemRow(
                                        track = track,
                                        isCurrent = currentTrack?.uriString == track.uriString,
                                        isPlaying = isPlaying && currentTrack?.uriString == track.uriString,
                                        onClick = { viewModel.playTrack(track) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionOnboarding(
    onGrantClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(DarkGreyDeck, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = PrimaryAmber
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.permission_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = SoftWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_desc),
                    fontSize = 14.sp,
                    color = MutedText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onGrantClick() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAmber,
                        contentColor = CharcoalBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("grant_permission_button")
                ) {
                    Text(
                        text = stringResource(R.string.grant_permission),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FolderSelectionOnboarding(
    onSelectFolderClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive micro music folder canvas drawing
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(DarkGreyDeck, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(50.dp)) {
                        // Drawing custom visual music folder outline
                        val width = size.width
                        val height = size.height
                        
                        // Folder background body
                        drawRoundRect(
                            color = PrimaryAmber,
                            topLeft = Offset(0f, height * 0.2f),
                            size = androidx.compose.ui.geometry.Size(width, height * 0.75f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                        )
                        // Folder top tab
                        drawRoundRect(
                            color = PrimaryAmber,
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(width * 0.45f, height * 0.35f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )
                        // Music note circle on top
                        drawCircle(
                            color = CharcoalGray,
                            radius = width * 0.18f,
                            center = Offset(width * 0.60f, height * 0.65f)
                        )
                        // Music note core
                        drawCircle(
                            color = AccentOrange,
                            radius = width * 0.08f,
                            center = Offset(width * 0.53f, height * 0.71f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.select_folder_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = SoftWhite,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.select_folder_desc),
                    fontSize = 14.sp,
                    color = MutedText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onSelectFolderClick() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAmber,
                        contentColor = CharcoalBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("select_folder_button")
                ) {
                    Text(
                        text = stringResource(R.string.select_folder_btn),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PlaybackTurntableDeck(
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isShuffleEnabled: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CharcoalGray),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rotating physical-like model of vinyl record
            RotatingVinyl(isPlaying = isPlaying)

            Spacer(modifier = Modifier.height(12.dp))

            // Text titles (Scrolling/Ellipses)
            Text(
                text = currentTrack?.title ?: "Дисковод пуст",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SoftWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentTrack?.artist ?: "Выберите аудиозапись",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Slider Scrubber
            val sliderPosition = if (duration > 0) currentPosition.toFloat() / duration else 0f
            var isDragging by remember { mutableStateOf(false) }
            var dragPosition by remember { mutableStateOf(0f) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(if (isDragging) (dragPosition * duration).toLong() else currentPosition),
                    fontSize = 11.sp,
                    color = MutedText,
                    modifier = Modifier.width(36.dp)
                )

                Slider(
                    value = if (isDragging) dragPosition else sliderPosition,
                    onValueChange = {
                        isDragging = true
                        dragPosition = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onSeek((dragPosition * duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = PrimaryAmber,
                        inactiveTrackColor = DarkGreyDeck,
                        thumbColor = PrimaryAmber
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatDuration(duration),
                    fontSize = 11.sp,
                    color = MutedText,
                    modifier = Modifier.width(36.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }

            // Controls Ribbon Block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Mode Button
                IconButton(
                    onClick = onShuffleClick,
                    modifier = Modifier.testTag("shuffle_button")
                ) {
                    val tint = if (isShuffleEnabled) PrimaryAmber else MutedText
                    Icon(
                        imageVector = if (isShuffleEnabled) Icons.Default.Shuffle else Icons.Outlined.Shuffle,
                        contentDescription = "Режим случайного порядка",
                        tint = tint
                    )
                }

                // Prev Track Button
                IconButton(
                    onClick = onPrevClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Предыдущий трек",
                        tint = SoftWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause Circle Button
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(PrimaryAmber, CircleShape)
                        .clickable { onPlayPauseClick() }
                        .testTag(if (isPlaying) "pause_button" else "play_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Проиграть / Пауза",
                        tint = CharcoalBlack,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next Track Button
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = SoftWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Order Sequential / Shuffle Display Indicator Mode
                Box(
                    modifier = Modifier
                        .background(DarkGreyDeck, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isShuffleEnabled) "RAND" else "SEQ",
                        fontWeight = FontWeight.Bold,
                        color = if (isShuffleEnabled) PrimaryAmber else MutedText,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TrackItemRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
            .testTag("track_item_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) DarkGreyDeck else CharcoalGray
        ),
        border = if (isCurrent) BorderStroke(1.dp, PrimaryAmber.copy(alpha = 0.5f)) else null,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left icon container based on playing state
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isCurrent) PrimaryAmber.copy(alpha = 0.15f) else DarkGreyDeck,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    // Audio equalizer glowing animation bars
                    LineEqualizerGlowing()
                } else {
                    Icon(
                        imageVector = if (isCurrent) Icons.Default.Audiotrack else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrent) PrimaryAmber else MutedText
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Texts column info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isCurrent) PrimaryAmber else SoftWhite,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    color = MutedText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Duration tag
            Text(
                text = track.getFormattedDuration(),
                color = MutedText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LineEqualizerGlowing() {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    
    // Create three separate bar animations for dynamic visual wave effects
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b3"
    )

    Row(
        modifier = Modifier.size(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar1Height)
                .background(PrimaryAmber, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar2Height)
                .background(PrimaryAmber, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar3Height)
                .background(PrimaryAmber, RoundedCornerShape(1.dp))
        )
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return String.format("%02d:%02d", min, sec)
}
