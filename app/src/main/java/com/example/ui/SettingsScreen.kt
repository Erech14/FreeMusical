package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onClose: () -> Unit
) {
    val theme by viewModel.appTheme.collectAsStateWithLifecycle()
    val style by viewModel.appStyle.collectAsStateWithLifecycle()
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val defaultPlaylistId by viewModel.defaultPlaylistId.collectAsStateWithLifecycle()

    var showPlaylistNameDialog by remember { mutableStateOf(false) }
    var pendingPlaylistUri by remember { mutableStateOf<Uri?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    val context = LocalContext.current
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            
            pendingPlaylistUri = uri
            showPlaylistNameDialog = true
        }
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
                    Text(Strings.get("create", language))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPlaylistNameDialog = false
                    pendingPlaylistUri = null
                }) {
                    Text(Strings.get("cancel", language))
                }
            },
            title = { Text(Strings.get("playlist_name", language)) },
            text = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text(Strings.get("enter_name", language)) }
                )
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.get("settings", language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = Strings.get("close", language))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground, navigationIconContentColor = MaterialTheme.colorScheme.onBackground)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Playlists Section
            item {
                Text(Strings.get("playlists", language), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { 
                    newPlaylistName = ""
                    folderPickerLauncher.launch(null) 
                }) {
                    Text(Strings.get("create_playlist", language))
                }
            }

            items(playlists) { playlist ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.selectFolder(Uri.parse(playlist.uri))
                        onClose()
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F7))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(playlist.uri, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                        }
                        IconButton(onClick = { 
                            if (defaultPlaylistId == playlist.id) viewModel.setDefaultPlaylist(null)
                            else viewModel.setDefaultPlaylist(playlist.id)
                        }) {
                            Icon(
                                imageVector = if (defaultPlaylistId == playlist.id) Icons.Default.Star else Icons.Outlined.StarOutline,
                                contentDescription = Strings.get("default", language),
                                tint = if (defaultPlaylistId == playlist.id) Color(0xFFFFCC00) else Color.Gray
                            )
                        }
                        IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = Strings.get("delete", language), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Appearance Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(Strings.get("appearance", language), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                
                // Theme Dropdown
                var themeExpanded by remember { mutableStateOf(false) }
                val themes = when (language) {
                    "English" -> listOf("Dark", "Light", "Adaptive")
                    "Cute Russian" -> listOf("Темненькая", "Светленькая", "Адаптивная :3")
                    else -> listOf("Тёмная", "Светлая", "Адаптивная")
                }
                ExposedDropdownMenuBox(
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = it },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    TextField(
                        value = themes.getOrElse(theme) { themes[2] },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(Strings.get("theme", language)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        themes.forEachIndexed { index, selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    viewModel.setAppTheme(index)
                                    themeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Style Dropdown
                var styleExpanded by remember { mutableStateOf(false) }
                val styles = listOf("Стандартный", "Material Design", "Глассморфизм")
                ExposedDropdownMenuBox(
                    expanded = styleExpanded,
                    onExpandedChange = { styleExpanded = it },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    TextField(
                        value = styles.getOrElse(style) { styles[0] },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(Strings.get("style", language)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = styleExpanded,
                        onDismissRequest = { styleExpanded = false }
                    ) {
                        styles.forEachIndexed { index, selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    viewModel.setAppStyle(index)
                                    styleExpanded = false
                                }
                            )
                        }
                    }
                }

                // Language Dropdown
                var langExpanded by remember { mutableStateOf(false) }
                val languages = listOf("Russian", "Cute Russian", "English")
                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    TextField(
                        value = language.takeIf { languages.contains(it) } ?: "Russian",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(Strings.get("language", language)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = langExpanded,
                        onDismissRequest = { langExpanded = false }
                    ) {
                        languages.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    viewModel.setAppLanguage(selectionOption)
                                    langExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
