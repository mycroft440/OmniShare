package com.omnishare.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnishare.utils.OmniStateRepository
import java.net.InetAddress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onScan: () -> Unit
) {
    val isRunning by OmniStateRepository.isServiceRunning.collectAsState()
    val groupInfo by OmniStateRepository.groupInfo.collectAsState()
    val hostIp by OmniStateRepository.hostIpAddress.collectAsState()
    val hostPort by OmniStateRepository.hostPort.collectAsState()
    val speed by OmniStateRepository.currentSpeed.collectAsState()
    val ping by OmniStateRepository.currentPing.collectAsState()
    val devices by OmniStateRepository.connectedDevices.collectAsState()

    val fabColor by animateColorAsState(
        targetValue = if (isRunning) RedError else CyanNeon,
        animationSpec = tween(500),
        label = "fabColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "OmniShare",
                            fontWeight = FontWeight.W700,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onStatsClick) {
                        Icon(Icons.Default.BarChart, contentDescription = "Estatísticas", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { if (isRunning) onStop() else onStart() },
                containerColor = fabColor,
                contentColor = if (isRunning) Color.White else Color.Black,
                shape = CircleShape,
                modifier = Modifier.shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = fabColor.copy(alpha = 0.4f),
                    spotColor = fabColor.copy(alpha = 0.4f)
                )
            ) {
                Crossfade(targetState = isRunning, label = "fabIcon") { running ->
                    Icon(
                        if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (running) "Parar" else "Iniciar",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = isRunning && groupInfo != null,
                transitionSpec = {
                    fadeIn(tween(400)) + slideInVertically { -40 } togetherWith
                        fadeOut(tween(300)) + slideOutVertically { 40 }
                },
                label = "contentSwitch"
            ) { active ->
                if (active) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HeroStatusCard(speed, ping, devices.size)
                        Spacer(Modifier.height(20.dp))
                        GlassConnectionCard(
                            ssid = groupInfo?.networkName ?: "OmniShare",
                            pass = groupInfo?.passphrase ?: "",
                            ip = hostIp,
                            port = hostPort
                        )
                    }
                } else {
                    AnimatedEmptyState(onScan)
                }
            }
        }
    }
}

@Composable
fun HeroStatusCard(speed: String, ping: String, deviceCount: Int) {
    val gradient = Brush.linearGradient(
        colors = listOf(CyanNeon.copy(alpha = 0.15f), PurpleDeep.copy(alpha = 0.15f)),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    val borderGradient = Brush.linearGradient(
        colors = listOf(CyanNeon.copy(alpha = 0.5f), PurpleDeep.copy(alpha = 0.5f))
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, borderGradient)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HeroStatItem(Icons.Default.Speed, speed, "Velocidade", CyanNeon)
                HeroStatItem(Icons.Default.NetworkCheck, ping, "Latência", PurpleLight)
                HeroStatItem(Icons.Default.Devices, deviceCount.toString(), "Dispositivos", GreenSuccess)
            }
        }
    }
}

@Composable
fun HeroStatItem(icon: ImageVector, value: String, label: String, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnim"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color,
            modifier = Modifier.size(28.dp).graphicsLayer { scaleX = pulse; scaleY = pulse })
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
fun GlassConnectionCard(ssid: String, pass: String, ip: String, port: Int) {
    val borderGradient = Brush.linearGradient(
        colors = listOf(CyanNeon.copy(alpha = 0.3f), PurpleDeep.copy(alpha = 0.2f), CyanNeon.copy(alpha = 0.1f))
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, borderGradient)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dados de Conexão", style = MaterialTheme.typography.titleMedium, color = CyanNeon)
            }
            Spacer(Modifier.height(16.dp))
            ConnectionRow(icon = Icons.Default.Router, label = "SSID", value = ssid)
            Spacer(Modifier.height(10.dp))
            ConnectionRow(icon = Icons.Default.Key, label = "Senha", value = pass)
            Spacer(Modifier.height(10.dp))
            ConnectionRow(icon = Icons.Default.Language, label = "Proxy", value = "$ip:$port")
        }
    }
}

@Composable
fun ConnectionRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.W500, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun AnimatedEmptyState(onScan: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyPulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = EaseInOutCubic), repeatMode = RepeatMode.Reverse),
        label = "iconPulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = EaseInOutCubic), repeatMode = RepeatMode.Reverse),
        label = "glowPulse"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(120.dp).clip(CircleShape)
                    .background(Brush.radialGradient(colors = listOf(CyanNeon.copy(alpha = glowAlpha), Color.Transparent)))
            )
            Icon(
                Icons.Default.WifiTethering, contentDescription = null,
                modifier = Modifier.size(64.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                tint = CyanNeon.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("OmniShare está inativo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text("Inicie o compartilhamento para começar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = onScan,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(CyanNeon, PurpleDeep))),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Conectar via QR Code", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.W500)
        }
    }
}
