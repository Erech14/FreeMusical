package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(viewModel: MusicViewModel, language: String, isDark: Boolean) {
    val contentColor = if (isDark) Color.White else Color.Black
    val apiToken by viewModel.apiToken.collectAsStateWithLifecycle()
    val apiTracks by viewModel.apiTracks.collectAsStateWithLifecycle()
    val isApiLoading by viewModel.isApiLoading.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter options
    var showFilterDialog by remember { mutableStateOf(false) }
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

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) {
                    Text(Strings.get("close", language), color = Color(0xFF00F5D4))
                }
            },
            containerColor = Color(0xFF1C1D22),
            title = { Text(Strings.get("filter", language), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(Strings.get("artist", language), color = Color.LightGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        var artistDropdown by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { artistDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Text(selectedArtist ?: Strings.get("all", language))
                        }
                        DropdownMenu(
                            expanded = artistDropdown,
                            onDismissRequest = { artistDropdown = false },
                            modifier = Modifier.background(Color(0xFF1E1F24)).heightIn(max = 200.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(Strings.get("all", language), color = Color.White) },
                                onClick = { selectedArtist = null; artistDropdown = false }
                            )
                            allArtists.forEach { artist ->
                                DropdownMenuItem(
                                    text = { Text(artist, color = Color.White) },
                                    onClick = { selectedArtist = artist; artistDropdown = false }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(Strings.get("uploader", language), color = Color.LightGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        var uploaderDropdown by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { uploaderDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Text(selectedUploader ?: Strings.get("all", language))
                        }
                        DropdownMenu(
                            expanded = uploaderDropdown,
                            onDismissRequest = { uploaderDropdown = false },
                            modifier = Modifier.background(Color(0xFF1E1F24)).heightIn(max = 200.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(Strings.get("all", language), color = Color.White) },
                                onClick = { selectedUploader = null; uploaderDropdown = false }
                            )
                            allUploaders.forEach { uploader ->
                                DropdownMenuItem(
                                    text = { Text(uploader, color = Color.White) },
                                    onClick = { selectedUploader = uploader; uploaderDropdown = false }
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
            IconButton(onClick = { showFilterDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Filter", tint = if (selectedArtist != null || selectedUploader != null) Color(0xFF00F5D4) else Color.Gray)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            items(filteredTracks) { track ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
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
