package com.omnishare.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import com.omnishare.R
import com.omnishare.service.OmniShareService
import com.omnishare.service.OmniVpnService
import com.omnishare.utils.OmniLogger
import com.omnishare.utils.OmniStateRepository
import com.omnishare.utils.QRCodeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettingsClick: () -> Unit,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onScan: () -> Unit
) {
    var mode by remember { mutableStateOf("share") } // "share" ou "receive"
    
    val isRunning by OmniStateRepository.isServiceRunning.collectAsStateWithLifecycle()
    val isVpnActive by OmniVpnService.isVpnActive.collectAsStateWithLifecycle()
    
    val groupInfo by OmniStateRepository.groupInfo.collectAsStateWithLifecycle()
    val hostIpAddress by OmniStateRepository.hostIpAddress.collectAsStateWithLifecycle()
    val port by OmniStateRepository.hostPort.collectAsStateWithLifecycle()
    val speed by OmniStateRepository.currentSpeed.collectAsStateWithLifecycle()
    val ping by OmniStateRepository.currentPing.collectAsStateWithLifecycle()
    val logs by OmniLogger.logs.collectAsStateWithLifecycle()
    val devices by OmniStateRepository.connectedDevices.collectAsStateWithLifecycle()
    
    var showLogs by remember { mutableStateOf(false) }
    var showQrCode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    ) 
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Seletor de Modo
                ModeSelector(
                    currentMode = mode,
                    onModeChange = { if (!isRunning && !isVpnActive) mode = it }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (mode == "share") {
                    ShareModeUI(
                        isRunning = isRunning,
                        speed = speed,
                        ping = ping,
                        groupInfo = groupInfo,
                        hostIpAddress = hostIpAddress,
                        port = port,
                        devices = devices,
                        logs = logs,
                        showLogs = showLogs,
                        onShowLogsToggle = { showLogs = !showLogs },
                        onShowQr = { showQrCode = true },
                        onStart = onStart,
                        onStop = onStop
                    )
                } else {
                    ReceiveModeUI(
                        isActive = isVpnActive,
                        logs = logs,
                        showLogs = showLogs,
                        onShowLogsToggle = { showLogs = !showLogs },
                        onStartVpn = onStartVpn,
                        onStopVpn = onStopVpn,
                        onScan = onScan
                    )
                }
            }
        }
    }

    if (showQrCode && groupInfo != null) {
        QrCodeDialog(
            ssid = groupInfo?.networkName ?: "",
            password = groupInfo?.passphrase ?: "",
            proxy = "$hostIpAddress:$port",
            onDismiss = { showQrCode = false }
        )
    }
}

@Composable
fun ModeSelector(currentMode: String, onModeChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp)
    ) {
        ModeButton(
            text = stringResource(R.string.mode_share),
            isSelected = currentMode == "share",
            modifier = Modifier.weight(1f),
            onClick = { onModeChange("share") }
        )
        ModeButton(
            text = stringResource(R.string.mode_receive),
            isSelected = currentMode == "receive",
            modifier = Modifier.weight(1f),
            onClick = { onModeChange("receive") }
        )
    }
}

@Composable
fun ModeButton(text: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun ShareModeUI(
    isRunning: Boolean,
    speed: String,
    ping: String,
    groupInfo: android.net.wifi.p2p.WifiP2pGroup?,
    hostIpAddress: String,
    port: Int,
    devices: List<String>,
    logs: List<String>,
    showLogs: Boolean,
    onShowLogsToggle: () -> Unit,
    onShowQr: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StatusIndicator(isRunning)
        Spacer(modifier = Modifier.height(32.dp))
        AnimatedVisibility(visible = isRunning) { StatsRow(speed, ping) }
        Spacer(modifier = Modifier.height(24.dp))
        if (isRunning && groupInfo != null) {
            InfoSection(
                ssid = groupInfo.networkName ?: "...",
                password = groupInfo.passphrase ?: "...",
                proxy = "$hostIpAddress:$port",
                onShowQr = onShowQr
            )
            Spacer(modifier = Modifier.height(24.dp))
            DevicesSection(devices)
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onShowLogsToggle) {
            Icon(if (showLogs) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (showLogs) stringResource(R.string.hide_logs) else stringResource(R.string.show_logs))
        }
        if (showLogs) { LogsPreview(logs); Spacer(modifier = Modifier.height(16.dp)) }
        ActionButton(isRunning, onStart, onStop)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ReceiveModeUI(
    isActive: Boolean,
    logs: List<String>,
    showLogs: Boolean,
    onShowLogsToggle: () -> Unit,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onScan: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StatusIndicator(isActive)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.scan_qr_desc),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onScan,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.scan_button))
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (isActive) {
            Text(
                text = stringResource(R.string.vpn_active),
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        TextButton(onClick = onShowLogsToggle) {
            Icon(if (showLogs) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (showLogs) stringResource(R.string.hide_logs) else stringResource(R.string.show_logs))
        }
        if (showLogs) { LogsPreview(logs); Spacer(modifier = Modifier.height(16.dp)) }
        
        ActionButton(isActive, onStartVpn, onStopVpn)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun StatusIndicator(isRunning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
        if (isRunning) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
            )
        }
        
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatsRow(speed: String, ping: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(label = stringResource(R.string.speed_label), value = speed, icon = Icons.Default.Speed)
        Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.LightGray.copy(alpha = 0.3f)))
        StatItem(label = stringResource(R.string.latency_label), value = ping, icon = Icons.Default.Timer)
    }
}

@Composable
fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 11.sp, color = Color.Gray)
        }
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InfoSection(ssid: String, password: String, proxy: String, onShowQr: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.connection_data),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            
            TextButton(onClick = onShowQr, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("QR Code", fontSize = 12.sp)
            }
        }
        PremiumInfoCard(label = stringResource(R.string.ssid_label), value = ssid, icon = Icons.Default.NetworkWifi)
        PremiumInfoCard(label = stringResource(R.string.password_label), value = password, icon = Icons.Default.Lock)
        PremiumInfoCard(label = stringResource(R.string.proxy_label), value = proxy, icon = Icons.Default.Router)
    }
}

@Composable
fun PremiumInfoCard(label: String, value: String, icon: ImageVector) {
    val clipboardManager = LocalClipboardManager.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { clipboardManager.setText(AnnotatedString(value)) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.LightGray)
        }
    }
}

@Composable
fun LogsPreview(logs: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        LazyColumn(
            modifier = Modifier.padding(12.dp),
            reverseLayout = false
        ) {
            items(logs) { log ->
                val color = when {
                    log.contains("[ERROR]") -> Color(0xFFFF5252)
                    log.contains("[WARN]") -> Color(0xFFFFD740)
                    log.contains("[INFO]") -> Color(0xFF64B5F6)
                    else -> Color(0xFF81C784)
                }
                Text(
                    text = log,
                    color = color,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ActionButton(isRunning: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Button(
        onClick = { if (isRunning) onStop() else onStart() },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isRunning) stringResource(R.string.stop_sharing).uppercase() else stringResource(R.string.start_sharing).uppercase(),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun DevicesSection(devices: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "DISPOSITIVOS CONECTADOS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        
        if (devices.isEmpty()) {
            Text(
                text = "Nenhum dispositivo usando o proxy no momento.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    devices.forEach { ip ->
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = ip, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QrCodeDialog(ssid: String, password: String, proxy: String, onDismiss: () -> Unit) {
    val qrContent = "WIFI:S:$ssid;T:WPA;P:$password;H:false;;PROXY:$proxy"
    val qrBitmap = remember(qrContent) { QRCodeUtils.generateQRCode(qrContent, 512) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Conexão Rápida",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Escaneie para conectar ao Wi-Fi e Proxy",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (qrBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(200.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("FECHAR")
                }
            }
        }
    }
}
