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
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val appStyle by viewModel.appStyle.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isPlayerDetailedOpened by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White),
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
                                tint = Color(0xFF118270),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = Strings.get("app_name", language),
                                color = Color(0xFF1C1C1E),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = Strings.get("settings", language),
                                tint = Color(0xFF8E8E93)
                            )
                        }
                        if (isPermissionGranted && selectedFolderUri != null) {
                            IconButton(
                                onClick = { viewModel.changeFolder() },
                                modifier = Modifier.testTag("change_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = Strings.get("change_folder", language),
                                    tint = Color(0xFF8E8E93)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color(0xFF1C1C1E)
                    )
                )
            },
            bottomBar = {
                if (currentTrack != null) {
                    BottomPlayBar(
                        track = currentTrack!!,
                        isPlaying = isPlaying,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onStopClick = { viewModel.stopPlayback() },
                        onBarClick = { isPlayerDetailedOpened = true },
                        style = appStyle
                    )
                }
            },
            containerColor = Color.White
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = Color.White
            ) {
            when {
                !isPermissionGranted -> {
                    PermissionOnboarding(
                        onGrantClick = { permissionLauncher.launch(permissionString) },
                        language = language
                    )
                }
                selectedFolderUri == null -> {
                    FolderSelectionOnboarding(
                        onSelectFolderClick = { folderPickerLauncher.launch(null) },
                        language = language
                    )
                }
                else -> {
                    // Fully configured music client
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        // Display error message if any
                        mediaError?.let { error ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
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

                        // Search and Sync Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = Strings.get("search", language),
                                        color = Color(0xFF8E8E93),
                                        fontSize = 14.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = Strings.get("search", language),
                                        tint = Color(0xFF8E8E93)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Color(0xFF8E8E93)
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
                                    focusedContainerColor = Color(0xFFF2F2F7),
                                    unfocusedContainerColor = Color(0xFFF2F2F7),
                                    disabledContainerColor = Color(0xFFF2F2F7),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color(0xFF1C1C1E),
                                    unfocusedTextColor = Color(0xFF1C1C1E)
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
                                    .background(Color(0xFFF2F2F7), CircleShape)
                                    .testTag("scan_button"),
                                enabled = !isScanning
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFF118270),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Rescan",
                                        tint = Color(0xFF118270)
                                    )
                                }
                            }
                        }

                        // Play actions row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { viewModel.playSequential() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF118270)),
                                modifier = Modifier.padding(end = 8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Strings.get("play_in_order", language), fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel.playRandomly() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF118270)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Strings.get("play_shuffle", language), fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (isScanning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF118270))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = Strings.get("scanning", language),
                                        color = Color(0xFF8E8E93),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else if (tracks.isEmpty()) {
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
                                        tint = Color(0xFF8E8E93),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = Strings.get("library_empty", language),
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1C1C1E),
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = Strings.get("add_folder", language),
                                        color = Color(0xFF8E8E93),
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            val filteredList = remember(tracks, searchQuery) {
                                if (searchQuery.isBlank()) {
                                    tracks
                                } else {
                                    tracks.filter {
                                        it.fileName.contains(searchQuery, ignoreCase = true) ||
                                                it.title.contains(searchQuery, ignoreCase = true) ||
                                                it.artist.contains(searchQuery, ignoreCase = true)
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items(filteredList, key = { it.uriString }) { track ->
                                    TrackItemRow(
                                        track = track,
                                        isCurrent = currentTrack?.uriString == track.uriString,
                                        isPlaying = isPlaying && currentTrack?.uriString == track.uriString,
                                        onClick = { viewModel.playTrack(track) },
                                        style = appStyle
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // Settings Slide-up or overlay
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            SettingsScreen(
                viewModel = viewModel,
                onClose = { showSettings = false }
            )
        }

        // Expanded full-screen sliding vinyl turntable player sheet
        AnimatedVisibility(
            visible = isPlayerDetailedOpened,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F1015))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Slide-down handle header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { isPlayerDetailedOpened = false }
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse",
                                tint = SoftWhite,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = Strings.get("now_playing", language),
                            color = SoftWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    // detailed Turntable component
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
                        onSeek = { position -> viewModel.seekTo(position) },
                        language = language
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun PermissionOnboarding(
    onGrantClick: () -> Unit,
    language: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F7)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFE5E5EA), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color(0xFF118270)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = Strings.get("permission_title", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1C1C1E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Strings.get("permission_desc", language),
                    fontSize = 14.sp,
                    color = Color(0xFF8E8E93),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onGrantClick() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF118270),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("grant_permission_button")
                ) {
                    Text(
                        text = Strings.get("grant_permission", language),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FolderSelectionOnboarding(
    onSelectFolderClick: () -> Unit,
    language: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F7)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFFE5E5EA), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(50.dp)) {
                        val width = size.width
                        val height = size.height
                        
                        drawRoundRect(
                            color = Color(0xFF499587),
                            topLeft = Offset(0f, height * 0.2f),
                            size = androidx.compose.ui.geometry.Size(width, height * 0.75f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                        )
                        drawRoundRect(
                            color = Color(0xFF499587),
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(width * 0.45f, height * 0.35f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )
                        drawCircle(
                            color = Color(0xFFF2F2F7),
                            radius = width * 0.18f,
                            center = Offset(width * 0.60f, height * 0.65f)
                        )
                        drawCircle(
                            color = Color(0xFFFFA000),
                            radius = width * 0.08f,
                            center = Offset(width * 0.53f, height * 0.71f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = Strings.get("select_folder_title", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1C1C1E),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Strings.get("select_folder_desc", language),
                    fontSize = 14.sp,
                    color = Color(0xFF8E8E93),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onSelectFolderClick() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF118270),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("select_folder_button")
                ) {
                    Text(
                        text = Strings.get("select_folder_btn", language),
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
    onSeek: (Long) -> Unit,
    language: String
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
            RotatingVinyl(isPlaying = isPlaying)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = currentTrack?.title ?: Strings.get("deck_empty", language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SoftWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentTrack?.artist ?: Strings.get("choose_audio", language),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
fun BottomPlayBar(
    track: Track,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onBarClick: () -> Unit,
    style: Int
) {
    val context = LocalContext.current
    val artworkBitmap = rememberTrackArtwork(context, track.uriString)

    val backgroundStyleColors = when (style) {
        1 -> Color(0xCC1E2125) // Glassmorphism (partially transparent)
        2 -> Color(0xFF24272D) // Neumorphism
        3 -> Color.Black // Minimalism
        else -> Color(0xFF1E2125) // Standard / Material
    }

    val shape = when (style) {
        1, 2, 4 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        else -> RoundedCornerShape(0.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(backgroundStyleColors)
            .clickable { onBarClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(72.dp)
                .background(Color(0xFF141518)),
            contentAlignment = Alignment.Center
        ) {
            if (artworkBitmap != null) {
                Image(
                    bitmap = artworkBitmap.asImageBitmap(),
                    contentDescription = "Cover Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF499587)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = Color(0xFF9E9EA5),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        IconButton(
            onClick = onStopClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
fun TrackItemRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    style: Int
) {
    val context = LocalContext.current
    val artworkBitmap = rememberTrackArtwork(context, track.uriString)

    val itemShape = when (style) {
        1, 2, 4 -> RoundedCornerShape(12.dp)
        else -> RoundedCornerShape(0.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (style in listOf(1, 2, 4)) 8.dp else 0.dp)
            .clip(itemShape)
            .clickable { onClick() }
            .background(Color.White)
            .testTag("track_item_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE5E5EA)),
                contentAlignment = Alignment.Center
            ) {
                if (artworkBitmap != null) {
                    Image(
                        bitmap = artworkBitmap.asImageBitmap(),
                        contentDescription = "Cover Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF499587)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(16.dp)
                            .background(Color(0x99000000), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        LineEqualizerGlowingSmall()
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.fileName,
                    color = if (isCurrent) Color(0xFF118270) else Color(0xFF1C1C1E),
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.getFormattedFileSize(),
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Text(
                        text = track.getFormattedDate(),
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        HorizontalDivider(
            color = Color(0xFFEEEEEE),
            thickness = 1.dp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
fun LineEqualizerGlowingSmall() {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_small")
    
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b3"
    )

    Row(
        modifier = Modifier.size(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight(bar1Height)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight(bar2Height)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight(bar3Height)
                .background(Color.White)
        )
    }
}

@Composable
fun LineEqualizerGlowing() {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    
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
            animation = tween(550, easing = LinearEasing),
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

@Composable
fun rememberTrackArtwork(context: Context, uriString: String?): Bitmap? {
    if (uriString == null) return null
    var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uriString))
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                }
            } catch (e: Exception) {
                // Ignore failure
            } finally {
                retriever.release()
            }
        }
    }
    return bitmap
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return String.format("%02d:%02d", min, sec)
}
