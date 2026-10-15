package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.TrackApiConfig
import com.example.player.TrackHttpServer
import com.example.ui.Strings

/**
 * Меню настроек интеграций: системный broadcast и локальный HTTP-сервер.
 * Обе интеграции по умолчанию выключены.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationSubmenuScreen(
    language: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var broadcastEnabled by remember { mutableStateOf(TrackApiConfig.isBroadcastEnabled(context)) }
    var httpEnabled by remember { mutableStateOf(TrackApiConfig.isHttpEnabled(context)) }
    var portText by remember { mutableStateOf(TrackApiConfig.httpPort(context).toString()) }
    var appliedPort by remember { mutableStateOf(TrackApiConfig.httpPort(context)) }
    var running by remember { mutableStateOf(TrackHttpServer.isRunning) }

    val localIp = remember { TrackHttpServer.localIpv4() }

    val portValue = portText.toIntOrNull()
    val portValid = portValue != null && portValue in TrackApiConfig.MIN_PORT..TrackApiConfig.MAX_PORT
    val portChanged = portValid && portValue != appliedPort

    val accent = Color(0xFF00F5D4)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121318))
    ) {
        TopAppBar(
            title = {
                Text(
                    text = Strings.get("integration_title", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
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
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF191A20))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Статус HTTP-сервера ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Strings.get("integration_status", language),
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (running) {
                                Strings.get("integration_running", language)
                            } else {
                                Strings.get("integration_stopped", language)
                            },
                            color = if (running) accent else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = Strings.get("integration_local_ip", language),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = localIp ?: Strings.get("integration_no_wifi", language),
                        color = if (localIp != null) Color.White else Color(0xFFFF9F0A),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )

                    if (running) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = TrackHttpServer.currentUrl() ?: "-",
                            color = accent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val url = TrackHttpServer.currentUrl()
                                if (url != null) {
                                    clipboardManager.setText(AnnotatedString(url))
                                    Toast.makeText(
                                        context,
                                        Strings.get("integration_url_copied", language),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = running,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2C2D35),
                                disabledContainerColor = Color(0xFF232428)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Strings.get("integration_copy_url", language), fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Broadcast ──
            IntegrationSwitchRow(
                title = Strings.get("integration_broadcast", language),
                description = Strings.get("integration_broadcast_desc", language),
                checked = broadcastEnabled,
                accent = accent,
                onCheckedChange = { enabled ->
                    broadcastEnabled = enabled
                    TrackApiConfig.setBroadcastEnabled(context, enabled)
                    Toast.makeText(
                        context,
                        Strings.get(
                            if (enabled) "integration_enabled_toast" else "integration_disabled_toast",
                            language
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── HTTP-сервер ──
            IntegrationSwitchRow(
                title = Strings.get("integration_http", language),
                description = Strings.get("integration_http_desc", language),
                checked = httpEnabled,
                accent = accent,
                onCheckedChange = { enabled ->
                    httpEnabled = enabled
                    TrackApiConfig.setHttpEnabled(context, enabled)
                    running = TrackHttpServer.isRunning
                    if (enabled && !running) {
                        Toast.makeText(
                            context,
                            Strings.get("integration_no_wifi", language),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            Strings.get(
                                if (enabled) "integration_enabled_toast" else "integration_disabled_toast",
                                language
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Порт ──
            Text(
                text = Strings.get("integration_port", language),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { input ->
                        if (input.length <= 5 && input.all { it.isDigit() }) {
                            portText = input
                        }
                    },
                    singleLine = true,
                    isError = portText.isNotEmpty() && !portValid,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accent.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val port = portText.toIntOrNull()
                        if (port != null && port in TrackApiConfig.MIN_PORT..TrackApiConfig.MAX_PORT) {
                            TrackApiConfig.setHttpPort(context, port)
                            appliedPort = port
                            running = TrackHttpServer.isRunning
                            Toast.makeText(
                                context,
                                Strings.get("integration_port_saved", language),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = portChanged,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        disabledContainerColor = Color(0xFF2C2D35)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = Strings.get("integration_apply", language),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = Strings.get("integration_port_hint", language),
                color = Color.Gray,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Эндпоинты ──
            Text(
                text = Strings.get("integration_endpoints", language),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            val base = TrackHttpServer.currentUrl() ?: "http://<ip>:$appliedPort/"
            Text(
                text = "GET ${base}status\nGET ${base}events",
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IntegrationSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF2C2D35)
            )
        )
    }
}
