package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.LogEntry
import com.example.ui.LogLevel
import com.example.ui.Logger
import com.example.ui.Strings

private enum class LogsTab { APP, API, INFO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsSubmenuScreen(
    language: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(LogsTab.APP) }
    var searchQuery by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var verboseLogging by remember { mutableStateOf(Logger.isDebugEnabled()) }

    val appLogs by Logger.appLogs.collectAsStateWithLifecycle()
    val apiLogs by Logger.apiLogs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val currentLogList = remember(selectedTab, appLogs, apiLogs, searchQuery, levelFilter) {
        var baseList = when (selectedTab) {
            LogsTab.APP -> appLogs
            LogsTab.API -> apiLogs
            LogsTab.INFO -> emptyList()
        }
        if (levelFilter != null) {
            baseList = baseList.filter { it.level == levelFilter }
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.message.contains(searchQuery, ignoreCase = true) ||
                    it.tag.contains(searchQuery, ignoreCase = true) ||
                    it.thread.contains(searchQuery, ignoreCase = true) ||
                    it.level.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(Logger.exportText().toByteArray())
                }
                Logger.info("Логи экспортированы в $uri", tag = "UI")
                Toast.makeText(context, "Логи сохранены", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Logger.error("Не удалось сохранить логи", e, "UI")
                Toast.makeText(context, "Ошибка экспорта: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val systemInfoText = remember { Logger.systemInfo() }

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
                        text = "Сессия ${Logger.sessionId}",
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
                // COPY: актуальный список или сервисная информация
                IconButton(
                    onClick = {
                        val textToCopy = if (selectedTab == LogsTab.INFO) {
                            systemInfoText
                        } else {
                            currentLogList.joinToString("\n") { it.toFormattedString() }
                        }
                        clipboardManager.setText(AnnotatedString(textToCopy))
                        Toast.makeText(context, Strings.get("logs_copied", language), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = Strings.get("logs_copy", language),
                        tint = Color(0xFF00F5D4)
                    )
                }

                // SHARE: полный дамп с сервисной информацией
                IconButton(onClick = { shareLogs(context) }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Поделиться логами",
                        tint = Color(0xFF00F5D4)
                    )
                }

                // EXPORT: сохранить в файл
                IconButton(onClick = {
                    exportLauncher.launch("freemusical_logs_${Logger.sessionId}.txt")
                }) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = "Экспорт логов в файл",
                        tint = Color(0xFF00F5D4)
                    )
                }

                // CLEAR (для APP/API)
                if (selectedTab != LogsTab.INFO) {
                    IconButton(
                        onClick = {
                            if (selectedTab == LogsTab.APP) Logger.clearAppLogs() else Logger.clearApiLogs()
                            Toast.makeText(
                                context,
                                "Cleared ${if (selectedTab == LogsTab.APP) "App" else "API"} logs",
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
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF191A20)
            )
        )

        // SEGMENTED TABS: App / API / Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogsTabButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Apps,
                label = "${Strings.get("logs_app", language)} (${appLogs.size})",
                selected = selectedTab == LogsTab.APP,
                selectedColor = Color(0xFF118270)
            ) { selectedTab = LogsTab.APP }

            Spacer(modifier = Modifier.width(4.dp))

            LogsTabButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Api,
                label = "${Strings.get("logs_api", language)} (${apiLogs.size})",
                selected = selectedTab == LogsTab.API,
                selectedColor = Color(0xFF5A0B9C)
            ) { selectedTab = LogsTab.API }

            Spacer(modifier = Modifier.width(4.dp))

            LogsTabButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Info,
                label = "Info",
                selected = selectedTab == LogsTab.INFO,
                selectedColor = Color(0xFF2C5364)
            ) { selectedTab = LogsTab.INFO }
        }

        if (selectedTab == LogsTab.INFO) {
            SystemInfoPanel(
                info = systemInfoText,
                logFilePath = Logger.logFilePath(),
                onCopy = {
                    clipboardManager.setText(AnnotatedString(systemInfoText))
                    Toast.makeText(context, Strings.get("logs_copied", language), Toast.LENGTH_SHORT).show()
                },
                onExport = { exportLauncher.launch("freemusical_logs_${Logger.sessionId}.txt") },
                onShare = { shareLogs(context) }
            )
        } else {
            // LEVEL FILTER + VERBOSE TOGGLE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogLevelFilterChip(label = "ALL", selected = levelFilter == null) { levelFilter = null }
                LogLevel.values().forEach { level ->
                    LogLevelFilterChip(label = level.name, selected = levelFilter == level) {
                        levelFilter = if (levelFilter == level) null else level
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                LogLevelFilterChip(
                    label = if (verboseLogging) "VERBOSE ON" else "VERBOSE OFF",
                    selected = verboseLogging
                ) {
                    verboseLogging = !verboseLogging
                    Logger.setMinLevel(if (verboseLogging) LogLevel.DEBUG else LogLevel.INFO)
                    Toast.makeText(
                        context,
                        if (verboseLogging) "Verbose logging ON" else "Verbose logging OFF",
                        Toast.LENGTH_SHORT
                    ).show()
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
                            imageVector = if (selectedTab == LogsTab.APP) Icons.Default.Dvr else Icons.Default.Http,
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
}

@Composable
private fun LogsTabButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) selectedColor else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (selected) Color.White else Color.Gray,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SystemInfoPanel(
    info: String,
    logFilePath: String?,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCopy, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2D35))) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy", fontSize = 12.sp)
            }
            Button(onClick = onExport, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2D35))) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export", fontSize = 12.sp)
            }
            Button(onClick = onShare, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2D35))) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Сервисная информация",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))

        SelectionContainer {
            Text(
                text = info,
                color = Color.White.copy(alpha = 0.9f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Файл логов",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = logFilePath ?: "Файловое логирование не инициализировано",
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}

private fun shareLogs(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "FreeMusical logs ${Logger.sessionId}")
            putExtra(Intent.EXTRA_TEXT, Logger.exportText())
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться логами"))
    } catch (e: Exception) {
        Logger.warn("Не удалось поделиться логами: ${e.message}", tag = "UI")
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

                Spacer(modifier = Modifier.width(6.dp))

                // THREAD
                Text(
                    text = logEntry.thread,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogLevelFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF118270),
            selectedLabelColor = Color.White,
            labelColor = Color.Gray
        )
    )
}
