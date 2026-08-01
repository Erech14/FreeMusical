package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.LogCategory
import com.example.ui.LogEntry
import com.example.ui.LogLevel
import com.example.ui.Logger
import com.example.ui.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsSubmenuScreen(
    language: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(LogCategory.APP) }
    var searchQuery by remember { mutableStateOf("") }
    
    val appLogs by Logger.appLogs.collectAsStateWithLifecycle()
    val apiLogs by Logger.apiLogs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val currentLogList = remember(selectedCategory, appLogs, apiLogs, searchQuery) {
        val baseList = if (selectedCategory == LogCategory.APP) appLogs else apiLogs
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter { 
                it.message.contains(searchQuery, ignoreCase = true) || 
                it.tag.contains(searchQuery, ignoreCase = true) ||
                it.level.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121318))
    ) {
        // TOP APP BAR
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = Strings.get("logs_title", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = Strings.get("logs_subtitle", language),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = Strings.get("close", language),
                        tint = Color.White
                    )
                }
            },
            actions = {
                // COPY ALL LOGS
                IconButton(
                    onClick = {
                        if (currentLogList.isNotEmpty()) {
                            val textToCopy = currentLogList.joinToString("\n") { it.toFormattedString() }
                            clipboardManager.setText(AnnotatedString(textToCopy))
                            Toast.makeText(
                                context,
                                Strings.get("logs_copied", language),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = Strings.get("logs_copy", language),
                        tint = Color(0xFF00F5D4)
                    )
                }
                
                // CLEAR LOGS FOR CURRENT CATEGORY
                IconButton(
                    onClick = {
                        if (selectedCategory == LogCategory.APP) {
                            Logger.clearAppLogs()
                        } else {
                            Logger.clearApiLogs()
                        }
                        Toast.makeText(
                            context,
                            "Cleared ${if (selectedCategory == LogCategory.APP) "App" else "API"} logs",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = Strings.get("logs_clear", language),
                        tint = Color(0xFFFF453A)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF191A20)
            )
        )

        // CATEGORY SEGMENTED TABS (App Logs vs API Logs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val isAppSelected = selectedCategory == LogCategory.APP
            val isApiSelected = selectedCategory == LogCategory.API

            // APP LOGS TAB
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isAppSelected) Color(0xFF118270) else Color.Transparent)
                    .clickable { selectedCategory = LogCategory.APP }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = if (isAppSelected) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${Strings.get("logs_app", language)} (${appLogs.size})",
                        color = if (isAppSelected) Color.White else Color.Gray,
                        fontWeight = if (isAppSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // API LOGS TAB
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isApiSelected) Color(0xFF5A0B9C) else Color.Transparent)
                    .clickable { selectedCategory = LogCategory.API }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Api,
                        contentDescription = null,
                        tint = if (isApiSelected) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${Strings.get("logs_api", language)} (${apiLogs.size})",
                        color = if (isApiSelected) Color.White else Color.Gray,
                        fontWeight = if (isApiSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // SEARCH BAR
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(Strings.get("logs_search_placeholder", language), color = Color.Gray, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00F5D4).copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // LOG ENTRIES LIST
        if (currentLogList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (selectedCategory == LogCategory.APP) Icons.Default.Dvr else Icons.Default.Http,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = Strings.get("logs_empty", language),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(currentLogList.reversed(), key = { it.id }) { logEntry ->
                        LogEntryItem(logEntry = logEntry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(
    logEntry: LogEntry
) {
    val levelColor = when (logEntry.level) {
        LogLevel.ERROR -> Color(0xFFFF453A)
        LogLevel.WARN -> Color(0xFFFF9F0A)
        LogLevel.INFO -> Color(0xFF30D158)
        LogLevel.DEBUG -> Color(0xFFBF5AF2)
    }

    val containerBg = when (logEntry.level) {
        LogLevel.ERROR -> Color(0xFF2C1014)
        LogLevel.WARN -> Color(0xFF2B200D)
        else -> Color.Black.copy(alpha = 0.45f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // LEVEL BADGE
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(levelColor.copy(alpha = 0.2f))
                        .border(1.dp, levelColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = logEntry.level.name,
                        color = levelColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // TAG
                Text(
                    text = "[${logEntry.tag}]",
                    color = Color(0xFF00F5D4),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // TIMESTAMP
            Text(
                text = logEntry.timestamp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // MESSAGE CONTENT
        Text(
            text = logEntry.message,
            color = if (logEntry.level == LogLevel.ERROR) Color(0xFFFFB3B0) else Color.White.copy(alpha = 0.9f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}
