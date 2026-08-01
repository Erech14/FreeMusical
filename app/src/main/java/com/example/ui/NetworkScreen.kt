package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.MusicViewModel

private enum class FilterSubMenu {
    MAIN,
    ARTIST,
    UPLOADER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(viewModel: MusicViewModel, language: String, isDark: Boolean) {
    val contentColor = if (isDark) Color.White else Color.Black
    val apiToken by viewModel.apiToken.collectAsStateWithLifecycle()
    val apiTracks by viewModel.apiTracks.collectAsStateWithLifecycle()
    val isApiLoading by viewModel.isApiLoading.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    // Filter states
    var showFilterSheet by remember { mutableStateOf(false) }
    var subMenuState by remember { mutableStateOf(FilterSubMenu.MAIN) }
    var subMenuSearchQuery by remember { mutableStateOf("") }

    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedUploader by remember { mutableStateOf<String?>(null) }

    val allArtists = remember(apiTracks) {
        apiTracks.flatMap { track -> track.artists.map { it.name } }.distinct().sorted()
    }

    val allUploaders = remember(apiTracks) {
        apiTracks.mapNotNull { track ->
            track.uploader?.let { uploader ->
                listOfNotNull(uploader.firstName, uploader.lastName).joinToString(" ").takeIf { it.isNotBlank() } ?: uploader.username
            }
        }.distinct().sorted()
    }

    LaunchedEffect(apiToken) {
        if (apiToken.isNotEmpty()) {
            viewModel.fetchApiTracks()
        }
    }

    val filteredTracks = remember(apiTracks, searchQuery, selectedArtist, selectedUploader) {
        var tracks = apiTracks
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            tracks = tracks.filter { track ->
                track.title.lowercase().contains(query) ||
                track.artists.any { it.name.lowercase().contains(query) }
            }
        }
        if (selectedArtist != null) {
            tracks = tracks.filter { track -> track.artists.any { it.name == selectedArtist } }
        }
        if (selectedUploader != null) {
            tracks = tracks.filter { track ->
                val uploaderName = track.uploader?.let {
                    listOfNotNull(it.firstName, it.lastName).joinToString(" ").takeIf { name -> name.isNotBlank() } ?: it.username
                }
                uploaderName == selectedUploader
            }
        }
        tracks
    }

    // Filter Dialog / Bottom Sheet Overlay
    if (showFilterSheet) {
        AlertDialog(
            onDismissRequest = {
                showFilterSheet = false
                subMenuState = FilterSubMenu.MAIN
                subMenuSearchQuery = ""
            },
            confirmButton = {},
            containerColor = Color(0xFF1C1D22),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                ) {
                    when (subMenuState) {
                        FilterSubMenu.MAIN -> {
                            // Main Filter Overview Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Strings.get("filter", language),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (selectedArtist != null || selectedUploader != null) {
                                        TextButton(onClick = {
                                            selectedArtist = null
                                            selectedUploader = null
                                        }) {
                                            Text(
                                                text = Strings.get("reset", language),
                                                color = Color(0xFF00F5D4),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        showFilterSheet = false
                                        subMenuState = FilterSubMenu.MAIN
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Option Row 1: Artist
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        subMenuSearchQuery = ""
                                        subMenuState = FilterSubMenu.ARTIST
                                    },
                                color = Color.Black.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = Strings.get("artist", language),
                                            color = Color.Gray,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                         Text(
                                            text = selectedArtist ?: Strings.get("all", language),
                                            color = if (selectedArtist != null) Color(0xFF00F5D4) else Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Option Row 2: Uploader
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        subMenuSearchQuery = ""
                                        subMenuState = FilterSubMenu.UPLOADER
                                    },
                                color = Color.Black.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = Strings.get("uploader", language),
                                            color = Color.Gray,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = selectedUploader ?: Strings.get("all", language),
                                            color = if (selectedUploader != null) Color(0xFF00F5D4) else Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.height(24.dp))

                            // Bottom Close/Apply Button
                            Button(
                                onClick = {
                                    showFilterSheet = false
                                    subMenuState = FilterSubMenu.MAIN
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5D4)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    text = Strings.get("apply", language),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        FilterSubMenu.ARTIST -> {
                            // Sub-menu for Artist selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { subMenuState = FilterSubMenu.MAIN }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Text(
                                    text = Strings.get("artist", language),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Search inside Sub-menu
                            TextField(
                                value = subMenuSearchQuery,
                                onValueChange = { subMenuSearchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                placeholder = { Text(Strings.get("search", language), color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                                trailingIcon = {
                                    if (subMenuSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { subMenuSearchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                                        }
                                    }
                                },
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

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = Strings.get("artists", language),
                                color = Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val filteredArtistsList = remember(allArtists, subMenuSearchQuery) {
                                if (subMenuSearchQuery.isBlank()) allArtists
                                else allArtists.filter { it.contains(subMenuSearchQuery, ignoreCase = true) }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                // "All" option
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedArtist = null
                                            }
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = Strings.get("all", language),
                                            color = if (selectedArtist == null) Color(0xFF00F5D4) else Color.White,
                                            fontWeight = if (selectedArtist == null) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 15.sp
                                        )
                                        RadioButton(
                                            selected = selectedArtist == null,
                                            onClick = { selectedArtist = null },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF00F5D4),
                                                unselectedColor = Color.Gray
                                            )
                                        )
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                }

                                items(filteredArtistsList) { artist ->
                                    val isSelected = selectedArtist == artist
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedArtist = artist
                                            }
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = artist,
                                            color = if (isSelected) Color(0xFF00F5D4) else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedArtist = artist },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF00F5D4),
                                                unselectedColor = Color.Gray
                                            )
                                        )
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { subMenuState = FilterSubMenu.MAIN },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5D4)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    text = Strings.get("close", language),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        FilterSubMenu.UPLOADER -> {
                            // Sub-menu for Uploader selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { subMenuState = FilterSubMenu.MAIN }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Text(
                                    text = Strings.get("uploader", language),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Search inside Sub-menu
                            TextField(
                                value = subMenuSearchQuery,
                                onValueChange = { subMenuSearchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                placeholder = { Text(Strings.get("search", language), color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                                trailingIcon = {
                                    if (subMenuSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { subMenuSearchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                                        }
                                    }
                                },
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

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = Strings.get("uploaders", language),
                                color = Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val filteredUploadersList = remember(allUploaders, subMenuSearchQuery) {
                                if (subMenuSearchQuery.isBlank()) allUploaders
                                else allUploaders.filter { it.contains(subMenuSearchQuery, ignoreCase = true) }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                // "All" option
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedUploader = null
                                            }
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = Strings.get("all", language),
                                            color = if (selectedUploader == null) Color(0xFF00F5D4) else Color.White,
                                            fontWeight = if (selectedUploader == null) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 15.sp
                                        )
                                        RadioButton(
                                            selected = selectedUploader == null,
                                            onClick = { selectedUploader = null },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF00F5D4),
                                                unselectedColor = Color.Gray
                                            )
                                        )
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                }

                                items(filteredUploadersList) { uploader ->
                                    val isSelected = selectedUploader == uploader
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedUploader = uploader
                                            }
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = uploader,
                                            color = if (isSelected) Color(0xFF00F5D4) else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedUploader = uploader },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF00F5D4),
                                                unselectedColor = Color.Gray
                                            )
                                        )
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { subMenuState = FilterSubMenu.MAIN },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5D4)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    text = Strings.get("close", language),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Strings.get("tab_network", language), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = contentColor)
            IconButton(onClick = { viewModel.fetchApiTracks() }, enabled = !isApiLoading && apiToken.isNotEmpty()) {
                if (isApiLoading) {
                    CircularProgressIndicator(color = Color(0xFF00F5D4), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF00F5D4))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                placeholder = { Text(Strings.get("search", language), color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
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
            IconButton(onClick = {
                subMenuState = FilterSubMenu.MAIN
                showFilterSheet = true
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Filter",
                    tint = if (selectedArtist != null || selectedUploader != null) Color(0xFF00F5D4) else Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            items(filteredTracks) { track ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val artistNames = track.artists.joinToString(", ") { it.name }
                            Text("$artistNames - ${track.title}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            val uploaderName = track.uploader?.let {
                                listOfNotNull(it.firstName, it.lastName).joinToString(" ").takeIf { name -> name.isNotBlank() } ?: it.username
                            } ?: Strings.get("artist_unknown", language)
                            Text("Uploaded by: $uploaderName", color = Color.LightGray, fontSize = 12.sp)
                        }
                        IconButton(onClick = { viewModel.downloadApiTrack(track) }) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Download", tint = Color(0xFF00F5D4))
                        }
                    }
                }
            }
        }
    }
}
