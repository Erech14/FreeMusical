package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Refresh
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(viewModel: MusicViewModel, language: String, isDark: Boolean) {
    val contentColor = if (isDark) Color.White else Color.Black
    val apiToken by viewModel.apiToken.collectAsStateWithLifecycle()
    val apiTracks by viewModel.apiTracks.collectAsStateWithLifecycle()
    val isApiLoading by viewModel.isApiLoading.collectAsStateWithLifecycle()
    
    var tokenInput by remember { mutableStateOf(apiToken) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "API Token",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = contentColor,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                modifier = Modifier.weight(1f).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.setApiToken(tokenInput) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF118270))) {
                Text("Save")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Server Tracks", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = contentColor)
            IconButton(onClick = { viewModel.fetchApiTracks() }, enabled = !isApiLoading && apiToken.isNotEmpty()) {
                if (isApiLoading) {
                    CircularProgressIndicator(color = Color(0xFF00F5D4), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF00F5D4))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            items(apiTracks) { track ->
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
                            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(track.artists.joinToString(", ") { it.name }, color = Color.LightGray, fontSize = 14.sp)
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
