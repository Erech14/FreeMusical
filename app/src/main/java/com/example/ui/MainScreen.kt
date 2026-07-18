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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()

    val isDark = when (appTheme) {
        0 -> true
        1 -> false
        else -> isSystemInDarkTheme()
    }

    val contentColor = if (isDark) Color.White else Color.Black
    val secondaryContentColor = if (isDark) Color.LightGray else Color(0xFF111115)

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val defaultPlaylistId by viewModel.defaultPlaylistId.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isPlayerDetailedOpened by remember { mutableStateOf(false) }

    var showPlaylistNameDialog by remember { mutableStateOf(false) }
    var pendingPlaylistUri by remember { mutableStateOf<Uri?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    
    var trackToUpload by remember { mutableStateOf<Track?>(null) }
    var uploadTitle by remember { mutableStateOf("") }
    var uploadArtists by remember { mutableStateOf("") }
    var uploadForRussia by remember { mutableStateOf("yes") }
    var uploadUnder18 by remember { mutableStateOf("no") }

    val playlistCreatorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) { e.printStackTrace() }
            
            pendingPlaylistUri = uri
            showPlaylistNameDialog = true
        }
    }

    // Launcher for general directory picker
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

    // Identify if current folder is the "Главный" / "Main" playlist
    val isCurrentMainPlaylist = remember(playlists, selectedFolderUri) {
        playlists.any { (it.name == "Главный" || it.name == "Main" || it.name == "Главный :3") && it.uri == selectedFolderUri }
    }

    if (showPlaylistNameDialog) {
        AlertDialog(
            onDismissRequest = {
                showPlaylistNameDialog = false
                pendingPlaylistUri = null
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank() && pendingPlaylistUri != null) {
                        viewModel.addPlaylist(newPlaylistName, pendingPlaylistUri!!)
                    }
                    showPlaylistNameDialog = false
                    pendingPlaylistUri = null
                }) {
                    Text(Strings.get("create", language), color = Color(0xFF00F5D4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPlaylistNameDialog = false
                    pendingPlaylistUri = null
                }) {
                    Text(Strings.get("cancel", language), color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                }
            },
            containerColor = Color(0xFF1C1D22),
            title = { Text(Strings.get("playlist_name", language), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text(Strings.get("enter_name", language), color = Color.Gray) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        focusedIndicatorColor = Color(0xFF118270),
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            },
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
        )
    }

    if (trackToUpload != null) {
        AlertDialog(
            onDismissRequest = { trackToUpload = null },
            confirmButton = {
                TextButton(onClick = {
                    trackToUpload?.let {
                        viewModel.uploadLocalTrack(it, uploadArtists, uploadTitle, uploadForRussia, uploadUnder18)
                    }
                    trackToUpload = null
                }) {
                    Text("Upload", color = Color(0xFF00F5D4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToUpload = null }) {
                    Text(Strings.get("cancel", language), color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                }
            },
            containerColor = Color(0xFF1C1D22),
            title = { Text("Upload Track", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextField(
                        value = uploadTitle,
                        onValueChange = { uploadTitle = it },
                        placeholder = { Text("Title", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color.White.copy(alpha = 0.08f), unfocusedContainerColor = Color.White.copy(alpha = 0.08f))
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = uploadArtists,
                        onValueChange = { uploadArtists = it },
                        placeholder = { Text("Artists", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color.White.copy(alpha = 0.08f), unfocusedContainerColor = Color.White.copy(alpha = 0.08f))
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uploadForRussia == "yes", onCheckedChange = { uploadForRussia = if (it) "yes" else "no" })
                        Text("For Russia", color = Color.White)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uploadUnder18 == "no", onCheckedChange = { uploadUnder18 = if (it) "no" else "yes" })
                        Text("18+", color = Color.White)
                    }
                }
            },
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
        )
    }

    // Deep glowing sea-violet backdrop gradient for Dark, pure white for Light (high contrast)
    val backgroundStyleColors = if (isDark) {
        listOf(Color(0xFF171329), Color(0xFF0B2B28))
    } else {
        listOf(Color.White, Color.White)
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(backgroundStyleColors))) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .blur(if (isPlayerDetailedOpened) 16.dp else 0.dp),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (selectedTab) {
                                    0 -> Icons.Default.MusicNote
                                    1 -> Icons.Default.QueueMusic
                                    2 -> Icons.Default.Settings
                                    else -> Icons.Default.CloudDownload
                                },
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF00F5D4) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = when (selectedTab) {
                                    0 -> Strings.get("app_name", language)
                                    1 -> Strings.get("tab_playlists", language)
                                    2 -> Strings.get("tab_settings", language)
                                    else -> Strings.get("tab_network", language)
                                },
                                color = contentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        }
                    },
                    actions = {
                        // Display reset directory ONLY on Tab 0 (Главная) AND ONLY if NOT current Main playlist
                        if (selectedTab == 0 && isPermissionGranted && selectedFolderUri != null && !isCurrentMainPlaylist) {
                            IconButton(
                                onClick = { viewModel.changeFolder() },
                                modifier = Modifier.testTag("change_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = Strings.get("change_folder", language),
                                    tint = contentColor
                                )
                            }
                        }
                        
                        // Add playlist button directly in the TopBar for the Playlists Tab
                        if (selectedTab == 1 && isPermissionGranted) {
                            IconButton(
                                onClick = {
                                    newPlaylistName = ""
                                    playlistCreatorLauncher.launch(null)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = Strings.get("create_playlist", language),
                                    tint = contentColor
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.White,
                        titleContentColor = contentColor
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = Color.Transparent
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
                        // Smooth Sliding Telegram Tab Transitions
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                        slideOutHorizontally { width -> -width } + fadeOut()
                                    )
                                } else {
                                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                        slideOutHorizontally { width -> width } + fadeOut()
                                    )
                                }
                            },
                            label = "tab_transition",
                            modifier = Modifier.fillMaxSize()
                        ) { tab ->
                            when (tab) {
                                0 -> {
                                    // ТАБ 0: ГЛАВНАЯ (Tracks list with search)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Transparent)
                                    ) {
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

                                        // Search and Sync row with glass style
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
                                                        color = Color.LightGray,
                                                        fontSize = 14.sp
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Search,
                                                        contentDescription = Strings.get("search", language),
                                                        tint = Color(0xFF00F5D4)
                                                    )
                                                },
                                                trailingIcon = {
                                                    if (searchQuery.isNotEmpty()) {
                                                        IconButton(onClick = { searchQuery = "" }) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Clear",
                                                                tint = Color.White
                                                            )
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(52.dp)
                                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(26.dp))
                                                    .testTag("search_input"),
                                                shape = RoundedCornerShape(26.dp),
                                                colors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                                                    disabledContainerColor = Color.Transparent,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
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
                                                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                                    .testTag("scan_button"),
                                                enabled = !isScanning
                                            ) {
                                                if (isScanning) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        color = Color(0xFF00F5D4),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Rescan",
                                                        tint = Color(0xFF00F5D4)
                                                    )
                                                }
                                            }
                                        }

                                        // Play actions row
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                                        ) {
                                            Button(
                                                onClick = { viewModel.playSequential() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF118270)),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(Strings.get("play_in_order", language), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = { viewModel.playRandomly() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF118270)),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(Strings.get("play_shuffle", language), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        if (isScanning && tracks.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    CircularProgressIndicator(color = Color(0xFF00F5D4))
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text(
                                                        text = Strings.get("scanning", language),
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.SemiBold
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
                                                        tint = Color.LightGray,
                                                        modifier = Modifier.size(64.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text(
                                                        text = Strings.get("library_empty", language),
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        fontSize = 18.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = Strings.get("add_folder", language),
                                                        color = Color.LightGray,
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
                                                contentPadding = PaddingValues(bottom = 160.dp) // Large space to float custom navigation
                                            ) {
                                                items(filteredList, key = { it.uriString }) { track ->
                                                    TrackItemRow(
                                                        track = track,
                                                        isCurrent = currentTrack?.uriString == track.uriString,
                                                        isPlaying = isPlaying && currentTrack?.uriString == track.uriString,
                                                        onClick = { viewModel.playTrack(track) },
                                                        onUploadClick = {
                                                            uploadTitle = track.title
                                                            uploadArtists = track.artist
                                                            trackToUpload = track
                                                        },
                                                        style = 2 // Lock to Glassmorphism
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    // ТАБ 1: ПЛЕЙЛИСТЫ (Playlists screen)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = Strings.get("playlists", language),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Button(
                                            onClick = {
                                                newPlaylistName = ""
                                                playlistCreatorLauncher.launch(null)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF118270)),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(Strings.get("create_playlist", language), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        LazyColumn(
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(bottom = 160.dp)
                                        ) {
                                            items(playlists) { playlist ->
                                                val isMainPlaylist = playlist.name == "Главный" || playlist.name == "Main" || playlist.name == "Главный :3"
                                                val isActive = selectedFolderUri == playlist.uri

                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp)
                                                        .border(
                                                            width = if (isActive) 2.dp else 1.dp,
                                                            color = if (isActive) Color(0xFF00F5D4) else Color.White.copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(16.dp)
                                                        )
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .clickable {
                                                            viewModel.selectFolder(Uri.parse(playlist.uri))
                                                            selectedTab = 0 // Transition beautifully back to tracks screen
                                                        },
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Folder,
                                                            contentDescription = null,
                                                            tint = if (isActive) Color(0xFF00F5D4) else Color.LightGray,
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(16.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = playlist.name,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 16.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = playlist.uri,
                                                                fontSize = 11.sp,
                                                                color = Color.LightGray,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        
                                                        // Star icon for default playlist
                                                        IconButton(onClick = {
                                                            if (defaultPlaylistId == playlist.id) viewModel.setDefaultPlaylist(null)
                                                            else viewModel.setDefaultPlaylist(playlist.id)
                                                        }) {
                                                            Icon(
                                                                imageVector = if (defaultPlaylistId == playlist.id) Icons.Default.Star else Icons.Outlined.StarOutline,
                                                                contentDescription = Strings.get("default", language),
                                                                tint = if (defaultPlaylistId == playlist.id) Color(0xFFFFCC00) else Color.Gray,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        }

                                                        // HIDE delete button completely on the Playlists Tab for "Главный" / "Main" playlist
                                                        if (!isMainPlaylist) {
                                                            IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Delete,
                                                                    contentDescription = Strings.get("delete", language),
                                                                    tint = Color(0xFFFF453A),
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    // ТАБ 2: НАСТРОЙКИ (Settings screen)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = Strings.get("appearance", language),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = contentColor,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )

                                        // THEME SELECTOR DROPDOWN
                                        val currentThemeVal by viewModel.appTheme.collectAsStateWithLifecycle()
                                        var themeExpanded by remember { mutableStateOf(false) }
                                        val themeOptions = listOf(0, 1, 2)
                                        val currentThemeLabel = when (currentThemeVal) {
                                            0 -> Strings.get("theme_dark", language)
                                            1 -> Strings.get("theme_light", language)
                                            else -> Strings.get("theme_system", language)
                                        }

                                        ExposedDropdownMenuBox(
                                            expanded = themeExpanded,
                                            onExpandedChange = { themeExpanded = it },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            TextField(
                                                value = currentThemeLabel,
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                                                colors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                    .menuAnchor()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = themeExpanded,
                                                onDismissRequest = { themeExpanded = false },
                                                modifier = Modifier.background(Color(0xFF1E1F24))
                                            ) {
                                                themeOptions.forEach { themeId ->
                                                    val optionLabel = when (themeId) {
                                                        0 -> Strings.get("theme_dark", language)
                                                        1 -> Strings.get("theme_light", language)
                                                        else -> Strings.get("theme_system", language)
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text(optionLabel, color = Color.White, fontWeight = FontWeight.Bold) },
                                                        onClick = {
                                                            viewModel.setAppTheme(themeId)
                                                            themeExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // LANGUAGE SELECTOR
                                        Text(
                                            text = Strings.get("language", language),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = contentColor,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        var langExpanded by remember { mutableStateOf(false) }
                                        val languages = listOf("Russian", "Cute Russian", "English")
                                        ExposedDropdownMenuBox(
                                            expanded = langExpanded,
                                            onExpandedChange = { langExpanded = it },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            TextField(
                                                value = language.takeIf { languages.contains(it) } ?: "Russian",
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                                                colors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                    .menuAnchor()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = langExpanded,
                                                onDismissRequest = { langExpanded = false },
                                                modifier = Modifier.background(Color(0xFF1E1F24))
                                            ) {
                                                languages.forEach { selectionOption ->
                                                    DropdownMenuItem(
                                                        text = { Text(selectionOption, color = Color.White, fontWeight = FontWeight.Bold) },
                                                        onClick = {
                                                            viewModel.setAppLanguage(selectionOption)
                                                            langExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))

                                        // API TOKEN CONFIGURATION
                                        Text(
                                            text = Strings.get("api_token_title", language),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = contentColor,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        val apiToken by viewModel.apiToken.collectAsStateWithLifecycle()
                                        var tokenInput by remember(apiToken) { mutableStateOf(apiToken) }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextField(
                                                value = tokenInput,
                                                onValueChange = { tokenInput = it },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                                colors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                singleLine = true
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { viewModel.setApiToken(tokenInput) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF118270))
                                            ) {
                                                Text(Strings.get("save", language), fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))

                                        // SPECIAL MANAGE CHANNELS SECTION
                                        // "Кроме как из меню настроек" - We can delete ANY playlist (including "Главный" / "Main") from here!
                                        Text(
                                            text = "Управление плейлистами (Настройки)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = contentColor,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        LazyColumn(
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(bottom = 160.dp)
                                        ) {
                                            items(playlists) { playlist ->
                                                val isMain = playlist.name == "Главный" || playlist.name == "Main" || playlist.name == "Главный :3"
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                                if (isMain) {
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text("★ Главный", color = Color(0xFF00F5D4), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                            Text(playlist.uri, color = Color.LightGray, fontSize = 10.sp, maxLines = 1)
                                                        }
                                                        
                                                        // Deleting "Главный" is fully allowed in this settings list context!
                                                        IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = Strings.get("delete", language),
                                                                tint = Color(0xFFFF453A),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    NetworkScreen(viewModel = viewModel, language = language, isDark = isDark)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating custom bottom navigation & player bar stacked vertically
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Unified Custom Telegram-Style Floating Capsule
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(32.dp))
                        .padding(bottom = 8.dp)
                ) {
                    // 1. Mini-player inside the capsule
                    if (isPermissionGranted && selectedFolderUri != null && currentTrack != null) {
                        BottomPlayBar(
                            track = currentTrack!!,
                            isPlaying = isPlaying,
                            onPlayPauseClick = { viewModel.togglePlayPause() },
                            onStopClick = { viewModel.stopPlayback() },
                            onBarClick = { isPlayerDetailedOpened = true },
                            style = 3 // Transparent style for merged capsule
                        )
                        Divider(
                            color = Color.White.copy(alpha = 0.1f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // 2. Navigation Items
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabLabels = listOf(
                            Strings.get("tab_main", language) to Icons.Default.MusicNote,
                            Strings.get("tab_playlists", language) to Icons.Default.QueueMusic,
                            Strings.get("tab_settings", language) to Icons.Default.Settings,
                            Strings.get("tab_network", language) to Icons.Default.CloudDownload
                        )

                        tabLabels.forEachIndexed { index, (label, icon) ->
                            val isSelected = selectedTab == index
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = index }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Highlight pill container around active icon
                                Box(
                                    modifier = Modifier
                                        .height(32.dp)
                                        .width(64.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) Color(0xFF118270).copy(alpha = 0.35f) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) Color(0xFF00F5D4) else Color(0xFF8E8E93),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFF8E8E93),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
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
                    .background(if (isDark) Color(0xFF0F1015) else Color.White)
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
                                tint = if (isDark) SoftWhite else Color(0xFF1C1B1F),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = Strings.get("now_playing", language),
                            color = if (isDark) SoftWhite else Color(0xFF1C1B1F),
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
                        language = language,
                        style = 2, // Lock to Glassmorphism
                        isDark = isDark
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
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    color = MaterialTheme.colorScheme.onBackground
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
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    val circleColor = MaterialTheme.colorScheme.surfaceVariant
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
                            color = circleColor,
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
                    color = MaterialTheme.colorScheme.onBackground,
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
    language: String,
    style: Int,
    isDark: Boolean
) {
    val context = LocalContext.current
    var artworkBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(currentTrack?.uriString) {
        val trackUriStr = currentTrack?.uriString
        if (trackUriStr != null) {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    val uri = Uri.parse(trackUriStr)
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        val artBytes = retriever.embeddedPicture
                        if (artBytes != null) {
                            val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                            artworkBitmap = bitmap
                        } else {
                            artworkBitmap = null
                        }
                    }
                } catch (e: Exception) {
                    artworkBitmap = null
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        } else {
            artworkBitmap = null
        }
    }

    val deckShape = when (style) {
        1 -> RoundedCornerShape(28.dp) // Material Design
        2 -> RoundedCornerShape(20.dp) // Глассморфизм
        else -> RoundedCornerShape(24.dp) // Стандартный
    }

    val cardModifier = if (style == 2) {
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.35f), deckShape)
    } else {
        Modifier.fillMaxWidth()
    }

    val cardColor = when (style) {
        1 -> MaterialTheme.colorScheme.surfaceVariant
        2 -> if (isDark) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = deckShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (style == 2) 0.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (artworkBitmap != null) {
                Image(
                    bitmap = artworkBitmap!!.asImageBitmap(),
                    contentDescription = "Обложка трека",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(8.dp, shape = RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                RotatingVinyl(isPlaying = isPlaying)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = currentTrack?.title ?: Strings.get("deck_empty", language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) SoftWhite else Color(0xFF1C1B1F),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentTrack?.artist ?: Strings.get("choose_audio", language),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) MutedText else Color(0xFF49454F),
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
                    color = if (isDark) MutedText else Color(0xFF49454F),
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
                        inactiveTrackColor = if (isDark) DarkGreyDeck else Color.Black.copy(alpha = 0.25f),
                        thumbColor = PrimaryAmber
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatDuration(duration),
                    fontSize = 11.sp,
                    color = if (isDark) MutedText else Color(0xFF49454F),
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
                    val tint = if (isShuffleEnabled) PrimaryAmber else (if (isDark) MutedText else Color.Black)
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
                        tint = if (isDark) SoftWhite else Color.Black,
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
                        tint = if (isDark) SoftWhite else Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(if (isDark) DarkGreyDeck else Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isShuffleEnabled) "RAND" else "SEQ",
                        fontWeight = FontWeight.Bold,
                        color = if (isShuffleEnabled) PrimaryAmber else (if (isDark) MutedText else Color.Black),
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
        1 -> MaterialTheme.colorScheme.surfaceVariant
        2 -> Color.Black.copy(alpha = 0.6f) // Glassmorphism translucent
        3 -> Color.Transparent // Merged capsule style
        else -> MaterialTheme.colorScheme.surface // Standard
    }

    val shape = when (style) {
        1 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        2 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        3 -> RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        else -> RoundedCornerShape(0.dp)
    }

    val barModifier = if (style == 2) {
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
            .clip(shape)
            .background(backgroundStyleColors)
            .clickable { onBarClick() }
    } else if (style == 3) {
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(backgroundStyleColors)
            .clickable { onBarClick() }
    } else {
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(backgroundStyleColors)
            .clickable { onBarClick() }
    }

    Row(
        modifier = barModifier,
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
    onUploadClick: () -> Unit = {},
    style: Int
) {
    val context = LocalContext.current
    val artworkBitmap = rememberTrackArtwork(context, track.uriString)

    val itemShape = when (style) {
        1 -> RoundedCornerShape(12.dp)
        2 -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(0.dp)
    }

    val itemBackgroundColor = when (style) {
        1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        2 -> Color.Black.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val itemModifier = if (style == 2) {
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .border(1.dp, Color.White.copy(alpha = 0.2f), itemShape)
            .clip(itemShape)
            .clickable { onClick() }
            .background(itemBackgroundColor)
            .testTag("track_item_card")
    } else {
        Modifier
            .fillMaxWidth()
            .padding(if (style == 1) 8.dp else 0.dp)
            .clip(itemShape)
            .clickable { onClick() }
            .background(itemBackgroundColor)
            .testTag("track_item_card")
    }

    Column(
        modifier = itemModifier
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
                    color = if (isCurrent) Color(0xFF118270) else MaterialTheme.colorScheme.onBackground,
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
            
            IconButton(onClick = onUploadClick) {
                Icon(Icons.Default.Upload, contentDescription = "Upload", tint = Color(0xFF00F5D4))
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
